# Карта переноса `:app` на FSD

Снимок исходников `dev`, 2026-10-09, по ADR 0008 и текущим imports. Это карта переноса, не кодовая миграция. Полный инвентарь 208 Kotlin-файлов находится в [app-файлах](fsd-map-files.md) и [модулях domain/data/tools](fsd-map-modules.md); слой, слайс, сегмент и причина указаны у каждого файла. Тесты переносятся с owning slice. Сегменты: `model` — модели/состояния/порты/чистая логика; `ui` — Compose; `api` — платформенные и внешние адаптеры; `lib` — локальные утилиты.

## Целевые слайсы и границы

| Слой | Слайс | Граница |
|---|---|---|
| `shared` | `design_system` | AppTheme, tokens, icons, platform-neutral Compose primitives and previews; does not know screens or entities. |
| `entities` | `conversation` | `HistoryItem`, ID, turn-state и retry/context policy. |
| `entities` | `tool_call` | Tool protocol, descriptions, arguments, result/content и pipeline contracts. |
| `entities` | `interaction` | Approval/permission requests и UI-independent interaction port. |
| `entities` | `model_access`, `auth`, `speech`, `settings`, `platform_services`, `agent_run` | Чистые модели и порты соответствующей предметной границы; циклы между entity slices вынести в `shared` API types, а не разрешать взаимные импорты. |
| `features` | `send_message` | Запуск/останов хода, отправка, retry, сохранение истории и обработка agent events; Compose остаётся в screen/widget. |
| `features` | `voice_dictation` | Запрос диктовки, состояния/ошибки и append-to-draft; Android recognizer, system dialog и lifecycle host только в `androidMain`. |
| `features` | `approve_call` | Подтверждение/редактирование одного и batch tool calls; использует entities contracts, не tool-specific UI. |
| `features` | `copy_share_answer` | Копирование/передача ответа; внешние Android intents — adapter сегмент. |
| `features` | `auth`, `settings`, `model_backend`, `conversation_history` | Auth/login orchestration, пользовательские настройки, Responses API и хранилище истории; порты в entities, реализации здесь. |
| `features` | `tool_alarm`, `tool_notify`, `tool_files`, `tool_status` | Один слайс на инструмент/связанную группу файлового поиска; регистрация и реализация принадлежат слайсу. |
| `widgets` | `composer`, `message_list`, `tool_call_card`, `approval_sheet`, `voice_bar` | Повторно используемые цельные UI-блоки, зависящие только от features/entities/shared. |
| `screens` | `chat`, `login`, `settings`, `about` | Маршруты и композиция виджетов конкретной страницы; не импортируют друг друга. |
| `app` | `composition_root` | MainActivity/Application, DI bindings, навигация, theme root, platform bridges. Только этот верхний слой соединяет независимые features/adapters. |

`tools` — слайсы `features.tool_*`, а не новый FSD-слой: порт `Tool` и общие типы остаются в `entities.tool_call`. `data:llm` → `features.model_backend`; `data:auth` → `features.auth`; `core:domain` распределяется в `entities` и частично `shared`. Это не делает конкретные инструменты общими `entities`: Android/SAF/HTTP реализации остаются feature adapters.

## Границы направления: обнаруженные нарушения

По imports 208 production Kotlin-файлов и destination-таблице нашлись 60 текущих boundary-imports без generated resources. После переноса auth contract вниз останется 51: 49 sibling-imports внутри `entities`, 1 import app bridge из chat и 1 соседний screen import. Ещё 43 `R`-ссылки пока используют ресурсы `:app`/`:tools` и должны переехать с ресурсами в owning Android-модули. Таблица фиксирует место и способ устранения каждого класса.

| Текущее место | Направление/проблема | Разрез |
|---|---|---|
| `ui.login.LoginViewModel`, `ui.settings.SettingsContract`, `ui.settings.SettingsViewModel`, `ui.settings.SettingsScreen`, `ui.settings.SettingsScreenPreview` → `app.auth.*` | 9 текущих imports screens → app; контракт `AuthCoordinator.kt`/состояния направлены в `features.auth`, а реализация остаётся в app. | Разнести публичный auth facade/state и Hilt coordinator; screen должен зависеть только от feature API. |
| `ui.chat.ChatViewModel` → `interaction.UserInteractionBridge` | Один import из `screens` напрямую в app bridge. | Chat использует `entities.interaction` contract; bridge принимает/передаёт события через composition root. `PendingInteraction`/состояние не должно жить в слое app, если потребитель ниже. |
| `ui.about.AboutLicenseSections` → `ui.settings.SettingsSection` | Один import соседнего slice `screens.settings`. | Перенести общий section layout в `shared.design_system` (либо общий widget) и оба экрана зависят вниз. |
| 43 импорта `com.example.phoneagent.R` из screens/widgets/features, включая `tools` resource reference | Ссылки на app/tools-generated resources сохраняют зависимость на модуль сборки, не на Kotlin API. | Переместить ресурсы вместе с компонентами; Android-модуль owning slice генерирует собственный `R`, а tools drawable уезжает с `tool_notify`. |
| `core:domain` пакеты (`agent`, `conversation`, `interaction`, `model`, `platform`, `tools`) импортируют типы друг друга | 49 import-ребёр между будущими slices одного `entities` слоя; граф содержит цикл `agent ↔ conversation` плюс interaction/tool/model/platform связи. | Перенести совместно используемые ID, events/results и contracts в нижний `entities` API либо `shared`; фиксировать граф до деления файлов и не допускать циклов. |

Верхнеуровневые модули `:tools`, `:data:llm`, `:data:auth` и текущий `:app` закономерно импортируют `:core:domain`; при переносе это направление сохраняется как нижележащий `entities`/`shared`. `features.model_backend` внутренние `dto/request/sse` пакеты следует считать одним слайсом. `app` может знать про любые features, но screen/widget/feature не должен импортировать `app` или вышележащий слой.
Порядок 14 самостоятельных переносов, оценки строк/тестов и platform-only перечень вынесены в [план миграции срезами](fsd-map-plan.md), чтобы карта и инвентарь оставались небольшими и независимо проверяемыми.


## Что остаётся Android-only

- Compose UI пока Android-only: shared UI-kit и widgets не означают Compose Multiplatform. Решение о CMP/iOS отдельное.
- `features.voice_dictation`: SpeechRecognizer/system speech dialog, recognizer intents, Android lifecycle/host, permission checks.
- `features.tool_alarm`: `AlarmManager`-подобная системная интеграция через `SET_ALARM` intent/launcher; `tool_notify`: NotificationManager/канал; `tool_status`: батарея/PowerManager/StatFs.
- `features.tool_files`: SAF `DocumentsContract`/tree URI и `MediaStore`; чтение URI-grants — Android API.
- `features.auth` adapters: Keystore-backed encrypted token store, Custom Tabs/browser callback/loopback receiver и Android manifest declarations.
- `features.conversation_history`/`settings` Room/DataStore реализации сохраняются в `androidMain`, пока не одобрены KMP-совместимые замены и миграция.
- `app`: Hilt graph, Activity/Application, navigation host, Android resource/version/license access, picker intents и platform bridges.

## Полнота и воспроизводимость

Inventory records one destination per path in `git ls-files 'app/src/main/**/*.kt' 'tools/src/main/**/*.kt' 'data/*/src/main/**/*.kt' 'core/domain/src/main/**/*.kt'`; it is split between [app files](fsd-map-files.md) and [domain/data/tools](fsd-map-modules.md). Current total 208. Compare source paths to mapping prefixes, then assert `missing=duplicate=extra=0`; for mutation, remove one filename in an inventory copy and rerun: `missing` becomes nonzero. This is a document-only check, not a runtime utility.

Направление проверяется по package imports: каждый экран/feature должен импортировать только shared/entities или нижние features, а dependency граф слайсов не должен содержать обратных рёбер. Срез самодостаточен, если все production зависимости находятся внутри него или в уже перенесённых шагах таблицы; перечисленные prerequisites задают этот порядок. Ключевой риск — entity cycle и app-boundary leakage, поэтому срезы 3, 6, 10, 13 требуют отдельного ревью imports и тестов до коммита.
