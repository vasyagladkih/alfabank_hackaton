# Frontend — кредитный demo-кабинет

Четыре связанных русскоязычных экрана: `/login`, `/client`, `/employee`, `/403`. Корень `/` перенаправляет на `/login`. Данные поступают через `fetch` по общему [`docs/openapi.yaml`](../docs/openapi.yaml); тестовые ответы не включены в приложение.

## Локальный запуск

Node.js 24.15+ в пределах major 24 и npm 11; точные зависимости закреплены в `package-lock.json`.

```sh
cd frontend
npm ci
cp .env.example .env.local
npm run dev
```

Откройте адрес, напечатанный Vite. В dev-режиме `/api` проксируется на `http://localhost:8080`. Для другого backend задайте `API_PROXY_TARGET` в `.env.local` и перезапустите Vite. Эта переменная используется только dev-сервером и не попадает в браузер.

`VITE_API_BASE_URL` по умолчанию пустая: запросы идут на тот же origin. При отдельном API можно задать, например, `VITE_API_BASE_URL=https://api.example.test` **перед сборкой**; backend должен разрешать CORS и заголовок `Authorization`. Секреты в `VITE_*` хранить нельзя: эти значения входят в публичный bundle.

```sh
npm run build
npm run preview
```

Готовая сборка находится в `dist/`. Для размещения SPA сервер должен возвращать `index.html` для `/login`, `/client`, `/employee`, `/403` и отдельно направлять `/api/v1/*` в backend. Публикация и deployment в рамках этой задачи не выполняются.

## Alfa Core Components

Используются официальные отдельные пакеты, как описано в [документации установки](https://github.com/core-ds/core-components/blob/master/docs/installation.stories.mdx), и корпоративная тема `@alfalab/core-components-themes/corp.css`. Это реальные компоненты библиотеки: Button, Input, FormControl, Typography, Plate, Status, Spinner и Table, а также glyph-иконки Alfa.

| Пакет | Версия |
| --- | --- |
| `@alfalab/core-components-button` | 13.2.4 |
| `@alfalab/core-components-input` | 17.2.2 |
| `@alfalab/core-components-form-control` | 14.0.11 |
| `@alfalab/core-components-typography` | 6.0.13 |
| `@alfalab/core-components-plate` | 9.0.12 |
| `@alfalab/core-components-status` | 5.1.1 |
| `@alfalab/core-components-spinner` | 6.0.9 |
| `@alfalab/core-components-table` | 4.0.30 |
| `@alfalab/core-components-themes` | 15.2.1 |
| `@alfalab/core-components-config` / `stack-context` | 1.1.0 / 1.0.1 |
| `@alfalab/icons-glyph` | 2.293.3 |
| `react` / `react-dom` | 17.0.2 |
| `react-router-dom` | 6.30.6 |

Совместимость проверяется для полного дерева, а не только прямых peer dependencies. Транзитивный `react-virtual@2.10.4` поддерживает React 16/17; `react-popper@2.3.0` — React 16/17/18. Поэтому выбран React 17.0.2, совместимый со всеми установленными пакетами. `--force`, `--legacy-peer-deps` и подмена peer dependencies не используются. TypeScript закреплён на 5.9.3, совместимой с openapi-typescript и typescript-eslint.

Тема импортируется **после** модулей компонентов в `src/main.tsx`, чтобы её значения не были заменены стандартными значениями компонентов. Разметка использует системные токены цветов, отступов, скруглений и `--font-family-system`. Для заголовков явно указан `font="system"`, что поддерживается [типографикой Core](https://github.com/core-ds/core-components/blob/master/docs/typography.stories.mdx). Файлы Styrene UI и Alfa Interface Sans не включены: отдельная лицензия на эти шрифты проекту не предоставлена. Лицензии кода и иконок проверены отдельно; см. [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

Адаптивные компоненты используют стандартную границу Core 1024 px. Сетка приложения дополнительно перестраивается на 768 px; широкие семантические таблицы прокручиваются внутри собственного контейнера. Есть связанные подписи и ошибки полей, видимый фокус, ссылка пропуска навигации, фокус заголовка при переходе, объявления статусов и поддержка reduced motion.

В браузерной проверке красный desktop `view="primary"` темы corp с обычной подписью 16 px дал контраст 3.76:1. Основные действия поэтому используют штатный `view="accent"` этой же корпоративной темы: тёмный фон и светлую подпись, без изменения брендовых цветов. Текст Status на цветных фонах использует токен `--color-light-text-primary`; вторичный текст размещён на белом фоне. Эти решения проверяются axe и computed styles в браузере. Plate сам предоставляет `role="alert"`, поэтому вокруг него нет второго live region.

У опубликованного `Select@19.2.11` фактический axe-прогон обнаружил combobox без доступного имени, `aria-controls` на отсутствующий закрытый список и SVG без альтернативы. Два поля выбора поэтому используют **публичный Core FormControl 14.0.11 с нативным `<select>`** (`src/components/NativeSelectField.tsx`): Core рисует адаптивный контейнер, подпись и ошибку; браузер обеспечивает семантику, клавиатуру и меню выбора. Связь `<label htmlFor>` и ошибки задаётся явно. Это композиция с Core FormControl, а не утверждение об использовании Core Select. Приватный DOM библиотеки не исправляется вручную, правила axe не отключены. Select остаётся транзитивной зависимостью Table/Pagination, но эти поля его не рендерят. Публичный API и официальные документы перечислены в [аудите источников](docs/CORE_COMPONENTS_SOURCE_AUDIT.md).

На 2026-10-22 `npm audit --omit=dev --json` сообщает 2 moderate-записи (`react-router` и зависимый `react-router-dom`), 0 high/critical. Два advisories касаются недоверенных адресов навигации и SSR hydration. Здесь используется клиентский BrowserRouter, все адреса переходов заданы приложением, SSR и пользовательского redirect-параметра нет. Это оценка применимости к текущему коду, а не исправление уязвимого пакета. Предлагаемая npm версия Router 7.18.4 требует React 18+, конфликтующий с `react-virtual@2.10.4`. Совместимая версия 6.30.6 сохранена без принудительного обхода peers; подробности и ссылки — в [VERIFICATION.md](VERIFICATION.md).

## API и жизненный цикл

- Demo-login отправляет один из `client_good`, `client_bad`, `bank_employee` без Bearer-заголовка. Возвращённые `token` и `user` проходят проверку формы данных и сохраняются под `auth_token` / `auth_user`.
- Контракт не содержит `/me`: при обновлении страницы восстанавливается проверенная пара из storage. Сохранённая роль управляет навигацией; права на данные должен проверять сервер.
- Остальные запросы содержат `Authorization: Bearer <token>`. HTTP 401 очищает сессию, HTTP 403 оставляет её и открывает страницу запрета. Ответ отменённого запроса или старой сессии не может завершить новую сессию.
- Сумма — целое число 100000–50000000 ₽, срок — 1–60 месяцев включительно, цель — одно из четырёх значений контракта. Повторный submit во время запроса заблокирован. POST никогда не повторяется автоматически; при сетевой неопределённости предлагается сначала обновить историю.
- Polling начинается через 2000 мс, следующий запрос планируется после завершения предыдущего. Для одного ID нет параллельных запросов. PROCESSING/MANUAL_REVIEW/OFFER_PROPOSED продолжают синхронизацию до APPROVED/REJECTED; unmount/logout/смена сессии отменяют запрос и таймер. Незавершённые заявки восстанавливают опрос из истории после refresh. Регрессии восстановленных баннеров сохранены.
- При временных ошибках опрос повторяется; после трёх ошибок подряд приостанавливается с кнопкой повтора. Постоянные 4xx, кроме 429, прекращают опрос сразу. У запросов есть таймаут 15 секунд.
- В API нет значения фильтра `ALL`; отсутствие status возвращает все заявки (исправлено прежнее расхождение с документацией). Для совместимости UI «Все» делает явные GET по пяти статусам, применяет поиск и объединяет ответы по ID/монотонной версии. При ошибке одного GET частичный реестр не показывается. Несколько GET не являются атомарным снимком всей очереди; обновление делает новую выборку.
- Суммы заявки/предложения — целые RUB. Расчётные денежные значения и ставки — десятичные строки с сервера; UI только форматирует их. Debt ratio передаётся долей (0.45=45%). Небезопасные JS-числа, неизвестные статусы, неверные версии и форма досье отклоняются runtime validation.
- Employee queue поддерживает поиск, открытие досье, breakdown, источники/свежесть и аудит. Решения отправляются настоящему API с expectedVersion, UUID requestId и обязательным основанием. На 409 досье перечитывается, ввод сохраняется, автоматического повторного решения нет. Серверные allowedActions ограничивают интерфейс, сервер повторно проверяет условия.
- Встречное предложение завершается настоящим client ACCEPT/REJECT endpoint, проверяющим владельца и версию. Кнопки не используют mocks; все источники маркируются demo и не создают обязательств.

## Проверки

```sh
npm run check              # OpenAPI drift, ESLint, Vitest, TypeScript, Vite build
npm ls --all               # полная проверка дерева зависимостей
npx playwright install --with-deps chromium
npm run test:e2e           # Chromium по production bundle, сначала npm run build
REAL_STACK_BASE_URL=http://localhost:3000 npm run test:fullstack # уже запущенный Java/PG/Nginx
```

`npm run generate:api` обновляет `src/api/schema.d.ts` после согласованного изменения OpenAPI. `npm run check:api` проверяет точное соответствие, не изменяя файл.

Unit/component-тесты подменяют только транспорт/окружение. `test:e2e` проверяет bundle с HTTP fixtures в `e2e/fixtures.ts`; это **не fullstack**. Отдельный `test:fullstack` использует работающий Nginx/Java/PostgreSQL без перехвата транспорта и проверяет клиентское создание, employee counteroffer, ответ клиента, refresh/logout, responsive/keyboard/axe. Для него нужен свежий disposable seed, поскольку учебные решения расходуют сценарии.

Исторические логи issue11 от 2026-10-22 сохранены в [VERIFICATION.md](VERIFICATION.md) и `docs/verification/`; они не являются результатами новых пакетов. Актуальные команды, exit codes, screenshots и ограничения этой поставки находятся в верхнем VERIFICATION.md/evidence архива. Проверки выбранных состояний axe не являются полной сертификацией доступности.

Workflow `.github/workflows/frontend.yml` проверяет PR, main и feature/fix pushes; `.github/workflows/fullstack.yml` добавляет Java25/PG16 и реальный Compose/Nginx/browser. Deployment или автоматического изменения main в них нет. Удалённые CI-прогоны в рамках подготовки ZIP не запускались.

## Состояние backend

Backend находится в `../backend/` и реализует demo JWT/RBAC, JDBC persistence, immutable snapshots, pure rules, bounded durable processing, employee decisions и client offer response. Источники FNS/BKI/internal — явные mock providers. Запуск и точки расширения описаны в [корневом README](../Readme.md) и [provider contracts](../docs/provider-contracts.md). Наличие кода и workflow не заменяет фактические логи проверок поставки.
