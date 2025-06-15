---
title: Можно ли деплоить
description: Как использовать Pact и инструмент can-i-deploy, чтобы убедиться в безопасности развёртывания приложения.
toc_max_heading_level: 4
---

Перед тем как развернуть новую версию приложения в production-среду, необходимо понять, совместима ли эта версия с уже
развернутыми версиями других приложений. Старый подход заключался в развёртывании заранее протестированных наборов
приложений одновременно, что создавало узкие места: быстрая разработка и тестирование одного сервиса тормозились из-за
медленного прогресса в другом.

Метод Pact предлагает другой подход — использовать Pact "Матрицу" и инструмент `can-i-deploy`.  
Матрица — это таблица, в которой отражены все версии потребителей и провайдеров, проверенные друг против друга.  
(Когда публикуется pact, сохраняется версия потребителя, сгенерировавшего его. Когда происходит проверка на стороне
провайдера, результаты публикуются в Pact Broker вместе с версией провайдера. Вся эта информация формирует Pact Matrix.)

Вы можете просматривать матрицу для любой пары приложений, кликнув на значок решётки рядом с вашим pact'ом в интерфейсе
Pact Broker.

### Пример матрицы для Foo (потребитель) и Bar (провайдер):

| Версия Foo (потребитель) | Версия Bar (провайдер) | Проверка успешна? |
|:-------------------------|:-----------------------|:------------------|
| 22                       | 56                     | true              |
| 23                       | 56                     | true              |
| 23                       | 57                     | false             |
| 23                       | 58                     | true              |
| 24                       | 58                     | true              |
| 25                       | 58                     | false             |

### Как это помогает?

Если мы знаем, что версия Bar 56 уже находится в production, матрица показывает, что можно безопасно развернуть версии
Foo 22 или 23 — но не более поздние. Аналогично, если версия Foo 24 уже в проде, мы можем безопасно развернуть Bar
версии 58 — но не предыдущие.

---

### Настройка уведомлений о развёртывании

В скрипт развёртывания каждого приложения, использующего Pact, нужно добавить шаг уведомления брокера после успешного
деплоя.

Актуальные версии Pact Broker используют команды [
`record-deployment`](recording_deployments_and_releases_ru#Запись-развертываний) и [record-release](recording_deployments_and_releases_ru#Запись-выпусков). Старые версии используют теги.
Подробнее см. в разделе [использование can-i-deploy с тегами](#Использование-can-i-deploy-с-тегами).

Пример команды:

```

\$ pact-broker record-deployment --pacticipant Bar --version 56 --environment production

```

Рекомендуется аналогично отмечать и деплои в pre-prod средах.

### Матрица теперь будет выглядеть так:

| Foo (потребитель) | Bar (провайдер) | Проверка успешна? |
|:------------------|:----------------|:------------------|
| 22                | 56 (production) | true              |
| 23                | 56 (production) | true              |
| 23                | 57              | false             |
| 23                | 58              | true              |
| 24                | 58              | true              |
| 25                | 58              | false             |

Благодаря такой информации, Pact Broker знает зависимости каждого приложения и может определить, безопасно ли развернуть
конкретную версию, проверив наличие успешных проверок между этой версией и уже развёрнутыми в среде.

---

### Проверка безопасности развёртывания Foo версии 23:

```

\$ pact-broker can-i-deploy --pacticipant Foo --version 23 --to-environment production

Computer says yes \o/

| CONSUMER | C.VERSION | PROVIDER | P.VERSION | SUCCESS? | RESULT# |
| -------- | --------- | -------- | --------- | -------- | ------- |
| Foo      | 23        | Bar      | 56        | true     | 1       |

## VERIFICATION RESULTS

1. [https://pact-broker/.../verification-results/375](https://pact-broker/.../verification-results/375) (success)

All required verification results are published and successful

```

Код выхода `0` означает "да".

---

### Пример неудачного запроса для Foo версии 24:

```

\$ pact-broker can-i-deploy --pacticipant Foo --version 24 --to-environment production

Computer says no ¯\_(ツ)\_/¯

| CONSUMER | C.VERSION | PROVIDER | P.VERSION | SUCCESS? | RESULT# |
| -------- | --------- | -------- | --------- | -------- | ------- |
| Foo      | 24        | Bar      | 56        | false    | 1       |

## VERIFICATION RESULTS

1. [https://pact-broker/.../verification-results/375](https://pact-broker/.../verification-results/375) (failure)

All required verification results are published and successful

```

Код выхода `1` означает "нельзя".

---

## Резюме

Чтобы убедиться в безопасности деплоя:

Перед деплоем:

```

\$ pact-broker can-i-deploy --pacticipant PACTICIPANT --version VERSION --to-environment ENVIRONMENT

```

После деплоя:

```

\$ pact-broker record-deployment --pacticipant PACTICIPANT --version VERSION --environment ENVIRONMENT

```

---

## Дополнительные возможности

См. [здесь](recording_deployments_and_releases_ru) подробности о деплоях и релизах.

Дополнительно `can-i-deploy` поддерживает:

* ожидание результатов проверки (полезно при запуске сборки провайдера через webhook),
* игнорирование отдельных интеграций (например, новый провайдер ещё не настроен),
* режим "dry-run", при котором результат отображается, но код выхода всегда успешен — полезно для отладки пайплайнов.

---

## Использование can-i-deploy с тегами

До появления полноценной поддержки записей о развёртывании, отслеживание версий в окружениях происходило через теги.
Поддержка тегов остаётся, но новые функции могут её не учитывать. Рекомендуется переходить на `record-deployment`.

Теги — это простые строковые значения, сохраняемые с объектом версии pacticipant'а.  
Одна версия может иметь несколько тегов, и один тег — использоваться у разных версий (по сути, "псевдо-ветка").

Изначально `can-i-deploy` работал только с тегами. Ниже — описание этого способа для пользователей, которые всё ещё
используют теги.

---

### Указание версии приложения

Параметры:

* `--pacticipant PACTICIPANT`
* Один из:
    * `--version VERSION` — указание точной версии (рекомендуется)
    * `--latest` — последняя версия
    * `--latest TAG` — последняя версия с тегом
    * `--all TAG` — все версии с тегом (например, все `prod`-версии мобильного клиента)

Использование конкретной версии — надёжный способ избежать гонок данных.

---

### Рекомендуемый подход: Broker сам вычисляет зависимости

Для этого необходимо, чтобы Broker знал, какие версии развернуты в окружении.

Если поддерживаются окружения:

```

pact-broker record-deployment --pacticipant Foo --version 173153ae0 --environment test

```

Если только теги:

```

pact-broker create-version-tag --pacticipant Foo --version 173153ae0 --tag test

```

После этого можно безопасно проверить:

```

\$ pact-broker can-i-deploy --pacticipant PACTICIPANT --version VERSION&#x20;
\[--to-environment ENV | --to TAG]&#x20;
\--broker-base-url [https://my-pact-broker](https://my-pact-broker)

```

### Примеры

Можно ли деплоить Foo версии 173153ae0 в тестовую среду?

```

\$ pact-broker can-i-deploy --pacticipant Foo --version 173153ae0&#x20;
\--to-environment test&#x20;
\--broker-base-url [https://my-pact-broker](https://my-pact-broker)

```

Можно ли деплоить последнюю версию Foo?

```

\$ pact-broker can-i-deploy --pacticipant Foo --latest&#x20;
\--broker-base-url [https://my-pact-broker](https://my-pact-broker)

```

Можно ли деплоить последнюю версию Foo с тегом "test" в прод?

```

\$ pact-broker can-i-deploy --pacticipant Foo --latest test&#x20;
\--to prod&#x20;
\--broker-base-url [https://my-pact-broker](https://my-pact-broker)

```

---

### Альтернативный подход: Явное указание зависимостей

Если вы не можете использовать теги, можно явно указать зависимости:

```

\$ pact-broker can-i-deploy --pacticipant Foo --version 173153ae0&#x20;
\--pacticipant Bar --version ac23df1e8

```

С тегами:

```

\$ pact-broker can-i-deploy --pacticipant Foo --latest master&#x20;
\--pacticipant Bar --latest master

```

Для провайдера Bar и всех `prod` версий Foo:

```

\$ pact-broker can-i-deploy --pacticipant Bar --version b80e7b1b&#x20;
\--pacticipant Foo --all prod&#x20;
\--to prod

```
