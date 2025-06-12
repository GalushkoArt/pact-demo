# Makefile for Pact Demo project
# Makefile для демонстрационного проекта Pact

# Variables - Configuration variables for the build process
# Переменные - Конфигурационные переменные для процесса сборки

# Detect OS and set appropriate commands for cross-platform compatibility
# Определение ОС и установка соответствующих команд для кросс-платформенной совместимости
ifeq ($(OS),Windows_NT)
    # Windows-specific commands
    # Команды для Windows
    GRADLE = gradlew.bat
    SLEEP_CMD = powershell -Command "Start-Sleep -Seconds 10"
else
    # Linux/Unix-specific commands
    # Команды для Linux/Unix
    GRADLE = ./gradlew
    SLEEP_CMD = sleep 10
endif
DOCKER_COMPOSE = docker-compose

consumer-tests:
	$(GRADLE) :price-service-consumer:clean :price-service-consumer:build --no-build-cache :price-service-consumer:pactPublish

consumer-publish-pact:
	$(GRADLE) :price-service-consumer:pactPublish

canideploy-consumer:
	$(GRADLE) :price-service-consumer:canIDeploy -Ppacticipant='price-service-consumer' -Platest=true

new-consumer-tests:
	$(GRADLE) :new-price-service-consumer:clean :new-price-service-consumer:build --no-build-cache

new-consumer-publish-pact:
	$(GRADLE) :new-price-service-consumer:pactPublish

canideploy-new-consumer:
	$(GRADLE) :new-price-service-consumer:canIDeploy -Ppacticipant='new-price-service-consumer' -Platest=true

docker-stop:
	$(DOCKER_COMPOSE) down -v

docker-start:
	$(DOCKER_COMPOSE) up -d
	@echo "Waiting for services to be ready..."
	@$(SLEEP_CMD)

pact-tests:
	$(GRADLE) :price-service-provider:cleanTest :price-service-provider:test --tests "com.example.priceservice.pact.*" --no-build-cache

canideploy-price:
	$(GRADLE) :price-service-provider:canideploy -Ppacticipant='price-service-provider-price' -Platest=true

canideploy-orderbook:
	$(GRADLE) :price-service-provider:canideploy -Ppacticipant='price-service-provider-orderbook' -Platest=true

full-workflow: docker-stop docker-start consumer-tests pact-tests canideploy-price canideploy-orderbook canideploy-consumer new-consumer-tests pact-tests canideploy-price canideploy-orderbook canideploy-new-consumer

.PHONY: consumer-tests consumer-publish-pact canideploy-consumer new-consumer-tests new-consumer-publish-pact canideploy-new-consumer docker-stop docker-start pact-tests pact-tests canideploy-price canideploy-orderbook canideploy-all full-workflow
