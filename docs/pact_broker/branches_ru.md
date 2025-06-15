---
title: Ветки
---

С версии 2.82.0 Pact Broker поддерживает репозиторные ветки как полноценную сущность. Ранее рекомендовалось
использовать [теги](tags_ru) для представления веток. Поддержка веток упрощает документацию, улучшает
сообщения и обеспечивает разумные значения по умолчанию.

## Доменная модель

Ветки в Pact Broker моделируют ветки репозитория (git, svn и т.д.). `branch` принадлежит `pacticipant` (приложению).
Одна ветка может содержать множество версий участника, и одна версия участника может принадлежать нескольким веткам (
хотя обычно — одной).

Одна версия участника (`pacticipant version`) должна соответствовать одному коммиту. Номер версии должен [*быть или
содержать коммит*](../getting_started/versioning_in_the_pact_broker_ru#Рекомендации).

## Когда создаются ветки?

Ветки создаются и связываются с версией автоматически при публикации pact'ов и результатов верификации. Проверьте
раздел [Поддержка](#support), чтобы узнать, поддерживает ли ваша библиотека эту возможность.

## Где используются ветки?

Ветки используются при выборе pact'ов, которые провайдер должен верифицировать, с
помощью селекторов версий потребителя. Обычно провайдер
верифицирует pact'ы из основной ветки потребителя. Также ветки участвуют в
расчёте pending-статуса и
определении WIP pact'ов.

## Автоматическое создание ветки из первого тега

Для упрощения миграции предусмотрен параметр [use_first_tag_as_branch](https://github.com/pact-foundation/pact_broker/blob/master/docs/configuration.yml). При `true`, первый тег на
версии участника, у которой ещё нет ветки, будет интерпретироваться как имя ветки.

После настройки клиентов Pact на использование веток рекомендуется отключить опцию (`false`).

## Свойство mainBranch у pacticipant

С версии 2.82.0 ресурс `pacticipant` поддерживает свойство `mainBranch`. Используется для отображения версий в UI и
выбора ветки при срабатывании webhook'а `contract_requiring_verification_published`.

### Автоопределение main ветки

Если создаётся версия с веткой или тегом `develop`, `main` или `master`, свойство `mainBranch` будет установлено
автоматически. Кандидаты [настраиваются](https://github.com/pact-foundation/pact_broker/blob/master/docs/configuration.yml).

Отключение — через `auto_detect_main_branch: false`.

### Проверка main ветки

```bash
pact-broker describe-pacticipant --name Foo
```

### Установка main ветки вручную

```bash
pact-broker create-or-update-pacticipant --name Foo --main-branch dev
```

## Поддержка

Поддержка веток добавляется в библиотеки Pact поэтапно (на конец 2022 года):

> Рекомендуется использовать Pact CLI для публикации, т.к. не все клиенты поддерживают ветки напрямую (например,
> pact-net v4, pact4s).

* Pact Ruby — v1.59.0
* Dockerized pact-provider-verifier (Ruby) — v1.36.0
* Pact Python — v1.6.0
* Pact JS — верификация с v9.17.0 / публикация — [issue](https://github.com/pact-foundation/pact-js/issues/749)
* Pact Go — v1.6.6 ([issue](https://github.com/pact-foundation/pact-go/issues/184))
* Pact Rust — verifier v0.10.10 ([issue](https://github.com/pact-foundation/pact-reference/issues/151))
* Pact JVM — v4.1.39 / v4.3.12 / v4.4.0-beta.3 ([issue](https://github.com/pact-foundation/pact-jvm/issues/1454))
* Pact NET — [v4.x](https://github.com/pact-foundation/pact-net/blob/master/docs/upgrading-to-4#provider-tests) для
  v3 spec / TBC для ruby core ([issue](https://github.com/pact-foundation/pact-net/issues/327))
* Pact Scala — TBC ([issue](https://github.com/ITV/scala-pact/issues/230))
* Pact4s — v0.2.0 ([issue](https://github.com/jbwheatley/pact4s/issues/89))
* Pact PHP — v7.1.0 ([PR](https://github.com/pact-foundation/pact-php/pull/240))

## Миграция от тегов к веткам

См. раздел [Автоматическое создание ветки из тега](#Автоматическое-создание-ветки-из-первого-тега).

* Обновите Pact-клиент до последней версии — см. [поддержку](#Поддержка)
* Обновите Pact Broker до версии 2.82.0+
* Если основная ветка называется не `develop` / `main` /
  `master`, [установите её вручную](#Установка main ветки вручную)
* Передавайте ветку при публикации pact'ов
  и/или результатов верификации
* На стороне провайдера замените селекторы вида `{ "tag": "<branch_name>"}` на `{ "branch": "<branch_name>"}`

## FAQ

### Мы не используем feature-ветки. Нужно ли задавать ветку?

Да. Даже если используется только одна ветка, всё равно рекомендуется указывать ветку при публикации и настраивать
свойство `mainBranch`. Это поможет отличить корректно настроенный брокер от неинициализированного.
