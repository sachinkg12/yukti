# Verification guarantees and limits

This note defines what the four-gate `ClaimVerifier` establishes and what a
passing report must not be taken to mean.

## Guaranteed for a passing structured claim

Given the evidence graph and rule configuration supplied to the verifier:

1. every cited evidence ID resolves to a node in that graph;
2. every declared entity is in the graph-derived entity allowlist;
3. every declared number is in the numeric allowlist or the documented fixed
   small-integer set; and
4. the claim cites the evidence types required for its declared claim type.

In strict mode, a bundle passes only when every claim passes. The rendering
pipeline emits only verified optional claims and otherwise uses its deterministic
fallback.

## Assumptions behind the guarantee

- The claim parser faithfully extracts the entities, numbers, evidence IDs, and
  claim type that the verifier receives.
- The evidence graph accurately represents the upstream optimization result.
- The rule map and schema versions used by producer and verifier agree.
- Digests are checked by a consumer when artifact identity matters.

## Not guaranteed

A pass does not establish:

- full natural-language entailment between prose and evidence;
- that the prose contains no undeclared entity or number missed by a parser;
- that every relevant fact was cited (under-citation or completeness);
- factual correctness of the upstream catalog, valuation, or solver inputs;
- optimality of a result produced by a non-exact optimizer;
- correctness outside the configured claim/evidence vocabulary; or
- cross-domain effectiveness of domain-specific evidence builders and rules.

These are deliberate scope limits. VerityGate is a structural faithfulness
check over a typed claim channel, not a general factuality oracle.

## Versioning obligations

Adding or changing an evidence type, claim type, parser field, number policy, or
type rule requires:

- a versioned schema change;
- producer and verifier tests;
- compatibility documentation; and
- re-evaluation before comparing metrics across versions.

The portable wire contract is documented in
[`evidence-contract-spec.md`](evidence-contract-spec.md).
