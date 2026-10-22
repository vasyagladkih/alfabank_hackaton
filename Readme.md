# Умный кредитный кабинет ММБ

Кредитный кабинет клиента и рабочее место сотрудника банка для хакатона Альфа-Банка.

- [`frontend/`](frontend/README.md) — четыре связанных экрана issue [#11](https://github.com/vasyagladkih/alfabank_hackaton/issues/11): демо-вход, кабинет ММБ, бэк-офис и 403. React/TypeScript, официальные Alfa Core Components, реальные HTTP-запросы по контракту.
- [`docs/openapi.yaml`](docs/openapi.yaml) — общий контракт API и источник сгенерированных TypeScript-типов.
- [`backend/alfahackaton/`](backend/alfahackaton/) — Java/Spring Boot проект. На базовом коммите `191108c233ec9981e144f06a2e6b79801235900b` контроллеры содержат незавершённые `{...}`; запускаемый API ещё необходим для проверки полного сценария с сервером.

## Запуск frontend

Требуется Node.js 24.15+ в пределах major 24 (`frontend/.nvmrc` выбирает major 24).

```sh
cd frontend
npm ci
cp .env.example .env.local
npm run dev
```

По умолчанию `/api` проксируется на `http://localhost:8080`. Настройка другого сервера, сборка и тесты описаны в [frontend/README.md](frontend/README.md).

В production-коде нет тестового сервера или подставных решений по заявкам. Без backend интерфейс показывает ошибку запроса. Транспортные фикстуры находятся только в тестах.

См. [отчёт проверок и матрицу issue #11](frontend/VERIFICATION.md), [лицензии сторонних компонентов](THIRD_PARTY_NOTICES.md).
