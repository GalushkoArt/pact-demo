# Pact Contract Testing Demo

This project demonstrates Pact-based contract testing in a microservices architecture, focusing on a price service domain with REST API communication between provider and consumer services.

## Project Overview

In modern microservice architectures, ensuring reliable communication between services is critical.
This demo project showcases how to implement contract testing using Pact,
a consumer-driven contract testing tool that helps teams detect integration issues early in the development cycle.

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

## More to read

You can read [Official Pact Documentation](https://docs.pact.io/) or see docs snapshot in the project (2025.06.15)

What to read in [docs](docs):
- [CI/CD Setup Guide (aka Pact Nirvana)](docs/pact_nirvana.md)
- [Convince me](docs/faq/convinceme.md)
- [Versioning in the Pact Broker](docs/getting_started/versioning_in_the_pact_broker.md)
- [When to use Pact](docs/getting_started/what_is_pact_good_for.md)

### Documentation and Guides

- [Official Pact Documentation](https://docs.pact.io/)
- [Pact JVM Documentation](https://github.com/pact-foundation/pact-jvm)
- [Spring Boot and Pact Integration Guide](https://docs.pact.io/implementation_guides/jvm/provider/spring)
