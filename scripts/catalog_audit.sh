#!/usr/bin/env bash
# Audits catalog for source-backed requirements and valid categories.
# Fails if any card lacks sources[] or asOfDate, or any earn rule uses unknown Category.
# Usage: ./scripts/catalog_audit.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CATALOG_DIR="$(cd "$SCRIPT_DIR/../yukti-catalog/src/main/resources/catalog/v1" && pwd)"
CARDS_DIR="$CATALOG_DIR/cards"

VALID_CATEGORIES="GROCERIES|DINING|GAS|TRAVEL|ONLINE|OTHER"
AS_OF_DATE_PATTERN='^[0-9]{4}-[0-9]{2}-[0-9]{2}$'

if [[ ! -d "$CARDS_DIR" ]]; then
  echo "Cards directory not found: $CARDS_DIR" >&2
  exit 1
fi

if ! command -v jq &>/dev/null; then
  echo "jq is required for catalog_audit.sh" >&2
  exit 1
fi

errors=0

for f in "$CARDS_DIR"/*.json; do
  [[ -f "$f" ]] || continue
  card_id=$(basename "$f" .json)

  # Require non-empty sources[]
  sources_len=$(jq -r 'if .sources | type == "array" then (.sources | length) else -1 end' "$f")
  if [[ "$sources_len" == "-1" || "$sources_len" == "null" ]]; then
    echo "ERROR: $card_id: missing or invalid 'sources' (must be non-empty array)" >&2
    ((errors++)) || true
  elif [[ "$sources_len" == "0" ]]; then
    echo "ERROR: $card_id: 'sources' array is empty" >&2
    ((errors++)) || true
  fi

  # Require asOfDate (YYYY-MM-DD)
  as_of=$(jq -r '.asOfDate // ""' "$f")
  if [[ -z "$as_of" || "$as_of" == "null" ]]; then
    echo "ERROR: $card_id: missing 'asOfDate' (required, YYYY-MM-DD)" >&2
    ((errors++)) || true
  elif ! [[ "$as_of" =~ $AS_OF_DATE_PATTERN ]]; then
    echo "ERROR: $card_id: invalid 'asOfDate' format (expected YYYY-MM-DD): $as_of" >&2
    ((errors++)) || true
  fi

  # Each earn rule category must be a valid Category enum
  categories=$(jq -r '.earnRules[]? | .category // ""' "$f")
  while IFS= read -r cat; do
    [[ -z "$cat" ]] && continue
    if ! [[ "$cat" =~ ^($VALID_CATEGORIES)$ ]]; then
      echo "ERROR: $card_id: unknown category in earn rule: '$cat' (must be one of: GROCERIES, DINING, GAS, TRAVEL, ONLINE, OTHER)" >&2
      ((errors++)) || true
    fi
  done <<< "$categories"
done

if [[ $errors -gt 0 ]]; then
  echo "Audit failed with $errors error(s)." >&2
  exit 1
fi

echo "Catalog audit passed: all cards have sources, asOfDate, and valid categories."
