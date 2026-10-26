# Перечень сторонних компонентов, смоделированных интеграций и лицензий (THIRD_PARTY_NOTICES)

Настоящий документ составлен во исполнение требований регламента хакатона «Умный кредитный кабинет ММБ» (стр. 7) и содержит сведения об используемых сторонних программных компонентах, библиотеках, смоделированных межведомственных связях, статусе машинного обучения и синтетических наборах данных.

---

## 1. Смоделированные интеграции и эмуляторы банковского контура

В соответствии с методическими указаниями вебинара (вопросы № 5, 10, 11) в проекте отсутствуют прямые подключения к боевым закрытым сетям государственных ведомств и банка. Все внешние взаимодействия смоделированы автономно в виде независимых эмуляторов:

| Интеграционный шлюз | Пакет / Расположение в коде | Назначение и моделируемые протоколы | Обоснование и нормативная база |
| :--- | :--- | :--- | :--- |
| **Шлюз ФНС (ЕГРЮЛ, ст. 76 НК РФ)** | `ru.alfahackaton.stub.fns` (`MockFnsController`, таблица `mock_fns_registry`) | Возвращает статус юридического лица (ACTIVE, LIQUIDATING, BANKRUPT), дату регистрации бизнеса и признак наличия приостановлений операций по счетам. | **Ст. 76 НК РФ, ст. 61 ГК РФ, 129-ФЗ.** Блокирует выдачу кредита банкротам и компаниям с налоговыми арестами счетов. |
| **Шлюз БКИ (218-ФЗ)** | `ru.alfahackaton.stub.bki` (`MockBkiController`, таблица `mock_bki_registry`) | Возвращает скоринговый балл бюро (300–850), размер совокупного среднемесячного платежа по кредитам в сторонних банках, длительность текущей просрочки и число дефолтов свыше 90 дней. | **Федеральный закон № 218-ФЗ «О кредитных историях», Положение ЦБ РФ № 590-П.** База для оценки кредитной нагрузки и классификации ссуды по 5 категориям качества. |
| **Идентификация и авторизация (IdP / SSO)** | `ru.alfahackaton.service.AuthService` | В реальной инфраструктуре предполагается наличие корпоративного Identity Provider Альфа-Банка. Для автономной работы и тестирования в конвейере смоделирован выпуск JWT-токенов с асимметричной цифровой подписью RS256. | **OAuth 2.0 / OIDC (RFC 6749, RFC 7519).** Демонстрационный вход заемщика и сотрудника с выдачей подписанных билетов доступа. |
| **Простая электронная подпись (ПЭП)** | `ru.alfahackaton.api.handler.UserController` (`signApplication`) | Моделирование подписания полного пакета кредитной документации и графика платежей кодом подтверждения в рамках единой веб-сессии без привлечения платных внешних SMS-шлюзов. | **Федеральный закон № 63-ФЗ «Об электронной подписи», п. 10 ТЗ хакатона.** Обеспечивает юридическую фиксацию согласия заемщика с индивидуальными условиями договора. |

---

## 2. Синтетические наборы данных (Synthetic Datasets)

1. **Отсутствие реальных персональных данных (ПДн):**
   * Все данные, используемые в конвейере (наименования организаций, ИНН, ОГРН, обороты, выписки и кредитные истории), являются на 100% синтетическими и сгенерированы исключительно в целях демонстрации и тестирования.
   * Проект полностью удовлетворяет требованиям **Федерального закона № 152-ФЗ «О персональных данных»** и не содержит охраняемой законом банковской или коммерческой тайны.
2. **Синтетические профили компаний:**
   * Профили заемщиков в таблицах users, mock_fns_registry и mock_bki_registry покрывают все ветки матрицы решений конвейера: идеальных заемщиков («Зеленый коридор»), клиентов с повышенной нагрузкой (сценарий «Контр-оффер»), банкротов и дефолтеров (сценарий «Отказ»), а также кейсы сетевых сбоев для проверки самовосстановления.

---

## 3. Статус компонентов машинного обучения (AI / ML Status)

1. **Полная автономия детерминированного ядра:**
   * В кредитном конвейере, скоринговом движке и логике андеррайтинга **НЕ используются** сторонние облачные нейросети, сторонние LLM-ассистенты или модели машинного обучения с непрозрачными весами («черные ящики»).
   * Вся логика оценки заемщика, расчет показателей долговой нагрузки (ПДН) и кредитных лимитов реализована строго детерминированно на языке Java на базе формул и обязательных регламентов Банка России (Положение № 590-П, Указание № 6377-У).
2. **Объяснимость и соответствие банковскому комплаенсу:**
   * Каждое решение конвейера (одобрение, контр-оффер, передача андеррайтеру или отказ) на 100% интерпретируемо, воспроизводимо и сопровождается исчерпывающим мотивированным обоснованием для регулятора и клиента.

---

## 4. Реестр сторонних компонентов и библиотек бэкенда (Java / Spring Boot)

Серверная часть кредитного конвейера разработана на Java 25 и Spring Boot 3.3.4. В проекте используются исключительно открытые библиотеки под permissive-лицензиями:

| Библиотека / Компонент | Версия | Лицензия | Назначение в архитектуре |
| :--- | :--- | :--- | :--- |
| **Eclipse Temurin OpenJDK / Java** | 25 | GPL-2.0 with Classpath Exception | Базовая среда исполнения платформы, поддержка Virtual Threads. |
| **Spring Boot (Web, Security, Data JDBC, Validation)** | 4.1.1 | Apache-2.0 | Каркас приложения, REST API, безопасность, работа с PostgreSQL. |
| **Nimbus JOSE + JWT** | 9.37.3 | Apache-2.0 | Валидация криптографических токенов JWT по стандарту RS256. |
| **PostgreSQL JDBC Driver** | 42.7.4 | BSD-2-Clause | Драйвер сетевого подключения к базе данных PostgreSQL 16. |
| **Flyway Core** | 10.18.0 | Apache-2.0 | Управление версионированием и миграциями схемы базы данных. |
| **Testcontainers (PostgreSQL, JUnit Jupiter)** | 1.20.1 | MIT / EPL-2.0 | Интеграционное тестирование с изолированной БД в Docker-контейнере. |
| **Project Lombok** | 1.18.34 | MIT | Генерация служебного кода (геттеры, билдеры, логирование). |
| **MapStruct** | 1.5.5.Final | Apache-2.0 | Высокопроизводительный compile-time маппинг DTO и сущностей. |
| **OpenAPI Tools Generator Gradle Plugin** | 7.8.0 | Apache-2.0 | Генерация интерфейсов контроллеров по контракту docs/openapi.yaml. |

---

## 5. Реестр сторонних компонентов и лицензий фронтенда (React / Alfa Core Components)

Ниже приведен аудит зависимостей фронтенд-приложения, сформированный на основе дерева пакетов rontend/package-lock.json.

Scope: 90 non-root lockfile entries with neither dev nor devOptional set to true.
Declared package metadata: 0BSD: 1, Apache-2.0: 3, BSD-3-Clause: 1, MIT: 85.

### Recorded source exceptions

- @alfalab/icons-glyph@2.293.3: MIT.
- intersection-observer@0.12.2: Apache-2.0 / W3C Software and Document Notice and License.
- 
eact-remove-scroll-bar@2.3.8: MIT.
- The 32 @alfalab/core-components-* package archives declare MIT.
- @reach/observe-rect@1.2.0: MIT.


## Package inventory

| Package | Version | Declared license | License / notice text |
| --- | --- | --- | --- |
| `@alfalab/core-components-backdrop` | `5.0.2` | MIT | L001 |
| `@alfalab/core-components-base-modal` | `7.2.2` | MIT | L001 |
| `@alfalab/core-components-bottom-sheet` | `8.1.16` | MIT | L001 |
| `@alfalab/core-components-button` | `13.2.4` | MIT | L001 |
| `@alfalab/core-components-checkbox` | `6.3.2` | MIT | L001 |
| `@alfalab/core-components-config` | `1.1.0` | MIT | L001 |
| `@alfalab/core-components-form-control` | `14.0.11` | MIT | L001 |
| `@alfalab/core-components-global-store` | `4.0.1` | MIT | L001 |
| `@alfalab/core-components-icon-button` | `8.0.12` | MIT | L001 |
| `@alfalab/core-components-indicator` | `4.0.2` | MIT | L001 |
| `@alfalab/core-components-input` | `17.2.2` | MIT | L001 |
| `@alfalab/core-components-modal` | `11.0.19` | MIT | L001 |
| `@alfalab/core-components-mq` | `6.0.9` | MIT | L001 |
| `@alfalab/core-components-navigation-bar-private` | `2.0.16` | MIT | L001 |
| `@alfalab/core-components-pagination` | `4.0.12` | MIT | L001 |
| `@alfalab/core-components-plate` | `9.0.12` | MIT | L001 |
| `@alfalab/core-components-popover` | `8.1.4` | MIT | L001 |
| `@alfalab/core-components-portal` | `5.0.6` | MIT | L001 |
| `@alfalab/core-components-scrollbar-private` | `0.0.1` | MIT | L001 |
| `@alfalab/core-components-select` | `19.2.11` | MIT | L001 |
| `@alfalab/core-components-shared` | `2.3.0` | MIT | L001 |
| `@alfalab/core-components-skeleton` | `7.0.5` | MIT | L001 |
| `@alfalab/core-components-spinner` | `6.0.9` | MIT | L001 |
| `@alfalab/core-components-stack` | `7.0.1` | MIT | L001 |
| `@alfalab/core-components-stack-context` | `1.0.1` | MIT | L001 |
| `@alfalab/core-components-status` | `5.1.1` | MIT | L001 |
| `@alfalab/core-components-status-badge` | `3.0.2` | MIT | L001 |
| `@alfalab/core-components-table` | `4.0.30` | MIT | L001 |
| `@alfalab/core-components-tag` | `10.3.2` | MIT | L001 |
| `@alfalab/core-components-themes` | `15.2.1` | MIT | L001 |
| `@alfalab/core-components-types` | `2.1.0` | MIT | L001 |
| `@alfalab/core-components-typography` | `6.0.13` | MIT | L001 |
| `@alfalab/data` | `1.10.2` | MIT | L002 |
| `@alfalab/hooks` | `1.18.1` | MIT | L002 |
| `@alfalab/icons-glyph` | `2.293.3` | MIT | L003 |
| `@alfalab/utils` | `1.22.0` | MIT | L002 |
| `@babel/runtime` | `7.29.10` | MIT | L004 |
| `@juggle/resize-observer` | `3.4.0` | Apache-2.0 | L005 |
| `@maskito/core` | `1.9.0` | Apache-2.0 | L006 |
| `@popperjs/core` | `2.11.8` | MIT | L007 |
| `@reach/observe-rect` | `1.2.0` | MIT | L008, L003 |
| `@remix-run/router` | `1.23.4` | MIT | L009 |
| `@types/prop-types` | `15.7.15` | MIT | L010 |
| `@types/react` | `17.0.90` | MIT | L010 |
| `@types/react-transition-group` | `4.4.12` | MIT | L010 |
| `@types/scheduler` | `0.16.8` | MIT | L010 |
| `classnames` | `2.5.1` | MIT | L011 |
| `compute-scroll-into-view` | `3.1.1` | MIT | L012 |
| `csstype` | `3.2.3` | MIT | L013 |
| `detect-browser` | `5.3.0` | MIT | L014 |
| `detect-node-es` | `1.1.0` | MIT | L015 |
| `dom-helpers` | `5.2.1` | MIT | L016 |
| `downshift` | `8.4.0` | MIT | L017 |
| `focus-lock` | `1.3.6` | MIT | L018 |
| `get-nonce` | `1.0.1` | MIT | L019 |
| `intersection-observer` | `0.12.2` | Apache-2.0 | L020, L021, L022 |
| `js-tokens` | `4.0.0` | MIT | L023 |
| `lodash` | `4.18.1` | MIT | L024 |
| `lodash-es` | `4.18.1` | MIT | L024 |
| `loose-envify` | `1.4.0` | MIT | L025 |
| `object-assign` | `4.1.1` | MIT | L026 |
| `popper-max-size-modifier` | `0.2.0` | MIT | L027 |
| `prop-types` | `15.8.1` | MIT | L028 |
| `react` | `17.0.2` | MIT | L029 |
| `react-clientside-effect` | `1.2.8` | MIT | L030 |
| `react-div-100vh` | `0.7.0` | MIT | L031 |
| `react-dom` | `17.0.2` | MIT | L029 |
| `react-fast-compare` | `3.2.2` | MIT | L032 |
| `react-focus-lock` | `2.13.7` | MIT | L033 |
| `react-is` | `16.13.1` | MIT | L029 |
| `react-is` | `18.3.1` | MIT | L029 |
| `react-merge-refs` | `1.1.0` | MIT | L034 |
| `react-popper` | `2.3.0` | MIT | L035 |
| `react-remove-scroll` | `2.7.2` | MIT | L018 |
| `react-remove-scroll-bar` | `2.3.8` | MIT | L036 |
| `react-router` | `6.30.6` | MIT | L009 |
| `react-router-dom` | `6.30.6` | MIT | L009 |
| `react-style-singleton` | `2.2.3` | MIT | L018 |
| `react-swipeable` | `7.0.2` | MIT | L037 |
| `react-transition-group` | `4.4.5` | BSD-3-Clause | L038 |
| `react-virtual` | `2.10.4` | MIT | L039 |
| `scheduler` | `0.20.2` | MIT | L029 |
| `simplebar-core` | `1.3.2` | MIT | L040 |
| `simplebar-react` | `3.3.2` | MIT | L040 |
| `tslib` | `2.8.1` | 0BSD | L041, L042 |
| `use-callback-ref` | `1.3.3` | MIT | L018 |
| `use-sidecar` | `1.1.3` | MIT | L018 |
| `use-sync-external-store` | `1.7.0` | MIT | L043 |
| `uuid` | `14.0.3` | MIT | L044 |
| `warning` | `4.0.3` | MIT | L028 |

## License and notice texts

### L001

Applies to:

- `@alfalab/core-components-backdrop@5.0.2`
- `@alfalab/core-components-base-modal@7.2.2`
- `@alfalab/core-components-bottom-sheet@8.1.16`
- `@alfalab/core-components-button@13.2.4`
- `@alfalab/core-components-checkbox@6.3.2`
- `@alfalab/core-components-config@1.1.0`
- `@alfalab/core-components-form-control@14.0.11`
- `@alfalab/core-components-global-store@4.0.1`
- `@alfalab/core-components-icon-button@8.0.12`
- `@alfalab/core-components-indicator@4.0.2`
- `@alfalab/core-components-input@17.2.2`
- `@alfalab/core-components-modal@11.0.19`
- `@alfalab/core-components-mq@6.0.9`
- `@alfalab/core-components-navigation-bar-private@2.0.16`
- `@alfalab/core-components-pagination@4.0.12`
- `@alfalab/core-components-plate@9.0.12`
- `@alfalab/core-components-popover@8.1.4`
- `@alfalab/core-components-portal@5.0.6`
- `@alfalab/core-components-scrollbar-private@0.0.1`
- `@alfalab/core-components-select@19.2.11`
- `@alfalab/core-components-shared@2.3.0`
- `@alfalab/core-components-skeleton@7.0.5`
- `@alfalab/core-components-spinner@6.0.9`
- `@alfalab/core-components-stack@7.0.1`
- `@alfalab/core-components-stack-context@1.0.1`
- `@alfalab/core-components-status@5.1.1`
- `@alfalab/core-components-status-badge@3.0.2`
- `@alfalab/core-components-table@4.0.30`
- `@alfalab/core-components-tag@10.3.2`
- `@alfalab/core-components-themes@15.2.1`
- `@alfalab/core-components-types@2.1.0`
- `@alfalab/core-components-typography@6.0.13`

Sources: S001.

````text
MIT License

Copyright (c) 2022-2024 core-ds

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L002

Applies to:

- `@alfalab/data@1.10.2`
- `@alfalab/hooks@1.18.1`
- `@alfalab/utils@1.22.0`

Sources: S002, S003, S006.

````text
MIT License

Copyright (c) 2022-2024 core-ds

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
````

### L003

Applies to:

- `@alfalab/icons-glyph@2.293.3`
- `@reach/observe-rect@1.2.0`

Sources: S005, S012.

````text
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
USE OR OTHER DEALINGS IN THE SOFTWARE.
````

### L004

Applies to:

- `@babel/runtime@7.29.10`

Sources: S007.

````text
MIT License

Copyright (c) 2014-present Sebastian McKenzie and other contributors

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
````

### L005

Applies to:

- `@juggle/resize-observer@3.4.0`

Sources: S008.

````text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright 2019 JUGGLE LTD

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
````

### L006

Applies to:

- `@maskito/core@1.9.0`

Sources: S009.

````text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   Copyright 2019 Tinkoff Bank

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
````

### L007

Applies to:

- `@popperjs/core@2.11.8`

Sources: S010.

````text
The MIT License (MIT)

Copyright (c) 2019 Federico Zivolo

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
````

### L008

Applies to:

- `@reach/observe-rect@1.2.0`

Sources: S011.

````text
MIT License
Copyright (c) 2018-present, Ryan Florence
````

### L009

Applies to:

- `@remix-run/router@1.23.4`
- `react-router@6.30.6`
- `react-router-dom@6.30.6`

Sources: S013, S049, S050.

````text
MIT License

Copyright (c) React Training LLC 2015-2019
Copyright (c) Remix Software Inc. 2020-2021
Copyright (c) Shopify Inc. 2022-2023

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L010

Applies to:

- `@types/prop-types@15.7.15`
- `@types/react@17.0.90`
- `@types/react-transition-group@4.4.12`
- `@types/scheduler@0.16.8`

Sources: S014, S015, S016, S017.

````text
    MIT License

    Copyright (c) Microsoft Corporation.

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE
````

### L011

Applies to:

- `classnames@2.5.1`

Sources: S018.

````text
The MIT License (MIT)

Copyright (c) 2018 Jed Watson

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L012

Applies to:

- `compute-scroll-into-view@3.1.1`

Sources: S019.

````text
MIT License

Copyright (c) 2025 Cody Olsen

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L013

Applies to:

- `csstype@3.2.3`

Sources: S020.

````text
Copyright (c) 2017-2018 Fredrik Nicol

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L014

Applies to:

- `detect-browser@5.3.0`

Sources: S021.

````text
The MIT License (MIT)

Copyright (c) 2019 Damon Oehlman <damon.oehlman@gmail.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L015

Applies to:

- `detect-node-es@1.1.0`

Sources: S022.

````text
MIT License

Copyright (c) 2017 Ilya Kantor

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L016

Applies to:

- `dom-helpers@5.2.1`

Sources: S023.

````text
The MIT License (MIT)

Copyright (c) 2015 Jason Quense

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L017

Applies to:

- `downshift@8.4.0`

Sources: S024.

````text
The MIT License (MIT)
Copyright (c) 2017 PayPal

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L018

Applies to:

- `focus-lock@1.3.6`
- `react-remove-scroll@2.7.2`
- `react-style-singleton@2.2.3`
- `use-callback-ref@1.3.3`
- `use-sidecar@1.1.3`

Sources: S025, S047, S051, S060, S061.

````text
MIT License

Copyright (c) 2017 Anton Korzunov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L019

Applies to:

- `get-nonce@1.0.1`

Sources: S026.

````text
MIT License

Copyright (c) 2020 Anton Korzunov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L020

Applies to:

- `intersection-observer@0.12.2`

Sources: S027.

````text
/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the W3C SOFTWARE AND DOCUMENT NOTICE AND LICENSE.
 *
 *  https://www.w3.org/Consortium/Legal/2015/copyright-software-and-document
 *
 */
````

### L021

Applies to:

- `intersection-observer@0.12.2`

Sources: S028.

````text
This work is being provided by the copyright holders under the following license.

License

By obtaining and/or copying this work, you (the licensee) agree that you have read, understood, and will comply with the following terms and conditions.

Permission to copy, modify, and distribute this work, with or without modification, for any purpose and without fee or royalty is hereby granted, provided that you include the following on ALL copies of the work or portions thereof, including modifications:
- The full text of this NOTICE in a location viewable to users of the redistributed or derivative work.
- Any pre-existing intellectual property disclaimers, notices, or terms and conditions. If none exist, the W3C Software and Document Short Notice should be included.
- Notice of any changes or modifications, through a copyright statement on the new code or document such as "This software or document includes material copied from or derived from [title and URI of the W3C document]. Copyright © [YEAR] W3C® (MIT, ERCIM, Keio, Beihang)."

Disclaimers

THIS WORK IS PROVIDED "AS IS," AND COPYRIGHT HOLDERS MAKE NO REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO, WARRANTIES OF MERCHANTABILITY OR FITNESS FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE SOFTWARE OR DOCUMENT WILL NOT INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS OR OTHER RIGHTS.

COPYRIGHT HOLDERS WILL NOT BE LIABLE FOR ANY DIRECT, INDIRECT, SPECIAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF ANY USE OF THE SOFTWARE OR DOCUMENT.

The name and trademarks of copyright holders may NOT be used in advertising or publicity pertaining to the work without specific, written prior permission. Title to copyright in this work will at all times remain with copyright holders.
````

### L022

Applies to:

- `intersection-observer@0.12.2`

Sources: S029.

````text

                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
````

### L023

Applies to:

- `js-tokens@4.0.0`

Sources: S030.

````text
The MIT License (MIT)

Copyright (c) 2014, 2015, 2016, 2017, 2018 Simon Lydell

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
````

### L024

Applies to:

- `lodash@4.18.1`
- `lodash-es@4.18.1`

Sources: S031, S032.

````text
Copyright OpenJS Foundation and other contributors <https://openjsf.org/>

Based on Underscore.js, copyright Jeremy Ashkenas,
DocumentCloud and Investigative Reporters & Editors <http://underscorejs.org/>

This software consists of voluntary contributions made by many
individuals. For exact contribution history, see the revision history
available at https://github.com/lodash/lodash

The following license applies to all parts of this software except as
documented below:

====

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

====

Copyright and related rights for sample code are waived via CC0. Sample
code is defined as all source code displayed within the prose of the
documentation.

CC0: http://creativecommons.org/publicdomain/zero/1.0/

====

Files located in the node_modules and vendor directories are externally
maintained libraries used by this software which have their own
licenses; we recommend you read them, as their terms may differ from the
terms above.
````

### L025

Applies to:

- `loose-envify@1.4.0`

Sources: S033.

````text
The MIT License (MIT)

Copyright (c) 2015 Andres Suarez <zertosh@gmail.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
````

### L026

Applies to:

- `object-assign@4.1.1`

Sources: S034.

````text
The MIT License (MIT)

Copyright (c) Sindre Sorhus <sindresorhus@gmail.com> (sindresorhus.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
````

### L027

Applies to:

- `popper-max-size-modifier@0.2.0`

Sources: S035.

````text
MIT License

Copyright (c) 2020 atomiks

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L028

Applies to:

- `prop-types@15.8.1`
- `warning@4.0.3`

Sources: S036, S064.

````text
MIT License

Copyright (c) 2013-present, Facebook, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L029

Applies to:

- `react@17.0.2`
- `react-dom@17.0.2`
- `react-is@16.13.1`
- `react-is@18.3.1`
- `scheduler@0.20.2`

Sources: S037, S040, S043, S044, S055.

````text
MIT License

Copyright (c) Facebook, Inc. and its affiliates.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L030

Applies to:

- `react-clientside-effect@1.2.8`

Sources: S038.

````text
The MIT License (MIT)

Copyright (c) 2015 Dan Abramov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L031

Applies to:

- `react-div-100vh@0.7.0`

Sources: S039.

````text
MIT License

Copyright (c) 2018-2020 Mikhail Vasin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L032

Applies to:

- `react-fast-compare@3.2.2`

Sources: S041.

````text
MIT License

Copyright (c) 2018 Formidable Labs
Copyright (c) 2017 Evgeny Poberezkin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L033

Applies to:

- `react-focus-lock@2.13.7`

Sources: S042.

````text
MIT License

Copyright (c) 2017 Anton

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L034

Applies to:

- `react-merge-refs@1.1.0`

Sources: S045.

````text
MIT License

Copyright (c) 2020 Greg Bergé

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L035

Applies to:

- `react-popper@2.3.0`

Sources: S046.

````text
The MIT License (MIT)

Copyright (c) 2018 React Popper authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L036

Applies to:

- `react-remove-scroll-bar@2.3.8`

Sources: S048.

````text
MIT License

Copyright (c) 2025 Anton Korzunov <thekashey@gmail.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L037

Applies to:

- `react-swipeable@7.0.2`

Sources: S052.

````text
The MIT License (MIT)

Copyright (C) 2014-2022 Josh Perez
Copyright (C) 2014-2022 Brian Emil Hartz
Copyright (C) 2022 Formidable Labs, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
````

### L038

Applies to:

- `react-transition-group@4.4.5`

Sources: S053.

````text
BSD 3-Clause License

Copyright (c) 2018, React Community
Forked from React (https://github.com/facebook/react) Copyright 2013-present, Facebook, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
````

### L039

Applies to:

- `react-virtual@2.10.4`

Sources: S054.

````text
MIT License

Copyright (c) 2019 Tanner Linsley

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L040

Applies to:

- `simplebar-core@1.3.2`
- `simplebar-react@3.3.2`

Sources: S056, S057.

````text
The MIT License (MIT)

Copyright (c) 2015 Jonathan Nicol

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L041

Applies to:

- `tslib@2.8.1`

Sources: S058.

````text
Copyright (c) Microsoft Corporation.

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
````

### L042

Applies to:

- `tslib@2.8.1`

Sources: S059.

````text
/******************************************************************************
Copyright (c) Microsoft Corporation.

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
***************************************************************************** */
````

### L043

Applies to:

- `use-sync-external-store@1.7.0`

Sources: S062.

````text
MIT License

Copyright (c) Meta Platforms, Inc. and affiliates.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
````

### L044

Applies to:

- `uuid@14.0.3`

Sources: S063.

````text
The MIT License (MIT)

Copyright (c) 2010-2020 Robert Kieffer and other contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
````

## Source inventory

### S001

- Evidence type: `official-monorepo-license-fallback`.
- Source: https://raw.githubusercontent.com/core-ds/core-components/1fa28dd0cb78b3975394e7ad14a602eb7b162ad2/LICENSE.md
- SHA-256 of source file/excerpt: `2dbc8c8fa27b9106a0ec907b98224eaff93085b8b6c9d9f68e7588a9cf49b834`.
- Reproduced text: L001.
- Provenance: The published component package declares MIT but omits a license file. This is the official Core Components monorepo root license pinned at audit time.

### S002

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@alfalab/data/-/data-1.10.2.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `acf0357beb0d41aa581ac873a74ac8f543d2155f193a2df7d58447507870ec3e`.
- Reproduced text: L002.
- Provenance: Copied from installed node_modules/@alfalab/data/LICENSE.md.

### S003

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@alfalab/hooks/-/hooks-1.18.1.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `acf0357beb0d41aa581ac873a74ac8f543d2155f193a2df7d58447507870ec3e`.
- Reproduced text: L002.
- Provenance: Copied from installed node_modules/@alfalab/hooks/LICENSE.md.

### S004

- Evidence type: `official-package-license-declaration`.
- Source: https://raw.githubusercontent.com/core-ds/ui-primitives/440b15a6a0481e440ce739150d15968a42cf502e/packages/glyph/package.json
- SHA-256 of source file/excerpt: `2dcac3e9671d9787537e74c6240281f0adbf1c6bebe28a7a9c2da5e2d62763cc`.
- Provenance: Manifest version 2.293.3 exactly matches the installed package; license field is MIT.

### S005

- Evidence type: `canonical-license-terms-extract`.
- Source: https://raw.githubusercontent.com/spdx/license-list-data/main/json/details/MIT.json
- SHA-256 of source file/excerpt: `83330a27667f812efa9c7cdaefd4c83f95b57a7454323a61ee29291312640468`.
- Reproduced text: L003.
- Provenance: Standard MIT permission/warranty paragraphs from SPDX. The template copyright-holder line is not reproduced as if it were an actual upstream attribution. No upstream holder attribution was available.

### S006

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@alfalab/utils/-/utils-1.22.0.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `acf0357beb0d41aa581ac873a74ac8f543d2155f193a2df7d58447507870ec3e`.
- Reproduced text: L002.
- Provenance: Copied from installed node_modules/@alfalab/utils/LICENSE.md.

### S007

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@babel/runtime/-/runtime-7.29.10.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `117da2af0d4ce0fe1c8e19b5cff9dcd806adf973d328d27b11d4448c4ff24f76`.
- Reproduced text: L004.
- Provenance: Copied from installed node_modules/@babel/runtime/LICENSE.

### S008

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@juggle/resize-observer/-/resize-observer-3.4.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `293c905e2ec4f6c30c4684592898fd24215ac735f0601b07b158d1588c8a5af7`.
- Reproduced text: L005.
- Provenance: Copied from installed node_modules/@juggle/resize-observer/LICENSE.

### S009

- Evidence type: `upstream-release-license`.
- Source: https://raw.githubusercontent.com/taiga-family/maskito/33ff40937fb6afcc7569273cdb8439d66ed56e70/LICENSE
- SHA-256 of source file/excerpt: `5015e75afef4dfd244f8bfb6098cba84296f2d278b93f229a5ae8d2e20173907`.
- Reproduced text: L006.
- Provenance: Release commit from npm gitHead; upstream LICENSE includes Copyright 2019 Tinkoff Bank.

### S010

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@popperjs/core/-/core-2.11.8.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `bf67e2c9b7974543bb18ced3a0b37f53729aaff953b442dd89a3adaf90fd93e8`.
- Reproduced text: L007.
- Provenance: Copied from installed node_modules/@popperjs/core/LICENSE.md.

### S011

- Evidence type: `installed-readme-legal-extract`.
- Source: https://registry.npmjs.org/@reach/observe-rect/-/observe-rect-1.2.0.tgz
- Path inside published package archive: `package/README.md`.
- SHA-256 of source file/excerpt: `0f4f77b884e4598397acb642f8bc757e3d2b950b451de6d2a6dacef66cbd3779`.
- Reproduced text: L008.
- Provenance: Verbatim Legal section from the bundled README; also verified in official upstream README at commit 4da69010382bf9b9a4cb2078afe613babab9d764.

### S012

- Evidence type: `canonical-license-terms-extract`.
- Source: https://raw.githubusercontent.com/spdx/license-list-data/main/json/details/MIT.json
- SHA-256 of source file/excerpt: `83330a27667f812efa9c7cdaefd4c83f95b57a7454323a61ee29291312640468`.
- Reproduced text: L003.
- Provenance: Standard MIT permission/warranty paragraphs from SPDX. Copyright attribution is separately preserved from the package README; no owner was invented.

### S013

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@remix-run/router/-/router-1.23.4.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `77c9ee6a9c5d5782fb0c50b50d101189116314c14f7df0a1b0d385fc96a7ba49`.
- Reproduced text: L009.
- Provenance: Copied from installed node_modules/@remix-run/router/LICENSE.md.

### S014

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@types/prop-types/-/prop-types-15.7.15.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383`.
- Reproduced text: L010.
- Provenance: Copied from installed node_modules/@types/prop-types/LICENSE.

### S015

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@types/react/-/react-17.0.90.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383`.
- Reproduced text: L010.
- Provenance: Copied from installed node_modules/@types/react/LICENSE.

### S016

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@types/react-transition-group/-/react-transition-group-4.4.12.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383`.
- Reproduced text: L010.
- Provenance: Copied from installed node_modules/@types/react-transition-group/LICENSE.

### S017

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/@types/scheduler/-/scheduler-0.16.8.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383`.
- Reproduced text: L010.
- Provenance: Copied from installed node_modules/@types/scheduler/LICENSE.

### S018

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/classnames/-/classnames-2.5.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `0c1c24dcc129cb0c02ee9803cfa7a50061c236a3b59b36a714c27f3ade66d7be`.
- Reproduced text: L011.
- Provenance: Copied from installed node_modules/classnames/LICENSE.

### S019

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/compute-scroll-into-view/-/compute-scroll-into-view-3.1.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `dfe2b2d612c7c1de1f32dcab5969bd68ecac0022a7582ca8cd4594729b9c8664`.
- Reproduced text: L012.
- Provenance: Copied from installed node_modules/compute-scroll-into-view/LICENSE.

### S020

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/csstype/-/csstype-3.2.3.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `11d55bd4541c75ee7879547ac49089c489163dae49551389713c3d026cab383e`.
- Reproduced text: L013.
- Provenance: Copied from installed node_modules/csstype/LICENSE.

### S021

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/detect-browser/-/detect-browser-5.3.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `865e4172abe65f4ad1fe3d73fb21d7b9a16588c68597753470b2078bedf3b54a`.
- Reproduced text: L014.
- Provenance: Copied from installed node_modules/detect-browser/LICENSE.

### S022

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/detect-node-es/-/detect-node-es-1.1.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `54b32293ea560d22cd9b62af9b2cda1a030d910a48c6601d227c5ff2338d95f8`.
- Reproduced text: L015.
- Provenance: Copied from installed node_modules/detect-node-es/LICENSE.

### S023

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/dom-helpers/-/dom-helpers-5.2.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `1b0a9ba95a67bc484e028b13c61b83fc5aa1ca7957d1e2d43c2b0f70d9e9dd45`.
- Reproduced text: L016.
- Provenance: Copied from installed node_modules/dom-helpers/LICENSE.

### S024

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/downshift/-/downshift-8.4.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `344d83316cc914b58e175f97942abc2ed8b9bb1a6221f5e03586422a2426a4d1`.
- Reproduced text: L017.
- Provenance: Copied from installed node_modules/downshift/LICENSE.

### S025

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/focus-lock/-/focus-lock-1.3.6.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `30f0cfddf483d1128e3610205020f2041a6c5e837aa999e0aa82e5576187d4a9`.
- Reproduced text: L018.
- Provenance: Copied from installed node_modules/focus-lock/LICENSE.

### S026

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/get-nonce/-/get-nonce-1.0.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `acf3b087b348d2f2e731b9d4131af185aad694c1204ce14a46909483fd42da05`.
- Reproduced text: L019.
- Provenance: Copied from installed node_modules/get-nonce/LICENSE.

### S027

- Evidence type: `installed-source-header-extract`.
- Source: https://registry.npmjs.org/intersection-observer/-/intersection-observer-0.12.2.tgz
- Path inside published package archive: `package/intersection-observer.js`.
- SHA-256 of source file/excerpt: `53e49150a985ed337ada3e9399639105aca756b884e53acdeba26452806d87e0`.
- Reproduced text: L020.
- Provenance: Verbatim distributed source header; matched official release source at gitHead a7874945a27061d01e4076b8aae8b30abc756e97.

### S028

- Evidence type: `source-referenced-license-terms`.
- Source: https://www.w3.org/copyright/software-license-2015/
- SHA-256 of source file/excerpt: `2135a7525d9ef8b9b36cacccd768fb99fd358cf531dbc260e61c04dfe1bafde0`.
- Reproduced text: L021.
- Provenance: Full license and disclaimer text extracted from the official 2015 license page referenced by the distributed source. Its full-notice requirement specifies a location viewable to users.

### S029

- Evidence type: `metadata-declared-canonical-license-terms`.
- Source: https://www.apache.org/licenses/LICENSE-2.0.txt
- SHA-256 of source file/excerpt: `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
- Reproduced text: L022.
- Provenance: Canonical Apache-2.0 terms included because that identifier is declared in package metadata. This does not resolve the conflicting W3C declaration in the actual source.

### S030

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/js-tokens/-/js-tokens-4.0.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `2213d91c606205c71eb051a199478cdc2adde945893404d7f1421436dd6d5cc1`.
- Reproduced text: L023.
- Provenance: Copied from installed node_modules/js-tokens/LICENSE.

### S031

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/lodash/-/lodash-4.18.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `f71e8ed126b46346494aad5486874cd8f0aafe95092ed67d2e3cb6110f939abc`.
- Reproduced text: L024.
- Provenance: Copied from installed node_modules/lodash/LICENSE.

### S032

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/lodash-es/-/lodash-es-4.18.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `f71e8ed126b46346494aad5486874cd8f0aafe95092ed67d2e3cb6110f939abc`.
- Reproduced text: L024.
- Provenance: Copied from installed node_modules/lodash-es/LICENSE.

### S033

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/loose-envify/-/loose-envify-1.4.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `4eb7543b08d955a6d23fcc224601d43ff566e775be918805e26210d7f6eb4893`.
- Reproduced text: L025.
- Provenance: Copied from installed node_modules/loose-envify/LICENSE.

### S034

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/object-assign/-/object-assign-4.1.1.tgz
- Path inside published package archive: `package/license`.
- SHA-256 of source file/excerpt: `6fb9754611c20f6649f68805e8c990e83261f29316e29de9e6cedae607b8634c`.
- Reproduced text: L026.
- Provenance: Copied from installed node_modules/object-assign/license.

### S035

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/popper-max-size-modifier/-/popper-max-size-modifier-0.2.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `0f141811d3a9e496c13783bfffd14065bf80e6445e3465b1157f1493b009bcd6`.
- Reproduced text: L027.
- Provenance: Copied from installed node_modules/popper-max-size-modifier/LICENSE.

### S036

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/prop-types/-/prop-types-15.8.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `f657f99d3fb9647db92628e96007aabb46e5f04f33e49999075aab8e250ca7ce`.
- Reproduced text: L028.
- Provenance: Copied from installed node_modules/prop-types/LICENSE.

### S037

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react/-/react-17.0.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `52412d7bc7ce4157ea628bbaacb8829e0a9cb3c58f57f99176126bc8cf2bfc85`.
- Reproduced text: L029.
- Provenance: Copied from installed node_modules/react/LICENSE.

### S038

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-clientside-effect/-/react-clientside-effect-1.2.8.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `65a1f00f35ea469048896fbfec3c5faed7d3fe7c7e0be2a0478470b59d9e1439`.
- Reproduced text: L030.
- Provenance: Copied from installed node_modules/react-clientside-effect/LICENSE.

### S039

- Evidence type: `upstream-release-license`.
- Source: https://raw.githubusercontent.com/mvasin/react-div-100vh/56872629f9a832b86a3e50e584ff990a1387f124/LICENSE
- SHA-256 of source file/excerpt: `643ec7df40cf670161acebab3c4ded9d135be4ea072a62accf5cda6bdef72ae2`.
- Reproduced text: L031.
- Provenance: Official v0.7.0 tag resolves to this commit; upstream LICENSE preserves its copyright statement.

### S040

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-dom/-/react-dom-17.0.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `52412d7bc7ce4157ea628bbaacb8829e0a9cb3c58f57f99176126bc8cf2bfc85`.
- Reproduced text: L029.
- Provenance: Copied from installed node_modules/react-dom/LICENSE.

### S041

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-fast-compare/-/react-fast-compare-3.2.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `1e187eec147d04bcbb3b7aef9f3b0ea82228961fa7d390d3efb9e57751dbe0cb`.
- Reproduced text: L032.
- Provenance: Copied from installed node_modules/react-fast-compare/LICENSE.

### S042

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-focus-lock/-/react-focus-lock-2.13.7.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `f3b3ff0eb35b71c73d0b85c0c0cfc83dbf735695de4fe46a44cbdf0ef1966634`.
- Reproduced text: L033.
- Provenance: Copied from installed node_modules/react-focus-lock/LICENSE.

### S043

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-is/-/react-is-16.13.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `52412d7bc7ce4157ea628bbaacb8829e0a9cb3c58f57f99176126bc8cf2bfc85`.
- Reproduced text: L029.
- Provenance: Copied from installed node_modules/prop-types/node_modules/react-is/LICENSE.

### S044

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-is/-/react-is-18.3.1.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `52412d7bc7ce4157ea628bbaacb8829e0a9cb3c58f57f99176126bc8cf2bfc85`.
- Reproduced text: L029.
- Provenance: Copied from installed node_modules/downshift/node_modules/react-is/LICENSE.

### S045

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-merge-refs/-/react-merge-refs-1.1.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `ab279d396fd279188cec77662c3298690ad468c59985c0e2a5027a3b4275e38f`.
- Reproduced text: L034.
- Provenance: Copied from installed node_modules/react-merge-refs/LICENSE.

### S046

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-popper/-/react-popper-2.3.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `7d8e07a60e4aa02d24648ce987b644c2e0978234d209fe454a4fe91701867355`.
- Reproduced text: L035.
- Provenance: Copied from installed node_modules/react-popper/LICENSE.

### S047

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-remove-scroll/-/react-remove-scroll-2.7.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `30f0cfddf483d1128e3610205020f2041a6c5e837aa999e0aa82e5576187d4a9`.
- Reproduced text: L018.
- Provenance: Copied from installed node_modules/react-remove-scroll/LICENSE.

### S048

- Evidence type: `upstream-current-license-fallback`.
- Source: https://raw.githubusercontent.com/theKashey/react-remove-scroll-bar/8ca9ba5ea52de03308fe8ced94f7b159a44d28ff/LICENSE
- SHA-256 of source file/excerpt: `a79aae0c0f21990d9d963bb3c5a79cdcea9a46f8523ba55c58d7fe776b6ebc84`.
- Reproduced text: L036.
- Provenance: Current official upstream LICENSE, pinned at audit time. Published npm gitHead b3b1287aad81def2e2ae707274b74531b61ddbaf was unavailable in the public repository; this is not represented as a release-commit match.

### S049

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-router/-/react-router-6.30.6.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `77c9ee6a9c5d5782fb0c50b50d101189116314c14f7df0a1b0d385fc96a7ba49`.
- Reproduced text: L009.
- Provenance: Copied from installed node_modules/react-router-dom/node_modules/react-router/LICENSE.md.

### S050

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-router-dom/-/react-router-dom-6.30.6.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `77c9ee6a9c5d5782fb0c50b50d101189116314c14f7df0a1b0d385fc96a7ba49`.
- Reproduced text: L009.
- Provenance: Copied from installed node_modules/react-router-dom/LICENSE.md.

### S051

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-style-singleton/-/react-style-singleton-2.2.3.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `30f0cfddf483d1128e3610205020f2041a6c5e837aa999e0aa82e5576187d4a9`.
- Reproduced text: L018.
- Provenance: Copied from installed node_modules/react-style-singleton/LICENSE.

### S052

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-swipeable/-/react-swipeable-7.0.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `a075b2ceb62e66f713d2390b332c66b4f70601d6dc63537a4faf3c43522b7ca8`.
- Reproduced text: L037.
- Provenance: Copied from installed node_modules/react-swipeable/LICENSE.

### S053

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-transition-group/-/react-transition-group-4.4.5.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `b9adc17ad75a847d8743be2843935f0842a0ea4f45e7b0c7e17ed0be98614b9b`.
- Reproduced text: L038.
- Provenance: Copied from installed node_modules/react-transition-group/LICENSE.

### S054

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/react-virtual/-/react-virtual-2.10.4.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `8802066b56e5473d7d0084fe7ee633a3e3377fd344d8408c620e3569dc604fe1`.
- Reproduced text: L039.
- Provenance: Copied from installed node_modules/react-virtual/LICENSE.

### S055

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/scheduler/-/scheduler-0.20.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `52412d7bc7ce4157ea628bbaacb8829e0a9cb3c58f57f99176126bc8cf2bfc85`.
- Reproduced text: L029.
- Provenance: Copied from installed node_modules/scheduler/LICENSE.

### S056

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/simplebar-core/-/simplebar-core-1.3.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `ecac7147196913380d69bde30b1ccb25e680c5a8ec5be1189078e5c69e8cc9e2`.
- Reproduced text: L040.
- Provenance: Copied from installed node_modules/simplebar-core/LICENSE.

### S057

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/simplebar-react/-/simplebar-react-3.3.2.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `ecac7147196913380d69bde30b1ccb25e680c5a8ec5be1189078e5c69e8cc9e2`.
- Reproduced text: L040.
- Provenance: Copied from installed node_modules/simplebar-react/LICENSE.

### S058

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/tslib/-/tslib-2.8.1.tgz
- Path inside published package archive: `package/LICENSE.txt`.
- SHA-256 of source file/excerpt: `210b19e543130388c68654b7497e967119ce17145f66ab7d85688fbd70f08751`.
- Reproduced text: L041.
- Provenance: Copied from installed node_modules/tslib/LICENSE.txt.

### S059

- Evidence type: `installed-package-additional-notice`.
- Source: https://registry.npmjs.org/tslib/-/tslib-2.8.1.tgz
- Path inside published package archive: `package/CopyrightNotice.txt`.
- SHA-256 of source file/excerpt: `da16ddb65f8ca390998fb99223d0112498b56b45784d00afd77ff8ce1ac4de8b`.
- Reproduced text: L042.
- Provenance: Additional bundled notice copied from node_modules/tslib/CopyrightNotice.txt.

### S060

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/use-callback-ref/-/use-callback-ref-1.3.3.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `30f0cfddf483d1128e3610205020f2041a6c5e837aa999e0aa82e5576187d4a9`.
- Reproduced text: L018.
- Provenance: Copied from installed node_modules/use-callback-ref/LICENSE.

### S061

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/use-sidecar/-/use-sidecar-1.1.3.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `30f0cfddf483d1128e3610205020f2041a6c5e837aa999e0aa82e5576187d4a9`.
- Reproduced text: L018.
- Provenance: Copied from installed node_modules/use-sidecar/LICENSE.

### S062

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/use-sync-external-store/-/use-sync-external-store-1.7.0.tgz
- Path inside published package archive: `package/LICENSE`.
- SHA-256 of source file/excerpt: `da6d3703ed11cbe42bd212c725957c98da23cbff1998c05fa4b3d976d1a58e93`.
- Reproduced text: L043.
- Provenance: Copied from installed node_modules/use-sync-external-store/LICENSE.

### S063

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/uuid/-/uuid-14.0.3.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `beaa6b04fb82e41dd2ad679e19e27953afb5999b1abbb455b6564e78ebfeb332`.
- Reproduced text: L044.
- Provenance: Copied from installed node_modules/uuid/LICENSE.md.

### S064

- Evidence type: `installed-package-license`.
- Source: https://registry.npmjs.org/warning/-/warning-4.0.3.tgz
- Path inside published package archive: `package/LICENSE.md`.
- SHA-256 of source file/excerpt: `f657f99d3fb9647db92628e96007aabb46e5f04f33e49999075aab8e250ca7ce`.
- Reproduced text: L028.
- Provenance: Copied from installed node_modules/warning/LICENSE.md.
