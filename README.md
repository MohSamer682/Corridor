# Corridor

Corridor is a virtual office for companies moving to remote work. Your team gets the same buildings, floors and rooms they had on site, and a conversation starts the way it did there: you see who is around and walk over.

The first release covers sign-in, the office map, presence, chat, voice and video, attendance, meetings and announcements. `docs/adr/` records the decisions behind the stack.

## Stack

- **Backend:** Java 21, Spring Boot 4.1, Spring Modulith, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL 17.
- **Frontend:** React 18, TypeScript, Vite, TanStack Query, Zustand, Tailwind, React Three Fiber.
- **Real time:** STOMP over WebSocket for presence and chat. WebRTC for voice and video: peer to peer for small groups, LiveKit for meetings.
- **Tests:** JUnit 5, Testcontainers, Vitest, React Testing Library, Playwright.

## Run it locally

You need JDK 21, Node 22 and Docker Desktop.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd ui && npm ci && npm run dev
```

The `local` profile starts PostgreSQL, Mailpit and LiveKit from `compose.yaml` and loads demo data. The API runs on `http://localhost:8080` and the UI on `http://localhost:5173`. On Windows, use `mvnw.cmd`.

## Test

```bash
./mvnw verify                  # unit and integration tests, formatting, Checkstyle, coverage
cd ui && npx vitest run        # component tests
cd ui && npx playwright test   # end-to-end tests
```

Integration tests start a real PostgreSQL in Docker, so Docker must be running.

## Where things live

| Path | What |
|------|------|
| `src/main/java/com/corridor/office/<module>` | One package per module: `common`, `identity`, `spaces`, `presence` and so on. `ModularityTests` keeps the boundaries. |
| `src/main/resources/db/migration` | Flyway migrations. Hibernate only validates the schema. |
| `src/main/resources/db/seed` | Demo data, loaded by the `local` profile only. |
| `ui/` | The React app. |
| `compose.yaml` | Local PostgreSQL, Mailpit and LiveKit. |
| `docs/adr/` | Architecture decision records. |

## Configuration

Secrets come from environment variables, never from YAML. Production needs `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `FLYWAY_USER` and `FLYWAY_PASSWORD`. Each feature that adds a setting documents it here.
