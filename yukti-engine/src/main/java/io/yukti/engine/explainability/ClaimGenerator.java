package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.ClaimIdGenerator;
import io.yukti.explain.core.claims.ClaimType;
import io.yukti.explain.core.claims.ClaimTypeRules;
import io.yukti.explain.core.claims.NormalizedClaim;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.explain.core.evidence.graph.EvidenceNode;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic claim generation from StructuredExplanation and EvidenceGraph only.
 * Produces NormalizedClaim list with schema v1 fields; claimId = sha256(canonical(claimType, normalizedFields, citedEvidenceIds)).
 */
public final class ClaimGenerator {

    private static final String ROOT_ID = EvidenceGraph.rootEvidenceId();

    // Content parsing for evidence node content strings (deterministic format from engine)
    private static final Pattern WINNER_RUNNER_UP = Pattern.compile(" over ([^:]+):");
    private static final Pattern WINNER_DELTA = Pattern.compile("delta \\$?([0-9.]+)");
    private static final Pattern CAP_REMAINING = Pattern.compile("remaining \\$?([0-9.]+)");
    private static final Pattern CAP_CAP_AMOUNT = Pattern.compile("cap \\$?([0-9.]+)");
    private static final Pattern CAP_FALLBACK = Pattern.compile("fallback ([a-zA-Z0-9_-]+)");
    private static final Pattern FEE_AMOUNT = Pattern.compile("fee \\$?([0-9.]+)");
    private static final Pattern FEE_CREDITS = Pattern.compile("credits \\$?([0-9.]+)");
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    private ClaimGenerator() {}

    /**
     * Generate claims only from structured explanation and evidence graph. Order is deterministic.
     */
    public static List<NormalizedClaim> generate(StructuredExplanation structured, EvidenceGraph graph) {
        List<EvidenceNode> evidenceNodes = graph.getNodes().stream()
            .filter(n -> !ROOT_ID.equals(n.evidenceId()))
            .sorted(Comparator.comparing(EvidenceNode::evidenceId))
            .toList();

        List<NormalizedClaim> out = new ArrayList<>();

        // 1) COMPARISON per WINNER_BY_CATEGORY node
        for (EvidenceNode n : evidenceNodes) {
            if (!"WINNER_BY_CATEGORY".equals(n.type())) continue;
            String winnerId = n.cardId();
            String category = n.category();
            String runnerUpId = parseRunnerUp(n.content());
            String deltaUsd = parseWinnerDelta(n.content());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("category", category);
            fields.put("winnerId", winnerId);
            fields.put("runnerUpId", runnerUpId != null ? runnerUpId : "");
            fields.put("deltaUsd", deltaUsd != null ? deltaUsd : "");
            List<String> citedIds = List.of(n.evidenceId());
            List<String> entities = new ArrayList<>(List.of(winnerId, category));
            if (runnerUpId != null && !runnerUpId.isEmpty()) entities.add(runnerUpId);
            entities.sort(String::compareTo);
            List<String> numbers = deltaUsd != null && !deltaUsd.isEmpty() ? List.of(deltaUsd) : List.of();
            String claimId = ClaimIdGenerator.compute(ClaimType.COMPARISON, fields, citedIds);
            out.add(new NormalizedClaim(claimId, ClaimType.COMPARISON, fields, citedIds, entities, numbers));
        }

        // 2) ALLOCATION per category from allocationByCategory (one claim per category; no segment evidence in graph today)
        List<Category> categories = Arrays.stream(Category.values()).sorted(Comparator.comparing(Enum::name)).toList();
        for (Category cat : categories) {
            String cardId = structured.allocationByCategory().get(cat);
            if (cardId == null) continue;
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("category", cat.name());
            fields.put("cardId", cardId);
            fields.put("spendUsd", "");
            fields.put("segmentIndex", 0);
            List<String> citedIds = List.of();
            List<String> entities = List.of(cat.name(), cardId);
            List<String> numbers = List.of();
            String claimId = ClaimIdGenerator.compute(ClaimType.ALLOCATION, fields, citedIds);
            out.add(new NormalizedClaim(claimId, ClaimType.ALLOCATION, fields, citedIds, entities, numbers));
        }

        // Build category→ALLOCATION_SEGMENT evidence ID map for CAP_SWITCH citation
        Map<String, String> allocSegByCat = new HashMap<>();
        for (EvidenceNode n : evidenceNodes) {
            if (ClaimTypeRules.ALLOCATION_SEGMENT.equals(n.type())) {
                allocSegByCat.put(n.category(), n.evidenceId());
            }
        }

        // 3) CAP_SWITCH per CAP_HIT node where remainderUsd > 0
        for (EvidenceNode n : evidenceNodes) {
            if (!"CAP_HIT".equals(n.type())) continue;
            String remainderStr = parseCapRemaining(n.content());
            if (remainderStr == null || remainderStr.isEmpty()) continue;
            BigDecimal remainder = new BigDecimal(remainderStr);
            if (remainder.compareTo(BigDecimal.ZERO) <= 0) continue;
            String capAmountStr = parseCapAmount(n.content());
            String fallbackId = parseCapFallback(n.content());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("category", n.category());
            fields.put("capAmountUsd", capAmountStr != null ? capAmountStr : "");
            fields.put("remainderUsd", remainderStr);
            fields.put("fromCardId", n.cardId());
            fields.put("toCardId", fallbackId != null ? fallbackId : "");
            List<String> citedIds = new ArrayList<>();
            citedIds.add(n.evidenceId());
            String segEid = allocSegByCat.get(n.category());
            if (segEid != null) citedIds.add(segEid);
            List<String> entities = new ArrayList<>(List.of(n.category(), n.cardId()));
            if (fallbackId != null && !fallbackId.isEmpty()) entities.add(fallbackId);
            entities.sort(String::compareTo);
            List<String> numbers = new ArrayList<>();
            if (capAmountStr != null) numbers.add(capAmountStr);
            numbers.add(remainderStr);
            numbers.sort(String::compareTo);
            String claimId = ClaimIdGenerator.compute(ClaimType.CAP_SWITCH, fields, citedIds);
            out.add(new NormalizedClaim(claimId, ClaimType.CAP_SWITCH, fields, citedIds, entities, numbers));
        }

        // 4) FEE_JUSTIFICATION per card in portfolio (from FEE_BREAK_EVEN node for that card)
        Set<String> portfolio = new HashSet<>(structured.portfolioCardIds());
        for (EvidenceNode n : evidenceNodes) {
            if (!"FEE_BREAK_EVEN".equals(n.type())) continue;
            String cardId = n.cardId();
            if (!portfolio.contains(cardId)) continue;
            String feeUsd = parseFeeAmount(n.content());
            String creditsUsd = parseFeeCredits(n.content());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("cardId", cardId);
            fields.put("feeUsd", feeUsd != null ? feeUsd : "");
            fields.put("creditsAssumedUsd", creditsUsd != null ? creditsUsd : "");
            List<String> citedIds = List.of(n.evidenceId());
            List<String> entities = List.of(cardId);
            List<String> numbers = new ArrayList<>();
            if (feeUsd != null) numbers.add(feeUsd);
            if (creditsUsd != null) numbers.add(creditsUsd);
            numbers.sort(String::compareTo);
            String claimId = ClaimIdGenerator.compute(ClaimType.FEE_JUSTIFICATION, fields, citedIds);
            out.add(new NormalizedClaim(claimId, ClaimType.FEE_JUSTIFICATION, fields, citedIds, entities, numbers));
        }

        // 5) ASSUMPTION: one claim from ASSUMPTION node
        Optional<EvidenceNode> assumptionNode = evidenceNodes.stream()
            .filter(n -> "ASSUMPTION".equals(n.type()))
            .findFirst();
        if (assumptionNode.isPresent()) {
            EvidenceNode n = assumptionNode.get();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("cppTableUsed", "default-cpp.v1");
            fields.put("penaltiesUsed", "strict");
            fields.put("creditsMode", "on");
            // Derive from content if needed; for determinism use fixed labels or parse
            List<String> citedIds = List.of(n.evidenceId());
            List<String> entities = List.of();
            List<String> numbers = extractNumbers(n.content());
            String claimId = ClaimIdGenerator.compute(ClaimType.ASSUMPTION, fields, citedIds);
            out.add(new NormalizedClaim(claimId, ClaimType.ASSUMPTION, fields, citedIds, entities, numbers));
        }

        return List.copyOf(out);
    }

    private static String parseRunnerUp(String content) {
        if (content == null) return null;
        Matcher m = WINNER_RUNNER_UP.matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String parseWinnerDelta(String content) {
        if (content == null) return null;
        Matcher m = WINNER_DELTA.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private static String parseCapRemaining(String content) {
        if (content == null) return null;
        Matcher m = CAP_REMAINING.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private static String parseCapAmount(String content) {
        if (content == null) return null;
        Matcher m = CAP_CAP_AMOUNT.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private static String parseCapFallback(String content) {
        if (content == null) return null;
        Matcher m = CAP_FALLBACK.matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String parseFeeAmount(String content) {
        if (content == null) return null;
        Matcher m = FEE_AMOUNT.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private static String parseFeeCredits(String content) {
        if (content == null) return null;
        Matcher m = FEE_CREDITS.matcher(content);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> extractNumbers(String content) {
        if (content == null) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(content);
        while (m.find()) out.add(m.group());
        return out.stream().distinct().sorted(String::compareTo).toList();
    }
}
