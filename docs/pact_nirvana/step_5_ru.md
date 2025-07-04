---
title: 5. Уровень Gold — интеграция с PR-пайплайнами
---

<details open>
  <summary>Диаграмма уровня Gold</summary>

```plantuml
@startuml
actor Consumer
actor Provider
database Broker

Consumer -> Broker : publish pact [feat 123]
note left of Consumer : PR-пайплайн валидации

Consumer -> Broker : publish pact [main]
note left of Consumer : Пайплайн коммита в main
note right of Provider : PR-пайплайн валидации

Provider -> Broker : верификация контракта main-версии\n+ задеплоенных версий потребителя
Provider -> Broker : публикация результатов (версия поставщика + ветка)
@enduml
````

</details>

Цель этого уровня — добавить поддержку Pact в ваш PR-пайплайн. Это гарантирует, что сборка упадёт, если тесты контракта
или верификации не пройдут.

Однако это **не** гарантирует, что вы не смержите или не задеплоите изменения, несовместимые с потребителями или
поставщиками в конкретной среде. Это достигается на следующем уровне — при добавлении поддержки `can-i-deploy`.

Одна из сложностей, вызванная «ориентированным на потребителя» характером процесса, заключается в том, что новые
взаимодействия часто добавляются в контракт **до** реализации соответствующей функциональности у поставщика.

Использование **веток** вместе с версиями приложений в брокере позволяет отделить «стабильные» контракты от «фичевых» и
добавлять новые ожидания в контракт без нарушения сборок поставщика. Представьте это как хронологически упорядоченные
серии контрактов для каждой ветки — аналогично git-фичевым веткам, где можно вести стабильную разработку и параллельно
тестировать новые, потенциально ломающие изменения.

Для реализации этого подхода, при публикации контракта, связанная версия pacticipant должна включать **идентификатор
ветки**, по которому поставщик будет различать стабильные (например, `master`) и потенциально ломающие контракты (
например, `feat-new-foobar`).

Чтобы сохранить успешные сборки в CI поставщика, нужно использовать **селекторы версий потребителя**, чтобы
верифицировать **ветку main**, а не просто последнюю доступную версию.

Если вы используете фичевые ветки при разработке потребителя, рекомендуется публиковать контракт с
указанием [pacticipant](https://docs.pact.io/getting_started/terminology#pacticipant)-версии и именем ветки. Если вы
используете feature toggle — ветка может быть названа по имени флага. Ваша клиентская библиотека Pact позволяет задать
имя ветки при публикации контрактов.

## Задачи

### A. Публикация контракта из pull request пайплайна потребителя

Если всё настроено правильно, контрактные тесты потребителя должны входить в обычный тестовый прогон. Но дополнительно
нужно изменить ваш PR-пайплайн, чтобы он публиковал контракт в брокер с корректной версией и названием ветки,
соответствующим PR-ветке.

1. Настройте сборку потребителя так, чтобы Pact-тесты запускались и контракт публиковался в Broker в рамках CI-сборки (
   см. документацию по используемому языку). Обычно Pact-тесты запускаются после юнит-тестов и до выката в тестовую
   среду.
2. Укажите имя ветки, используемое для каждого билда потребителя при публикации контракта (см. документацию для вашей
   библиотеки Pact). Рекомендуемый подход — определять и использовать имя git/svn-ветки автоматически. В крайнем случае
   можно задать статичное имя, например, `"main"` или `"stable"`.

### B. Изменение пайплайна коммитов в main для публикации с основной веткой

После настройки PR-пайплайна потребителя на публикацию контрактов, необходимо также изменить пайплайн ветки `main`,
чтобы зафиксировать, что конкретная версия потребителя принадлежит именно ветке `main`. Обычно это делает CI-система,
прогоняющая те же тесты после слияния PR в `main`.

Поставщику нужна эта информация, чтобы получить **последний опубликованный контракт из ветки main**. Иначе может быть
получен контракт из фичевой ветки, который не готов к верификации.

### C. Настройка верификации контрактов при изменениях у поставщика

После публикации контрактов потребителем с корректными названиями веток, можно добавить верификацию Pact в PR-пайплайн
поставщика.

Верификация Pact с использованием селекторов версий потребителя должна быть частью обычного прогона юнит-тестов.

1. Как указано в шаге 4, настройте сборку поставщика на загрузку контрактов из брокера с использованием селекторов
   версий потребителя и публикацию результатов верификации как часть сборки (см. документацию для вашей библиотеки
   Pact). Обычно это происходит после юнит-тестов и до выката в тестовую среду. Рекомендуемую конфигурацию
   смотрите [здесь](/provider/recommended_configuration#verification-triggered-by-provider-change).
2. В конфигурации верификации поставщика убедитесь, что верифицируется **контракт последней версии ветки main** (см.
   документацию вашей библиотеки). Это поможет сохранить "зелёные" сборки.

    1. Пример: `{ "mainBranch": true }`, при условии, что контракт потребителя публиковался из ветки `main`/`master` —
       подробнее см. [документацию](https://docs.pact.io/pact_broker/branches#automatic-main-branch-detection) по
       автоматическому определению ветки в pact-broker.

### Примечания

Хотя некоторые языковые клиенты Pact (например, Gradle) предоставляют собственные средства публикации, мы рекомендуем
использовать один из [CLI-инструментов Pact](https://docs.pact.io/pact_broker/client_cli):

1. [Docker](https://hub.docker.com/r/pactfoundation/pact-cli)
2. [Pact Standalone CLI](https://github.com/pact-foundation/pact-ruby-standalone/releases)
3. [Pact Broker Client (Ruby)](https://github.com/pact-foundation/pact_broker-client)
4. [GitHub Actions](https://github.com/pactflow/actions)

Полезные ссылки:

* [Лучшие практики версионирования pacticipant](getting_started/versioning_in_the_pact_broker.md)
* [Рекомендованная конфигурация для публикации](https://docs.pact.io/consumer/recommended_configuration)
* [Рекомендованная конфигурация для верификации](https://docs.pact.io/provider/recommended_configuration)
