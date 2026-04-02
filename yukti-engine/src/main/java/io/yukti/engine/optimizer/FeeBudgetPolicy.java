package io.yukti.engine.optimizer;

import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.domain.Money;
import io.yukti.core.domain.UserConstraints;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Shared policy enforcing portfolio fee budget: Σ F_i y_i ≤ B.
 * All optimizers must call enforce when proposing a portfolio.
 * Deterministic: same portfolio + constraints + catalog → same result.
 */
public final class FeeBudgetPolicy {

    /**
     * Checks whether the portfolio satisfies the fee budget.
     *
     * @param portfolioCardIds card IDs in the proposed portfolio
     * @param constraints user constraints (maxAnnualFeeUsd is the budget)
     * @param catalog card catalog for annualFeeUsd
     * @return true if Σ F_i ≤ maxAnnualFeeUsd, false otherwise
     */
    public boolean enforce(
        List<String> portfolioCardIds,
        UserConstraints constraints,
        Catalog catalog
    ) {
        Objects.requireNonNull(portfolioCardIds);
        Objects.requireNonNull(constraints);
        Objects.requireNonNull(catalog);

        Map<String, Card> cardById = catalog.cards().stream()
            .collect(Collectors.toMap(Card::id, c -> c));

        Money totalFees = Money.zeroUsd();
        for (String cardId : portfolioCardIds) {
            Card card = cardById.get(cardId);
            if (card != null) {
                totalFees = totalFees.add(card.annualFee());
            }
        }

        return totalFees.getAmount().compareTo(constraints.getMaxAnnualFee().getAmount()) <= 0;
    }
}
