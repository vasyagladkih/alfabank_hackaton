# Спецификация REST API: Умный кредитный кабинет ММБ

## 1. Общие сведения и стандарты

API кредитного конвейера спроектирован по принципу **Contract-First** на основе спецификации OpenAPI 3.0.3 ([`docs/openapi.yaml`](openapi.yaml)).

* **Базовый URL разработки:** `http://localhost:8080`
* **Формат передачи данных:** `application/json;charset=UTF-8`
* **Формат даты и времени:** ISO-8601 (`YYYY-MM-DDThh:mm:ss.sssZ` / `OffsetDateTime`)
* **Кодогенерация:** DTO-классы и валидационные модели генерируются автоматически в пакет `ru.alfahackaton.dto.*` с помощью плагина `openapi-generator-maven-plugin / gradle`.

---

## 2. Аутентификация и авторизация

Сервис функционирует как **OAuth 2.0 Resource Server**. Доступ к защищенным эндпоинтам осуществляется посредством передачи JWT-токена в заголовке запроса:
```http
Authorization: Bearer <JWT_TOKEN>
```

### Ролевая модель (RBAC):
* **`ROLE_CLIENT`:** предприниматель или уполномоченный представитель юридического лица. Имеет доступ исключительно к контуру `/api/v1/client/**`.
* **`ROLE_EMPLOYEE`:** сотрудник банка (кредитный аналитик, андеррайтер). Имеет доступ к контуру бэк-офиса `/api/v1/employee/**`.

### Защита от IDOR (Insecure Direct Object References):
В клиентском контуре проверка прав владения ресурсом вшита на уровне SQL-запроса:
`WHERE id = :id AND user_id = :currentUserId`.
Попытка клиента запросить или подписать чужую заявку пресекается со статусом `404 Not Found`.

---

## 3. Унифицированная модель ошибок

Все нештатные ситуации и ошибки валидации возвращаются в стандартном теле `ErrorResponse`:

```json
{
  "code": "BAD_REQUEST",
  "message": "Сумма кредита не может быть меньше 100 000 руб.",
  "timestamp": "2026-10-26T15:00:00+03:00"
}
```

| HTTP Статус | Код ошибки (`code`) | Описание ситуации |
| :--- | :--- | :--- |
| **400 Bad Request** | `BAD_REQUEST` | Нарушение бизнес-правил, некорректное состояние заявки. |
| **400 Bad Request** | `VALIDATION_ERROR` | Ошибка валидации схемы входного JSON-запроса (Bean Validation). |
| **401 Unauthorized** | `UNAUTHORIZED` | Токен отсутствует, истек срок действия или невалидна цифровая подпись. |
| **403 Forbidden** | `FORBIDDEN` | Недостаточно прав для выполнения операции (несоответствие роли). |
| **404 Not Found** | `BAD_REQUEST` | Запрашиваемая заявка не найдена или принадлежит другому пользователю. |
| **500 Internal Error** | `INTERNAL_ERROR` | Непредвиденное исключение во время обработки запроса. |

---

## 4. Каталог эндпоинтов

### 4.1. Аутентификация (`auth`)

#### `POST /api/v1/auth/demo-login`
Демо-авторизация под одной из предустановленных тестовых ролей. Возвращает подписанный JWT-токен и профиль пользователя.

* **Тело запроса (`DemoLoginRequest`):**
```json
{
  "username": "client_good"
}
```
*Допустимые значения `username`:* `client_good` (благонадежный клиент), `client_bad` (проблемный клиент), `bank_employee` (сотрудник банка).

* **Успешный ответ (200 OK — `DemoLoginResponse`):**
```json
{
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "username": "client_good",
    "role": "CLIENT",
    "companyName": "ООО \"Ромашка\"",
    "inn": "7701234567"
  }
}
```

---

### 4.2. Клиентский контур (`client-applications`)
*Требуется заголовок `Authorization: Bearer <TOKEN>` с ролью `ROLE_CLIENT`.*

#### `POST /api/v1/client/applications`
Создание новой кредитной заявки предпринимателем. Заявка регистрируется со статусом `PROCESSING`, скоринг запускается асинхронно.

* **Тело запроса (`CreateApplicationRequest`):**
```json
{
  "amount": 2500000,
  "termMonths": 24,
  "purpose": "REPLENISHMENT_WORKING_CAPITAL"
}
```
*Цели кредитования (`purpose`):* `REPLENISHMENT_WORKING_CAPITAL`, `EQUIPMENT_PURCHASE`, `BUSINESS_EXPANSION`.

* **Успешный ответ (201 Created — `ApplicationResponse`):**
```json
{
  "id": 105,
  "userId": 1,
  "companyName": "ООО \"Ромашка\"",
  "inn": "7701234567",
  "amount": 2500000,
  "termMonths": 24,
  "purpose": "REPLENISHMENT_WORKING_CAPITAL",
  "status": "PROCESSING",
  "createdAt": "2026-10-26T15:02:10+03:00",
  "updatedAt": "2026-10-26T15:02:10+03:00"
}
```

#### `GET /api/v1/client/applications`
Получение списка всех кредитных заявок текущего авторизованного пользователя (сортировка от новых к старым).

* **Успешный ответ (200 OK — `List<ApplicationResponse>`):**
Массив объектов `ApplicationResponse`. Если заявка перешла в статус `APPROVED` или `SIGNED`, в теле ответа возвращается аннуитетный график (`schedule`).

#### `GET /api/v1/client/applications/{id}`
Получение детальной информации по конкретной заявке текущего пользователя.

* **Успешный ответ (200 OK — `ApplicationResponse`):**
```json
{
  "id": 105,
  "userId": 1,
  "companyName": "ООО \"Ромашка\"",
  "inn": "7701234567",
  "amount": 2500000,
  "termMonths": 24,
  "purpose": "REPLENISHMENT_WORKING_CAPITAL",
  "status": "APPROVED",
  "approvedAmount": 2500000,
  "approvedRate": 17.5,
  "createdAt": "2026-10-26T15:02:10+03:00",
  "updatedAt": "2026-10-26T15:02:12+03:00",
  "schedule": [
    {
      "monthNumber": 1,
      "paymentDate": "2026-11-26",
      "paymentRub": 124316,
      "principalRub": 87858,
      "interestRub": 36458,
      "remainingDebtRub": 2412142
    }
  ]
}
```

#### `POST /api/v1/client/applications/{id}/sign`
Внутрипродуктовое подтверждение условий кредитования простой электронной подписью (ПЭП). Доступно только для заявок в статусе `APPROVED`.

* **Тело запроса (`SignApplicationRequest`):**
```json
{
  "confirmTerms": true,
  "smsCode": "1234"
}
```
* **Успешный ответ (200 OK — `ApplicationResponse`):**
Возвращает объект заявки в терминальном статусе `SIGNED`.

---

### 4.3. Контур сотрудника банка (`employee-applications`)
*Требуется заголовок `Authorization: Bearer <TOKEN>` с ролью `ROLE_EMPLOYEE`.*

#### `GET /api/v1/employee/applications`
Реестр всех заявок в банке с серверной пагинацией и фильтрацией по статусу.

* **Query-параметры:**
  * `status` *(опционально)*: фильтр по статусу (`PROCESSING`, `APPROVED`, `REJECTED`, `MANUAL_REVIEW`, `SIGNED`).
  * `page` *(по умолчанию: 0)*: номер страницы (0-based index).
  * `size` *(по умолчанию: 10)*: количество элементов на странице.

* **Успешный ответ (200 OK — `ApplicationPageResponse`):**
```json
{
  "items": [ /* массив ApplicationResponse */ ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

#### `GET /api/v1/employee/applications/{id}`
Полное кредитное досье заявки для кредитного офицера / андеррайтера. Содержит сырые снимки внешних баз, расчетные параметры скоринга и признак возможности перезапуска.

* **Успешный ответ (200 OK — `ApplicationDetailsResponse`):**
```json
{
  "id": 105,
  "companyName": "ООО \"Ромашка\"",
  "inn": "7701234567",
  "amount": 2500000,
  "termMonths": 24,
  "purpose": "REPLENISHMENT_WORKING_CAPITAL",
  "status": "MANUAL_REVIEW",
  "reviewReasonCode": "TECH_FAILURE",
  "canRetry": true,
  "user": {
    "id": 1,
    "username": "client_good",
    "role": "CLIENT",
    "companyName": "ООО \"Ромашка\"",
    "inn": "7701234567"
  },
  "fns": {
    "inn": "7701234567",
    "companyName": "ООО \"Ромашка\"",
    "stateStatus": "ACTIVE",
    "taxBlocksExist": false,
    "businessAgeMonths": 38,
    "createdAt": "2026-10-26T15:02:11+03:00"
  },
  "bki": null,
  "scoring": {
    "scoringScore": null,
    "calculatedPdnPercent": null,
    "maxLimitRub": null
  },
  "createdAt": "2026-10-26T15:02:10+03:00",
  "updatedAt": "2026-10-26T15:02:12+03:00"
}
```

#### `POST /api/v1/employee/applications/{id}/retry`
Ручной перезапуск конвейера скоринга. Разрешен только для заявок в статусе `MANUAL_REVIEW` с причиной `TECH_FAILURE` (когда `canRetry == true`).

* **Параметры пути:**
  * `id` *(обязательный, int64)*: уникальный идентификатор заявки.

* **Успешный ответ (200 OK — `ApplicationResponse`):**
Заявка переводится в статус `PROCESSING`, запускается повторное обогащение и выносится итоговое решение.

* **Пример вызова (cURL):**
```bash
curl -X POST "http://localhost:8080/api/v1/employee/applications/105/retry" \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN"
```

#### `POST /api/v1/employee/applications/{id}/approve`
Ручное одобрение кредитной заявки андеррайтером. Переводит заявку из статуса `MANUAL_REVIEW` в статус `APPROVED`. Андеррайтер может подтвердить условия скоринга или скорректировать лимит (`approvedAmount`) и процентную ставку (`approvedRate`). Обязательным является заполнение мотивированного комментария (`comment`).

* **Параметры пути:**
  * `id` *(обязательный, int64)*: уникальный идентификатор заявки.

* **Тело запроса (`ManualApproveRequest`):**
```json
{
  "approvedAmount": 1500000,
  "approvedRate": 18.5,
  "comment": "Показатели стабильны, предоставлены расширенные выписки РКО"
}
```
*Поля запроса:*
* `approvedAmount` *(опционально, int64)*: скорректированная сумма кредита в рублях (если не указана, сохраняется расчетный лимит конвейера).
* `approvedRate` *(опционально, double)*: скорректированная годовая ставка в процентах (если не указана, сохраняется ставка конвейера).
* `comment` *(обязательно, string)*: развернутое обоснование решения андеррайтера.

* **Успешный ответ (200 OK — `ApplicationResponse`):**
```json
{
  "id": 105,
  "userId": 1,
  "companyName": "ООО \"Ромашка\"",
  "inn": "7701234567",
  "amount": 2500000,
  "termMonths": 24,
  "purpose": "REPLENISHMENT_WORKING_CAPITAL",
  "status": "APPROVED",
  "approvedAmount": 1500000,
  "approvedRate": 18.5,
  "createdAt": "2026-10-26T15:02:10+03:00",
  "updatedAt": "2026-10-26T15:10:00+03:00",
  "schedule": [
    {
      "monthNumber": 1,
      "paymentDate": "2026-11-26",
      "paymentRub": 75324,
      "principalRub": 52199,
      "interestRub": 23125,
      "remainingDebtRub": 1447801
    }
  ]
}
```

* **Ошибки:**
  * `400 Bad Request` — статус заявки отличен от `MANUAL_REVIEW`, пустой комментарий или некорректные параметры (`BAD_REQUEST` / `VALIDATION_ERROR`).
  * `401 Unauthorized` — отсутствует или невалиден JWT-токен (`UNAUTHORIZED`).
  * `403 Forbidden` — у пользователя отсутствует роль `ROLE_EMPLOYEE` (`FORBIDDEN`).
  * `404 Not Found` — заявка с указанным идентификатором не найдена (`BAD_REQUEST`).

* **Пример вызова (cURL):**
```bash
curl -X POST "http://localhost:8080/api/v1/employee/applications/105/approve" \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "approvedAmount": 1500000,
    "approvedRate": 18.5,
    "comment": "Показатели стабильны, предоставлены расширенные выписки РКО"
  }'
```

#### `POST /api/v1/employee/applications/{id}/reject`
Ручной отказ по кредитной заявке андеррайтером. Переводит заявку из статуса `MANUAL_REVIEW` в терминальный статус `REJECTED` с фиксацией мотивированной причины отказа в досье.

* **Параметры пути:**
  * `id` *(обязательный, int64)*: уникальный идентификатор заявки.

* **Тело запроса (`ManualRejectRequest`):**
```json
{
  "comment": "Высокая кредитная нагрузка группы связанных компаний"
}
```
*Поля запроса:*
* `comment` *(обязательно, string)*: мотивированная причина ручного отклонения заявки.

* **Успешный ответ (200 OK — `ApplicationResponse`):**
```json
{
  "id": 105,
  "userId": 1,
  "companyName": "ООО \"Ромашка\"",
  "inn": "7701234567",
  "amount": 2500000,
  "termMonths": 24,
  "purpose": "REPLENISHMENT_WORKING_CAPITAL",
  "status": "REJECTED",
  "rejectionReason": "Высокая кредитная нагрузка группы связанных компаний",
  "approvedAmount": null,
  "approvedRate": null,
  "createdAt": "2026-10-26T15:02:10+03:00",
  "updatedAt": "2026-10-26T15:10:00+03:00"
}
```

* **Ошибки:**
  * `400 Bad Request` — статус заявки отличен от `MANUAL_REVIEW`, пустой комментарий (`BAD_REQUEST` / `VALIDATION_ERROR`).
  * `401 Unauthorized` — отсутствует или невалиден JWT-токен (`UNAUTHORIZED`).
  * `403 Forbidden` — у пользователя отсутствует роль `ROLE_EMPLOYEE` (`FORBIDDEN`).
  * `404 Not Found` — заявка с указанным идентификатором не найдена (`BAD_REQUEST`).

* **Пример вызова (cURL):**
```bash
curl -X POST "http://localhost:8080/api/v1/employee/applications/105/reject" \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "comment": "Высокая кредитная нагрузка группы связанных компаний"
  }'
```

---

### 4.4. Интеграционные мок-сервисы (`stubs`)

Внутренние эмуляторы государственных и банковских реестров (открытый доступ без авторизации):
* **`POST /api/v1/stubs/fns/check`** — эмулятор ФНС (возвращает статус регистрации, наличие налоговых арестов по ст. 76 НК РФ и дату создания).
* **`POST /api/v1/stubs/bki/report`** — эмулятор БКИ (возвращает скоринговый балл бюро, текущие просрочки и наличие критических дефолтов 90+ DPD).

---

## 5. Справочники и перечисления (Enums)

### `ApplicationStatus`
* `PROCESSING` — заявка принята, выполняется параллельное обогащение и расчет скоринга.
* `APPROVED` — заявка одобрена конвейером, сформированы условия и график платежей.
* `REJECTED` — отказ в кредитовании (сработали стоп-факторы или превышен лимит риска).
* `MANUAL_REVIEW` — требуется вмешательство андеррайтера (технический сбой или пограничный балл).
* `SIGNED` — кредитная сделка заключена заемщиком через электронную подпись (терминальный статус).

### `ReviewReasonCode`
* `TECH_FAILURE` — временная недоступность внешних сервисов (ФНС / БКИ). Разрешен `canRetry`.
* `BORDERLINE_SCORE` — пограничный скоринговый балл (50–64). Требуется мотивированное суждение риск-офицера.

### `ApplicationPurpose`
* `REPLENISHMENT_WORKING_CAPITAL` — пополнение оборотных средств.
* `EQUIPMENT_PURCHASE` — покупка оборудования или транспорта.
* `BUSINESS_EXPANSION` — расширение бизнеса и открытие новых точек.
