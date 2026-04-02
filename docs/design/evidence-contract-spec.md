# Evidence and Claim Contract (Portable Protocol)

This document defines the **evidence bundle** and **claim bundle** formats used for verifiable explanations. External systems can produce evidence and claims conforming to these schemas and use the validator (`scripts/validate_evidence_claims.py`) to check that all claims pass the verification checklist. The Java `ClaimVerifier` implements the same rules; this spec is the portable contract.

---

## 1. Evidence bundle

An **evidence bundle** is a JSON object that describes the evidence graph (nodes) and the allowlists used during verification.

### 1.1 Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `nodes` | array | Yes | List of evidence nodes. Each node: `evidenceId`, `type`, `cardId`, `category`, `content`. |
| `allowedEntities` | array of string | No | If absent, derived from nodes (all `cardId`, non-empty `category`, plus any explicitly listed). |
| `allowedNumbers` | array of string | No | If absent, derived from node `content` (numbers matched by `\d+\.?\d*`) and any explicitly listed. |

**Canonicalization:** Evidence IDs are stable per (type, cardId, category, content) in the Java pipeline (EvidenceIdHelper). For portable bundles, provide explicit `evidenceId` per node. Node order does not affect verification; graph digest (if computed) uses sorted nodes.

### 1.2 Evidence node

| Field | Type | Description |
|-------|------|-------------|
| `evidenceId` | string | Unique id for this node (referenced by claims). |
| `type` | string | One of: WINNER_BY_CATEGORY, CAP_HIT, FEE_BREAK_EVEN, RESULT_BREAKDOWN, PORTFOLIO_SUMMARY, ASSUMPTION, ALLOCATION_SEGMENT, RESULT. |
| `cardId` | string | Card identifier (e.g. "amex-bcp"). |
| `category` | string | Category (e.g. "GROCERIES") or empty. |
| `content` | string | Human-readable or structured content; numbers in content may be used to build allowedNumbers. |

### 1.3 Evidence block types (reference)

| Type | Typical use |
|------|-------------|
| WINNER_BY_CATEGORY | Winner vs runner-up per category. |
| CAP_HIT | Cap hit and segment boundary. |
| FEE_BREAK_EVEN | Fee justification. |
| RESULT_BREAKDOWN | Earn, credits, fees, net. |
| PORTFOLIO_SUMMARY | Card list, total fee. |
| ASSUMPTION | Goal/valuation assumption. |
| ALLOCATION_SEGMENT | Category–card allocation. |
| RESULT | Virtual root (optional in bundle). |

---

## 2. Claim bundle

A **claim bundle** is a JSON object with a list of claims.

### 2.1 Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `claims` | array | Yes | List of claim objects. |

### 2.2 Claim

| Field | Type | Description |
|-------|------|-------------|
| `claimId` | string | Unique id for the claim. |
| `claimType` | string | One of: COMPARISON, THRESHOLD, ALLOCATION, ASSUMPTION, FEE_JUSTIFICATION, CAP_SWITCH. |
| `text` | string | Human-readable claim text. |
| `citedEvidenceIds` | array of string | Evidence node IDs this claim cites. |
| `citedEntities` | array of string | Entity identifiers (cardIds, categories) mentioned. |
| `citedNumbers` | array of string | Numeric values mentioned. |

---

## 3. Verification checklist

The validator (and ClaimVerifier) apply:

1. **Evidence ID check:** Every `citedEvidenceId` must appear in the evidence graph (nodes).
2. **Allowlist – entities:** Every `citedEntity` (non-empty) must be in `allowedEntities`.
3. **Allowlist – numbers:** Every `citedNumber` (non-empty) must be in `allowedNumbers` or in the fixed allowlist `{"0","1","2","3"}`.
4. **Type-specific rules (at least one):** For the claim’s `claimType`, the claim must cite at least one node of each required type:
   - COMPARISON → WINNER_BY_CATEGORY  
   - FEE_JUSTIFICATION → FEE_BREAK_EVEN  
   - ASSUMPTION → ASSUMPTION  
   - CAP_SWITCH → CAP_HIT  
   - THRESHOLD, ALLOCATION → no requirement  
5. **Type-specific rules (all of):** For CAP_SWITCH, if the graph contains ALLOCATION_SEGMENT, the claim must cite at least one ALLOCATION_SEGMENT node in addition to CAP_HIT.
6. **Strict mode:** If any claim fails, the whole verification fails.

**Binding (optional, not in v1):** A future extension may require that every cited entity/number appear in at least one of the *cited* evidence nodes. The current contract does not enforce binding.

---

## 4. JSON Schemas

- **Evidence block (one node):** `docs/design/schemas/evidence_block_v1.json`
- **Claim (one claim):** `docs/design/schemas/claim_v1.json`

Validator: `scripts/validate_evidence_claims.py` (evidence file path, claims file path). Exit 0 if all claims pass; 1 and print violations otherwise.

---

## 5. References

- `yukti-explain-core`: `ClaimVerifier`, `ClaimTypeRules`, `EvidenceGraph`
- `docs/design/VERIFICATION_GUARANTEES.md`: Soundness and completeness
