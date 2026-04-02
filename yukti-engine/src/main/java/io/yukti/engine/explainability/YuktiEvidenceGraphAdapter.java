package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.EvidenceBlock;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.explain.core.evidence.graph.EvidenceEdge;
import io.yukti.explain.core.evidence.graph.EvidenceEdgeType;
import io.yukti.explain.core.evidence.graph.EvidenceGraphBuilder;
import io.yukti.explain.core.evidence.graph.EvidenceGraphV1;
import io.yukti.explain.core.evidence.graph.EvidenceItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps OptimizationResult evidence blocks into EvidenceGraph v1.
 * Defines stablePayload, entities, and numbers per block type; builds edges per adapter rules.
 */
public final class YuktiEvidenceGraphAdapter {

    private static final String EVIDENCE_VERSION = "v1";
    private static final String ROOT_TYPE = "RESULT";

    private YuktiEvidenceGraphAdapter() {}

    /**
     * Build EvidenceGraph v1 from an optimization result.
     * Entities = card ids, category names, currency codes. Numbers = monetary/cpp values normalized to strings.
     */
    public static EvidenceGraphV1 build(OptimizationResult result) {
        Objects.requireNonNull(result);
        List<EvidenceItem> items = new ArrayList<>();
        List<EvidenceEdge> edges = new ArrayList<>();

        // Root node: entities = card ids, category names; numbers = breakdown (monetary/cpp normalized)
        SortedSet<String> rootEntities = new TreeSet<>();
        result.getPortfolioIds().forEach(rootEntities::add);
        result.getAllocation().values().forEach(rootEntities::add);
        result.getAllocation().keySet().forEach(c -> rootEntities.add(c.name()));
        SortedSet<String> rootNumbers = new TreeSet<>();
        rootNumbers.add(result.getBreakdown().getEarnValue().getAmount().stripTrailingZeros().toPlainString());
        rootNumbers.add(result.getBreakdown().getCreditsValue().getAmount().stripTrailingZeros().toPlainString());
        rootNumbers.add(result.getBreakdown().getFees().getAmount().stripTrailingZeros().toPlainString());
        rootNumbers.add(result.getBreakdown().getNet().getAmount().stripTrailingZeros().toPlainString());
        Map<String, Object> rootPayload = new LinkedHashMap<>();
        rootPayload.put("portfolioSize", result.getPortfolioIds().size());
        EvidenceItem root = EvidenceItem.of(ROOT_TYPE, EVIDENCE_VERSION, rootPayload, rootEntities, rootNumbers);
        items.add(root);

        Map<String, String> winnerByCategory = new LinkedHashMap<>(); // category -> evidenceId of WINNER_BY_CATEGORY
        List<EvidenceItem> evidenceItems = new ArrayList<>();

        for (EvidenceBlock eb : result.getEvidenceBlocks()) {
            EvidenceItem item = toEvidenceItem(eb);
            if (item == null) continue;
            evidenceItems.add(item);
            items.add(item);
            edges.add(new EvidenceEdge(root.getEvidenceId(), item.getEvidenceId(), EvidenceEdgeType.SUPPORTS));

            if ("WINNER_BY_CATEGORY".equals(eb.getType()) && eb.getCategory() != null && !eb.getCategory().isEmpty()) {
                winnerByCategory.put(eb.getCategory(), item.getEvidenceId());
            }
        }

        // CapHit dependsOn WinnerByCategory for same category
        for (EvidenceItem item : evidenceItems) {
            if ("CAP_HIT".equals(item.getEvidenceType())) {
                Object cat = item.getStablePayload().get("category");
                if (cat != null) {
                    String winnerId = winnerByCategory.get(cat.toString());
                    if (winnerId != null) {
                        edges.add(new EvidenceEdge(item.getEvidenceId(), winnerId, EvidenceEdgeType.DEPENDS_ON));
                    }
                }
            }
        }

        return EvidenceGraphBuilder.defaultBuilder().build(items, edges);
    }

    /**
     * Map domain EvidenceBlock to EvidenceItem with stablePayload, entities, numbers per type.
     */
    static EvidenceItem toEvidenceItem(EvidenceBlock eb) {
        String type = eb.getType();
        if (type == null) return null;

        return switch (type) {
            case "WINNER_BY_CATEGORY" -> mapWinnerByCategory(eb);
            case "CAP_HIT" -> mapCapHit(eb);
            case "FEE_BREAK_EVEN" -> mapFeeBreakEven(eb);
            case "ASSUMPTION" -> mapAssumption(eb);
            case "PORTFOLIO_STOP" -> mapPortfolioStop(eb);
            case "EARN_RATE", "ALLOCATION_SEGMENT" -> mapGeneric(eb);
            default -> mapGeneric(eb);
        };
    }

    // WINNER_BY_CATEGORY: category, winnerId, runnerUpId, deltaUsd
    private static final Pattern WINNER_DELTA = Pattern.compile("delta\\s+\\$?([0-9]+\\.[0-9]{2})");
    private static final Pattern WINNER_OVER = Pattern.compile("\\s+over\\s+([a-zA-Z0-9-]+)");

    private static EvidenceItem mapWinnerByCategory(EvidenceBlock eb) {
        String category = eb.getCategory() != null ? eb.getCategory() : "";
        String winnerId = eb.getCardId() != null ? eb.getCardId() : "";
        String content = eb.getContent() != null ? eb.getContent() : "";
        String runnerUpId = null;
        String deltaUsd = null;
        Matcher over = WINNER_OVER.matcher(content);
        if (over.find()) runnerUpId = over.group(1);
        Matcher delta = WINNER_DELTA.matcher(content);
        if (delta.find()) deltaUsd = delta.group(1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category);
        payload.put("winnerId", winnerId);
        payload.put("runnerUpId", runnerUpId != null ? runnerUpId : "");
        payload.put("deltaUsd", deltaUsd != null ? deltaUsd : "");

        SortedSet<String> entities = new TreeSet<>();
        if (!winnerId.isEmpty()) entities.add(winnerId);
        if (!category.isEmpty()) entities.add(category);
        if (runnerUpId != null && !runnerUpId.isEmpty()) entities.add(runnerUpId);
        SortedSet<String> numbers = new TreeSet<>();
        if (deltaUsd != null && !deltaUsd.isEmpty()) numbers.add(normalizeNumber(deltaUsd));

        return EvidenceItem.of("WINNER_BY_CATEGORY", EVIDENCE_VERSION, payload, entities, numbers);
    }

    // CAP_HIT: category, capAmountUsd, spendAppliedUsd, remainderUsd, fallbackCardId
    private static final Pattern CAP_NUMS = Pattern.compile("cap=\\$?([0-9.]+)\\s+applied=\\$?([0-9.]+)\\s+remainder=\\$?([0-9.]+)");
    private static final Pattern CAP_COMMA = Pattern.compile("cap\\s+\\$?([0-9.]+),?\\s+applied\\s+\\$?([0-9.]+),?\\s+remaining\\s+\\$?([0-9.]+)");
    private static final Pattern CAP_FALLBACK = Pattern.compile("fallback\\s+([a-zA-Z0-9-]+)|fallback=([a-zA-Z0-9-]+)");
    private static final Pattern CAP_HIT_NUMS_ALT = Pattern.compile("cap=([^\\s]+)\\s+period=[^\\s]+\\s+applied=([^\\s]+)\\s+remainder=([^\\s]+)");

    private static EvidenceItem mapCapHit(EvidenceBlock eb) {
        String category = eb.getCategory() != null ? eb.getCategory() : "";
        String cardId = eb.getCardId() != null ? eb.getCardId() : "";
        String content = eb.getContent() != null ? eb.getContent() : "";
        String capAmountUsd = "";
        String spendAppliedUsd = "";
        String remainderUsd = "";
        String fallbackCardId = "";
        Matcher m = CAP_NUMS.matcher(content);
        if (!m.find()) m = CAP_COMMA.matcher(content);
        if (!m.find()) m = CAP_HIT_NUMS_ALT.matcher(content);
        if (m.find()) {
            capAmountUsd = stripMoney(m.group(1));
            spendAppliedUsd = m.groupCount() >= 2 ? stripMoney(m.group(2)) : "";
            remainderUsd = m.groupCount() >= 3 ? stripMoney(m.group(3)) : "";
        }
        Matcher fb = CAP_FALLBACK.matcher(content);
        if (fb.find()) fallbackCardId = fb.group(1) != null ? fb.group(1) : (fb.group(2) != null ? fb.group(2) : "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("category", category);
        payload.put("capAmountUsd", capAmountUsd);
        payload.put("spendAppliedUsd", spendAppliedUsd);
        payload.put("remainderUsd", remainderUsd);
        payload.put("fallbackCardId", fallbackCardId);

        SortedSet<String> entities = new TreeSet<>();
        if (!cardId.isEmpty()) entities.add(cardId);
        if (!category.isEmpty()) entities.add(category);
        if (!fallbackCardId.isEmpty()) entities.add(fallbackCardId);
        SortedSet<String> numbers = new TreeSet<>();
        for (String n : List.of(capAmountUsd, spendAppliedUsd, remainderUsd))
            if (n != null && !n.isEmpty()) numbers.add(normalizeNumber(n));

        return EvidenceItem.of("CAP_HIT", EVIDENCE_VERSION, payload, entities, numbers);
    }

    // FEE_BREAK_EVEN: cardId, feeUsd, creditsAssumedUsd, netDeltaUsd (content e.g. "card: fee $95, credits $0, net delta $155" or "fee X, credits Y, incremental earn Z, net delta W")
    private static final Pattern FEE_NUMS = Pattern.compile("fee\\s+\\$?([0-9.]+).*?credits\\s+\\$?([0-9.]+).*?net delta\\s+\\$?([0-9.]+)");
    private static final Pattern FEE_NUMS_FULL = Pattern.compile("fee\\s+\\$?([0-9.]+).*?credits\\s+\\$?([0-9.]+).*?incremental earn\\s+\\$?([0-9.]+).*?net delta\\s+\\$?([0-9.]+)");

    private static EvidenceItem mapFeeBreakEven(EvidenceBlock eb) {
        String cardId = eb.getCardId() != null ? eb.getCardId() : "";
        String content = eb.getContent() != null ? eb.getContent() : "";
        String feeUsd = "";
        String creditsAssumedUsd = "";
        String incrementalEarnUsd = "";
        String netDeltaUsd = "";
        Matcher m = FEE_NUMS_FULL.matcher(content);
        if (m.find()) {
            feeUsd = stripMoney(m.group(1));
            creditsAssumedUsd = stripMoney(m.group(2));
            incrementalEarnUsd = stripMoney(m.group(3));
            netDeltaUsd = stripMoney(m.group(4));
        } else {
            m = FEE_NUMS.matcher(content);
            if (m.find()) {
                feeUsd = stripMoney(m.group(1));
                creditsAssumedUsd = stripMoney(m.group(2));
                netDeltaUsd = stripMoney(m.group(3));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cardId", cardId);
        payload.put("feeUsd", feeUsd);
        payload.put("creditsAssumedUsd", creditsAssumedUsd);
        payload.put("netDeltaUsd", netDeltaUsd);

        SortedSet<String> entities = new TreeSet<>();
        if (!cardId.isEmpty()) entities.add(cardId);
        SortedSet<String> numbers = new TreeSet<>();
        for (String n : List.of(feeUsd, creditsAssumedUsd, incrementalEarnUsd, netDeltaUsd))
            if (n != null && !n.isEmpty()) numbers.add(normalizeNumber(n));

        return EvidenceItem.of("FEE_BREAK_EVEN", EVIDENCE_VERSION, payload, entities, numbers);
    }

    // ASSUMPTION: cppUsedByCurrency, penalties, creditsMode (from content or minimal)
    private static EvidenceItem mapAssumption(EvidenceBlock eb) {
        String content = eb.getContent() != null ? eb.getContent() : "";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cppUsedByCurrency", Map.of());
        payload.put("penalties", Map.of());
        payload.put("creditsMode", "");
        payload.put("content", content);

        SortedSet<String> entities = new TreeSet<>();
        SortedSet<String> numbers = new TreeSet<>();
        Matcher num = Pattern.compile("[0-9]+\\.[0-9]+").matcher(content);
        while (num.find()) numbers.add(normalizeNumber(num.group()));

        return EvidenceItem.of("ASSUMPTION", EVIDENCE_VERSION, payload, entities, numbers);
    }

    private static EvidenceItem mapPortfolioStop(EvidenceBlock eb) {
        String content = eb.getContent() != null ? eb.getContent() : "";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reasonCode", content.contains(":") ? content.substring(0, content.indexOf(':')) : content);
        payload.put("message", content);
        SortedSet<String> entities = new TreeSet<>();
        SortedSet<String> numbers = new TreeSet<>();
        return EvidenceItem.of("PORTFOLIO_STOP", EVIDENCE_VERSION, payload, entities, numbers);
    }

    private static EvidenceItem mapGeneric(EvidenceBlock eb) {
        String type = eb.getType();
        String content = eb.getContent() != null ? eb.getContent() : "";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cardId", eb.getCardId() != null ? eb.getCardId() : "");
        payload.put("category", eb.getCategory() != null ? eb.getCategory() : "");
        payload.put("content", content);
        SortedSet<String> entities = new TreeSet<>();
        if (eb.getCardId() != null && !eb.getCardId().isEmpty()) entities.add(eb.getCardId());
        if (eb.getCategory() != null && !eb.getCategory().isEmpty()) entities.add(eb.getCategory());
        SortedSet<String> numbers = new TreeSet<>();
        Matcher m = Pattern.compile("[0-9]+\\.[0-9]+").matcher(content);
        while (m.find()) numbers.add(normalizeNumber(m.group()));
        return EvidenceItem.of(type, EVIDENCE_VERSION, payload, entities, numbers);
    }

    private static String stripMoney(String s) {
        if (s == null) return "";
        return s.replace("$", "").trim();
    }

    private static String normalizeNumber(String s) {
        if (s == null || s.isEmpty()) return s;
        try {
            return new BigDecimal(s.replace("$", "").trim()).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return s;
        }
    }

}
