#!/usr/bin/env bash
# Device side effect: batterystats --reset is permitted and resets battery statistics.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/measure/common.sh
source "$SCRIPT_DIR/common.sh"
require_adb
require_one_device
package="${PACKAGE:-com.example.phoneagent}"
minutes="${1:-60}"
[[ "$minutes" =~ ^[1-9][0-9]*$ ]] || fail "Укажите целое число минут больше нуля: battery.sh [минуты]"

battery_level() {
    adb shell dumpsys battery | awk -F': ' '/^  level:/ { print $2; found=1; exit } END { if (!found) exit 1 }'
}
start_level="$(battery_level)" || fail "Не удалось прочитать начальный уровень заряда"
adb shell dumpsys batterystats --reset >/dev/null || fail "Не удалось сбросить статистику батареи"
printf 'Ожидание %s мин.; оставьте приложение без активного чата.\n' "$minutes"
sleep "$((minutes * 60))"
end_level="$(battery_level)" || fail "Не удалось прочитать конечный уровень заряда"
output="$(adb shell dumpsys batterystats --charged)" || fail "Не удалось прочитать статистику батареи"
printf 'Уровень заряда: %s%% → %s%% (дельта: %s п.п.)\n' "$start_level" "$end_level" "$((end_level - start_level))"
uid="$(adb shell dumpsys package "$package" | awk '/userId=/ { sub(/.*userId=/, ""); sub(/[^0-9].*/, ""); print; exit }')"
if [[ -z "$uid" ]]; then
    printf 'Вклад приложения (%s): UID не найден в выводе package manager.\n' "$package"
    exit 0
fi
app_section="$(awk -v uid="$uid" 'index($0, "Uid " uid) || index($0, "uid=" uid) { found=1 } found { print; if (++lines == 12) exit }' <<<"$output")"
if [[ -n "$app_section" ]]; then
    printf 'Вклад приложения (%s, UID %s) из batterystats --charged:\n%s\n' "$package" "$uid" "$app_section"
else
    printf 'Вклад приложения (%s, UID %s): отдельная запись отсутствует в batterystats --charged.\n' "$package" "$uid"
fi
