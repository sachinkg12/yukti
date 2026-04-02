#!/usr/bin/env python3
"""
Validate claims against an evidence bundle (portable protocol; mirrors ClaimVerifier rules).
Usage: python3 scripts/validate_evidence_claims.py <evidence.json> <claims.json>
Exit 0 if all claims pass; 1 and print violations otherwise.
See docs/design/evidence-contract-spec.md and docs/design/schemas/.
"""
from pathlib import Path
import json
import re
import sys

REPO_ROOT = Path(__file__).resolve().parent.parent

# Claim type rules (mirror ClaimTypeRules.java)
WINNER_BY_CATEGORY = "WINNER_BY_CATEGORY"
CAP_HIT = "CAP_HIT"
ALLOCATION_SEGMENT = "ALLOCATION_SEGMENT"
FEE_BREAK_EVEN = "FEE_BREAK_EVEN"
ASSUMPTION = "ASSUMPTION"

REQUIRED_EVIDENCE_TYPES = {
    "COMPARISON": {WINNER_BY_CATEGORY},
    "THRESHOLD": set(),
    "ALLOCATION": set(),
    "ASSUMPTION": {ASSUMPTION},
    "FEE_JUSTIFICATION": {FEE_BREAK_EVEN},
    "CAP_SWITCH": {CAP_HIT},
}

REQUIRED_ALL = {
    "CAP_SWITCH": {CAP_HIT, ALLOCATION_SEGMENT},
}

NUMBER_ALLOWLIST = {"0", "1", "2", "3"}
NUMBER_IN_CONTENT = re.compile(r"\d+\.?\d*")


def load_json(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def derive_entities_from_nodes(nodes: list) -> set:
    entities = set()
    for n in nodes:
        cid = n.get("cardId") or ""
        if cid:
            entities.add(cid)
        cat = n.get("category") or ""
        if cat:
            entities.add(cat)
    return entities


def derive_numbers_from_content(content: str) -> set:
    if not content:
        return set()
    return set(NUMBER_IN_CONTENT.findall(content))


def derive_numbers_from_nodes(nodes: list) -> set:
    numbers = set()
    for n in nodes:
        numbers |= derive_numbers_from_content(n.get("content") or "")
    return numbers


def build_evidence_id_to_type(nodes: list) -> dict:
    return {n["evidenceId"]: n["type"] for n in nodes if n.get("evidenceId")}


def verify_claims(evidence_path: Path, claims_path: Path):
    # Returns (passed: bool, violations: list[str])
    evidence = load_json(evidence_path)
    claims_doc = load_json(claims_path)
    nodes = evidence.get("nodes", [])
    if not nodes:
        return False, ["Evidence bundle has no nodes"]

    allowed_entities = set(evidence.get("allowedEntities", [])) or derive_entities_from_nodes(nodes)
    allowed_numbers = set(evidence.get("allowedNumbers", [])) | derive_numbers_from_nodes(nodes) | NUMBER_ALLOWLIST

    evidence_ids = {n["evidenceId"] for n in nodes}
    evidence_id_to_type = build_evidence_id_to_type(nodes)
    types_in_graph = set(evidence_id_to_type.values())

    claims = claims_doc.get("claims", [])
    if not claims:
        return True, []

    violations_log: list[str] = []
    all_passed = True

    for c in claims:
        claim_id = c.get("claimId", "?")
        claim_type = c.get("claimType", "")
        cited_ids = c.get("citedEvidenceIds") or []
        cited_entities = c.get("citedEntities") or []
        cited_numbers = c.get("citedNumbers") or []

        errors = []

        for eid in cited_ids:
            if eid not in evidence_ids:
                errors.append(f"citedEvidenceId not in graph: {eid}")

        for entity in cited_entities:
            if entity and entity not in allowed_entities:
                errors.append(f"citedEntity not allowed: {entity}")

        for num in cited_numbers:
            if num and num not in allowed_numbers:
                errors.append(f"citedNumber not allowed: {num}")

        required_types = REQUIRED_EVIDENCE_TYPES.get(claim_type, set())
        if required_types:
            cited_types = {evidence_id_to_type.get(eid) for eid in cited_ids if eid in evidence_id_to_type}
            if not (required_types & cited_types):
                errors.append(f"{claim_type} must cite one of {required_types} evidence")

        required_all = REQUIRED_ALL.get(claim_type, set())
        for req_type in required_all:
            if req_type in types_in_graph:
                cited_types = {evidence_id_to_type.get(eid) for eid in cited_ids if eid in evidence_id_to_type}
                if req_type not in cited_types:
                    errors.append(f"{claim_type} must cite {req_type} evidence")

        if errors:
            all_passed = False
            violations_log.append(f"Claim {claim_id}: " + "; ".join(errors))

    return all_passed, violations_log


def main() -> None:
    if len(sys.argv) != 3:
        print("Usage: python3 scripts/validate_evidence_claims.py <evidence.json> <claims.json>", file=sys.stderr)
        sys.exit(2)
    evidence_path = Path(sys.argv[1])
    claims_path = Path(sys.argv[2])
    if not evidence_path.is_file():
        print(f"Evidence file not found: {evidence_path}", file=sys.stderr)
        sys.exit(2)
    if not claims_path.is_file():
        print(f"Claims file not found: {claims_path}", file=sys.stderr)
        sys.exit(2)

    passed, violations = verify_claims(evidence_path, claims_path)
    if violations:
        for v in violations:
            print(v, file=sys.stderr)
    if passed:
        print("All claims passed verification.")
        sys.exit(0)
    sys.exit(1)


if __name__ == "__main__":
    main()
