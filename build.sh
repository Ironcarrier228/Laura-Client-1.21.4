#!/usr/bin/env bash
#
# ============================================================
#   LAURA CLIENT — кастомная сборка (Linux / macOS)
#
#   Использование:
#     ./build.sh                          — интерактивный режим (меню)
#     ./build.sh dev                      — dev-сборка (срок: 01.01.2099, вводить не нужно)
#     ./build.sh public 31.12.2026        — public-сборка с нужным сроком
#     ./build.sh public "31.12.2026 23:59" — public-сборка с датой и временем
#
#   Результат: build/libs/*.jar + копия build/release/Laura-Client-v<версия>-<тип>.jar
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

DEV_EXPIRE="01.01.2099 00:00"
EXPIRE=""

banner() {
    echo ""
    echo "=================================================="
    echo "   LAURA CLIENT — кастомная сборка"
    echo "=================================================="
    echo ""
}

usage() {
    echo "Использование:"
    echo "  ./build.sh                            — интерактивный режим (меню)"
    echo "  ./build.sh dev                        — dev-сборка (срок 01.01.2099)"
    echo "  ./build.sh public [дд.мм.гггг [чч:мм]] — public-сборка со сроком"
}

# --- Выбор типа сборки (меню) ---------------------------------------------
read_type() {
    local choice
    while true; do
        # Меню выводим в stderr, чтобы $(read_type) поймало только результат
        {
            echo "Тип сборки:"
            echo "  [1] dev    — не просрочен (дата: $DEV_EXPIRE, вводить не нужно)"
            echo "  [2] public — дату окончания вы вводите сами"
            echo ""
        } >&2
        read -rp "Выберите (1/2): " choice
        case "$choice" in
            1|dev|DEV|Dev) echo "dev"; return ;;
            2|public|PUBLIC|Public) echo "public"; return ;;
            *) echo "  ? Введите 1 или 2" >&2 ;;
        esac
    done
}

# --- Проверка даты (без GNU date, чтобы работало и на macOS) -----------------
# Возвращает 0 и кладёт нормализованную дату в VALID_DATE.
validate_date() {
    local input="$1"
    if ! [[ "$input" =~ ^([0-9]{1,2})\.([0-9]{1,2})\.([0-9]{4})([[:space:]]([0-9]{1,2}):([0-9]{2}))?$ ]]; then
        return 1
    fi
    # Нормализуем (сбрасываем ведущие нули)
    local d=$((10#${BASH_REMATCH[1]})) m=$((10#${BASH_REMATCH[2]})) y=$((10#${BASH_REMATCH[3]}))
    local h=$((10#${BASH_REMATCH[5]:-0})) min=$((10#${BASH_REMATCH[6]:-0}))
    VALID_DATE="$(printf '%02d.%02d.%04d %02d:%02d' "$d" "$m" "$y" "$h" "$min")"

    # Время
    if (( h > 23 || min > 59 )); then
        return 1
    fi
    # Месяц
    if (( m < 1 || m > 12 )); then
        return 1
    fi
    # День: реальный календарный день (с високосным годом)
    local max_day
    case "$m" in
        1|3|5|7|8|10|12) max_day=31 ;;
        4|6|9|11)        max_day=30 ;;
        2)
            if (( y % 4 == 0 && (y % 100 != 0 || y % 400 == 0) )); then
                max_day=29
            else
                max_day=28
            fi
            ;;
    esac
    if (( d < 1 || d > max_day )); then
        return 1
    fi
    return 0
}

# --- Ввод срока для public-сборки -------------------------------------------
read_expire() {
    local input
    while true; do
        echo ""
        read -rp "Дата окончания (дд.мм.гггг или дд.мм.гггг чч:мм): " input
        input="$(printf '%s' "$input" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
        if [ -z "$input" ]; then
            echo "  ? Введите дату"
            continue
        fi
        if ! validate_date "$input"; then
            echo "  ? Неверная дата. Примеры: 31.12.2026 или 31.12.2026 23:59"
            continue
        fi
        EXPIRE="$VALID_DATE"
        # Предупреждение, если срок уже прошёл
        local now y="${EXPIRE:6:4}" m="${EXPIRE:3:2}" d="${EXPIRE:0:2}"
        now="$(date +%Y%m%d)"
        if [ "$y$m$d" -lt "$now" ]; then
            echo "  ⚠ Это дата из прошлого — клиент сразу будет просрочен."
            read -rp "  Всё равно продолжить? (y/n): " confirm
            case "$confirm" in
                y|Y|д|Д) return ;;
                *) continue ;;
            esac
        fi
        return
    done
}

main() {
    banner

    local build_type="" expire_arg=""
    if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "help" ]; then
        usage
        exit 0
    fi
    build_type="${1:-}"
    expire_arg="${2:-}"

    if [ -n "$build_type" ]; then
        case "$build_type" in
            dev|DEV|Dev|1) BUILD_TYPE="dev" ;;
            public|PUBLIC|Public|2) BUILD_TYPE="public" ;;
            *)
                echo "Неизвестный тип сборки: $build_type (допустимо: dev, public)"
                echo ""
                usage
                exit 1
                ;;
        esac
    else
        BUILD_TYPE="$(read_type)"
    fi

    if [ "$BUILD_TYPE" = "dev" ]; then
        EXPIRE="$DEV_EXPIRE"
        echo ""
        echo "  dev-сборка: срок окончания автоматически $EXPIRE (вводить не нужно)."
    else
        if [ -n "$expire_arg" ]; then
            if ! validate_date "$expire_arg"; then
                echo "Неверная дата: $expire_arg"
                echo "Формат: дд.мм.гггг или дд.мм.гггг чч:мм"
                exit 1
            fi
            EXPIRE="$VALID_DATE"
        else
            read_expire
        fi
    fi

    echo ""
    echo "Собираем:"
    echo "  Тип сборки:     $BUILD_TYPE"
    echo "  Срок окончания: $EXPIRE"
    echo ""

    ./gradlew clean build -PlauraBuildType="$BUILD_TYPE" -PlauraExpire="$EXPIRE"

    local jar
    jar="$(ls -1 build/libs/*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)"
    if [ -z "$jar" ]; then
        echo ""
        echo "⚠ Сборка прошла, но JAR не найден в build/libs/"
        exit 1
    fi

    local mod_version
    mod_version="$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' src/main/resources/fabric.mod.json | head -1)"
    [ -z "$mod_version" ] && mod_version="dev"

    mkdir -p build/release
    cp -f "$jar" "build/release/Laura-Client-v${mod_version}-${BUILD_TYPE}.jar"

    echo ""
    echo "=================================================="
    echo "  Готово! Сборка завершена."
    echo "  JAR:   build/libs/$(basename "$jar")"
    echo "  Копия: build/release/Laura-Client-v${mod_version}-${BUILD_TYPE}.jar"
    echo "  Тип:   $BUILD_TYPE | Срок: $EXPIRE"
    echo "=================================================="
}

main "$@"
