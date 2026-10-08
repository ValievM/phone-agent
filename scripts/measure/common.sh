#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.example.phoneagent}"
fail() { printf 'Ошибка: %s\n' "$*" >&2; exit 1; }
require_adb() {
    command -v adb >/dev/null 2>&1 || fail "adb не найден. Установите Android Platform Tools и добавьте adb в PATH."
}
require_one_device() {
    local devices count
    devices="$(adb devices)" || fail "Не удалось получить список устройств через adb"
    count="$(awk 'NR > 1 && $2 == "device" { count++ } END { print count+0 }' <<<"$devices")"
    (( count == 1 )) || fail "Нужно ровно одно подключённое устройство adb (найдено: $count)"
}
