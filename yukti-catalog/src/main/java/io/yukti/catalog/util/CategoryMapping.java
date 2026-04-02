package io.yukti.catalog.util;

import io.yukti.core.domain.Category;

import java.util.Map;

/**
 * Maps JSON category strings to Category. Unknown categories map to OTHER.
 */
public final class CategoryMapping {
    private static final Map<String, Category> JSON_TO_CATEGORY = Map.of(
        "GROCERIES", Category.GROCERIES,
        "DINING", Category.DINING,
        "GAS", Category.GAS,
        "TRAVEL", Category.TRAVEL,
        "ONLINE", Category.ONLINE,
        "OTHER", Category.OTHER,
        "DRUGSTORES", Category.OTHER,
        "TRANSIT", Category.TRAVEL,
        "ENTERTAINMENT", Category.OTHER,
        "STREAMING", Category.ONLINE
    );

    public static Category fromJson(String jsonCategory) {
        return JSON_TO_CATEGORY.getOrDefault(jsonCategory.toUpperCase(), Category.OTHER);
    }

    /** True if the string is a canonical category enum name (used for strict validation). */
    public static boolean isValidCategoryName(String name) {
        if (name == null || name.isBlank()) return false;
        String u = name.toUpperCase();
        for (Category c : Category.values()) {
            if (c.name().equals(u)) return true;
        }
        return false;
    }
}
