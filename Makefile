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

# Target 1: Run clean, test, pactPublish for price-service-consumer module
# Цель 1: Запуск clean, test, pactPublish для модуля price-service-consumer
consumer-tests:
	$(GRADLE) :price-service-consumer:clean :price-service-consumer:test :price-service-consumer:pactPublish

# Target for new-price-service-consumer module: Run clean, test, pactPublish
# Цель для модуля new-price-service-consumer: Запуск clean, test, pactPublish
new-consumer-tests:
	$(GRADLE) :new-price-service-consumer:clean :new-price-service-consumer:test :new-price-service-consumer:pactPublish

# Target 2: Stop containers via docker-compose (removes volumes with -v flag)
# Цель 2: Остановка контейнеров через docker-compose (удаляет тома с флагом -v)
docker-stop:
	$(DOCKER_COMPOSE) down -v

# Target 3: Start containers via docker-compose in detached mode
# Цель 3: Запуск контейнеров через docker-compose в фоновом режиме
docker-start:
	$(DOCKER_COMPOSE) up -d
	@echo "Waiting for services to be ready..."
	@$(SLEEP_CMD)

# Target 4: Run junit5 tests in price-service-provider module for all Pact test classes
# Цель 4: Запуск junit5 тестов в модуле price-service-provider для всех тестовых классов Pact
pact-tests:
	$(GRADLE) :price-service-provider:test --tests "com.example.priceservice.pact.*"

# Target 4a: Force run junit5 tests in price-service-provider module (cleans test results first)
# Цель 4a: Принудительный запуск junit5 тестов в модуле price-service-provider (сначала очищает результаты тестов)
pact-tests-force:
	$(GRADLE) :price-service-provider:cleanTest :price-service-provider:test --tests "com.example.priceservice.pact.*"

# Target 5: Run canideploy for price-service-provider-price participant with latest version
# Цель 5: Запуск canideploy для участника price-service-provider-price с последней версией
canideploy-price:
	$(GRADLE) :price-service-provider:canideploy -Ppacticipant='price-service-provider-price' -Platest=true

# Target 6: Run canideploy for price-service-provider-orderbook participant with latest version
# Цель 6: Запуск canideploy для участника price-service-provider-orderbook с последней версией
canideploy-orderbook:
	$(GRADLE) :price-service-provider:canideploy -Ppacticipant='price-service-provider-orderbook' -Platest=true

# Target 7: Run all canideploy commands for both participants
# Цель 7: Запуск всех команд canideploy для обоих участников
canideploy-all: canideploy-price canideploy-orderbook

# Target 8: Full workflow - complete CI/CD pipeline execution
# Цель 8: Полный рабочий процесс - выполнение полного конвейера CI/CD
# Steps: stop containers → start containers → run consumer tests → run provider tests → verify deployment → run new consumer tests → force provider tests → verify deployment again
# Шаги: остановка контейнеров → запуск контейнеров → запуск тестов потребителя → запуск тестов поставщика → проверка развертывания → запуск новых тестов потребителя → принудительные тесты поставщика → повторная проверка развертывания
full-workflow: docker-stop docker-start consumer-tests pact-tests canideploy-all new-consumer-tests pact-tests-force canideploy-all

# Mark targets as phony (not representing files)
# Пометка целей как фиктивных (не представляющих файлы)
.PHONY: consumer-tests new-consumer-tests docker-stop docker-start pact-tests pact-tests-force canideploy-price canideploy-orderbook canideploy-all full-workflow
