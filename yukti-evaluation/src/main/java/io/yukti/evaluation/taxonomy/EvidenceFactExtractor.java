package io.yukti.evaluation.taxonomy;

import io.yukti.core.domain.Category;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.core.explainability.evidence.EvidenceBlock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts the set of allowed numbers and entities from a {@link StructuredExplanation}
 * for use by hallucination detectors.
 *
 * <p>The allowed sets include:
 * <ul>
 *   <li>All numbers appearing in the breakdown (earn, credits, fees, net).</li>
 *   <li>All numbers appearing in any evidence block's content string.</li>
 *   <li>All card IDs in the portfolio and in any evidence block.</li>
 *   <li>All spend categories (six fixed values).</li>
 *   <li>The primary currency, if any.</li>
 * </ul>
 *
 * <p>This is intentionally union-of-everything-seen-in-evidence: false positives in
 * the hallucination report are worse than false negatives, because a false positive
 * makes a real claim look fabricated.
 */
public final class EvidenceFactExtractor {

    private static final Set<String> KNOWN_CATEGORIES =
        Arrays.stream(Category.values()).map(Enum::name).collect(Collectors.toSet());

    private EvidenceFactExtractor() {}

    /** Build the union of allowed numeric values, in canonical normalized form. */
    public static Set<String> allowedNumbers(StructuredExplanation evidence) {
        Set<String> out = new HashSet<>();
        // Breakdown values
        var b = evidence.breakdown();
        addIfNotNull(out, NumberNormalizer.normalize(b.netValueUsd().toPlainString()));
        addIfNotNull(out, NumberNormalizer.normalize(b.totalEarnValueUsd().toPlainString()));
        addIfNotNull(out, NumberNormalizer.normalize(b.totalCreditValueUsd().toPlainString()));
        addIfNotNull(out, NumberNormalizer.normalize(b.totalFeesUsd().toPlainString()));
        // Numbers embedded in evidence block content strings
        for (EvidenceBlock block : evidence.evidenceBlocks()) {
            String content = block.content();
            out.addAll(NumberNormalizer.extractAll(content));
        }
        return out;
    }

    /** Build the union of allowed entities (card IDs, category names, currency codes). */
    public static Set<String> allowedEntities(StructuredExplanation evidence) {
        Set<String> out = new HashSet<>();
        // Portfolio card IDs
        out.addAll(evidence.portfolioCardIds());
        // Categories
        out.addAll(KNOWN_CATEGORIES);
        // Primary currency
        if (evidence.primaryCurrencyOrNull() != null) {
            out.add(evidence.primaryCurrencyOrNull());
        }
        // Card IDs and categories from evidence blocks
        for (EvidenceBlock block : evidence.evidenceBlocks()) {
            if (block.cardId() != null && !block.cardId().isBlank()) {
                out.add(block.cardId());
            }
            if (block.category() != null && !block.category().isBlank()) {
                out.add(block.category());
            }
        }
        return out;
    }

    /** Whether the cited string matches any allowed number after normalization. */
    public static boolean isAllowedNumber(String cited, Set<String> allowed) {
        if (cited == null) return false;
        String canon = NumberNormalizer.normalize(cited);
        if (canon == null) return false;
        return allowed.contains(canon);
    }

    private static void addIfNotNull(Set<String> out, String value) {
        if (value != null) out.add(value);
    }

    /** Returns the list of known spend category names. */
    public static Set<String> knownCategories() {
        return Set.copyOf(KNOWN_CATEGORIES);
    }
}
