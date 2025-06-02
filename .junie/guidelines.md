# Project Guidelines

This document provides guidelines and instructions for working with the Pact Demo project.

## Build/Configuration Instructions

### Prerequisites
- Java 17 or higher
- Gradle 8.0 or higher
- Docker and Docker Compose (for running the Pact Broker)

### Project Structure
The project consists of three main modules:
- `price-service-api`: Shared API definitions and DTOs
- `price-service-consumer`: Consumer application that uses the price service
- `price-service-provider`: Provider application that implements the price service

### Building the Project
To build the entire project:
```bash
./gradlew clean build
```

To build a specific module:
```bash
./gradlew :price-service-consumer:clean :price-service-consumer:build
```

### Running the Applications
To run the provider application:
```bash
./gradlew :price-service-provider:bootRun
```

To run the consumer application:
```bash
./gradlew :price-service-consumer:bootRun
```

### Running with Pact Broker
The project includes a Docker Compose configuration for running a Pact Broker:
```bash
docker-compose up -d
```

This will start a Pact Broker at http://localhost:9292 with the following credentials:
- Username: pact
- Password: pact

## Testing Information

### Running Tests
To run all tests:
```bash
./gradlew test
```

To run tests for a specific module:
```bash
./gradlew :price-service-consumer:test
```

To run a specific test class:
```bash
./gradlew :price-service-consumer:test --tests "com.example.priceclient.SimpleTest"
```

### Pact Contract Testing
This project uses Pact for contract testing between the consumer and provider.

#### Consumer Tests
Consumer tests define the contracts that the provider must fulfill. These tests are located in:
- `price-service-consumer/src/test/java/com/example/priceclient/client/PriceServiceClientPactTest.java`
- `price-service-consumer/src/test/java/com/example/priceclient/client/OrderBookClientPactTest.java`

To run the consumer tests:
```bash
./gradlew :price-service-consumer:test
```

#### Publishing Pacts
After running the consumer tests, the generated Pact contracts need to be published to the Pact Broker:
```bash
./gradlew :price-service-consumer:pactPublish
```

#### Provider Verification
Provider verification tests ensure that the provider can fulfill the contracts defined by the consumers. These tests are located in:
- `price-service-provider/src/test/java/com/example/priceservice/pact/PriceServiceProviderPactTest.java`

To run the provider verification tests:
```bash
./gradlew :price-service-provider:test
```

### Adding New Tests
To add a new Pact contract test:

#### Adding Consumer Pact Tests
1. Create a new test class in the consumer module's test directory (e.g., `price-service-consumer/src/test/java/com/example/priceclient/client/`)
2. Extend the class with `@ExtendWith(PactConsumerTestExt.class)` and add `@PactTestFor(providerName = "price-service-provider")`
3. Define the contract using `@Pact(consumer = "price-service-consumer")` methods
4. Implement test methods with `@PactTestFor(pactMethod = "yourPactMethod")` to verify the consumer works with the contract

Example of a consumer Pact test:
```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider")
public class NewServiceClientPactTest {
    @Pact(consumer = "price-service-consumer")
    public V4Pact getDataPact(PactDslWithProvider builder) {
        return builder
                .given("data exists")
                .uponReceiving("a request for data")
                .path("/data")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(newJsonBody(body -> {
                    body.stringType("id", "example");
                }).build())
                .toPact().asV4Pact().get();
    }

    @Test
    @PactTestFor(pactMethod = "getDataPact")
    void testGetData() {
        // Test consumer client with the contract
        System.out.println("[DEBUG_LOG] Consumer test executed");
    }
}
```

#### Adding Provider Pact Tests
1. Create or update the provider verification test in `price-service-provider/src/test/java/com/example/priceservice/pact/`
2. Use `@Provider("price-service-provider")` and `@PactBroker` annotations
3. Implement `@State` methods to set up the test data for each provider state
4. Run the provider tests to verify it fulfills all consumer contracts

Example of a provider state method:
```java
@State("data exists")
public void dataExists() {
    // Set up test data for this provider state
    System.out.println("[DEBUG_LOG] Provider state setup");
}
```

### Debugging Tests
You can add debug logs to your tests by prefixing log messages with `[DEBUG_LOG]`:
```java
System.out.println("[DEBUG_LOG] Your debug message here");
```

## Additional Development Information

### Code Style
- The project follows standard Java code style conventions
- Use Lombok annotations to reduce boilerplate code
- Follow the existing package structure when adding new classes

### Architecture
The project follows a hexagonal architecture pattern:
- `api`: Contains DTOs and service interfaces
- `adapter`: Contains implementations of ports (controllers, repositories)
- `domain`: Contains business logic and domain models
- `config`: Contains configuration classes

### CI/CD Pipeline
The project includes a GitLab CI configuration (`.gitlab-ci.yml`) with the following stages:
1. `test`: Runs consumer tests
2. `publish`: Publishes Pact contracts to the Pact Broker
3. `verify`: Runs provider verification tests

### Working with Pact
When making changes to the API:
1. Update the consumer tests to reflect the new contract
2. Run the consumer tests to generate new Pact contracts
3. Publish the new contracts to the Pact Broker
4. Update the provider to implement the new contract
5. Run the provider verification tests to ensure the provider fulfills the contract

This workflow ensures that the consumer and provider remain compatible.
