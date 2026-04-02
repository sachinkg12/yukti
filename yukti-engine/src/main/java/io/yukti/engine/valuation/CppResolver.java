package io.yukti.engine.valuation;

import io.yukti.core.api.Catalog;
import io.yukti.core.domain.GoalType;
import io.yukti.core.domain.RewardCurrency;
import io.yukti.core.domain.RewardCurrencyType;
import io.yukti.core.domain.UserGoal;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.core.valuation.ValuationContext;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves effective USD-per-point for a currency given goal, overrides, and PenaltyPolicyV1.
 * Override precedence: userGoal.cppOverrides > context default table.
 * Override validation: > 0 and <= 0.10 (10 cents per point).
 * Single unit: USD per point (0.013 = 1.3 cpp). centsPerUnit = usdPerPoint * 100 for backward compat.
 */
public final class CppResolver {
    private static final BigDecimal MAX_OVERRIDE_USD = new BigDecimal("0.10");

    private final ValuationContext context;

    public CppResolver(ValuationContext context) {
        this.context = context;
    }

    /** When only catalog is available, load default cpp table (catalog not used for cpp). */
    public CppResolver(Catalog catalog) {
        this(DefaultCppTableLoader.load());
    }

    /** Effective USD per point (after override + penalty). */
    public BigDecimal usdPerPoint(RewardCurrency currency, UserGoal userGoal) {
        RewardCurrencyType type = currency.getType();
        BigDecimal cpp = resolveCpp(type, userGoal);
        BigDecimal penalty = PenaltyPolicyV1.penalty(type, userGoal);
        return cpp.multiply(penalty);
    }

    private BigDecimal resolveCpp(RewardCurrencyType type, UserGoal userGoal) {
        Optional<BigDecimal> override = userGoal.getCppOverride(type);
        if (override.isPresent()) {
            BigDecimal v = override.get();
            if (v.compareTo(BigDecimal.ZERO) <= 0 || v.compareTo(MAX_OVERRIDE_USD) > 0) {
                throw new IllegalArgumentException("cpp override for " + type + " must be > 0 and <= 0.10, got " + v);
            }
            return v;
        }
        return context.getUsdPerPoint(type);
    }

    /** Cents per point for backward compat. value = points * centsPerUnit/100. */
    public BigDecimal centsPerUnit(RewardCurrency currency, UserGoal userGoal) {
        return usdPerPoint(currency, userGoal).multiply(BigDecimal.valueOf(100));
    }

    /** Build assumption evidence for explainability. */
    public AssumptionEvidence buildAssumptionEvidence(UserGoal userGoal, Map<RewardCurrencyType, BigDecimal> cppUsed) {
        Map<String, BigDecimal> cppMap = new HashMap<>();
        cppMap.put(RewardCurrencyType.USD_CASH.name(), context.getUsdPerPoint(RewardCurrencyType.USD_CASH));
        if (userGoal.getGoalType() == GoalType.PROGRAM_POINTS) {
            userGoal.getPrimaryCurrency().ifPresent(p ->
                cppMap.put(p.name(), resolveCppWithOverride(p, userGoal)));
        }
        for (RewardCurrencyType c : userGoal.getPreferredCurrencies()) {
            cppMap.put(c.name(), resolveCppWithOverride(c, userGoal));
        }
        if (cppUsed != null) cppUsed.forEach((k, v) -> cppMap.put(k.name(), v));

        Map<String, BigDecimal> penalties = new HashMap<>();
        for (RewardCurrencyType c : RewardCurrencyType.values()) {
            BigDecimal p = PenaltyPolicyV1.penalty(c, userGoal);
            if (p.compareTo(BigDecimal.ZERO) > 0 && p.compareTo(BigDecimal.ONE) < 0) {
                penalties.put(c.name(), p);
            }
        }

        return new AssumptionEvidence(
            cppMap,
            penalties,
            Map.of(),
            userGoal.getGoalType(),
            userGoal.getPrimaryCurrency().map(Enum::name).orElse(null)
        );
    }

    private BigDecimal resolveCppWithOverride(RewardCurrencyType type, UserGoal userGoal) {
        Optional<BigDecimal> o = userGoal.getCppOverride(type);
        if (o.isPresent()) {
            BigDecimal v = o.get();
            if (v.compareTo(BigDecimal.ZERO) > 0 && v.compareTo(MAX_OVERRIDE_USD) <= 0) return v;
        }
        return context.getUsdPerPoint(type);
    }
}
