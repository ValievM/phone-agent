# Context7 (Serena отключён)

> См. также: [stages/a-environment.md](../stages/a-environment.md) (история установки) · [architecture/testing.md](../architecture/testing.md) · [README.md](../README.md)

## Serena — отключена, не использовать
Решение 2026-09-23: Serena отключена глобально (`enabled = false` в `[mcp_servers.serena]`
в `~/.codex/config.toml`; в Claude Code она и не была подключена). Причина — фрагильная
Kotlin-обвязка требовала повторной ручной починки (просроченные сборки `intellij-server`,
конфликт KSP2/KSP1, путь к JDK 17) чаще, чем реально экономила время. Не пытайтесь её
включать и не советуйте пользователю чинить конфиг — вместо неё: `rg`/обычный grep по коду,
`Read`/`Grep` в Claude Code, `get_symbols_overview`-подобной замены нет, читайте файл целиком
или нужный диапазон строк. История настройки (на случай если решение пересмотрят) сохранена
ниже в этом файле.

## Context7: актуальная документация библиотек
Не полагайся на память для API библиотек. Перед кодом, который использует Compose, AndroidX (DataStore, Room, Keystore), OkHttp, kotlinx.serialization, kotlinx.coroutines, Navigation 3, markdown-библиотеку:
1. `resolve-library-id` (библиотека + твой вопрос) → выбери подходящий ID.
2. `query-docs` (ID + конкретный вопрос) → используй ответ.

## Context7: актуальная документация библиотек
Не полагайся на память для API библиотек. Перед кодом, который использует Compose, AndroidX (DataStore, Room, Keystore), OkHttp, kotlinx.serialization, kotlinx.coroutines, Navigation 3, markdown-библиотеку:
1. `resolve-library-id` (библиотека + твой вопрос) → выбери подходящий ID.
2. `query-docs` (ID + конкретный вопрос) → используй ответ.

Правила:
- Указывай версию из `gradle/libs.versions.toml`.
- Не больше 2-3 вызовов на один вопрос, большие выдержки не вставляй в ответ.
- **В запросах в Context7 нет токенов, ключей, данных пользователя и чужого кода.** Запросы уходят на внешний сервис.
- Context7 только для библиотек. Контракт входа и бэкенда OpenAI берётся из `docs/backend-notes.md` (после этапа 0, раньше: [research/backend-facts.md](../research/backend-facts.md)) и исходников `openai/codex`, а не из Context7 и не из памяти.
- Если имя инструмента отличается от `query-docs`, смотри актуальный список инструментов.

## Архив: настройка Kotlin в Serena (не актуально, Serena отключена)
- В `.serena/project.yml` включены `language_servers: [kotlin, python]`. Serena выбирает языки при первой активации, а тогда в проекте был только Python-скрипт, поэтому Kotlin не был включён.
- Kotlin LS (`intellij-server`) в Serena 1.7.0 закреплён на сборке `262.9593.0`, она **просрочена** («This build of intellij-server has expired»). Новая сборка задана в **глобальном** `~/.serena/serena_config.yml` (в `project.yml` эта настройка не действует):
  ```yaml
  ls_specific_settings:
    kotlin:
      kotlin_lsp_version: '263.4702.0'
      jvm_options: '-Xmx2G'
  ```
  Сборка скачивается с `download-cdn.jetbrains.com`, для непроверенных Serena версий хеш не сверяется. Если Serena снова пишет «expired», обнови `kotlin_lsp_version` (список релизов: репозиторий `Kotlin/kotlin-lsp`) или обнови Serena.
- Проверка: `serena project health-check` должен закончиться «Health check passed».
- Импорт Gradle с KSP2 падал («Querying the mapped value ... before task ':app:kspDebugKotlin' has completed»), из-за этого в `:app` не разрешались типы Android и Hilt. Исправлено 22.09.2026: в `gradle.properties` стоит `ksp.useKSP2=false` (KSP1). После этого импорт проходит, `assembleDebug` и `./gradlew check` зелёные. Цена: KSP1 объявлен устаревшим, при переходе на новые версии KSP вернёмся к вопросу (проверить, починили ли импорт с KSP2). Оставшиеся предупреждения `[IMPORT ERR]` в логе Kotlin LS (`onVariants`, отсутствие `javaPreCompile` для `debugAndroidTest`) не блокируют работу; часть уходит после сборки `assembleDebugAndroidTest`.
- Сборка из терминала требует JDK 17 (toolchain в проекте), а Gradle сам его не находит (JDK установлен через Homebrew). Команда: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew check -Dorg.gradle.java.installations.paths=$JAVA_HOME`. Чтобы не повторять, путь можно один раз прописать в `~/.gradle/gradle.properties` (`org.gradle.java.installations.paths=...`).
- После смены конфига перезапусти сессию Codex: MCP-сервер читает настройки при старте.
