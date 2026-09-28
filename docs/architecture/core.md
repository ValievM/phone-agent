# Ядро (`:core:domain`)

> См. также: [обзор](overview.md) · [адаптеры](adapters.md) · [тесты](testing.md)

## Структура пакетов
```
com.example.phoneagent.domain
├── agent/         AgentLoop, AgentEvent, AgentConfig, AgentError
├── conversation/  HistoryItem, ToolCall, ContextWindowPolicy
├── model/         ModelGateway (порт), ModelRequest, ModelEvent, ModelCatalog (порт)
├── tools/         Tool, ToolSpec, Risk, ToolRegistry, ToolPipeline, ToolOutcome
├── auth/          CredentialsProvider, Credentials, ApiKeyValidationGateway (порты)
├── interaction/   UserInteraction (порт), ApprovalRequest
├── storage/       ConversationRepository, SettingsRepository (порты), AppSettings
└── platform/      Clock, DispatcherProvider, IdGenerator, AppLogger, LogRedaction
```
Разрешённые библиотеки: kotlin-stdlib, `kotlinx-coroutines-core`, `kotlinx-serialization-json`. Запрещены: `android.*`, `androidx.*`, OkHttp, Room, Dagger/Hilt, Compose.

## Ключевые порты (эскиз)
```kotlin
public interface ModelGateway {
    public fun stream(request: ModelRequest): Flow<ModelEvent>        // ошибки приходят как ModelEvent.Failed
}
public sealed interface ModelEvent {
    data class TextDelta(val text: String) : ModelEvent
    data class ToolCallRequested(val call: ToolCall) : ModelEvent
    data class OpaqueItem(val provider: String, val payload: JsonElement) : ModelEvent  // см. «История и непрозрачные элементы» ниже
    data class Completed(val usage: Usage?) : ModelEvent
    data class Failed(val error: AgentError) : ModelEvent
}

public interface Tool {
    public val spec: ToolSpec                     // имя, описание для модели, JSON-схема, Risk
    public fun describe(args: JsonObject): CallDescription   // человеческое описание вызова: заголовок, суть, детали
    public fun argumentEditor(args: JsonObject): ToolArgumentEditor? // необязательные типизированные поля для одного CONFIRM
    public suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}
// CallDescription(title, summary, details) нужен для карточки в ленте и текста подтверждения (ApprovalRequest = CallDescription + Risk).
// Его формирует сам инструмент (строки из ресурсов :tools), поэтому UI не знает про конкретные инструменты
// и подтверждение рисуется из структурированных данных, а не из текста модели.

public interface UserInteraction {
    public suspend fun confirm(request: ApprovalRequest): ConfirmResult // Approved(аргументы) | Rejected
    public suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean   // один вопрос на пачку вызовов одного ответа (ADR 0004)
    public suspend fun ensure(capability: Capability): CapabilityResult   // Capability: Notifications и т.п.
}

public interface CredentialsProvider {        // то, что нужно клиенту модели от слоя входа
    public suspend fun current(): CredentialsResult // Ready(токен + accountId либо API-ключ), SignedOut или Unavailable (временный сбой обновления, сессия не потеряна)
    public suspend fun refreshAfterUnauthorized(): CredentialsResult
}
```
`Capability` — понятие ядра («нужны уведомления»). В Android-разрешения его переводит `:app`, ядро про них не знает.

## Цикл агента (`AgentLoop`) — императивный Flow

`AgentLoop.run(history, input): Flow<AgentEvent>` — **холодный Flow**, не привязан к ViewModel или Android и пригоден для сервиса/фоновой задачи. Реализован императивным `flow { while (true) }`, а не автоматом `reduce(state, event)`:

- шаг: запрос модели → текст и вызовы инструментов; шагов не больше `maxSteps`, иначе `TurnFailed(StepLimit)`;
- вызовов инструментов за ход не больше `maxToolCallsPerTurn` (30, не меньше 1): шаг, который не помещается в остаток, отклоняется целиком (ни один вызов в нём не запускается), ход завершается `TurnFailed(StepLimit)`, сделанное остаётся в истории;
- `Tool.describe` упал: для `SAFE` карточка нейтральная (название = имя инструмента) и вызов идёт дальше; для одиночного `CONFIRM`/`SENSITIVE` вызов не запускается, шторка не показывается (`Failed`), описание запрашивается один раз и показывается ровно то же. Если описание любого участника пакета не удалось построить до показа подтверждения, вся группа получает `NotShown`, ничего не запускается, а модель получает `Not shown: …`.
- ошибка запроса → `TurnFailed(error)`; ответ без вызовов → `TurnCompleted(newItems)`;
- `AgentLoop.continueTurn(history)`: без нового `UserMessage`; `ContextWindowPolicy`, лимиты заново, в `TurnCompleted`/`TurnFailed` только новые элементы.
- `classifyRetry(history)`: после последнего `UserMessage` `Continue`, если есть вызовы и каждый имеет `ToolOutput` с тем же `callId`; иначе `Restart`.
- отмена собирающего — обычная отмена корутины: сеть и инструмент прерываются, `CancellationException` не глотается.

- Ответ модели может содержать **несколько вызовов**: выполняем по порядку, все результаты добавляем в историю, только потом следующий запрос. Параллелить можно только `SAFE`-инструменты, и не в MVP.
- Перезапуск: история хранит пары `ToolCallItem`/`ToolOutput` по `callId`. `toOutputText()` пишет успех как есть, остальные исходы — с метками `Rejected:`, `Not shown:`, `Failed:`. Старый `Rejected: not shown to user:` читается как `Rejected`. Чат восстанавливает исход через `toolOutcomeFromOutputText`, описание — через `AgentLoop.describe`; без результата показывает «Прервано». `ToolContent` идёт в событие, не в историю и не модели; инструмент восстанавливает его из текста (будильник даёт `HandedOff("Часы")` только при точном `AlarmCopy.RESULT_SUCCESS`). UI не разбирает JSON инструментов; форматы Room и `ToolOutput` прежние.
- `AgentEvent` (для UI): `TextDelta`, `ToolCallStarted`, `ApprovalRequested`, `ToolCallFinished`, `TurnCompleted(newItems)`, `TurnFailed(error)`.
- История между запросами передаётся целиком (`store=false`); сколько отправлять, решает `ContextWindowPolicy` (стратегия, заменяемая: «последние N», «по размеру», позже «с суммаризацией»).

## Инструменты: плагины + конвейер

**Регистрация.** `ToolRegistry` собирается из `Set<Tool>`, который даёт DI (Hilt multibinding). Цикл агента про конкретные инструменты не знает.

**Конвейер.** `ToolPipeline`: реестр, подтверждение по `Risk`, запрос разрешений, бюджет выполнения, вызов инструмента, обрезка вывода до 4 КБ и безопасный лог. Порядок зашит в классе; инструмент его не реализует.

- Обязательные аргументы (`required` в схеме) проверяет `ToolPipeline`; остальную валидацию (диапазоны, лимиты длин) делают сами инструменты через sealed-результаты валидации.
- Разрешения: `ToolPipeline` запрашивает `ToolSpec.requiredCapabilities` **до** таймаута; ожидание пользователя не считается. Таймаут из `ToolSpec.executionTimeoutMillis` (по умолчанию 5 с, `find_files`/`search_files` — 30 с); таймаут даёт `Failed("tool execution timed out")`, лог содержит тип и миллисекунды, UI показывает русскую строку. Отказ — `Failed(Tool.deniedMessage)`. Исключение из `Tool.execute` (кроме отмены) превращается в `Failed("tool failed: <тип>")`.
- `ApprovalPolicy` по `Risk` инструмента: `SAFE` (без вопроса: `find_files`, `notify`), `CONFIRM` (разовое подтверждение: `set_alarm`; несколько `CONFIRM`-вызовов одного ответа модели подтверждаются одним вопросом «всё или ничего», до 15 за раз: `ToolPipeline.batchGroups` и `confirmBatch`, [ADR 0004](../adr/0004-batch-approval.md)), `SENSITIVE` (подтверждение с деталями, «запомнить» запрещено; на будущее: SMS, звонки).
- Одиночный `CONFIRM`: `ToolArgumentEditor` даёт поля и `review(args)` (описание, ошибка); UI не знает имя инструмента. `confirm` возвращает `Approved(arguments)` или `Rejected`. Конвейер проверяет правки; `AgentLoop` сохраняет и отправляет модели реальные аргументы. Отказ сохраняет исходные. `SAFE`, `SENSITIVE` и пачка не редактируются.
- Тот же вызов (имя, равный `JsonObject`) после `ToolOutput` в ходе требует подтверждения и для `SAFE`; прошлые ходы исключены. Ошибка описания блокирует запуск.
- `ToolOutcome = Success(text, content?) | Rejected(reason) | NotShown(reason) | Failed(reason)` — итог конвейера. `Rejected("user declined")` — отказ пользователя; `NotShown` — подтверждение не показывали и инструмент не запускали. `ToolResult` — ответ самого инструмента. `ToolContent` — типизированные метаданные для клиентов, текст для модели он не меняет. Любой исход становится `function_call_output`.

Так безопасность, лимиты и логирование живут в одном месте и применяются ко **всем** инструментам, включая будущие.

**Типизированные аргументы.** Каждый инструмент сам описывает JSON-схему (`buildJsonObject`) и декодирует аргументы в свой `@Serializable`-класс (`AlarmArgs`, `NotifyArgs`, …); декодирование падает в `ToolOutcome.Failed`, не в исключение. Общего `TypedTool<A>` и DSL схем нет — при 3–5 инструментах это было бы лишним слоем; заводим, когда инструментов станет заметно больше.

## История и «непрозрачные» элементы
История — не только текст. Модель со `store=false` может возвращать служебные элементы (например, зашифрованные reasoning-блоки), которые нужно вернуть в следующем запросе. Поэтому `HistoryItem` включает `Opaque(provider, payload)`: ядро их не понимает, но хранит и отдаёт обратно адаптеру. Нужно ли это для нашего бэкенда — проверяется на этапе 0 (пункт 0.3). Закладываем слот сейчас, чтобы не менять формат хранения потом.

## Ошибки
```
AgentError
├─ SessionExpired · Forbidden · Offline · Timeout
├─ RateLimited(retryAfter?) · ProtocolChanged · Server(code)
├─ ToolFailure(reason) · StepLimit · Interrupted
└─ Cancelled · Unexpected(details)
```
Плоский список без групп-обёрток (не иерархия `Auth`/`Network`/`Tool`): sealed-типов меньше, а исчерпывающий `when` в UI всё равно заставляет обработать каждый. Отдельных `Validation`/`PermissionDenied` нет: отказ валидации приходит как `ToolFailure`, а «запрещено» — это `Forbidden`; отказ пользователя — `ToolOutcome.Rejected`, непоказанное подтверждение — `ToolOutcome.NotShown`.
В ядре ошибки без текста для людей. Тексты на русском, `strings.xml`, подставляет UI-слой. `CancellationException` всегда пробрасывается дальше и никогда не глотается.
