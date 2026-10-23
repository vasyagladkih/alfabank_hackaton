# Умный кредитный кабинет предпринимателя — demo-прототип

Рабочий хакатонный конвейер на **синтетических данных**: клиент создаёт заявку, backend сохраняет снимок входов mock-провайдеров, рассчитывает объяснимую рекомендацию и показывает её клиенту/сотруднику. Сотрудник рассматривает спорные заявки, сохраняет основание решения или предлагает меньшую сумму. Клиент принимает либо отклоняет учебные условия; состояние синхронизируется в обоих кабинетах.

Это учебный прототип, не банковская система реальных решений. В нём нет live-запросов ФНС/БКИ, персональных финансовых данных, ML, договора, подписи или выдачи денег. Пороги, ставки и баллы — допущения demo. Полная официальная рубрика кейса не приложена; team wiki не объявляется такой рубрикой.

## Стек и устройство

| Слой | Реализация |
|---|---|
| Интерфейс | React 17.0.2, TypeScript 5.9.3, Vite 8.3.5; официальные Alfa Core Components и corporate theme |
| API | Java 25, Spring Boot 4.1.1, JDBC; OpenAPI → Spring/TypeScript |
| Хранение | PostgreSQL 16, аддитивные Flyway-миграции, снимки/результаты/аудит |
| Расчёт | Чистый Java `RiskEngine`, `BigDecimal`, `DEMO_RULES_V1`, без сети/ML |
| Данные | `InternalFinanceDataProvider`, `BkiDataProvider`, `FnsDataProvider` с mock implementations |
| Вход | Три прежних userCode, `DemoIdentityProvider`, строго проверяемые HS256 JWT, CLIENT/EMPLOYEE/ownership |
| Запуск | Compose: PostgreSQL → Java → Nginx со статическим frontend и `/api/` proxy |

Сохранены `/login`, `/client`, `/employee`, `/403`, polling и восстановление баннеров. Досье открывается в employee workspace. Backend находится в `backend/`; прежний текст о незавершённых контроллерах устарел.

## Быстрый локальный запуск

Нужны Docker с Compose v2 и свободные локальные порты 3000/8080/5432. Из корня:

```sh
cp .env.example .env
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
```

Открыть **http://localhost:3000**. `GET /api/health` через тот же адрес проверяет готовность приложения после startup/seed и доступность PostgreSQL. Порты Compose привязаны к loopback. После миграций и seed доступны 21 сценарий из [`docs/demo-scenarios.json`](docs/demo-scenarios.json).

`.env.example` содержит явно публичный **локальный demo-ключ**, не реальный секрет. Backend не подставляет встроенный ключ: без `JWT_SECRET` (≥32 байт) или с redacted placeholder запуск завершается ошибкой. `DEMO_MODE=true` отдельно разрешает переключение учебных ролей; Bearer API проверяет подпись, срок, alg/typ, issuer/audience, subject и роль. Настоящий банковский вход подключается отдельным адаптером.

Прежний named volume `postgres_data` сохраняется. Миграции сохраняют users/applications, ID и данные. Повторный seed не переписывает решения/snapshots/audit. `docker compose down` останавливает стек, сохраняя данные. **`docker compose down --volumes` уничтожает базу**: использовать только для собственной одноразовой demo-базы. Сброс не выполняется автоматически; обновление существующей базы сначала проверяется на её резервной копии.

## Сценарий демонстрации

1. Войти как `client_good`, подать 1 500 000 ₽ на 12 месяцев, дождаться результата и обновить страницу.
2. Подать 10 000 000 ₽ на 12 месяцев: стандартный synthetic profile получит меньшее доступное предложение. Принять либо отклонить его; исходная сумма остаётся в истории.
3. Войти как `bank_employee`, выбрать ручное рассмотрение, открыть досье. Изучить вклад факторов, причины, mock-факты, свежесть и аудит, сохранить разрешённое решение с основанием.
4. Вернуться к клиенту: polling/история покажут результат. Две команды одной версии дают 200/409, точный повтор команды не создаёт второй audit.
5. `client_bad` показывает объяснённый отказ; seeded missing/stale — отсутствие достаточных данных без выдуманного балла.

Login-коды не задают жёсткий исход: прежний переключатель по ИНН заменён расчётом по snapshot. Разные seed-сценарии принадлежат тем же трём профилям.

## Настройки

| Переменная | Default вне Compose | Назначение |
|---|---|---|
| `JWT_SECRET` | нет | Явно заданный ключ локальной demo-сессии |
| `DEMO_MODE` | `false` | Demo-login; при false отвечает 403 |
| `AUTH_PROVIDER` / `DATA_PROVIDER` | `mock` | Только shipped mock adapters; новый адаптер регистрируется явно |
| `DEMO_SEED_ENABLED` | `false` | Идемпотентный synthetic seed |
| `DEMO_AS_OF` | `2026-10-23` | Фиксированная дата оценки штатных fixtures |
| `DEMO_PROCESSING_ENABLED` | `true` | Worker; выключатель нужен также для restart-test |
| `DEMO_PROCESSING_BATCH_SIZE` | `8` | Максимум 64 кратких транзакции в batch |
| `DEMO_PROCESSING_DELAY_MS` | `1000` | Пауза между batch |
| `DEMO_PROCESSING_MAX_ATTEMPTS` | `3` | Ограниченные повторы с записью попыток в БД |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Точные origins, без wildcard/credentials |
| `DB_HOST/PORT/NAME/USER/PASSWORD` | `localhost/5432/alfadb/postgres/postgres` | Локальная PostgreSQL demo-база |

Неизвестный AUTH_PROVIDER/DATA_PROVIDER останавливает запуск из-за отсутствующего bean: скрытого live-fallback нет. Изменение DEMO_AS_OF без нового набора данных может перевести новые заявки в ручное рассмотрение; сохранённые заявки остаются привязаны к исходной дате/версии.

Worker читает durable jobs с PostgreSQL SKIP LOCKED и коммитит результат/состояние/версию/audit/job атомарно. После сбоя незавершённая транзакция откатывается, job остаётся. После ограниченного числа ошибок — ручное рассмотрение с объяснением; неполные данные нельзя одобрить обходным решением. Отдельные брокеры и распределённые leases для этой демонстрации не нужны.

## Разработка без Docker

Нужны Java25, PostgreSQL16 и Node24.15+ в пределах major24 / npm11. Создайте локальную demo-базу alfadb, затем из корня:

```sh
cp .env.example .env
set -a
. ./.env
set +a
export DEMO_MODE=true DEMO_SEED_ENABLED=true AUTH_PROVIDER=mock DATA_PROVIDER=mock
export CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
cd backend
./gradlew bootRun
```

В другом терминале:

```sh
cd frontend
npm ci
cp .env.example .env.local
npm run dev
```

Vite проксирует `/api` на localhost:8080; другой dev-backend — через API_PROXY_TARGET. Для этого запуска выше явно заданы origins порта 5173: корневой `.env.example` по умолчанию рассчитан на Nginx/Compose 3000. Переменные VITE_* публичны и не должны содержать секреты. [Frontend README](frontend/README.md) объясняет компоненты и доступность.

## Проверки

```sh
python3 scripts/generate-demo-seed.py --check
cd frontend
npm ci
npm ls --all
npm run check
npx playwright install --with-deps chromium
npm run test:e2e
```

В отдельной тестовой PostgreSQL-базе тесты создают уникальные схемы и удаляют только их:

```sh
cd backend
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/alfa_tests \
TEST_DATABASE_USER=postgres TEST_DATABASE_PASSWORD=postgres ./gradlew test bootJar
```

Без TEST_DATABASE_URL PG-тесты явно пропускаются; это не успешная проверка БД. contextLoads с mocked datasource также не заменяет интеграцию.

На свежем одноразовом seed через **работающий Nginx**, без перехвата HTTP:

```sh
python3 scripts/fullstack-smoke.py --base-url http://localhost:3000
cd frontend
REAL_STACK_BASE_URL=http://localhost:3000 npm run test:fullstack
```

Команды расходуют seeded manual-сценарии, принимая учебные решения. Для повторяемой приёмки используйте отдельную чистую demo-базу. Smoke проверяет 3 роли, 401/403/IDOR/404/400, скоринг, approve/reject/counteroffer, ответ клиента, конфликт и replay. Real-browser suite проверяет UI→Java→mock providers, responsive320/390/768/1440, клавиатуру и axe; test:e2e остаётся отдельным fixture UI suite.

Restart: запустить worker=false, выполнить `fullstack-smoke.py --mode create-pending`, перезапустить backend с worker=true, выполнить `--mode verify-recovered`. При пересоздании backend в Compose перезапустите frontend для обновления Nginx upstream. `.github/workflows/fullstack.yml` содержит этот сценарий, Java25/PG16 и browser/Nginx gate. Workflow-файлы не означают, что удалённый CI уже запускался; фактические результаты поставки — в верхнем VERIFICATION.md архива.

## Документы и границы

- [OpenAPI](docs/openapi.yaml) — активный контракт; generation/drift должны проходить вместе.
- [Признаки](docs/data-dictionary.md), [seed](docs/demo-seeding.md), [правила](docs/risk-policy.md) — данные, сценарии, допущения/округления.
- [Employee specification](docs/employee-workspace-spec.md) — состояния, RBAC, поля, конфликты и матрица тестов.
- [Provider contracts](docs/provider-contracts.md) — mock adapters и точки расширения.
- [Сохранённые notices](THIRD_PARTY_NOTICES.md), [новые зависимости](docs/backend-dependencies.md).

Реальные интеграции/данные, ML/AutoML, новости, подпись/выдача, PDF/CSV и портфельная аналитика не входят в ядро. NPL/CoR/PD не вычисляются: реальных наблюдений нет. Demo-auth и публичные локальные fixtures не предназначены для публичного размещения.
