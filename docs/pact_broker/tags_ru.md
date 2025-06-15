---
title: Теги
---

Теперь рекомендуется указывать свойство [branch](branches_ru) при публикации pact'ов и результатов
верификации, а при развертывании или выпуске
использовать [record-deployment](recording_deployments_and_releases_ru#Запись-развертываний)
или [record-release](recording_deployments_and_releases_ru#Запись-выпусков).

Теги — это простые строковые значения, привязанные к ресурсам "версий участников" (версий приложений) в Pact Broker.
Обычно они используются для предоставления метаданных о версии — чаще всего, чтобы указать git-ветку (например,
`master`) или этап (`test`, `prod`).

Теги могут использоваться для различных целей:

* Тестирование `head` и `prod` версий потребителя и провайдера.
* Добавление новых взаимодействий без поломки сборок.

Тег привязывается к **версии приложения**, а не к самому pact'у. Однако URL-структура позволяет получать pact'ы по тегам
соответствующих версий.

Пример: чтобы отметить версию приложения `Foo` с хэшем `b5236e772` как продакшн-версию, выполните `PUT` на
`/pacticipants/Foo/versions/b5236e772/tags/prod`. Теперь все pact'ы и верификации, связанные с этой версией, считаются
продакшн.

Путь начинается с `/pacticipants`, а не `/consumers` или `/providers`, потому что "consumer" и "provider" — это роли,
которые приложение может принимать, а не его тип.

Важно: используемая схема версий не должна допускать дублирование версии в разных ветках — версия должна быть git sha
или включать его, например `1.2.456+405b31ec6`.
Подробнее — [Versioning in the Pact Broker](../getting_started/versioning_in_the_pact_broker_ru).

### Идентификация версий по тегу

При поиске "последней версии с тегом X" Pact Broker ищет самую позднюю **созданную** версию участника с этим тегом. То
есть, если вы сначала тегировали v1, затем v2, затем снова v1, последней будет считаться v2.

### "Последние" pact'ы

В отличие от Docker, в Pact Broker слово `latest` не является тегом. Это динамическая ссылка на последнюю созданную
версию. Например, `/pacts/provider/{provider}/consumer/{consumer}/latest` возвращает последний pact по времени.
`/{tag}` — pact для самой свежей версии с указанным тегом.

**Не создавайте тег `latest`**, иначе столкнётесь с путаницей вроде `/latest/latest`.

## Когда создаются теги?

### При публикации pact'ов

Можно указать "consumer version tags", которые будут применены к версии потребителя. Рекомендуется использовать имя
git-ветки.

### При публикации результатов верификации

Провайдер может указать "provider version tags". Лучше всего использовать имя ветки или общее имя вроде `dev`.

### После развертывания

*Тег обязателен для использования `can-i-deploy`, но для новичков можно временно пропустить.*

После развертывания версии приложения в окружение (например, `test`, `prod`), его версия должна быть отмечена тегом
окружения. Используйте [create version tag](https://github.com/pact-foundation/pact_broker-client#create-version-tag).

## Когда используются теги?

### При получении pact'ов для верификации

Провайдеры должны быть настроены на получение pact'ов по тегам, например последние `master` и `prod`.

### Перед развертыванием

`can-i-deploy` использует теги, чтобы проверить совместимость с другими приложениями. Например:

```bash
can-i-deploy --pacticipant Foo --version ad72df2 --to prod
```

## Получение pact'ов

* `/pacts/provider/PROVIDER/consumer/CONSUMER/latest/TAG` — pact для последней версии с тегом
* `/pacts/provider/PROVIDER/consumer/CONSUMER/latest-untagged` — для последней **без тегов**
* `/pacts/provider/PROVIDER/consumer/CONSUMER/latest` — просто последняя

## Создание тегов

Большинство библиотек Pact поддерживают автоматическое создание тегов при публикации pact'ов и верификаций.

Для ручного создания
используйте [Pact Broker CLI](https://github.com/pact-foundation/pact_broker-client#create-version-tag) или `PUT` запрос
к соответствующему URL.

### Пример (Javascript, Travis CI):

```js
const opts = {
    pactFilesOrDirs: ['./pacts'],
    pactBroker: '...',
    consumerVersion: process.env.TRAVIS_COMMIT,
    tags: [process.env.TRAVIS_BRANCH]
}

new Publisher(opts).publishPacts()
```

Для локальной публикации:

```js
const exec = command =>
    childProcess
        .execSync(command)
        .toString()
        .trim()

const opts = {
    ...,
    consumerVersion: process.env.TRAVIS_COMMIT || exec('git rev-parse HEAD'),
    tags: [process.env.TRAVIS_BRANCH || exec('git rev-parse --abbrev-ref HEAD')]
}

new Publisher(opts).publishPacts()
```

### Публикация результатов верификации

```js
const opts = {
    pactBrokerUrl: '...',
    providerVersion: process.env.TRAVIS_COMMIT,
    providerVersionTags: [process.env.TRAVIS_BRANCH],
    publishVerificationResult: process.env.CI === 'true'
}

return new Verifier(opts).verifyProvider()
```

## Использование тегов

### При верификации

Если вы используете `can-i-deploy`, убедитесь, что pact'ы из всех окружений были проверены.

Если вы новичок, начните с верификации `master`:

```js
const opts = {
    ...,
    consumerVersionTags: ['master', 'test', 'production'],
}

return new Verifier(opts).verifyProvider()
```

### При развертывании

См. документацию [can-i-deploy](can_i_deploy).

## Удаление тегов

CLI пока нет. Можно удалить тег через API:

```bash
curl -X DELETE https://broker/pacticipants/PACTICIPANT/versions/VERSION/tags/TAG
```

Обычно требуется при [откате](#Откаты).

## Добавление новых взаимодействий

Поддерживайте CI зелёным для стабильной версии pact'а, одновременно предоставляя новую версию для обновления кода
провайдера. Как только обе версии будут зелёными — сделайте новую версию стабильной.

Помните: успешный контракт достигается **совместной работой** обеих команд.

## Гарантия обратной совместимости

Чтобы провайдер поддерживал и текущую, и прод-версию потребителя, используйте тегирование.

### Шаг 1. Отметьте прод-версию pact'а тегом

Используйте команду [create-version-tag](https://github.com/pact-foundation/pact_broker-client#create-version-tag).

### Шаг 2. Настройте провайдера на верификацию прод pact'а

Добавьте `production` в список тегов для верификации.

## Откаты

Если вы откатываетесь к предыдущей версии, **удалите** тег `production` с текущей версии.
См. [удаление тегов](#Удаление-тегов).

## Использование тегов с feature toggle'ами

### Для потребителя

Теги создавались под git workflow с feature-ветками, но могут применяться и при trunk-based разработке с feature
toggle'ами.

#### Публикация pact'ов

Создайте "матрицу" pact'ов:

* **Базовый pact** — с дефолтными значениями флагов. Версия: `${GIT_SHA}`, тег: `${GIT_BRANCH}`.
* **Фичи** — по одному pact'у с включённой фичей. Версия: `${GIT_SHA}+${FEATURE_NAME}`, тег:
  `${GIT_BRANCH}+${FEATURE_NAME}`.

Пример:

* `58024cb` с тегом `main`
* `58024cb+feat_a` с тегом `main+feat_a`
* и т.д.

Каждая версия должна быть уникальной, иначе pact'ы перезапишутся.

#### Верификация pact'ов

На стороне провайдера включите поддержку WIP pact'ов.

#### Развертывание

Развёртывание — обычный вызов `can-i-deploy` с `${GIT_SHA}`. Для проверки включённой фичи —
`can-i-deploy --version ${GIT_SHA}+${FEATURE_NAME}`.
