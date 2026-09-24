# CI и запуск Gradle

## CI на GitHub
`.github/workflows/check.yml`: на каждый push в `main` и на каждый pull request выполняет `./gradlew check` (сборка, spotless, detekt, lint, unit- и UI-тесты в Robolectric) и `python3 scripts/check_docs.py`. Права токена только на чтение.

`scripts/check.sh` в CI не используется: в нём жёстко прописан путь к JDK 17 в Homebrew (macOS). На runner'е JDK 17 ставит `actions/setup-java`.

**Статус: не проверено.** Файл написан без запуска на GitHub. Первый запуск может упасть на окружении (лицензии Android SDK, Robolectric на Linux, права); чинить по логу. Действия подключены по мажорным тегам (`@v4`), закрепление по SHA — на будущее.

## JDK для Gradle
Gradle 8.13 не умеет работать на JDK 24 (системная в этой среде), а проекту нужна 17. Файл `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=17`) заставляет демон Gradle запускаться на JDK 17. Чтобы Gradle нашёл её на Mac, в `~/.gradle/gradle.properties` должна быть строка `org.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (это локальная настройка, в репозиторий не входит). После этого обычный `./gradlew` работает без `JAVA_HOME` и без флагов; `scripts/check.sh` по-прежнему годится.

## Первый пуш
Репозиторий создаётся и наполняется вручную одной командой владельца (`gh repo create`). Перед первым пушем проверить историю на секреты (`*.jks`, `local.properties`, `spike/auth*.json`, токены).
