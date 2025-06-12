# Pact Contract Testing Demo

This project demonstrates Pact-based contract testing in a microservices architecture, focusing on a price service domain with REST API communication between provider and consumer services.

Check [the article about implementing contract test](Implementing%20Contract%20Testing%20with%20Pact%20in%20Java%20Spring%20Applications.md) out
after finishing reading README

## Project Overview

In modern microservice architectures, ensuring reliable communication between services is critical.
This demo project showcases how to implement contract testing using Pact,
a consumer-driven contract testing tool that helps teams detect integration issues early in the development cycle.

### Key Features

- **Consumer-Driven Contracts**: Consumers define their expectations, providers verify they can meet them
- **Automated Contract Verification**: CI/CD integration ensures contracts are always verified
- **Shared Pact Broker**: Central repository for contracts with visibility into compatibility
- **Spring Boot Integration**: Seamless integration with Spring Boot applications
- **Authentication Testing**: Verification of secured endpoints with proper authentication
- **Dynamic State Management**: Flexible provider state setup for reliable testing

The project uses code generation based on the Open API specification,
demonstrating how contract testing can complement API-first development approaches.

## Prerequisites

- Java 17 or higher
- Gradle 8.0 or higher
- Docker and Docker Compose (for running the Pact Broker and PostgreSQL)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/GalushkoArt/pact-demo.git
cd pact-demo
```

### 2. Start PostgreSQL and Pact Broker

```bash
# Using the Makefile
make docker-start

# Or directly with docker-compose
docker-compose up -d
```

This will start:
- PostgreSQL for the application on port 5432
- PostgreSQL for tests on port 5433
- Pact Broker on port 9292

The Pact Broker will be available at http://localhost:9292 with the following credentials:
- Username: `pact`
- Password: `pact`

### 3. Understanding the Project Structure

Before running tests, familiarize yourself with the project structure:

- **price-service-provider**: The service that implements the API (provider)
- **price-service-consumer**: A client that consumes the API (consumer)
- **new-price-service-consumer**: Another client consuming the API (consumer)

Each module contains:
- `src/main/java`: Application code
- `src/test/java`: Test code, including Pact contract tests
- `build.gradle`: Module-specific dependencies and Pact configuration

### 4. Run the Complete Workflow

You can run the complete workflow demonstration:

```bash
# Run the full workflow
make full-workflow
```

### Kafka Messaging Using Protobuf

The demo includes asynchronous communication over Kafka. The provider publishes
`PriceUpdate` events encoded with Protocol Buffers and the new consumer subscribes
to the same topic. The message schema is defined in [`proto/price_update.proto`](proto/price_update.proto).
Pact tests exercise this flow with the protobuf plugin. Scenarios
related to ordering or delivery guarantees are better covered with integration tests.

But suggest following the step-by-step instructions in the "Contract Testing Workflow Demonstration" section.

### 5. Implementing Your Own Contract Tests

To implement your own contract tests:

1. **For Consumers**:
   - See examples in `price-service-consumer/src/test/java/com/example/priceclient/client/PriceApiPactTest.java`
   - Define expectations using the Pact DSL
   - Run tests to generate contract files
   - Publish contracts to the Pact Broker

2. **For Providers**:
   - See examples in `price-service-provider/src/test/java/com/example/priceservice/pact/PriceServiceProviderPricePactTest.java`
   - Implement provider states
   - Verify against contracts from the Pact Broker

## Module Details

### Code Generation with Open API

All modules in this project use code generation based on the Open API specification:

- The specification is defined in the `oas/openapi.yaml` file
- The provider uses it to generate controller interfaces
- The consumers use it to generate client code

This approach ensures that all services are working with the same API contract.

### price-service-provider

The provider implements a REST API for a price service with the following endpoints:

- `GET /prices`: Retrieve all prices
- `GET /prices/{instrumentId}`: Retrieve price for a specific instrument
- `POST /prices/{instrumentId}`: Create or update price for a specific instrument (requires authentication)
- `DELETE /prices/{instrumentId}`: Delete price for a specific instrument (requires authentication)
- `GET /orderbook/{instrumentId}`: Retrieve an order book for a specific instrument
- `POST /orderbook/{instrumentId}`: Create or update an order book for a specific instrument (requires authentication)

#### Authentication

The provider uses Basic Authentication for POST and DELETE operations:
- Username: `admin`
- Password: `password`

Example using curl:
```bash
curl -X POST -u admin:password -H "Content-Type: application/json" -d '{"instrumentId":"GOOG","bidPrice":2750.00,"askPrice":2752.50,"lastUpdated":"2023-05-29T10:00:00.000"}' http://localhost:8080/prices/GOOG
```

#### Database Configuration

The provider uses PostgreSQL for data persistence. The database configuration can be modified in `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/priceservice
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Flyway migrations are used to create the database schema and insert sample data.

### price-service-consumer

The consumer implements a client for the price service with:

- REST client for communicating with the provider
- Authentication support for secured endpoints
- Pact contract tests defining the expected behavior
- Service layer for business logic

The consumer tests can be run with:

```bash
make consumer-tests
```

This will generate Pact contracts in [price-service-consumer/build/pacts/](price-service-consumer/build/pacts/) and publish them to the Pact Broker.

#### Consumer Configuration

The consumer configuration can be modified in `application.properties`:

```properties
price-service.base-url=http://localhost:8080
price-service.username=admin
price-service.password=password
```

### new-price-service-consumer

This is another consumer implementation that also interacts with the price service:

- Uses the same Open API specification for client generation
- Implements its own set of Pact contract tests
- Demonstrates how multiple consumers can interact with the same provider
- Shows how Pact can manage compatibility between multiple consumers and a provider

The new consumer tests can be run with:

```bash
make new-consumer-tests
```

This will generate Pact contracts in [new-price-service-consumer/build/pacts/](new-price-service-consumer/build/pacts/) and publish them to the Pact Broker.

#### gRPC contract tests

The new consumer also verifies the gRPC `PriceService`. The tests cover `GetAllPrices` and `GetPrice` methods using the Pact gRPC plugin. The streaming `StreamPrices` method is excluded because Pact currently focuses on request/response interactions and does not handle long‑lived streams well.

Run the gRPC consumer tests with:

```bash
./gradlew :new-price-service-consumer:test
```

#### Kafka protobuf contract tests

The consumer also subscribes to price updates delivered via Kafka using Protocol Buffers. 
Pact tests verify the structure of the `PriceUpdate` message with the protobuf plugin.
Message ordering and Kafka-specific delivery semantics are out of scope for Pact and should be
covered with integration tests using an embedded Kafka broker.

Limitations of Pact for Kafka testing:
- Ordering of events and delivery semantics are not verified.
- Headers and partitioning metadata are ignored.
- Long running streams should be tested with system or integration tests.

### pact-broker

Docker Compose setup for the Pact Broker, including:

- Pact Broker service
- PostgreSQL database for storing contracts

## Contract Testing Workflow Demonstration

This section demonstrates a complete workflow using the commands from the Makefile. Follow these steps to understand how Pact contract testing works in practice.

### 1. Start the Containers

First, start the Docker containers for PostgreSQL and Pact Broker:

```bash
make docker-start
```

This command starts the containers in detached mode and waits for them to be ready.

### 2. Run Consumer Tests and Create Pacts

Run the tests for the first consumer and publish the generated Pact contracts to the broker:

```bash
make consumer-tests
```

This will:
- Run the tests for price-service-consumer
- Generate Pact contract files (located in [price-service-consumer/build/pacts/](price-service-consumer/build/pacts/))
- Publish the contracts to the Pact Broker

### 3. Check the Pact Broker

Open the Pact Broker UI to see the published contracts:
[http://localhost:9292/](http://localhost:9292/)

You should see the contracts between price-service-consumer and price-service-provider.

### 4. Verify Provider Against Pacts

Run the provider verification tests to ensure the provider can fulfill the contracts:

```bash
make pact-tests
```

This command runs the JUnit5 tests in the price-service-provider module
that verifies the provider against the published Pact contracts.
It now includes verification of the gRPC `PriceService`.

### 5. Check the Pact Broker Again

Refresh the Pact Broker UI to see the verification results:
[http://localhost:9292/](http://localhost:9292/)

You should now see that the contracts have been verified.

### 6. Check Deployment Compatibility

Check if the provider and consumer can be safely deployed:

```bash
make canideploy-price
make canideploy-orderbook
make canideploy-consumer
```

This command checks if both price-service-provider-price, price-service-provider-orderbook, and price-service-consumer can be deployed
without breaking any contract.
Now it should say `Computer says yes \o/`.

### 7. Run Tests for the New Consumer

Run the tests for the new consumer and publish its contracts:

```bash
make new-consumer-tests
```

This will:
- Run the tests for new-price-service-consumer
- Generate Pact contract files (located in [new-price-service-consumer/build/pacts/](new-price-service-consumer/build/pacts/))
- Publish the contracts to the Pact Broker

### 8. Check Orderbook Deployment Compatibility

Check if the orderbook provider can be safely deployed:

```bash
make canideploy-orderbook
```

This should pass with `Computer says yes \o/` because the new consumer doesn't use this provider.

### 9. Check Price Deployment Compatibility

Check if the price provider can be safely deployed:

```bash
make canideploy-price
```

This should fail with `Can you deploy? Computer says no ¯\_(ツ)_/¯` because the new consumer uses this provider and the provider hasn't verified the new contracts yet.

### 10. Run Provider Tests Again

Run the provider tests again:

```bash
make pact-tests
```

This will verify all contracts, including the ones from the new consumer.

### 11. Check the Pact Broker Once More

Refresh the Pact Broker UI again to see the updated verification results:
[http://localhost:9292/](http://localhost:9292/)

You should now see that all contracts have been verified.

### 12. Check Deployment Compatibility Again

Check if both providers can now be safely deployed:

```bash
make canideploy-price
make canideploy-orderbook
make canideploy-new-consumer
```

This should now pass with `Computer says yes \o/` for both providers and a new consumer.

## Dynamic State Management

The provider uses dynamic state management for contract verification:

- Each provider state is implemented as a method annotated with `@State`
- State methods set up the necessary data for each interaction
- This ensures tests are isolated and repeatable
- No reliance on pre-allocated data

Example:
```java
@State("price with ID exists")
@Transactional
public void priceWithIdAaplExists() {
    // Clear existing data for this ID
    priceJpaRepository.findById("AAPL").ifPresent(price -> priceJpaRepository.delete(price));

    // Create test data
    PriceEntity apple = PriceEntity.builder()
            .instrumentId("AAPL")
            .bidPrice(new BigDecimal("175.50"))
            .askPrice(new BigDecimal("175.75"))
            .lastUpdated(LocalDateTime.now())
            .build();

    priceJpaRepository.save(apple);
}
```

## Authentication Testing

The project includes authentication testing:

- Tests for successful authentication
- Tests for failed authentication (this should cover cases when credentials are missing or wrong)

This ensures that the security requirements are properly verified through contracts.

## Best Practices for Pact Contract Testing

### Consumer-Side Best Practices

1. **Use Type Matchers Instead of Exact Values**
   ```java
   // GOOD: Using type matchers makes the contract more flexible
   .body(newJsonBody(body -> {
       body.decimalType("bidPrice", 175.50);
       body.decimalType("askPrice", 175.75);
   }).build())
   ```

2. **Define Clear Provider States**
   ```java
   // GOOD: Provider state clearly defined
   .given("price with ID exists")
   .uponReceiving("a request for price with ID AAPL")
   ```

3. **Test Error Scenarios**
   - Include tests for 404 Not Found, 401 Unauthorized, etc.
   - Verify proper error handling in your client code

4. **Handle Authentication Properly**
   - Include authentication headers in your contract tests
   - Test both successful and failed authentication scenarios

5. **Use Flexible Assertions**
   ```java
   // GOOD: Asserting presence of values, not exact content
   assertThat(price.getBidPrice()).isNotNull();
   ```

### Provider-Side Best Practices

1. **Mock at the Repository Level**
   - Avoid mocking controllers or services
   - Use repository mocks or in-memory databases for more realistic tests

2. **Clean Up Test Data**
   ```java
   @State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
   public void priceWithIdExistsCleanup() {
       // Clean up code here
   }
   ```

3. **Use Version Selectors**
   ```java
   @PactBrokerConsumerVersionSelectors
   public static SelectorBuilder consumerVersionSelectors() {
       return new SelectorBuilder()
               .mainBranch()
               .tag("prod")
               .latestTag("dev");
   }
   ```

4. **Handle Authentication in Provider Tests**
   - Replace authentication headers in requests
   - Ensure proper security context for tests

5. **Use Dynamic State Management**
   - Create test data dynamically for each test
   - Avoid relying on pre-existing data

## Common Pitfalls and Solutions

### 1. Tests Pass Locally But Fail in CI

**Problem**: Contract tests pass on your local machine but fail in the CI pipeline.

**Solution**:
- Ensure environment variables are properly set in CI
- Check that the Pact Broker URL and credentials are correct
- Verify that the provider states match exactly between consumer and provider tests

### 2. Authentication Issues

**Problem**: Tests fail with 401 Unauthorized errors.

**Solution**:
- Ensure authentication headers are correctly set in consumer tests
- Implement proper authentication handling in provider tests:
  ```java
  private void replaceAuthHeader(HttpRequest request) {
      if (request.containsHeader("Authorization")) {
          request.removeHeaders("Authorization");
          request.addHeader("Authorization", AUTH_HEADER);
      }
  }
  ```

### 3. Provider States Not Working

**Problem**: Provider tests fail because the expected state is not set up correctly.

**Solution**:
- Ensure provider state names match exactly between consumer and provider
- Use dynamic state parameters when needed:
  ```java
  @State(value = "price with ID exists")
  public Map<String, String> priceWithIdExists() {
      var parameters = new HashMap<String, String>();
      var instrumentId = parameters.computeIfAbsent("instrumentId", 
          id -> RandomStringUtils.secure().nextAlphanumeric(4));
      // Setup code using instrumentId
      return parameters;
  }
  ```

### 4. Contract Verification Failures

**Problem**: Provider verification fails with an unexpected response or status code.

**Solution**:
- Compare the actual response with the expected response in the contract
- Check for changes in the provider API that might break the contract
- Update consumer tests if the API has changed intentionally

## When to Use Pact vs. Other Testing

- **Unit Tests**: For testing business logic in isolation
- **Component Tests**: For testing business logic of the service with mocks and db running in docker
- **Pact Tests**: For testing service boundaries and API contracts
- **Integration Tests**: For testing interactions with databases and other dependencies
- **End-to-End Tests**: For testing critical user journeys

Pact is particularly valuable when:
- You have multiple services that need to communicate
- You want to detect breaking changes before deployment
- You want to evolve your APIs with confidence
- You want to reduce the need for end-to-end testing

## Benefits of Consumer-Driven Contracts

1. **Clear Separation of Concerns**:
   - Provider and consumer responsibilities are clearly defined
   - API contracts are explicit and versioned

2. **Evolutionary Design**:
   - Changes to contracts are detected early
   - Breaking changes are identified before deployment

3. **Confidence in Deployments**:
   - Verified contracts ensure compatibility
   - Reduced risk of integration issues

4. **Documentation as a Byproduct**:
   - Contracts serve as living documentation
   - OpenAPI integration provides additional API documentation

## More to read

Check [the article about implementing contract test](Implementing%20Contract%20Testing%20with%20Pact%20in%20Java%20Spring%20Applications.md) out

### Documentation and Guides

- [Official Pact Documentation](https://docs.pact.io/)
- [Pact JVM Documentation](https://github.com/pact-foundation/pact-jvm)
- [Spring Boot and Pact Integration Guide](https://docs.pact.io/implementation_guides/jvm/provider/spring)

### Tools and Plugins

- [Pact Broker](https://github.com/pact-foundation/pact_broker)
- [Pact JVM Provider Spring](https://github.com/pact-foundation/pact-jvm/tree/master/provider/spring)
- [Pact JVM Consumer JUnit 5](https://github.com/pact-foundation/pact-jvm/tree/master/consumer/junit5)

### Additional Reading

- [Consumer-Driven Contracts: A Service Evolution Pattern](https://martinfowler.com/articles/consumerDrivenContracts.html)
- [Microservices Testing Strategies](https://martinfowler.com/articles/microservice-testing/)
