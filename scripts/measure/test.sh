#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/no-adb"
cat >"$tmp/bin/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == devices ]]; then
    if [[ "${ADB_MODE:-}" == two-devices ]]; then
        printf 'List of devices attached\none device\tdevice\ntwo device\tdevice\n'
    else
        printf 'List of devices attached\none device\tdevice\n'
    fi
elif [[ "$*" == *'dumpsys meminfo'* ]]; then
    printf ' TOTAL PSS: 1,024 TOTAL\n'
elif [[ "$*" == *'dumpsys jobscheduler'* || "$*" == *'dumpsys alarm'* || "$*" == *'dumpsys activity services'* || "$*" == *'ps -A'* ]]; then
    if [[ "${ADB_MODE:-}" == job && ( "$*" == *'dumpsys jobscheduler'* || "$*" == *'dumpsys activity services'* ) ]]; then
        printf 'ServiceRecord{com.example.phoneagent/.WorkService}\n'
    fi
else
    printf 'unexpected adb call: %s\n' "$*" >&2
    exit 2
fi
ADB
chmod +x "$tmp/bin/adb"
ln -s "$(command -v dirname)" "$tmp/no-adb/dirname"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
for script in "$SCRIPT_DIR"/*.sh; do /bin/bash -n "$script" || fail "bash -n: $script"; done

output="$(PATH="$tmp/bin:$PATH" SAMPLE_INTERVAL=0 /bin/bash "$SCRIPT_DIR/mem.sh")"
[[ "$output" == *'Замер 1: 1024 КБ (1.00 МБ)'* && "$output" == *'Среднее: 1024 КБ (1.00 МБ)'* ]] || fail "mem.sh не вычислил значения TOTAL PSS: $output"
printf 'PASS: память читает PSS в КБ и МБ\n'

output="$(PATH="$tmp/bin:$PATH" ADB_MODE=clean /bin/bash "$SCRIPT_DIR/background.sh")"
[[ "$output" == *'фоновых задач: 0'* ]] || fail "background.sh не сообщил об отсутствии записей: $output"
printf 'PASS: фон без задач\n'
if output="$(PATH="$tmp/bin:$PATH" ADB_MODE=job /bin/bash "$SCRIPT_DIR/background.sh" 2>&1)"; then
    fail "background.sh принял образец с задачей"
else
    status=$?
    [[ "$status" -ne 0 && "$output" == *'com.example.phoneagent'* ]] || fail "background.sh не показал найденную задачу: $output"
fi
printf 'PASS: фон с задачей возвращает ошибку и показывает запись\n'

if output="$(PATH="$tmp/bin:$PATH" ADB_MODE=two-devices /bin/bash "$SCRIPT_DIR/mem.sh" 2>&1)"; then
    fail "mem.sh принял два устройства"
else
    [[ "$output" == *'ровно одно подключённое устройство'* ]] || fail "ошибка двух устройств не объяснена: $output"
fi
printf 'PASS: два устройства отклоняются с понятным сообщением\n'

if output="$(PATH="$tmp/no-adb" /bin/bash "$SCRIPT_DIR/mem.sh" 2>&1)"; then
    fail "mem.sh прошёл без adb"
else
    [[ "$output" == *'adb не найден'* && "$output" == *'Platform Tools'* ]] || fail "ошибка отсутствующего adb не содержит подсказки: $output"
fi
printf 'PASS: отсутствие adb объяснено\n'
