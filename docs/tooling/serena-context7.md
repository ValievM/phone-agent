# Serena и Context7

> См. также: [stages/a-environment.md](../stages/a-environment.md) (установка и конфиг) · [architecture/testing.md](../architecture/testing.md) · [README.md](../README.md)

Как агент работает с двумя MCP-серверами. Установка и блок для `~/.codex/config.toml` описаны в этапе A.

## Serena: навигация и правка кода по символам
Используй Serena вместо чтения файлов целиком:
- Файл незнакомый: сначала `get_symbols_overview`, потом нужный символ через `find_symbol`.
- Перед изменением сигнатуры или поведения: `find_referencing_symbols`, чтобы увидеть, кто это использует.
- Правка символа целиком: `replace_symbol_body`, вставка рядом: `insert_after_symbol` / `insert_before_symbol`.
- Поиск по тексту и регуляркам: `search_for_pattern`.
- В конце этапа сохрани короткую заметку в память Serena (этап, принятые решения, ловушки), в начале нового этапа прочитай её.
- Kotlin в Serena работает через экспериментальный language server. Если символы не находятся или ответы пустые, переключись на обычный поиск (`rg`) и скажи пользователю об этом. Не делай вид, что Serena сработала.
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
