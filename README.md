# Pact Contract Testing Demo

This project demonstrates Pact-based contract testing in a microservices architecture, focusing on a price service domain with REST API communication between provider and consumer services.

## Project Overview

The project consists of the following modules:

- **price-service-api**: Shared API definitions (DTOs and interfaces)
- **price-service-provider**: Provider implementation with Spring Boot, PostgreSQL, and authentication
- **price-service-consumer**: Consumer implementation with contract tests
- **pact-broker**: Docker Compose setup for the Pact Broker

## Architecture

The project follows Hexagonal Architecture (Ports and Adapters) with clear separation between:

- **Core Domain**: Business logic in the center
- **Ports**: Interfaces defining how the core interacts with external systems
- **Adapters**: Implementations connecting to external systems

This architecture ensures:
- Clear separation of business logic from external concerns
- Testability of core domain logic in isolation
- Flexibility to change external dependencies without affecting core logic

## Prerequisites

- Java 17 or higher
- Gradle 8.0 or higher
- Docker and Docker Compose (for running the Pact Broker and PostgreSQL)

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/pact-demo.git
cd pact-demo
```

### 2. Start PostgreSQL and Pact Broker

```bash
# Start PostgreSQL and Pact Broker
docker-compose up -d
```

This will start:
- PostgreSQL for the application on port 5432
- PostgreSQL for tests on port 5433
- Pact Broker on port 9292

The Pact Broker will be available at http://localhost:9292 with the following credentials:
- Username: `pact`
- Password: `pact`

### 3. Run the Consumer Tests and Publish Contracts

```bash
./gradlew :price-service-consumer:clean :price-service-consumer:test :price-service-consumer:pactPublish
```

This will:
- Run the consumer tests
- Generate Pact contract files
- Publish the contracts to the Pact Broker

### 4. Run the Provider and Verify Contracts

```bash
./gradlew :price-service-provider:clean :price-service-provider:test
```

This will:
- Start the provider service
- Fetch contracts from the Pact Broker
- Verify that the provider can fulfill the contracts

### 5. Run the Complete Build

```bash
./gradlew clean build
```

## Module Details

### price-service-api

This module contains the shared API definitions:

- **DTOs**: Data Transfer Objects for communication between services
- **Service Interfaces**: Contracts defining the service operations

### price-service-provider

The provider implements a REST API for a price service with the following endpoints:

- `GET /prices`: Retrieve all prices
- `GET /prices/{instrumentId}`: Retrieve price for a specific instrument
- `POST /prices/{instrumentId}`: Create or update price for a specific instrument (requires authentication)
- `DELETE /prices/{instrumentId}`: Delete price for a specific instrument (requires authentication)
- `GET /orderbook/{instrumentId}`: Retrieve order book for a specific instrument
- `POST /orderbook/{instrumentId}`: Create or update order book for a specific instrument (requires authentication)

The provider includes:
- PostgreSQL database integration with Flyway migrations
- Authentication for POST and DELETE operations
- OpenAPI documentation:
  - Interactive UI at `/swagger-ui.html`
  - JSON specification at `/v3/api-docs`
  - YAML specification at `/v3/api-docs.yaml`
  - Raw specification file at `src/main/resources/openapi.yaml`
- Hexagonal architecture with domain, ports, and adapters

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

#### Consumer Configuration

The consumer configuration can be modified in `application.properties`:

```properties
price-service.base-url=http://localhost:8080
price-service.username=admin
price-service.password=password
```

### pact-broker

Docker Compose setup for the Pact Broker, including:

- Pact Broker service
- PostgreSQL database for storing contracts

## Contract Testing Workflow

1. **Consumer Defines Contracts**: The consumer creates Pact contract tests that define its expectations of the provider.

2. **Publish Contracts**: The contracts are published to the Pact Broker.

3. **Provider Verifies Contracts**: The provider verifies that it can fulfill the contracts.

4. **CI/CD Integration**: The workflow is integrated into CI/CD pipelines to ensure contracts are always verified.

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

The project includes comprehensive authentication testing:

- Tests for successful authentication
- Tests for failed authentication with wrong credentials
- Tests for malformed authentication headers
- Tests for missing authentication

This ensures that the security requirements are properly verified through contracts.

## When to Use Pact vs. Other Testing

- **Unit Tests**: For testing business logic in isolation
- **Pact Tests**: For testing service boundaries and API contracts
- **Integration Tests**: For testing interactions with databases and other dependencies
- **End-to-End Tests**: For testing critical user journeys

Pact is particularly valuable when:
- You have multiple services that need to communicate
- You want to detect breaking changes before deployment
- You want to evolve your APIs with confidence
- You want to reduce the need for end-to-end testing

## CI/CD Integration

### GitLab CI Example

Create a `.gitlab-ci.yml` file in the root of your project:

```yaml
stages:
  - test
  - publish
  - verify

variables:
  PACT_BROKER_URL: "http://pact-broker:9292"
  PACT_BROKER_USERNAME: "pact"
  PACT_BROKER_PASSWORD: "pact"
  POSTGRES_USER: "postgres"
  POSTGRES_PASSWORD: "postgres"
  POSTGRES_DB: "priceservice"

services:
  - name: postgres:14
    alias: postgres
  - name: pactfoundation/pact-broker:2.107.0.1
    alias: pact-broker
    variables:
      PACT_BROKER_DATABASE_URL: "postgres://postgres:postgres@postgres/postgres"
      PACT_BROKER_BASIC_AUTH_USERNAME: "pact"
      PACT_BROKER_BASIC_AUTH_PASSWORD: "pact"
      PACT_BROKER_PORT: "9292"
      PACT_BROKER_ALLOW_PUBLIC_READ: "true"

consumer_test:
  stage: test
  script:
    - ./gradlew :price-service-consumer:clean :price-service-consumer:test

publish_pacts:
  stage: publish
  script:
    - ./gradlew :price-service-consumer:pactPublish
  dependencies:
    - consumer_test

provider_verify:
  stage: verify
  script:
    - ./gradlew :price-service-provider:clean :price-service-provider:test
  dependencies:
    - publish_pacts
```

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

## Troubleshooting

### Database Connection Issues

If you have trouble connecting to PostgreSQL:

1. Ensure the PostgreSQL containers are running:
   ```bash
   docker ps | grep postgres
   ```

2. Check the logs:
   ```bash
   docker logs postgres
   ```

3. Verify the connection settings in `application.properties`

### Pact Broker Connection Issues

If you have trouble connecting to the Pact Broker:

1. Ensure the Docker containers are running:
   ```bash
   docker-compose ps
   ```

2. Check the logs:
   ```bash
   docker-compose logs pact-broker
   ```

3. Verify the broker is accessible:
   ```bash
   curl -u pact:pact http://localhost:9292
   ```

### Authentication Issues

If you encounter authentication problems:

1. Ensure you're using the correct credentials (admin/password)
2. Check that the Authorization header is properly formatted
3. Verify that the consumer client is configured with the correct credentials

### Test Database Issues

If you encounter issues with the test database:

1. Ensure the test PostgreSQL container is running on port 5433
2. Check that the test configuration is using the correct database URL
3. Verify that the test database is properly initialized

## Conclusion

This project demonstrates how to implement consumer-driven contract testing using Pact in a Java microservices environment with PostgreSQL persistence and authentication. By following the patterns and practices shown here, you can build more reliable and maintainable service integrations.
