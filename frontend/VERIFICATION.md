# Frontend #27 — проверка 2026-10-26

Ветка `feat/27-signing-dossier-retry` объединена с `main` (`c7609f2`, merge `d53487c`). Изменения этой работы относятся только к frontend: backend и `docs/openapi.yaml` совпадают с актуальным `main`. Push и merge в `main` не выполнялись.

## Реализовано

- Серверный график платежей и условия заявки, включая явный контроффер при предложении меньшей суммы. Сумма, ставка и платежи не пересчитываются в браузере.
- Подпись с согласием и четырьмя ASCII-цифрами; ведущие нули сохраняются. Контроффер принимается существующим вызовом подписания без передачи суммы клиентом.
- Блокировка повторной подписи при неопределённом/прерванном ответе до успешной сверки GET. Переключение заявок блокировку не сбрасывает. При смене API-сессии прежняя история скрывается, в том числе при ошибке нового GET.
- Досье в нативном модальном `dialog`, оформленном как правая выдвижная панель. Escape, закрытие кнопкой, удержание и возврат фокуса; отмена ожидающих запросов.
- Перезапуск проверки только при допустимых сервером статусе/причине/флаге. Неопределённый POST требует сверки GET; успешный результат сохраняется при последующей ошибке чтения. Коррекция опустевшей последней страницы сохраняет открытое досье.
- Защита терминальных статусов от поздних ответов, сравнение времени с точностью дробной части; более новый переход MANUAL_REVIEW → PROCESSING сохранён.

## Выполненные проверки

Среда: Linux WSL, Node 24.19.0, Chromium Headless Shell 156.0.8078.4 / Playwright 1.64.1. Node, браузер и недостающие shared libraries размещены локально в `/tmp/alfabank-frontend-tools`; системные пакеты не устанавливались. Docker/Java не устанавливались, backend не запускался.

| Проверка | Результат |
| --- | --- |
| `npm ci` | Успешно, lockfile не изменён |
| `npm ls --all` | Успешно, дерево зависимостей совместимо |
| `npm run check` | Успешно: OpenAPI drift, ESLint, Vitest, TypeScript и production build |
| Vitest | 332 теста в 15 файлах прошли |
| `npm run test:e2e -- --workers=2` | Все 28 сценариев Chromium прошли |
| axe / адаптивность | Условия/контроффер и досье: 320/390/1440 px; базовые экраны также 768 px. Переполнения страницы и serious/critical нарушений не обнаружено |
| Независимое ревью | Lifecycle и UX проверены отдельными агентами; замечание о старой истории при смене сессии исправлено и покрыто тестами |

Логи итоговых прогонов: [check](docs/verification/2026-10-26-issue27-check.log), [E2E](docs/verification/2026-10-26-issue27-e2e.log).

В тестах проверены двойные отправки, 400/503/сетевая ошибка, невалидный ответ, неуспешная и успешная сверка, закрытие ожидающего запроса, смена заявки/сессии и поздние ответы. Браузерные сценарии проверяют клавиатуру и реальную модальность; jsdom shim только реализует open/close и не доказывает удержание фокуса.

## Границы результата

Это проверка frontend по контрактным HTTP-фикстурам, не интеграционный прогон Java API. Backend-зависимости остаются отдельно: реальная проверка SMS-кода, показатели РКО и endpoints ручного одобрения/отказа. Существующий backend mapping колонки БКИ также не исправлялся в этой frontend-задаче. Никакие фиктивные данные или действия для этих возможностей в интерфейс не добавлены.

Ниже сохранён исторический отчёт #11 от 2026-10-22; его версии, ограничения backend и числа проверок относятся к той дате.

---

# Issue #11 — реализация и проверка

Дата: 2026-10-22. Это отчёт по фактически выполненной работе и локальным проверкам перед review PR для [issue #11](https://github.com/vasyagladkih/alfabank_hackaton/issues/11).

База: `main` → `191108c233ec9981e144f06a2e6b79801235900b`; Git tree `9306cffe1f8a1859c20a8a3815e821fdc1063f71`. Контракт `docs/openapi.yaml`, blob `74f4a01614a7d656491a971b78d852329c6fee3c`, сохранён без изменений. Рабочая ветка — `feat/11-credit-workspaces`.

## Результат и граница проверки

Реализованы четыре связанных экрана, реальный HTTP-клиент, сохранение/восстановление сессии, разграничение ролей, создание/история/опрос заявок и реестр сотрудника. Production-код не содержит фиктивного API, выдачи токенов или автоматического решения о кредите.

Все локальные функциональные проверки ниже прошли на реальных модулях и собранном интерфейсе. В тестах транспорт заменён контролируемыми ответами по OpenAPI. **Это не проверка совместной работы с запущенным Java backend.** Существующие контроллеры содержат буквальные `{...}`, а реализация авторизации, хранилища и скоринга отсутствует. Backend build и его тесты не запускались; они не объявляются прошедшими.

## Checklist issue #11 → код → проверки

Все пути в колонках кода и тестов относительны `frontend/`.

| Статус | Требование | Реализация | Фактическая проверка |
| --- | --- | --- | --- |
| [x] | Единый OpenAPI и TS-типы | `src/api/schema.d.ts`, `types.ts`, `scripts/check-api.mjs` | `npm run check:api`: точное совпадение генерации с текущим контрактом |
| [x] | `auth_token`, сохранение user, refresh | `src/auth/session.ts`, `AuthProvider.tsx`; ключ профиля `auth_user` | `session.test.ts`, `App.test.tsx`; E2E refresh/logout |
| [x] | Bearer во всех защищённых вызовах, исключение demo-login | `src/api/client.ts` | `client.test.ts`; строгие проверки заголовков в `e2e/fixtures.ts` |
| [x] | Гость → `/login`; CLIENT/EMPLOYEE на чужом маршруте → `/403` с сохранением сессии | `src/App.tsx`, `AuthProvider.tsx` | `App.test.tsx`; отдельные E2E обеих ролей |
| [x] | HTTP 401 очищает сессию, HTTP 403 сохраняет её | API-клиент и AuthProvider; защита от ответа старой сессии | `client.test.ts`, `App.test.tsx`; два E2E для HTTP 401/403 |
| [x] | Три точных карточки и userCode, POST demo-login, переход по серверной роли | `src/pages/LoginPage.tsx` | `App.test.tsx`; три E2E `client_good`, `client_bad`, `bank_employee` |
| [x] | Шапки: компания/ИНН или сотрудник/роль; выход | `src/components/Layout.tsx` | E2E профилей, refresh, logout, смены пользователя |
| [x] | Amount — целое 100000–50000000; term — целое 1–60; четыре purpose | `ApplicationForm.tsx`, `validation.ts`, `model.ts`, `NativeSelectField.tsx` | 28 случаев `validation.test.ts`; E2E неверных границ, фокуса и допустимых минимумов; выбор EQUIPMENT и управление клавиатурой |
| [x] | POST заявки и блокировка повторной отправки | `ApplicationForm.tsx`, API-клиент; без автоматического повтора POST | E2E double-click с задержанным ответом: ровно один POST и disabled-кнопка |
| [x] | Спиннер и «Заявка №{id} в обработке»; polling 2 секунды | `ApplicationResult.tsx`, `polling.ts`, `useClientApplications.ts` | 10 тестов polling; E2E точного текста и запроса после 2000 мс |
| [x] | APPROVED — зелёный баннер; REJECTED — красный и причина; остановка опроса | `ApplicationResult.tsx`, `ApplicationsTable.tsx`; Core Plate/Status | Два терминальных E2E, banner-регрессии, polling-тесты |
| [x] | Отмена при unmount/logout/смене сессии, без параллельного опроса одного ID | AbortController для каждого ID, очистка таймера, проверки устаревшего ответа | `polling.test.ts`, `useClientApplications.test.tsx`, `App.test.tsx`; E2E logout и refresh pending-заявки |
| [x] | История GET `/api/v1/client/applications` | `ClientPage.tsx`, `useClientApplications.ts`, `ApplicationsTable.tsx` | API/component/E2E; завершённая история очищает устаревшую ошибку опроса |
| [x] | Реестр сотрудника со всеми колонками; фильтр и Обновить | `EmployeePage.tsx`, `ApplicationsTable.tsx` | E2E всех фильтров/refresh, 2 component-теста заполненных таблиц |
| [x] | «Все» не трактует отсутствующий status как ALL | Три явных запроса PROCESSING/APPROVED/REJECTED, дедупликация, терминальная версия предпочтительна, сортировка | `client.test.ts`, `model.test.ts`; E2E перемещения ID между статусами и отказа одного запроса |
| [x] | `/403`: точный заголовок, объяснение роли, свой кабинет, смена пользователя | `ForbiddenPage.tsx` | E2E обеих ролей, сохранения/очистки storage и возврата |
| [x] | Loading/error/empty, таймауты и понятный повтор | `Feedback.tsx`, страницы, HTTP-клиент, polling; 15 секунд на запрос | API/polling/component-тесты; E2E отказа ALL и восстановления |
| [x] | Все четыре экрана связаны и адаптивны | Router, Core Components и `src/styles.css` | 16 Chromium E2E; 4 экрана × 4 ширины; просмотр 8 снимков |
| [x] | Реальные Alfa Core Components, совместимость и лицензии | Официальные пакеты, corp.css; FormControl + native select; notices | Чистый `npm ci`, `npm ls --all`; installed API/source review; axe; полный inventory 90 production dependency entries |
| [ ] | Приёмка end-to-end с работающим backend | Production HTTP-вызовы готовы | Не выполнена: в базе backend-заглушки; тестовые ответы не выдаются за Java integration |

`ALL` — три отдельных HTTP-запроса, поэтому атомарность общего снимка не гарантируется. Её может обеспечить только изменение серверного API. Числа вне безопасного диапазона JavaScript отклоняются вместо потери точности. Сохранённая роль управляет интерфейсом; сервер должен проверять JWT и права на данные.

## Выполненные команды и результаты

Среда: Node.js `24.19.0`, npm `11.9.0`, TypeScript `5.9.3`, Vite `8.3.5`, Vitest `5.0.5`, Playwright `1.64.1`, Chromium headless shell `156.0.8078.4` (revision `1248`), axe `4.14.0`.

| Команда | Результат |
| --- | --- |
| `npm ci --no-audit --no-fund` | exit 0; чистая установка 413 пакетов по lockfile |
| `npm ls --all --json` | exit 0; `problems: []`, без `--force`, `--legacy-peer-deps`, overrides |
| `npm run check` | exit 0: OpenAPI drift, ESLint, Vitest, TypeScript, Vite |
| `npm run check:api` (в составе check) | `API types match docs/openapi.yaml.` |
| `npm run lint` (в составе check) | exit 0 |
| `npm test` (в составе check) | 9 файлов, **86 passed**, 0 failed |
| `npm run build` (в составе check) | exit 0; 738 модулей; JS 397.28 kB / gzip 122.21 kB; CSS 279.62 kB / gzip 35.28 kB |
| `PLAYWRIGHT_BROWSERS_PATH=/tmp/issue11-playwright npm run test:e2e -- --workers=2` | **16 passed**, 0 failed; 13.5 секунды |
| Targeted E2E после исправления выбора: `--workers=1 --grep 'all four Core screens\|form reports invalid bounds'` | 2 passed; затем выполнен полный набор выше |
| `npm audit --omit=dev --json` | **exit 1**: 2 moderate entries; 0 low/high/critical. См. отдельный раздел ниже |
| Сверка license inventory с финальным lockfile | 90 entries; missing = 0, extra = 0; совпадают package path/version |

Сохранены реальные выводы финальных запусков: [check.log](docs/verification/2026-10-22-check.log), [e2e.log](docs/verification/2026-10-22-e2e.log), [npm-audit.json](docs/verification/2026-10-22-npm-audit.json). Тестовые таймеры управляются тестами; время в выводе раннера относится к его окружению.

Состав unit/component: session — 12; API client — 14; validation — 28; polling — 10; merge model — 1; App — 15; ApplicationResult — 2; ApplicationsTable — 2; useClientApplications — 2.

Обычный sandbox запрещал bind localhost и запуск Chromium (`EPERM`). Те же необходимые команды реально выполнены с разрешённым исполнением вне этого ограничения. Playwright CDN вернул 195-байтовую HTML-страницу `Site Unavailable`; установлен **тот же требуемый браузер**, взятый из [официального Google Chrome for Testing archive](https://storage.googleapis.com/chrome-for-testing-public/156.0.8078.4/linux64/chrome-headless-shell-linux64.zip). Размер 125797539 bytes, MD5 `743dac7baf99eaa72e221335ea966f07` совпал с Google response header; ZIP CRC и фактический запуск проверены. Версия браузера не подменялась.

В репозитории есть workflow `.github/workflows/frontend.yml` для воспроизведения установки, dependency check, check и Chromium E2E. Локальный результат не выдаётся за результат GitHub Actions: этот отчёт подготовлен до публикации PR. Текущий удалённый статус следует смотреть в PR/Actions.

## Реальная проверка Core, доступности и responsive

Зафиксированы официальные пакеты Button 13.2.4, Input 17.2.2, FormControl 14.0.11, Typography 6.0.13, Plate 9.0.12, Status 5.1.1, Spinner 6.0.9, Table 4.0.30, Themes 15.2.1, Config 1.1.0, StackContext 1.0.1 и icons-glyph 2.293.3. React/react-dom 17.0.2 удовлетворяют полной peer-цепочке, включая `react-virtual@2.10.4` (React 16/17) и `react-popper@2.3.0` (React 16/17/18).

Это реальные модули библиотеки с её compiled corp theme и токенами. CSS приложения задаёт сетку и композицию. Системные шрифты разрешены API Typography; проприетарные Styrene UI/Alfa Interface Sans не добавлены.

Проверка выявила и помогла устранить:

1. Core Table не обрабатывал `false` в children заголовка: передаются плоские массивы действительных ячеек.
2. Core Plate уже имеет `role="alert"`: лишние внешние live regions удалены.
3. Красный desktop Button primary с обычной подписью дал контраст 3.76:1: выбран официальный корпоративный `view="accent"`; токены бренда не заменялись.
4. Текст цветных Status использует официальный токен primary; вторичный текст размещён на белом фоне.
5. Select 19.2.11 создавал unnamed combobox, ссылку aria-controls на отсутствующий закрытый список и SVG без имени. Вместо приватных DOM-исправлений применён поддерживаемый публичный **Core FormControl + нативный select**. Нативное поле не называется Core Select. Есть реальный label, ссылка на ошибку, required/disabled и штатное клавиатурное управление.
6. Терминальная история могла оставить старую ошибку polling; ошибка теперь очищается и не заменяет APPROVED/REJECTED. Независимые контроллеры не перезапускают приостановленный опрос при изменении другой заявки.

Финальный axe-прогон: `/login`, `/client`, `/403`, `/employee` при ширине **320, 390, 768, 1440 px**, теги `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`; **0 serious/critical** в каждом проверенном состоянии. Правила не отключались. Проверялось отсутствие горизонтального overflow у всей страницы; таблицы намеренно прокручиваются внутри контейнера. Снимки всех четырёх экранов при 390/1440 px просмотрены, нативный select проверен ArrowDown/ArrowUp, вход — Enter, невалидная форма переводит фокус в первое поле.

Не заявляется полная WCAG-сертификация. Отдельные реальные screen reader, Safari/WebKit, Firefox и физические мобильные устройства не тестировались.

## Dependency audit: оставшиеся сведения

`npm audit --omit=dev --json` вернул 2 moderate entries: `react-router` и зависящий от него `react-router-dom`. Под ними находятся два официальных advisories:

- [GHSA-wrjc-x8rr-h8h6](https://github.com/remix-run/react-router/security/advisories/GHSA-wrjc-x8rr-h8h6): внешний redirect через недоверенный navigation path.
- [GHSA-337j-9hxr-rhxg](https://github.com/remix-run/react-router/security/advisories/GHSA-337j-9hxr-rhxg): SSR error hydration; официальный advisory исключает Declarative Mode.

Проверены все production `Navigate`, `navigate`, `Link`, `href`: путь задаётся приложением как `/login`, `/403`, `/client`, `/employee`, якорь или файл notices. Данные API/query/location не используются как redirect destination. Приложение использует BrowserRouter в клиентском режиме; SSR/hydration ошибок нет. Это аргумент о текущей применимости, а не заявление об исправлении библиотеки или отсутствии любых уязвимостей.

Предлагаемая npm версия `react-router-dom@7.18.4` проверена через `npm view ... peerDependencies`: React/react-dom `>=18`. Она конфликтует с установленным Core react-virtual peer range, поэтому механический `npm audit fix --force` не выполнялся. Версия 6.30.6 и находки сохранены явно для review. При добавлении динамических redirect/SSR или обновлении Core этот выбор нужно пересмотреть. Development-зависимости этой командой audit не охватывались.

## Лицензии: охват и ограничения

Финальный SHA-256 `package-lock.json`: `92870e196a3259294f83186f5bcd0bd7d761e646cc3692b8f50745912dcbf697`.

Проверены все **90 production dependency entries**, 64 записи источников и 44 группы текстов. Декларации manifests: 85 MIT, 3 Apache-2.0, 1 BSD-3-Clause, 1 0BSD. Это closure пакетов, а не утверждение, что каждый включён в JS bundle.

Полный точный список package/version/source URL/hash и исходных файлов — [docs/LICENSE_SOURCE_INVENTORY.json](docs/LICENSE_SOURCE_INVENTORY.json); сами notices — [../THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md) и идентичный публичный `public/third-party-notices.txt`, доступный по ссылке в footer.

Явные исключения сохранены:

- 32 Core-архива не содержат LICENSE: использован официальный корневой MIT LICENSE по commit `1fa28dd0cb78b3975394e7ad14a602eb7b162ad2`.
- icons-glyph 2.293.3 объявляет MIT, но полный upstream tree `440b15a6a0481e440ce739150d15968a42cf502e` не содержит license/notice/copyright-файла, а прочитанные manifest/README — указания правообладателя. Отсутствующая атрибуция не выдумывалась.
- intersection-observer 0.12.2: manifest Apache-2.0 расходится с фактическим W3C 2015 header. Сохранены обе декларации и Google copyright; расхождение не объявляется разрешённым или двойным лицензированием.
- react-remove-scroll-bar 2.3.8: точный npm gitHead недоступен в публичном upstream; лицензия взята из текущего официального pinned commit, что помечено как fallback.
- Полная юридическая проверка прав на бренды и шрифты не выполнялась. Файлы шрифтов не распространяются; в `docs/FONT_METADATA.json` сохранены только прочитанные метаданные.

## Точный репозиторный контекст, прочитанный до PR

Сверялись исходники и GitHub, не только доступность подключения. Повторная проверка перед публикацией: **2026-10-22 19:15:31 Europe/Istanbul (16:15:31 UTC)**. `main` всё ещё `191108c…`, одна ветка main, PR-list `state=all` пустой, issue #11 открыт, комментариев нет. Recursive tree вернулся полностью (`truncated: false`), 22 файла. Тело issue не изменилось относительно первого прочтения; baseline paths/blob hashes повторно сверены.

В базе нет `AGENTS.md` и `CONTRIBUTING*` ни в корне, ни в подкаталогах. Также проверены родительские каталоги workspace на AGENTS.md — отсутствует. `.github` содержит только пять issue templates, перечисленных ниже. `docs` содержит только `openapi.yaml`; корневые Readme/THIRD_PARTY_NOTICES имеют 0 bytes. Эти два файла заполнены текущим изменением. Workflow и документы реализации добавлены текущим PR, они не выдаются за прежние инструкции владельца.

**Все 21 текстовый файл baseline прочитаны полностью:**

1. `.github/ISSUE_TEMPLATE/Backend.yml`
2. `.github/ISSUE_TEMPLATE/Data _Delivery.yml`
3. `.github/ISSUE_TEMPLATE/Frontend.yml`
4. `.github/ISSUE_TEMPLATE/Product_System.yml`
5. `.github/ISSUE_TEMPLATE/Risk _Scoring.yml`
6. `Readme.md` — пустой в базе.
7. `THIRD_PARTY_NOTICES.md` — пустой в базе.
8. `backend/alfahackaton/.gitattributes`
9. `backend/alfahackaton/.gitignore`
10. `backend/alfahackaton/build.gradle`
11. `backend/alfahackaton/gradle/wrapper/gradle-wrapper.properties`
12. `backend/alfahackaton/gradlew` — все 248 строк.
13. `backend/alfahackaton/gradlew.bat` — все 112 строк.
14. `backend/alfahackaton/settings.gradle`
15. `backend/alfahackaton/src/main/java/ru/alfahackaton/AlfahackatonApplication.java`
16. `backend/alfahackaton/src/main/java/ru/alfahackaton/api/handler/EmplController.java`
17. `backend/alfahackaton/src/main/java/ru/alfahackaton/api/handler/UserController.java`
18. `backend/alfahackaton/src/main/java/ru/alfahackaton/api/interceptor/ExceptionHandler.java`
19. `backend/alfahackaton/src/main/resources/application.properties`
20. `backend/alfahackaton/src/test/java/ru/alfahackaton/AlfahackatonApplicationTests.java`
21. `docs/openapi.yaml` — все 391 строка.

**Прочитанные внешние/пользовательские источники:**

- Полное тело [issue #11](https://github.com/vasyagladkih/alfabank_hackaton/issues/11), metadata и комментарии (пустой список). Единственная указанная в issue спецификация — `docs/openapi.yaml`; других linked docs в теле нет.
- Прикреплённый `Вставленный текст.txt` целиком: требования, разрешения, полный вставленный API-контракт. Контракт сверён с актуальным blob, а управляющий символ ошибочного storage key из issue не скопирован.
- GitHub API репозитория: `/branches?per_page=100`, `/git/trees/main?recursive=1`, `/pulls?state=all&per_page=100`, `/issues/11`, `/issues/11/comments?per_page=100`. Login `be0hhh` подтверждён отдельно; доступ на чтение и поле push в metadata не считаются доказательством права интеграции записывать Git objects.
- [Запрошенная Core Storybook URL](https://core-ds.github.io/core-components/master/?path=/docs/intro--docs): получена страница, но извлечённого текста 0 строк. Визуальное изучение всего Storybook не заявляется. Прочитаны официальные исходные MDX, API и CSS опубликованных пакетов — **все точные 19 repository paths/URLs и package files перечислены в [docs/CORE_COMPONENTS_SOURCE_AUDIT.md](docs/CORE_COMPONENTS_SOURCE_AUDIT.md)**.
- Официальные технические руководства, использованные при выборе/настройке tooling: [Vite guide](https://vite.dev/guide/), [openapi-typescript introduction](https://openapi-ts.dev/introduction), [Vitest guide](https://vitest.dev/guide/), [React Router installation](https://reactrouter.com/start/declarative/installation), [Playwright browsers](https://playwright.dev/docs/browsers). Текущая страница Router не использована как доказательство совместимости v6: совместимость установлена по фактическим pinned manifests/peers.
- Registry manifests/tarballs и официальные license sources: полный inventory с точными URL/versions/hash приведён выше. Включены W3C 2015 terms, Google source header, Microsoft tslib CopyrightNotice и остальные реальные notices; отсутствующие upstream сведения помечены.
- Официальные refs Actions v6: `actions/checkout` → `d23441a48e516b6c34aea4fa41551a30e30af803`, `actions/setup-node` → `249970729cb0ef3589644e2896645e5dc5ba9c38`; workflow закреплён по SHA.
- Два официальных React Router advisories и metadata исправленной версии 7.18.4, указанные в разделе audit.

## Недоступное, непрочитанное и непроверенное

- `backend/alfahackaton/gradle/wrapper/gradle-wrapper.jar`: бинарный файл 48749 bytes, blob `9fa7f288283f81eec4952784c271abd7e0938dee`. Проверены наличие/размер/hash; бинарное содержимое не разбиралось.
- Полная история всех прошлых коммитов, другие issues, GitHub wiki/Projects и не связанные с issue документы не исследовались. Приведённый охват относится к полному **актуальному дереву main**, issue и перечисленным источникам.
- Внешние ссылки из boilerplate Gradle wrapper не обходились; полный сайт Core и все вложенные справочные ссылки из MDX не прочитаны. Полнота охвата этих сайтов не заявляется.
- Интерактивный Storybook не использовался как успешно прочитанная документация. Извлечение отдельных GitHub страниц через web также давало DisabledError; соответствующие официальные исходники затем реально получены через GitHub/raw, что отражено в audit.
- Отсутствующие upstream attribution/gitHead сведения перечислены в разделе лицензий; отдельных прав на шрифты проекту не предоставлено.
- Реальный backend, выдача/проверка JWT сервером, scoring, DB и полный frontend+backend acceptance не проверены. Backend source read не равен backend build.
- Удалённый CI до публикации PR не выполнялся. Deployment, merge, force push, изменение main и ручное закрытие issue не выполнялись и не входят в задачу. Описание PR должно содержать **`Closes #11`**; закрытие происходит при принятии владельцем.

## Блокер публикации PR и переносимый результат

После успешных локальных проверок GitHub отклонил фактический `POST /git/trees`: HTTP 403, `Resource not accessible by integration`. Проверка collaborator permission через тот же connector также вернула 403. Альтернативный read-only `GIT_TERMINAL_PROMPT=0 git push --dry-run origin HEAD:refs/heads/feat/11-credit-workspaces` завершился exit 128: `could not read Username for 'https://github.com': terminal prompts disabled`.

Это ограничения доступного подключения и отсутствие git-аутентификации, не отказ тестов и не отказ automatic approval review. PR и удалённая feature-ветка не созданы. Работа сохранена в локальном коммите; подготовлен полный ZIP исходников с patch, точным описанием PR (`Closes #11` + checklist), этим отчётом и логами. Patch проверяется применением к отдельному Git index базового коммита и сравнением полученного tree с итоговым коммитом. Для публикации необходимо подключение с разрешённой записью содержимого/PR и workflow либо аутентифицированный git/gh. Учётные данные в архив не включаются.

## Независимое review

Review производственного кода, тестов и контрактов не выявило critical дефектов. Обнаруженная important-проблема устаревшей ошибки polling при терминальном ответе истории исправлена с регрессиями. Два minor-замечания — независимый lifecycle нескольких polling ID и минимальная версия Node — также исправлены и повторно проверены. Follow-up review публичного FormControl/native select не выявило новых important дефектов. Это review не подменяет перечисленные реальные тесты и не является разрешением на merge.
