---
title: Запись развертываний и выпусков
---

Pact Broker должен знать, какие версии приложений находятся в каких окружениях, чтобы возвращать корректные pact'ы для
верификации и определять, [безопасно ли развертывание](can_i_deploy_ru).

Для уведомления брокера о развертывании или выпуске версии приложения используются команды
`pact-broker record-deployment` и `pact-broker record-release` из CLI.

"Развернутые версии" и "выпущенные версии" очень похожи, но моделируются по-разному в Pact Broker. Отличие между
`record-deployment` и `record-release` состоит в следующем:

* `record-deployment` используется для сценария, при котором новое развертывание заменяет предыдущую версию приложения.
  Применяется для API и потребителей, разворачиваемых в конкретных инстансах. Автоматически помечает предыдущую версию
  как неразвернутую.
* `record-release` моделирует ситуацию, когда выпуск новой версии не делает предыдущие версии недоступными. Применяется
  для мобильных приложений и библиотек, распространяемых через сторы или репозитории. Не изменяет статус предыдущих
  выпусков.

Версии развертываний и выпусков — разные типы ресурсов в Pact Broker, и одна и та же версия приложения может быть
одновременно и развернута, и выпущена. Например, мобильное приложение может быть развернуто в тестовом окружении на
устройстве для автоматического тестирования и выпущено в сторе в проде.

Жизненные циклы развертываний и выпусков полностью независимы. Прекращение поддержки выпуска не помечает развернутую
версию как неразвернутую, и наоборот.

## Окружения

Прежде чем записывать развертывание или выпуск, необходимо создать окружение в Pact Broker. Для быстрого старта
окружения `test` и `production` уже созданы.

Создание окружения:

```bash
$ pact-broker create-environment --name NAME --display-name DISPLAY_NAME \
[--no-production|--production]
```

#### Примеры

```bash
$ pact-broker create-environment --name uat --display-name UAT --no-production
$ pact-broker create-environment --name customer-1-production --display-name "Customer 1 Production" --production
```

### Обработка разных представлений об "окружении"

Для корректной работы `can-i-deploy` все команды и Pact Broker должны единообразно понимать, что такое "окружение".
Например, потребитель может иметь несколько приложений, использующих один инстанс провайдера. Для потребителя — это
разные окружения, для провайдера — одно.

Возможные подходы:

1. Создать одно окружение и использовать `--application-instance`, присваивая каждому приложению уникальный
   идентификатор.
2. Создать отдельное окружение для каждой подгруппы и вызывать `record-deployment` отдельно для каждой. При этом
   `can-i-deploy` нужно будет вызывать для каждого окружения и развертывать только при положительном результате для
   всех.

## Развертывания

### Запись развертываний

Вызывайте `record-deployment` в самом конце процесса развертывания, когда оно точно завершилось и старые инстансы больше
не работают.

#### Примеры

```bash
record-deployment --pacticipant foo --version 6897aa95e --environment production

record-deployment --pacticipant foo --version 6897aa95e --environment production \
--application-instance customer-1

record-deployment --pacticipant foo --version 6897aa95e --environment test \
--application-instance iphone-2
```

#### Инстансы приложений

Параметр `application-instance` используется, если одновременно и постоянно развёрнуто несколько инстансов одного
приложения в одном окружении.

Используется для различения версий одного приложения внутри окружения и для корректного определения предыдущей версии,
которую следует пометить как неразвернутую.

Примечание: значение `null` (анонимный инстанс) считается отдельным значением.

**Не** используйте `application-instance` для моделирования blue/green или rolling deployments — см. следующий раздел.

##### Почему `application-instance` не подходит для длительных развертываний

Пример: Consumer v2 зависит от Provider v2. Пока идёт rolling deployment Provider v2, Consumer v2 **не может** быть
безопасно развернут, потому что Provider v1 ещё в проде. Поэтому запись Provider v2 как "развернутого" до завершения
развертывания не имеет смысла.

#### Откаты

При откате вызовите `record-deployment` с версией, на которую происходит откат.

### Запись отмены развертывания

Обычно не требуется, так как `record-deployment` сам помечает старую версию как неразвернутую.

Если же инстанс приложения полностью удаляется из окружения:

```bash
record-undeployment --pacticipant my-retired-service --environment test

record-undeployment --pacticipant foo --environment test \
--application-instance mobile-2
```

## Выпуски

### Запись выпусков

`record-release` используется после того, как версия приложения стала доступна в проде (например, релиз на Github,
публикация в app store и т.д.). Не влияет на предыдущие версии. Отсутствует понятие инстанса.

#### Примеры

```bash
record-release --pacticipant foo-mobile-app --version 6897aa95e --environment production
```

### Завершение поддержки выпуска

Если выпуск больше не поддерживается:

```bash
record-support-ended --pacticipant foo-mobile-app --version 6897aa95e --environment production
```

## Использование развернутых и выпущенных версий

Два основных случая:

1. Определение pact'ов, которые должен верифицировать провайдер — используйте селектор:

```json
{
  "deployedOrReleased": true
}
```

2. Проверка возможности развертывания через [can-i-deploy](can_i_deploy_ru):

```bash
pact-broker can-i-deploy --pacticipant Foo \
--version 617c76e8bf05e1a480aed86a0946357c042c533c \
--to-environment production
```

## Миграция от тегов к развертываниям/выпускам

Ранее использовались [теги](tags_ru), но они не отражают смысловую нагрузку и не позволяют моделировать отмену
развертывания.

Переход к моделированию окружений, развертываний и выпусков позволяет:

* Определять, какие pact'ы нужно верифицировать
* Запускать проверки автоматически
* Проверять безопасность развертывания без дополнительной настройки

### Зачем мигрировать?

* Теги трудны для понимания новичками
* Поддержка новых webhook'ов для публикации результатов
* Лучшая производительность при большом объеме данных
* Новые функции будут ориентированы на развертывания/выпуски, а не теги

### Поддержка селектора `{ deployedOrReleased: true }`

* Pact JS — с v9.16
* Pact Ruby — с v1.59.0
* Pact Go — с v1.6.3
* Pact Rust — с v0.8.7
* Pact JVM — 4.3.12+
* Pact NET — v4.0.0-beta.1 ([issue](https://github.com/pact-foundation/pact-net/issues/311))
* Pact Python — не поддерживается ([issue](https://github.com/pact-foundation/pact-python/issues/246))
* Pact Scala — не поддерживается ([issue](https://github.com/ITV/scala-pact/issues/224))
* Pact4s — не поддерживается
* Pact PHP — не поддерживается ([issue](https://github.com/pact-foundation/pact-php/issues/206))

### Шаги миграции

1. Обновите Pact Broker

2. Обновите Pact Broker Client

3. Проверьте поддержку селектора `{ deployedOrReleased: true }`

4. Создайте ресурсы окружений: `pact-broker create-environment`

```bash
pact-broker create-environment --name NAME --display-name DISPLAY_NAME [--no-production|--production]
```

5. После `create-version-tag` добавьте вызов `record-deployment`

```bash
pact-broker record-deployment --pacticipant PACTICIPANT --version VERSION --environment ENVIRONMENT
```

6. В коде провайдера добавьте селектор `{ "deployedOrReleased": true }`

7. Обновите вызов `can-i-deploy`:

```bash
pact-broker can-i-deploy --pacticipant PACTICIPANT --version VERSION --to-environment ENVIRONMENT
```

8. После успешного перехода удалите вызовы `create-version-tag` и селекторы по тегам.
