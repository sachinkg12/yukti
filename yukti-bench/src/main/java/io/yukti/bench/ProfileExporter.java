package io.yukti.bench;

import io.yukti.bench.BenchmarkHarness.BenchProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports RewardsBench v1 profiles to JSON for MILP baseline and other consumers.
 * Single source of truth: mirrors BenchmarkHarness.createProfiles50().
 */
public final class ProfileExporter {
    public static void main(String[] args) throws Exception {
        List<BenchProfile> profiles = createProfiles50();
        List<Map<String, Object>> out = new ArrayList<>();
        for (BenchProfile p : profiles) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", p.id());
            entry.put("monthly", p.monthly());
            Map<String, Double> spend = new LinkedHashMap<>();
            p.spend().forEach((cat, v) -> spend.put(cat.name(), v));
            entry.put("spend", spend);
            out.add(entry);
        }
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path path = args.length > 0 ? Path.of(args[0]) : Path.of("docs/bench/profiles_v1.json");
        Files.createDirectories(path.getParent());
        om.writeValue(path.toFile(), out);
        System.out.println("Wrote " + path + " (" + profiles.size() + " profiles)");
    }

    private static List<BenchProfile> createProfiles50() {
        List<BenchProfile> list = new ArrayList<>(50);
        list.add(new BenchProfile("light", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 3000., io.yukti.core.domain.Category.DINING, 1500., io.yukti.core.domain.Category.GAS, 1200., io.yukti.core.domain.Category.OTHER, 2500.)));
        list.add(new BenchProfile("moderate", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 6000., io.yukti.core.domain.Category.DINING, 3000., io.yukti.core.domain.Category.GAS, 2400., io.yukti.core.domain.Category.TRAVEL, 2000., io.yukti.core.domain.Category.ONLINE, 1200., io.yukti.core.domain.Category.OTHER, 5000.)));
        list.add(new BenchProfile("heavy", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 15000., io.yukti.core.domain.Category.DINING, 6000., io.yukti.core.domain.Category.GAS, 3600., io.yukti.core.domain.Category.TRAVEL, 8000., io.yukti.core.domain.Category.ONLINE, 3000., io.yukti.core.domain.Category.OTHER, 10000.)));
        list.add(new BenchProfile("balanced", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 5000., io.yukti.core.domain.Category.DINING, 2500., io.yukti.core.domain.Category.GAS, 2000., io.yukti.core.domain.Category.TRAVEL, 2500., io.yukti.core.domain.Category.ONLINE, 1500., io.yukti.core.domain.Category.OTHER, 4000.)));
        list.add(new BenchProfile("minimal", false, Map.of(io.yukti.core.domain.Category.OTHER, 5000.)));
        list.add(new BenchProfile("high-net-worth", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 24000., io.yukti.core.domain.Category.DINING, 12000., io.yukti.core.domain.Category.GAS, 6000., io.yukti.core.domain.Category.TRAVEL, 20000., io.yukti.core.domain.Category.ONLINE, 6000., io.yukti.core.domain.Category.OTHER, 20000.)));
        list.add(new BenchProfile("grocery-heavy", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 12000., io.yukti.core.domain.Category.DINING, 2000., io.yukti.core.domain.Category.OTHER, 3000.)));
        list.add(new BenchProfile("dining-heavy", false, Map.of(io.yukti.core.domain.Category.DINING, 10000., io.yukti.core.domain.Category.GROCERIES, 4000., io.yukti.core.domain.Category.OTHER, 2000.)));
        list.add(new BenchProfile("travel-heavy", false, Map.of(io.yukti.core.domain.Category.TRAVEL, 15000., io.yukti.core.domain.Category.DINING, 4000., io.yukti.core.domain.Category.OTHER, 5000.)));
        list.add(new BenchProfile("gas-heavy", false, Map.of(io.yukti.core.domain.Category.GAS, 6000., io.yukti.core.domain.Category.GROCERIES, 3000., io.yukti.core.domain.Category.OTHER, 3000.)));
        list.add(new BenchProfile("online-heavy", false, Map.of(io.yukti.core.domain.Category.ONLINE, 8000., io.yukti.core.domain.Category.GROCERIES, 3000., io.yukti.core.domain.Category.OTHER, 4000.)));
        list.add(new BenchProfile("grocery-heavy-other-low", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 12000., io.yukti.core.domain.Category.DINING, 2000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("grocery-heavy-other-med", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 12000., io.yukti.core.domain.Category.DINING, 2000., io.yukti.core.domain.Category.OTHER, 5000.)));
        list.add(new BenchProfile("grocery-heavy-other-high", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 12000., io.yukti.core.domain.Category.DINING, 2000., io.yukti.core.domain.Category.OTHER, 12000.)));
        list.add(new BenchProfile("dining-heavy-other-low", false, Map.of(io.yukti.core.domain.Category.DINING, 10000., io.yukti.core.domain.Category.GROCERIES, 4000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("travel-heavy-other-low", false, Map.of(io.yukti.core.domain.Category.TRAVEL, 15000., io.yukti.core.domain.Category.DINING, 4000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-below", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 5900., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-at", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 6000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-above", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 6100., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap6000-far", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 12000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap12000-below", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 11900., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap12000-at", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 12000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap12000-above", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 12100., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap12000-far", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 24000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap25000-below", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 24900., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap25000-at", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 25000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap25000-above", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 25100., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap25000-far", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 50000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap1500-below", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 1400., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap1500-at", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 1500., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap1500-above", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 1600., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("groceries-cap1500-far", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 3000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("online-cap2000-below", false, Map.of(io.yukti.core.domain.Category.ONLINE, 1900., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("online-cap2000-at", false, Map.of(io.yukti.core.domain.Category.ONLINE, 2000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("online-cap2000-above", false, Map.of(io.yukti.core.domain.Category.ONLINE, 2100., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("online-cap2000-far", false, Map.of(io.yukti.core.domain.Category.ONLINE, 4000., io.yukti.core.domain.Category.OTHER, 1000.)));
        list.add(new BenchProfile("aa-sweetspot", false, Map.of(io.yukti.core.domain.Category.TRAVEL, 12000., io.yukti.core.domain.Category.DINING, 6000., io.yukti.core.domain.Category.GROCERIES, 3000., io.yukti.core.domain.Category.OTHER, 2000.)));
        list.add(new BenchProfile("flex-sweetspot", false, Map.of(io.yukti.core.domain.Category.ONLINE, 8000., io.yukti.core.domain.Category.TRAVEL, 6000., io.yukti.core.domain.Category.DINING, 4000., io.yukti.core.domain.Category.OTHER, 3000.)));
        list.add(new BenchProfile("cashback-sweetspot", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 5000., io.yukti.core.domain.Category.DINING, 3000., io.yukti.core.domain.Category.GAS, 2000., io.yukti.core.domain.Category.TRAVEL, 2000., io.yukti.core.domain.Category.ONLINE, 1500., io.yukti.core.domain.Category.OTHER, 4000.)));
        list.add(new BenchProfile("fee-sensitive", false, Map.of(io.yukti.core.domain.Category.GROCERIES, 6000., io.yukti.core.domain.Category.DINING, 3000., io.yukti.core.domain.Category.OTHER, 5000.)));
        list.add(new BenchProfile("monthly-light", true, Map.of(io.yukti.core.domain.Category.GROCERIES, 400., io.yukti.core.domain.Category.DINING, 200., io.yukti.core.domain.Category.OTHER, 300.)));
        list.add(new BenchProfile("monthly-moderate", true, Map.of(io.yukti.core.domain.Category.GROCERIES, 600., io.yukti.core.domain.Category.DINING, 350., io.yukti.core.domain.Category.GAS, 250., io.yukti.core.domain.Category.TRAVEL, 200., io.yukti.core.domain.Category.ONLINE, 100., io.yukti.core.domain.Category.OTHER, 500.)));
        list.add(new BenchProfile("monthly-grocery-cap-below", true, Map.of(io.yukti.core.domain.Category.GROCERIES, 408., io.yukti.core.domain.Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-grocery-cap-at", true, Map.of(io.yukti.core.domain.Category.GROCERIES, 500., io.yukti.core.domain.Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-grocery-cap-above", true, Map.of(io.yukti.core.domain.Category.GROCERIES, 508., io.yukti.core.domain.Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-grocery-cap-far", true, Map.of(io.yukti.core.domain.Category.GROCERIES, 1000., io.yukti.core.domain.Category.OTHER, 100.)));
        list.add(new BenchProfile("monthly-travel", true, Map.of(io.yukti.core.domain.Category.TRAVEL, 1000., io.yukti.core.domain.Category.OTHER, 200.)));
        list.add(new BenchProfile("monthly-dining", true, Map.of(io.yukti.core.domain.Category.DINING, 500., io.yukti.core.domain.Category.OTHER, 200.)));
        list.add(new BenchProfile("monthly-online", true, Map.of(io.yukti.core.domain.Category.ONLINE, 400., io.yukti.core.domain.Category.OTHER, 150.)));
        list.add(new BenchProfile("monthly-commuter", true, Map.of(io.yukti.core.domain.Category.GAS, 350., io.yukti.core.domain.Category.DINING, 150., io.yukti.core.domain.Category.OTHER, 200.)));
        return list;
    }
}
