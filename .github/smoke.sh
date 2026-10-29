#!/usr/bin/env bash
# Installs the debug APK, opens every tab and checks the app never crashes.
set -u
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
adb logcat -c
adb shell am start -n it.mattia.ciclismo/.MainActivity
sleep 6
adb exec-out screencap -p > smoke-1-onboarding.png

# Skip the onboarding ("Salta per ora"), then tap each bottom tab and take a screenshot.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  local b
  b=$(grep -o "text=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" ui.xml | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  set -- $b
  if [ $# -eq 4 ]; then adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); return 0; fi
  return 1
}
tap_text "Salta per ora"
sleep 3
n=2
for t in Oggi Giri Settimana Piano Diario Forma; do
  tap_text "$t"; sleep 2
  adb exec-out screencap -p > "smoke-$n-$t.png"; n=$((n+1))
done
tap_text "Piano"; sleep 1; tap_text "Carico carbo"; sleep 2
adb exec-out screencap -p > smoke-$n-carico.png
tap_text "Impostazioni"; sleep 2
adb exec-out screencap -p > smoke-$((n+1))-impostazioni.png

# Notification settings with the master switch on.
tap_text "Notifiche"; sleep 2
tap_text "Notifiche attive"; sleep 2
tap_text "Allow" || tap_text "Consenti"; sleep 1
tap_text "Pasti"; sleep 2
adb exec-out screencap -p > smoke-20-notifiche.png
tap_text "Indietro"; sleep 1; tap_text "Chiudi"; sleep 1

# Diary: open the first meal, edit its foods, open the food picker.
tap_text "Diario"; sleep 2
tap_text "Tocca un pasto per grammi, perché e alternative"
adb shell input tap 540 700; sleep 2
adb exec-out screencap -p > smoke-21-pasto-aperto.png
tap_text "Modifica alimenti"; sleep 2
adb shell input swipe 540 1700 540 700 400; sleep 2
adb exec-out screencap -p > smoke-22-modifica.png
tap_text "Aggiungi alimento"; sleep 2
adb shell input text "parmig"; sleep 2
adb exec-out screencap -p > smoke-23-ricerca.png
tap_text "Chiudi"; sleep 1

# Same tabs in dark mode (theme follows the system by default).
adb shell cmd uimode night yes; sleep 3
for t in Oggi Giri Settimana Piano Diario Forma; do
  tap_text "$t"; sleep 2
  adb exec-out screencap -p > "smoke-3$n-scuro-$t.png"; n=$((n+1))
done
adb shell cmd uimode night no; sleep 2

adb shell monkey -p it.mattia.ciclismo --throttle 150 -s 7 400 >/dev/null 2>&1
sleep 2
adb logcat -d > logcat.txt
if grep -q "FATAL EXCEPTION" logcat.txt; then
  grep -A25 "FATAL EXCEPTION" logcat.txt | head -60
  exit 1
fi
echo "No crash detected"
