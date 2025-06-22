# Pact Contract Testing Demo

This project demonstrates Pact-based contract testing in a microservices architecture, focusing on a price service
domain with REST API, gRPC, and Kafka communication between provider and consumer services.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture and Modules](#architecture-and-modules)
3. [Prerequisites](#prerequisites)
4. [Getting Started](#getting-started)
5. [Contract Testing Workflow](#contract-testing-workflow)
6. [Module Details](#module-details)
7. [Reference guides](#reference-guides)
8. [Advanced Topics](#advanced-topics)
9. [Troubleshooting](#troubleshooting)
10. [Additional Resources](#additional-resources)

## Project Overview

In modern microservice architectures, ensuring reliable communication between services is critical. This demo project
showcases how to implement contract testing using Pact, a consumer-driven contract testing tool that helps teams detect
integration issues early in the development cycle.

Contract testing with Pact enables teams to:

- **Develop independently**: Teams can develop and deploy services independently while maintaining integration
  confidence
- **Catch breaking changes early**: Contract verification runs in CI/CD pipelines to detect compatibility issues before
  production
- **Reduce integration testing complexity**: Eliminate the need for complex shared test environments
- **Improve deployment confidence**: Verify that service changes won't break existing integrations

## Architecture and Modules

This demonstration includes three main modules that showcase different aspects of contract testing:

### Service Architecture

```
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│                     │    │                     │    │                     │
│ price-service-      │    │ new-price-service-  │    │ price-service-      │
│ consumer            │    │ consumer            │    │ provider            │
│                     │    │                     │    │                     │
│ • REST API Client   │    │ • REST API Client   │    │ • REST API Server   │
│ • gRPC Client       │    │ • Simplified        │    │ • gRPC Server       │
│ • Kafka Consumer    │    │   Consumer          │    │ • Kafka Producer    │
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
                              │ • Contract Storage  │
                              │ • Verification      │
                              │ • Can I Deploy?     │
                              │                     │
                              └─────────────────────┘
```

### Communication Protocols

The demonstration covers multiple communication protocols commonly used in microservices:

| Protocol     | Use Case                     | Consumer Modules       | Provider Module        |
|--------------|------------------------------|------------------------|------------------------|
| **REST API** | Synchronous request-response | Both consumers         | price-service-provider |
| **gRPC**     | High-performance RPC         | price-service-consumer | price-service-provider |
| **Kafka**    | Asynchronous messaging       | price-service-consumer | price-service-provider |

## Prerequisites

- **Java 17 or higher**: Required for running the Spring Boot applications
- **Gradle 8.0 or higher**: Build tool for compiling and running tests
- **Docker and Docker Compose**: For running the Pact Broker and PostgreSQL databases
- **Git**: For cloning the repository

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/GalushkoArt/pact-demo.git
cd pact-demo
```

### 2. Start Infrastructure Services

Start PostgreSQL and Pact Broker using Docker Compose:

```bash
# Using the Makefile (recommended)
make docker-start

# Or directly with docker-compose
docker-compose up -d
```

This will start:

- **PostgreSQL for application**: Port 5432
- **PostgreSQL for tests**: Port 5433
- **Pact Broker**: Port 9292

The Pact Broker will be available at [http://localhost:9292](http://localhost:9292) with credentials:

- Username: `pact`
- Password: `pact`

### 3. Verify Setup

Check that all services are running:

```bash
# Check Docker containers
docker-compose ps

# Verify Pact Broker is accessible
curl -u pact:pact http://localhost:9292/
```

### 4. Run the Complete Workflow

Execute the full contract testing workflow:

```bash
# Run the complete demonstration
make full-workflow
```

This command will execute all the steps described in the workflow section below.

## Contract Testing Workflow

This section demonstrates a complete contract testing workflow using the commands from the Makefile. Follow these steps
to understand how Pact contract testing works in practice.

### Step 1: Start Infrastructure

```bash
make docker-start
```

Starts Docker containers in detached mode and waits for them to be ready.

### Step 2: Consumer Tests and Contract Generation

```bash
make consumer-tests
```

This command:

- Runs tests for `price-service-consumer`
- Generates Pact contract files in [price-service-consumer/build/pacts/](price-service-consumer/build/pacts/)
- Publishes contracts to the Pact Broker

**View Results**: Open [http://localhost:9292/](http://localhost:9292/) to see published contracts.

### Step 3: Provider Verification

```bash
make pact-tests
```

Runs provider verification tests that:

- Download contracts from the Pact Broker
- Verify the provider can fulfill all consumer contracts
- Test REST API, gRPC, and Kafka interactions
- Publish verification results back to the broker

### Step 4: Deployment Safety Checks

```bash
make canideploy-price
make canideploy-orderbook  
make canideploy-consumer
```

These commands check if services can be safely deployed without breaking contracts. Should return
`Computer says yes \o/`.

### Step 5: New Consumer Introduction

```bash
make new-consumer-tests
```

Demonstrates adding a new consumer:

- Runs tests for `new-price-service-consumer`
- Generates and publishes new contracts
- Shows how new consumers integrate into existing contract testing

### Step 6: Deployment Compatibility Verification

```bash
# Check orderbook (should pass - new consumer doesn't use it)
make canideploy-orderbook

# Check price service (should fail initially)
make canideploy-price
```

The price service check fails because the provider hasn't verified the new consumer's contracts yet.

### Step 7: Complete Verification Cycle

```bash
# Re-run provider verification to include new contracts
make pact-tests

# Now all deployment checks should pass
make canideploy-price
make canideploy-orderbook
make canideploy-new-consumer
```

All services should now show `Computer says yes \o/` for deployment.

## Module Details

### price-service-provider

**Purpose**: Implements the provider API that serves price and orderbook data through multiple protocols.

**Key Features**:

- **REST API**: Provides HTTP endpoints for price operations (GET, POST, DELETE)
- **gRPC Service**: High-performance price service implementation
- **Kafka Producer**: Publishes price updates to Kafka topics (JSON and protobuf message formats)
- **Multi-provider Configuration**: Separate Pact verification for different service aspects
- **Authentication**: Secured endpoints with Basic Authentication
- **Database Integration**: PostgreSQL with JPA for data persistence

**Pact Contract Tests**:

- **Provider Verification**: Tests located in `src/test/java/.../pact/`
- **Provider States**: Dynamic state management with setup/teardown
- **Multi-Protocol Support**: Separate verification for REST, gRPC, and Kafka (JSON and protobuf message formats)
- **Authentication Handling**: Automatic credential replacement for test scenarios
- **Error Scenario Testing**: Comprehensive error condition verification

**Build Configuration**:

- **Pact Plugin**: `au.com.dius.pact` version 4.6.17
- **Multiple Service Providers**: price, orderbook, and gRPC configurations
- **Broker Integration**: Automatic contract fetching and result publishing
- **OpenAPI Generation**: Automatic API interface generation
- **gRPC Support**: Protobuf compilation and gRPC service generation

### price-service-consumer

**Purpose**: Comprehensive client that consumes the price service API through multiple communication protocols.

**Key Features**:

- **REST API Client**: OpenAPI-generated client for HTTP operations
- **gRPC Client**: Type-safe gRPC client implementation
- **Kafka Consumer**: Asynchronous message processing (JSON and protobuf message formats)
- **Authentication**: Configurable authentication for secured endpoints
- **Error Handling**: Comprehensive error scenario handling

**Pact Contract Tests**:

- **Consumer Contract Definition**: Tests located in `src/test/java/.../client/`
- **Multiple Protocol Contracts**: REST, gRPC, and Kafka contract definitions
- **Flexible Matchers**: Type-based matching for robust contracts
- **Provider State Usage**: Dynamic state parameters for flexible testing
- **Error Scenario Coverage**: 404, 401, and other error condition testing
- **Authentication Testing**: Both successful and failed authentication scenarios

**Build Configuration**:

- **Pact Plugin**: Consumer contract testing and publishing
- **OpenAPI Client Generation**: Automatic client code generation
- **gRPC Support**: Protobuf compilation for gRPC clients
- **Kafka Integration**: Spring Kafka for message processing
- **Contract Publishing**: Automatic publishing to Pact Broker

### new-price-service-consumer

**Purpose**: Simplified consumer demonstrating how new services integrate into existing contract testing workflows.

**Key Features**:

- **REST API Only**: Focused on HTTP-based price service consumption
- **Simplified Architecture**: Demonstrates essential contract testing patterns
- **Independent Development**: Shows how new consumers can develop independently
- **Contract Evolution**: Illustrates contract compatibility and evolution

**Pact Contract Tests**:

- **Basic Contract Definition**: Essential contract patterns for new consumers
- **Provider State Integration**: Reuses existing provider states
- **Type-Safe Contracts**: Demonstrates proper matcher usage
- **Error Handling**: Basic error scenario coverage

**Build Configuration**:

- **Minimal Dependencies**: Focused on essential contract testing components
- **OpenAPI Integration**: Client generation for REST API consumption
- **Contract Publishing**: Integration with existing Pact Broker workflow

## Reference guides

- [Pact REST API Contract Testing Guide](docs/reference_guide/pact-rest-examples.md)
- [Pact gRPC API Contract Testing Guide](docs/reference_guide/pact-grpc-examples.md)

## Advanced Topics

### Contract Testing Strategies

**Consumer-Driven Contracts**: Consumers define what they need from providers, ensuring that provider changes don't
break actual consumer requirements.

**Provider States**: Dynamic test data setup that allows providers to create specific conditions for contract
verification.

**Contract Evolution**: Strategies for evolving contracts over time while maintaining backward compatibility.

### Multi-Protocol Support

**REST API Testing**: Standard HTTP-based contract testing with comprehensive error scenario coverage.

**gRPC Contract Testing**: Binary protocol testing with protobuf schema validation and type safety.

**Kafka Message Testing**: Asynchronous message contract testing with serialization validation.

### CI/CD Integration

**Branch-Based Testing**: Contract verification against specific consumer versions based on development branches.

**Deployment Gates**: Using contract verification results to control deployment decisions.

**Can I Deploy?**: Automated compatibility checking before service deployments.

## Troubleshooting

### Common Issues

**Port Conflicts**: Ensure ports 5432, 5433, and 9292 are available for PostgreSQL and Pact Broker.

**Docker Issues**: Verify Docker and Docker Compose are installed and running.

**Contract Verification Failures**: Check provider states and ensure test data setup is correct.

**Authentication Errors**: Verify that authentication credentials match between consumer tests and provider
configuration.

### Debugging Tips

**Pact Broker UI**: Use [http://localhost:9292](http://localhost:9292) to inspect contracts and verification results.

**Verbose Logging**: Enable debug logging in test configurations for detailed execution information.

**Contract Files**: Examine generated contract files in `build/pacts/` directories for contract structure.

**Provider States**: Verify that provider state setup creates the expected test data conditions.

### Getting Help

**Logs**: Check application logs and test output for detailed error information.

**Pact Documentation**: Refer to [official Pact documentation](https://docs.pact.io/) for detailed guidance.

**Community**: Join the Pact community for support and best practices sharing.

## Additional Resources

### Documentation

You can read the [Official Pact Documentation](https://docs.pact.io/) or see the docs snapshot included in this
project (2025.06.15).

**Recommended Reading** in the [docs](docs) directory:

- [CI/CD Setup Guide (aka Pact Nirvana)](docs/pact_nirvana.md)
- [Convince me](docs/faq/convinceme.md)
- [Versioning in the Pact Broker](docs/getting_started/versioning_in_the_pact_broker.md)
- [When to use Pact](docs/getting_started/what_is_pact_good_for.md)

### External Resources

- [Official Pact Documentation](https://docs.pact.io/)
- [Pact JVM Documentation](https://github.com/pact-foundation/pact-jvm)
- [Spring Boot and Pact Integration Guide](https://docs.pact.io/implementation_guides/jvm/provider/spring)
- [Consumer-Driven Contract Testing](https://martinfowler.com/articles/consumerDrivenContracts.html)
- [Microservices Testing Strategies](https://martinfowler.com/articles/microservice-testing/)

### Community and Support

- [Pact Foundation GitHub](https://github.com/pact-foundation)
- [Pact Slack Community](https://pact-foundation.slack.com/)
- [Stack Overflow - Pact Tag](https://stackoverflow.com/questions/tagged/pact)
