#!/usr/bin/env bash
# Проверка на эмуляторе «как человек»: главный экран, раздел, ответы, экзамен, поиск,
# настройки, крупный шрифт, поворот, восстановление позиции, размеры кнопок, падения.
# Аргументы: имя набора; apk для проверки (по умолчанию отладочный).
set -uo pipefail
NAME="$1"
APK="${2:-app/build/outputs/apk/debug/app-debug.apk}"
PKG=ru.etc51.trenazher
OUT="device-check/$NAME"
mkdir -p "$OUT"
FAIL=0
STEP=0
log() { echo "== $*"; }
fail() { echo "!! ПРОВАЛ: $*"; echo "ПРОВАЛ: $*" >> "$OUT/result.txt"; FAIL=1; }
ok() { echo "ok: $*"; echo "ok: $*" >> "$OUT/result.txt"; }
info() { echo "info: $*"; echo "info: $*" >> "$OUT/result.txt"; }
shot() { STEP=$((STEP+1)); adb exec-out screencap -p > "$OUT/$(printf %02d $STEP)-$1.png"; }

dump() {
  # uiautomator иногда отдаёт пустой снимок, пока экран анимируется: пробуем до трёх раз.
  for _ in 1 2 3; do
    adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || echo "" > /tmp/ui.xml
    grep -q "<node" /tmp/ui.xml && return 0
    sleep 1
  done
}
# Координаты центра элемента. Режим: text (точно), desc (content-desc содержит), id (resource-id содержит), textin (text содержит).
find_xy() {
  python3 - "$1" "$2" <<'PY'
import re, sys, html
mode, want = sys.argv[1], sys.argv[2]
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    def attr(a):
        t = re.search(' ' + a + r'="([^"]*)"', n)
        return html.unescape(t.group(1)) if t else ''
    hit = (mode == 'text' and attr('text') == want) or \
          (mode == 'textin' and want.lower() in attr('text').lower()) or \
          (mode == 'desc' and want.lower() in attr('content-desc').lower()) or \
          (mode == 'id' and want in attr('resource-id'))
    if hit:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups())
        if x2 > x1 and y2 > y1:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
}
tap() {
  # Если элемента не видно, листаем вниз: на маленьких экранах кнопки бывают ниже края.
  local xy
  for _ in 1 2 3 4; do
    dump; xy=$(find_xy "$1" "$2")
    [ -n "$xy" ] && [ "${xy#* }" -lt $((H*9/10)) ] && { adb shell input tap $xy; sleep "${3:-2}"; return 0; }
    scroll_down
  done
  [ -n "$xy" ] && { adb shell input tap $xy; sleep "${3:-2}"; return 0; }
  return 1
}
scroll_down() { adb shell input swipe $((W/2)) $((H*2/3)) $((W/2)) $((H/3)) 400; sleep 1; }
tap_text() { tap text "$1" "${2:-2}"; }
has() { dump; [ -n "$(find_xy "$1" "$2")" ]; }
has_text() { has text "$1"; }
# Найти элемент, при необходимости пролистав вниз.
seen() { for _ in 1 2 3 4; do has "$1" "$2" && return 0; scroll_down; done; return 1; }
wait_has() { for i in $(seq 1 "${3:-20}"); do has "$1" "$2" && return 0; sleep 1; done; return 1; }
dismiss_anr() {
  dump
  if grep -q "isn't responding" /tmp/ui.xml; then info "системное окно ANR, закрываю"; tap_text "Wait" || adb shell input keyevent 4; fi
}
start() { adb shell am start -S -n $PKG/ru.trenazher.ui.MainActivity --es screen "$1" "${@:2}" >/dev/null; sleep 4; dismiss_anr; }
crashes() { adb logcat -d -b crash | grep -c "Process: $PKG" || true; }

# Все нажимаемые элементы внутри экрана и не меньше 40dp; тексты не обрезаны за краем.
layout() {
  dump
  python3 - "$W" "$H" "$DENS" "$1" <<'PY' >> "$OUT/result.txt"
import re, sys, html
W, H, dens, where = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
min_px = 40 * dens / 160
bad = []
box = lambda n: tuple(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
# Элементы, обрезанные краем прокручиваемого списка, — не мелкие кнопки.
edges = [box(m.group(0)) for m in re.finditer(r'<node [^>]*scrollable="true"[^>]*>', xml)]
def clipped(x1, y1, x2, y2):
    return any(abs(x1-a) <= 2 or abs(x2-c) <= 2 or abs(y1-b) <= 2 or abs(y2-d) <= 2 for a, b, c, d in edges)
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    if 'package="ru.etc51.trenazher"' not in n: continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    x1, y1, x2, y2 = map(int, b.groups())
    name = html.unescape((re.search(r' text="([^"]*)"', n).group(1) or re.search(r'content-desc="([^"]*)"', n).group(1)))[:40]
    if x1 < 0 or x2 > W: bad.append(f"за краем по ширине: «{name}» {x1}..{x2}")
    if 'clickable="true"' in n and y2 > y1 and y2 < H and (x2 - x1 < min_px or y2 - y1 < min_px) and y1 > 0 and not clipped(x1, y1, x2, y2):
        bad.append(f"мелкая кнопка {x2-x1}x{y2-y1}px: «{name}»")
for x in bad[:10]: print(f"ПРОВАЛ: {where}: {x}")
if not bad: print(f"ok: {where}: раскладка в порядке")
PY
  grep -q "ПРОВАЛ: $1:" "$OUT/result.txt" && { FAIL=1; echo "!! ПРОВАЛ раскладки: $1"; } || true
}

read -r W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')
DENS=$(adb shell wm density | grep -o '[0-9]*' | tail -1)
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
info "Android API $API, экран ${W}x${H}, плотность $DENS, apk $APK"

adb logcat -c; adb logcat -b crash -c
adb uninstall $PKG >/dev/null 2>&1 || true
adb install -r "$APK" || { fail "APK не устанавливается"; exit 1; }
DEBUG=0; adb shell run-as $PKG true 2>/dev/null && DEBUG=1
seed() { [ $DEBUG = 1 ] && adb shell am broadcast -n $PKG/ru.trenazher.debug.DemoDataReceiver -a ru.trenazher.debug.$1 >/dev/null; sleep 2; }
seed CLEAR

log "1. Главный экран"
start home
wait_has textin "Ростехнадзор" 20 && ok "главный экран открылся" || fail "нет заголовка главного экрана"
shot glavnaya
layout "главный"
has text "Найти вопрос" && ok "поиск на главном" || fail "нет поиска"
FIRST=$(python3 -c "import json;print(json.load(open('app/src/main/assets/baza/index.json'))[0]['code'])")
info "первый раздел $FIRST"

log "2. Раздел"
tap textin "$FIRST" 3 || fail "раздел $FIRST не нажимается"
wait_has text "Начать экзамен" 15 && ok "экран раздела" || fail "нет экрана раздела"
shot razdel
layout "раздел"

log "3. По порядку: ответ и основание"
tap text "Все вопросы по порядку" 3 || fail "нет режима по порядку"
wait_has textin "Вопрос 1 из" 10 && ok "первый вопрос" || fail "вопрос не открылся"
tap desc "Вариант А" 1 || fail "вариант А не нажимается"
shot vopros
layout "вопрос"
tap text "Ответить" 2 || fail "кнопка Ответить"
if seen textin "верно" || has desc "верно"; then ok "показан итог ответа"; else fail "нет Верно/Неверно"; fi
seen textin "Основание" && ok "основание показано" || fail "нет основания"
shot otvet
tap text "Следующий вопрос" 2 || fail "нет Следующий вопрос"
has textin "Вопрос 2 из" && ok "переход ко второму" || fail "не перешли ко второму"

log "4. Позиция сохраняется после выгрузки"
adb shell am force-stop $PKG; sleep 1
start "section:$FIRST"
seen textin "продолжить с вопроса 2" && ok "позиция сохранилась" || fail "позиция потерялась"

log "5. Экзамен до конца"
tap text "Начать экзамен" 3 || fail "экзамен не начинается"
wait_has text "Экзамен" 10 && ok "экзамен открыт" || fail "нет экрана экзамена"
shot ekzamen
layout "экзамен"
for k in $(seq 1 40); do
  if has text "Завершить"; then break; fi
  [ $((k % 2)) = 0 ] && tap desc "Вариант Б" 0.5
  tap text "Дальше" 1 || tap text "Пропустить" 1 || break
done
tap text "Завершить" 2 && tap text "Завершить" 3 || fail "не завершить экзамен"
if wait_has text "Экзамен сдан" 5 || has text "Не сдан"; then ok "итог экзамена"; else fail "нет итога экзамена"; fi
shot itog
layout "итог"
tap text "К разделу" 2 || adb shell input keyevent 4

log "6. Поиск"
start home
tap text "Найти вопрос" 2 && has text "Поиск вопроса" && ok "поиск открывается" || fail "поиск не открывается"
# Кириллицу через adb не напечатать: запрос передаём в маршруте.
start "search:лиценз"; sleep 2
dump; cp /tmp/ui.xml "$OUT/poisk.xml"
shot poisk
adb shell input keyevent 111; sleep 1
if tap textin " · № " 3; then has textin "Вопрос 1 из" && ok "вопрос из поиска открылся" || fail "вопрос из поиска не открылся"; shot iz-poiska; else fail "поиск ничего не нашёл"; fi

log "7. Настройки и тёмная тема"
start home --ez settings true
wait_has text "Настройки" 10 && ok "настройки открыты" || fail "настройки не открылись"
seen text "k@etc51.ru" && ok "почта в контактах" || fail "нет почты"
grep -qi github /tmp/ui.xml && fail "в настройках упомянут GitHub" || true
shot nastroyki
tap text "Тёмная" 2 || fail "нет кнопки тёмной темы"
adb shell input keyevent 4; sleep 2
shot temnaya
start "section:$FIRST"; shot temnaya-razdel
start home --ez settings true; tap text "Как в системе" 2; adb shell input keyevent 4

log "8. Крупный шрифт системы"
adb shell settings put system font_scale 1.3; sleep 2
start home; shot shrift-glavnaya; layout "крупный шрифт главный"
start "quiz:$FIRST:order"; shot shrift-vopros; layout "крупный шрифт вопрос"
adb shell settings put system font_scale 1.0

log "9. Поворот"
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 3
read -r W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' '); [ "$W" -lt "$H" ] && { T=$W; W=$H; H=$T; }
start "quiz:$FIRST:order"; shot povorot-vopros; layout "поворот вопрос"
start home; shot povorot-glavnaya; layout "поворот главный"
adb shell settings put system user_rotation 0; sleep 2
read -r W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr 'x' ' ')

log "10. Кадры с прогрессом"
if [ $DEBUG = 1 ]; then
  seed SEED
  start home; sleep 2; shot promo-glavnaya
  start "section:$FIRST"; shot promo-razdel
  start "quiz:$FIRST:order"; tap desc "Вариант А" 1; tap text "Ответить" 2; shot promo-otvet
fi

C=$(crashes)
[ "$C" = 0 ] && ok "падений нет" || { fail "падений: $C"; adb logcat -d -b crash > "$OUT/crash.txt"; }
exit $FAIL
