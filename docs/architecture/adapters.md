# Адаптеры

> См. также: [ядро](core.md) · [UI](ui.md) · [требования](../product/requirements.md)

## `:data:llm` — клиент модели (anti-corruption layer)
```
ResponsesGateway (public, реализует ModelGateway)
├─ BackendProfile        URL, системный промпт и модель по умолчанию; один профиль ChatGptSubscription
├─ request/              сборка запроса из ModelRequest, включая выбор модели (internal)
├─ dto/                  DTO формата Responses API (internal, никогда не выходят за модуль)
└─ sse/                  разбор потока: парсер кадров и маппинг в ModelEvent (internal)
```
- Профиль один: клиент работает только с подпиской. Заголовки (токен, `chatgpt-account-id`, версия клиента) подставляет `ResponsesGateway`; модель по умолчанию задана в `BackendProfile`. Слота под API-профиль нет — если появится API-вход, профиль и заголовки вернутся в `BackendProfile`.
- OkHttp напрямую, без Retrofit: один эндпоинт и SSE, Retrofit почти ничего не даёт.
- Ретрай 401 — не OkHttp `Authenticator`, а обычный suspend-цикл в `ResponsesGateway` (не больше двух попыток): после сбоя вызывается `CredentialsProvider.refreshAfterUnauthorized()` и запрос собирается заново. Результат различает `Ready`, `SignedOut` (сессия потеряна: «Сессия завершилась») и `Unavailable` (обновить сейчас нельзя, например нет сети: ошибка сети с «Повторить», токен цел). Причина: `Authenticator` требует `runBlocking` на suspend-обновлении, что правилами запрещено; single-flight обновления токена живёт в `:data:auth`.
- Если бэкенд поменял формат: правим `dto/` и `sse/` + обновляем записанный образец ответа в тестах. Остальной код не трогаем.

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

Провайдеры собираются явным `mapOf` в `SessionAssemblyModule` (`app/di`), без Hilt `@IntoMap`: способов входа три и состав стабильный, явная карта виднее на ревью. Добавить способ входа = новый класс + строка в этой карте + кнопка в UI; при появлении динамики переходим на multibinding.

## `:tools`
```
tools/
├─ alarm/    AlarmTool, AlarmArgs, AlarmIntentFactory (чистая функция), AlarmBindings
├─ notify/   NotifyTool, NotifyArgs, Notifier, NotifyBindings
└─ files/    FindFilesTool и SearchFilesTool (ADR 0003), их Args, SAF-обход, SelectedFolder, FilesBindings
```
- Пакет `common/` не нужен: `FileSearchCriteria` общий только внутри `tools/files`; `find_files` ищет в выбранной папке SAF, а `search_files` — доступные записи MediaStore (ADR 0003). Реализации остаются `internal`, наружу доступна только регистрация; пакеты инструментов не импортируют друг друга (архитектурный тест).
- **Humble object.** Вызовы Android API остаются в тонких классах (`Notifier`, launcher intent, `SafTreeWalker`, `AndroidMediaStoreSearch`); сопоставление, проверка аргументов и лимиты обхода — обычная Kotlin-логика, тестируемая без устройства.
- Папку для поиска пользователь выбирает **заранее в настройках** (хранится в `SettingsRepository`). Если не выбрана, `find_files` возвращает `Failed(NeedsSetup)`, и UI предлагает открыть настройки. Так инструменту не нужен Activity-результат в момент работы.

## `UserInteractionBridge` (`:app`) — самое хитрое место
Инструментам и циклу нужно спросить пользователя (подтверждение, разрешение), а они работают вне UI. Решение — мост:
1. Ядро вызывает `suspend fun confirm(request)` и ждёт `ConfirmResult`.
2. Мост кладёт запрос в `StateFlow<PendingInteraction?>` и ждёт `CompletableDeferred<ConfirmResult>`.
3. Корневой composable (`InteractionHost`) показывает шторку подтверждения или запускает системный запрос разрешения (`rememberLauncherForActivityResult`), завершает `deferred`.
4. Для обычного `CONFIRM` шторка возвращает подтверждённые аргументы; для `SENSITIVE` редактора нет. Если экран ушёл или пользователь отменил: `Rejected` / `Denied`, ожидание отменяется вместе с корутиной.

Ядро и инструменты ничего не знают про Activity и Compose.

## Голосовой ввод
`VoiceInputViewModel` управляет состоянием UI через порт `SpeechInput`. `SystemDialogSpeechInput` запускает системный запрос через `SpeechRequestBridge`; `RecognizerSpeechInput` ведёт собственную сессию через `SpeechRecognizer` с частичными результатами и уровнем звука. Последний зависит от `RecognizerEngine` и чистых мапперов/нормализации; `AndroidRecognizerEngineFactory` — тонкая Android-обёртка, создающая и освобождающая распознаватель на главном потоке. Распознанный результат попадает в черновик, отправка остаётся действием пользователя.

`VoiceHost` не отменяет запрос при уходе из композиции: он переживает поворот экрана, как системный диалог. В отличие от него, `InteractionHost` отменяет ожидание взаимодействия при уходе экрана.


## Хранилище (в `:app`, пока)
- Room: одна таблица `conversation_history` (`ConversationEntity`), JSON для `Opaque`-элементов. **Entity отдельно от доменных моделей**, между ними мапперы. `exportSchema = false`, версия 1, миграций пока нет — они появятся вместе с первым изменением схемы.
- DataStore: `AppSettings` (модель, папка поиска, включённые инструменты).
- Порты `ConversationRepository`, `SettingsRepository` определены в ядре; `conversationId` в API не появился — история одна, несколько диалогов не заложены ни в API, ни в таблицу. Если понадобятся, это миграция + расширение порта.
