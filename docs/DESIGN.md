# Design notes

Details behind the [README](../README.md): architecture, domain model, database, error handling and resilience.

- [Architecture](#architecture)
- [Domain model](#domain-model)
- [Database](#database)
- [Picture sources](#picture-sources)
- [Error handling](#error-handling)
- [Circuit breaker](#circuit-breaker)
- [Web UI and CORS](#web-ui-and-cors)
- [Configuration](#configuration)
- [Testing and CI](#testing-and-ci)

## Architecture

Clean architecture (ports and adapters). Dependencies point **inwards only**:

```
 adapter.in.web ─┐                         ┌─ adapter.out.persistence (SQLite)
                 ├─▶ application ─▶ domain ◀┤
 config ─────────┘   (use cases + ports)    └─ adapter.out.pictures (HTTP APIs)
```

```
src/main/java/com/example/animalpictures/
  domain/                    Picture (entity), PictureId, Image, ImageOrigin (value objects), AnimalType,
                             PictureRepository (repository interface), PictureSummary. Plain Java
  application/               Use cases: FetchPicturesUseCase, PictureQueryUseCase, SourceStatusUseCase
    port/out/                PictureSource: where pictures come from (+ its data types)
  adapter/in/web/            REST controllers, request/response records, error handling, CORS, strict JSON
  adapter/out/persistence/   SqlitePictureRepository implements PictureRepository (JDBC)
  adapter/out/pictures/      HttpPictureSource implements PictureSource (fallback, circuit breaker, limits)
  config/                    Wires the use cases to the adapters (the only place that knows both)
src/main/resources/
  application.yml   configuration
  db/migration/     database migrations (Flyway)
  static/index.html web UI
```

- **The use cases don't know about Spring, HTTP or SQL.** They only use the `PictureSource` and `PictureRepository` interfaces, so they're tested with simple in-memory fakes. Swapping SQLite for another database, or adding a picture provider, means writing a new adapter without touching the use cases.
- **The rules are enforced by tests.** `ArchitectureTest` (ArchUnit) fails the build if:
  - the domain or application layer depends on Spring or an adapter;
  - the web layer uses an outbound adapter directly;
  - the outbound adapters depend on each other;
  - anything outside the persistence adapter depends on it.
- **Kept pragmatic:** one Maven module, records shared between layers where a separate mapping would add nothing, and the transaction boundary inside the persistence adapter (`saveAll`). The controllers call the use-case classes directly rather than through extra input-port interfaces, because there's only one inbound adapter (REST).

## Domain model

- **`Picture` is an entity, and the aggregate root.** Its identity is a `PictureId` (a UUID) **assigned by the domain** when the picture is fetched (`Picture.fetch(...)`), not by the database. A picture is complete, id included, before it's stored, and storing it doesn't change it. Repositories rebuild stored pictures with `Picture.restore(...)`. Two pictures are equal only if their ids are equal.
- **Value objects** make invalid states impossible:
  - **`Image`:** non-empty bytes with an `image/*` type; the bytes are copied in and out, so it can't be changed after creation.
  - **`ImageOrigin`:** source website and URL.
  - **`PictureId`:** only the canonical UUID form is accepted.
- **`PictureRepository` lives in the domain.** It's the collection of pictures, expressed in domain terms (`findById(PictureId)`, `findLatest()`…). The SQLite adapter implements it, and the database's row id stays inside that adapter.
- **Rules owned by the use cases:** `count` must be 1–10 and `limit` 1–50. They're checked in the use cases themselves, not only in the web layer, so they hold for any caller.

## Database

Embedded **SQLite**. The `org.xerial:sqlite-jdbc` driver, added through Maven, bundles the native library and **creates the database file on first connection**. The app creates the parent directory (`./data`) if it's missing.

The schema is managed by **Flyway** migrations in [`src/main/resources/db/migration`](../src/main/resources/db/migration), which run at startup:

| Migration | Change |
|---|---|
| `V1__create_picture_table.sql` | The original table |
| `V2__add_domain_picture_id.sql` | Adds `picture_id`, the picture's domain id (UUID), and gives existing rows one |

The resulting table:

```sql
CREATE TABLE picture (
    id             INTEGER PRIMARY KEY AUTOINCREMENT, -- internal row id: never leaves the persistence adapter
    picture_id     TEXT    NOT NULL UNIQUE,           -- the picture's domain id (PictureId, a UUID)
    animal_type    TEXT    NOT NULL CHECK (animal_type IN ('CAT', 'DOG', 'BEAR')),
    image          BLOB    NOT NULL,   -- the picture's binary data
    content_type   TEXT    NOT NULL,   -- e.g. image/jpeg
    source_website TEXT    NOT NULL,   -- e.g. placebear.com
    source_url     TEXT    NOT NULL,   -- full URL it was downloaded from
    created_at     TEXT    NOT NULL    -- fetch timestamp, ISO-8601 UTC
);
CREATE INDEX idx_picture_created_at ON picture (created_at);
CREATE INDEX idx_picture_animal_created_at ON picture (animal_type, created_at);
```

- **Two ids, two owners.** `picture_id` is the domain's id: assigned by the application when a picture is fetched, and the id used in the API. `id` is the database's own row id. It's only used inside `SqlitePictureRepository`, as an insertion-order tie-breaker when sorting.
- **Upgrading older databases:** a database created before Flyway was added (old schema, no history table) is baselined at V1, and then only V2 runs. Existing pictures keep their data and get a UUID. Tested with a database created by the previous version.
- **Timestamps:** SQLite has no timestamp type, so `created_at` is stored as **fixed-width** ISO-8601 UTC text (always 9 fractional digits, e.g. `2026-10-19T07:17:19.572703000Z`). That makes plain string ordering chronological, so "latest" is `ORDER BY created_at DESC, id DESC LIMIT 1`.
- **One connection:** SQLite allows one writer at a time, so the connection pool has a single connection. Downloads therefore happen *outside* the database transaction, which only covers the short save at the end. Otherwise a slow fetch would block every other request.
- **Inspecting the database:** `sqlite3 data/pictures.db "SELECT picture_id, animal_type, source_website, created_at, length(image) FROM picture ORDER BY created_at DESC;"`

## Picture sources

Every animal has at least one backup. Sources are tried in order:

| Animal | Primary | Backups |
|---|---|---|
| Cat | [cataas.com](https://cataas.com) | [placecats.com](https://placecats.com) |
| Dog | [place.dog](https://place.dog) | [placedog.net](https://placedog.net), then [dog.ceo](https://dog.ceo/dog-api/) |
| Bear | [placebear.com](https://placebear.com) | [iNaturalist API](https://api.inaturalist.org/v1/docs/) (openly licensed research-grade bear photos) |

- **placekitten.com** (from the brief) is offline (HTTP 521), so cats come from cataas.com, which is on the public-apis list. cataas.com itself went down several times during development (timeouts of 10–40 s), which is why every animal has a backup.
- **Two kinds of source**, both configured in `application.yml` under `pictures.sources`:
  - **Image sources** return the picture directly, e.g. `https://placebear.com/{width}/{height}`. place.dog, placedog.net and placebear return the *same* picture for the same size, so each request uses a random width and height (200–600 px).
  - **JSON sources** return JSON containing the picture's URL, so the fetch has two steps:
    - **`image-url-path`** is a JSON Pointer to the URL (e.g. `/message` for dog.ceo).
    - **`allowed-image-hosts`** must be listed for each JSON source, and the extracted URL has to be `https` on one of them. This stops a compromised API from making the app fetch internal addresses.
    - **For iNaturalist**, a random results page (`{random}`, 1–7,000) gives a random bear. `image-url-find`/`image-url-replace` swap the 75 px thumbnail for the medium-size photo, and only CC0 / CC-BY / CC-BY-SA photos are requested.
- **`source_website`** is the host of the source that served the picture (e.g. `api.inaturalist.org`). **`source_url`** is the exact image URL that was downloaded.

## Error handling

| Situation | Behaviour |
|---|---|
| A source is down, slow, returns something that isn't an image, or (for JSON sources) returns no image URL | Try the **next source** for that animal |
| All sources for an animal fail | `502 Bad Gateway` with the reason; **nothing is saved** |
| Upstream too slow | 5 s connect / 10 s read timeout, then fall back or fail |
| Image larger than 10 MB | Rejected without reading it all into memory. The size is checked against `Content-Length` and again while reading |
| JSON source returns an image URL that isn't `https` or isn't on its allowed hosts | Rejected |
| A source keeps failing | Circuit breaker skips it for 60 s (see below) |
| One picture in a batch fails (e.g. 3 of 5) | Nothing from that request is saved: all pictures are downloaded first, then saved in one transaction |
| A slow fetch is running | Other requests aren't blocked (see "One connection" above) |
| Unknown animal, `count` outside 1–10 or not a JSON integer (`1.5`, `"3"`), malformed JSON, invalid picture id | `400 Bad Request` |
| No picture stored yet, unknown picture id | `404 Not Found` |

Errors use the standard problem JSON format (RFC 9457): `{"status": 502, "title": "Bad Gateway", "detail": "..."}`.

## Circuit breaker

Each picture source has its own circuit breaker ([Resilience4j](https://resilience4j.readme.io/docs/circuitbreaker)):

- **CLOSED** (normal): the source is called as usual.
- **OPEN**: after **2 failures in a row**, the source is **skipped for 60 seconds**. Requests go straight to the next source instead of waiting out a timeout.
- **HALF_OPEN**: after the 60 s, the next request is a **trial call**. If it succeeds the circuit closes; if it fails it opens for another 60 s.
- **If every source for an animal is open**, the request fails immediately with `502`.

Measured with a dead primary (connect timeout) and the real placecats.com as backup:

| Request | Time | |
|---|---|---|
| 1 | 6.4 s | primary times out → backup |
| 2 | 5.9 s | primary times out → backup, **circuit opens** |
| 3–6 | 0.25–0.47 s | primary skipped → backup immediately |

Live state: `GET /api/sources`, also shown in the UI. The breaker state is kept in memory per app instance.

## Web UI and CORS

- **UI:** a single HTML page (`src/main/resources/static/index.html`), plain HTML and JavaScript with no framework, served by the same app at `/`. It has three parts:
  - **Fetch:** choose an animal and count, fetch and save, or show the latest picture.
  - **Gallery:** the latest 24 pictures, filterable by animal.
  - **Sources:** every source's circuit-breaker state, refreshed every 5 s.

  All data from the API is inserted with `textContent`, never `innerHTML`.
- **CORS isn't needed for this UI**, because it's served from the same origin as the API.
- **CORS is configured for `/api/**`** so a UI hosted elsewhere (e.g. a React dev server) can call the API. Allowed origins come from `CORS_ALLOWED_ORIGINS`; other origins get `403`.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:sqlite:./data/pictures.db` | SQLite database location |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated origins allowed to call `/api/**` |

In `application.yml`:
- the picture sources (`pictures.sources`);
- the image size limit (`pictures.max-image-size`, default 10MB);
- the circuit breaker (`pictures.circuit-breaker.failure-threshold`, default 2; `open-duration`, default 60s);
- the Flyway settings.

## Testing and CI

103 tests (`./mvnw test`), none of which need network access:

| Kind | What it covers |
|---|---|
| Domain | `Picture` identity and equality; `PictureId`, `Image`, `ImageOrigin` |
| Use cases | Plain Java with in-memory fakes for the ports (no Spring, no mocks), including the `count`/`limit` rules |
| HTTP adapter | Mocked HTTP server: image and JSON sources, fallback, URL allowlist, size limit, circuit breaker open / half-open / close |
| Persistence | Real SQLite files created by the Flyway migrations, including upgrading an old-schema database |
| Integration | The whole app with a real SQLite file and only the external APIs mocked: status codes, CORS, strict JSON, reads not blocked during a slow fetch |
| Architecture | ArchUnit rules for the dependency direction |
| Config | The shipped `application.yml` gives every animal a backup, and every JSON source an allowlist |

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every push and pull request:
1. **Build and test:** `./mvnw -B verify` on JDK 21. It uploads the test reports and the executable jar as artifacts.
2. **Docker image build:** builds the image, which runs the tests again in the build stage.
