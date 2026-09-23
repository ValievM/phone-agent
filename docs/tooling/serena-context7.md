# Serena и Context7 (только в этом проекте)

> См. также: [stages/a-environment.md](../stages/a-environment.md) (история установки) · [architecture/testing.md](../architecture/testing.md) · [README.md](../README.md)

## Область действия
Решение 2026-09-23: Serena и Context7 выключены глобально (`enabled = false` в
`~/.codex/config.toml`, чтобы не грузить их зря в остальных проектах пользователя) и
включены обратно только для phone-agent через project-scoped `.codex/config.toml` в
корне репозитория (Codex CLI читает его для доверенных проектов — `trust_level =
"trusted"` в `[projects."<путь>"]` глобального конфига). Файл в git, поэтому применяется
одинаково во всех worktree этого репозитория; **для нового worktree вне уже доверенных
путей может понадобиться доверить его вручную** (добавить `[projects."<путь>"]
trust_level = "trusted"` в `~/.codex/config.toml`) — Codex либо запросит это
интерактивно, либо project-local конфиг тихо не подхватится, тогда просто работайте
обычным поиском без вопросов пользователю.
В Claude Code ни Serena, ни project-scoped Codex-конфиг не участвуют — здесь всегда
обычный поиск (`Grep`/`Read`) и свой Context7 MCP, если он подключён отдельно.

## Serena: навигация и правка кода по символам
Используй Serena вместо чтения файлов целиком:
- Файл незнакомый: сначала `get_symbols_overview`, потом нужный символ через `find_symbol`.
- Перед изменением сигнатуры или поведения: `find_referencing_symbols`, чтобы увидеть, кто это использует.
- Правка символа целиком: `replace_symbol_body`, вставка рядом: `insert_after_symbol` / `insert_before_symbol`.
- Поиск по тексту и регуляркам: `search_for_pattern`.
- В конце этапа сохрани короткую заметку в память Serena (этап, принятые решения, ловушки), в начале нового этапа прочитай её.
- Kotlin в Serena работает через экспериментальный language server (настройка — см. архив ниже). Если символы не находятся или ответы пустые, переключись на обычный поиск (`rg`) и скажи пользователю об этом. Не делай вид, что Serena сработала.
- Вызывай только инструменты, которые реально есть в списке MCP. Если имя отличается, ориентируйся на список.
- В Codex-приложении (не CLI) в начале сессии выполни: «Activate the current dir as project using serena».

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

## Архив: настройка Kotlin в Serena (проверено 21-22.09.2026)
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
