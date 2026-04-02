#!/usr/bin/env python3
"""
Verify a reproducibility certificate against a bench results file.
Recomputes configHash (from runStamp with normalized timestamp) and resultHash
(from full bench with normalized timestamp) and compares to the certificate.
Usage: python3 scripts/verify_reproducibility_certificate.py <certificate.json> [bench_results.json]
  If bench_results.json is omitted, uses artifacts/bench/v1/bench_results.json next to repo root.
Exit 0 if hashes match; 1 and print errors otherwise.
"""
import hashlib
import json
import sys
from pathlib import Path
from decimal import Decimal, ROUND_HALF_UP

REPO_ROOT = Path(__file__).resolve().parent.parent
NORMALIZED_TIMESTAMP = "1970-01-01T00:00:00Z"


def _format_number(key: str, n) -> str:
    """Match Java CanonicalJsonWriter: 2 decimals for keys containing 'usd', else 6; half-up; strip trailing zeros."""
    try:
        d = Decimal(str(n))
    except Exception:
        return str(n)
    key_lower = (key or "").lower()
    scale = 2 if "usd" in key_lower else 6
    d = d.quantize(Decimal(10) ** -scale, rounding=ROUND_HALF_UP)
    s = str(d)
    if "E" in s:
        return s
    s = s.rstrip("0").rstrip(".")
    return s if s else "0"


def _escape_string(s: str) -> str:
    return json.dumps(s)


def _canonical_dump(obj, parent_key: str) -> str:
    """Emit canonical JSON string (sorted keys, number format like Java: no trailing .0)."""
    if obj is None:
        return "null"
    if obj is True:
        return "true"
    if obj is False:
        return "false"
    if isinstance(obj, (int, float)):
        return _format_number(parent_key, obj)
    if isinstance(obj, str):
        return _escape_string(obj)
    if isinstance(obj, list):
        return "[" + ",".join(_canonical_dump(x, parent_key) for x in obj) + "]"
    if isinstance(obj, dict):
        parts = [_escape_string(k) + ":" + _canonical_dump(v, k) for k, v in sorted(obj.items())]
        return "{" + ",".join(parts) + "}"
    return _escape_string(str(obj))


def _canonical_bytes(obj) -> bytes:
    """UTF-8 bytes of canonical JSON matching Java CanonicalJsonWriter (sorted keys, stable numbers)."""
    return _canonical_dump(obj, "").encode("utf-8")


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify(cert_path: Path, bench_path: Path) -> tuple[bool, list[str]]:
    if not cert_path.is_file():
        return False, [f"Certificate not found: {cert_path}"]
    if not bench_path.is_file():
        return False, [f"Bench results not found: {bench_path}"]

    with open(cert_path, "r", encoding="utf-8") as f:
        cert = json.load(f)
    with open(bench_path, "r", encoding="utf-8") as f:
        bench = json.load(f)

    run_stamp = bench.get("runStamp")
    if not run_stamp:
        return False, ["Bench file has no runStamp"]
    run_stamp = dict(run_stamp)
    run_stamp["generatedAtIso"] = NORMALIZED_TIMESTAMP

    config_bytes = _canonical_bytes(run_stamp)
    config_hash = sha256_hex(config_bytes)

    bench_normalized = dict(bench)
    bench_normalized["runStamp"] = run_stamp
    result_bytes = _canonical_bytes(bench_normalized)
    result_hash = sha256_hex(result_bytes)

    errors = []
    if cert.get("configHash") != config_hash:
        errors.append(f"configHash mismatch: certificate={cert.get('configHash')}, computed={config_hash}")
    if cert.get("resultHash") != result_hash:
        errors.append(f"resultHash mismatch: certificate={cert.get('resultHash')}, computed={result_hash}")

    return len(errors) == 0, errors


def main() -> None:
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/verify_reproducibility_certificate.py <certificate.json> [bench_results.json]", file=sys.stderr)
        sys.exit(2)
    cert_path = Path(sys.argv[1])
    bench_path = Path(sys.argv[2]) if len(sys.argv) > 2 else REPO_ROOT / "artifacts" / "bench" / "v1" / "bench_results.json"

    ok, errors = verify(cert_path, bench_path)
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        sys.exit(1)
    print("Certificate verified: configHash and resultHash match.")
    sys.exit(0)


if __name__ == "__main__":
    main()
