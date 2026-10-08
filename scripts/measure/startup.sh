#!/usr/bin/env bash
# Device side effects: force-stop before each cold launch is permitted. It does not delete app data.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/measure/common.sh
source "$SCRIPT_DIR/common.sh"
require_adb
require_one_device
package="${PACKAGE:-com.example.phoneagent}"
total_ms=0
for run in 1 2 3 4 5; do
    adb shell am force-stop "$package" >/dev/null || fail "Не удалось остановить приложение перед стартом"
    output="$(adb shell am start -W -n "$package/.MainActivity")" || fail "Не удалось запустить приложение $package"
    elapsed_ms="$(awk -F': ' '$1 == "TotalTime" { print $2; found=1; exit } END { if (!found) exit 1 }' <<<"$output")" || fail "В выводе am start -W нет TotalTime"
    [[ "$elapsed_ms" =~ ^[0-9]+$ ]] || fail "Не удалось разобрать время старта"
    printf 'Старт %d: %s мс\n' "$run" "$elapsed_ms"
    total_ms=$((total_ms + elapsed_ms))
done
printf 'Среднее время старта: %d мс\n' "$((total_ms / 5))"
