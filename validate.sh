#!/bin/bash

# Script to validate the build and run tests with Pact Broker and PostgreSQL

# Exit on error
set -e

echo "Starting validation of enhanced Pact Demo project..."

# Step 1: Start PostgreSQL and Pact Broker
echo "Starting PostgreSQL and Pact Broker..."
docker-compose down -v
docker-compose up -d

# Wait for services to be ready
echo "Waiting for services to be ready..."
sleep 10

# Step 2: Build the project
echo "Building the project..."
./gradlew clean build -x test

# Step 3: Run consumer tests and publish contracts
echo "Running consumer tests and publishing contracts..."
./gradlew :price-service-consumer:test :price-service-consumer:pactPublish

# Step 4: Run provider verification tests
echo "Running provider verification tests..."
./gradlew :price-service-provider:test

# Step 5: Verify the full build
echo "Verifying the full build..."
./gradlew clean build

echo "Validation completed successfully!"
