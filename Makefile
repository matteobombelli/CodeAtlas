.PHONY: start stop test clean

start:
	docker compose up --build

stop:
	docker compose down

test:
	./gradlew test
	cd frontend && npm ci && npm run lint && npm test && npm run build

clean:
	docker compose down --volumes --remove-orphans
	./gradlew clean
	cd frontend && npm run clean --if-present
