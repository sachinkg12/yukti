package io.yukti.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yukti.catalog.dsl.CardDefinition;
import io.yukti.catalog.dsl.CatalogIndex;
import io.yukti.catalog.dsl.CreditRule;
import io.yukti.catalog.dsl.EarnRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the legacy single-file catalog (catalog-v1.json) from the v1
 * index + per-card JSON so the bundle stays in sync with the source-backed catalog.
 */
public final class CatalogBundleGenerator {

    private static final ObjectMapper OM = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** DSL rewardCurrencyType -> legacy bundle currency id (for rules and valuationPolicies). */
    private static final Map<String, String> CURRENCY_TO_LEGACY = Map.ofEntries(
        Map.entry("USD_CASH", "USD"),
        Map.entry("BANK_UR", "CHASE_UR"),
        Map.entry("BANK_MR", "AMEX_MR"),
        Map.entry("BANK_TY", "CITI_TYP"),
        Map.entry("BANK_C1", "CAP1_VENTURE"),
        Map.entry("AA_MILES", "AA_MILES"),
        Map.entry("AVIOS", "AVIOS"),
        Map.entry("UNITED_MILES", "UNITED_MILES"),
        Map.entry("DELTA_SKYMILES", "DELTA_SKYMILES"),
        Map.entry("MARRIOTT_POINTS", "MARRIOTT_POINTS"),
        Map.entry("HILTON_POINTS", "HILTON_POINTS"),
        Map.entry("HYATT_POINTS", "HYATT_POINTS"),
        Map.entry("SOUTHWEST_RR", "SOUTHWEST_RR"),
        Map.entry("IHG_POINTS", "IHG_POINTS"),
        Map.entry("WYNDHAM_POINTS", "WYNDHAM_POINTS"),
        Map.entry("JETBLUE_POINTS", "JETBLUE_POINTS"),
        Map.entry("BILT_POINTS", "BILT_POINTS"),
        Map.entry("AEROPLAN", "AEROPLAN"),
        Map.entry("WF_REWARDS", "WF_REWARDS")
    );

    /**
     * Reads catalog/v1 (index + cards), converts to legacy format, writes catalog-v1.json.
     *
     * @param catalogV1Dir directory containing index.json and cards/
     * @param outputPath path to write catalog-v1.json (e.g. catalog/catalog-v1.json)
     */
    public static void generate(Path catalogV1Dir, Path outputPath) throws Exception {
        Path indexFile = catalogV1Dir.resolve("index.json");
        if (!Files.isRegularFile(indexFile)) {
            throw new IllegalArgumentException("Index not found: " + indexFile);
        }

        CatalogIndex index = OM.readValue(Files.readString(indexFile), CatalogIndex.class);
        CardDefinitionParser parser = new CardDefinitionParser();
        List<Map<String, Object>> cards = new ArrayList<>();

        for (CatalogIndex.IndexEntry entry : index.entries()) {
            String relPath = entry.path() != null && !entry.path().isBlank()
                ? entry.path() : "cards/" + entry.cardId() + ".json";
            Path cardFile = catalogV1Dir.resolve(relPath);
            if (!Files.isRegularFile(cardFile)) {
                throw new IllegalArgumentException("Card file not found: " + cardFile);
            }
            CardDefinition def = parser.parse(Files.readString(cardFile));
            if (!entry.cardId().equals(def.cardId())) {
                throw new IllegalStateException("CardId mismatch: index " + entry.cardId() + " vs file " + def.cardId());
            }
            cards.add(toLegacyCard(def));
        }

        List<Map<String, Object>> valuationPolicies = legacyValuationPolicies();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "1.0");
        root.put("valuationPolicies", valuationPolicies);
        root.put("cards", cards);

        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        OM.writeValue(outputPath.toFile(), root);
    }

    private static List<Map<String, Object>> legacyValuationPolicies() {
        return List.of(
            Map.of("id", "usd-cashback", "currency", "USD", "goal", "CASHBACK", "centsPerUnit", 100),
            Map.of("id", "chase-ur-flex", "currency", "CHASE_UR", "goal", "FLEX_POINTS", "centsPerUnit", 1.25),
            Map.of("id", "amex-mr-flex", "currency", "AMEX_MR", "goal", "FLEX_POINTS", "centsPerUnit", 1.2),
            Map.of("id", "citi-ty-flex", "currency", "CITI_TYP", "goal", "FLEX_POINTS", "centsPerUnit", 1.25),
            Map.of("id", "cap1-venture-flex", "currency", "CAP1_VENTURE", "goal", "FLEX_POINTS", "centsPerUnit", 1.0),
            Map.of("id", "avios-program", "currency", "AVIOS", "goal", "PROGRAM_POINTS", "centsPerUnit", 1.3)
        );
    }

    private static Map<String, Object> toLegacyCard(CardDefinition def) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", def.cardId());
        card.put("displayName", def.name());
        card.put("issuer", def.issuer());
        card.put("annualFee", def.annualFeeUsd());
        card.put("statementCreditsAnnual", sumCreditsAnnual(def));
        String legacyCurrency = CURRENCY_TO_LEGACY.getOrDefault(def.rewardCurrencyType(), "USD");

        // Paper §3.1 DSL format: multiplier, cap (object or null), fallbackMultiplier
        List<Map<String, Object>> rules = new ArrayList<>();
        for (EarnRule r : def.earnRules()) {
            if (!"EARN_MULTIPLIER".equals(r.ruleType())) continue;
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("category", r.category());
            rule.put("multiplier", r.multiplier() != null ? r.multiplier().doubleValue() : 0);
            if (r.cap() != null && r.cap().amountUsd() > 0) {
                rule.put("cap", Map.of("amountUsd", r.cap().amountUsd(), "period", r.cap().period()));
            } else {
                rule.put("cap", (Object) null);
            }
            rule.put("fallbackMultiplier", r.fallbackMultiplier() != null ? r.fallbackMultiplier().doubleValue() : null);
            rule.put("currency", legacyCurrency);
            rules.add(rule);
        }
        card.put("rules", rules);
        return card;
    }

    private static double sumCreditsAnnual(CardDefinition def) {
        double total = 0;
        for (CreditRule c : def.credits()) {
            if (!"CREDIT".equals(c.ruleType())) continue;
            double ev = c.amountUsd() * c.assumedUtilizationDefault();
            if ("MONTHLY".equalsIgnoreCase(c.period())) {
                ev *= 12;
            }
            total += ev;
        }
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: CatalogBundleGenerator <catalogV1Dir> <outputPath>");
            System.exit(1);
        }
        Path catalogV1Dir = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);
        generate(catalogV1Dir, outputPath);
        System.out.println("Generated " + outputPath);
    }
}
