# ЗАДАЧИ ВЛАДЕЛЬЦА

Все задачи проекта на GitHub: [issues](https://github.com/ValievM/phone-agent/issues), устройство и метки в [tracker.md](docs/tooling/tracker.md). Здесь только то, что нужно от тебя. Обновляет Claude. Обновлено: 2026-09-26 (влито в `main`: #2, #3, #4, #5, #6, #14, #34; на GitHub пока не пушено).

**Твой список одним фильтром:** [всё, что ждёт тебя](https://github.com/ValievM/phone-agent/issues?q=is%3Aopen+label%3A%22who%3Aowner%22).

## Команды мне (одним словом)
- **«вливай»**: слить проверенную ветку в `main`.
- **«пушь»**: отправить `main` на GitHub (перед этим проверю коммиты на секреты).
- **«ставь»**: собрать release из `main` и поставить на телефон поверх, вход сохранится ([#11](https://github.com/ValievM/phone-agent/issues/11)).
- **«промпты»**: выдать свежие промпты. Готовые универсальные лежат в [swarm.md](docs/tooling/swarm.md): рой для GPT-1 и GPT-2 (сами берут задачи из issues) и очередь для GLM.
- **«нравится»** или **«не нравится»** про вид «Стыка»: при «нравится» запишу эталоны скриншотов ([#17](https://github.com/ValievM/phone-agent/issues/17)).

## Решения (ответь одним словом в issue или мне в чате)
- [#7](https://github.com/ValievM/phone-agent/issues/7) «Повторить» не должен выполнять действия заново. Я за «да»
- [#8](https://github.com/ValievM/phone-agent/issues/8) Честный статус «передано другому приложению» вместо зелёной галочки. Я за «да»
- [#20](https://github.com/ValievM/phone-agent/issues/20) Чистить ли историю и папку поиска при выходе. Я за «спрашивать»
- [#22](https://github.com/ValievM/phone-agent/issues/22) Зачем нужен `notify`. Я за «уникальный id и открытие приложения по нажатию»
- [#21](https://github.com/ValievM/phone-agent/issues/21) Название приложения и `applicationId`: проверить Tenon?
- [#18](https://github.com/ValievM/phone-agent/issues/18) Несколько чатов или фоновый агент (T7). Я за «нет»
- [#19](https://github.com/ValievM/phone-agent/issues/19) Журнал будильников. Я за «да, после MVP»

## Телефон и релиз
- [#10](https://github.com/ValievM/phone-agent/issues/10) Проверка на телефоне: 8 главных сценариев (после «ставь»)
- [#9](https://github.com/ValievM/phone-agent/issues/9) Проверка протокола на живом бэкенде (T15): нужно, чтобы ты был рядом при входе
- [#13](https://github.com/ValievM/phone-agent/issues/13) Релизная подпись и APK 0.1.0: keystore создаёшь ты, пароли только у тебя

## Кто чем занят
| Кто (зона) | В работе | Готово к выдаче |
|---|---|---|
| GPT-1 (логика) | [#1](https://github.com/ValievM/phone-agent/issues/1) T13: осталось 2 мелкие правки, тесты разрешены | [#35](https://github.com/ValievM/phone-agent/issues/35) записка «безопасный «Повторить»» |
| GPT-2 (интерфейс) | | [#15](https://github.com/ValievM/phone-agent/issues/15) экран «О приложении», [#36](https://github.com/ValievM/phone-agent/issues/36) записка «статус «передано»» |
| GLM (тесты, доки) | | [#37](https://github.com/ValievM/phone-agent/issues/37) техдолг, [#38](https://github.com/ValievM/phone-agent/issues/38) правило `ArchitectureTest`; [#32](https://github.com/ValievM/phone-agent/issues/32) парковка |
| Claude (интеграция, релиз) | ревью сдач, слияния | [#9](https://github.com/ValievM/phone-agent/issues/9) T15, [#12](https://github.com/ValievM/phone-agent/issues/12) замеры (нужен телефон), [#16](https://github.com/ValievM/phone-agent/issues/16) приёмка, [#30](https://github.com/ValievM/phone-agent/issues/30) чистка веток, [#31](https://github.com/ValievM/phone-agent/issues/31) CI |

Кто кого проверяет и как: [swarm.md](docs/tooling/swarm.md).

`[x]` в `docs/manual-verification*.md` ставишь только ты.
