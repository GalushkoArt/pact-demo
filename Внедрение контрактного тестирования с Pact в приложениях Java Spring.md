# Внедрение контрактного тестирования с Pact в приложениях Java Spring

## Введение

Контрактное тестирование — это важная техника для обеспечения надежной интеграции между микросервисами. В отличие от традиционного сквозного тестирования, контрактное тестирование проверяет, что сервисы могут корректно взаимодействовать без необходимости одновременного развертывания всех сервисов. Этот подход особенно ценен в микросервисных архитектурах, где команды работают независимо и должны обеспечивать правильную интеграцию своих сервисов.

Эта статья представляет собой подробное руководство по внедрению контрактного тестирования с использованием Pact в приложениях Java Spring. Мы рассмотрим как сторону потребителя, так и сторону поставщика, выделим хорошие и плохие шаблоны реализации и предоставим практические детали конфигурации для команд, использующих общий Pact Broker.

## Что такое контрактное тестирование?

Контрактное тестирование — это техника тестирования точки интеграции путем проверки каждого приложения изолированно, чтобы убедиться, что сообщения, которые оно отправляет или получает, соответствуют общему пониманию, задокументированному в "контракте". Для HTTP-приложений эти сообщения представляют собой HTTP-запросы и ответы.

Pact — это инструмент контрактного тестирования, ориентированный на потребителя и основанный на коде. Контракт генерируется во время выполнения автоматизированных тестов потребителя, а затем проверяется поставщиком. Этот подход гарантирует, что тестируются только те части API, которые фактически используются потребителями, позволяя поставщикам изменять неиспользуемое поведение без нарушения тестов.

## Почему стоит использовать Pact для контрактного тестирования?

- **Более быстрые циклы обратной связи**: Раннее обнаружение проблем интеграции без развертывания всех сервисов
- **Независимая разработка**: Команды могут работать над своими сервисами, не дожидаясь других
- **Документация**: Контракты служат живой документацией взаимодействия сервисов
- **Уверенность в развертывании**: Проверка того, что изменения не нарушат работу существующих потребителей
- **Снижение потребности в сквозном тестировании**: Меньше сложных, хрупких и медленных интеграционных тестов

## Настройка Pact в приложении Spring Boot

### Зависимости

Для приложения Spring Boot вам нужно добавить соответствующие зависимости Pact в файл сборки.

Для Gradle (Потребитель):

```gradle
dependencies {
    // Pact для тестирования потребителя
    testImplementation 'au.com.dius.pact.consumer:junit5:4.6.17'
}

pact {
    publish {
        pactBrokerUrl = 'http://your-pact-broker-url'
        pactBrokerUsername = 'username'
        pactBrokerPassword = 'password'
        tags = ['dev', 'test']
        version = project.version
    }
}
```

Для Gradle (Поставщик):

```gradle
dependencies {
    // Pact для проверки поставщика
    testImplementation 'au.com.dius.pact.provider:junit5spring:4.6.17'
    testImplementation 'au.com.dius.pact.provider:junit5:4.6.17'
    testImplementation 'au.com.dius.pact.provider:spring:4.6.17'
}

pact {
    serviceProviders {
        'your-provider-name' {
            port = 8080
            // Получение контрактов из брокера
            hasPactsFromPactBroker('http://your-pact-broker-url', 
                authentication: ['Basic', 'username', 'password'])
        }
    }
}
```

Для Maven вы бы включили аналогичные зависимости в файл pom.xml.

## Написание контрактных тестов потребителя

Контрактные тесты потребителя определяют ожидания, которые потребитель имеет от поставщика. Эти тесты используют макет поставщика для проверки того, что потребитель правильно взаимодействует с API поставщика.

### Пример хорошей реализации

Вот пример хорошо реализованного контрактного теста потребителя из репозитория:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "price-service.base-url=http://localhost:9090",
        "price-service.username=admin",
        "price-service.password=password",
})
@MockServerConfig(port = "9090")
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
@PactTestFor(providerName = "price-service-provider-price")
public class PriceApiPactTest {
    @Autowired
    private PricesApi pricesApi;

    @BeforeAll
    public static void setup() {
        System.setProperty("http.keepAlive", "false");
    }

    @Pact(consumer = "new-price-service-consumer")
    public RequestResponsePact getPricePact(PactDslWithProvider builder) {
        var timestamp = Instant.now().toString();
        return builder
                .given("price with ID exists")
                .uponReceiving("a request for price with ID AAPL")
                .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(newJsonBody(body -> {
                    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                    body.decimalType("bidPrice", 175.50);
                    body.decimalType("askPrice", 175.75);
                    body.stringType("lastUpdated", timestamp);
                }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPricePact")
    void testGetPrice() {
        PriceDto price = pricesApi.getPrice("AAPL");

        assertThat(price.getInstrumentId()).isEqualTo("AAPL");
        assertThat(price.getBidPrice()).isNotNull();
        assertThat(price.getAskPrice()).isNotNull();
        assertThat(price.getLastUpdated()).isNotNull();
    }
}
```

### Ключевые лучшие практики в тестах потребителя

1. **Используйте сопоставители типов вместо точных значений**: Обратите внимание, как используется `decimalType` вместо точных значений для цен. Это делает контракт более гибким.

2. **Определяйте состояния поставщика**: Условие `.given("price with ID exists")` определяет состояние, в котором должен находиться поставщик для прохождения теста.

3. **Используйте содержательные описания**: `.uponReceiving("a request for price with ID AAPL")` четко описывает взаимодействие.

4. **Тестируйте сценарии ошибок**: Включайте тесты для ответов с ошибками, например, 404 Not Found:

```java
@Pact(consumer = "new-price-service-consumer")
public RequestResponsePact getPriceNotFoundPact(PactDslWithProvider builder) {
    return builder
            .given("price with ID UNKNOWN does not exist")
            .uponReceiving("a request for price with ID UNKNOWN")
            .path("/prices/UNKNOWN")
            .method("GET")
            .willRespondWith()
            .status(404)
            .toPact();
}

@Test
@PactTestFor(pactMethod = "getPriceNotFoundPact")
void testGetPriceNotFound() {
    assertThatThrownBy(() -> pricesApi.getPrice("UNKNOWN"))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
}
```

5. **Обрабатывайте аутентификацию**: Для защищенных конечных точек включайте заголовки аутентификации:

```java
private static final String USERNAME = "admin";
private static final String PASSWORD = "password";
private static final String AUTH_HEADER = "Basic " + 
    Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());

@Pact(consumer = "price-service-consumer")
public RequestResponsePact savePricePact(PactDslWithProvider builder) {
    var timestamp = Instant.now().toString();

    return builder
            .given("price can be saved")
            .uponReceiving("an authenticated request to save price for AAPL")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("POST")
            .headers("Authorization", AUTH_HEADER)
            .headers("Content-Type", "application/json")
            .body(newJsonBody(body -> {
                body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                body.decimalType("bidPrice", 176.50);
                body.decimalType("askPrice", 176.75);
                body.stringType("lastUpdated", timestamp);
            }).build())
            .willRespondWith()
            .status(200)
            .body(newJsonBody(body -> {
                body.stringType("instrumentId", "AAPL");
                body.decimalType("bidPrice", 176.50);
                body.decimalType("askPrice", 176.75);
                body.stringType("lastUpdated", timestamp);
            }).build())
            .toPact();
}
```

### Плохие шаблоны реализации, которых следует избегать

1. **Использование точных значений вместо сопоставителей типов**:

```java
// ПЛОХО: Использование точных значений делает контракт слишком строгим
.body("{\"bidPrice\": 175.50, \"askPrice\": 175.75}")

// ХОРОШО: Использование сопоставителей типов делает контракт более гибким
.body(newJsonBody(body -> {
    body.decimalType("bidPrice", 175.50);
    body.decimalType("askPrice", 175.75);
}).build())
```

2. **Отсутствие состояний поставщика**:

```java
// ПЛОХО: Состояние поставщика не определено
.uponReceiving("a request for price with ID AAPL")

// ХОРОШО: Состояние поставщика четко определено
.given("price with ID exists")
.uponReceiving("a request for price with ID AAPL")
```

3. **Слишком конкретные утверждения**:

```java
// ПЛОХО: Утверждение точных значений делает тесты хрупкими
assertThat(price.getBidPrice()).isEqualTo(new BigDecimal("175.50"));

// ХОРОШО: Утверждение наличия значений, а не их точного содержания
assertThat(price.getBidPrice()).isNotNull();
```

4. **Отсутствие тестирования сценариев ошибок**:

```java
// ПЛОХО: Тестирование только успешного сценария
@Test
void testGetPrice() {
    PriceDto price = pricesApi.getPrice("AAPL");
    assertThat(price).isNotNull();
}

// ХОРОШО: Тестирование как успешного сценария, так и сценариев ошибок
@Test
void testGetPriceNotFound() {
    assertThatThrownBy(() -> pricesApi.getPrice("UNKNOWN"))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
}
```

## Написание контрактных тестов поставщика

Контрактные тесты поставщика проверяют, что поставщик может выполнить ожидания, определенные в контрактах потребителя. Эти тесты используют фактическую реализацию поставщика и проверяют ее на соответствие контрактам, полученным из Pact Broker.

### Пример хорошей реализации

Вот пример хорошо реализованного контрактного теста поставщика:

```java
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, value = {
        "admin.username=admin",
        "admin.password=password",
})
@Provider("price-service-provider-price")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", 
                                         password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
public class PriceServiceProviderPricePactTest {
    private static final Logger log = LoggerFactory.getLogger(PriceServiceProviderPricePactTest.class);
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private PriceJpaRepository priceJpaRepository;
    
    private static final String username = "admin";
    private static final String password = "password";
    private static final String AUTH_HEADER = "Basic " + 
        Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .tag("prod")
                .latestTag("dev");
    }

    @BeforeEach
    void setUp(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void testTemplate(PactVerificationContext context, HttpRequest request) {
        replaceAuthHeader(request);
        context.verifyInteraction();
    }

    private void replaceAuthHeader(HttpRequest request) {
        if (request.containsHeader("Authorization")) {
            request.removeHeaders("Authorization");
            request.addHeader("Authorization", AUTH_HEADER);
        } else {
            log.warn("Request does not contain Authorization header. Skipping authorization header replacement.");
        }
    }

    @State(value = "price with ID exists", action = StateChangeAction.SETUP)
    @Transactional
    public Map<String, String> priceWithIdExists() {
        // Очистка существующих данных для этого ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", 
            id -> RandomStringUtils.secure().nextAlphanumeric(4));
        priceJpaRepository.findById(instrumentId).ifPresent(price -> priceJpaRepository.delete(price));

        // Создание тестовых данных
        PriceEntity apple = PriceEntity.builder()
                .instrumentId(instrumentId)
                .bidPrice(new BigDecimal("175.50"))
                .askPrice(new BigDecimal("175.75"))
                .lastUpdated(Instant.now())
                .build();

        priceJpaRepository.save(apple);
        return parameters;
    }

    @State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
    @Transactional
    public void priceWithIdExistsCleanup(Map<String, String> parameters) {
        log.debug("Cleaning up price with ID: {}", parameters.get("instrumentId"));
        Optional.ofNullable(parameters.get("instrumentId"))
            .ifPresent(id -> priceJpaRepository.deleteById(id));
    }

    @State("price with ID UNKNOWN does not exist")
    @Transactional
    public void priceWithIdUnknownDoesNotExist() {
        // Убедиться, что цена не существует
        priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
    }
}
```

### Ключевые лучшие практики в тестах поставщика

1. **Используйте обработчики состояний для настройки тестовых данных**: Аннотации `@State` определяют методы, которые настраивают поставщика в требуемом состоянии для каждого взаимодействия.

2. **Очищайте после тестов**: Действие `StateChangeAction.TEARDOWN` гарантирует, что тестовые данные очищаются после каждого теста.

3. **Обрабатывайте аутентификацию**: Метод `replaceAuthHeader` гарантирует, что заголовки аутентификации правильно установлены для каждого запроса.

4. **Используйте селекторы версий**: Метод `consumerVersionSelectors` определяет, какие версии потребителя проверять.

5. **Мокайте на уровне репозитория**: Вместо мокирования контроллеров или сервисов, мокируйте на уровне репозитория или используйте базу данных в памяти для более реалистичных тестов.

### Плохие шаблоны реализации, которых следует избегать

1. **Мокирование на уровне контроллера**:

```java
// ПЛОХО: Мокирование на уровне контроллера
@MockBean
private PriceController priceController;

@State("price with ID exists")
public void priceWithIdExists() {
    when(priceController.getPrice("AAPL")).thenReturn(new PriceDto(...));
}

// ХОРОШО: Настройка тестовых данных на уровне репозитория
@Autowired
private PriceJpaRepository priceJpaRepository;

@State("price with ID exists")
public void priceWithIdExists() {
    priceJpaRepository.save(PriceEntity.builder()
            .instrumentId("AAPL")
            .bidPrice(new BigDecimal("175.50"))
            .askPrice(new BigDecimal("175.75"))
            .lastUpdated(Instant.now())
            .build());
}
```

2. **Отсутствие обработки аутентификации**:

```java
// ПЛОХО: Отсутствие обработки аутентификации
@TestTemplate
@ExtendWith(PactVerificationInvocationContextProvider.class)
void testTemplate(PactVerificationContext context) {
    context.verifyInteraction();
}

// ХОРОШО: Обработка аутентификации
@TestTemplate
@ExtendWith(PactVerificationInvocationContextProvider.class)
void testTemplate(PactVerificationContext context, HttpRequest request) {
    replaceAuthHeader(request);
    context.verifyInteraction();
}
```

3. **Отсутствие очистки тестовых данных**:

```java
// ПЛОХО: Отсутствие очистки тестовых данных
@State("price with ID exists")
public void priceWithIdExists() {
    priceJpaRepository.save(new PriceEntity(...));
}

// ХОРОШО: Очистка тестовых данных
@State(value = "price with ID exists", action = StateChangeAction.SETUP)
public void priceWithIdExists() {
    priceJpaRepository.save(new PriceEntity(...));
}

@State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
public void priceWithIdExistsCleanup() {
    priceJpaRepository.deleteById("AAPL");
}
```

## Настройка Pact Broker

Pact Broker — это центральный репозиторий для контрактов. Он позволяет потребителям публиковать контракты, а поставщикам получать их для проверки.

### Конфигурация в build.gradle (Потребитель)

```gradle
pact {
    publish {
        pactBrokerUrl = 'http://localhost:9292'
        pactBrokerUsername = 'pact'
        pactBrokerPassword = 'pact'
        tags = ['dev', 'test']
        version = project.version
    }
}
```

### Конфигурация в build.gradle (Поставщик)

```gradle
pact {
    serviceProviders {
        'price-service-provider-price' {
            port = 8080
            // Получение контрактов из брокера
            hasPactsFromPactBroker('http://localhost:9292', 
                authentication: ['Basic', 'pact', 'pact'])
        }
    }
    broker {
        pactBrokerUrl = 'http://localhost:9292/'
        retryCountWhileUnknown = 3
        retryWhileUnknownInterval = 10 // 10 секунд между повторными попытками
    }
}

test {
    // Эти свойства должны быть установлены в процессе тестирования JVM
    systemProperty("pact.provider.version", 
        System.getenv("GIT_COMMIT") == null ? version : System.getenv("GIT_COMMIT"))
    systemProperty("pact.provider.branch", 
        System.getenv("GIT_BRANCH") == null ? "" : System.getenv("GIT_BRANCH"))
    systemProperty("pact.verifier.publishResults", "true")
    
    // Контракты в разработке
    systemProperty("pactbroker.includeWipPactsSince", 
        java.time.LocalDate.now().minusMonths(6).format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")))
}
```

## Интеграция с CI/CD

Интеграция Pact в ваш CI/CD конвейер имеет решающее значение для обеспечения проверки контрактов перед развертыванием.

### Пример конфигурации .gitlab-ci.yml

```yaml
stages:
  - build
  - test
  - deploy

variables:
  PACT_URL: "http://pact-broker.example.com"
  PACT_BROKER_USER: "pact"
  PACT_BROKER_PASSWORD: "pact"

consumer_test:
  stage: test
  script:
    - ./gradlew :price-service-consumer:test
    - ./gradlew :price-service-consumer:pactPublish

provider_test:
  stage: test
  script:
    - ./gradlew :price-service-provider:test
  environment:
    name: test
```

## Лучшие практики для контрактного тестирования с Pact

На основе нашего анализа репозитория и исследования лучших практик, вот ключевые рекомендации по внедрению контрактного тестирования с Pact:

1. **Сосредоточьтесь на фактическом API, а не на коде**: Вызывайте фактическую конечную точку, чтобы увидеть запрос и ответ перед написанием контрактного теста.

2. **Пишите контракт так, чтобы он отражал фактическую интеграцию, которую вы ожидаете**: Согласуйте контракт с вашим поставщиком перед слиянием.

3. **Будьте гибкими в своих ожиданиях**: Используйте сопоставители (регулярные выражения, типы, форматы дат) вместо точных значений, когда это возможно.

4. **Используйте меньше констант и общих компонентов в тестах**: Избегайте ненужной связности в ваших тестах.

5. **Используйте меньше классов производственной модели в вашем тестовом коде**: Полагайтесь на простые JSON и DSL библиотеки вместо этого.

6. **Всегда проверяйте контракт на стороне поставщика**: Каждый контракт должен быть проверен на стороне поставщика, чтобы связать потребителя и поставщика.

7. **Мокируйте как можно глубже на стороне поставщика**: Мокируйте на уровне репозитория или вставляйте данные в вашу базу данных в тестах.

8. **Тестируйте сценарии ошибок**: Включайте тесты для ответов с ошибками, таких как 404 Not Found, 401 Unauthorized и т.д.

9. **Обрабатывайте аутентификацию**: Убедитесь, что заголовки аутентификации правильно установлены для защищенных конечных точек.

10. **Очищайте тестовые данные**: Используйте `StateChangeAction.TEARDOWN` для очистки тестовых данных после каждого теста.

11. **Используйте селекторы версий**: Определите, какие версии потребителя проверять, используя селекторы версий.

12. **Интегрируйтесь с CI/CD**: Убедитесь, что контракты проверяются перед развертыванием.

## Заключение

Контрактное тестирование с Pact — это мощная техника для обеспечения надежной интеграции между микросервисами. Следуя лучшим практикам, изложенным в этой статье, вы можете реализовать эффективные контрактные тесты, которые обеспечат уверенность в интеграции ваших сервисов.

Помните, что контрактное тестирование не является заменой всех других типов тестирования, но оно может значительно снизить потребность в сложных и хрупких сквозных тестах. Используйте его как часть комплексной стратегии тестирования, которая включает модульные тесты, интеграционные тесты и сквозные тесты, где это уместно.

## Ссылки

1. [Документация Pact](https://docs.pact.io/)
2. [Лучшие практики для написания контрактных тестов с Pact в JVM стеке](https://dev.to/art_ptushkin/best-practices-for-writing-contract-tests-with-pact-in-jvm-stack-124l)
3. [Контракты, управляемые потребителем, с Pact](https://www.baeldung.com/pact-junit-consumer-driven-contracts)
4. [Контрактное тестирование с использованием Pact](https://medium.com/@santhoshshetty58/contract-testing-using-pact-a0caddc08bed)
