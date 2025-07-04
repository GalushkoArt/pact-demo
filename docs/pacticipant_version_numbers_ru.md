---
title: Номера версий Pacticipant'ов
---

При публикации pact'а или результата проверки, ресурс связывается с определённой версией pacticipant'а (приложения), которая идентифицируется номером версии. Чтобы внести полную ясность — номер версии, указанный в URL pact'а или проверки, это **номер версии приложения**, а не версия самого pact'а. Версионирование содержимого pact'а происходит автоматически на стороне Broker'а и основывается на содержимом контракта. Кстати, не переживайте по поводу большого количества данных из-за публикации pact'а при каждой сборке — это нормальное поведение. В базу данных будет добавлена новая строка только если содержимое pact'а изменилось (так что избегайте случайных данных в ваших pact'ах).

Если бы я мог вернуться назад и переименовать этот параметр, я бы назвал его чем-то вроде "application codebase ref" или "application commit", а не "application version number". Чтобы максимально эффективно использовать Pact Broker, это значение должно быть либо **git SHA** (или аналогом для вашей системы контроля версий), либо **git-тегом**, либо включать SHA или тег как метаинформацию при использовании семантического версионирования, например: `1.2.456+405b31ec6`.  
Вы можете использовать семантические версии, но очень важно строго соблюдать описанные ниже требования, чтобы данные, на которых работает `can-i-deploy`, были надёжными. Git SHA — самый простой и безопасный способ соответствовать этим требованиям.

### Номер версии pacticipant'а должен:

* изменяться (почти) при каждой публикации pact'а;
* однозначно идентифицировать или быть способным однозначно указывать на коммит кодовой базы, который сгенерировал pact или результат проверки (это важно, чтобы, например, по тегу можно было получить `prod`-версию провайдера и [проверить «матрицу»](http://rea.tech/enter-the-pact-matrix-or-how-to-decouple-the-release-cycles-of-your-microservices/));
* существовать только в одной ветке кода (чтобы не получилось, что одна и та же версия pacticipant'а попадает в несколько git-веток);
* быть известен во время релиза (чтобы можно было передать его в `can-i-deploy` и убедиться в безопасности релиза).

### Хорошие примеры:

* `cefbfb4d4e1a53b8044cf399fee21033e603e5fc`
* `a86579910`
* `1.2.456+405b31ec6`

### Плохие примеры:

* `2.0.<номер_сборки>` (см. примечание ниже)
* `2.0.0` — если все части версии увеличиваются вручную (pact'ы из разных веток и коммитов могут перезаписывать друг друга)
* `1` — слишком общо
* Версия — это просто номер сборки из вашей CI-системы (при смене CI у вас начнётся новая нумерация, и произойдёт путаница)

Если вы всё же используете номер сборки в составе номера версии (например, `major.minor.<buildnumber>`), убедитесь, что версия соответствует конкретному коммиту, создав git-тег с таким номером версии для каждой сборки. Это в любом случае хорошая практика и позволяет в будущем получить именно ту версию провайдера, которую нужно [для проверки «матрицы»](http://rea.tech/enter-the-pact-matrix-or-how-to-decouple-the-release-cycles-of-your-microservices/).

Одно из преимуществ использования git SHA — возможность отправлять статус проверки pact'а обратно в репозиторий в виде commit-статуса.

## Сортировка

Версии сортируются по порядку создания, за исключением очень старых версий Pact Broker'а, где использовалась семантическая сортировка. Семантический порядок версий больше не поддерживается и не рекомендуется.
