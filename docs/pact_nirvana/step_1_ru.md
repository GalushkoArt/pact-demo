---
title: 1. Подготовьтесь — изучите Pact
---

Перед прочтением этого документа вам следует:

* иметь базовое понимание концепций как [контрактов, управляемых потребителем (consumer driven contracts)](https://martinfowler.com/articles/consumerDrivenContracts.html), так и Pact,
* прочитать [Обзор Pact Broker](../pact_broker/overview_ru)
* ознакомиться с разделом о [версионировании в Pact Broker](../getting_started/versioning_in_the_pact_broker_ru)
* пройти воркшоп по CI/CD

Перед тем как настраивать Pact в собственной среде, полезно понять, как может выглядеть рабочий конвейер (pipeline).

Прохождение [воркшопа по CI/CD с Pact](https://docs.pactflow.io/docs/workshops/ci-cd/) даст вам хорошее представление о том,
как клиентские библиотеки Pact взаимодействуют с Pact Broker и какое место эти взаимодействия занимают в процессе релиза.

> Обратите внимание: если вы используете собственный экземпляр open source Pact Broker, он не поддерживает секреты и
> не имеет пользовательского интерфейса для управления [webhooks](https://docs.pact.io/pact_broker/webhooks).
> Вам придётся использовать API или HAL Browser для создания webhook, а ваш CI-токен придётся хранить в открытом виде
> в webhook. Документацию по API для webhook вы найдёте [здесь](https://docs.pact.io/pact_broker/advanced_topics/api_docs/webhooks)
