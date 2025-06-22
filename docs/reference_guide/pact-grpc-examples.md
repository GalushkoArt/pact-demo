# Pact gRPC Contract Testing Guide

This comprehensive guide demonstrates how to implement gRPC contract testing using Pact in Java applications. The
examples are based on a price service that provides financial instrument data through gRPC endpoints using Protocol
Buffers for serialization.

## Table of Contents

1. [Introduction to gRPC Contract Testing](#introduction-to-grpc-contract-testing)
2. [Protocol Buffer Schema Definition](#protocol-buffer-schema-definition)
3. [Consumer-Side gRPC Contract Testing](#consumer-side-grpc-contract-testing)
4. [Provider-Side gRPC Contract Verification](#provider-side-grpc-contract-verification)
5. [Advanced gRPC Patterns](#advanced-grpc-patterns)
6. [Error Handling in gRPC Contracts](#error-handling-in-grpc-contracts)
7. [Authentication and Metadata](#authentication-and-metadata)
8. [Best Practices for gRPC Contract Testing](#best-practices-for-grpc-contract-testing)
9. [References](#references)

## Introduction to gRPC Contract Testing

gRPC contract testing with Pact enables teams to verify that gRPC services can communicate correctly using Protocol
Buffers without requiring both services to be running simultaneously. This approach is particularly valuable for
microservices architectures where services communicate through strongly-typed gRPC interfaces.

Unlike REST API testing, gRPC contract testing must handle binary Protocol Buffer messages, streaming capabilities, and
gRPC-specific metadata. The Pact framework provides a specialized plugin system that understands Protocol Buffer schemas
and can generate appropriate mock responses for gRPC calls.

The consumer-driven approach remains the same: consumers define their expectations about the provider's gRPC service
behavior, and providers verify they can meet those expectations. However, the implementation differs significantly due
to gRPC's binary protocol and schema-driven nature.

gRPC contract testing provides several advantages over traditional integration testing. It catches breaking changes in
Protocol Buffer schemas early, validates that service implementations match their interface definitions, and ensures
that both client and server handle gRPC status codes correctly. Additionally, it verifies that metadata handling works
correctly across service boundaries.

## Protocol Buffer Schema Definition

Before implementing contract tests, it's essential to understand the Protocol Buffer schema that defines the gRPC
service interface. The schema serves as the contract specification that both consumer and provider must adhere to.

### Service Definition

The price service is defined in Protocol Buffers with clear method signatures and message types:

```protobuf
syntax = "proto3";

package com.example.priceservice.grpc;

option java_multiple_files = true;
option java_package = "com.example.priceservice.grpc";
option java_outer_classname = "PriceServiceProto";

import "google/protobuf/timestamp.proto";
import "price_update.proto";

service PriceService {
  rpc GetAllPrices(GetAllPricesRequest) returns (GetAllPricesResponse);
  rpc GetPrice(GetPriceRequest) returns (GetPriceResponse);
  rpc StreamPrices(StreamPricesRequest) returns (stream PriceUpdate);
}
```

This service definition establishes three key operations: retrieving all prices with pagination support, getting a
specific price by instrument ID, and streaming real-time price updates. Each operation has clearly defined request and
response message types.

### Message Type Definitions

The request and response messages define the data structures used in gRPC communication:

```protobuf
message GetAllPricesRequest {
  int32 page = 1;
  int32 size = 2;
}

message GetAllPricesResponse {
  repeated Price prices = 1;
  int32 total_count = 2;
  int32 page = 3;
  int32 size = 4;
}

message GetPriceRequest {
  string instrument_id = 1;
}

message GetPriceResponse {
  Price price = 1;
}
```

### Core Data Types

The core `Price` message represents the fundamental data structure:

```protobuf
message Price {
  string instrument_id = 1;
  double bid_price = 2;
  double ask_price = 3;
  google.protobuf.Timestamp last_updated = 4;
}
```

### Schema Evolution Considerations

Protocol Buffer schemas support backward and forward compatibility through careful field numbering and optional fields.
When writing contract tests, it's important to consider how schema changes might affect existing contracts. Adding
optional fields is generally safe, but removing fields or changing field types can break existing contracts.

The schema serves as the foundation for all contract testing activities. Both consumer and provider tests must reference
the same schema files to ensure consistency. The schema also determines what validation rules can be applied during
contract verification.

## Consumer-Side gRPC Contract Testing

Consumer-side gRPC contract testing involves creating tests that define expected gRPC service behavior using the Pact
protobuf plugin. These tests generate contracts that specify both the message structure and the gRPC metadata
requirements.

### Basic Test Setup and Configuration

The foundation of gRPC consumer testing requires specific configuration to enable the protobuf plugin and gRPC mock
server:

```java

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "price-service-provider-grpc",
        providerType = ProviderType.SYNCH_MESSAGE,
        pactVersion = PactSpecVersion.V4)
@MockServerConfig(
        implementation = MockServerImplementation.Plugin,
        registryEntry = "protobuf/transport/grpc"
)
public class GrpcPriceServicePactTest {
    private static final Instant timestamp = Instant.now();
}
```

The key differences from REST testing include the `ProviderType.SYNCH_MESSAGE` which indicates synchronous message-based
communication, and the `MockServerConfig` that specifies the protobuf plugin implementation. The `PactSpecVersion.V4` is
required for plugin support.

The `registryEntry` value "protobuf/transport/grpc" tells Pact to use the gRPC transport layer with Protocol Buffer
message encoding. This configuration enables the mock server to understand gRPC calls and generate appropriate
responses.

### Defining Basic gRPC Contracts

A simple gRPC contract defines the service method, request parameters, and expected response structure:

```java

@Pact(consumer = "price-service-consumer")
public V4Pact getAllPricesPact(PactBuilder builder) {
    return builder
            .usingPlugin("protobuf")
            .given("prices exist")
            .given("valid token")
            .expectsToReceive("get all prices", "core/interaction/synchronous-message")
            .with(Map.of(
                    "pact:proto", filePath("../proto/price_service.proto"),
                    "pact:content-type", "application/grpc",
                    "pact:proto-service", "PriceService/GetAllPrices",
                    "requestMetadata", Map.of("authorization", "Bearer valid-token"),
                    "responseMetadata", Map.of(
                            "x-authenticated", "matching(type, 'true')"
                    ),
                    "request", Map.of(),
                    "response", List.of(Map.of(
                            "prices", List.of(Map.of(
                                    "instrument_id", "matching(type, 'AAPL')",
                                    "bid_price", "matching(number, 175.50)",
                                    "ask_price", "matching(number, 175.75)"
                            )),
                            "page", "matching(number, 1)",
                            "size", "matching(number, 1)"
                    ))
            ))
            .toPact();
}
```

This contract demonstrates several important concepts. The `usingPlugin("protobuf")` declaration enables Protocol Buffer
support. The `pact:proto` field references the schema file that defines the service interface. The `pact:proto-service`
specifies the exact service method being tested.

The request and response sections use Pact's matching expressions to validate message structure. The
`matching(type, 'AAPL')` expression ensures the field is a string but allows any string value, while
`matching(number, 175.50)` validates numeric fields without requiring exact values.

### Request and Response Message Handling

gRPC contracts must handle both the message payload and the gRPC metadata. The request section defines the message
fields:

```java
"request",Map.of(
        "instrument_id","AAPL"
)
```

For the `GetPrice` method, the request includes the specific instrument ID. Empty request maps are used for methods that
don't require parameters, such as `GetAllPrices`.

The response section defines the expected message structure:

```java
"response",List.of(Map.of(
        "price", Map.of(
                           "instrument_id", "AAPL",
                           "bid_price","matching(number, 175.50)",
                           "ask_price","matching(number, 175.75)",
                           "last_updated",Map.of(
                           "seconds", format("matching(integer, %d)", timestamp.getEpochSecond()),
        "nanos",

format("matching(integer, %d)",timestamp.getNano())
        )
        )
        ))
```

This response structure mirrors the Protocol Buffer message definition. The `last_updated` field demonstrates how to
handle complex types like `google.protobuf.Timestamp`, which is represented as separate `seconds` and `nanos` fields.

### Metadata and Authentication Handling

gRPC metadata is equivalent to HTTP headers and is used for cross-cutting concerns like authentication:

```java
"requestMetadata",Map.of("authorization","Bearer valid-token"),
"responseMetadata",Map.

of(
        "x-authenticated","matching(type, 'true')"
)
```

Request metadata defines what the client will send, while response metadata specifies what the server should return. The
authentication token in request metadata is validated by the server, and the response metadata confirms successful
authentication.

### Provider State Integration

Provider states work similarly to REST contracts but are particularly important for gRPC services that might have
complex setup requirements:

```java
.given("price with ID exists",Map.of("instrumentId", "AAPL"))
```

The provider state parameters can be used to set up specific test scenarios. This is especially useful for gRPC services
that interact with databases or external systems.

### Test Method Implementation

The test method verifies that the gRPC client can handle the mock server responses correctly:

```java

@Test
@PactTestFor(pactMethod = "getAllPricesPact", providerType = ProviderType.SYNCH_MESSAGE)
void testGetAllPrices(MockServer mockServer, V4Interaction.SynchronousMessages interaction) {
    var optional = getClient(mockServer).getAllPrices(null, null);
    assertThat(optional.isPresent()).isTrue();
    var response = optional.get();
    assertThat(response.getPage()).isEqualTo(1);
    assertThat(response.getSize()).isEqualTo(1);
    List<Price> allPrices = response.getPricesList();
    assertThat(allPrices).hasSize(1);
    assertThat(allPrices.getFirst().getInstrumentId()).isEqualTo("AAPL");
    assertThat(allPrices.getFirst().getBidPrice()).isEqualTo(175.50);
    assertThat(allPrices.getFirst().getAskPrice()).isEqualTo(175.75);
}
```

The test method receives both the mock server instance and the interaction details. The mock server provides the port
and connection information needed to create a gRPC client. The assertions verify that the client correctly parses the
response and extracts the expected data.

### gRPC Client Setup

Creating a gRPC client for testing requires proper channel configuration and interceptor setup:

```java
private GrpcPriceClient getClient(MockServer mockServer) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + mockServer.getPort())
            .usePlaintext()
            .intercept(new TokenClientInterceptor("valid-token"))
            .build();
    PriceServiceGrpc.PriceServiceBlockingStub stub = PriceServiceGrpc.newBlockingStub(channel);
    GrpcPriceClient grpcPriceClient = new GrpcPriceClient();
    grpcPriceClient.setPriceServiceStub(stub);
    return grpcPriceClient;
}
```

The client setup includes several important elements. The `usePlaintext()` call disables TLS for testing, which is
appropriate for local mock servers. The `TokenClientInterceptor` adds authentication metadata to requests, matching the
contract requirements.

The blocking stub is used for synchronous calls, though async stubs could be used for more complex scenarios. The client
wrapper (`GrpcPriceClient`) provides a higher-level interface that handles gRPC exceptions and converts them to
application-specific responses.

### Timestamp Handling in gRPC

Protocol Buffer timestamps require special handling due to their representation as separate seconds and nanoseconds
fields:

```java
private static final Instant timestamp = Instant.now();

// In contract definition
"last_updated",Map.

of(
   "seconds",format("matching(integer, %d)", timestamp.getEpochSecond()),
        "nanos",

format("matching(integer, %d)",timestamp.getNano())
        )

// In test assertions
assertThat(price.get().

getLastUpdated().

getSeconds()).

isEqualTo(timestamp.getEpochSecond());

assertThat(price.get().

getLastUpdated().

getNanos()).

isEqualTo(timestamp.getNano());
```

This approach ensures that timestamp values are properly validated while maintaining consistency between contract
definition and test assertions. The use of exact values for timestamps is appropriate when testing specific scenarios,
but type matchers could be used for more flexible validation.

## Provider-Side gRPC Contract Verification

Provider-side gRPC contract verification ensures that the actual gRPC service implementation can fulfill the contracts
defined by consumers. This involves running the gRPC server and executing consumer contracts against it using the Pact
plugin system.

### Provider Test Configuration

The provider test setup requires specific configuration to enable gRPC plugin verification:

```java

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "grpc.server.port=19090",
        "admin.username=admin",
        "admin.password=password"
})
@Provider("price-service-provider-grpc")
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}"),
        providerBranch = "${GIT_BRANCH:master}"
)
@VersionSelector
public class PriceServiceProviderGrpcPactTest {
    @Autowired
    private PriceJpaRepository priceJpaRepository;
}
```

The key difference from REST provider testing is the gRPC server port configuration. The `grpc.server.port` property
ensures the gRPC server runs on a predictable port that the test framework can connect to. The provider name must match
the name used in consumer contracts.

### Plugin Test Target Configuration

gRPC verification requires a specialized test target that understands the gRPC protocol:

```java

@BeforeEach
void setUp(PactVerificationContext context) {
    context.setTarget(new PluginTestTarget(Map.of(
            "host", "localhost",
            "port", 19090,
            "transport", "grpc"
    )));
}
```

The `PluginTestTarget` configures the verification framework to connect to the gRPC server using the appropriate
transport. The host and port must match the running gRPC server configuration. The transport specification tells the
plugin system to use gRPC protocol for communication.

### Consumer Version Selection for gRPC

The consumer version selection strategy for gRPC services follows the same patterns as REST services:

```java

@PactBrokerConsumerVersionSelectors
public static SelectorBuilder consumerVersionSelectors() {
    return new SelectorBuilder()
            .mainBranch()
            .latestTag("dev");
}
```

This configuration ensures that the provider verifies against both main branch consumers and the latest development
versions. For gRPC services, this is particularly important because Protocol Buffer schema changes can have significant
compatibility implications.

### Verification Template Method

The verification template for gRPC is simpler than REST because there's no need for request modification:

```java

@TestTemplate
@ExtendWith(PactVerificationInvocationContextProvider.class)
void testTemplate(PactVerificationContext context) {
    context.verifyInteraction();
}
```

Unlike REST verification, gRPC verification doesn't typically require request modification because authentication and
other metadata are handled through gRPC interceptors rather than manual header manipulation.

### Provider State Management for gRPC

Provider states for gRPC services handle the same data setup requirements as REST services but may need to consider
gRPC-specific concerns:

```java

@State(value = "prices exist", action = StateChangeAction.SETUP)
@Transactional
public void pricesExist() throws InterruptedException {
    if (pricesExist.tryLock(30, TimeUnit.SECONDS)) {
        try {
            priceJpaRepository.findById("AAPL").ifPresentOrElse(
                    e -> {
                    },
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
}
```

The data setup is identical to REST services because the underlying business logic and data requirements are the same.
The locking mechanism prevents race conditions when multiple gRPC calls are verified concurrently.

### Parameterized Provider States

Dynamic provider states work the same way for gRPC as for REST services:

```java

@State(value = "price with ID exists", action = StateChangeAction.SETUP)
@Transactional
public Map<String, String> priceWithIdExists(Map<String, String> param) {
    var parameters = new HashMap<>(param);
    var instrumentId = parameters.computeIfAbsent("instrumentId", id -> TestDataFactory.randomInstrumentId());
    priceJpaRepository.findById(instrumentId).ifPresent(price -> priceJpaRepository.delete(price));

    var price = PriceEntity.builder()
            .instrumentId(instrumentId)
            .bidPrice(new BigDecimal("175.50"))
            .askPrice(new BigDecimal("175.75"))
            .lastUpdated(Instant.now())
            .build();
    priceJpaRepository.save(price);
    return parameters;
}
```

The parameterized approach allows the same provider state to work with different instrument IDs, making tests more
flexible and reusable. The returned parameters can be used by consumer contracts to substitute values in request and
response messages.

### State Cleanup for gRPC

Cleanup methods ensure test isolation:

```java

@State(value = "price with ID exists", action = StateChangeAction.TEARDOWN)
@Transactional
public void priceWithIdExistsCleanup(Map<String, String> parameters) {
    Optional.ofNullable(parameters.get("instrumentId")).ifPresent(id -> priceJpaRepository.deleteById(id));
}
```

Proper cleanup is especially important for gRPC services because they often handle high-throughput scenarios where test
pollution could affect performance measurements or concurrent test execution.

### Error State Setup

Error scenarios require ensuring that the expected conditions exist:

```java

@State("price with ID UNKNOWN does not exist")
@Transactional
public void priceWithIdUnknownDoesNotExist() {
    priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
}
```

This state method ensures that the specified resource doesn't exist, allowing the gRPC service to return the expected
`NOT_FOUND` status code. Testing error conditions is crucial for gRPC services because clients need to handle various
gRPC status codes correctly.

### gRPC Server Configuration Considerations

When configuring the gRPC server for testing, several factors should be considered:

```java
@SpringBootTest(properties = {
        "grpc.server.port=19090",
        "admin.username=admin",
        "admin.password=password"
})
```

The fixed port assignment ensures predictable connectivity for the test framework. In production environments, the port
might be dynamic, but for testing, a fixed port simplifies configuration and debugging.

The authentication properties configure any security interceptors that might be part of the gRPC service. These should
match the credentials expected by the consumer contracts.

## Advanced gRPC Patterns

Advanced gRPC contract testing involves handling complex scenarios such as streaming, custom metadata, and sophisticated
error handling. These patterns ensure that contracts cover the full range of gRPC capabilities while maintaining test
reliability and maintainability.

### Custom Metadata Patterns

gRPC metadata can carry complex information beyond simple authentication tokens:

```java
"requestMetadata",Map.of(
   "authorization","Bearer valid-token",
           "x-request-id","matching(regex, '[a-f0-9-]{36}', '123e4567-e89b-12d3-a456-426614174000')",
           "x-client-version","matching(type, '1.0.0')",
           "x-trace-id","matching(type, 'trace-12345')"
),
"responseMetadata",Map.

of(
   "x-authenticated","matching(type, 'true')",
           "x-rate-limit-remaining","matching(number, 100)",
           "x-response-time-ms","matching(number, 50)"
)
```

This pattern shows how to validate complex metadata while allowing for variation in values that might change between
requests. The regex matcher for request IDs ensures proper UUID format validation.

### Protocol Buffer Any Type Handling

When gRPC services use the `google.protobuf.Any` type for flexible message content, contracts must handle the type URL
and value encoding:

```java
"flexible_data",Map.of(
   "type_url","matching(type, 'type.googleapis.com/com.example.CustomMessage')",
           "value","matching(type, 'base64-encoded-data')"
)
```

The Any type requires special handling because it encapsulates arbitrary Protocol Buffer messages. The contract
validates the structure without requiring knowledge of the specific message type being carried.

### Oneof Field Handling

Protocol Buffer `oneof` fields represent mutually exclusive options that require careful contract design:

```protobuf
message PriceRequest {
  oneof identifier {
    string instrument_id = 1;
    string isin = 2;
    string cusip = 3;
  }
}
```

Contracts for oneof fields should test each possible variant:

```java
// Contract for instrument_id variant
"request",Map.of(
     "instrument_id","AAPL"
)

// Contract for isin variant  
"request",Map.

of(
    "isin","US0378331005"
)
```

Each variant should have its own contract to ensure that the provider correctly handles all possible input formats.

### Repeated Field Validation

Proto example:

```protobuf
message Person {
  string name = 1;
  int32 id = 2;
  string email = 3;

  enum PhoneType {
    MOBILE = 0;
    HOME = 1;
    WORK = 2;
  }

  message PhoneNumber {
    string number = 1;
    PhoneType type = 2 [default = HOME];
  }

  repeated PhoneNumber phones = 4;
}
```

Protocol Buffer repeated fields (arrays) require careful validation strategies:

```java
"phones",Map.of(
    "number","matching(regex, '(\\+\\d+)?(\\d+\\-)?\\d+\\-\\d+', '+61-03-1234-5678')" // Phone numbers must match a regular expression
    // We don't include type, as it is an emum and has a default value, so it is optional
    // but we could have done something like matching(equalTo, 'WORK')
)
```

You can apply matching rules to enforce the minimum or maximum number fo fields, as well as applying rules for each item.
For instance, assume that numbers is a repeated String field. You could define

```java
"numbers":"atLeast(1), eachValue(matching(regex, '\\d+', '100))"
```

This would also work if numbers was a numeric repeated field. In fact, it would work with any primitive field by
applying the regex to the string representation of the field value.

### Error Detail Handling

gRPC error responses can include detailed error information using the `google.rpc.Status` message:

```java
"responseMetadata",Map.of(
        "grpc-status","INVALID_ARGUMENT",
        "grpc-message","matching(type, 'Invalid instrument ID format')",
        "grpc-status-details-bin","matching(type, 'base64-encoded-error-details')"
)
```

The error details can contain structured information about validation failures or other error conditions. Testing these
details ensures that clients can provide meaningful error messages to users.

### Interceptor Integration Patterns

gRPC interceptors handle cross-cutting concerns like authentication, logging, and metrics. Contract tests should account
for interceptor behavior:

```java
// Authentication interceptor adds metadata
"responseMetadata",Map.of(
   "x-authenticated-user","matching(type, 'admin')",
   "x-auth-method","matching(type, 'bearer-token')"
)

// Metrics interceptor adds timing information
"responseMetadata",Map.of(
   "x-processing-time-ms","matching(number, 100)",
   "x-db-query-count","matching(number, 2)"
)
```

These patterns ensure that interceptor behavior is properly validated and documented in contracts.

## Error Handling in gRPC Contracts

gRPC error handling differs significantly from REST APIs because it uses a standardized set of status codes and can
include detailed error information. Contract testing must thoroughly cover error scenarios to ensure robust client
implementations and proper server error reporting.

### gRPC Status Code Testing

gRPC defines a standard set of status codes that represent different types of errors. Each status code requires specific
contract testing patterns:

```java

@Pact(consumer = "price-service-consumer")
public V4Pact getPriceNotFoundPact(PactBuilder builder) {
    return builder
            .usingPlugin("protobuf")
            .given("price with ID UNKNOWN does not exist")
            .expectsToReceive("price not found", "core/interaction/synchronous-message")
            .with(Map.of(
                    "pact:proto", filePath("../proto/price_service.proto"),
                    "pact:content-type", "application/grpc",
                    "pact:proto-service", "PriceService/GetPrice",
                    "requestMetadata", Map.of("authorization", "Bearer valid-token"),
                    "request", Map.of(
                            "instrument_id", "UNKNOWN"
                    ),
                    "responseMetadata", Map.of(
                            "grpc-status", "NOT_FOUND",
                            "grpc-message", "matching(type, 'Price not found')"
                    )
            ))
            .toPact();
}
```

The `NOT_FOUND` status code is the gRPC equivalent of HTTP 404. The contract specifies both the status code and a
human-readable error message. The message uses a type matcher to allow for variations in the exact wording while
ensuring a meaningful message is provided.

### Authentication Error Handling

Authentication failures in gRPC use the `UNAUTHENTICATED` status code:

```java

@Pact(consumer = "price-service-consumer")
public V4Pact missingTokenPact(PactBuilder builder) {
    return builder
            .usingPlugin("protobuf")
            .given("price with ID exists", Map.of("instrumentId", "AAPL"))
            .given("missing token")
            .expectsToReceive("missing token get price", "core/interaction/synchronous-message")
            .with(Map.of(
                    "pact:proto", filePath("../proto/price_service.proto"),
                    "pact:content-type", "application/grpc",
                    "pact:proto-service", "PriceService/GetPrice",
                    "request", Map.of(
                            "instrument_id", "AAPL"
                    ),
                    "responseMetadata", Map.of(
                            "grpc-status", "UNAUTHENTICATED",
                            "grpc-message", "matching(type, 'Missing token')"
                    )
            ))
            .toPact();
}
```

Notice that the `authorization` metadata is deliberately omitted from the request to simulate the missing token
scenario. This tests the server's ability to detect and properly respond to authentication failures.

### Client-Side Error Handling Verification

The consumer test must verify that the client correctly handles gRPC status codes:

```java

@Test
@PactTestFor(pactMethod = "getPriceNotFoundPact", providerType = ProviderType.SYNCH_MESSAGE)
void testGetPriceNotFound(MockServer mockServer, V4Interaction.SynchronousMessages interaction) {
    var price = getClient(mockServer).getPrice("UNKNOWN");
    assertThat(price).isEmpty();
}

@Test
@PactTestFor(pactMethod = "missingTokenPact", providerType = ProviderType.SYNCH_MESSAGE)
void testMissingToken(MockServer mockServer) {
    assertThatThrownBy(() -> getClient(mockServer).getPrice("AAPL"))
            .isInstanceOf(StatusRuntimeException.class)
            .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.UNAUTHENTICATED);
}
```

The first test shows a client that gracefully handles `NOT_FOUND` by returning an empty Optional. The second test
verifies that authentication errors are properly propagated as `StatusRuntimeException` with the correct status code.

### Validation Error Patterns

Input validation errors typically use the `INVALID_ARGUMENT` status code:

```java

@Pact(consumer = "price-service-consumer")
public V4Pact invalidInstrumentIdPact(PactBuilder builder) {
    return builder
            .usingPlugin("protobuf")
            .given("invalid instrument ID format")
            .expectsToReceive("invalid instrument ID", "core/interaction/synchronous-message")
            .with(Map.of(
                    "pact:proto", filePath("../proto/price_service.proto"),
                    "pact:content-type", "application/grpc",
                    "pact:proto-service", "PriceService/GetPrice",
                    "requestMetadata", Map.of("authorization", "Bearer valid-token"),
                    "request", Map.of(
                            "instrument_id", "INVALID_FORMAT_123!"
                    ),
                    "responseMetadata", Map.of(
                            "grpc-status", "INVALID_ARGUMENT",
                            "grpc-message", "matching(type, 'Invalid instrument ID format')"
                    )
            ))
            .toPact();
}
```

This pattern tests server-side validation logic and ensures that clients receive meaningful error messages for invalid
input.

### Provider State Error Setup

Provider states for error scenarios must ensure the appropriate conditions exist:

```java

@State("price with ID UNKNOWN does not exist")
@Transactional
public void priceWithIdUnknownDoesNotExist() {
    priceJpaRepository.findById("UNKNOWN").ifPresent(price -> priceJpaRepository.delete(price));
}

@State("invalid instrument ID format")
public void invalidInstrumentIdFormat() {
    // No setup needed - the service will validate the format
}
```

Some error states require active setup (like ensuring data doesn't exist), while others rely on the service's built-in
validation logic.

### Error Message Localization

For internationalized applications, error messages might vary by locale:

```java
"responseMetadata",Map.of(
   "grpc-status","NOT_FOUND",
   "grpc-message","matching(type, 'Price not found')",
   "content-language","matching(type, 'en-US')"
)
```

The contract can specify the expected language while allowing for different localized messages.

### Structured Error Details

gRPC supports structured error details using the `google.rpc.Status` message:

```java
"responseMetadata",Map.of(
   "grpc-status","INVALID_ARGUMENT",
   "grpc-message","matching(type, 'Validation failed')",
   "grpc-status-details-bin","matching(type, 'base64-encoded-error-details')"
)
```

The error details can contain structured information about validation failures, field-specific errors, or other detailed
error information that clients can use to provide better user experiences.

Comprehensive error testing ensures that gRPC services provide consistent, meaningful error responses and that clients
handle all error scenarios gracefully. This is particularly important for gRPC services because the binary protocol and
status code system differ significantly from traditional HTTP error handling patterns.

## Authentication and Metadata

gRPC authentication and metadata handling are fundamental aspects of secure service communication. Unlike REST APIs that
use HTTP headers, gRPC uses metadata for carrying authentication tokens, request IDs, and other cross-cutting concerns.
Contract testing must thoroughly validate metadata handling to ensure secure and reliable service interactions.

### Bearer Token Authentication Patterns

The most common gRPC authentication pattern uses Bearer tokens in metadata:

```java
"requestMetadata",Map.of("authorization","Bearer valid-token")
```

This metadata entry follows the same pattern as HTTP Authorization headers but is transmitted through gRPC's metadata
mechanism. The consumer contract specifies that the client will send this metadata with every authenticated request.

The corresponding provider verification ensures that the server correctly validates these tokens:

```java

@State("valid token")
public void validToken() {
    // no-op, token validated by interceptor
}
```

The empty provider state method indicates that token validation is handled by gRPC interceptors rather than requiring
specific test setup. This separation of concerns allows the authentication logic to be tested independently of business
logic.

### Missing Authentication Testing

Testing authentication failures is crucial for security validation:

```java

@Pact(consumer = "price-service-consumer")
public V4Pact missingTokenPact(PactBuilder builder) {
    return builder
            .usingPlugin("protobuf")
            .given("missing token")
            .expectsToReceive("missing token get price", "core/interaction/synchronous-message")
            .with(Map.of(
                    "pact:proto", filePath("../proto/price_service.proto"),
                    "pact:content-type", "application/grpc",
                    "pact:proto-service", "PriceService/GetPrice",
                    "request", Map.of("instrument_id", "AAPL"),
                    "responseMetadata", Map.of(
                            "grpc-status", "UNAUTHENTICATED",
                            "grpc-message", "matching(type, 'Missing token')"
                    )
            ))
            .toPact();
}
```

Notice that the `requestMetadata` section is completely omitted, simulating a client that fails to include
authentication credentials. The expected response includes the `UNAUTHENTICATED` status code, which is the gRPC
equivalent of HTTP 401.

### Client-Side Interceptor Implementation

The consumer test demonstrates how to implement authentication on the client side:

```java
private GrpcPriceClient getClient(MockServer mockServer) {
    ManagedChannel channel = ManagedChannelBuilder.forTarget("127.0.0.1:" + mockServer.getPort())
            .usePlaintext()
            .intercept(new TokenClientInterceptor("valid-token"))
            .build();
    // ... rest of client setup
}
```

The `TokenClientInterceptor` automatically adds authentication metadata to all requests. This pattern ensures that
authentication is handled consistently across all service calls without requiring manual metadata management in business
logic.

### Response Metadata Validation

Servers often return metadata to confirm authentication status or provide additional security information:

```java
"responseMetadata",Map.of(
   "x-authenticated","matching(type, 'true')",
   "x-auth-method","matching(type, 'bearer-token')",
   "x-user-id","matching(type, 'admin')"
)
```

This response metadata provides confirmation that authentication was successful and includes information about the
authenticated user. The type matchers allow for variation in the exact values while ensuring the essential information
is present.

### Request Tracing and Correlation

Modern distributed systems often use correlation IDs and tracing information:

```java
"requestMetadata",Map.of(
   "authorization","Bearer valid-token",
   "x-request-id","matching(regex, '[a-f0-9-]{36}', '123e4567-e89b-12d3-a456-426614174000')",
   "x-trace-id","matching(type, 'trace-12345')",
   "x-span-id","matching(type, 'span-67890')"
)
```

The regex matcher for request IDs ensures proper UUID format validation while allowing for different actual values.
Tracing metadata helps with debugging and monitoring in production environments.

### Custom Authentication Schemes

Some services use custom authentication schemes beyond simple Bearer tokens:

```java
"requestMetadata",Map.of(
   "x-api-key","matching(type, 'api-key-12345')",
   "x-signature","matching(regex, '[a-f0-9]{64}', 'abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890')",
   "x-timestamp","matching(number, 1640995200)"
)
```

This pattern demonstrates API key authentication with request signing, where the signature is computed from the request
content and timestamp. The regex matcher validates the signature format while allowing for different actual signatures.

### Role-Based Access Control Metadata

Services implementing role-based access control often include role information in metadata:

```java
"requestMetadata",Map.of(
   "authorization","Bearer valid-token",
   "x-user-role","matching(type, 'admin')",
   "x-permissions","matching(type, 'read,write,delete')"
),
"responseMetadata",Map.of(
   "x-permission-check","matching(type, 'passed')",
   "x-allowed-operations","matching(type, 'get,update,delete')"
)
```

The response metadata confirms that permission checks were performed and indicates what operations the user is
authorized to perform.

### Error Context Metadata

When errors occur, additional context can be provided through metadata:

```java
"responseMetadata",Map.of(
   "grpc-status","INVALID_ARGUMENT",
   "grpc-message","matching(type, 'Validation failed')",
   "x-error-code","matching(type, 'INVALID_INSTRUMENT_ID')",
   "x-error-details","matching(type, 'Instrument ID must be 1-5 uppercase letters')",
   "x-request-id","matching(type, '123e4567-e89b-12d3-a456-426614174000')"
)
```

The error metadata includes structured error codes and detailed messages that clients can use to provide better user
experiences.

### Metadata Validation Best Practices

When designing metadata contracts, several best practices should be followed:

1. **Use Type Matchers for Variable Values**: Most metadata values will vary between requests, so type matchers are more
   appropriate than exact values.

2. **Include Security-Critical Metadata**: Always test authentication and authorization metadata to ensure security
   mechanisms work correctly.

3. **Test Both Presence and Absence**: Create contracts for both successful authentication and authentication failures.

4. **Validate Format When Important**: Use regex matchers for metadata that must follow specific formats (like UUIDs or
   timestamps).

5. **Consider Backward Compatibility**: When adding new metadata fields, ensure that older clients can still function
   without them.

The metadata system in gRPC provides a powerful mechanism for handling cross-cutting concerns, and thorough contract
testing ensures that these mechanisms work correctly across service boundaries. Proper metadata testing is essential for
building secure, observable, and maintainable gRPC services.

## Best Practices for gRPC Contract Testing

Effective gRPC contract testing requires understanding the unique characteristics of Protocol Buffer-based communication
and applying proven patterns that ensure reliable, maintainable tests. These best practices have been developed through
real-world experience with gRPC services in production environments.

### Schema Management and Versioning

Protocol Buffer schemas are the foundation of gRPC contract testing, and proper schema management is crucial for
long-term success.

#### Centralized Schema Storage

Store Protocol Buffer schemas in a centralized location that both consumer and provider tests can access:

```java
"pact:proto",filePath("../proto/price_service.proto")
```

The relative path approach works well for monorepos, but consider using absolute paths or schema registries for
distributed development. The schema file must be identical between consumer and provider tests to ensure contract
compatibility.

#### Schema Evolution Strategy

When evolving Protocol Buffer schemas, follow backward compatibility rules:

```protobuf
message Price {
  string instrument_id = 1;
  double bid_price = 2;
  double ask_price = 3;
  google.protobuf.Timestamp last_updated = 4;
  // New optional fields should use higher field numbers
  optional string currency = 5;  // Safe to add
  // Never reuse field numbers or change field types
}
```

Contract tests should include scenarios that verify backward compatibility when new fields are added. This ensures that
older clients continue to work when servers are updated with new schema versions.

#### Field Number Management

Maintain a registry of used field numbers to prevent conflicts:

```protobuf
// Reserved field numbers for future use or deprecated fields
    reserved 10 to 15;
    reserved "deprecated_field_name";
```

This practice prevents accidental reuse of field numbers that could break compatibility with existing clients.

### Test Organization and Structure

Organize gRPC contract tests to maximize maintainability and coverage while minimizing duplication.

#### Separate Test Classes by Service

Create separate test classes for each gRPC service:

```java
public class PriceServicePactTest {
    // Tests for PriceService methods
}

public class OrderServicePactTest {
    // Tests for OrderService methods  
}
```

This organization makes tests easier to maintain and allows for service-specific configuration and setup.

### Provider State Design Patterns

Effective provider state management is crucial for reliable gRPC contract testing.

#### Parameterized States for Flexibility

Use parameterized provider states to create flexible, reusable test scenarios:

```java

@State(value = "price with ID exists", action = StateChangeAction.SETUP)
@Transactional
public Map<String, String> priceWithIdExists(Map<String, String> param) {
    var parameters = new HashMap<>(param);
    var instrumentId = parameters.computeIfAbsent("instrumentId", id -> TestDataFactory.randomInstrumentId());
    // Setup logic using the instrumentId
    return parameters;
}
```

This pattern allows the same provider state to work with different data values, reducing test duplication and improving
maintainability.

#### Atomic State Operations

Ensure that provider state setup and teardown operations are atomic:

```java

@State(value = "prices exist", action = StateChangeAction.SETUP)
@Transactional
public void pricesExist() throws InterruptedException {
    if (pricesExist.tryLock(30, TimeUnit.SECONDS)) {
        try {
            // Atomic setup operations
        } finally {
            pricesExist.unlock();
        }
    }
}
```

The combination of transactions and locking prevents race conditions when multiple contracts are verified concurrently.

#### Minimal State Setup

Keep provider state setup minimal and focused:

```java

@State("valid token")
public void validToken() {
    // no-op, token validated by interceptor
}
```

When the required state is handled by infrastructure (like authentication interceptors), empty state methods document
the requirement without unnecessary setup code.

### Error Testing Strategies

Comprehensive error testing is essential for robust gRPC services.

#### Test Error Message Quality

Validate that error messages provide useful information:

```java
"grpc-message","matching(type, 'Price not found for instrument AAPL')"
```

Use type matchers to allow for message variations while ensuring that meaningful information is provided to clients.

#### Include Error Context

Test that error responses include appropriate context:

```java
"responseMetadata",Map.of(
   "grpc-status","INVALID_ARGUMENT",
   "grpc-message","matching(type, 'Validation failed')",
   "x-error-code","matching(type, 'INVALID_INSTRUMENT_ID')",
   "x-request-id","matching(type, '123e4567-e89b-12d3-a456-426614174000')"
)
```

Error context helps with debugging and provides clients with structured error information.

### Metadata Testing Best Practices

Metadata handling is crucial for gRPC services and requires careful testing.

#### Test Required Metadata

Always test that required metadata is properly validated:

```java
// Test with required metadata present
"requestMetadata",Map.of("authorization","Bearer valid-token")

// Test with required metadata missing  
// (omit requestMetadata section entirely)
```

This ensures that services properly enforce metadata requirements.

#### Validate Metadata Propagation

For services that propagate metadata to downstream services, test the propagation:

```java
"requestMetadata",Map.of(
        "x-trace-id","matching(type, 'trace-12345')"
),
"responseMetadata",Map.of(
        "x-trace-id","matching(type, 'trace-12345')"  // Same value propagated
)
```

Metadata propagation testing ensures that tracing and correlation information flows correctly through service chains.

### Client Implementation Patterns

gRPC client implementations should follow consistent patterns that are validated through contract testing.

#### Proper Exception Handling

Ensure that clients handle gRPC exceptions appropriately:

```java

@Test
void testMissingToken(MockServer mockServer) {
    assertThatThrownBy(() -> getClient(mockServer).getPrice("AAPL"))
            .isInstanceOf(StatusRuntimeException.class)
            .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.Code.UNAUTHENTICATED);
}
```

This pattern verifies that clients convert gRPC status codes into appropriate application exceptions.

#### Graceful Degradation

Test that clients handle service unavailability gracefully:

```java
var price = getClient(mockServer).getPrice("UNKNOWN");

assertThat(price).isEmpty();  // Graceful handling of NOT_FOUND
```

Graceful degradation testing ensures that client applications remain functional even when some service calls fail.

### Continuous Integration Integration

gRPC contract tests should integrate smoothly with CI/CD pipelines.

#### Environment Configuration

Use environment variables for configuration that varies between environments:

```java
@PactBroker(
        url = "${PACT_URL:http://localhost:9292}",
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USER:pact}", password = "${PACT_BROKER_PASSWORD:pact}")
)
```

This allows the same test code to work in different environments without modification.

#### Parallel Test Execution

Design tests to support parallel execution:

```java
@SpringBootTest(properties = {
        "grpc.server.port=0"  // Use random port
})
```

Random port assignment prevents conflicts when multiple test suites run concurrently.

### Documentation and Maintenance

Maintain comprehensive documentation for gRPC contract tests.

#### Document Provider States

Clearly document what each provider state represents:

```java
/**
 * Sets up a scenario where prices exist for multiple instruments.
 * Creates test data for AAPL and MSFT with realistic bid/ask spreads.
 * Used by contracts that test bulk price retrieval operations.
 */
@State(value = "prices exist", action = StateChangeAction.SETUP)
public void pricesExist() {
}
```

Good documentation helps team members understand test scenarios and maintain tests effectively.

#### Version Compatibility Matrix

Maintain a matrix of tested version combinations:

```
Consumer v1.0 ↔ Provider v1.0 ✓
Consumer v1.0 ↔ Provider v1.1 ✓  
Consumer v1.1 ↔ Provider v1.0 ✓
Consumer v1.1 ↔ Provider v1.1 ✓
```

This matrix helps track compatibility and plan migration strategies.

These best practices ensure that gRPC contract tests provide reliable protection against breaking changes while
remaining maintainable as services evolve. The key is balancing thorough testing with practical maintainability,
focusing on the most important scenarios while avoiding over-testing of implementation details.

## References

1. [Pact Plugin Matching Rule Documentation](https://github.com/pact-foundation/pact-plugins/blob/main/docs/matching-rule-definition-expressions.md)
2. [Pact Protobuf/gRPC plugin](https://github.com/pactflow/pact-protobuf-plugin/blob/main/README.md)
3. [gRPC Official Documentation](https://grpc.io/docs/)
4. [Protocol Buffers Language Guide](https://developers.google.com/protocol-buffers/docs/proto3)
5. [gRPC Status Codes](https://grpc.github.io/grpc/core/md_doc_statuscodes.html)
6. [Pact JVM gRPC Support](https://github.com/pact-foundation/pact-jvm)
7. [Official Pact Documentation](https://docs.pact.io/)
8. [gRPC Java Documentation](https://grpc.io/docs/languages/java/)
9. [Protocol Buffers Java Tutorial](https://developers.google.com/protocol-buffers/docs/javatutorial)
10. [gRPC Authentication Guide](https://grpc.io/docs/guides/auth/)
11. [Pact Plugin System](https://docs.pact.io/plugins)
12. [gRPC Error Handling Best Practices](https://grpc.io/docs/guides/error/)
13. [Protocol Buffers Schema Evolution](https://developers.google.com/protocol-buffers/docs/proto3#updating)
