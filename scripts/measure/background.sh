#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/measure/common.sh
source "$SCRIPT_DIR/common.sh"
require_adb
require_one_device
package="${PACKAGE:-com.example.phoneagent}"
issues=0

check_section() {
    local label="$1" output="$2"
    local matches
    matches="$(awk -v p="$package" 'index($0, p) { print }' <<<"$output")"
    if [[ -n "$matches" ]]; then
        printf '%s:\n%s\n' "$label" "$matches"
        issues=$((issues + 1))
    fi
}

check_section "Задачи JobScheduler" "$(adb shell dumpsys jobscheduler)"
check_section "Будильники приложения" "$(adb shell dumpsys alarm)"
check_section "Сервисы процесса" "$(adb shell dumpsys activity services)"
check_section "Процессы приложения" "$(adb shell ps -A)"
if (( issues == 0 )); then
    printf 'фоновых задач: 0\n'
else
    printf 'Найдены связанные с приложением фоновые записи: %d\n' "$issues"
    exit 1
fi
