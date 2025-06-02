# Implementing Contract Testing with Pact in Java Spring Applications

## Introduction

Contract testing is a crucial technique for ensuring reliable integrations between microservices. Unlike traditional end-to-end testing, contract testing verifies that services can communicate correctly without requiring all services to be deployed simultaneously. This approach is particularly valuable in microservice architectures where teams work independently and need to ensure their services integrate properly.

This article provides a comprehensive guide to implementing contract testing using Pact in Java Spring applications. We'll cover both consumer and provider sides, highlight good and bad implementation patterns, and provide practical configuration details for teams using a shared Pact Broker.

## What is Contract Testing?

Contract testing is a technique for testing an integration point by checking each application in isolation to ensure the messages it sends or receives conform to a shared understanding documented in a "contract." For HTTP-based applications, these messages are HTTP requests and responses.

Pact is a code-first, consumer-driven contract testing tool. The contract is generated during the execution of automated consumer tests and then verified by the provider. This approach ensures that only the parts of the API actually used by consumers are tested, allowing providers to change unused behavior without breaking tests.

## Why Use Pact for Contract Testing?

- **Faster feedback cycles**: Detect integration issues early without deploying all services
- **Independent development**: Teams can work on their services without waiting for others
- **Documentation**: Contracts serve as living documentation of service interactions
- **Confidence in deployments**: Verify that changes won't break existing consumers
- **Reduced need for end-to-end testing**: Fewer complex, brittle, and slow integration tests

## Setting Up Pact in a Spring Boot Application

### Dependencies

For a Spring Boot application, you'll need to add the appropriate Pact dependencies to your build file.

For Gradle (Consumer):

```gradle
dependencies {
    // Pact for consumer testing
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

For Gradle (Provider):

```gradle
dependencies {
    // Pact for provider verification
    testImplementation 'au.com.dius.pact.provider:junit5spring:4.6.17'
    testImplementation 'au.com.dius.pact.provider:junit5:4.6.17'
    testImplementation 'au.com.dius.pact.provider:spring:4.6.17'
}

pact {
    serviceProviders {
        'your-provider-name' {
            port = 8080
            // Get pacts from broker
            hasPactsFromPactBroker('http://your-pact-broker-url', 
                authentication: ['Basic', 'username', 'password'])
        }
    }
}
```

For Maven, you would include similar dependencies in your pom.xml file.

## Writing Consumer Contract Tests

Consumer contract tests define the expectations that a consumer has of a provider. These tests use a mock provider to verify that the consumer correctly interacts with the provider's API.

### Good Implementation Example

Here's an example of a well-implemented consumer contract test from the repository:

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

### Key Best Practices in Consumer Tests

1. **Use type matchers instead of exact values**: Notice how `decimalType` is used instead of exact values for prices. This makes the contract more flexible.

2. **Define provider states**: The `.given("price with ID exists")` clause defines a state that the provider must be in for the test to pass.

3. **Use meaningful descriptions**: `.uponReceiving("a request for price with ID AAPL")` clearly describes the interaction.

4. **Test error scenarios**: Include tests for error responses, like 404 Not Found:

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

5. **Handle authentication**: For secured endpoints, include authentication headers:

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

### Bad Implementation Patterns to Avoid

1. **Using exact values instead of type matchers**:

```java
// BAD: Using exact values makes the contract too strict
.body("{\"bidPrice\": 175.50, \"askPrice\": 175.75}")

// GOOD: Using type matchers makes the contract more flexible
.body(newJsonBody(body -> {
    body.decimalType("bidPrice", 175.50);
    body.decimalType("askPrice", 175.75);
}).build())
```

2. **Missing provider states**:

```java
// BAD: No provider state defined
.uponReceiving("a request for price with ID AAPL")

// GOOD: Provider state clearly defined
.given("price with ID exists")
.uponReceiving("a request for price with ID AAPL")
```

3. **Overly specific assertions**:

```java
// BAD: Asserting exact values makes tests brittle
assertThat(price.getBidPrice()).isEqualTo(new BigDecimal("175.50"));

// GOOD: Asserting presence of values, not exact content
assertThat(price.getBidPrice()).isNotNull();
```

4. **Not testing error scenarios**:

```java
// BAD: Only testing happy path
@Test
void testGetPrice() {
    PriceDto price = pricesApi.getPrice("AAPL");
    assertThat(price).isNotNull();
}

// GOOD: Testing both happy path and error scenarios
@Test
void testGetPriceNotFound() {
    assertThatThrownBy(() -> pricesApi.getPrice("UNKNOWN"))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
}
```

## Writing Provider Contract Tests

Provider contract tests verify that the provider can fulfill the expectations defined in the consumer contracts. These tests use the actual provider implementation and verify it against the contracts retrieved from the Pact Broker.

### Good Implementation Example

Here's an example of a well-implemented provider contract test:

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
        // Clear existing data for this ID
        var parameters = new HashMap<String, String>();
        var instrumentId = parameters.computeIfAbsent("instrumentId", 
            id -> RandomStringUtils.secure().nextAlphanumeric(4));
        priceJpaRepository.findById(instrumentId).ifPresent(price -> priceJpaRepository.delete(price));

        // Create test data
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
        // Ensure the price doesn't exist
        priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
    }
}
```

### Key Best Practices in Provider Tests

1. **Use state handlers to set up test data**: The `@State` annotations define methods that set up the provider in the required state for each interaction.

2. **Clean up after tests**: The `StateChangeAction.TEARDOWN` action ensures that test data is cleaned up after each test.

3. **Handle authentication**: The `replaceAuthHeader` method ensures that authentication headers are correctly set for each request.

4. **Use version selectors**: The `consumerVersionSelectors` method defines which consumer versions to verify against.

5. **Mock at the repository level**: Instead of mocking controllers or services, mock at the repository level or use an in-memory database to make tests more realistic.

### Bad Implementation Patterns to Avoid

1. **Mocking at the controller level**:

```java
// BAD: Mocking at the controller level
@MockBean
private PriceController priceController;

@State("price with ID exists")
public void priceWithIdExists() {
    when(priceController.getPrice("AAPL")).thenReturn(new PriceDto(...));
}

// GOOD: Setting up test data at the repository level
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

2. **Not handling authentication**:

```java
// BAD: Not handling authentication
@TestTemplate
@ExtendWith(PactVerificationInvocationContextProvider.class)
void testTemplate(PactVerificationContext context) {
    context.verifyInteraction();
}

// GOOD: Handling authentication
@TestTemplate
@ExtendWith(PactVerificationInvocationContextProvider.class)
void testTemplate(PactVerificationContext context, HttpRequest request) {
    replaceAuthHeader(request);
    context.verifyInteraction();
}
```

3. **Not cleaning up test data**:

```java
// BAD: Not cleaning up test data
@State("price with ID exists")
public void priceWithIdExists() {
    priceJpaRepository.save(new PriceEntity(...));
}

// GOOD: Cleaning up test data
@State(value = "price with ID exists", action = StateChangeAction.SETUP)
public void priceWithIdExists() {
    priceJpaRepository.save(new PriceEntity(...));
}

@State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
public void priceWithIdExistsCleanup() {
    priceJpaRepository.deleteById("AAPL");
}
```

## Configuring the Pact Broker

The Pact Broker is a central repository for contracts. It allows consumers to publish contracts and providers to retrieve them for verification.

### Configuration in build.gradle (Consumer)

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

### Configuration in build.gradle (Provider)

```gradle
pact {
    serviceProviders {
        'price-service-provider-price' {
            port = 8080
            // Get pacts from broker
            hasPactsFromPactBroker('http://localhost:9292', 
                authentication: ['Basic', 'pact', 'pact'])
        }
    }
    broker {
        pactBrokerUrl = 'http://localhost:9292/'
        retryCountWhileUnknown = 3
        retryWhileUnknownInterval = 10 // 10 seconds between retries
    }
}

test {
    // These properties need to be set on the test JVM process
    systemProperty("pact.provider.version", 
        System.getenv("GIT_COMMIT") == null ? version : System.getenv("GIT_COMMIT"))
    systemProperty("pact.provider.branch", 
        System.getenv("GIT_BRANCH") == null ? "" : System.getenv("GIT_BRANCH"))
    systemProperty("pact.verifier.publishResults", "true")
    
    // Work in progress pacts
    systemProperty("pactbroker.includeWipPactsSince", 
        java.time.LocalDate.now().minusMonths(6).format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")))
}
```

## CI/CD Integration

Integrating Pact into your CI/CD pipeline is crucial for ensuring that contracts are verified before deployment.

### Example .gitlab-ci.yml Configuration

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

## Best Practices for Contract Testing with Pact

Based on our analysis of the repository and research on best practices, here are key recommendations for implementing contract testing with Pact:

1. **Focus on the actual API rather than the code**: Call the actual endpoint to see the request and response before writing the contract test.

2. **Write the contract to reflect the actual integration you expect**: Align the contract with your provider before merging.

3. **Be flexible in your expectations**: Use matchers (regex, type, date formats) instead of exact values whenever possible.

4. **Use fewer constants and common components in tests**: Avoid unnecessary cohesion in your tests.

5. **Use fewer production model classes in your test code**: Rely on plain JSONs and library DSLs instead.

6. **Always verify the contract on the provider side**: Every pact must be verified on the provider side to bind the consumer and the provider.

7. **Mock as deep as possible on the provider side**: Mock at the repository level or insert data into your database in the tests.

8. **Test error scenarios**: Include tests for error responses like 404 Not Found, 401 Unauthorized, etc.

9. **Handle authentication**: Ensure that authentication headers are correctly set for secured endpoints.

10. **Clean up test data**: Use `StateChangeAction.TEARDOWN` to clean up test data after each test.

11. **Use version selectors**: Define which consumer versions to verify against using version selectors.

12. **Integrate with CI/CD**: Ensure that contracts are verified before deployment.

## Conclusion

Contract testing with Pact is a powerful technique for ensuring reliable integrations between microservices. By following the best practices outlined in this article, you can implement effective contract tests that provide confidence in your service integrations.

Remember that contract testing is not a replacement for all other types of testing, but it can significantly reduce the need for complex and brittle end-to-end tests. Use it as part of a comprehensive testing strategy that includes unit tests, integration tests, and end-to-end tests where appropriate.

## References

1. [Pact Documentation](https://docs.pact.io/)
2. [Best practices for writing contract tests with Pact in JVM stack](https://dev.to/art_ptushkin/best-practices-for-writing-contract-tests-with-pact-in-jvm-stack-124l)
3. [Consumer Driven Contracts with Pact](https://www.baeldung.com/pact-junit-consumer-driven-contracts)
4. [Contract Testing using Pact](https://medium.com/@santhoshshetty58/contract-testing-using-pact-a0caddc08bed)
