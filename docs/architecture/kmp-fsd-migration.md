# План перехода на KMP и FSD

См. [ADR 0008](../adr/0008-kmp-fsd.md). Оценка по размеру кода и нашему темпу (≈ 6–8 задач в день); это ориентир, не обещание.

| Этап | Что | Задач | Риск |
|---|---|---|---|
| 0 | ADR, каркас слоёв-модулей, правила Konsist, проба на ядре | 2–3 | низкий |
| 1 | `:core:domain` → `:entities` + `:shared` в KMP: убрать `java.time`/`UUID`/`Locale`, тесты на `kotlin.test` | 5–7 | низкий (механика) |
| 2 | Сеть и вход на Ktor; интерфейс хранилища токена, реализация в `androidMain` | 8–12 | **высокий**: SSE, отмена |
| 3 | Room KMP, DataStore KMP, миграция схемы | 4–6 | средний |
| 4 | Фичи по слайсам (чат, голос, инструменты, вход, настройки) вместе с тестами | 15–25 | средний |
| 5 | Compose Multiplatform и оболочка iOS (только по решению владельца) | 25–45 | высокий |

Итого общий слой и фичи (этапы 0–4): ≈ 35–55 задач. Этап 5 отдельно.

## Целевая структура
```
:app        entry, DI (Hilt), navigation, theme            (androidMain)
:screens    Chat, Settings, About, Login                   (pages)
:widgets    Composer, MessageList, ApprovalSheet, VoiceBar
:features   send_message, voice, approve_call, share, ...  (model + ui + api)
:entities   message, tool_call, conversation, ...          (domain models + ports)
:shared     ui-kit (design system), api (Ktor), lib, config
```
Правила: модуль зависит только от модулей ниже (Gradle); слайсы одного слоя не импортируют друг друга (Konsist); `commonMain` без Android-типов; адаптеры платформы в `androidMain`.

## Правила переноса среза
Срезы вливаются в `dev` (стабильная версия отдельно: `release/0.1`). Один срез = одна фича/сущность целиком (код + тесты), приложение собирается и тесты зелёные на каждом шаге; старый код удаляется в том же срезе; новые Konsist-исключения только временные и перечислены в списке, который уменьшается.
