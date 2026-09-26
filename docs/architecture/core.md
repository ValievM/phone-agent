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
    public fun stream(request: ModelRequest): Flow<ModelEvent> // errors are ModelEvent.Failed
}
public sealed interface ModelEvent {
    data class TextDelta(val text: String) : ModelEvent
    data class ToolCallRequested(val call: ToolCall) : ModelEvent
    data class OpaqueItem(val provider: String, val payload: JsonElement) : ModelEvent
    data class Completed(val usage: Usage?) : ModelEvent
    data class Failed(val error: AgentError) : ModelEvent
}

public interface Tool {
    public val spec: ToolSpec // name, description, schema, risk
    public fun describe(args: JsonObject): CallDescription // user-facing card and approval details
    public suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}
// Tool-authored descriptions power cards and approvals; UI never uses model text for confirmation.

public interface UserInteraction {
    public suspend fun confirm(request: ApprovalRequest): Boolean
    public suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean // one yes/no per batch
    public suspend fun ensure(capability: Capability): CapabilityResult
}

public interface CredentialsProvider {
    public suspend fun current(): CredentialsResult // Ready, SignedOut, or transient refresh unavailable
    public suspend fun refreshAfterUnauthorized(): CredentialsResult
}
```
`Capability` is a domain need; `:app` maps it to Android permissions.

## Цикл агента (`AgentLoop`) — императивный Flow

`AgentLoop.run(history, input): Flow<AgentEvent>` — **холодный Flow**, не привязан ни к ViewModel, ни к Android. Его можно позже запустить из сервиса или фоновой задачи без переписывания. Внутри — императивный `flow { while (true) }` по шагам модели; автомата с `reduce(state, event)` нет, поведение то же, что задумывалось:

- шаг: запрос модели → текст и вызовы инструментов; шагов не больше `maxSteps`, иначе `TurnFailed(StepLimit)`;
- вызовов инструментов за ход не больше `maxToolCallsPerTurn` (30, не меньше 1): шаг, который не помещается в остаток, отклоняется целиком (ни один вызов в нём не запускается), ход завершается `TurnFailed(StepLimit)`, сделанное остаётся в истории;
- `Tool.describe` упал: для `SAFE` карточка нейтральная (название = имя инструмента) и вызов идёт дальше; для `CONFIRM`/`SENSITIVE` вызов не запускается и шторка не показывается (`Failed`), описание запрашивается один раз и показывается ровно то же; в пакете битый участник отклоняет всю группу (модель получит «пользователь отказал», хотя ему ничего не показывали: известная неточность);
- Для повторного вызова `SAFE`, которому требуется подтверждение, ошибка описания закрывает запуск: подтверждение не показывается.
- ошибка запроса → `TurnFailed(error)`; ответ без вызовов → `TurnCompleted(newItems)`;
- `AgentLoop.continueTurn(history)` продолжает последний ход без нового `UserMessage`; запрос проходит через `ContextWindowPolicy`, лимиты шагов и вызовов начинают считаться заново, а терминальное событие содержит только новые элементы.
- `classifyRetry(history)` смотрит хвост после последнего `UserMessage`: `Continue` — если есть вызовы и у каждого есть `ToolOutput` с тем же `callId`; иначе `Restart`.
- отмена собирающего — обычная отмена корутины: сеть и инструмент прерываются, `CancellationException` не глотается.

- Ответ модели может содержать **несколько вызовов**: выполняем по порядку, все результаты добавляем в историю, только потом следующий запрос. Параллелить можно только `SAFE`-инструменты, и не в MVP.
- Карточки восстанавливаются по паре `ToolCallItem`/`ToolOutput`. В историю сохраняется только `ToolOutcome.toOutputText()` (успех, `Rejected:`, `Failed:`); чат сопоставляет по `callId`, описание берёт через `AgentLoop.describe`. `ToolContent` передаётся событием, но не хранится и не отправляется модели; при загрузке его восстанавливает зарегистрированный инструмент. Форматы `ToolOutput` и Room не меняются. Вызов без результата — «Прервано». Текст успеха с префиксом `Rejected:`/`Failed:` читается как ошибка.
- Тесты цикла — на `AgentLoop` с фейковым `ModelGateway`, без сети; логика шагов и лимитов та же, что задумывалась для автомата.
- `AgentEvent` (для UI): `TextDelta`, `ToolCallStarted`, `ApprovalRequested`, `ToolCallFinished`, `TurnCompleted(newItems)`, `TurnFailed(error)`.
- История между запросами передаётся целиком (`store=false`); сколько отправлять, решает `ContextWindowPolicy` (стратегия, заменяемая: «последние N», «по размеру», позже «с суммаризацией»).

## Инструменты: плагины + конвейер

**Регистрация.** `ToolRegistry` собирается из `Set<Tool>`, который даёт DI (Hilt multibinding). Цикл агента про конкретные инструменты не знает.

**Конвейер.** Один класс `ToolPipeline`: имя ищется в реестре, дальше — подтверждение по `Risk`, таймаут, вызов инструмента, обрезка вывода до 4 КБ, лог без секретов. Инструмент этого не реализует. Готовых ступеней-«миддлварей» нет — порядок зашит в `ToolPipeline`, расширение новым шагом означает правку этого класса.

- Обязательные аргументы (`required` в схеме) проверяет `ToolPipeline`; остальную валидацию (диапазоны, лимиты длин) делают сами инструменты через sealed-результаты валидации.
- Разрешения: инструмент объявляет `ToolSpec.requiredCapabilities`, `ToolPipeline` запрашивает их **до** таймаута (время на системный диалог не считается); отказ — `Failed` с текстом `Tool.deniedMessage`. Исключение из `Tool.execute` (кроме отмены) превращается в `Failed("tool failed: <тип>")`, а не обрывает ход.
- `ApprovalPolicy` по `Risk` инструмента: `SAFE` (без вопроса: `find_files`, `notify`), `CONFIRM` (разовое подтверждение: `set_alarm`; несколько `CONFIRM`-вызовов одного ответа модели подтверждаются одним вопросом «всё или ничего», до 15 за раз: `ToolPipeline.batchGroups` и `confirmBatch`, [ADR 0004](../adr/0004-batch-approval.md)), `SENSITIVE` (подтверждение с деталями, «запомнить» запрещено; на будущее: SMS, звонки).
- Повтор вызова с теми же именем и равными аргументами `JsonObject` после сохранённого `ToolOutput` внутри текущего хода требует подтверждения и для `SAFE`; вызов из предыдущего хода не считается. Отказ даёт `Rejected("user declined")`, инструмент не запускается.
- Результат: `ToolOutcome = Success(text, content?) | Rejected(reason) | Failed(error)`. Это итог всего конвейера, включая отказ пользователя и таймаут. `Rejected` с причиной `user declined` означает отказ пользователя; у пачки, которую не показали из-за сломанного описания действия, причина сообщает, что подтверждения не было и вызовы не запускались. `ToolResult` — только то, что вернул сам инструмент. Дополнительный `ToolContent` — типизированные метаданные для клиентов, не замена и не изменение текста для модели. Любой исход превращается в `function_call_output` для модели.

Так безопасность, лимиты и логирование живут в одном месте и применяются ко **всем** инструментам, включая будущие.

**Типизированные аргументы.** Инструмент задаёт JSON-схему и декодирует её в свой `@Serializable`-тип; ошибка декодирования → `ToolOutcome.Failed`. Общего `TypedTool<A>`/DSL нет: для 3–5 инструментов это лишний слой.

## История и «непрозрачные» элементы
Модель со `store=false` может вернуть служебные элементы (например, reasoning), нужные в следующем запросе. `HistoryItem.Opaque(provider, payload)` сохраняет их непрозрачными и возвращает адаптеру; проверка бэкенда — этап 0.3.

## Ошибки
```
AgentError
├─ SessionExpired · Forbidden · Offline · Timeout
├─ RateLimited(retryAfter?) · ProtocolChanged · Server(code)
├─ ToolFailure(reason) · StepLimit · Interrupted
└─ Cancelled · Unexpected(details)
```
Ошибки — плоский список: лишние группы не нужны, а исчерпывающий `when` заставляет UI обработать все варианты. Валидация → `ToolFailure`; запрет → `Forbidden` или `ToolOutcome.Rejected`, поэтому отдельных `Validation`/`PermissionDenied` нет.
Пользовательские тексты живут в русских ресурсах `strings.xml` UI; ядро их не содержит. `CancellationException` всегда пробрасывается.
