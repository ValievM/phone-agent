# Ядро (`:core:domain`)

> См. также: [обзор](overview.md) · [адаптеры](adapters.md) · [тесты](testing.md)

## Структура пакетов
```
com.example.phoneagent.domain
├── agent/         AgentLoop, AgentEvent, AgentConfig, AgentError
├── conversation/  Message (HistoryItem), ToolCall, ContextWindowPolicy
├── model/         ModelGateway (порт), ModelRequest, ModelEvent
├── tools/         Tool, TypedTool, ToolSpec, JsonSchema (DSL), Risk,
│                  ToolRegistry, ToolPipeline + middleware, ToolOutcome
├── auth/          AuthProvider, CredentialsProvider (порты), Session, AuthState, AuthMethod
├── interaction/   UserInteraction (порт), ApprovalRequest
├── storage/       ConversationRepository, SettingsRepository (порты), AppSettings
└── platform/      Clock, DispatcherProvider, IdGenerator, AppLogger (порты)
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
    public suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}
// CallDescription(title, summary, details) нужен для карточки в ленте и текста подтверждения (ApprovalRequest = CallDescription + Risk).
// Его формирует сам инструмент (строки из ресурсов :tools), поэтому UI не знает про конкретные инструменты
// и подтверждение рисуется из структурированных данных, а не из текста модели.

public interface UserInteraction {
    public suspend fun confirm(request: ApprovalRequest): Boolean
    public suspend fun confirmBatch(requests: List<ApprovalRequest>): Boolean   // один вопрос на пачку вызовов одного ответа (ADR 0004)
    public suspend fun ensure(capability: Capability): CapabilityResult   // Capability: Notifications и т.п.
}

public interface CredentialsProvider {        // то, что нужно клиенту модели от слоя входа
    public suspend fun current(): CredentialsResult // Ready(токен + accountId либо API-ключ) или SignedOut
    public suspend fun refreshAfterUnauthorized(): Credentials?
}
```
`Capability` — понятие ядра («нужны уведомления»). В Android-разрешения его переводит `:app`, ядро про них не знает.

## Цикл агента (`AgentLoop`) — конечный автомат

`AgentLoop.run(history, input): Flow<AgentEvent>` — **холодный Flow**, не привязан ни к ViewModel, ни к Android. Его можно позже запустить из сервиса или фоновой задачи без переписывания.

```
Idle ──user message──▶ AwaitingModel ──TextDelta──▶ AwaitingModel (накапливаем текст)
                            │
                            ├─ ToolCallRequested ─▶ RunningTools ──(все результаты)──▶ AwaitingModel  (step + 1)
                            ├─ Completed без вызовов ─▶ Done
                            ├─ Failed / ошибка инструмента-фатал ─▶ Failed(error)
                            ├─ step > maxSteps ─▶ Failed(StepLimit)
                            └─ отмена ─▶ Cancelled (Flow отменён, сеть и инструмент прерваны)
```
- Ответ модели может содержать **несколько вызовов**: выполняем по порядку, все результаты добавляем в историю, только потом следующий запрос. Параллелить можно только `SAFE`-инструменты, и не в MVP.
- Переход состояния описан **чистой функцией** `reduce(state, event) → state`. Тесты автомата не требуют ни сети, ни корутин.
- `AgentEvent` (для UI): `TextDelta`, `ToolCallStarted`, `ApprovalRequested`, `ToolCallFinished`, `TurnCompleted(newItems)`, `TurnFailed(error)`.
- История между запросами передаётся целиком (`store=false`); сколько отправлять, решает `ContextWindowPolicy` (стратегия, заменяемая: «последние N», «по размеру», позже «с суммаризацией»).

## Инструменты: плагины + конвейер

**Регистрация.** `ToolRegistry` собирается из `Set<Tool>`, который даёт DI (Hilt multibinding). Цикл агента про конкретные инструменты не знает.

**Конвейер (middleware, цепочка ответственности).** Каждый вызов проходит одинаковые ступени, инструмент их не реализует:
```
ValidateArgs → ApprovalPolicy → Timeout → RunTool → TruncateOutput(4 КБ) → Log(без секретов)
```
- `ValidateArgs`: имя есть в реестре, аргументы соответствуют схеме, лимиты длин.
- `ApprovalPolicy` по `Risk` инструмента: `SAFE` (без вопроса: `find_files`, `notify`), `CONFIRM` (разовое подтверждение: `set_alarm`; несколько `CONFIRM`-вызовов одного ответа модели подтверждаются одним вопросом «всё или ничего», до 15 за раз: `ToolPipeline.batchGroups` и `confirmBatch`, [ADR 0004](../adr/0004-batch-approval.md)), `SENSITIVE` (подтверждение с деталями, «запомнить» запрещено; на будущее: SMS, звонки).
- Результат: `ToolOutcome = Success(text) | Rejected(reason) | Failed(error)`. Это итог всего конвейера, включая отказ пользователя и таймаут; `ToolResult` — только то, что вернул сам инструмент. Любой исход превращается в `function_call_output` для модели.

Так безопасность, лимиты и логирование живут в одном месте и применяются ко **всем** инструментам, включая будущие.

**Типизированные аргументы.** `TypedTool<A>` — базовая помощь: декодирует JSON в `@Serializable`-класс `A`, инструмент получает готовый объект. Схема описывается небольшим DSL и проверяется тестом на соответствие классу.

## История и «непрозрачные» элементы
История — не только текст. Модель со `store=false` может возвращать служебные элементы (например, зашифрованные reasoning-блоки), которые нужно вернуть в следующем запросе. Поэтому `HistoryItem` включает `Opaque(provider, payload)`: ядро их не понимает, но хранит и отдаёт обратно адаптеру. Нужно ли это для нашего бэкенда — проверяется на этапе 0 (пункт 0.3). Закладываем слот сейчас, чтобы не менять формат хранения потом.

## Ошибки
```
AgentError
├─ Auth:      SessionExpired · Forbidden
├─ Network:   Offline · Timeout
├─ Limits:    RateLimited(retryAfter?)
├─ Backend:   ProtocolChanged · Server(code)
├─ Tool:      Validation · PermissionDenied · Timeout · Failed(reason)
└─ Agent:     StepLimit · Cancelled · Unexpected
```
В ядре ошибки без текста для людей. Тексты на русском, `strings.xml`, подставляет UI-слой. `CancellationException` всегда пробрасывается дальше и никогда не глотается.
