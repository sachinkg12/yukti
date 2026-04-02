package io.yukti.engine.optimizer;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import io.yukti.core.api.*;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.evidence.AssumptionEvidence;
import io.yukti.engine.config.BenchRunConfig;
import io.yukti.engine.reward.RewardModelV1;
import io.yukti.engine.valuation.CppResolver;
import io.yukti.engine.valuation.ValuationModelV1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * LP relaxation optimizer: relaxes the binary card selection variables y[i] to
 * continuous [0,1], solves the LP, rounds y >= 0.5 to selected, then runs
 * AllocationSolverV1_1 for the final integer allocation.
 *
 * <p>The LP bound (pre rounding objective) provides an upper bound on the true
 * optimum, useful for optimality gap analysis. The rounded solution is feasible
 * but may be suboptimal relative to the exact MILP.
 *
 * <p>Uses the same formulation as {@link MilpModelBuilder}: coverage, card limit,
 * fee budget, linking, and piecewise cap constraints. The only difference is
 * y[i] in [0,1] instead of y[i] in {0,1}.
 *
 * <p>Deterministic: OR-Tools LP solver (GLOP) is deterministic.
 */
public final class LpRelaxationOptimizer implements Optimizer {

    private static final Logger LOG = Logger.getLogger(LpRelaxationOptimizer.class.getName());
    private static final BigDecimal FALLBACK_CASH = new BigDecimal("0.01");
    private static final BigDecimal FALLBACK_POINTS = BigDecimal.ONE;
    private static final double ROUNDING_THRESHOLD = 0.5;

    private static volatile boolean nativeLoaded = false;

    /** LP bound from last solve (pre rounding). Package private for testing. */
    private volatile double lastLpBound = Double.NaN;

    @Override
    public String id() {
        return "lp-relaxation-v1";
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request, Catalog catalog) {
        SpendProfile profile = request.getSpendProfile();
        UserGoal userGoal = request.getUserGoal();
        UserConstraints constraints = request.getUserConstraints();

        if (profile.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        Map<Category, BigDecimal> annualSpend = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            BigDecimal spend = profile.annualSpend(cat).getAmount();
            if (spend.compareTo(BigDecimal.ZERO) > 0) {
                annualSpend.put(cat, spend);
            }
        }
        if (annualSpend.isEmpty()) {
            return OptimizationResult.empty("No spend provided.");
        }

        ensureNativeLoaded();
        CppResolver cppResolver = new CppResolver(catalog);
        List<Card> cards = new ArrayList<>(catalog.cards());
        List<String> cardIds = cards.stream().map(Card::id).collect(Collectors.toList());
        Map<String, Integer> cardIndex = new HashMap<>();
        for (int i = 0; i < cardIds.size(); i++) {
            cardIndex.put(cardIds.get(i), i);
        }
        int n = cards.size();
        int maxCards = Math.min(3, constraints.getMaxCards());

        // Build LP (same as MILP but with continuous y variables)
        MPSolver solver = MPSolver.createSolver("GLOP");
        if (solver == null) {
            throw new IllegalStateException("OR-Tools GLOP solver not available");
        }

        // Decision variables: y[i] in [0,1] (RELAXED from binary)
        MPVariable[] yVars = new MPVariable[n];
        Map<String, Map<Category, MPVariable>> xVars = new LinkedHashMap<>();
        Map<String, Map<Category, MPVariable>> wVars = new LinkedHashMap<>();
        Map<String, Map<Category, MPVariable>> zVars = new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            Card card = cards.get(i);
            String cid = card.id();
            yVars[i] = solver.makeNumVar(0, 1, "y_" + i);  // RELAXED: continuous [0,1]
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

        // C1: Coverage
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

        // C2: Card limit
        MPConstraint cardLimit = solver.makeConstraint(0, maxCards, "card_limit");
        for (int i = 0; i < n; i++) {
            cardLimit.setCoefficient(yVars[i], 1.0);
        }

        // C3: Fee budget
        double feeBudget = constraints.getMaxAnnualFee().getAmount().doubleValue();
        MPConstraint feeConstraint = solver.makeConstraint(0, feeBudget, "fee_budget");
        for (int i = 0; i < n; i++) {
            feeConstraint.setCoefficient(yVars[i], cards.get(i).annualFee().getAmount().doubleValue());
        }

        // C4: Linking
        for (int i = 0; i < n; i++) {
            String cid = cardIds.get(i);
            for (Category cat : annualSpend.keySet()) {
                double sc = annualSpend.get(cat).doubleValue();
                MPVariable xVar = xVars.get(cid).get(cat);
                if (xVar != null) {
                    MPConstraint link = solver.makeConstraint(Double.NEGATIVE_INFINITY, 0, "link_" + i + "_" + cat);
                    link.setCoefficient(xVar, 1.0);
                    link.setCoefficient(yVars[i], -sc);
                }
            }
        }

        // C5: Piecewise for capped rules
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

                // z >= x - cap
                MPConstraint overflow = solver.makeConstraint(-capUsd, Double.POSITIVE_INFINITY,
                    "overflow_" + idx + "_" + cat);
                overflow.setCoefficient(zVar, 1.0);
                overflow.setCoefficient(xVar, -1.0);
            }
        }

        // Objective: Maximize earn + credits - fees
        MPObjective obj = solver.objective();
        obj.setMaximization();

        for (int i = 0; i < n; i++) {
            Card card = cards.get(i);
            String cid = card.id();

            for (Category cat : annualSpend.keySet()) {
                RewardsRule rule = bestRuleForCategory(card, cat);

                if (rule == null) {
                    RewardCurrency currency = primaryCurrency(card);
                    double cpp = effectiveCpp(cppResolver, currency, userGoal);
                    if (cpp <= 0) continue;
                    boolean isCash = currency.getType() == RewardCurrencyType.USD_CASH;
                    double fallbackRate = isCash ? FALLBACK_CASH.doubleValue() : FALLBACK_POINTS.doubleValue();
                    MPVariable xVar = xVars.get(cid).get(cat);
                    if (xVar != null) {
                        obj.setCoefficient(xVar, isCash ? fallbackRate : fallbackRate * cpp);
                    }
                    continue;
                }

                double cpp = effectiveCpp(cppResolver, rule.currency(), userGoal);
                if (cpp <= 0) continue;
                boolean isCash = rule.currency().getType() == RewardCurrencyType.USD_CASH;

                if (rule.cap().isPresent()) {
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

            double credits = BenchRunConfig.effectiveCredits(card).getAmount().doubleValue();
            double fee = card.annualFee().getAmount().doubleValue();
            obj.setCoefficient(yVars[i], credits - fee);
        }

        // Solve LP relaxation
        long startMs = System.currentTimeMillis();
        MPSolver.ResultStatus status = solver.solve();
        long elapsedMs = System.currentTimeMillis() - startMs;

        if (status != MPSolver.ResultStatus.OPTIMAL) {
            LOG.log(Level.WARNING, "LP relaxation returned {0}; falling back to CapAwareGreedyOptimizer", status);
            return new CapAwareGreedyOptimizer().optimize(request, catalog);
        }

        lastLpBound = solver.objective().value();

        // Round: select cards with y >= 0.5
        List<String> selectedIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (yVars[i].solutionValue() >= ROUNDING_THRESHOLD) {
                selectedIds.add(cardIds.get(i));
            }
        }
        Collections.sort(selectedIds);

        // Enforce fee budget on rounded solution; drop lowest y value cards if over budget
        FeeBudgetPolicy feeBudgetPolicy = new FeeBudgetPolicy();
        if (!feeBudgetPolicy.enforce(selectedIds, constraints, catalog)) {
            // Sort by y value descending, drop cards until budget is satisfied
            List<Map.Entry<String, Double>> yValues = new ArrayList<>();
            for (String cid : selectedIds) {
                int idx = cardIndex.get(cid);
                yValues.add(Map.entry(cid, yVars[idx].solutionValue()));
            }
            yValues.sort(Map.Entry.<String, Double>comparingByValue().reversed());
            List<String> trimmed = new ArrayList<>();
            for (Map.Entry<String, Double> entry : yValues) {
                List<String> candidate = new ArrayList<>(trimmed);
                candidate.add(entry.getKey());
                if (feeBudgetPolicy.enforce(candidate, constraints, catalog)) {
                    trimmed.add(entry.getKey());
                }
            }
            selectedIds = trimmed;
            Collections.sort(selectedIds);
        }

        // Trim to maxCards if rounding selected too many
        if (selectedIds.size() > maxCards) {
            List<Map.Entry<String, Double>> yValues = new ArrayList<>();
            for (String cid : selectedIds) {
                int idx = cardIndex.get(cid);
                yValues.add(Map.entry(cid, yVars[idx].solutionValue()));
            }
            yValues.sort(Map.Entry.<String, Double>comparingByValue().reversed());
            selectedIds = yValues.subList(0, maxCards).stream()
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (selectedIds.isEmpty()) {
            return OptimizationResult.empty("LP relaxation rounding produced empty portfolio.");
        }

        // Run AllocationSolverV1_1 for final integer allocation
        RewardModel rewardModel = new RewardModelV1();
        ValuationModel valuationModel = new ValuationModelV1(catalog);
        AllocationSolverV1_1 allocSolver = new AllocationSolverV1_1();
        AllocationResult allocResult = allocSolver.solve(request, catalog, selectedIds, rewardModel, valuationModel);

        Map<String, Card> cardById = cards.stream().collect(Collectors.toMap(Card::id, c -> c));

        List<EvidenceBlock> finalEvidence = new ArrayList<>(allocResult.evidenceBlocks());
        AssumptionEvidence assumptionEvidence = cppResolver.buildAssumptionEvidence(userGoal, null);
        finalEvidence.add(new EvidenceBlock("ASSUMPTION", "valuation", "valuation", assumptionEvidence.content()));

        ObjectiveBreakdown breakdown = new ObjectiveBreakdown(
                allocResult.earnedValueUsd(),
                allocResult.creditValueUsd(),
                allocResult.feesUsd());

        StringBuilder narrative = new StringBuilder();
        narrative.append("[LP relaxation] Portfolio: ").append(
                selectedIds.stream().map(id -> cardById.get(id).displayName()).collect(Collectors.joining(", ")));
        narrative.append(". Earn: ").append(allocResult.earnedValueUsd());
        narrative.append(", Credits: ").append(allocResult.creditValueUsd());
        narrative.append(", Fees: ").append(allocResult.feesUsd());
        narrative.append(", Net: ").append(breakdown.getNet());
        narrative.append(String.format(". LP bound: $%.2f, solve: %dms.", lastLpBound, elapsedMs));

        List<String> switchingNotes = buildSwitchingNotes(allocResult.allocationPlan(), cardById);

        return new OptimizationResult(
                selectedIds,
                allocResult.allocationByCategory(),
                breakdown,
                finalEvidence,
                narrative.toString(),
                switchingNotes
        );
    }

    /** Returns the LP bound from the most recent solve (pre rounding objective value). */
    public double getLastLpBound() {
        return lastLpBound;
    }

    private RewardsRule bestRuleForCategory(Card card, Category cat) {
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

    private double effectiveCpp(CppResolver cppResolver, RewardCurrency currency, UserGoal goal) {
        return cppResolver.usdPerPoint(currency, goal).doubleValue();
    }

    private BigDecimal resolveFallback(Card card, RewardsRule rule) {
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

    private RewardCurrency primaryCurrency(Card card) {
        for (RewardsRule r : card.rules()) {
            return r.currency();
        }
        return new RewardCurrency(RewardCurrencyType.USD_CASH, "USD");
    }

    private List<String> buildSwitchingNotes(AllocationPlan plan, Map<String, Card> cardById) {
        List<String> notes = new ArrayList<>();
        if (plan == null) return notes;
        for (Map.Entry<Category, List<AllocationSegment>> e : plan.segmentsByCategory().entrySet()) {
            List<AllocationSegment> segs = e.getValue();
            if (segs.size() < 2) continue;
            Category cat = e.getKey();
            String firstCard = segs.get(0).cardId();
            String secondCard = segs.get(1).cardId();
            Card second = cardById.get(secondCard);
            if (second != null) {
                notes.add(String.format("%s: use %s then switch to %s for remainder.",
                        cat.name(), cardById.get(firstCard).displayName(), second.displayName()));
            }
        }
        return notes;
    }

    private static synchronized void ensureNativeLoaded() {
        if (!nativeLoaded) {
            Loader.loadNativeLibraries();
            nativeLoaded = true;
        }
    }
}
