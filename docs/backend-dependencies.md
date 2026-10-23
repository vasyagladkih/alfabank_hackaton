# Новые зависимости и notices

Исходный `THIRD_PARTY_NOTICES.md` и публичная frontend-копия сохранены. npm runtime/dev dependencies и lockfile не менялись; React17/Alfa Core Components сохраняют исходные версии. Новые браузерные проверки используют уже присутствующие Playwright/axe. RiskEngine, mock providers, JWT adapter и Python smoke не добавляют ML/API SDK или новые библиотеки.

Сравнение фактически разрешённых Gradle `runtimeClasspath` baseline и интегрированного проекта выявило **четыре новых артефакта**. Прямое добавление issue14 — Spring Boot Flyway starter и PostgreSQL module; остальные приходят транзитивно. Версии управляются существующим Spring Boot4.1.1 BOM.

| Артефакт | Версия | Лицензия / происхождение |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-flyway` | 4.1.1 | Apache-2.0; LICENSE.txt и NOTICE.txt из установленного JAR |
| `org.springframework.boot:spring-boot-flyway` | 4.1.1 | Apache-2.0; LICENSE.txt и NOTICE.txt из установленного JAR |
| `org.flywaydb:flyway-core` | 12.4.0 | Apache-2.0; LICENSE.txt из JAR, declaration из `flyway-parent:12.4.0` POM |
| `org.flywaydb:flyway-database-postgresql` | 12.4.0 | Apache-2.0 declaration наследуется от `flyway-parent:12.4.0`; отдельного LICENSE/NOTICE в module JAR нет |

Полные доступные тексты сохранены в [`licenses/`](licenses/). [`new-backend-dependencies.json`](new-backend-dependencies.json) содержит координаты, SHA-256 установленных JAR/POM и пути к notices. Пустой license-массив в JSON означает отсутствие declaration непосредственно в module POM; для Flyway применяется явно указанный parent, не выдуманная module-attribution. Сравнительные Gradle-логи включены в evidence поставки.

Backend Docker runtime добавляет системный `curl` для readiness HTTP probe. Это пакет Ubuntu Noble с curl license; системные copyright-файлы остаются в образе `/usr/share/doc/`. Остальные базовые образы — Java25 Temurin, Node24, PostgreSQL16, Nginx — сохраняют прежний стек. Их пакеты не копируются в исходный ZIP.

Основные источники артефактов: Maven Central `org/springframework/boot/` и `org/flywaydb/`; проверены локально скачанные POM/JAR. Официальный Flyway parent declaration: `https://repo.maven.apache.org/maven2/org/flywaydb/flyway-parent/12.4.0/flyway-parent-12.4.0.pom`. Это инвентаризация зависимостей, а не правовое заключение или аудит всех транзитивных компонентов исходного backend.
