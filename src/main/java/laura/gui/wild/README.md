# WildClient-style ClickGUI для Laura

`ClickGuiScreen.java` — это **новый** ClickGUI, написанный с нуля на Laura API,
визуально вдохновлённый `ClickGuiScreen` из Wexside-1.21.8.

## Что это НЕ

Это **НЕ перенос** wild `ClickGuiScreen.java`. Тот требует переноса ~130 файлов
зависимостей из Wexside (ThemeManager, BlurStateManager, FeatureManager с
зависимостью на все 120 модулей, и т.д.) — это нереалистично.

## Что это

Полностью **самостоятельный** GUI в wild-стиле:

- ✅ Категории слева как большие цветные кнопки
- ✅ Список модулей в центре с hover-эффектом
- ✅ Панель настроек модуля справа
- ✅ Поиск по модулям вверху
- ✅ Анимация открытия/закрытия
- ✅ Цветовая схема в стиле Wild (можно переключать AccentColor)

## Что заменено

| Wild (Wexside-1.21.8)         | Laura (наш аналог)                              |
|-------------------------------|-------------------------------------------------|
| `ru.wild.WildClient`          | `laura.core.Laura.getInstance()`                |
| `FeatureManager` (120 модулей) | `ModuleProcessor` (`getModuleProcessor().t()`) |
| `ModuleCategory`              | `laura.core.Category`                           |
| `ThemeManager`/`ThemePalette` | встроенные `AccentColor` enum (4 темы)         |
| `BlurStateManager`            | `laura.ui.shader.BlurShader` (через `Draw2DProcessor`) |
| `RoundedRectRenderer` (2276 строк) | `Draw2DProcessor.a(...)` — наш rectangle shader |
| `EasingFunctions`             | `laura.render.EasingList`                       |
| `FontRegistry`                | встроенный `MinecraftClient.textRenderer`       |
| `SyntheticKeyState`           | `org.lwjgl.glfw.GLFW`                           |
| `PanelHitTest` и т.д.         | стандартный hit-test в нашем коде               |

## Как включить

1. Включи модуль **GUI Selector** в категории Misc
2. Выбери стиль **"Wild Classic"**
3. Нажми **RShift** в игре — откроется этот GUI

## Структура кода

- `ClickGuiScreen extends Screen` — главный класс (403 строки)
- 3 внутренние области:
  - **Левая панель категорий** — `renderCategoryPanel()`
  - **Центральная панель модулей** — `renderModuleList()`
  - **Правая панель настроек** — `renderSettingsPanel()` (только когда модуль выбран)
- **Поиск** — `renderSearchBar()` + `searchFocused` флаг
- **Анимация** — `openAnimation.a(true)` каждый кадр в `render()`

## Цветовые схемы

Встроены 4 темы через enum `AccentColor`:
- **WILD** (синий акцент) — по умолчанию
- **CHERRY** (розовый акцент)
- **MINT** (мятный акцент)
- **SUN** (оранжевый акцент)

Каждая задаёт 3 цвета: `accent`, `background`, `foreground`.
Чтобы добавить новую — просто добавь enum-значение.

## Что можно улучшить

- [ ] Сделать панели перетаскиваемыми (drag-and-drop)
- [ ] Добавить настройки модуля как в `GUIScreen` (через `Element.onDrawEvent`)
- [ ] Анимация переключения категорий
- [ ] Иконки для категорий
- [ ] Скроллинг модулей когда их много
- [ ] Поддержка мульти-выбора модулей с зажатым Shift
