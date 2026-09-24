# Документация проекта

Android-приложение: чат с Codex + узкие инструменты для управления телефоном (будильник, поиск файлов, уведомление). Нативный Kotlin, разработка по TDD.

**Вся документация лежит в `docs/`.** Единственное исключение — [`AGENTS.md`](../AGENTS.md) в корне репозитория: Codex подхватывает его, только если он лежит в корне (или на пути от корня к рабочей папке). Это файл настроек для агента, а не документация: в нём короткие правила и ссылки сюда.

## Статус
Архитектура, дизайн, требования и этапы **утверждены 21.09.2026**: [ADR 0001](adr/0001-baseline.md) (что решено, что проверено, что остаётся непроверенным и на каком этапе закрывается). Допущения с пометкой `[проверить]` до своего этапа считаются гипотезами.

## Как читать (и почему файлов много)
Документация разбита на небольшие тематические файлы (каждый до ~14 КБ), чтобы человек и агент читали только нужное: на одну задачу обычно хватает 2-4 файлов. Всё, что прочитано, остаётся в контексте агента и стоит токенов, поэтому «прочитать всё подряд» не нужно.

**Стартовая точка:** файл текущего этапа из [stages/](stages/README.md). В нём есть блок «Читать перед началом» со ссылками на нужные документы.

## Роутер: задача → что читать

| Задача | Читать |
|---|---|
| Работа над этапом | [stages/README.md](stages/README.md) → файл этапа |
| Что входит в MVP, критерии приёмки, риски | [product/overview.md](product/overview.md) |
| Требования: вход, клиент модели, цикл агента, инструменты, экраны | [product/requirements.md](product/requirements.md) |
| Константы входа и бэкенда (источник истины) | [backend-notes.md](backend-notes.md) |
| Модули, куда класть код | [architecture/overview.md](architecture/overview.md) |
| Порты, `AgentLoop`, инструменты, ошибки | [architecture/core.md](architecture/core.md) |
| Клиент модели, вход, инструменты Android, мост диалогов, хранилище | [architecture/adapters.md](architecture/adapters.md) |
| ViewModel и состояние экрана | [architecture/ui.md](architecture/ui.md) |
| Паттерны, корутины, DI | [architecture/patterns.md](architecture/patterns.md) |
| Тесты, TDD, автоматические правила границ | [architecture/testing.md](architecture/testing.md) |
| Добавить инструмент, способ входа, экран | [architecture/recipes.md](architecture/recipes.md) |
| Почему так решили, риски архитектуры | [architecture/decisions.md](architecture/decisions.md) |
| Обзор дизайна | [design/overview.md](design/overview.md) |
| Цвет, шрифты, формы | [design/style.md](design/style.md) |
| Иконки, анимации, тексты интерфейса | [design/icons-motion-copy.md](design/icons-motion-copy.md) |
| Токены, компоненты, экраны | [design/components.md](design/components.md) |
| Вёрстка Compose: списки, insets, состояния | [design/layout-rules.md](design/layout-rules.md) |
| Доступность и безопасность UI (markdown, ссылки) | [design/a11y-security.md](design/a11y-security.md) |
| Библиотеки и инструменты UI | [design/libraries.md](design/libraries.md) |
| Скриншот-тесты, Design QA | [design/qa.md](design/qa.md) |
| Что из сделанного пользователь ещё не проверил на своём телефоне | [manual-verification.md](manual-verification.md) |
| Что пишется в логи и как это проверять | [log-audit.md](log-audit.md) |
| Serena и Context7 | [tooling/serena-context7.md](tooling/serena-context7.md) |
| Оптимизация сборки (замечания на будущее) | [tooling/build-performance.md](tooling/build-performance.md) |
| CI и JDK для Gradle | [tooling/ci.md](tooling/ci.md) |

## Карта документов
- `product/`: что строим. [overview](product/overview.md), [requirements](product/requirements.md).
- `research/`: исходные гипотезы. [backend-facts](research/backend-facts.md). Проверенные значения: [backend-notes](backend-notes.md).
- `architecture/`: как устроено. [overview](architecture/overview.md), [core](architecture/core.md), [adapters](architecture/adapters.md), [ui](architecture/ui.md), [patterns](architecture/patterns.md), [testing](architecture/testing.md), [recipes](architecture/recipes.md), [decisions](architecture/decisions.md).
- `design/`: как выглядит. [overview](design/overview.md), [style](design/style.md), [icons-motion-copy](design/icons-motion-copy.md), [components](design/components.md), [layout-rules](design/layout-rules.md), [a11y-security](design/a11y-security.md), [libraries](design/libraries.md), [qa](design/qa.md).
- `stages/`: в каком порядке делаем. [README](stages/README.md), [A](stages/a-environment.md), [0](stages/0-spike.md), [1](stages/1-skeleton.md), [2](stages/2-auth.md), [3](stages/3-model-client.md), [4](stages/4-tools.md), [5](stages/5-hardening.md), [6](stages/6-release.md).
- `tooling/`: инструменты агента. [serena-context7](tooling/serena-context7.md), [build-performance](tooling/build-performance.md), [ci](tooling/ci.md).
- [manual-verification.md](manual-verification.md): что из сделанного реально потрогал пользователь на своём телефоне, а что нет. [log-audit.md](log-audit.md): аудит логов на токены и данные чата.
- `adr/`: принятые решения. [0001-baseline](adr/0001-baseline.md), [0002-subscription-login-go](adr/0002-subscription-login-go.md), [0003-global-file-search](adr/0003-global-file-search.md).

**Появятся по ходу работы:**

| Документ | Когда | Что внутри |
|---|---|---|
| `measurements.md` | этап 6 | замеры памяти и батареи |
| `INSTALL.md` | этап 6 | инструкция для пользователя: скачать, установить, войти, что умеет |

## Правила ведения
- Новый документ создаём в `docs/`, не в корне и не рядом с кодом. Язык русский, имена файлов латиницей.
- Новый файл сразу добавляем в этот индекс. Один файл — одна тема, до ~14 КБ; `AGENTS.md` до 8 КБ (его читают при каждом запуске).
- Одно знание в одном месте. Ссылаемся на файл и заголовок, а не на номера разделов.
- Решение изменилось: документ обновляем **в том же коммите**, что и код.
- При расхождении приоритет такой: `backend-notes.md` (значения бэкенда) → `architecture/` (устройство) → `product/` (требования). Расхождение называем вслух и исправляем.
- Проверка ссылок, размеров и структуры: `python3 scripts/check_docs.py` (запускаем после любой правки документов).
