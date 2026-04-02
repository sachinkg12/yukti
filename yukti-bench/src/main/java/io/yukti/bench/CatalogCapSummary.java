package io.yukti.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.yukti.core.api.Card;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.RewardsRule;
import io.yukti.core.domain.Cap;
import io.yukti.core.domain.Period;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts distinct (category, period, amountUsd) caps from a catalog and writes
 * a deterministic summary to artifacts/bench/v1/catalog-cap-summary.json for visibility.
 */
public final class CatalogCapSummary {

    public static void write(Catalog catalog, Path outputDir) throws Exception {
        Map<String, List<CapEntry>> byCategory = new TreeMap<>();
        for (Card card : catalog.cards()) {
            for (RewardsRule rule : card.rules()) {
                rule.cap().ifPresent(cap -> {
                    double amountUsd = cap.getAmount().getAmount().doubleValue();
                    String period = cap.getPeriod() == Period.ANNUAL ? "ANNUAL" : "MONTHLY";
                    String cat = rule.category().name();
                    byCategory.computeIfAbsent(cat, k -> new ArrayList<>())
                        .add(new CapEntry(rule.category().name(), period, amountUsd));
                });
            }
        }
        // Deduplicate by (category, period, amountUsd) and sort
        Map<String, List<CapEntry>> deduped = new LinkedHashMap<>();
        for (Map.Entry<String, List<CapEntry>> e : byCategory.entrySet()) {
            List<CapEntry> list = e.getValue().stream()
                .distinct()
                .sorted(Comparator.comparing(CapEntry::getPeriod).thenComparingDouble(CapEntry::getAmountUsd))
                .collect(Collectors.toList());
            deduped.put(e.getKey(), list);
        }
        // Build JSON-serializable structure (plain maps/lists) so Jackson does not need to serialize CapEntry
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("catalogVersion", catalog.version());
        Map<String, List<Map<String, Object>>> capsByCategoryJson = new LinkedHashMap<>();
        List<Map<String, Object>> allCaps = new ArrayList<>();
        for (Map.Entry<String, List<CapEntry>> e : deduped.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (CapEntry c : e.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("category", c.category);
                m.put("period", c.period);
                m.put("amountUsd", c.amountUsd);
                list.add(m);
                allCaps.add(m);
            }
            capsByCategoryJson.put(e.getKey(), list);
        }
        root.put("capsByCategory", capsByCategoryJson);
        root.put("allCaps", allCaps);
        Path out = outputDir.resolve("catalog-cap-summary.json");
        Files.createDirectories(out.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(out.toFile(), root);
    }

    private static final class CapEntry {
        final String category;
        final String period;
        final double amountUsd;

        CapEntry(String category, String period, double amountUsd) {
            this.category = category;
            this.period = period;
            this.amountUsd = amountUsd;
        }

        String getPeriod() { return period; }
        double getAmountUsd() { return amountUsd; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CapEntry that = (CapEntry) o;
            return Double.compare(that.amountUsd, amountUsd) == 0
                && Objects.equals(category, that.category)
                && Objects.equals(period, that.period);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, period, amountUsd);
        }
    }
}
