# Animal Picture App

A Spring Boot microservice that fetches random pictures of cats, dogs or bears from public APIs, stores them in an embedded SQLite database, and returns the last stored picture of an animal. A simple web UI is served by the same app.

**Stack:** Java 21 · Spring Boot 4.1 · Maven (wrapper included) · SQLite + Flyway · Resilience4j · JUnit 6 + ArchUnit · Docker · GitHub Actions

![UI screenshot](docs/ui-screenshot.png)

## Quick start

**From source** (needs JDK 21; no Maven install or database setup needed):

```bash
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run
```

**With Docker** (no Java needed):

```bash
docker compose up --build
```

Then open **http://localhost:8080**.

**Tests:**

```bash
./mvnw test                       # 103 tests, no network access needed
```

**Executable jar:** `./mvnw package && java -jar target/animal-picture-app-1.0.0.jar`. The jar embeds its web server, so no separate application server is needed.

## How the brief is covered

| Requirement | Where |
|---|---|
| REST endpoint to fetch and save pictures by animal type and count | `POST /api/pictures` with `{"animal": "cat\|dog\|bear", "count": 1-10}` |
| Pictures stored in a database | Embedded SQLite (`data/pictures.db`, created on first start). Schema managed by Flyway migrations in `src/main/resources/db/migration` |
| REST endpoint for the last stored picture of an animal | `GET /api/pictures/{animal}/last` (image) and `…/last/metadata` (JSON) |
| Containerized | Multi-stage `Dockerfile` (builds and tests from source, runs as non-root) and `docker-compose.yml` with a data volume |
| Builds and runs with and without containers | `./mvnw spring-boot:run` or `docker compose up --build`. Both verified. CI builds and tests both on every push |
| Database shipped with the app, not a local setup | SQLite is embedded and created automatically |
| README | This file. Design details are in [`docs/DESIGN.md`](docs/DESIGN.md) |
| Bonus: automated tests | 103 tests: domain, use cases, adapters, real SQLite, whole-app integration, architecture rules. Run by GitHub Actions on every push and PR |
| Bonus: simple UI | `http://localhost:8080`: fetch pictures, a gallery of recent ones, and the status of each picture source |

**placekitten.com is offline** (HTTP 521), so cats come from **cataas.com**, which is on the public-apis list, as the brief allows. Dogs come from place.dog and bears from placebear.com.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/pictures` | Fetch and store pictures. Returns `201` with each picture's metadata |
| `GET` | `/api/pictures/{animal}/last` | Last stored picture of an animal, as an image (`…/metadata` for JSON) |
| `GET` | `/api/pictures/latest` | Last stored picture of any animal (`…/metadata` for JSON) |
| `GET` | `/api/pictures?animal=&limit=` | Recent pictures, newest first (metadata only) |
| `GET` | `/api/pictures/{id}/image` | Any stored picture by its id (a UUID) |
| `GET` | `/api/sources` | Each picture source and whether it's currently in use or skipped |
| `GET` | `/actuator/health` | Health check (used by the Docker `HEALTHCHECK`) |

```bash
curl -X POST localhost:8080/api/pictures -H 'Content-Type: application/json' -d '{"animal": "dog", "count": 3}'
curl -o dog.jpg localhost:8080/api/pictures/dog/last
```

Errors come back as problem JSON: `400` for invalid input, `404` when nothing is stored yet, `502` when every source for that animal failed.

## Design highlights

- **Resilient fetching:**
  - each animal has backup sources, tried in order;
  - timeouts, a 10 MB size limit and a check that the response really is an image;
  - a per-source **circuit breaker** skips a failing source for 60 s, so requests go straight to the backup.
- **All or nothing:** a request's pictures are downloaded first, then saved in one short transaction. A failure saves nothing, and a slow download never blocks other requests.
- **Clean architecture:** domain and use cases have no Spring, HTTP or SQL in them. Adapters implement their interfaces, and ArchUnit tests enforce the dependency direction.
- **Domain-driven model:**
  - `Picture` is an entity whose identity (`PictureId`, a UUID) is assigned by the domain, not the database;
  - `Image` and `ImageOrigin` are value objects that can't be invalid;
  - the database's row id never leaves the persistence adapter.
- **Strict input:** `count` must be a JSON integer from 1 to 10. That's enforced in the use case itself, not only over HTTP.

More detail: [`docs/DESIGN.md`](docs/DESIGN.md) (architecture, domain model, database schema and migrations, picture sources, error handling, circuit breaker, configuration, tests). Setting up the project in VS Code: [`docs/VSCODE_SETUP.md`](docs/VSCODE_SETUP.md).

## Not included / next steps

- **Parallel downloads** for larger `count` values (e.g. with virtual threads).
- **Metrics:** Actuator/Prometheus metrics for request rates and source failures.
- **OpenAPI documentation** (springdoc) and pagination for the picture list.
- **Shared circuit-breaker state:** it's kept in memory per app instance, which is fine for a single instance.
- **A smaller Docker image**, e.g. a jlink-trimmed JRE or a distroless base.
