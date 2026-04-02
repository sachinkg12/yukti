package io.yukti.engine.explainability;

import io.yukti.core.explainability.ExplanationGenerator;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.StructuredExplanation;
import io.yukti.explain.core.claims.Claim;
import io.yukti.explain.core.claims.ClaimType;

import java.util.ArrayList;
import java.util.List;

/**
 * ExplanationGenerator that intentionally produces one claim with an invalid citation
 * so that ClaimVerifier rejects it. Used to demonstrate that the verifier is not tautological
 * (naive narrator → 0% verifiability). OCP: new implementation; verifier unchanged.
 */
public final class NaiveNarrator implements ExplanationGenerator {

    private static final String FAKE_ENTITY = "fake-card-naive";
    private static final String FAKE_NUMBER = "99.99";

    private final DeterministicExplanationGeneratorV1 delegate = new DeterministicExplanationGeneratorV1();

    @Override
    public NarrativeExplanation generate(StructuredExplanation structured) {
        NarrativeExplanation valid = delegate.generate(structured);
        List<Claim> claims = new ArrayList<>(valid.claims());
        if (claims.isEmpty()) {
            claims.add(new Claim(
                "naive-invalid",
                ClaimType.THRESHOLD,
                "Fake claim",
                List.of(),
                List.of(FAKE_ENTITY),
                List.of(FAKE_NUMBER)
            ));
        } else {
            Claim first = claims.get(0);
            List<String> badEntities = new ArrayList<>(first.citedEntities());
            badEntities.add(FAKE_ENTITY);
            List<String> badNumbers = new ArrayList<>(first.citedNumbers());
            badNumbers.add(FAKE_NUMBER);
            claims.set(0, new Claim(
                first.claimId(),
                first.claimType(),
                first.text(),
                first.citedEvidenceIds(),
                badEntities,
                badNumbers
            ));
        }
        return delegate.renderFromClaims(structured, claims);
    }

    @Override
    public NarrativeExplanation renderFromClaims(StructuredExplanation structured, List<Claim> claims) {
        return delegate.renderFromClaims(structured, claims);
    }
}
