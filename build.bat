@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Laura Client - кастомная сборка
rem ==========================================================
rem   LAURA CLIENT - кастомная сборка (Windows)
rem
rem   Использование:
rem     build.bat                          - интерактивный режим (меню)
rem     build.bat dev                      - dev-сборка (срок: 01.01.2099)
rem     build.bat public 31.12.2026        - public-сборка с нужным сроком
rem     build.bat public "31.12.2026 23:59" - public-сборка с датой и временем
rem
rem   Результат: build\libs\*.jar + копия
rem           build\release\Laura-Client-v<версия>-<тип>.jar
rem ==========================================================

set "DEV_EXPIRE=01.01.2099 00:00"
set "EXPIRE_ARG=%~2"
cd /d "%~dp0"

echo.
echo ==================================================
echo    LAURA CLIENT - кастомная сборка
echo ==================================================
echo.

if /i "%~1"=="help" goto :show_help
if /i "%~1"=="-h" goto :show_help

rem --- тип по аргументу командной строки
set "BUILD_TYPE="
if not "%~1"=="" (
    if /i "%~1"=="dev"    ( set "BUILD_TYPE=dev"    & goto :pick_expire )
    if /i "%~1"=="1"      ( set "BUILD_TYPE=dev"    & goto :pick_expire )
    if /i "%~1"=="public" ( set "BUILD_TYPE=public" & goto :pick_expire )
    if /i "%~1"=="2"      ( set "BUILD_TYPE=public" & goto :pick_expire )
    echo Неизвестный тип сборки: %~1 ^(допустимо: dev, public^)
    echo.
    goto :show_help
)

rem --- интерактивное меню
:menu
set "CHOICE="
echo Тип сборки:
echo   [1] dev    - не просрочен (дата: %DEV_EXPIRE%, вводить не нужно)
echo   [2] public - дату окончания вы вводите сами
echo.
set /p CHOICE=Выберите (1/2):
if /i "!CHOICE!"=="1"      ( set "BUILD_TYPE=dev"    & goto :pick_expire )
if /i "!CHOICE!"=="dev"    ( set "BUILD_TYPE=dev"    & goto :pick_expire )
if /i "!CHOICE!"=="2"      ( set "BUILD_TYPE=public" & goto :pick_expire )
if /i "!CHOICE!"=="public" ( set "BUILD_TYPE=public" & goto :pick_expire )
echo   ? Введите 1 или 2
goto :menu

:pick_expire
set "EXPIRE="
if /i "!BUILD_TYPE!"=="dev" (
    set "EXPIRE=%DEV_EXPIRE%"
    echo.
    echo   dev-сборка: срок окончания автоматически %DEV_EXPIRE% (вводить не нужно).
    goto :run_build
)

rem --- public: дата окончания
if not "!EXPIRE_ARG!"=="" (
    set "RAW_INPUT=!EXPIRE_ARG!"
    goto :validate
)

:ask_expire
echo.
set "RAW_INPUT="
set /p RAW_INPUT=Дата окончания (дд.мм.гггг или дд.мм.гггг чч:мм):
if "!RAW_INPUT!"=="" (
    echo   ? Введите дату
    goto :ask_expire
)

:validate
set "RAW=!RAW_INPUT!"
echo(!RAW! | findstr /R /C:"^[0-9][0-9]?[.][0-9][0-9]?[.][0-9][0-9][0-9][0-9]([ ]([0-9][0-9]?:[0-9][0-9]))?$" >nul
if errorlevel 1 goto :bad_date

for /f "tokens=1" %%a in ("!RAW!") do set "DATEPART=%%a"
for /f "tokens=2" %%b in ("!RAW!") do set "TIMEPART=%%b"
if not defined TIMEPART set "TIMEPART=00:00"

for /f "tokens=1,2,3 delims=." %%a in ("!DATEPART!") do (
    set "D=%%a"
    set "M=%%b"
    set "Y=%%c"
)
for /f "tokens=1,2 delims=:" %%a in ("!TIMEPART!") do (
    set "H=%%a"
    set "MIN=%%b"
)
set /a M=!M! D=!D! Y=!Y! H=!H! MIN=!MIN!

if !M! LSS 1  goto :bad_date
if !M! GTR 12 goto :bad_date
if !D! LSS 1  goto :bad_date
if !H! GTR 23 goto :bad_date
if !MIN! GTR 59 goto :bad_date

rem Реальный календарный день (с учётом високосных лет)
set "MAXD=31"
if !M! EQU 4  set "MAXD=30"
if !M! EQU 6  set "MAXD=30"
if !M! EQU 9  set "MAXD=30"
if !M! EQU 11 set "MAXD=30"
if !M! EQU 2 (
    set "MAXD=28"
    set /a rem4=!Y! %% 4
    if !rem4! EQU 0 (
        set /a rem100=!Y! %% 100
        if !rem100! EQU 0 (
            set /a rem400=!Y! %% 400
            if !rem400! EQU 0 set "MAXD=29"
        ) else (
            set "MAXD=29"
        )
    )
)
if !D! GTR !MAXD! goto :bad_date

rem Нормализованная дата: дд.мм.гггг чч:мм
set "EXPIRE="
if !D! LSS 10 set "EXPIRE=0!D!." else set "EXPIRE=!D!."
if !M! LSS 10 set "EXPIRE=!EXPIRE!0!M!." else set "EXPIRE=!EXPIRE!!M!."
set "EXPIRE=!EXPIRE!!Y! "
if !H! LSS 10 set "EXPIRE=!EXPIRE!0!H!:" else set "EXPIRE=!EXPIRE!!H!:"
if !MIN! LSS 10 set "EXPIRE=!EXPIRE!0!MIN!" else set "EXPIRE=!EXPIRE!!MIN!"

echo.
echo   Срок окончания: !EXPIRE!
if "!EXPIRE_ARG!"=="" (
    rem В интерактивном режиме срок подтверждаем
    set /p CONFIRM=Продолжить? (y/n):
    if /i "!CONFIRM!"=="y" goto :run_build
    if "!CONFIRM!"=="д" goto :run_build
    if "!CONFIRM!"=="Д" goto :run_build
    goto :ask_expire
)
goto :run_build

:bad_date
echo   ? Неверная дата. Примеры: 31.12.2026 или 31.12.2026 23:59
if "!EXPIRE_ARG!"=="" goto :ask_expire
echo.
pause
exit /b 1

:run_build
echo.
echo Собираем:
echo   Тип сборки:     !BUILD_TYPE!
echo   Срок окончания: !EXPIRE!
echo.
call gradlew.bat clean build -PlauraBuildType=!BUILD_TYPE! "-PlauraExpire=!EXPIRE!"
if errorlevel 1 (
    echo.
    echo Сборка завершилась с ошибкой.
    echo.
    pause
    exit /b 1
)

rem --- ищем собранный JAR
set "JAR="
for %%f in (build\libs\*.jar) do (
    set "NAME=%%~nxf"
    echo(!NAME! | findstr /i /c:"-sources" >nul
    if not errorlevel 1 set "JAR=build\libs\!NAME!"
)
if not defined JAR (
    echo.
    echo Сборка прошла, но JAR не найден в build\libs\
    echo.
    pause
    exit /b 1
)

rem --- версия мода из fabric.mod.json
set "MOD_VER="
for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "(Get-Content -Raw 'src\main\resources\fabric.mod.json' ^| ConvertFrom-Json).version"`) do set "MOD_VER=%%v"
if not defined MOD_VER set "MOD_VER=dev"

if not exist build\release mkdir build\release
copy /y "!JAR!" "build\release\Laura-Client-v!MOD_VER!-!BUILD_TYPE!.jar" >nul

echo.
echo ==================================================
echo   Готово! Сборка завершена.
echo   JAR:   !JAR!
echo   Копия: build\release\Laura-Client-v!MOD_VER!-!BUILD_TYPE!.jar
echo   Тип:   !BUILD_TYPE!  ^|  Срок: !EXPIRE!
echo ==================================================
echo.
pause
exit /b 0

:show_help
echo Использование:
echo   build.bat                             - интерактивный режим (меню)
echo   build.bat dev                         - dev-сборка (срок 01.01.2099)
echo   build.bat public [дд.мм.гггг [чч:мм]] - public-сборка со сроком
echo.
pause
exit /b 0
