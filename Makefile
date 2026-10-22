# Laptop demo in Docker Compose. Kubernetes mode has its own Makefile: make -C platform up
COMPOSE := docker compose -f compose/compose.yaml --profile apps

.PHONY: demo down

## demo: build the services, start compose, place and cancel an order, print links to Grafana and Alertmanager
demo:
	@compose/demo.sh

## down: stop the demo and delete its volumes (database, Kafka, Grafana data)
down:
	$(COMPOSE) down -v
