package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.explainability.ExplanationGenerator;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.core.explainability.evidence.*;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimType;
import io.yukti.explain.core.claims.ClaimTypeRules;
import io.yukti.explain.core.evidence.graph.EvidenceIdHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic narrative from StructuredExplanation. Produces a claims list and rendered text derived from claims only.
 */
public final class DeterministicExplanationGeneratorV1 implements ExplanationGenerator {
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    @Override
    public NarrativeExplanation generate(StructuredExplanation structured) {
        List<Claim> claims = buildClaims(structured);
        return renderFromClaims(structured, claims);
    }

    /**
     * Renders narrative from an arbitrary list of claims (e.g. verified LLM claims). Used when LLM path passes verification.
     */
    public NarrativeExplanation renderFromClaims(StructuredExplanation structured, List<Claim> claims) {
        String summary = "";
        String allocationTable = buildAllocationTable(structured);
        String details = renderDetailsFromClaims(claims);
        String assumptions = renderAssumptionsFromClaims(claims);
        for (Claim c : claims) {
            if (c.claimType() == ClaimType.THRESHOLD && summary.isEmpty() && c.text().contains("Net value")) {
                summary = c.text();
                break;
            }
        }
        if (summary.isEmpty()) {
            summary = buildSummaryFallback(structured);
        }
        String fullText = summary + "\n\n" + allocationTable + "\n\n" + details + "\n\n" + assumptions;
        return new NarrativeExplanation(
            claims,
            summary,
            allocationTable,
            details,
            assumptions,
            fullText,
            structured.evidenceGraphDigest(),
            structured.evidenceIds(),
            "",
            "PASS",
            claims.size(),
            0
        );
    }

    private List<Claim> buildClaims(StructuredExplanation structured) {
        EvidenceIndex index = new EvidenceIndex(structured.evidenceBlocks());
        List<Claim> out = new ArrayList<>();
        int t = 0, a = 0, c = 0, s = 0, f = 0, as = 0;

        var b = structured.breakdown();
        String summaryText = String.format(Locale.ROOT, "Net value: $%.2f. Portfolio: %d card(s). Goal: %s",
            b.netValueUsd().setScale(2, ROUNDING), structured.portfolioCardIds().size(), structured.goalType());
        if (structured.primaryCurrencyOrNull() != null) {
            summaryText += " (primary: " + structured.primaryCurrencyOrNull() + ")";
        }
        summaryText += ".";
        out.add(new Claim(
            "det-threshold-" + (t++),
            ClaimType.THRESHOLD,
            summaryText,
            List.of(),
            new ArrayList<>(structured.portfolioCardIds()),
            extractNumbers(b.netValueUsd(), b.totalEarnValueUsd(), b.totalCreditValueUsd(), b.totalFeesUsd())
        ));

        StringBuilder tableSb = new StringBuilder();
        tableSb.append("| Category | Card |\n|----------|------|\n");
        for (Category cat : Category.values()) {
            String cardId = structured.allocationByCategory().get(cat);
            if (cardId != null) {
                tableSb.append("| ").append(cat.name()).append(" | ").append(cardId).append(" |\n");
            }
        }
        List<String> tableEntities = new ArrayList<>(structured.allocationByCategory().values());
        out.add(new Claim("det-allocation-" + (a++), ClaimType.ALLOCATION, tableSb.toString(), List.of(), tableEntities, List.of()));

        for (WinnerByCategoryEvidence w : index.winnerByCategory()) {
            String eid = EvidenceIdHelper.compute("WINNER_BY_CATEGORY", w.winnerCardId(), w.cat().name(), w.content());
            List<String> entities = new ArrayList<>();
            entities.add(w.winnerCardId());
            entities.add(w.cat().name());
            if (w.runnerUpCardIdOrNull() != null) entities.add(w.runnerUpCardIdOrNull());
            out.add(new Claim(
                "det-comparison-" + (c++),
                ClaimType.COMPARISON,
                "- " + w.content(),
                List.of(eid),
                entities,
                extractNumbersFromContent(w.content())
            ));
        }
        for (EvidenceBlock bl : index.legacyWithType("WINNER_BY_CATEGORY")) {
            String eid = EvidenceIdHelper.compute(bl.type(), bl.cardId(), bl.category(), bl.content());
            out.add(new Claim("det-comparison-" + (c++), ClaimType.COMPARISON, "- " + bl.content(),
                List.of(eid), List.of(bl.cardId(), bl.category()), extractNumbersFromContent(bl.content())));
        }

        for (PortfolioStopEvidence p : index.portfolioStops()) {
            out.add(new Claim("det-threshold-" + (t++), ClaimType.THRESHOLD, "Stop reason: " + p.content(), List.of(), List.of(), List.of()));
        }
        for (EvidenceBlock bl : index.legacyWithType("PORTFOLIO_STOP")) {
            out.add(new Claim("det-threshold-" + (t++), ClaimType.THRESHOLD, "Stop reason: " + bl.content(), List.of(), List.of(), List.of()));
        }

        // Build category→ALLOCATION_SEGMENT evidence ID map for CAP_SWITCH citation
        Map<String, String> allocSegByCat = new HashMap<>();
        for (EvidenceBlock seg : index.legacyWithType(ClaimTypeRules.ALLOCATION_SEGMENT)) {
            String segEid = EvidenceIdHelper.compute(seg.type(), seg.cardId(), seg.category(), seg.content());
            allocSegByCat.put(seg.category(), segEid);
        }

        for (CapHitEvidence ch : index.capHits()) {
            String eid = EvidenceIdHelper.compute("CAP_HIT", ch.cardId(), ch.cat().name(), ch.content());
            List<String> cited = new ArrayList<>();
            cited.add(eid);
            String segEid = allocSegByCat.get(ch.cat().name());
            if (segEid != null) cited.add(segEid);
            out.add(new Claim(
                "det-capSwitch-" + (s++),
                ClaimType.CAP_SWITCH,
                "- " + ch.content(),
                cited,
                List.of(ch.cardId(), ch.cat().name()),
                extractNumbersFromContent(ch.content())
            ));
        }
        for (EvidenceBlock bl : index.legacyWithType("CAP_HIT")) {
            String eid = EvidenceIdHelper.compute(bl.type(), bl.cardId(), bl.category(), bl.content());
            List<String> cited = new ArrayList<>();
            cited.add(eid);
            String segEid = allocSegByCat.get(bl.category());
            if (segEid != null) cited.add(segEid);
            out.add(new Claim("det-capSwitch-" + (s++), ClaimType.CAP_SWITCH, "- " + bl.content(),
                cited, List.of(bl.cardId(), bl.category()), extractNumbersFromContent(bl.content())));
        }

        for (FeeBreakEvenEvidence fe : index.feeBreakEvens()) {
            String eid = EvidenceIdHelper.compute("FEE_BREAK_EVEN", fe.cardId(), "", fe.content());
            out.add(new Claim(
                "det-feeJustification-" + (f++),
                ClaimType.FEE_JUSTIFICATION,
                "- " + fe.content(),
                List.of(eid),
                List.of(fe.cardId()),
                extractNumbersFromContent(fe.content())
            ));
        }
        for (EvidenceBlock bl : index.legacyWithType("FEE_BREAK_EVEN")) {
            String eid = EvidenceIdHelper.compute(bl.type(), bl.cardId(), bl.category(), bl.content());
            out.add(new Claim("det-feeJustification-" + (f++), ClaimType.FEE_JUSTIFICATION, "- " + bl.content(),
                List.of(eid), List.of(bl.cardId()), extractNumbersFromContent(bl.content())));
        }

        Optional<AssumptionEvidence> ae = index.firstAssumption();
        if (ae.isPresent()) {
            var aev = ae.get();
            String content = aev.content();
            String eid = EvidenceIdHelper.compute("ASSUMPTION", "", "", content);
            List<String> nums = new ArrayList<>();
            aev.cppUsedByCurrency().values().forEach(v -> nums.add(v != null ? v.toPlainString() : ""));
            out.add(new Claim("det-assumption-" + (as++), ClaimType.ASSUMPTION, "- " + content, List.of(eid), List.of(), nums));
        }
        for (EvidenceBlock bl : index.legacyWithType("ASSUMPTION")) {
            String eid = EvidenceIdHelper.compute(bl.type(), bl.cardId(), bl.category(), bl.content());
            out.add(new Claim("det-assumption-" + (as++), ClaimType.ASSUMPTION, "- " + bl.content(),
                List.of(eid), List.of(), extractNumbersFromContent(bl.content())));
        }

        return out;
    }

    private static List<String> extractNumbers(BigDecimal... vals) {
        List<String> out = new ArrayList<>();
        for (BigDecimal v : vals) {
            if (v != null) out.add(v.stripTrailingZeros().toPlainString());
        }
        return out;
    }

    private static List<String> extractNumbersFromContent(String content) {
        if (content == null) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(content);
        while (m.find()) out.add(m.group());
        return out;
    }

    private String renderDetailsFromClaims(List<Claim> claims) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Why these cards\n");
        claims.stream().filter(c -> c.claimType() == ClaimType.COMPARISON).forEach(c -> sb.append(c.text()).append("\n"));
        sb.append("\n## How to use them\n");
        sb.append("Allocate spend per category to the card shown in the table above.\n");
        claims.stream().filter(c -> c.claimType() == ClaimType.THRESHOLD && c.text().startsWith("Stop")).forEach(c -> sb.append(c.text()).append("\n"));
        sb.append("\n## Caps and switching\n");
        claims.stream().filter(c -> c.claimType() == ClaimType.CAP_SWITCH).forEach(c -> sb.append(c.text()).append("\n"));
        sb.append("\n## Fees and credits\n");
        claims.stream().filter(c -> c.claimType() == ClaimType.FEE_JUSTIFICATION).forEach(c -> sb.append(c.text()).append("\n"));
        List<Claim> otherThreshold = claims.stream().filter(c -> c.claimType() == ClaimType.THRESHOLD && !c.text().startsWith("Stop")).toList();
        if (!otherThreshold.isEmpty()) {
            sb.append("\n## Narrative (AI)\n\n");
            otherThreshold.forEach(c -> sb.append(c.text()).append("\n"));
        }
        return sb.toString();
    }

    private String renderAssumptionsFromClaims(List<Claim> claims) {
        StringBuilder sb = new StringBuilder();
        claims.stream().filter(c -> c.claimType() == ClaimType.ASSUMPTION).forEach(c -> sb.append(c.text()).append("\n"));
        return sb.isEmpty() ? "Not available\n" : sb.toString();
    }

    private String buildSummaryFallback(StructuredExplanation structured) {
        var b = structured.breakdown();
        int numCards = structured.portfolioCardIds().size();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Net value: $%.2f. Portfolio: %d card(s). Goal: %s",
            b.netValueUsd().setScale(2, ROUNDING), numCards, structured.goalType()));
        if (structured.primaryCurrencyOrNull() != null) {
            sb.append(" (primary: ").append(structured.primaryCurrencyOrNull()).append(")");
        }
        sb.append(".");
        return sb.toString();
    }

    private String buildAllocationTable(StructuredExplanation structured) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Category | Card |\n");
        sb.append("|----------|------|\n");
        for (Category cat : Category.values()) {
            String cardId = structured.allocationByCategory().get(cat);
            if (cardId != null) {
                sb.append("| ").append(cat.name()).append(" | ").append(cardId).append(" |\n");
            }
        }
        return sb.toString();
    }
}
