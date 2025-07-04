# Демонстрация контрактного тестирования Pact

Этот проект демонстрирует контрактное тестирование на основе Pact в архитектуре микросервисов, сосредоточенное на домене
сервиса цен с коммуникацией через REST API, gRPC и Kafka между поставщиком и потребительскими сервисами.

## Содержание

1. [Обзор проекта](#обзор-проекта)
2. [Архитектура и модули](#архитектура-и-модули)
3. [Предварительные требования](#предварительные-требования)
4. [Начало работы](#начало-работы)
5. [Рабочий процесс контрактного тестирования](#рабочий-процесс-контрактного-тестирования)
6. [Детали модулей](#детали-модулей)
7. [Лучшие практики и руководства](#лучшие-практики-и-руководства)
8. [Продвинутые темы](#продвинутые-темы)
9. [Устранение неполадок](#устранение-неполадок)
10. [Дополнительные ресурсы](#дополнительные-ресурсы)

## Обзор проекта

В современных архитектурах микросервисов обеспечение надежной коммуникации между сервисами имеет критическое значение.
Этот демонстрационный проект показывает, как реализовать контрактное тестирование с использованием Pact — инструмента
контрактного тестирования, управляемого потребителем, который помогает командам обнаруживать проблемы интеграции на
ранней стадии цикла разработки.

Контрактное тестирование с Pact позволяет командам:

- **Разрабатывать независимо**: Команды могут разрабатывать и развертывать сервисы независимо, сохраняя уверенность в
  интеграции
- **Обнаруживать критические изменения рано**: Проверка контрактов выполняется в CI/CD конвейерах для обнаружения
  проблем совместимости до продакшена
- **Снижать сложность интеграционного тестирования**: Устранить необходимость в сложных общих тестовых средах
- **Повышать уверенность в развертывании**: Проверять, что изменения сервиса не нарушат существующие интеграции

## Архитектура и модули

Эта демонстрация включает три основных модуля, которые демонстрируют различные аспекты контрактного тестирования:

### Архитектура сервисов

```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│                     │    │                     │    │                     │
│ price-service-      │    │ new-price-service-  │    │ price-service-      │
│ consumer            │    │ consumer            │    │ provider            │
│                     │    │                     │    │                     │
│ • REST API Client   │    │ • REST API Client   │    │ • REST API Server   │
│ • gRPC Client       │    │ • Упрощенный        │    │ • gRPC Server       │
│ • Kafka Consumer    │    │   потребитель       │    │ • Kafka Producer    │
│                     │    │                     │    │                     │
└─────────────────────┘    └─────────────────────┘    └─────────────────────┘
           │                           │                           ▲
           │                           │                           │
           └───────────────────────────┼───────────────────────────┘
                                       │
                                       │
                              ┌─────────────────────┐
                              │                     │
                              │ Pact Broker         │
                              │                     │
                              │ • Хранение          │
                              │   контрактов        │
                              │ • Проверки          │
                              │ • Можно ли          │
                              │   развернуть?       │
                              │   (can-i-depoy)     │
                              │                     │
                              └─────────────────────┘
```

### Протоколы коммуникации

Демонстрация охватывает несколько протоколов коммуникации, обычно используемых в микросервисах:

| Протокол     | Случай использования          | Модули потребителей    | Модуль поставщика      |
|--------------|-------------------------------|------------------------|------------------------|
| **REST API** | Синхронный запрос-ответ       | Оба потребителя        | price-service-provider |
| **gRPC**     | Высокопроизводительный RPC    | price-service-consumer | price-service-provider |
| **Kafka**    | Асинхронный обмен сообщениями | price-service-consumer | price-service-provider |

## Предварительные требования

- **Java 17 или выше**: Требуется для запуска приложений Spring Boot
- **Gradle 8.0 или выше**: Инструмент сборки для компиляции и запуска тестов
- **Docker и Docker Compose**: Для запуска Pact Broker и баз данных PostgreSQL
- **Git**: Для клонирования репозитория

## Начало работы

### 1. Клонирование репозитория

```bash
git clone https://github.com/GalushkoArt/pact-demo.git
cd pact-demo
```

### 2. Запуск инфраструктурных сервисов

Запустите PostgreSQL и Pact Broker с помощью Docker Compose:

```bash
# Используя Makefile (рекомендуется)
make docker-start

# Или напрямую с docker-compose
docker-compose up -d
```

Это запустит:

- **PostgreSQL для приложения**: Порт 5432


- **PostgreSQL для тестов**: Порт 5433
- **Pact Broker**: Порт 9292

Pact Broker будет доступен по адресу [http://localhost:9292](http://localhost:9292) с учетными данными:

- Имя пользователя: `pact`
- Пароль: `pact`

### 3. Проверка настройки

Убедитесь, что все сервисы запущены:

```bash
# Проверка Docker контейнеров
docker-compose ps

# Проверка доступности Pact Broker
curl -u pact:pact http://localhost:9292/
```

### 4. Запуск полного рабочего процесса

Выполните полный рабочий процесс контрактного тестирования:

```bash
# Запуск полной демонстрации
make full-workflow
```

Эта команда выполнит все шаги, описанные в разделе рабочего процесса ниже.

## Рабочий процесс контрактного тестирования

Этот раздел демонстрирует полный рабочий процесс контрактного тестирования с использованием команд из Makefile. Следуйте
этим шагам, чтобы понять, как работает контрактное тестирование Pact на практике.

### Шаг 1: Запуск инфраструктуры

```bash
make docker-start
```

Запускает Docker контейнеры в отсоединенном режиме и ждет их готовности.

### Шаг 2: Тесты потребителей и генерация контрактов

```bash
make consumer-tests
```

Эта команда:

- Запускает тесты для `price-service-consumer`
- Генерирует файлы контрактов Pact в `price-service-consumer/build/pacts/`
- Публикует контракты в Pact Broker

**Просмотр результатов**: Откройте [http://localhost:9292/](http://localhost:9292/) для просмотра опубликованных
контрактов.

### Шаг 3: Проверка поставщика

```bash
make pact-tests
```

Запускает тесты проверки поставщика, которые:

- Загружают контракты из Pact Broker
- Проверяют, что поставщик может выполнить все контракты потребителей
- Тестируют взаимодействия REST API, gRPC и Kafka
- Публикуют результаты проверки обратно в брокер

### Шаг 4: Проверки безопасности развертывания

```bash
make canideploy-price
make canideploy-orderbook  
make canideploy-consumer
```

Эти команды проверяют, можно ли безопасно развернуть сервисы без нарушения контрактов. Должны вернуть
`Computer says yes \o/`.

### Шаг 5: Введение нового потребителя

```bash
make new-consumer-tests
```

Демонстрирует добавление нового потребителя:

- Запускает тесты для `new-price-service-consumer`
- Генерирует и публикует новые контракты
- Показывает, как новые потребители интегрируются в существующее контрактное тестирование

### Шаг 6: Проверка совместимости развертывания

```bash
# Проверка orderbook (должна пройти - новый потребитель его не использует)
make canideploy-orderbook

# Проверка сервиса цен (должна изначально не пройти)
make canideploy-price
```

Проверка сервиса цен не проходит, потому что поставщик еще не проверил контракты нового потребителя.

### Шаг 7: Завершение цикла проверки

```bash
# Повторный запуск проверки поставщика для включения новых контрактов
make pact-tests

# Теперь все проверки развертывания должны пройти
make canideploy-price
make canideploy-orderbook
make canideploy-new-consumer
```

Все сервисы теперь должны показывать `Computer says yes \o/` для развертывания.

## Детали модулей

### price-service-provider

**Назначение**: Реализует API поставщика, который обслуживает данные о ценах и книге заказов через несколько протоколов.

**Ключевые особенности**:

- **REST API**: Предоставляет HTTP конечные точки для операций с ценами (GET, POST, DELETE)
- **gRPC сервис**: Высокопроизводительная реализация сервиса цен
- **Kafka Producer**: Публикует обновления цен в топики Kafka (JSON и protobuf форматы сообщений)
- **Мульти-провайдерная конфигурация**: Отдельная проверка Pact для различных аспектов сервиса
- **Аутентификация**: Защищенные конечные точки с базовой аутентификацией
- **Интеграция с базой данных**: PostgreSQL с JPA для постоянного хранения данных

**Контрактные тесты Pact**:

- **Проверка поставщика**: Тесты расположены в `src/test/java/.../pact/`
- **Состояния поставщика**: Динамическое управление состоянием с настройкой/очисткой
- **Поддержка мульти-протоколов**: Отдельная проверка для REST, gRPC и Kafka (JSON и protobuf форматы сообщений)
- **Обработка аутентификации**: Автоматическая замена учетных данных для тестовых сценариев
- **Тестирование сценариев ошибок**: Комплексная проверка условий ошибок

**Конфигурация сборки**:

- **Плагин Pact**: `au.com.dius.pact` версия 4.6.17
- **Несколько поставщиков сервисов**: конфигурации price, orderbook и gRPC
- **Интеграция с брокером**: Автоматическое получение контрактов и публикация результатов
- **Генерация OpenAPI**: Автоматическая генерация интерфейса API
- **Поддержка gRPC**: Компиляция Protobuf и генерация gRPC сервиса

### price-service-consumer

**Назначение**: Комплексный клиент, который потребляет API сервиса цен через несколько протоколов коммуникации.

**Ключевые особенности**:

- **REST API клиент**: Сгенерированный OpenAPI клиент для HTTP операций
- **gRPC клиент**: Типобезопасная реализация gRPC клиента
- **Kafka Consumer**: Асинхронная обработка сообщений (JSON и protobuf форматы сообщений)
- **Аутентификация**: Настраиваемая аутентификация для защищенных конечных точек
- **Обработка ошибок**: Комплексная обработка сценариев ошибок

**Контрактные тесты Pact**:

- **Определение контрактов потребителя**: Тесты расположены в `src/test/java/.../client/`
- **Контракты мульти-протоколов**: Определения контрактов REST, gRPC и Kafka (JSON и protobuf форматы сообщений)
- **Гибкие матчеры**: Сопоставление на основе типов для надежных контрактов
- **Использование состояний поставщика**: Динамические параметры состояния для гибкого тестирования
- **Покрытие сценариев ошибок**: Тестирование условий ошибок 404, 401 и других
- **Тестирование аутентификации**: Как успешные, так и неудачные сценарии аутентификации

**Конфигурация сборки**:

- **Плагин Pact**: Контрактное тестирование потребителя и публикация
- **Генерация OpenAPI клиента**: Автоматическая генерация клиентского кода
- **Поддержка gRPC**: Компиляция Protobuf для gRPC клиентов
- **Интеграция Kafka**: Spring Kafka для обработки сообщений
- **Публикация контрактов**: Автоматическая публикация в Pact Broker

### new-price-service-consumer

**Назначение**: Упрощенный потребитель, демонстрирующий, как новые сервисы интегрируются в существующие рабочие процессы
контрактного тестирования.

**Ключевые особенности**:

- **Только REST API**: Сосредоточен на потреблении сервиса цен на основе HTTP
- **Упрощенная архитектура**: Демонстрирует основные паттерны контрактного тестирования
- **Независимая разработка**: Показывает, как новые потребители могут разрабатываться независимо
- **Эволюция контрактов**: Иллюстрирует совместимость и эволюцию контрактов

**Контрактные тесты Pact**:

- **Базовое определение контрактов**: Основные паттерны контрактов для новых потребителей
- **Интеграция состояний поставщика**: Повторное использование существующих состояний поставщика
- **Типобезопасные контракты**: Демонстрирует правильное использование матчеров
- **Обработка ошибок**: Базовое покрытие сценариев ошибок

**Конфигурация сборки**:

- **Минимальные зависимости**: Сосредоточен на основных компонентах контрактного тестирования
- **Интеграция OpenAPI**: Генерация клиента для потребления REST API
- **Публикация контрактов**: Интеграция с существующим рабочим процессом Pact Broker

## Лучшие практики и руководства

Этот репозиторий включает комплексные руководства по лучшим практикам как для поставщика, так и для потребительской
стороны контрактного тестирования:

### 📖 [Руководство по лучшим практикам Pact Provider](pact-provider-best-practices_ru.md)

Комплексное руководство, охватывающее:

- **Архитектура тестов поставщика**: Настройка, конфигурация и интеграция Spring Boot
- **Управление состояниями поставщика**: Динамические состояния, настройка/очистка и потокобезопасность
- **Обработка аутентификации**: Замена учетных данных и тестирование безопасности
- **Тестирование мульти-протоколов**: Паттерны проверки REST, gRPC и Kafka


- **Оптимизация производительности**: Параллельное выполнение, кэширование и управление ресурсами
- **Интеграция CI/CD**: Интеграция конвейеров и шлюзы развертывания
- **Устранение неполадок**: Стратегии отладки и решение общих проблем

## Руководства

- [Руководство по контрактному тестированию REST API с Pact](docs/reference_guide/pact-rest-examples_ru.md)
- [Руководство по контрактному тестированию gRPC API с Pact](docs/reference_guide/pact-grpc-examples_ru.md)

## Продвинутые темы

### Стратегии контрактного тестирования

**Контракты, управляемые потребителем**: Потребители определяют, что им нужно от поставщиков, обеспечивая, что изменения
поставщика не нарушают фактические требования потребителя.

**Состояния поставщика**: Динамическая настройка тестовых данных, которая позволяет поставщикам создавать специфические
условия для проверки контрактов.

**Эволюция контрактов**: Стратегии для развития контрактов со временем при сохранении обратной совместимости.

### Поддержка мульти-протоколов

**Тестирование REST API**: Стандартное контрактное тестирование на основе HTTP с комплексным покрытием сценариев ошибок.

**Контрактное тестирование gRPC**: Тестирование бинарного протокола с валидацией схемы protobuf и типобезопасностью.

**Тестирование сообщений Kafka**: Асинхронное контрактное тестирование сообщений с валидацией сериализации.

### Интеграция CI/CD

**Тестирование на основе веток**: Проверка контрактов против специфических версий потребителей на основе веток
разработки.

**Шлюзы развертывания**: Использование результатов проверки контрактов для управления решениями о развертывании.

**Можно ли развернуть?**: Автоматическая проверка совместимости перед развертыванием сервисов.

## Устранение неполадок

### Общие проблемы

**Конфликты портов**: Убедитесь, что порты 5432, 5433 и 9292 доступны для PostgreSQL и Pact Broker.

**Проблемы с Docker**: Проверьте, что Docker и Docker Compose установлены и работают.

**Сбои проверки контрактов**: Проверьте состояния поставщика и убедитесь, что настройка тестовых данных корректна.

**Ошибки аутентификации**: Проверьте, что учетные данные аутентификации совпадают между тестами потребителя и
конфигурацией поставщика.

### Советы по отладке

**UI Pact Broker**: Используйте [http://localhost:9292](http://localhost:9292) для инспекции контрактов и результатов
проверки.

**Подробное логирование**: Включите отладочное логирование в конфигурациях тестов для детальной информации о выполнении.

**Файлы контрактов**: Изучите сгенерированные файлы контрактов в директориях `build/pacts/` для структуры контрактов.

**Состояния поставщика**: Проверьте, что настройка состояния поставщика создает ожидаемые условия тестовых данных.

### Получение помощи

**Логи**: Проверьте логи приложения и вывод тестов для детальной информации об ошибках.

**Документация Pact**: Обратитесь к [официальной документации Pact](https://docs.pact.io/) для подробного руководства.

**Сообщество**: Присоединитесь к сообществу Pact для поддержки и обмена лучшими практиками.

## Дополнительные ресурсы

### Документация

Вы можете прочитать [Официальную документацию Pact](https://docs.pact.io/) или посмотреть снимок документации,
включенный в этот проект (2025.06.15).

**Рекомендуется к чтению** из папки [docs](docs):

- [Руководство по настройке CI/CD (aka Pact Nirvana)](docs/pact_nirvana_ru.md)
- [Убеди меня](docs/faq/convinceme_ru.md)
- [Версионирование в Pact Broker](docs/getting_started/versioning_in_the_pact_broker_ru.md)
- [Когда использовать Pact](docs/getting_started/what_is_pact_good_for_ru.md)

### Внешние ресурсы

- [Официальная документация Pact](https://docs.pact.io/)
- [Документация Pact JVM](https://github.com/pact-foundation/pact-jvm)
- [Руководство по интеграции Spring Boot и Pact](https://docs.pact.io/implementation_guides/jvm/provider/spring)
- [Контрактное тестирование, управляемое потребителем](https://martinfowler.com/articles/consumerDrivenContracts.html)
- [Стратегии тестирования микросервисов](https://martinfowler.com/articles/microservice-testing/)

### Сообщество и поддержка

- [GitHub Pact Foundation](https://github.com/pact-foundation)
- [Slack сообщество Pact](https://pact-foundation.slack.com/)
- [Stack Overflow - тег Pact](https://stackoverflow.com/questions/tagged/pact)
