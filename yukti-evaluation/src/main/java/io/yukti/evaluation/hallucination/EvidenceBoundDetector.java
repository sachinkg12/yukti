package io.yukti.evaluation.hallucination;

import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.evaluation.taxonomy.FailureCategory;
import io.yukti.evaluation.taxonomy.FailureCategoryClassifier;
import io.yukti.evaluation.taxonomy.RuleBasedFailureClassifier;
import io.yukti.explain.core.claims.Claim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Default detector. Delegates per claim classification to a {@link FailureCategoryClassifier}
 * and aggregates the result into a {@link HallucinationReport}.
 */
public final class EvidenceBoundDetector implements HallucinationDetector {

    private final FailureCategoryClassifier classifier;

    public EvidenceBoundDetector() {
        this(new RuleBasedFailureClassifier());
    }

    public EvidenceBoundDetector(FailureCategoryClassifier classifier) {
        if (classifier == null) throw new IllegalArgumentException("classifier must be non-null");
        this.classifier = classifier;
    }

    @Override
    public HallucinationReport detect(StructuredExplanation evidence, List<Claim> claims) {
        if (claims == null) claims = List.of();
        int totalClaims = claims.size();
        int hallucinated = 0;
        Map<FailureCategory, Integer> counts = new EnumMap<>(FailureCategory.class);
        List<HallucinationInstance> instances = new ArrayList<>();

        for (Claim claim : claims) {
            List<FailureCategory> categories = classifier.classify(claim, evidence);
            if (categories.isEmpty()) continue;
            hallucinated++;
            for (FailureCategory c : categories) {
                counts.merge(c, 1, Integer::sum);
                instances.add(new HallucinationInstance(
                    claim.claimId() != null ? claim.claimId() : "",
                    c,
                    summarizeOffender(claim, c),
                    claim.text() != null ? claim.text() : ""
                ));
            }
        }
        return new HallucinationReport(totalClaims, hallucinated, counts, instances);
    }

    private static String summarizeOffender(Claim claim, FailureCategory category) {
        return switch (category) {
            case EVIDENCE_FABRICATION -> String.join(",", claim.citedEvidenceIds());
            case ENTITY_FABRICATION, CROSS_CURRENCY_CONFUSION -> String.join(",", claim.citedEntities());
            case VALUE_DISTORTION -> String.join(",", claim.citedNumbers());
            case TYPE_MISMATCH -> claim.claimType() != null ? claim.claimType().name() : "";
            case CAUSAL_MISATTRIBUTION, SCOPE_VIOLATION -> claim.text() != null ? claim.text() : "";
        };
    }
}
