# Этап 1. Каркас приложения

> Читать перед началом: [архитектура](../architecture/overview.md) · [ядро](../architecture/core.md) · [тесты и TDD](../architecture/testing.md) · [дизайн](../design/overview.md) · [стиль](../design/style.md) · [библиотеки](../design/libraries.md) · [правила вёрстки](../design/layout-rules.md)

**Цель:** приложение с правильным скелетом: модули, порты, фейки и защита границ уже на месте, чтобы дальше добавлять функции, а не переделывать основу.

- [ ] 1.1 Создан проект (Kotlin, Compose, Material 3, minSdk 26, targetSdk актуальный); версии в `gradle/libs.versions.toml`; общая конфигурация модулей в `build-logic/` (convention plugins).
- [ ] 1.2 Модули по [architecture/overview.md](../architecture/overview.md) (раздел «Модули»): `:app`, `:core:domain` (Kotlin JVM), `:data:llm` (Kotlin JVM), `:data:auth`, `:tools`; в библиотечных модулях включён `explicitApi()`.
- [ ] 1.3 В `:core:domain` объявлены порты и базовые типы ([architecture/core.md](../architecture/core.md)): `ModelGateway`, `ModelEvent`, `Tool` (с `describe`), `ToolSpec`, `CallDescription`, `Risk`, `UserInteraction`, `CredentialsProvider`, `ConversationRepository`, `SettingsRepository`, `Clock`, `DispatcherProvider`, `AppLogger`, `AgentError`, `HistoryItem` (со слотом `Opaque`). Рядом `testFixtures` с фейками портов.
- [ ] 1.4 Hilt настроен, DI-сборка только в `:app/di`. В `:core:domain` и `:data:llm` нет DI-аннотаций. Константы входа и бэкенда: заглушки в `AuthConfig` и `BackendProfile` с пометкой этапа.
- [ ] 1.5 Подключены ktlint/spotless и detekt; написаны архитектурные тесты Konsist (правила из раздела «Защита архитектуры автоматикой» в [architecture/testing.md](../architecture/testing.md#защита-архитектуры-автоматикой)); `./gradlew check` проходит.
- [ ] 1.6 Экран чата по UDF: `ChatRoute` + `ChatScreen`, `ChatViewModel`, `ChatUiState`, `ChatUiEvent`; ответ выдаёт `FakeModelGateway`, а не «эхо» внутри UI.
- [ ] 1.7 Манифест: только `INTERNET`; `android:allowBackup="false"`.
- [ ] 1.8 Sanity-тесты: один в `:core:domain`, один на `ChatViewModel` с фейком.
- [ ] 1.9 `.gitignore` проверен, коммиты по Conventional Commits.
- [ ] 1.10 Serena видит Kotlin: `get_symbols_overview` на `MainActivity.kt` возвращает символы. Если нет, в `AGENTS.md` записано, что Kotlin LS не работает и используется обычный поиск.
- [ ] 1.11 Скелет дизайн-системы `ui/designsystem` по документам [design/](../design/overview.md): `AppTheme` (светлая/тёмная, динамические цвета + запасная палитра из Theme Builder, переключатель), типографика с вшитыми шрифтами (выбраны Roboto Flex или Inter + JetBrains Mono, кириллица проверена), формы, токены `spacing`, `AppExtendedColors`, `AppIcons` с первыми иконками (vector drawable из Material Symbols Rounded).
- [ ] 1.12 Подключены `compose-rules` (ktlint или detekt), `enableEdgeToEdge()`, Navigation 3 с ключами `Chat` и заглушкой настроек, `core-splashscreen`, заглушки иконки приложения (adaptive + monochrome).
- [ ] 1.13 Превью темы и компонентов-заглушек проходят Design QA из [design/qa.md](../design/qa.md) (в объёме этапа).

**Тест-лист (пишем первым):**
- Konsist: «`:core:domain` не импортирует `android.*`» (красный при намеренном нарушении, зелёный после отката); то же для зависимости инструментов друг от друга.
- `ChatViewModel`: отправка сообщения → `isRunning = true` → ответ фейка появился в списке → `isRunning = false`.
- `ChatViewModel`: ошибка от фейковой модели → в состоянии ошибка, приложение не падает.
- Konsist (дизайн): `Color(0x…)` только в `ui/designsystem/theme`; `designsystem` не импортирует экраны; markdown-библиотека только внутри `AssistantMarkdown`; возможность правила «нет литералов в `Text("…")`» подтверждена или заменена проверкой lint.

**Что должно быть по итогу:** приложение стартует на реальном телефоне и ведёт чат на фейковой модели; структура совпадает с [architecture/overview.md](../architecture/overview.md); нарушить границы модулей нельзя без красной сборки.

**Как проверить:**
- `./gradlew assembleDebug` и `./gradlew check` без ошибок; запуск из Android Studio на устройстве; сообщение получает ответ от `FakeModelGateway`.
- Превью темы в Android Studio: светлая, тёмная, динамические цвета, масштаб шрифта 2.0; строка «Ёё Щщ Ъъ Ыы» отображается выбранным шрифтом.
- **Проверка защиты:** временно добавить в `:core:domain` импорт `android.util.Log` (или зависимость OkHttp), убедиться, что `check` падает, и откатить. Так же для импорта одного инструмента из другого.

**Ворота:** запуск на устройстве, `./gradlew check` зелёный, защита границ реально срабатывает.

**Стартовый промпт:** «Работаем над этапом 1 по блоку «Читать перед началом» этого этапа. Сначала план: список модулей, зависимостей и порядок создания. Жди ок.»

---

Следующий этап: [2-auth.md](2-auth.md). Возврат к списку: [README.md](README.md).
