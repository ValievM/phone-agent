# Визуальный стиль: цвет, шрифты, формы

> См. также: [обзор](overview.md) · [иконки, движение, тексты](icons-motion-copy.md) · [компоненты](components.md)

## Цвет
- **Динамические цвета** на Android 12+ (`dynamicLightColorScheme` / `dynamicDarkColorScheme`), в настройках переключатель «Цвета системы».
- **Запасная палитра** (Android < 12 или переключатель выключен): генерируем в [Material Theme Builder](https://material.io/material-theme-builder) из одного seed-цвета, экспортируем в `Color.kt`. Предложение: индиго около `#4F5BD5`. Окончательно выбираем по превью (открытый вопрос).
- **Тёмная и светлая темы** по системе; обе обязательны, обе в превью.
- **Статусные цвета** (успех, предупреждение): в M3 их нет, делаем custom colors в Theme Builder (он гармонизирует их с основной палитрой) и кладём в `AppExtendedColors` через `CompositionLocal`.
- Слои — через роли `surface` и `surfaceContainer*`, не через свои серые.

| Элемент | Фон | Контент |
|---|---|---|
| Экран | `surface` | `onSurface` |
| Сообщение пользователя | `secondaryContainer` | `onSecondaryContainer` |
| Сообщение ассистента | без фона (на `surface`) | `onSurface` |
| Карточка инструмента | `surfaceContainerLow` + рамка 1dp `outlineVariant` | `onSurface`, второстепенное `onSurfaceVariant` |
| Композер (поле ввода) | `surfaceContainerHigh` | `onSurface` |
| Шторка подтверждения | `surfaceContainerLow` (по умолчанию M3) | `onSurface` |
| Успех | `successContainer` (custom) | `onSuccessContainer` |
| Ошибка | `errorContainer` | `onErrorContainer` |
| Баннер «нет сети», «сессия истекла» | `surfaceContainerHighest` + иконка | `onSurface` |

## Типографика
- Шрифт интерфейса: **Roboto Flex** (родной для Material) или **Inter**, финальный выбор по превью. Условие: кириллица (проверочная строка «Ёё Щщ Ъъ Ыы»).
- Шрифт кода и аргументов инструментов: **JetBrains Mono**.
- Шрифты **вшиваем** в APK (`res/font`, variable). Скачиваемые Google Fonts не подходят: variable-шрифты через них не поддерживаются, плюс нужна сеть и Google-сервисы. Variable-шрифты в Compose работают с Android 8, а у нас minSdk 26.
- Лицензии шрифтов (OFL) и иконок (Apache 2.0): тексты сохраняем и показываем на экране «О приложении» (этап 6).

| Где | Стиль M3 | Замечание |
|---|---|---|
| Текст сообщений | `bodyLarge` (16/24) | для чтения не меньше 16sp |
| Заголовки в markdown | h1 `titleLarge`, h2 `titleMedium`, h3 `titleSmall` | не крупнее `titleLarge`, чтобы ответы «не кричали» |
| Название инструмента в карточке | `titleSmall` | |
| Статус, подписи | `labelMedium` | |
| Код и аргументы | `bodyMedium`, моно, 14sp | перенос строк включён, горизонтальный скролл для блоков кода |
| Кнопки | `labelLarge` | |
| Заголовок шторки | `titleLarge` | |
| Подсказки, ошибки поля | `bodySmall` | |

Размеры только в `sp`, фиксированной высоты у текстовых контейнеров нет, проверка при масштабе шрифта 200%.

## Формы и глубина
- Формы из M3 (`MaterialTheme.shapes`): композер `extraLarge`, сообщения пользователя `large` (угол у «хвоста» 4dp), карточки `medium`, шторка `extraLarge` сверху.
- Глубина через тональные слои, тени почти не используем.
