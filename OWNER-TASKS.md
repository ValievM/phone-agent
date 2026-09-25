# ЗАДАЧИ ВЛАДЕЛЬЦА

Все задачи проекта на GitHub: [issues](https://github.com/ValievM/phone-agent/issues), устройство и метки в [tracker.md](docs/tooling/tracker.md). Здесь только то, что нужно от тебя. Обновляет Claude. Обновлено: 2026-09-25.

**Твой список одним фильтром:** [всё, что ждёт тебя](https://github.com/ValievM/phone-agent/issues?q=is%3Aopen+label%3A%22who%3Aowner%22).

## Команды мне (одним словом)
- **«вливай»**: слить проверенную ветку в `main`.
- **«пушь»**: отправить `main` на GitHub (перед этим проверю коммиты на секреты).
- **«ставь»**: собрать release из `main` и поставить на телефон поверх, вход сохранится ([#11](https://github.com/ValievM/phone-agent/issues/11)).
- **«промпты»**: выдать свежие промпты. Готовые универсальные лежат в [swarm.md](docs/tooling/swarm.md): рой для GPT (сам берёт задачи из issues) и очередь для GLM.
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
| Кто | В работе | Готово к выдаче |
|---|---|---|
| GPT (рой) | [#1](https://github.com/ValievM/phone-agent/issues/1) T13, [#2](https://github.com/ValievM/phone-agent/issues/2) отказ пачки | [#15](https://github.com/ValievM/phone-agent/issues/15) экран «О приложении», записки [#35](https://github.com/ValievM/phone-agent/issues/35) (безопасный «Повторить») и [#36](https://github.com/ValievM/phone-agent/issues/36) (статус «передано»); проверяет сдачи GLM |
| GLM (очередь) | [#3](https://github.com/ValievM/phone-agent/issues/3) G2b, [#4](https://github.com/ValievM/phone-agent/issues/4) защита `confirmBatch`, [#5](https://github.com/ValievM/phone-agent/issues/5) контракт `UserInteraction` | [#6](https://github.com/ValievM/phone-agent/issues/6) контекст целыми ходами, [#14](https://github.com/ValievM/phone-agent/issues/14) `INSTALL.md`, [#34](https://github.com/ValievM/phone-agent/issues/34) проверка «нет фона» |
| Claude | ревью сдач, слияния | [#9](https://github.com/ValievM/phone-agent/issues/9) T15, [#12](https://github.com/ValievM/phone-agent/issues/12) замеры, [#16](https://github.com/ValievM/phone-agent/issues/16) приёмка, [#30](https://github.com/ValievM/phone-agent/issues/30) чистка веток |

`[x]` в `docs/manual-verification*.md` ставишь только ты.
