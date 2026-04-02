package io.yukti.api.dto.v1;

import java.math.BigDecimal;
import java.util.List;

/**
 * API v1 catalog cards response.
 */
public record CardsResponse(
    String catalogVersion,
    List<CardMetadataDto> cards
) {
    public record CardMetadataDto(
        String cardId,
        String issuer,
        String name,
        String segment,
        BigDecimal annualFeeUsd,
        String rewardCurrency
    ) {}
}
