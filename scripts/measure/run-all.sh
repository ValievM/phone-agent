#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/measure/common.sh
source "$SCRIPT_DIR/common.sh"
require_adb
require_one_device
minutes="${1:-60}"
[[ "$minutes" =~ ^[1-9][0-9]*$ ]] || fail "Укажите целое число минут больше нуля: run-all.sh [минуты]"
root="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
report_dir="$root/build/measure"
mkdir -p "$report_dir"
timestamp="$(date '+%Y%m%d-%H%M%S')"
report="$report_dir/$timestamp.md"
{
    printf "# Отчёт замеров\n\n- Время: %s\n- Пакет: \`%s\`\n- Ожидание замера батареи: %s мин.\n\n" "$(date '+%Y-%m-%d %H:%M:%S %z')" "$PACKAGE" "$minutes"
} >"$report"
run_section() {
    local title="$1" script="$2" output status
    printf '== %s ==\n' "$title"
    if output="$(bash "$SCRIPT_DIR/$script" "$minutes" 2>&1)"; then
        status=0
    else
        status=$?
    fi
    printf '%s\n' "$output"
    {
        printf "## %s\n\n\`\`\`text\n%s\n\`\`\`\n\n" "$title" "$output"
    } >>"$report"
    (( status == 0 )) || fail "$title завершился с ошибкой (код $status); отчёт: $report"
}
run_section 'Память (три замера)' mem.sh
run_section 'Батарея' battery.sh
run_section 'Фоновая работа' background.sh
run_section 'Холодный запуск (пять повторов)' startup.sh
printf 'Отчёт сохранён: %s\n' "$report"
