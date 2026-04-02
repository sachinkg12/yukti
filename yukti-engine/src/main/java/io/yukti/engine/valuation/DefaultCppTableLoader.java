package io.yukti.engine.valuation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.core.domain.RewardCurrencyType;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads default CPP table from default-cpp.v1.json.
 * Validates version "cpp.v1", all values > 0, USD_CASH = 1.0.
 * Units: USD per point (0.013 = 1.3 cents per point).
 */
public final class DefaultCppTableLoader {

    private static final String RESOURCE_V1 = "valuation/default-cpp.v1.json";
    private static final String VERSION = "cpp.v1";
    private static final BigDecimal USD_CASH_REQUIRED = new BigDecimal("1.000");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");

    /**
     * Global CPP scale factor for sensitivity analysis.
     * Only applies to non-cash currencies. Default 1.0 (no scaling).
     * Set via {@code -Dio.yukti.bench.cppScale=0.8} or programmatically.
     */
    private static volatile double cppScaleFactor = 1.0;

    public static void setCppScaleFactor(double factor) {
        if (factor <= 0) throw new IllegalArgumentException("cppScaleFactor must be > 0");
        cppScaleFactor = factor;
    }

    public static double getCppScaleFactor() {
        return cppScaleFactor;
    }

    public static DefaultCppTable load() {
        try (InputStream in = DefaultCppTableLoader.class.getClassLoader().getResourceAsStream(RESOURCE_V1)) {
            if (in == null) return DefaultCppTable.fallbackDefaults();
            JsonNode root = new ObjectMapper().readTree(in);
            String version = root.path("version").asText(null);
            if (!VERSION.equals(version)) {
                throw new IllegalArgumentException("Unsupported CPP config version: " + version + ", expected " + VERSION);
            }
            JsonNode usdPerPoint = root.path("usdPerPoint");
            Map<RewardCurrencyType, BigDecimal> m = new EnumMap<>(RewardCurrencyType.class);
            for (RewardCurrencyType t : RewardCurrencyType.values()) {
                if (usdPerPoint.has(t.name())) {
                    BigDecimal v = BigDecimal.valueOf(usdPerPoint.path(t.name()).asDouble());
                    if (v.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("usdPerPoint for " + t + " must be > 0, got " + v);
                    }
                    m.put(t, v);
                }
            }
            if (!m.containsKey(RewardCurrencyType.USD_CASH)) {
                throw new IllegalArgumentException("usdPerPoint must include USD_CASH");
            }
            BigDecimal usdCash = m.get(RewardCurrencyType.USD_CASH);
            if (usdCash.subtract(USD_CASH_REQUIRED).abs().compareTo(TOLERANCE) > 0) {
                throw new IllegalArgumentException("USD_CASH must equal 1.000, got " + usdCash);
            }
            // Apply CPP scale factor for sensitivity analysis (non-cash currencies only)
            if (cppScaleFactor != 1.0) {
                Map<RewardCurrencyType, BigDecimal> scaled = new EnumMap<>(RewardCurrencyType.class);
                for (Map.Entry<RewardCurrencyType, BigDecimal> e : m.entrySet()) {
                    if (e.getKey() == RewardCurrencyType.USD_CASH) {
                        scaled.put(e.getKey(), e.getValue()); // Cash is always 1.0
                    } else {
                        scaled.put(e.getKey(), e.getValue().multiply(BigDecimal.valueOf(cppScaleFactor)));
                    }
                }
                return new DefaultCppTable(scaled);
            }
            return new DefaultCppTable(m);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return DefaultCppTable.fallbackDefaults();
        }
    }
}
