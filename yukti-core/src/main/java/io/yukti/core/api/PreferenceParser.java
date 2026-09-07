package io.yukti.core.api;

import io.yukti.core.domain.ParsedPreferences;

/**
 * Parses text or structured input into preferences. Model-assisted goal
 * interpretation is an optional adapter and preserves this domain boundary.
 */
public interface PreferenceParser {
    String id();
    ParsedPreferences parse(String text) throws Exception;
}
