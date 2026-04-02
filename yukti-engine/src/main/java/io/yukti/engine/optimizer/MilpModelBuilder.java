package io.yukti.engine.optimizer;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.*;
import io.yukti.engine.config.BenchRunConfig;
import io.yukti.engine.valuation.CppResolver;
import io.yukti.engine.valuation.PenaltyPolicyV1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Constructs the MILP model for credit card portfolio optimization (Paper §2).
 *
 * <p>Formulation matches {@code milp_baseline.py} exactly for cross-validation:
 * <ul>
 *   <li>Decision variables: y[i] ∈ {0,1} card selection; x[i,c] ≥ 0 spend allocation;
 *       w[i,c], z[i,c] piecewise capped/overflow segments.</li>
 *   <li>Constraints: coverage, card limit, fee budget, linking, piecewise.</li>
 *   <li>Objective: maximize (earn + credits − fees) with goal-aware valuation.</li>
 * </ul>
 *
 * <p>Uses OR-Tools CBC solver. Model construction is separated from solving
 * (Builder pattern) and solution extraction for testability.
 */
public final class MilpModelBuilder {

    private static final BigDecimal FALLBACK_CASH = new BigDecimal("0.01");
    private static final BigDecimal FALLBACK_POINTS = BigDecimal.ONE;

    private static volatile boolean nativeLoaded = false;

    private final Catalog catalog;
    private final Map<Category, BigDecimal> annualSpend;
    private final UserGoal goal;
    private final UserConstraints constraints;
    private final CppResolver cppResolver;

    private List<Card> cards;
    private List<String> cardIds;
    private Map<String, Integer> cardIndex;

    // Variable references (stored for solution extraction)
    private MPVariable[] yVars;
    private Map<String, Map<Category, MPVariable>> xVars;
    private Map<String, Map<Category, MPVariable>> wVars;
    private Map<String, Map<Category, MPVariable>> zVars;

    public MilpModelBuilder(Catalog catalog, SpendProfile profile, UserGoal goal, UserConstraints constraints) {
        this.catalog = Objects.requireNonNull(catalog);
        this.goal = Objects.requireNonNull(goal);
        this.constraints = Objects.requireNonNull(constraints);
        this.cppResolver = new CppResolver(catalog);

        this.annualSpend = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            BigDecimal spend = profile.annualSpend(cat).getAmount();
            if (spend.compareTo(BigDecimal.ZERO) > 0) {
                annualSpend.put(cat, spend);
            }
        }
    }

    /**
     * Builds and returns the OR-Tools MILP solver. Call {@link #extractSolution} after solving.
     */
    public MPSolver build() {
        ensureNativeLoaded();

        cards = new ArrayList<>(catalog.cards());
        cardIds = cards.stream().map(Card::id).collect(Collectors.toList());
        cardIndex = new HashMap<>();
        for (int i = 0; i < cardIds.size(); i++) {
            cardIndex.put(cardIds.get(i), i);
        }
        int n = cards.size();

        MPSolver solver = MPSolver.createSolver("CBC");
        if (solver == null) {
            throw new IllegalStateException("OR-Tools CBC solver not available");
        }

        // --- Decision Variables ---
        yVars = new MPVariable[n];
        xVars = new LinkedHashMap<>();
        wVars = new LinkedHashMap<>();
        zVars = new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            Card card = cards.get(i);
            String cid = card.id();
            yVars[i] = solver.makeBoolVar("y_" + i);
            xVars.put(cid, new EnumMap<>(Category.class));

            for (Category cat : annualSpend.keySet()) {
                double sc = annualSpend.get(cat).doubleValue();
                xVars.get(cid).put(cat, solver.makeNumVar(0, sc, "x_" + i + "_" + cat));

                RewardsRule rule = bestRuleForCategory(card, cat);
                if (rule != null && rule.cap().isPresent()) {
                    double capUsd = rule.cap().get().getAmount().getAmount().doubleValue();
                    wVars.computeIfAbsent(cid, k -> new EnumMap<>(Category.class))
                         .put(cat, solver.makeNumVar(0, Math.min(sc, capUsd), "w_" + i + "_" + cat));
                    zVars.computeIfAbsent(cid, k -> new EnumMap<>(Category.class))
                         .put(cat, solver.makeNumVar(0, Math.max(0, sc - capUsd), "z_" + i + "_" + cat));
                }
            }
        }

        // --- Constraints ---

        // C1: Coverage — sum_i x[i,c] = S[c]
        for (Map.Entry<Category, BigDecimal> entry : annualSpend.entrySet()) {
            Category cat = entry.getKey();
            double sc = entry.getValue().doubleValue();
            MPConstraint coverage = solver.makeConstraint(sc, sc, "coverage_" + cat);
            for (int i = 0; i < n; i++) {
                MPVariable xVar = xVars.get(cardIds.get(i)).get(cat);
                if (xVar != null) {
                    coverage.setCoefficient(xVar, 1.0);
                }
            }
        }

        // C2: Card limit — sum_i y[i] <= maxCards
        int maxCards = Math.min(3, constraints.getMaxCards());
        MPConstraint cardLimit = solver.makeConstraint(0, maxCards, "card_limit");
        for (int i = 0; i < n; i++) {
            cardLimit.setCoefficient(yVars[i], 1.0);
        }

        // C3: Fee budget — sum_i F[i] * y[i] <= B
        double feeBudget = constraints.getMaxAnnualFee().getAmount().doubleValue();
        MPConstraint feeConstraint = solver.makeConstraint(0, feeBudget, "fee_budget");
        for (int i = 0; i < n; i++) {
            feeConstraint.setCoefficient(yVars[i], cards.get(i).annualFee().getAmount().doubleValue());
        }

        // C4: Linking — x[i,c] <= S[c] * y[i]
        for (int i = 0; i < n; i++) {
            String cid = cardIds.get(i);
            for (Category cat : annualSpend.keySet()) {
                double sc = annualSpend.get(cat).doubleValue();
                MPVariable xVar = xVars.get(cid).get(cat);
                if (xVar != null) {
                    MPConstraint link = solver.makeConstraint(
                        Double.NEGATIVE_INFINITY, 0, "link_" + i + "_" + cat);
                    link.setCoefficient(xVar, 1.0);
                    link.setCoefficient(yVars[i], -sc);
                }
            }
        }

        // C5: Piecewise for capped rules — w + z = x, w <= cap (via bounds), z >= x - cap
        for (Map.Entry<String, Map<Category, MPVariable>> wEntry : wVars.entrySet()) {
            String cid = wEntry.getKey();
            int idx = cardIndex.get(cid);
            Card card = cards.get(idx);

            for (Map.Entry<Category, MPVariable> catEntry : wEntry.getValue().entrySet()) {
                Category cat = catEntry.getKey();
                MPVariable wVar = catEntry.getValue();
                MPVariable zVar = zVars.get(cid).get(cat);
                MPVariable xVar = xVars.get(cid).get(cat);

                RewardsRule rule = bestRuleForCategory(card, cat);
                double capUsd = rule.cap().get().getAmount().getAmount().doubleValue();

                // w + z = x
                MPConstraint split = solver.makeConstraint(0, 0, "split_" + idx + "_" + cat);
                split.setCoefficient(wVar, 1.0);
                split.setCoefficient(zVar, 1.0);
                split.setCoefficient(xVar, -1.0);

                // w <= cap (already enforced via variable upper bound)

                // z >= x - cap
                MPConstraint overflow = solver.makeConstraint(-capUsd, Double.POSITIVE_INFINITY,
                    "overflow_" + idx + "_" + cat);
                overflow.setCoefficient(zVar, 1.0);
                overflow.setCoefficient(xVar, -1.0);
            }
        }

        // --- Objective: Maximize earn + credits - fees ---
        MPObjective obj = solver.objective();
        obj.setMaximization();

        for (int i = 0; i < n; i++) {
            Card card = cards.get(i);
            String cid = card.id();

            for (Category cat : annualSpend.keySet()) {
                RewardsRule rule = bestRuleForCategory(card, cat);

                if (rule == null) {
                    // No explicit rule: use fallback rate (matches AllocationSolverV1_1).
                    // Most cards earn a base rate on all categories even when unlisted.
                    RewardCurrency currency = primaryCurrency(card);
                    double cpp = effectiveCpp(currency);
                    if (cpp <= 0) continue;
                    boolean isCash = currency.getType() == RewardCurrencyType.USD_CASH;
                    double fallbackRate = isCash ? FALLBACK_CASH.doubleValue() : FALLBACK_POINTS.doubleValue();
                    MPVariable xVar = xVars.get(cid).get(cat);
                    if (xVar != null) {
                        obj.setCoefficient(xVar, isCash ? fallbackRate : fallbackRate * cpp);
                    }
                    continue;
                }

                double cpp = effectiveCpp(rule.currency());
                if (cpp <= 0) continue;

                boolean isCash = rule.currency().getType() == RewardCurrencyType.USD_CASH;

                if (rule.cap().isPresent()) {
                    // Capped rule: earn = m*w + b*z (scaled by cpp for points)
                    MPVariable wVar = wVars.getOrDefault(cid, Map.of()).get(cat);
                    MPVariable zVar = zVars.getOrDefault(cid, Map.of()).get(cat);
                    if (wVar != null && zVar != null) {
                        double m = rule.rate().doubleValue();
                        double b = resolveFallback(card, rule).doubleValue();
                        if (isCash) {
                            obj.setCoefficient(wVar, m);
                            obj.setCoefficient(zVar, b);
                        } else {
                            obj.setCoefficient(wVar, m * cpp);
                            obj.setCoefficient(zVar, b * cpp);
                        }
                    }
                } else {
                    // Uncapped rule: earn = m*x (scaled by cpp for points)
                    MPVariable xVar = xVars.get(cid).get(cat);
                    if (xVar != null) {
                        double m = rule.rate().doubleValue();
                        if (isCash) {
                            obj.setCoefficient(xVar, m);
                        } else {
                            obj.setCoefficient(xVar, m * cpp);
                        }
                    }
                }
            }

            // Credits and fees on y[i]
            double credits = BenchRunConfig.effectiveCredits(card).getAmount().doubleValue();
            double fee = card.annualFee().getAmount().doubleValue();
            obj.setCoefficient(yVars[i], credits - fee);
        }

        return solver;
    }

    /**
     * Extracts the solved variable values into an immutable {@link MilpSolution}.
     * Must be called after {@link MPSolver#solve()} returns OPTIMAL.
     */
    public MilpSolution extractSolution(MPSolver solver, long elapsedMs) {
        List<String> selectedIds = new ArrayList<>();
        for (int i = 0; i < yVars.length; i++) {
            if (yVars[i].solutionValue() > 0.5) {
                selectedIds.add(cardIds.get(i));
            }
        }
        Collections.sort(selectedIds);

        Map<String, Map<Category, Double>> xMatrix = new LinkedHashMap<>();
        Map<String, Map<Category, Double>> wMatrix = new LinkedHashMap<>();
        Map<String, Map<Category, Double>> zMatrix = new LinkedHashMap<>();

        for (String cid : selectedIds) {
            Map<Category, Double> xRow = new EnumMap<>(Category.class);
            for (Category cat : annualSpend.keySet()) {
                MPVariable xVar = xVars.get(cid).get(cat);
                if (xVar != null) {
                    double val = xVar.solutionValue();
                    if (val > 0.001) xRow.put(cat, val);
                }
            }
            if (!xRow.isEmpty()) xMatrix.put(cid, xRow);

            Map<Category, MPVariable> wMap = wVars.getOrDefault(cid, Map.of());
            Map<Category, MPVariable> zMap = zVars.getOrDefault(cid, Map.of());
            Map<Category, Double> wRow = new EnumMap<>(Category.class);
            Map<Category, Double> zRow = new EnumMap<>(Category.class);
            for (Category cat : wMap.keySet()) {
                double wVal = wMap.get(cat).solutionValue();
                double zVal = zMap.get(cat).solutionValue();
                if (wVal > 0.001) wRow.put(cat, wVal);
                if (zVal > 0.001) zRow.put(cat, zVal);
            }
            if (!wRow.isEmpty()) wMatrix.put(cid, wRow);
            if (!zRow.isEmpty()) zMatrix.put(cid, zRow);
        }

        // Compute objective breakdown
        double earnUsd = computeEarn(selectedIds, xMatrix, wMatrix, zMatrix);
        double creditsUsd = 0;
        double feesUsd = 0;
        Map<String, Card> cardById = cards.stream().collect(Collectors.toMap(Card::id, c -> c));
        for (String cid : selectedIds) {
            Card card = cardById.get(cid);
            creditsUsd += BenchRunConfig.effectiveCredits(card).getAmount().doubleValue();
            feesUsd += card.annualFee().getAmount().doubleValue();
        }

        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
            Money.usd(BigDecimal.valueOf(earnUsd).setScale(2, RoundingMode.HALF_UP)),
            Money.usd(BigDecimal.valueOf(creditsUsd).setScale(2, RoundingMode.HALF_UP)),
            Money.usd(BigDecimal.valueOf(feesUsd).setScale(2, RoundingMode.HALF_UP))
        );

        return new MilpSolution(
            selectedIds,
            xMatrix,
            wMatrix,
            zMatrix,
            breakdown,
            solver.objective().value(),
            elapsedMs,
            "OPTIMAL",
            solver.numVariables(),
            solver.numConstraints(),
            (int) Arrays.stream(yVars).count()
        );
    }

    private double computeEarn(List<String> selectedIds,
                               Map<String, Map<Category, Double>> xMatrix,
                               Map<String, Map<Category, Double>> wMatrix,
                               Map<String, Map<Category, Double>> zMatrix) {
        Map<String, Card> cardById = cards.stream().collect(Collectors.toMap(Card::id, c -> c));
        double totalEarn = 0;
        for (String cid : selectedIds) {
            Card card = cardById.get(cid);
            for (Category cat : annualSpend.keySet()) {
                RewardsRule rule = bestRuleForCategory(card, cat);

                if (rule == null) {
                    // Fallback earn for categories without explicit rules
                    RewardCurrency currency = primaryCurrency(card);
                    double cpp = effectiveCpp(currency);
                    if (cpp <= 0) continue;
                    boolean isCash = currency.getType() == RewardCurrencyType.USD_CASH;
                    double fallbackRate = isCash ? FALLBACK_CASH.doubleValue() : FALLBACK_POINTS.doubleValue();
                    double xVal = xMatrix.getOrDefault(cid, Map.of()).getOrDefault(cat, 0.0);
                    totalEarn += isCash ? fallbackRate * xVal : fallbackRate * xVal * cpp;
                    continue;
                }

                double cpp = effectiveCpp(rule.currency());
                if (cpp <= 0) continue;

                boolean isCash = rule.currency().getType() == RewardCurrencyType.USD_CASH;

                if (rule.cap().isPresent()) {
                    double wVal = wMatrix.getOrDefault(cid, Map.of()).getOrDefault(cat, 0.0);
                    double zVal = zMatrix.getOrDefault(cid, Map.of()).getOrDefault(cat, 0.0);
                    double m = rule.rate().doubleValue();
                    double b = resolveFallback(card, rule).doubleValue();
                    if (isCash) {
                        totalEarn += m * wVal + b * zVal;
                    } else {
                        totalEarn += (m * wVal + b * zVal) * cpp;
                    }
                } else {
                    double xVal = xMatrix.getOrDefault(cid, Map.of()).getOrDefault(cat, 0.0);
                    double m = rule.rate().doubleValue();
                    if (isCash) {
                        totalEarn += m * xVal;
                    } else {
                        totalEarn += m * xVal * cpp;
                    }
                }
            }
        }
        return totalEarn;
    }

    /**
     * Select best rule for a card in a category. Matches Python's best_rule_for_category
     * and AllocationSolverV1_1's selectRule: highest rate, prefer capped, lex by id.
     */
    RewardsRule bestRuleForCategory(Card card, Category cat) {
        List<RewardsRule> candidates = new ArrayList<>();
        for (RewardsRule r : card.rules()) {
            if (r.category() == cat) {
                candidates.add(r);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> {
            int cmp = b.rate().compareTo(a.rate());
            if (cmp != 0) return cmp;
            boolean aCapped = a.cap().isPresent();
            boolean bCapped = b.cap().isPresent();
            if (aCapped && !bCapped) return -1;
            if (!aCapped && bCapped) return 1;
            return a.id().compareTo(b.id());
        });
        return candidates.get(0);
    }

    /**
     * Effective cpp (USD per point × penalty). Matches Python's effective_cpp().
     * For USD_CASH: returns the rate directly (cpp=1.0 for CASHBACK, 0.0 otherwise).
     */
    private double effectiveCpp(RewardCurrency currency) {
        BigDecimal usdPerPoint = cppResolver.usdPerPoint(currency, goal);
        return usdPerPoint.doubleValue();
    }

    /**
     * Resolve fallback multiplier for a capped rule. Matches Python's fallback logic:
     * 1. Explicit fallbackMultiplier from rule
     * 2. Same-card, same-category uncapped rule rate
     * 3. Default: 0.01 for cash, 1.0 for points
     */
    BigDecimal resolveFallback(Card card, RewardsRule rule) {
        if (rule.fallbackMultiplier().isPresent()) {
            return rule.fallbackMultiplier().get();
        }
        boolean isCash = rule.currency().getType() == RewardCurrencyType.USD_CASH;
        for (RewardsRule r : card.rules()) {
            if (r.category() == rule.category() && r != rule
                    && r.cap().isEmpty() && r.currency().equals(rule.currency())) {
                return r.rate();
            }
        }
        return isCash ? FALLBACK_CASH : FALLBACK_POINTS;
    }

    /**
     * Returns a card's primary currency (from first rule).
     * Matches AllocationSolverV1_1.cardCurrency().
     */
    private RewardCurrency primaryCurrency(Card card) {
        for (RewardsRule r : card.rules()) {
            return r.currency();
        }
        return new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");
    }

    /** Package-private access to card list for MilpSolutionAnalyzer. */
    List<Card> getCards() {
        return cards;
    }

    Map<Category, BigDecimal> getAnnualSpend() {
        return annualSpend;
    }

    private static synchronized void ensureNativeLoaded() {
        if (!nativeLoaded) {
            Loader.loadNativeLibraries();
            nativeLoaded = true;
        }
    }
}
