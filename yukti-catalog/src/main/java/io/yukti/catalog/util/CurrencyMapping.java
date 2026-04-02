package io.yukti.catalog.util;

import io.yukti.core.domain.RewardCurrency;
import io.yukti.core.domain.RewardCurrencyType;

import java.util.Map;

/**
 * Maps JSON currency IDs to RewardCurrency.
 */
public final class CurrencyMapping {
    private static final Map<String, RewardCurrencyType> ID_TO_TYPE = Map.ofEntries(
        Map.entry("USD", RewardCurrencyType.USD_CASH),
        Map.entry("USD_CASH", RewardCurrencyType.USD_CASH),
        Map.entry("CHASE_UR", RewardCurrencyType.BANK_UR),
        Map.entry("BANK_UR", RewardCurrencyType.BANK_UR),
        Map.entry("AMEX_MR", RewardCurrencyType.BANK_MR),
        Map.entry("BANK_MR", RewardCurrencyType.BANK_MR),
        Map.entry("CITI_TYP", RewardCurrencyType.BANK_TY),
        Map.entry("BANK_TY", RewardCurrencyType.BANK_TY),
        Map.entry("CAP1_VENTURE", RewardCurrencyType.BANK_C1),
        Map.entry("BANK_C1", RewardCurrencyType.BANK_C1),
        Map.entry("AA_MILES", RewardCurrencyType.AA_MILES),
        Map.entry("AVIOS", RewardCurrencyType.AVIOS),
        Map.entry("UNITED_MILES", RewardCurrencyType.UNITED_MILES),
        Map.entry("DELTA_SKYMILES", RewardCurrencyType.DELTA_SKYMILES),
        Map.entry("MARRIOTT_POINTS", RewardCurrencyType.MARRIOTT_POINTS),
        Map.entry("HILTON_POINTS", RewardCurrencyType.HILTON_POINTS),
        Map.entry("HYATT_POINTS", RewardCurrencyType.HYATT_POINTS),
        Map.entry("SOUTHWEST_RR", RewardCurrencyType.SOUTHWEST_RR),
        Map.entry("IHG_POINTS", RewardCurrencyType.IHG_POINTS),
        Map.entry("WYNDHAM_POINTS", RewardCurrencyType.WYNDHAM_POINTS),
        Map.entry("JETBLUE_POINTS", RewardCurrencyType.JETBLUE_POINTS),
        Map.entry("BILT_POINTS", RewardCurrencyType.BILT_POINTS),
        Map.entry("AEROPLAN", RewardCurrencyType.AEROPLAN),
        Map.entry("WF_REWARDS", RewardCurrencyType.WF_REWARDS)
    );

    public static RewardCurrency fromJsonId(String jsonId) {
        RewardCurrencyType type = ID_TO_TYPE.getOrDefault(jsonId, RewardCurrencyType.USD_CASH);
        return new RewardCurrency(type, jsonId);
    }

    /** Returns true if the JSON id is a known reward currency. */
    public static boolean isValidCurrency(String jsonId) {
        return jsonId != null && ID_TO_TYPE.containsKey(jsonId);
    }
}
