---
title: Рекомендуемая конфигурация для верификации контрактов
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

Обычно существует две причины для запуска задачи верификации контракта (pact):

1. Изменения на стороне поставщика
    * В этом случае необходимо выполнить полную регрессионную проверку всех контрактов со всеми потребителями на всех
      поддерживаемых этапах (например, `test`, `production`), чтобы убедиться в сохранении обратной совместимости.
2. Изменения в контракте
    * В этом случае необходимо верифицировать только изменённый контракт.

## Верификация, вызванная изменением у поставщика

Примеры ниже требуют поддержки API "pacts for verification" в вашей библиотеке Pact. Подробнее об этом
читайте [здесь](../pact_broker/advanced_topics/provider_verification_results_ru#pacts-for-verification).

### Селекторы версий потребителя

#### Если вы используете ветки и окружения

* Минимум, что должно быть проверено — это последний контракт с основной ветки разработки потребителя (например:
  `{ "mainBranch": true }`). Для этого потребитель должен передавать имя ветки при публикации контракта.
* Если вы уже [записываете развёртывания](../pact_broker/recording_deployments_and_releases) в Pact Broker при деплое в
  окружение, следует включить селекторы для задеплоенных и выпущенных версий (например:
  `{ "deployedOrReleased": true }`)
* Распространённая практика: поставщик создаёт ветку с таким же именем, как ветка потребителя (используемая в качестве
  тега версии потребителя), и применяет селектор для поиска совпадающей ветки (например: `{ "matchingBranch": true }`).
    * Контракты будут дедуплицированы, так что если `GIT_BRANCH` — это `main`, будет возвращён только один контракт для
      проверки.
    * Если невозможно определить нужные теги фичевых контрактов динамически, необходимо временно изменить селекторы при
      работе в ветке, а затем вернуть обратно после слияния.

#### Если вы используете теги

Это **не рекомендуется**, если вы используете версии Pact Broker и библиотек, поддерживающие ветки и окружения.

* Минимум — это последний контракт с основной ветки разработки потребителя (например: `{ tag: "main", latest: true }`).
  Это требует, чтобы [тег потребителя соответствовал git-ветке](../consumer/recommended_configuration_ru).

* Если вы достигли этапа, когда [тегируете версии приложения](../pact_nirvana/step_6_ru) в Pact Broker при деплое, можно
  включить селекторы по тегам окружений (например, `test`, `production`).

* Часто используемый паттерн — создание ветки поставщика с тем же именем, что у ветки потребителя, и настройка селектора
  на текущую ветку (например: `{ tag: process.env.GIT_BRANCH, latest: true }`).

    * Контракты будут дедуплицированы, и если `GIT_BRANCH` — `main`, будет один контракт. При отсутствии контрактов с
      указанным тегом, селектор игнорируется.

    * Если невозможно определить теги фичевых контрактов динамически, селекторы нужно временно менять в ветке и
      возвращать при слиянии.

### Ожидающие (pending) контракты

Включение этой функции предотвращает поломку основной сборки поставщика при изменении контракта. Если вы автоматически
подключаете контракт через совпадение имён веток, вы можете **отключить** pending на фичевых ветках, чтобы сборка
правильно проваливалась, пока функциональность не реализована, и затем успешно проходила при готовности.

### Контракты "в процессе работы" (work in progress)

Подключение таких контрактов позволяет верифицировать изменения без ручной правки конфигурации. Можно включать только на
основной ветке разработки, так как в фичевых ветках либо идёт реализация, либо Pact вообще не трогается.

### Примеры

Использование веток и окружений:

```js
const verificationOptions = {
    // ....
    provider: "example-provider",
    pactBrokerUrl: "http://test.pactflow.io",
    consumerVersionSelectors: [
        {mainBranch: true},
        {matchingBranch: true},
        {deployedOrReleased: true}
    ],
    enablePending: true,
    ...(process.env.GIT_BRANCH === "main"
        ? {includeWipPactsSince: "2020-01-01"}
        : {}),
    publishVerificationResult: process.env.CI === "true", // публикуем только из CI
    providerVersion: process.env.GIT_COMMIT, // используйте нужную переменную CI
    providerVersionBranch: process.env.GIT_BRANCH, // используйте нужную переменную CI
}
````

Использование тегов (устаревший способ):

```js
const verificationOptions = {
    // ....
    provider: "example-provider",
    pactBrokerUrl: "http://test.pactflow.io",
    consumerVersionSelectors: [
        {tag: "main", latest: true},
        {tag: process.env.GIT_BRANCH, latest: true},
        {tag: "test", latest: true},
        {tag: "production", latest: true}
    ],
    enablePending: true,
    includeWipPactsSince: process.env.GIT_BRANCH === "main" ? "2020-01-01" : undefined,
    publishVerificationResult: process.env.CI === "true",
    providerVersion: process.env.GIT_COMMIT,
    providerVersionTags: process.env.GIT_BRANCH ? [process.env.GIT_BRANCH] : [],
}
```

Использование веток и окружений в Ruby:

```js
# git-команды — только для локального теста
provider_version = ENV['GIT_COMMIT'] || `git rev-parse --verify HEAD`.strip
provider_branch = ENV['GIT_BRANCH'] || `git name-rev --name-only HEAD`.strip
publish_results = ENV['CI'] == 'true'
credentials = {
  username: ENV['PACT_BROKER_USERNAME'],
  password: ENV['PACT_BROKER_PASSWORD'],
  token: ENV['PACT_BROKER_TOKEN']
}.compact

Pact.service_provider "example-provider" do
  app_version provider_version
  app_version_branch provider_branch
  publish_verification_results publish_results
  
  honours_pacts_from_pact_broker do
    pact_broker_base_url 'http://test.pactflow.io', credentials

    consumer_version_selectors [
        { main_branch: true },
        { matching_branch: true },
        { deployed_or_released: true }
    ]
    enable_pending true
    include_wip_pacts_since provider_branch == "main" ? "2020-01-01" : nil
  end
end
```

Использование тегов в Ruby (устаревший способ):

```js
provider_version = ENV['GIT_COMMIT'] || `git rev-parse --verify HEAD`.strip
provider_branch = ENV['GIT_BRANCH'] || `git name-rev --name-only HEAD`.strip
publish_results = ENV['CI'] == 'true'
credentials = {
  username: ENV['PACT_BROKER_USERNAME'],
  password: ENV['PACT_BROKER_PASSWORD'],
  token: ENV['PACT_BROKER_TOKEN']
}

Pact.service_provider "example-provider" do
  app_version provider_version
  app_version_tags [provider_branch]
  publish_verification_results publish_results
  
  honours_pacts_from_pact_broker do
    pact_broker_base_url 'http://test.pactflow.io', credentials

    consumer_version_selectors [
        { tag: 'main', latest: true },
        { tag: provider_branch, latest: true },
        { tag: 'test', latest: true },
        { tag: 'production', latest: true }
    ]
    enable_pending true
    include_wip_pacts_since provider_branch == "main" ? "2020-01-01" : nil
  end
end
```

## Верификация, вызванная публикацией изменённого контракта

Когда контракт изменяется, webhook в Pact Broker запускает сборку поставщика и передаёт URL изменённого контракта.
Подробнее об этом в разделе [CI/CD настройки](../pact_nirvana/step_6_ru#Добавление-новой-задачи-верификации-поставщика).

Этот подход позволяет протестировать изменённый контракт против последней версии поставщика и всех задеплоенных или
выпущенных версий, как и в случае с селекторами версий потребителя.

Если известен URL контракта, не нужно указывать `pactBrokerUrl`, `providerName`,
`consumerVersionSelectors/consumerVersionTags`, `enablePending`, `includeWipPactsSince`. Пример переключения между
режимами верификации (все против изменённые) можно
посмотреть [здесь](https://github.com/pactflow/example-provider/blob/f1c91ec9f6ab428f95e03cce27c9bd525ee37107/src/product/product.pact.test.js#L23-L75).

### Примеры

```js
const verificationOptions = {
    pactUrls: [process.env.PACT_URL],
    publishVerificationResult: process.env.CI === "true",
    providerVersion: process.env.GIT_COMMIT,
    providerVersionBranch: process.env.GIT_BRANCH,
    providerVersionTags: process.env.GIT_BRANCH ? [process.env.GIT_BRANCH] : [],
}
```

```shell
PACT_BROKER_BASE_URL="..." # также задайте PACT_BROKER_USERNAME/PACT_BROKER_PASSWORD или PACT_BROKER_TOKEN
bundle exec rake pact:verify:at[${PACT_URL}]
```
