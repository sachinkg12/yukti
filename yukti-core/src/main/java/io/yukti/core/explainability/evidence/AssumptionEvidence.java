package io.yukti.core.explainability.evidence;

import io.yukti.core.domain.GoalType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record AssumptionEvidence(
    Map<String, BigDecimal> cppUsedByCurrency,
    Map<String, BigDecimal> penaltiesUsedByCurrency,
    Map<String, BigDecimal> creditUtilizationByCardId,
    GoalType goalType,
    String primaryCurrencyOrNull
) implements EvidenceBlock {
    public AssumptionEvidence {
        cppUsedByCurrency = cppUsedByCurrency != null ? Map.copyOf(cppUsedByCurrency) : Map.of();
        penaltiesUsedByCurrency = penaltiesUsedByCurrency != null ? Map.copyOf(penaltiesUsedByCurrency) : Map.of();
        creditUtilizationByCardId = creditUtilizationByCardId != null ? Map.copyOf(creditUtilizationByCardId) : Map.of();
    }

    @Override
    public String type() { return "ASSUMPTION"; }

    @Override
    public String content() {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: ").append(goalType);
        if (primaryCurrencyOrNull != null) sb.append(", primary: ").append(primaryCurrencyOrNull);
        if (!cppUsedByCurrency.isEmpty()) sb.append(", cpp: ").append(new TreeMap<>(cppUsedByCurrency));
        if (!penaltiesUsedByCurrency.isEmpty()) sb.append(", penalties: ").append(new TreeMap<>(penaltiesUsedByCurrency));
        if (!creditUtilizationByCardId.isEmpty()) sb.append(", utilization: ").append(new TreeMap<>(creditUtilizationByCardId));
        return sb.toString();
    }
}
