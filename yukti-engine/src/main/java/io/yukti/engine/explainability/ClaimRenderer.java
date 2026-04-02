package io.yukti.engine.explainability;

import io.yukti.explain.core.claims.ClaimType;
import io.yukti.explain.core.claims.NormalizedClaim;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Renders narrative text from NormalizedClaim list only. Does not introduce new numbers or entities;
 * only interpolates values already present in normalizedFields.
 */
public final class ClaimRenderer {

    private ClaimRenderer() {}

    /**
     * Render full narrative from claims: sections for comparison, allocation, cap switch, fee, assumption.
     * All displayed text is derived only from claim normalizedFields.
     */
    public static String render(List<NormalizedClaim> claims) {
        StringBuilder sb = new StringBuilder();

        // Summary line from first threshold if any; otherwise skip (deterministic path may have no THRESHOLD)
        List<NormalizedClaim> comparisons = claims.stream().filter(c -> c.claimType() == ClaimType.COMPARISON).toList();
        List<NormalizedClaim> allocations = claims.stream().filter(c -> c.claimType() == ClaimType.ALLOCATION).toList();
        List<NormalizedClaim> capSwitches = claims.stream().filter(c -> c.claimType() == ClaimType.CAP_SWITCH).toList();
        List<NormalizedClaim> feeJustifications = claims.stream().filter(c -> c.claimType() == ClaimType.FEE_JUSTIFICATION).toList();
        List<NormalizedClaim> assumptions = claims.stream().filter(c -> c.claimType() == ClaimType.ASSUMPTION).toList();

        sb.append("## Why these cards\n");
        for (NormalizedClaim c : comparisons) {
            sb.append("- ").append(renderComparison(c)).append("\n");
        }

        sb.append("\n## Allocation\n");
        sb.append("| Category | Card |\n|----------|------|\n");
        for (NormalizedClaim c : allocations) {
            String cat = getStr(c.normalizedFields(), "category");
            String card = getStr(c.normalizedFields(), "cardId");
            sb.append("| ").append(cat).append(" | ").append(card).append(" |\n");
        }

        sb.append("\n## Caps and switching\n");
        for (NormalizedClaim c : capSwitches) {
            sb.append("- ").append(renderCapSwitch(c)).append("\n");
        }

        sb.append("\n## Fees and credits\n");
        for (NormalizedClaim c : feeJustifications) {
            sb.append("- ").append(renderFeeJustification(c)).append("\n");
        }

        sb.append("\n## Assumptions\n");
        for (NormalizedClaim c : assumptions) {
            sb.append("- ").append(renderAssumption(c)).append("\n");
        }

        return sb.toString().trim();
    }

    /** Single claim line: only uses normalizedFields. */
    public static String renderClaimLine(NormalizedClaim claim) {
        return switch (claim.claimType()) {
            case COMPARISON -> renderComparison(claim);
            case ALLOCATION -> getStr(claim.normalizedFields(), "category") + " → " + getStr(claim.normalizedFields(), "cardId");
            case CAP_SWITCH -> renderCapSwitch(claim);
            case FEE_JUSTIFICATION -> renderFeeJustification(claim);
            case ASSUMPTION -> renderAssumption(claim);
            case THRESHOLD -> "";
        };
    }

    private static String renderComparison(NormalizedClaim c) {
        String winnerId = getStr(c.normalizedFields(), "winnerId");
        String category = getStr(c.normalizedFields(), "category");
        String runnerUpId = getStr(c.normalizedFields(), "runnerUpId");
        String deltaUsd = getStr(c.normalizedFields(), "deltaUsd");
        if (runnerUpId == null || runnerUpId.isEmpty()) {
            return String.format(Locale.ROOT, "%s wins %s: delta $%s", winnerId, category, deltaUsd);
        }
        return String.format(Locale.ROOT, "%s wins %s over %s: delta $%s", winnerId, category, runnerUpId, deltaUsd);
    }

    private static String renderCapSwitch(NormalizedClaim c) {
        String category = getStr(c.normalizedFields(), "category");
        String capAmountUsd = getStr(c.normalizedFields(), "capAmountUsd");
        String remainderUsd = getStr(c.normalizedFields(), "remainderUsd");
        String fromCardId = getStr(c.normalizedFields(), "fromCardId");
        String toCardId = getStr(c.normalizedFields(), "toCardId");
        if (toCardId == null || toCardId.isEmpty()) {
            return String.format(Locale.ROOT, "%s %s: cap $%s, remaining $%s", fromCardId, category, capAmountUsd, remainderUsd);
        }
        return String.format(Locale.ROOT, "%s %s: cap $%s, remaining $%s, fallback %s", fromCardId, category, capAmountUsd, remainderUsd, toCardId);
    }

    private static String renderFeeJustification(NormalizedClaim c) {
        String cardId = getStr(c.normalizedFields(), "cardId");
        String feeUsd = getStr(c.normalizedFields(), "feeUsd");
        String creditsAssumedUsd = getStr(c.normalizedFields(), "creditsAssumedUsd");
        return String.format(Locale.ROOT, "%s: fee $%s, credits $%s", cardId, feeUsd, creditsAssumedUsd);
    }

    private static String renderAssumption(NormalizedClaim c) {
        String cpp = getStr(c.normalizedFields(), "cppTableUsed");
        String penalties = getStr(c.normalizedFields(), "penaltiesUsed");
        String credits = getStr(c.normalizedFields(), "creditsMode");
        return String.format(Locale.ROOT, "cpp: %s, penalties: %s, credits: %s", cpp, penalties, credits);
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return "";
        return v.toString();
    }
}
