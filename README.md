<p align="center">
  <img src="src/main/resources/assets/laura/icon.png" alt="Laura Client" width="140" height="140" />
</p>

<h1 align="center">Laura Client</h1>

<p align="center">
  <b>Анархический клиент для Minecraft 1.21.4</b><br/>
  <i>Модульный, с кастомными shader-ами, встроенным GUI-конфигом, альт-менеджером и своей темой.</i>
</p>

<p align="center">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.21.4-4e7b3f?logo=minecraft&logoColor=white" />
  <img alt="Java" src="https://img.shields.io/badge/Java-21+-ef4820?logo=openjdk&logoColor=white" />
  <img alt="Fabric" src="https://img.shields.io/badge/Fabric-0.18.4-844fba?logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAA4klEQVR42u3WQQrCQBAF0P9GZ7p0t7SrfQfP4EJcCP9qIyY9e3r6PytJNpMhXq4QgpYtW/Y8EBAgQIAAAQIEvksAARcCEICAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAECBAgQIECAAAEC/xDwATk4Jx8mXqJ1AAAAAElFTkSuQmCC" />
  <img alt="Fabric API" src="https://img.shields.io/badge/Fabric%20API-0.119.4%2B1.21.4-8f5e2e" />
  <img alt="Build" src="https://img.shields.io/badge/build-gradle%20loom-2f7d34" />
</p>

<p align="center">
  <a href="#features">Возможности</a> ·
  <a href="#modules">Модули</a> ·
  <a href="#commands">Команды</a> ·
  <a href="#installation">Установка</a> ·
  <a href="#build">Сборка</a> ·
  <a href="#usage">Использование</a> ·
  <a href="#stack">Стек</a>
</p>

---

## 🧭 О проекте

**Laura Client** — клиентский мод для Minecraft **1.21.4** (Fabric). Ориентирован на
анархические сервера и автоматизацию рутинных действий, но при этом имеет аккуратное
кастомное меню вместо стандартного и полностью настраиваемую инфо-панель (watermark).

Ключевые особенности:

- 🎨 **Кастомный GUI** — собственное главное меню (кнопки с пружинными hover/press
  анимациями и каскадным появлением), радиальное меню, альт-менеджер и
  «клик-гуи» (StationScreen) с иконкой клиента.
- 🌀 **Пружинная система анимаций** (`laura/render/Spring.java`) — физика пружины
  с овершутом для hover, press, тумблеров и слайдеров; staggered-вход панелей GUI.
- 🛠️ **107 модулей** в 5 категориях (Combat, Movement, Render, Player, Misc) с настройками.
- ✨ **Собственные core-шейдеры** (blur, noise, rect, text), регистрируемые через
  `ShaderProgramKeys` до первого релоада ресурсов.
- 🖥️ **Кастомный рендер** — MSDF-шрифты, иконки, плавные анимации, эффекты частиц.
- ⚙️ **Гибкая конфигурация** — сохранение/загрузка конфигов, макросы, бинды, layout панелей.
- 🔌 **Свой бэкенд** — защищённый WebSocket-клиент с криптографической подписью пакетов,
  интеграция с Discord Rich Presence.
- 🎵 **Настраиваемый звук** и **Streamer Mode** для приватности.

> **⚠️ Статус:** проект в активной разработке. На часть модулей, завязанных на бэкенд
> (например, `Captcha Solver`), могут влиять внешние сервера.

---

## ✨ Возможности

### 🖥️ GUI и внешний вид
- **Главное меню** (заменяет ванильное) — фоновый блюр, кастомные шрифты, кнопки.
- **Радиальное меню** и **Alt-менеджер** для быстрого переключения аккаунтов.
- **Infra-панель (watermark)**: задержка, FPS, время, сервер + логотип `laura:icon.png` из ресурсов.
- **Кастомные шрифты**: `gt_regular`, `sf_regular`, `sf_medium`, `onest_regular`, `icons`.
- **Настраиваемый layout** панелей (`HUD`) с сохранением позиций — `.layout`.

### 🛡️ Бой и автоматизация
- **Combat**: Aura, Trigger Bot, Anti Bot, Velocity, Auto Totem, Hit Boxes, Mace Helper…
- **Movement**: Fly, Scaffold, Safe Walk, Speed-подобные модули, Elytra, Free Camera…
- **Misc**: Auto Buy (FunPay/магазин), Collector, Auto Warden, Ancient/Apple Farmer, Nuker, X Ray…
- **Player**: Auto Eat, Auto Fish, Chest Stealer, Fast Load/EXP, Auto Tool, Death Coords…

### 🧩 Модульные команды и конфиги
Полный список команд — в [разделе ниже](#commands). Конфиги хранятся в `.json`,
управляются через `.cfg <save|load|list|reset|remove|dir>`.

---

## 📦 Модули

107 модулей, сгруппированных по категориям:

| Категория | Описание | Кол-во |
|---|---|---|
| ⚔️ **Combat** | Атака, прицел, защита, анти-кик | 20 |
| 🏃 **Movement** | Полёт, движение, элитра, камера | 16 |
| ✨ **Render** | Видение, ESP, шейдеры, интерфейс, визуальные эффекты | 25 |
| 🧍 **Player** | Автоматизация, инвентарь, утилиты | 25 |
| 🛠️ **Misc** | Фарм, серверные помощники, чат, звук | 20 |
| **Итого** | | **107** |

### ✨ Визуальные эффекты (Render)

| Модуль | Что делает |
|---|---|
| **Damage Indicator** | Летающие цифры урона над сущностью (свой урон / урон по вам / урон по мобам), подсветка критов и хитмаркер на прицеле. |
| **Electric Arcs** | Электрические дуги из двух проходов (яркое ядро + широкое свечение) между вами и целью под прицелом или игроками рядом. |
| **Trails** | Затухающий шлейф за игроком, другими игроками и мобами; цвет берётся из темы клиента. |
| **Pulse Rings** | Расходящееся кольцо в точке приземления сущности и пульсирующий круг под целью под прицелом. |

Все четыре модуля чисто клиентские: они только рисуют и не отправляют на сервер
никаких пакетов, поэтому не влияют на игровую логику и античит.

Все модули настраиваются через GUI и имеют горячие клавиши (бинды). Точки входа модулей
описаны аннотацией `@ModuleRegister(name, description, category)`.

---

## 💬 Команды

Все команды вызываются с префиксом `.` (точка) в игровом чате. Команды построены на
**Brigadier** (`net.minecraft.command.CommandSource`).

| Команда | Описание |
|---|---|
| `.ah` | Аукцион / торговля |
| `.bind` | Привязка модуля к клавише — `.bind add <модуль> <клавиша>` |
| `.blockesp` | ESP на блоки — `.blockesp add <блок> [цвет]` |
| `.ccc` | Клиентские команды |
| `.cfg` | Конфиги — `.cfg save\|load\|list\|reset\|remove\|dir <имя>` |
| `.friend` | Друзья — `.friend add\|remove\|list\|clear <ник>` |
| `.gps` | Координаты — `.gps <x> <z>`, `.gps off` |
| `.hclip` | Горизонтальный телепорт — `.hclip <число\|forward\|back>` |
| `.layout` | Схема HUD-панелей — `.layout save\|load\|remove\|list\|clear <название>` |
| `.macros` | Макросы клавиш — `.macros add <клавиша> <команда>` |
| `.rct` | Радиальное меню |
| `.staff` | Стафф-лист — `.staff add\|remove\|list\|clear <ник>` |
| `.vclip` | Вертикальный телепорт — `.vclip <число\|up\|down>` |
| `.warden` | Анархия-сервера — `.warden add\|remove\|list\|clear <анархия>` |
| `.way` | Точки (waypoints) — `.way add\|me\|remove\|list\|clear\|event` |

---

## 🚀 Установка

### Требования
| Зависимость | Версия |
|---|---|
| [Minecraft](https://www.minecraft.net/) | **1.21.4** |
| [Fabric Loader](https://fabricmc.net/use/installer/) | **≥ 0.16.5** (заявлено `0.18.4`) |
| [Fabric API](https://modrinth.com/mod/fabric-api) | **0.119.4+1.21.4** |
| Java | **21+** |

### 🪟 Установщик для Windows (рекомендуется)
В папке [`windows/`](windows/README.md) — готовый установщик: ставит Java 21
(при отсутствии — через winget), чистый инстанс 1.21.4 + Fabric, клиент и
Fabric API, и **сам запускает игру** без лаунчера Mojang.

- **Быстро:** `gradlew build` → скопировать `build\libs\laura-client-*.jar` в
  `windows\` → два клика на `laura-launch.bat`.
- **Как .exe:** либо через GitHub Actions (workflow «Windows Installer» →
  артефакт `Laura-Client-Setup` с готовым `Laura-Client-Setup.exe`), либо
  локально: собрать `windows\Laura-Installer.iss` в Inno Setup 6 (ярлыки, без
  прав администратора).
- troubleshooting «установщик не запускается» — в [`windows/README.md`](windows/README.md).

### Готовый JAR
1. Соберите или скачайте собранный `laura-client-*.jar`.
2. Скопируйте его в папку `mods/` вашего инстанса **1.21.4**.
3. Убедитесь, что установлен Fabric Loader и **Fabric API**.
4. Запустите игру.

> **`IllegalStateException: duplicate ASM classes found on classpath` сразу после
> запуска** (в стеке `LoaderUtil.verifyClasspath`, `Knot.<clinit>`) — это баг
> лаунчера, а не клиента: он собрал classpath из двух профилей и положил в него
> и `org.ow2.asm:asm:9.6` из профиля Minecraft 1.21.4, и `asm:9.9` из профиля
> Fabric-лоадера, а лоадер не терпит двух версий ASM. Свой лаунчер
> ([`launcher/`](launcher/README.md)) перекрывает профиль Mojang профилем
> лоадера; в стороннем лаунчере — обнови его либо удали папку
> `libraries/org/ow2/asm/9.6` в инстансе.

---

## 🔨 Сборка из исходников

Проект использует **Fabric Loom** (gradle). Требуется JDK 21.

```bash
# Локальный запуск с dev-окружением (клиент)
./gradlew runClient

# Чистая сборка (JAR будет в build/libs/)
./gradlew build

# Собрать только JAR
./gradlew jar
```

Собранный артефакт: `build/libs/laura-client-<version>.jar`.

> **Примечание.** При первом запуске Gradle скачает зависимости из Maven Central и
> `https://maven.fabricmc.net/`, а Loom подготовит тиры `minecraft` и `yarn`.

---

## 🎮 Использование

- **Открыть GUIScreen**: нажмите **Right Shift** (клавиша `344`) при закрытом экране —
  появится панель клиента (MainScreen / GUIScreen).
- **Главное меню**: клиент заменяет ванильный `TitleScreen` (mixin `TitleScreenMixin`).
- **Alt-менеджер**: в главном меню есть кнопка перехода в `AltScreen`.
- **Настройка модулей**: в GUI, либо через `.bind` и `.cfg`.
- **Инность с бэкендом**: клиент подключается к защищённому WebSocket-серверу для
  синхронизации, Discord-активности и части модулей (auth — по токену).

---

## 🎵 Spotify HUD

Виджет «Сейчас играет» (категория **Render**, модуль **Spotify HUD**) показывает
обложку, название трека, исполнителя, прогресс-бар и позволяет управлять плеером
горячими клавишами. Работает через официальный **Spotify Web API** (OAuth2).

### Настройка (1 раз)

1. Зайдите в [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
   и создайте приложение (**Create app**).
2. Откройте **Settings** приложения — там будут **Client ID** и **Client Secret**.
3. В **Redirect URI** добавьте любой адрес, например `http://localhost:8888/callback`
   (он нужен только для получения токена; сам клиент его не открывает).
4. Получите **Refresh Token** по Authorization Code flow с этими **scopes**:

   ```
   user-read-playback-state
   user-modify-playback-state
   user-read-currently-playing
   ```

   Удобно использовать скрипт из официальной документации Spotify (Authorization Code
   с PKCE) — сохраните полученный `refresh_token` (он живёт долго и именно он нужен
   клиенту, `access_token` подтягивается автоматически).
5. Вставьте **Client ID**, **Client Secret** и **Refresh Token** в настройки модуля
   **Spotify HUD** и нажмите **«Проверить подключение»**.

> ⚠️ Клиент работает на одной учётке Spotify. Убедитесь, что Spotify запущен и
> что-то играет на том же аккаунте, иначе виджет покажет «Ничего не играет».

### Два режима позиции

У модуля есть настройка **«Режим позиции»** с двумя вариантами:

| Режим | Что делает |
|---|---|
| **По углам** | Виджет автоматически прижимается к выбранному углу экрана (настройка «Угол»). |
| **Своя позиция** | Виджет ставится туда, куда вы сами его перетащите. Точные координаты тоже доступны как ползунки «Позиция X/Y (%)». |

Чтобы перетащить виджет в свободном режиме, откройте **чат** и просто потяните его
мышью — позиция сохранится в конфиг (`default.json`) и переживёт перезапуск.

### Что означает текст в виджете

- `Spotify не подключён` — нужно вписать Client ID/Secret/Refresh Token.
- `Ошибка авторизации` — данные не приняты (показан HTTP-код причины).
- `Нет соединения` — нет доступа к интернету/API.
- `Ничего не играет` — Spotify работает, но сейчас ничего не воспроизводится.

---

## 🛠️ Технический стек

- **Minecraft 1.21.4** + **Fabric Loader / Fabric API** (`ClientModInitializer`).
- **Gradle + Fabric Loom** (`${loom_version}`), Java 21, `-Xmaxerrs`.
- **Библиотеки**: Gson, Log4j, Java-WebSocket, Javassist, Reflections, GeoClib, jsoup и др.
- **Файлы**: `fabric.mod.json`, `laura.mixins.json`, `laura.accesswidener`, кастомные core-шейдеры.
- **Архитектура**: пакеты `laura.*` (core, module, ui, render, config, command, network, cosmetic,
  discord) + `platform.*` (initializer, mixins, accessors) + `baritone.*` (API).

---

## 📄 Лицензия

Проект распространяется под **Minecraft EULA** (указано в `fabric.mod.json`). Используйте
в соответствии с правилами Minecraft и серверов, на которых играете.

---

<p align="center">
  Сделано с ❤️ для Minecraft 1.21.4.<br/>
  <a href="https://github.com/Ironcarrier228/Laura-Client-1.21.4">github.com/Ironcarrier228/Laura-Client-1.21.4</a>
</p>
