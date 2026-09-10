# Laura Client — установщик для Windows

В этой папке лежит готовый установщик клиента для Windows. Он создаёт чистый
инстанс Minecraft **1.21.4 + Fabric**, ставит клиент и Fabric API и **сам
запускает игру** — официальный лаунчер Mojang не нужен.

## Файлы

| Файл | Назначение |
|---|---|
| `laura-launch.bat` | Запускатель (двойной клик / ярлык). Консоль остаётся открытой, ошибки видны. |
| `LauraLauncher.ps1` | Сам логика лаунчера (PowerShell 5.1+, входит в Windows 10/11). |
| `Laura-Installer.iss` | Скрипт **Inno Setup 6** — собирает `Laura-Client-Setup.exe`. |
| `icon.ico` | Иконка (сгенерирована из `assets/laura/icon.png`). |

## Быстрый старт (без .exe)

1. Соберите клиент:
   ```bat
   gradlew build
   ```
   JAR появится в `build\libs\laura-client-<версия>.jar`.
2. Скопируйте этот JAR в папку `windows\` (рядом с `laura-launch.bat`)
   — либо просто запустите бат из папки `windows\`, он сам найдёт JAR в `..\build\libs`.
3. Дважды кликните `laura-launch.bat`.
4. Первый запуск скачает Java-библиотеки, jar игры и ассеты (несколько минут,
   один раз). Дальше запуск быстрый.

## Сборка установщика .exe

### Вариант 1 — через GitHub Actions (без Windows под рукой)
Просто запушь изменения (или запусти вручную): workflow
[`.github/workflows/installer.yml`](../.github/workflows/installer.yml)
сам собирает JAR и компилирует `Laura-Installer.iss`.

Готовый файл скачиваешь: **GitHub → вкладка Actions → «Windows Installer» →
последний зелёный ран → артефакт `Laura-Client-Setup`** → внутри
`Laura-Client-Setup.exe`.

### Вариант 2 — локально (Inno Setup 6)
1. Установите **Inno Setup 6** (бесплатно): https://jrsoftware.org/isdl.php
2. Соберите клиент: `gradlew build`.
3. Откройте `Laura-Installer.iss` в Inno Setup IDE → **Compile**.
4. Готовый файл: `windows\Output\Laura-Client-Setup.exe`.

Установщик кладёт лаунчер в `%LOCALAPPDATA%\Laura Client`, создаёт ярлыки
(рабочий стол — по выбору) и сразу запускает игру.

## Параметры лаунчера (правый клик по ярлыку → Свойства → Аргументы)

| Параметр | Пример | Что делает |
|---|---|---|
| `-Username <ник>` | `-Username Laura` | Никнейм (по умолчанию — имя пользователя Windows). |
| `-Reset` | `-Reset` | Полностью удалить инстанс и настроить заново. |
| `-InstanceRoot <путь>` | `-InstanceRoot D:\Games\Laura` | Куда ставить инстанс. |

Пример ярлыка:
```
powershell -NoProfile -ExecutionPolicy Bypass -File "C:\...\laura-launch.bat" -Username Laura
```
(или просто добавьте `-Username Laura` в поле «Аргументы» ярлыка `.bat`).

## Почему старый установщик мог «не запускаться» — чек-лист

Если ваш предыдущий установщик молча закрывался или не стартует, типовые причины:

1. **`.bat` с переносами строк LF (а не CRLF).** Файл, созданный на Linux/Mac
   или в VS Code без CRLF, в `cmd.exe` выполняется неправильно — скрипт
   обрывается на первой же строке. Этот `laura-launch.bat` сохранён в CRLF.
2. **Кириллица в `.bat` без `chcp 65001`.** Кодировка cmd — OEM-866; русские
   строки ломают парсер. Поэтому в `.bat` только ASCII, а весь русский текст —
   в `.ps1` (UTF-8 **с BOM** — обязательно, иначе PowerShell 5.1 читает мусор).
3. **Не та PowerShell / политика выполнения.** Запуск всегда через
   `powershell -NoProfile -ExecutionPolicy Bypass -File ...` — это обходит
   блокировку скриптов политикой.
4. **Нет Java 21.** Лаунчер ищет Java в PATH, `JAVA_HOME` и типовых папках
   установки; если не находит — сам предлагает поставить Temurin 21 через
   winget и показывает ссылку.
5. **Antivirus/SmartScreen.** Self-extracted .exe и bat-скачиватели часто
   блокируются. Для `.exe`-установщика Inno Setup это редкость, но если
   SmartScreen спрашивает — «Подробнее → Выполнить в любом случае».
6. **Требование прав администратора.** Наш установщик работает **без admin**
   (`PrivilegesRequired=lowest`), пишет в `%LOCALAPPDATA%` — типичная причина
   «срывается при установке» (манифест с `requireAdministrator` + UAC) исключена.

## Офлайн-режим

Headless-запуск работает в **офлайн-режиме** (одиночная игра, сервера с
`online-mode=false`). Для онлайн-анархии с `online-mode=true` нужен авторизованный
аккаунт — для этого запустите игру через официальный лаунчер, поставив туда
содержимое папки `mods` из инстанса (`%LOCALAPPDATA%\Laura Client\instance\mods`).

## Структура инстанса

```
%LOCALAPPDATA%\Laura Client\
├── laura-launch.bat        (или весь установщик, если ставили .exe)
├── LauraLauncher.ps1
├── laura-client-*.jar
├── icon.ico
├── scripts\
│   └── fabric-installer.jar
└── instance\               ← сам Minecraft-инстанс
    ├── mods\               (laura-client + fabric-api)
    ├── libraries\
    ├── assets\
    ├── versions\
    └── natives-windows\
```
