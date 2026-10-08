# Порядок переноса FSD-срезами

План к [карте слоёв и границ](fsd-map.md), основан на снимке 2026-10-09. Оценки текущего объёма ±30%; тесты переносятся с кодом owning slice, количество файлов `src/test`/`androidTest` уточняется по фактическому инвентарю каждого среза. Ранние шаги выбирают чистые листья графа; у каждого следующего среза production dependencies находятся в нём самом или в предыдущих шагах.

| № | Срез и prerequisites | Размер и тесты вместе | Риск |
|---:|---|---|---|
| 1 | `entities.platform_services`; нет feature prerequisites. | ~350 LOC; перенос portable policy tests. | Низкий; Android logger остаётся в `app`. |
| 2 | `entities.conversation` + `entities.settings`; зависит от 1 для shared primitives. | ~650 LOC, 3 domain tests. | Низкий-средний; стабильность persisted history types. |
| 3 | `entities.tool_call`, `interaction`, `auth`, `model_access`, `speech`, `agent_run`; зависит от 1–2. | ~1,100 LOC; 16 domain tests: AgentLoop, pipeline, fakes and contracts. | Высокий; разрезать entity import cycle и сохранить поведение. |
| 4 | `shared.design_system`; нет entity dependency, generic primitives only. | ~2,200 LOC, previews + theme/component UI tests. | Средний; шрифты/иконки вместе с ресурсами модуля. |
| 5 | `features.model_backend`; зависит от entities 1–3. | ~740 LOC, all 9 llm tests: SSE, request, gateway, catalog. | Высокий; streaming/cancel/provider DTO. |
| 6 | `features.auth`; зависит от entities auth/platform и model_backend validation port; `app` wiring. | ~1,330 LOC, ~10 unit + 2 Android tests: PKCE, browser/device auth, session/token store. | Высокий; secrets, encrypted store, callback. |
| 7 | `features.tool_status`; depends on entities tool_call/platform. | ~100 LOC, tool unit/adapter tests. | Низкий; system APIs остаются androidMain. |
| 8 | `features.tool_notify`; depends on entities tool_call/interaction. | ~150 LOC, validation/permission tests. | Низкий; notification permission/channel. |
| 9 | `features.tool_alarm`; depends on entities tool_call and approval contract. | ~200 LOC, schema/validation/confirmation tests. | Средний; Android intent and risk policy. |
| 10 | `features.tool_files`; depends on entities tool_call/settings/platform. | ~1,100 LOC, criteria/codec/SAF traversal/MediaStore limits tests. | Высокий; URI grants, bounded traversal, platform differences. |
| 11 | `features.conversation_history` + `features.settings`; depends on entities conversation/settings and app root. | ~300 LOC, Room mapper/repository and settings tests. | Средний; Room schema and DataStore behavior. |
| 12 | `features.voice_dictation`; depends on entities speech/interaction/platform. | ~500 LOC, speech mapper/decorator/lifecycle tests. | Средний-высокий; recognizer/system dialog strictly androidMain. |
| 13 | `features.send_message`; depends on entities 1–3, backend, history and interaction port. | ~700 LOC, app chat tests plus AgentLoop tests from step 3. | Высокий; streaming, save/restore, cancel/retry. |
| 14 | `features.approve_call`, `copy_share_answer`, widgets composer/message_list/tool_call_card/approval_sheet/voice_bar, screens chat/login/settings/about; depends on 1–13. | Remaining app UI (~4,000 LOC), relevant Compose/UI tests and previews. | Высокий; navigation/composition, no sibling-screen imports. |

Перед каждым срезом актуализировать inventory и оценку; конкретные существующие unit/instrumented/UI suites, которые едут с кодом, перечислены ниже. Порядок выбирает листья, затем consumers/adapters и только после этого screens.
Тесты, забираемые каждым срезом (пути относительно `app/src/test/kotlin/com/example/phoneagent/`, если не указан модуль):
1. `core/domain/.../platform/{LogRedactionTest,RedactingAppLoggerTest,RingLogTest}`; `app/logging/{AndroidLogcatLoggerTest,CrashLoggingHandlerTest}` остаются с app adapter, а не в shared.
2. `core/domain/.../conversation/{LastNMessagesPolicyTest,RetryPlanTest,TurnStateTransitionTest}`.
3. `DomainContractsTest`, `agent/{AgentLoopBatchApprovalTest,AgentLoopContextWindowTest,AgentLoopContinueTest,AgentLoopEditableApprovalTest,AgentLoopTest,CurrentTimeNoteTest}`, `tools/{ToolOutcomeTextTest,ToolPipelineEditableApprovalTest,ToolPipelineHardeningTest,ToolPipelineTest,ToolRegistryTest}`, `fakes/*ContractTest`.
4. `AssistantMarkdownUiTest`, `CloseUnclosedFenceTest`, `PreparingIndicatorUiTest`, `ContentColumnUiTest`, `BrandThemeTokensTest`, `ThemeGeometryTest`, `ThemeResolverTest`, `TopAppBarThemeUiTest`; shared primitive previews move with their component.
5. All 9 `data/llm/src/test` files: `ErrorBodySummaryTest`, `OkHttpApiKeyValidationGatewayTest`, `OkHttpModelCatalogTest`, `ResponsesGatewayContractTest`, `ResponsesGatewayTest`, `SystemPromptTest`, `request/ResponsesRequestBuilderTest`, `sse/{ResponsesEventMapperTest,SseParserTest}`.
6. All 12 `data/auth` tests: 10 `src/test` files (`ApiKeyAuthTest`, `AuthPrimitivesTest`, `ChatGptBrowserAuthTest`, `ChatGptDeviceCodeAuthTest`, `LoopbackCallbackReceiverDispatcherTest`, `OkHttpDeviceCodeGatewayTest`, `OkHttpTokenRefreshGatewayTest`, `RecordingDispatcher`, `SessionManagerContractTest`, `SessionManagerTest`) and `src/androidTest/{EncryptedTokenStoreTest,LoopbackCallbackReceiverTest}`.
7. `tools/status/DeviceStatusToolTest`; 8. `tools/notify/{NotificationIdsTest,NotifyToolTest,NotifyValidationTest}`; 9. `tools/alarm/{AlarmArgumentEditorTest,AlarmIntentFactoryTest,AlarmToolTest,AlarmValidationTest}`.
10. `tools/files/{FakeDocumentNode,FindFilesToolTest,SafTreeWalkerTest,SearchFilesMetadataTest,SearchFilesPolicyTest,SearchFilesToolTest}`.
11. `app/storage/{HistoryItemEntityMapperTest,PreferencesSettingsRepositoryTest,RoomConversationRepositoryTest,RoomSchemaGuardTest}`; screen settings tests remain step 14.
12. `app/voice/*Test`, `app/ui/voice/VoiceHostUiTest`, and `app/ui/chat/voice/{AppendDictationTest,VoiceDictationEndToEndUiTest,VoiceInputViewModelTest}`.
13. `app/ui/chat/{ChatViewModel*,ErrorForDisplayTest,ErrorPresentationTest,FeedFollowTest,HistoryRestoreTest,ToolContentMapperTest}` (all ChatViewModel state/order/retry/restore/stop/approval suites).
14. Remaining app presentation/root tests not listed earlier: `SplashVisibilityTest`, `architecture/*Test`, `interaction/*Test`, `logging/*Test`, `testing/*Test`, `ui/AppVersionTest`, `screenshot/*`, `ui/about/*Test`, `ui/login/*Test`, `ui/settings/*Test`, `ui/chat/*UiTest`, `ui/designsystem/components/*Test`, `ui/interaction/*Test`, `ui/navigation/*Test`, `ui/root/*Test`, and `ui/designsystem/theme/*Test`; architecture rules stay in app composition root.

## Android-only границы

- Compose остаётся Android-only до отдельного решения о Compose Multiplatform/iOS.
- `voice_dictation`: SpeechRecognizer/system speech dialog, intents, Android lifecycle/host и permissions.
- `tool_alarm`: системный `SET_ALARM` intent/launcher; `tool_notify`: NotificationManager/channel; `tool_status`: батарея, PowerManager, StatFs.
- `tool_files`: SAF DocumentsContract/tree URI, MediaStore и URI grants.
- `auth` adapters: Keystore encrypted token store, Custom Tabs/browser callback/loopback receiver, Android manifest.
- Room/DataStore реализации `conversation_history`/`settings` остаются в `androidMain`, пока не одобрены KMP-замены и миграции.
- `app`: Hilt, Activity/Application, navigation host, Android resources/version/license access, picker intents и platform bridges.

## Проверки полноты и направления

Полнота сопоставляется с `git ls-files 'app/src/main/**/*.kt' 'tools/src/main/**/*.kt' 'data/*/src/main/**/*.kt' 'core/domain/src/main/**/*.kt'`: каждая исходная строка должна иметь ровно одну destination-запись в [инвентаре](fsd-map-files.md), то есть `missing=duplicate=extra=0` (208 файлов в текущем снимке). Mutation: удалить одну запись в копии inventory и повторить тот же compare — `missing` становится 1; исходник не меняется. Для проверки направления извлечь `com.example.phoneagent.*` imports, сверить package каждого пути с таблицей destinations и отвергнуть import вверх или в sibling slice. Срез самодостаточен при отсутствии production dependencies за пределами себя и уже пройденных шагов.
