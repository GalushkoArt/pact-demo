# Pact REST API Contract Testing Guide

This comprehensive guide demonstrates how to implement REST API contract testing using Pact in Java applications. The examples are based on a price service that manages financial instrument prices through HTTP endpoints.

# Table of Contents

1. [Introduction to REST Contract Testing](#introduction-to-rest-contract-testing)
2. [Consumer-Side Contract Testing](#consumer-side-contract-testing)
3. [Provider-Side Contract Verification](#provider-side-contract-verification)
4. [Advanced Patterns and Best Practices](#advanced-patterns-and-best-practices)
5. [Error Handling and Edge Cases](#error-handling-and-edge-cases)
6. [Authentication and Security](#authentication-and-security)
7. [Data Matching Strategies](#data-matching-strategies)
8. [References](#references)

# Introduction to REST Contract Testing

REST API contract testing with Pact enables teams to verify that HTTP-based services can communicate correctly without requiring both services to be running simultaneously. This approach is particularly valuable in microservices architectures where services are developed and deployed independently.

Contract testing differs from traditional integration testing by focusing on the communication protocol and data structures rather than business logic. The consumer defines expectations about the provider's API behavior, and the provider verifies it can meet those expectations. This creates a safety net that catches breaking changes early in the development cycle.

The Pact framework uses a consumer-driven approach, meaning the consumer service defines the contract based on its actual needs. This ensures that providers only implement features that consumers actually use, preventing over-engineering and reducing unnecessary API surface area.



# Consumer-Side Contract Testing

Consumer-side contract testing involves creating tests that define the expected behavior of the provider service. These tests run against a mock server that simulates the provider's responses based on the defined contracts.

## Basic Test Setup

The foundation of consumer-side testing requires proper test configuration and dependency injection. Here's how to set up a basic consumer test class:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "price-service.base-url=http://localhost:9090",
        "price-service.username=admin",
        "price-service.password=password",
        "spring.autoconfigure.exclude=net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration",
})
@MockServerConfig(port = "9090")
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-price")
public class PriceApiPactTest {
    @Autowired
    private PricesApi pricesApi;
    
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "password";
    private static final String AUTH_HEADER = "Basic " + 
        Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
    
    @BeforeAll
    public static void setup() {
        System.setProperty("http.keepAlive", "false");
    }
}
```

The `@MockServerConfig` annotation configures the port where the mock server will run during tests. The `@PactTestFor` annotation specifies the provider name that will be used in the generated contract files.

The `@BeforeAll` method disables HTTP keep-alive connections, which helps prevent connection issues during test execution. This is a known workaround for issues with the Pact mock server ([Pact JVM GitHub Issue #342 - HTTP Keep-Alive Issues](https://github.com/pact-foundation/pact-jvm/issues/342)).

## Defining Simple GET Contracts

The most basic contract involves a simple GET request that returns a collection of resources. Here's how to define a contract for retrieving all prices:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact getAllPricesPact(PactDslWithProvider builder) {
    return builder
            .given("prices exist")
            .uponReceiving("a request for all prices")
            .path("/prices")
            .method("GET")
            .willRespondWith()
            .status(200)
            .body(newJsonArrayMinLike(1, array -> array.object(o -> {
                o.stringType("instrumentId", "AAPL");
                o.numberType("bidPrice", 175.50);
                o.numberType("askPrice", 175.75);
                o.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
            })).build())
            .toPact();
}
```

This contract definition demonstrates several important concepts. The `given` clause defines the provider state, which tells the provider what conditions should exist when the contract is verified. The `uponReceiving` clause describes the request being made, while `willRespondWith` defines the expected response.

The `newJsonArrayMinLike` matcher ensures the response contains at least one item with the specified structure, but allows for additional items. This provides flexibility while still validating the essential data structure.

Test ONLY fields you REQUIRED! Not all fields that are marked as required by contract!

The corresponding test method verifies that the client can properly handle the response:

```java
@Test
@PactTestFor(pactMethod = "getAllPricesPact")
void testGetAllPrices() {
    List<PriceDto> prices = pricesApi.getAllPrices();

    assertThat(prices).isNotNull();
    assertThat(prices.size()).isGreaterThanOrEqualTo(1);
    assertThat(prices.get(0).getInstrumentId()).isEqualTo("AAPL");
    assertThat(prices.get(0).getBidPrice()).isEqualTo(BigDecimal.valueOf(175.50));
    assertThat(prices.get(0).getAskPrice()).isEqualTo(BigDecimal.valueOf(175.75));
    assertThat(prices.get(0).getLastUpdated().toInstant()).isEqualTo(timestamp);
}
```

## Using Provider State Parameters

More sophisticated contracts can use provider state parameters to make tests more flexible and reusable. This approach allows the same contract structure to work with different data values:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact getPricePact(PactDslWithProvider builder) {
    return builder
            .given("price with ID exists", Map.of("instrumentId", "AAPL"))
            .uponReceiving("a request for price with ID AAPL")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("GET")
            .willRespondWith()
            .status(200)
            .body(newJsonBody(body -> {
                body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                body.decimalType("bidPrice", 175.50);
                body.decimalType("askPrice", 175.75);
                body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
            }).build())
            .toPact();
}
```

The `pathFromProviderState` method allows the URL path to be constructed using provider state parameters. The first parameter is the template with placeholders, and the second is the fallback value used during consumer testing. Similarly, `valueFromProviderState` in the response body uses provider state values, making the contract more flexible.

## POST Requests with Request Bodies

Creating resources requires more complex contracts that include request bodies and authentication. Here's how to define a contract for saving a price:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact savePricePact(PactDslWithProvider builder) {
    return builder
            .given("price can be saved", Map.of("instrumentId", "AAPL"))
            .uponReceiving("an authenticated request to save price for AAPL")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("POST")
            .headerFromProviderState("Authorization", "Basic ${basicAuth}", AUTH_HEADER)
            .headers("Content-Type", "application/json")
            .body(newJsonBody(body -> {
                body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                body.decimalType("bidPrice", 176.50);
                body.decimalType("askPrice", 176.75);
                body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
            }).build())
            .willRespondWith()
            .status(200)
            .body(newJsonBody(body -> {
                body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                body.decimalType("bidPrice", 176.50);
                body.decimalType("askPrice", 176.75);
                body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
            }).build())
            .toPact();
}
```

This contract includes both request and response bodies, demonstrating how to validate the complete request-response cycle. The authentication header is included to test secured endpoints, which is crucial for production APIs.

The test method creates the request object and verifies the response:

```java
@Test
@PactTestFor(pactMethod = "savePricePact")
void testSavePrice() {
    PriceDto priceDto = new PriceDto();
    priceDto.setInstrumentId("AAPL");
    priceDto.setBidPrice(new BigDecimal("176.50"));
    priceDto.setAskPrice(new BigDecimal("176.75"));
    priceDto.setLastUpdated(timestamp.atOffset(ZoneOffset.UTC));

    PriceDto savedPrice = pricesApi.savePrice(priceDto.getInstrumentId(), priceDto);

    assertThat(savedPrice.getInstrumentId()).isEqualTo("AAPL");
    assertThat(savedPrice.getBidPrice()).isEqualByComparingTo(new BigDecimal("176.50"));
    assertThat(savedPrice.getAskPrice()).isEqualByComparingTo(new BigDecimal("176.75"));
    assertThat(savedPrice.getLastUpdated()).isEqualTo(timestamp.atOffset(ZoneOffset.UTC));
}
```

## DELETE Requests and Empty Responses

DELETE operations typically return empty responses with specific status codes. Here's how to handle such scenarios:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact deletePricePact(PactDslWithProvider builder) {
    return builder
            .given("price with ID exists", Map.of("instrumentId", "AAPL"))
            .uponReceiving("an authenticated request to delete price with ID AAPL")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("DELETE")
            .headerFromProviderState("Authorization", "Basic ${basicAuth}", AUTH_HEADER)
            .willRespondWith(response -> response.status(204))
            .toPact();
}
```

The contract specifies a 204 No Content status code, which is the standard response for successful DELETE operations. The test verifies the operation completes without error:

```java
@Test
@PactTestFor(pactMethod = "deletePricePact")
void testDeletePrice() {
    pricesApi.deletePriceWithResponseSpec("AAPL").toBodilessEntity();
}
```


# Provider-Side Contract Verification

Provider-side testing verifies that the actual service implementation can fulfill the contracts defined by consumers. This involves running the provider application and executing the consumer contracts against it to ensure compatibility.

## Basic Provider Test Setup

The provider test configuration requires several key components to properly verify contracts from the Pact Broker:

```java
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, value = {
        "admin.username=admin",
        "admin.password=password",
})
@Provider("price-service-provider-price")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
public class PriceServiceProviderPricePactTest {
    @LocalServerPort
    private int port;
    
    @Autowired
    private PriceJpaRepository priceJpaRepository;
}
```

The `@Provider` annotation identifies this provider in the Pact ecosystem. The `@PactBroker` annotation configures how to retrieve contracts from the broker, including authentication credentials and branch information. The `@VersionSelector` annotation enables automatic version selection based on the consumer version selectors.

## Consumer Version Selection Strategy

A critical aspect of provider testing is determining which consumer versions to verify against. The following configuration demonstrates best practices for version selection:

```java
@PactBrokerConsumerVersionSelectors
public static SelectorBuilder consumerVersionSelectors() {
    return new SelectorBuilder()
            .mainBranch()
            .tag("prod")
            .latestTag("dev");
}
```

This configuration verifies against consumers from the main branch, production-tagged versions, and the latest development versions. This strategy ensures that providers are compatible with both current production consumers and upcoming development versions ([Pact JVM GitHub Issue #342 - HTTP Keep-Alive Issues](https://github.com/pact-foundation/pact-jvm/issues/342)).

## Test Target Configuration

The provider test needs to know where to send verification requests. This is configured in the setup method:

```java
@BeforeEach
void setUp(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", port));
}
```

The `HttpTestTarget` points to the locally running Spring Boot application. The port is automatically injected by Spring Boot's test framework, ensuring tests use the correct port even when it's randomly assigned.

## Verification Template Method

The core verification logic uses a template method that handles each contract interaction:

```java
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
```

The template method allows for request modification before verification. In this example, authentication headers are replaced with valid credentials, which is essential for testing secured endpoints. This approach separates authentication concerns from contract verification, making tests more maintainable.

## Provider State Management

Provider states are the mechanism by which the provider prepares its environment to fulfill specific contract expectations. Each state method corresponds to a `given` clause in the consumer contracts.

### Simple State Setup

Basic state setup involves creating the necessary data conditions:

```java
@State(value = "prices exist", action = StateChangeAction.SETUP)
@Transactional
public void pricesExist() throws InterruptedException {
    try {
        priceJpaRepository.findById("AAPL").ifPresentOrElse(
                e -> {},
                () -> priceJpaRepository.save(PriceEntity.builder()
                        .instrumentId("AAPL")
                        .bidPrice(new BigDecimal("175.50"))
                        .askPrice(new BigDecimal("175.75"))
                        .lastUpdated(Instant.now())
                        .build())
        );
    } finally {
        pricesExist.unlock();
    }
}
```

This state method ensures that price data exists when the consumer expects it. The use of locks prevents race conditions when multiple tests run concurrently. The `@Transactional` annotation ensures that database changes are properly managed.

### Parameterized State Management

More sophisticated state management uses parameters to create dynamic test scenarios:

```java
@State(value = "price with ID exists", action = StateChangeAction.SETUP)
@Transactional
public Map<String, String> priceWithIdExists() {
    var parameters = new HashMap<String, String>();
    var instrumentId = parameters.computeIfAbsent("instrumentId", id -> TestDataFactory.randomInstrumentId());
    
    // Clear existing data for this ID
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
```

This approach generates dynamic instrument IDs for each test run, preventing conflicts between different test executions. The returned parameters map can be used by the consumer contract to substitute values in URLs and response bodies.

### State Cleanup

Proper cleanup ensures test isolation and prevents side effects between tests:

```java
@State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
@Transactional
public void priceWithIdExistsCleanup(Map<String, String> parameters) {
    log.debug("Cleaning up price with ID: {}", parameters.get("instrumentId"));
    Optional.ofNullable(parameters.get("instrumentId")).ifPresent(id -> priceJpaRepository.deleteById(id));
}
```

Cleanup methods receive the same parameters that were returned during setup, allowing them to clean up the exact resources that were created. This is crucial for maintaining test isolation and preventing test pollution.

### Error State Management

Testing error scenarios requires setting up conditions where the expected resource doesn't exist:

```java
@State("price with ID UNKNOWN does not exist")
@Transactional
public void priceWithIdUnknownDoesNotExist() {
    priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
}
```

This state method ensures that the specified resource is absent, allowing the provider to return the expected 404 Not Found response. Testing error conditions is just as important as testing success scenarios, as it verifies that the provider handles edge cases correctly.


# Advanced Patterns and Best Practices

Effective REST contract testing requires understanding advanced patterns that handle real-world complexity. These patterns ensure contracts remain maintainable and provide meaningful validation as applications evolve.

## Type Matchers vs Exact Values

One of the most important decisions in contract testing is when to use type matchers versus exact values. The choice affects both test flexibility and validation strength.

### Using Type Matchers for Flexibility

Type matchers validate data structure and types without requiring exact values:

```java
.body(newJsonBody(body -> {
    body.stringType("instrumentId", "AAPL");
    body.numberType("bidPrice", 175.50);
    body.numberType("askPrice", 175.75);
    body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
}).build())
```

The `stringType` matcher ensures the field is a string but doesn't require the exact value "AAPL". Similarly, `numberType` validates numeric fields without enforcing specific values. This approach provides flexibility when the provider might return different but valid data.

However, there are important considerations when choosing between `numberType` and `decimalType`:

```java
// Prefer numberType for floating-point values
body.numberType("bidPrice", 175.50);

// Use decimalType for precise decimal values
body.decimalType("askPrice", 175.75);
```

The `numberType` matcher is more flexible for floating-point arithmetic, while `decimalType` enforces exact decimal precision with floating point. Choose based on your application's precision requirements.

### Date and Time Handling

Date and time fields require special handling to account for timezone differences and formatting variations:

```java
public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
private static final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);

// In contract definition
body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
```

The timestamp is truncated to milliseconds because the ISO format has millisecond precision. This prevents precision mismatches that could cause contract failures. The format string should match exactly what your API produces and consumes.

### Array Handling with MinLike Matchers

Arrays present unique challenges because their size can vary. The `newJsonArrayMinLike` matcher addresses this:

```java
.body(newJsonArrayMinLike(1, array -> array.object(o -> {
    o.stringType("instrumentId", "AAPL");
    o.numberType("bidPrice", 175.50);
    o.numberType("askPrice", 175.75);
    o.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
})).build())
```

This matcher ensures the array contains at least one element with the specified structure, but allows for additional elements. This is crucial for APIs that return variable-length collections while still validating the essential data structure.

## Provider State Parameter Patterns

Provider state parameters enable dynamic contract testing that adapts to different scenarios without duplicating contract definitions.

### Dynamic Path Construction

Using provider state parameters in URL paths creates flexible contracts:

```java
.pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
```

The first parameter is the template used during provider verification, while the second is the fallback used during consumer testing. This pattern allows the same contract to work with different resource identifiers.

### Response Body Parameter Substitution

Provider state parameters can also be used in response bodies:

```java
body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
```

This ensures that response data matches the request parameters, creating more realistic test scenarios. The provider can substitute actual values during verification while the consumer test uses the fallback value.

## Authentication and Security Patterns

Securing REST APIs requires careful handling of authentication in contract tests. The patterns shown here work with Basic Authentication but can be adapted for other schemes.

### Consumer-Side Authentication Setup

Consumers define authentication requirements in their contracts:

```java
private static final String USERNAME = "admin";
private static final String PASSWORD = "password";
private static final String AUTH_HEADER = "Basic " + 
    Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());

// In contract definition
.headerFromProviderState("Authorization", "Basic ${basicAuth}", AUTH_HEADER)
```

The authentication header is included in contracts that require security. This documents the authentication requirements and ensures the provider validates them correctly.

### Provider-Side Authentication Handling

Providers must handle authentication during contract verification:

```java
private void replaceAuthHeader(HttpRequest request) {
    if (request.containsHeader("Authorization")) {
        request.removeHeaders("Authorization");
        request.addHeader("Authorization", AUTH_HEADER);
    }
}
```

This approach replaces consumer-provided credentials with valid provider credentials. This separation allows consumers to define authentication requirements without needing actual provider credentials.

## Error Handling Patterns

Comprehensive contract testing must include error scenarios to ensure robust client implementations.

### 404 Not Found Scenarios

Testing resource not found scenarios:

```java
@Pact(consumer = "price-service-consumer")
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
```

The contract specifies only the status code without a response body, which is typical for 404 responses. The corresponding test verifies that the client handles the exception correctly:

```java
@Test
@PactTestFor(pactMethod = "getPriceNotFoundPact")
void testGetPriceNotFound() {
    assertThatThrownBy(() -> pricesApi.getPrice("UNKNOWN"))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
}
```

### 401 Unauthorized Scenarios

Authentication failure testing is crucial for secured APIs:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact saveWithWrongAuthPact(PactDslWithProvider builder) {
    return builder
            .given("price can be saved", Map.of("instrumentId", "AAPL"))
            .uponReceiving("an unauthenticated request to save price")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("POST")
            .headers("Content-Type", "application/json")
            .body(/* request body */)
            .willRespondWith()
            .status(401)
            .toPact();
}
```

Notice that the `Authorization` header is deliberately omitted to test the unauthorized scenario. This ensures that the provider correctly rejects unauthenticated requests.

## HTTP Keep-Alive Considerations

A common issue in Pact testing involves HTTP connection management:

```java
@BeforeAll
public static void setup() {
    System.setProperty("http.keepAlive", "false");
}
```

Disabling HTTP keep-alive prevents connection issues during test execution. This is a known workaround for issues with the Pact mock server and should be included in all consumer tests ([Pact JVM GitHub Issue #342 - HTTP Keep-Alive Issues](https://github.com/pact-foundation/pact-jvm/issues/342)).

## Mock Server Configuration

Consumer tests require proper mock server configuration:

```java
@MockServerConfig(port = "9090")
```

The port should match the configuration in your application properties. Using a fixed port ensures consistency between test runs and makes debugging easier.

These advanced patterns form the foundation of robust REST contract testing. They handle the complexity of real-world APIs while maintaining test reliability and maintainability. The key is balancing flexibility with validation strength, ensuring contracts provide meaningful protection against breaking changes while not being overly brittle.


# Error Handling and Edge Cases

Robust REST contract testing must thoroughly cover error scenarios and edge cases. These tests ensure that both consumers and providers handle exceptional situations correctly, leading to more resilient distributed systems.

## HTTP Status Code Testing

Different HTTP status codes represent different types of failures, and each requires specific handling patterns in contract tests.

### 404 Not Found - Resource Absence

The 404 status code indicates that a requested resource doesn't exist. This is one of the most common error scenarios in REST APIs:

```java
@Pact(consumer = "price-service-consumer")
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
```

The contract deliberately omits a response body, which is typical for 404 responses. Some APIs include error details in the body, but the minimal approach shown here focuses on the essential behavior.

The consumer test verifies proper exception handling:

```java
@Test
@PactTestFor(pactMethod = "getPriceNotFoundPact")
void testGetPriceNotFound() {
    assertThatThrownBy(() -> pricesApi.getPrice("UNKNOWN"))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
}
```

This test ensures that the client properly converts HTTP 404 responses into appropriate exceptions. The specific exception type depends on your HTTP client library, but the pattern remains consistent.

### 401 Unauthorized - Authentication Failures

Authentication failures are critical security scenarios that must be tested thoroughly:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact saveWithWrongAuthPact(PactDslWithProvider builder) {
    return builder
            .given("price can be saved", Map.of("instrumentId", "AAPL"))
            .uponReceiving("an unauthenticated request to save price")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("POST")
            .headers("Content-Type", "application/json")
            .body(newJsonBody(body -> {
                body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
                body.decimalType("bidPrice", 175.50);
                body.decimalType("askPrice", 175.75);
                body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
            }).build())
            .willRespondWith()
            .status(401)
            .toPact();
}
```

The key aspect of this contract is the deliberate omission of the `Authorization` header. This tests the scenario where a client forgets to include authentication credentials or provides invalid ones.

The corresponding test verifies that authentication failures are handled correctly:

```java
@Test
@PactTestFor(pactMethod = "saveWithWrongAuthPact")
void testSavePriceWithoutAuth() {
    PriceDto priceDto = new PriceDto();
    priceDto.setInstrumentId("AAPL");
    priceDto.setBidPrice(new BigDecimal("176.50"));
    priceDto.setAskPrice(new BigDecimal("176.75"));
    priceDto.setLastUpdated(timestamp.atOffset(ZoneOffset.UTC));

    assertThatThrownBy(() -> pricesApi.savePrice(priceDto.getInstrumentId(), priceDto))
            .isInstanceOf(HttpClientErrorException.Unauthorized.class);
}
```

This pattern should be applied to all secured endpoints to ensure comprehensive security testing.

## Provider State Error Scenarios

Provider states can be used to set up error conditions, ensuring that the provider can generate the expected error responses.

### Setting Up Non-Existent Resources

For 404 scenarios, the provider state ensures the resource doesn't exist:

```java
@State("price with ID UNKNOWN does not exist")
@Transactional
public void priceWithIdUnknownDoesNotExist() {
    priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
}
```

This state method actively removes any existing data that might interfere with the error scenario. This is important because previous tests might have created data that could affect the current test.

### Simulating System Failures

More complex error scenarios might involve simulating system failures or resource constraints. While not shown in the basic examples, provider states can be used to configure mock dependencies to fail or to set up database constraints that cause specific errors.

## Content-Type and Header Validation

REST APIs often have strict requirements about request headers, and contract tests should validate these requirements.

### Required Content-Type Headers

POST and PUT requests typically require specific content types:

```java
.headers("Content-Type", "application/json")
```

Including this header in contracts ensures that both consumer and provider agree on the expected content type. The provider should validate this header and return appropriate errors (typically 415 Unsupported Media Type) if it's missing or incorrect.

### Custom Header Requirements

APIs might require custom headers for various purposes:

```java
.headers("X-API-Version", "v1")
.headers("X-Request-ID", "12345")
```

These headers should be included in contracts when they're required by the API. This documents the requirements and ensures compatibility.

For authorization and state required headers use `headerFromProviderState`

```java
.headerFromProviderState("Authorization", "Basic ${basicAuth}", AUTH_HEADER)
```

## Request Body Validation

Complex request bodies can have various validation requirements that should be tested through contracts.

### Required Field Validation

Contracts should include all fields that required by consumer to ensure the provider validates them correctly:

```java
.body(newJsonBody(body -> {
    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
    body.decimalType("bidPrice", 176.50);
    body.decimalType("askPrice", 176.75);
    body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
}).build())
```

There is no need to add all fields that are marked as required in the specification. Pact is purposed for contract tests. Not for functional tests.

### Data Type Validation

The choice of matchers affects what validation occurs:

```java
// Validates that the field is a number but allows any numeric value
body.numberType("bidPrice", 176.50);

// Validates numbers with the required floating point
body.decimalType("askPrice", 176.75);

// Validates integers without a floating point
body.integerType("volume", 10000);

// Validates string format and content
body.stringType("instrumentId", "AAPL");
```

Choose matchers based on your API's validation requirements. If the API validates specific formats or ranges, consider using more specific matchers or exact values.

## Response Validation Edge Cases

Response validation should handle various edge cases that might occur in production.

### Empty Response Bodies

Some operations return empty response bodies with specific status codes:

```java
.willRespondWith(response -> response.status(204))
```

The 204 No Content status code explicitly indicates an empty response body. This is common for DELETE operations and some PUT operations.

## Timeout and Connection Handling

While not directly part of contract testing, connection management affects test reliability.

### HTTP Keep-Alive Issues

A common issue in Pact testing involves HTTP connection reuse:

```java
@BeforeAll
public static void setup() {
    System.setProperty("http.keepAlive", "false");
}
```

Disabling keep-alive prevents connection issues that can cause intermittent test failures. This is particularly important when running multiple contract tests in sequence ([Pact JVM GitHub Issue #342 - HTTP Keep-Alive Issues](https://github.com/pact-foundation/pact-jvm/issues/342)).

## Error Response Body Patterns

While many error responses have empty bodies, some APIs return structured error information.

### Structured Error Responses

Some APIs return detailed error information:

```java
.willRespondWith()
.status(400)
.body(newJsonBody(body -> {
    body.stringType("error", "INVALID_REQUEST");
    body.stringType("message", "Invalid instrument ID format");
    body.arrayLike("details", detail -> {
        detail.stringType("field", "instrumentId");
        detail.stringType("code", "INVALID_FORMAT");
    });
}).build())
```

This pattern documents the error response structure, ensuring consumers can extract meaningful error information.

### Minimal Error Responses

For simpler APIs, minimal error responses might be preferred:

```java
.willRespondWith()
.status(400)
.body(newJsonBody(body -> {
    body.stringType("message", "Bad Request");
}).build())
```

The choice between detailed and minimal error responses depends on your API design philosophy and client requirements.

These error handling patterns ensure that contract tests provide comprehensive coverage of failure scenarios. By testing both success and failure paths, teams can build more resilient distributed systems that handle real-world conditions gracefully.


# Authentication and Security

Security is a critical aspect of REST API design, and contract testing must thoroughly validate authentication and authorization mechanisms. This section covers patterns for testing various security schemes while maintaining test maintainability and security best practices.

## Basic Authentication Patterns

Basic Authentication is one of the simplest authentication schemes and serves as a good foundation for understanding security testing patterns in Pact.

### Consumer-Side Authentication Setup

Consumers must include authentication credentials in their contract definitions:

```java
private static final String USERNAME = "admin";
private static final String PASSWORD = "password";
private static final String AUTH_HEADER = "Basic " + 
    Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
```

The credentials are encoded according to the Basic Authentication specification. In real applications, these credentials should be externalized through configuration or environment variables rather than hardcoded.

### Including Authentication in Contracts

Secured endpoints require authentication headers in their contract definitions:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact savePricePact(PactDslWithProvider builder) {
    return builder
            .given("price can be saved", Map.of("instrumentId", "AAPL"))
            .uponReceiving("an authenticated request to save price for AAPL")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("POST")
            .headerFromProviderState("Authorization", "Basic ${basicAuth}", AUTH_HEADER)
            .headers("Content-Type", "application/json")
            .body(/* request body */)
            .willRespondWith()
            .status(200)
            .body(/* response body */)
            .toPact();
}
```

The `Authorization` header is included in the contract, documenting that this endpoint requires authentication. This serves both as documentation and as a test specification.

### Testing Authentication Failures

Equally important is testing what happens when authentication fails:

```java
@Pact(consumer = "price-service-consumer")
public RequestResponsePact saveWithWrongAuthPact(PactDslWithProvider builder) {
    return builder
            .given("price can be saved", Map.of("instrumentId", "AAPL"))
            .uponReceiving("an unauthenticated request to save price")
            .pathFromProviderState("/prices/${instrumentId}", "/prices/AAPL")
            .method("POST")
            .headers("Content-Type", "application/json")
            .body(/* request body */)
            .willRespondWith()
            .status(401)
            .toPact();
}
```

Notice that the `Authorization` header is deliberately omitted. This tests the scenario where clients fail to provide authentication credentials, ensuring the provider correctly rejects such requests.

## Provider-Side Authentication Handling

Provider tests must handle authentication during contract verification while maintaining security best practices.

### Authentication Header Replacement

The provider test framework allows modification of requests before verification:

```java
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
```

This approach replaces consumer-provided credentials with valid provider credentials. This separation is important because:

1. Consumers don't need access to real provider credentials
2. Provider credentials can be managed securely
3. Tests can use different credentials for different environments

### Credential Management

Provider credentials should be externalized and managed securely:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, value = {
        "admin.username=admin",
        "admin.password=password",
})
```

In production environments, these values should come from secure configuration management systems rather than being hardcoded in test files.

## Security Testing Best Practices

When implementing security testing in Pact contracts, several best practices should be followed:

### Credential Separation

Never include real production credentials in test code. Use fake credentials in consumer tests and replace them with valid test credentials in provider tests.

# Data Matching Strategies

Effective data matching is crucial for creating maintainable and reliable contract tests. The choice between exact values, type matchers, and flexible patterns determines how resilient your contracts are to changes while still providing meaningful validation.

## Understanding Matcher Types

Pact provides various matchers that serve different purposes in contract validation. Understanding when to use each type is essential for creating effective contracts.

### Type Matchers for Structural Validation

Type matchers validate data structure and types without requiring exact values:

```java
.body(newJsonBody(body -> {
    body.stringType("instrumentId", "AAPL");
    body.numberType("bidPrice", 175.50);
    body.numberType("askPrice", 175.75);
    body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
}).build())
```

The `stringType` matcher ensures the field exists and is a string, but accepts any string value. This provides flexibility when the exact value might vary between test runs or environments. Similarly, `numberType` validates that a field is numeric without enforcing a specific value.

### Exact Value Matching

Sometimes exact values are necessary for business logic validation:

```java
body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
```

This matcher uses exact values from provider state parameters, ensuring that response data matches request parameters. This is particularly important for resource identifiers and other fields where exact matching is required.

### Decimal vs Number Types

The choice between decimal and number types affects precision handling:

```java
// For floating-point values where slight precision differences are acceptable
body.numberType("bidPrice", 175.50);

// For exact decimal precision requirements
body.decimalType("askPrice", 175.75);
```

Use `numberType` for floating-point arithmetic where small precision differences are acceptable. Use `decimalType` when exact decimal precision is required, such as for financial calculations where precision matters.

## Array and Collection Handling

Arrays present unique challenges because their size and content can vary. Pact provides several strategies for handling collections effectively.

### Minimum Array Size Validation

The `newJsonArrayMinLike` matcher ensures arrays contain at least a specified number of elements:

```java
.body(newJsonArrayMinLike(1, array -> array.object(o -> {
    o.stringType("instrumentId", "AAPL");
    o.numberType("bidPrice", 175.50);
    o.numberType("askPrice", 175.75);
    o.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
})).build())
```

This matcher validates that the array contains at least one element with the specified structure, but allows for additional elements. This is ideal for APIs that return variable-length collections while still validating the essential data structure.

### Fixed Array Size Validation

When arrays should have a specific size, use the `arrayLike` matcher:

```java
.body(newJsonBody(body -> {
    body.arrayLike("prices", 3, price -> {
        price.stringType("instrumentId", "AAPL");
        price.numberType("bidPrice", 175.50);
        price.numberType("askPrice", 175.75);
    });
}).build())
```

This ensures the array contains exactly three elements, each matching the specified structure.

### Empty Array Handling

Testing empty arrays is important for edge cases:

```java
.body(newJsonBody(body -> {
    body.array("prices");  // Empty array
}).build())
```

This validates that the field is an array but doesn't require any specific content. This is useful for testing scenarios where no data is available.

## Date and Time Matching

Date and time fields require special handling due to timezone differences, formatting variations, and precision considerations.

### ISO DateTime Format Matching

The most common pattern uses ISO 8601 format with specific precision:

```java
public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
private static final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);

// In contract definition
body.datetime("lastUpdated", ISO_DATE_TIME_FORMAT, timestamp);
```

The timestamp is truncated to milliseconds because the ISO format has millisecond precision. This prevents precision mismatches that could cause contract failures when the provider returns timestamps with different precision.

### Date-Only Matching

For date-only fields, use the date matcher:

```java
body.date("tradeDate", "yyyy-MM-dd", LocalDate.now());
```

This validates date format without time components, which is appropriate for business date fields.

### Flexible Time Matching

When exact time matching isn't necessary, use type matchers:

```java
body.stringType("lastUpdated", "2023-12-01T10:30:00.000Z");
```

This approach validates that the field is a string without enforcing specific time values, which can be useful when testing across different time zones or when exact timing isn't critical.

## Complex Object Matching

Real-world APIs often return complex nested objects that require sophisticated matching strategies.

### Nested Object Validation

For objects with nested structures:

```java
.body(newJsonBody(body -> {
    body.stringType("instrumentId", "AAPL");
    body.object("price", price -> {
        price.numberType("bid", 175.50);
        price.numberType("ask", 175.75);
        price.datetime("timestamp", ISO_DATE_TIME_FORMAT, timestamp);
    });
    body.object("metadata", metadata -> {
        metadata.stringType("source", "NASDAQ");
        metadata.stringType("currency", "USD");
    });
}).build())
```

This validates the structure of nested objects while allowing flexibility in the actual values.

### Array of Objects

When arrays contain complex objects:

```java
.body(newJsonBody(body -> {
    body.arrayLike("trades", 1, trade -> {
        trade.stringType("tradeId", "T12345");
        trade.numberType("quantity", 100);
        trade.numberType("price", 175.60);
        trade.datetime("timestamp", ISO_DATE_TIME_FORMAT, timestamp);
        trade.object("counterparty", cp -> {
            cp.stringType("name", "Broker ABC");
            cp.stringType("id", "BRK001");
        });
    });
}).build())
```

This pattern validates arrays of complex objects, ensuring both the array structure and the object structure within each element.

## Regular Expression Matching

For fields with specific format requirements, regular expressions provide precise validation:

```java
body.stringMatcher("instrumentId", "^[A-Z]{1,5}$", "AAPL");
```

This validates that instrument IDs follow a specific pattern (1-5 uppercase letters) while providing a concrete example. Regular expressions should be used sparingly and only when format validation is truly necessary.

## Provider State Integration with Matching

Provider state parameters can be integrated with matching strategies for dynamic validation:

```java
.body(newJsonBody(body -> {
    body.valueFromProviderState("instrumentId", "${instrumentId}", "AAPL");
    body.numberType("bidPrice", 175.50);
    body.numberType("askPrice", 175.75);
}).build())
```

This approach combines exact matching for critical fields (using provider state values) with flexible matching for other fields. The provider state ensures that response data matches request parameters while still allowing flexibility in other areas.

## Matching Strategy Selection Guidelines

Choosing the right matching strategy depends on several factors:

### Use Exact Values When:
- Field values must match request parameters
- Business logic depends on specific values
- Testing specific edge cases or boundary conditions

### Use Type Matchers When:
- Field structure is more important than specific values
- Values might vary between test runs or environments
- Testing general API behavior rather than specific business logic

### Use Flexible Patterns When:
- Field formats are standardized but values vary
- Testing integration points where exact values aren't critical
- Balancing validation with test maintainability

The key is finding the right balance between validation strength and test flexibility. Contracts should catch meaningful breaking changes while not being so brittle that they fail due to irrelevant variations in data values.


# References

1. [Oracle Documentation - Persistent Connections](https://docs.oracle.com/javase/8/docs/technotes/guides/net/http-keepalive.html)
2. [Official Pact Documentation](https://docs.pact.io/)
3. [Pact JVM Documentation](https://github.com/pact-foundation/pact-jvm)
4. [Spring Boot and Pact Integration Guide](https://docs.pact.io/implementation_guides/jvm/provider/spring)
5. [Consumer-Driven Contract Testing](https://docs.pact.io/getting_started/what_is_pact)
6. [Pact Broker Documentation](https://docs.pact.io/pact_broker)
7. [Pact Matching Rules](https://docs.pact.io/implementation_guides/jvm/consumer/junit5#matching)
8. [Provider State Documentation](https://docs.pact.io/getting_started/provider_states)
9. [Pact Best Practices](https://docs.pact.io/implementation_guides/jvm/best_practices)

