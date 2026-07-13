#!/usr/bin/env bash
set -euo pipefail

# Parse detekt text report and emit GitHub Actions annotation commands.
#
# Usage:
#   ./scripts/gh-annotations.sh [report.txt]
#   ./scripts/gh-annotations.sh demo-codebase/build/reports/detekt/main.txt

report="${1:-demo-codebase/build/reports/detekt/main.txt}"

if [[ ! -f "$report" ]]; then
  echo "::notice::No detekt text report found at $report — skipping annotations"
  exit 0
fi

# Format: RuleName - [entity] at /abs/path/File.kt:line:col - Signature=...
pat='^([A-Za-z]+) - \[([^]]+)\] at (.+\.kt):([0-9]+):([0-9]+) - Signature=.*'

count=0
while IFS= read -r line; do
  if [[ "$line" =~ $pat ]]; then
    rule="${BASH_REMATCH[1]}"
    file="${BASH_REMATCH[3]}"
    ln="${BASH_REMATCH[4]}"
    col="${BASH_REMATCH[5]}"

    # Determine YAWN ID
    case "$rule" in
      QueryInsideLoop)               id="YAWN001" ;;
      MaterializedCount)             id="YAWN002" ;;
      ExternalCallInsideTransaction) id="YAWN003" ;;
      CollectionJoinWithoutDistinct) id="YAWN004" ;;
      *)                             id="$rule"   ;;
    esac

    # Relativize file path against repo root (GITHUB_WORKSPACE)
    if [[ -n "${GITHUB_WORKSPACE:-}" ]]; then
      rel="${file#$GITHUB_WORKSPACE/}"
    else
      rel="$file"
    fi

    # Strip leading ./ if present
    rel="${rel#./}"

    echo "::warning file=$rel,line=$ln,col=$col,title=[$id] $rule::$rel:$ln:$col — $id $rule"
    ((count++))
  fi
done < "$report"

echo "::notice::Generated $count GitHub annotation(s)"
