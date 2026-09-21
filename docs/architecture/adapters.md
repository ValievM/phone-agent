# Адаптеры

> См. также: [ядро](core.md) · [UI](ui.md) · [требования](../product/requirements.md)

## `:data:llm` — клиент модели (anti-corruption layer)
```
ResponsesGateway (public, реализует ModelGateway)
├─ BackendProfile        URL, заголовки, имя модели по умолчанию; два профиля: ChatGptSubscription и PublicApi
├─ request/              сборка запроса из ModelRequest (internal)
├─ dto/                  DTO формата Responses API (internal, никогда не выходят за модуль)
├─ sse/                  разбор потока событий (internal)
└─ mapper/               DTO → ModelEvent (internal)
```
- Профиль выбирается по способу входа: один и тот же клиент работает и с подпиской, и с API-ключом (стратегия), различаются URL и заголовки.
- OkHttp напрямую, без Retrofit: один эндпоинт и SSE, Retrofit почти ничего не даёт.
- `AuthInterceptor` добавляет токен и `chatgpt-account-id`; `Authenticator` при 401 вызывает `CredentialsProvider.refreshAfterUnauthorized()`, один раз. Обновление токена **single-flight** (мьютекс): параллельные запросы ждут один refresh.
- Если бэкенд поменял формат: правим `dto/` и `mapper/` + обновляем записанный образец ответа в тестах. Остальной код не трогаем.

## `:data:auth`
```
AuthProvider (стратегия):  ChatGptBrowserAuth · ChatGptDeviceCodeAuth · ApiKeyAuth
SessionManager             хранит AuthState (StateFlow), реализует CredentialsProvider
TokenStore                 DataStore + AES-GCM, ключ в Android Keystore
TokenRefresher             мьютекс, атомарная запись нового refresh-токена
pkce/ · jwt/               чистые функции (тестируются без Android)
AuthConfig                 issuer, client_id, scope, redirect, параметры (значения из docs/backend-notes.md)
```
**Порт 1455 может занять чужое приложение.** Украденный `code` бесполезен без `code_verifier` (PKCE), `state` проверяется обязательно, при занятом порту включается device code.

Провайдеры регистрируются как `Map<AuthMethod, AuthProvider>` (Hilt `@IntoMap`). Добавить способ входа = новый класс + строка регистрации + кнопка в UI.

## `:tools`
```
tools/
├─ alarm/    AlarmTool, AlarmArgs, AlarmIntentFactory (чистая функция), AlarmBindings
├─ notify/   NotifyTool, NotifyArgs, Notifier, NotifyBindings
├─ files/    FindFilesTool, FindFilesArgs, SafTreeWalker, FilesBindings
└─ common/   маленькие общие помощники (без логики инструментов)
```
- Каждый инструмент в своём пакете, всё `internal`, наружу торчит только регистрация. Пакеты инструментов **не импортируют друг друга** (проверяет тест).
- **Humble object.** Вызовы Android API вынесены в тонкие классы (`Notifier`, запуск intent, `SafTreeWalker`), логика (сборка intent, валидация, лимиты обхода) — в чистые функции, которые тестируются без устройства.
- Папку для поиска пользователь выбирает **заранее в настройках** (хранится в `SettingsRepository`). Если не выбрана, `find_files` возвращает `Failed(NeedsSetup)`, и UI предлагает открыть настройки. Так инструменту не нужен Activity-результат в момент работы.

## `UserInteractionBridge` (`:app`) — самое хитрое место
Инструментам и циклу нужно спросить пользователя (подтверждение, разрешение), а они работают вне UI. Решение — мост:
1. Ядро вызывает `suspend fun confirm(request)`.
2. Мост кладёт запрос в `StateFlow<PendingInteraction?>` и ждёт `CompletableDeferred`.
3. Корневой composable (`InteractionHost`) показывает шторку подтверждения или запускает системный запрос разрешения (`rememberLauncherForActivityResult`), завершает `deferred`.
4. Если экран ушёл или пользователь отменил: `false` / `Denied`, ожидание отменяется вместе с корутиной.

Ядро и инструменты ничего не знают про Activity и Compose.

## Хранилище (в `:app`, пока)
- Room: `ConversationEntity`, `MessageEntity`, JSON для `Opaque`-элементов. **Entity отдельно от доменных моделей**, между ними мапперы. Миграции с тестами.
- DataStore: `AppSettings` (модель, папка поиска, включённые инструменты).
- Порты `ConversationRepository`, `SettingsRepository` определены в ядре, API уже рассчитан на несколько диалогов (есть `conversationId`), хотя UI показывает один.
