#!/usr/bin/env bash
# Rauchtest im Emulator: App starten, Spiel erzeugen, Ziffern und Notizen eingeben, Screenshots speichern.
set -euo pipefail

APK=$1
OUT=$2
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

# Mittelpunkt des ersten UI-Elements mit dem gegebenen Text bzw. der Beschreibung.
bounds_of() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb shell cat /sdcard/ui.xml | grep -o "$1=\"$2\"[^>]*bounds=\"[^\"]*\"" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/'
}

tap_text() {
  read -r x0 y0 x1 y1 <<< "$(bounds_of text "$1")"
  adb shell input tap $(((x0 + x1) / 2)) $(((y0 + y1) / 2))
  sleep 1
}

check_crash() {
  if adb logcat -d | grep -q "FATAL EXCEPTION"; then
    adb logcat -d | grep -A 30 "FATAL EXCEPTION"
    exit 1
  fi
}

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n app.killersudoku/.MainActivity
sleep 3
shot 1-start
check_crash

tap_text "Mittel"
sleep 8
shot 2-spiel
check_crash

# Geometrie des Spielfelds aus der Position des Zurück-Knopfs und der Bildschirmbreite ableiten.
density=$(adb shell wm density | grep -o '[0-9]*' | tail -1)
width=$(adb shell wm size | grep -o '[0-9]*x' | tail -1 | tr -d x)
dp() { echo $(($1 * density / 160)); }
read -r _ _ _ back_bottom <<< "$(bounds_of content-desc "Zurück zum Menü")"
board_left=$(dp 8)
board_top=$((back_bottom + $(dp 8)))
cell=$(((width - 2 * $(dp 8)) / 9))
tap_cell() { adb shell input tap $((board_left + $2 * cell + cell / 2)) $((board_top + $1 * cell + cell / 2)); sleep 0.5; }

read -r _ undo_top _ undo_bottom <<< "$(bounds_of text "Rückgängig")"
pad_y=$((undo_bottom + $(dp 12) + $(dp 34)))
key=$(((width - 2 * $(dp 8) - 8 * $(dp 4)) / 9))
tap_digit() { adb shell input tap $(($(dp 8) + ($1 - 1) * (key + $(dp 4)) + key / 2)) $pad_y; sleep 0.5; }

# Zwei Felder mit Ziffern füllen (eine davon ist mit hoher Wahrscheinlichkeit falsch).
tap_cell 0 0
tap_digit 5
tap_cell 4 4
tap_digit 3
shot 3-eingaben
check_crash

# Notizmodus: drei Notizen setzen und eine davon wieder entfernen.
tap_text "Notizen: aus"
tap_cell 8 8
tap_digit 1
tap_digit 2
tap_digit 7
shot 4-notizen
tap_digit 2
shot 5-notiz-entfernt
check_crash

# Tipp und Rückgängig.
tap_text "Notizen: an"
tap_cell 2 6
tap_text "Tipp"
shot 6-tipp
tap_text "Rückgängig"
check_crash

# Querformat.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 3
shot 7-querformat
adb shell settings put system user_rotation 0
sleep 2
check_crash

# App beenden und neu starten: Spielstand muss erhalten bleiben.
adb shell am force-stop app.killersudoku
adb shell am start -W -n app.killersudoku/.MainActivity
sleep 3
shot 8-neustart
check_crash
echo "Rauchtest erfolgreich."
