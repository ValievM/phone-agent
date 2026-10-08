#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/measure/common.sh
source "$SCRIPT_DIR/common.sh"
require_adb
require_one_device

package="${PACKAGE:-com.example.phoneagent}"
count=0
total_kb=0
for sample in 1 2 3; do
    output="$(adb shell dumpsys meminfo "$package")" || fail "Не удалось прочитать память приложения $package"
    pss_kb="$(awk '/TOTAL PSS:/ { value=$3; gsub(/,/, "", value); print value; found=1; exit } END { if (!found) exit 1 }' <<<"$output")" || fail "В выводе meminfo нет TOTAL PSS для $package"
    printf 'Замер %d: %s КБ (%.2f МБ)\n' "$sample" "$pss_kb" "$(awk -v n="$pss_kb" 'BEGIN { print n / 1024 }')"
    total_kb=$((total_kb + pss_kb))
    count=$((count + 1))
    if (( sample < 3 )); then sleep "${SAMPLE_INTERVAL:-2}"; fi
done
average_kb=$((total_kb / count))
printf 'Среднее: %s КБ (%.2f МБ)\n' "$average_kb" "$(awk -v n="$average_kb" 'BEGIN { print n / 1024 }')"
