.PHONY: up down down-remove logs status \
	up-observability down-observability logs-observability status-observability \
	run-user run-core test build build-test build-prod

DOPPLER_EXEC := doppler run --project backend --

# user-service + core-service compose файли разом (окремі postgres/redis на кожен,
# спільна core-kafka) - див. коментарі в самих файлах щодо унікальних service keys/портів.
INFRA_COMPOSE := docker compose --project-directory . \
	-f user-service/docker-compose.yaml \
	-f core-service/docker-compose.yaml

# Спільний observability стек (Prometheus/Grafana/Tempo/Loki) - піднімається окремо,
# бо не потрібен для звичайної локальної розробки.
OBSERVABILITY_COMPOSE := docker compose --project-directory . -f docker-compose.observability.yaml

# Модуль, для якого збирається образ у build-test/build-prod.
# Приклад: make build-test MODULE=core-service
MODULE ?= user-service

# Підняти інфраструктуру (змінні прокидаються з обох проєктів)
up:
	$(DOPPLER_EXEC) $(INFRA_COMPOSE) up -d

# Зупинити інфраструктуру
down:
	$(DOPPLER_EXEC) $(INFRA_COMPOSE) down

down-remove:
	$(DOPPLER_EXEC) $(INFRA_COMPOSE) down -v

# Переглянути логи
logs:
	$(DOPPLER_EXEC) $(INFRA_COMPOSE) logs -f

# Перевірити статус контейнерів
status:
	$(DOPPLER_EXEC) $(INFRA_COMPOSE) ps

# Підняти/зупинити observability стек окремо
up-observability:
	$(DOPPLER_EXEC) $(OBSERVABILITY_COMPOSE) up -d

down-observability:
	$(DOPPLER_EXEC) $(OBSERVABILITY_COMPOSE) down

logs-observability:
	$(DOPPLER_EXEC) $(OBSERVABILITY_COMPOSE) logs -f

status-observability:
	$(DOPPLER_EXEC) $(OBSERVABILITY_COMPOSE) ps

# Запуск user-service
run-user:
	$(DOPPLER_EXEC) ./gradlew :user-service:bootRun

# Запуск core-service
run-core:
	$(DOPPLER_EXEC) ./gradlew :core-service:bootRun

# Запуск тестів
test:
	$(DOPPLER_EXEC) ./gradlew test

# Виконання повної збірки проекту
build:
	$(DOPPLER_EXEC) ./gradlew build

# Вираховуємо 75% ядер процесора
ifeq ($(OS),Windows_NT)
	# Кількість логічних процесорів у Windows
	TOTAL_CPUS := $(NUMBER_OF_PROCESSORS)
	# Беремо приблизно 75% (якщо пусто або збій — дефолт 6)
	CPUS_75 := $(shell powershell -NoProfile -Command "[math]::Floor($(TOTAL_CPUS) * 0.75)")
else
	# Для Linux та macOS
	CPUS_75 := $(shell nproc --all 2>/dev/null | awk '{print int($$1 * 0.75)}' || sysctl -n hw.ncpu 2>/dev/null | awk '{print int($$1 * 0.75)}' || echo 6)
endif

# Гарантований fallback, якщо змінна виявилася порожньою
CPUS_75 ?= 6

build-test:
	docker build \
		--build-arg BUILD_WORKERS=$(CPUS_75) \
		--build-arg MODULE_NAME=$(MODULE) \
		--target runner-jvm \
		-t backend:dev .

build-prod:
	docker build \
		--build-arg BUILD_WORKERS=$(CPUS_75) \
		--build-arg MODULE_NAME=$(MODULE) \
		--target runner-native \
		-t backend:prod .
