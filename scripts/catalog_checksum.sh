#!/usr/bin/env bash
# Recomputes sha256 for catalog/v1/cards/*.json and rewrites index.json (DSL v0.1 format).
# Usage: ./scripts/catalog_checksum.sh
# Index: catalogVersion, dslVersion, generatedAtIso, cards[] with cardId, path, sha256 (lowercase).

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CATALOG_DIR="$(cd "$SCRIPT_DIR/../yukti-catalog/src/main/resources/catalog/v1" && pwd)"
CARDS_DIR="$CATALOG_DIR/cards"

if [[ ! -d "$CARDS_DIR" ]]; then
  echo "Cards directory not found: $CARDS_DIR" >&2
  exit 1
fi

# Compute sha256 over exact file bytes (deterministic)
gen_sha() {
  if command -v sha256sum &>/dev/null; then
    sha256sum "$1" | cut -d' ' -f1 | tr 'A-F' 'a-f'
  else
    shasum -a 256 "$1" | cut -d' ' -f1 | tr 'A-F' 'a-f'
  fi
}

entries=()
while IFS= read -r f; do
  card_id=$(basename "$f" .json)
  sha=$(gen_sha "$f")
  path="cards/${card_id}.json"
  entries+=("$card_id|$path|$sha")
done < <(find "$CARDS_DIR" -name "*.json" -type f | sort)

generated_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Write index.json (DSL v0.1)
{
  echo '{'
  echo '  "catalogVersion": "v1",'
  echo '  "dslVersion": "carddsl.v0.1",'
  echo "  \"generatedAtIso\": \"$generated_at\","
  echo '  "cards": ['
  for i in "${!entries[@]}"; do
    IFS='|' read -r cid path sha <<< "${entries[$i]}"
    [[ $i -gt 0 ]] && echo ','
    printf '    {"cardId": "%s", "path": "%s", "sha256": "%s"}' "$cid" "$path" "$sha"
  done
  echo
  echo '  ]'
  echo '}'
} > "$CATALOG_DIR/index.json"

echo "Updated $CATALOG_DIR/index.json with ${#entries[@]} cards (sorted by cardId)"
