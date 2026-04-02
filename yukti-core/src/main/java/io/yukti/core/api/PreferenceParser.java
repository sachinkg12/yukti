package io.yukti.core.api;

import io.yukti.core.domain.ParsedPreferences;

/**
 * Parses text or structured input into preferences.
 * Phase-1: deterministic; optional AI later.
 */
public interface PreferenceParser {
    String id();
    ParsedPreferences parse(String text) throws Exception;
}
