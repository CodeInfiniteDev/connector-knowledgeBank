#!/usr/bin/env bash
# ============================================================
# validate.sh - Validate midPoint XML files for well-formedness
# Usage: ./validate.sh <directory>
# ============================================================
set -euo pipefail

DIR="${1:-.}"

echo "Validating XML files in: ${DIR}"

errors=0
count=0

while IFS= read -r -d '' file; do
    count=$((count + 1))
    if xmllint --noout "$file" 2>/dev/null; then
        echo "  OK: $file"
    else
        echo "  FAIL: $file"
        xmllint --noout "$file" 2>&1 | sed 's/^/    /'
        errors=$((errors + 1))
    fi
done < <(find "$DIR" -name "*.xml" -type f -print0)

echo ""
echo "Validated ${count} files, ${errors} errors."

if [ "$errors" -gt 0 ]; then
    exit 1
fi
