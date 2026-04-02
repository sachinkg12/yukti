package io.yukti.core.api;

import io.yukti.core.domain.EvidenceBlock;

import java.util.List;

/**
 * Generates narrative from evidence blocks. Pluggable by style.
 */
public interface ExplanationGenerator {
    String id();
    String generate(List<EvidenceBlock> evidence);
}
