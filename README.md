# Societal Innovation Portal: backend

Spring Boot 4.1, Java 21, MySQL 8, Flyway.

## First-time setup

1. In MySQL Workbench, as an administrator, run this with a password of your own:

   ```sql
   CREATE DATABASE societal_portal CHARACTER SET utf8mb4;
   CREATE USER 'portal_app'@'localhost' IDENTIFIED BY 'CHOOSE_A_PASSWORD';
   GRANT ALL PRIVILEGES ON societal_portal.* TO 'portal_app'@'localhost';
   ```

2. Copy `.env.example` to `.env` and fill in `DB_PASSWORD` and `JWT_SECRET` (32+ random characters).
   Set `DEMO_DATA=true` for a local demo; it only fills an empty database.
   `.env` is ignored by Git. Never commit it.
3. Run `mvn spring-boot:run` here, and `npm run dev` in `../frontend`.

Flyway creates the tables. Never edit an applied migration; add the next number instead.

The full API is described in [docs/API.md](docs/API.md).

To serve the built frontend from this server as well, set `FRONTEND_DIR` (for example `../frontend/dist`).

## Workflow rules

SUBMITTED -> VALIDATED or REJECTED (reason required) -> ASSIGNED (approved university)
-> IN_PROGRESS (accepted; declining goes back to VALIDATED) -> SOLVED (admin confirms). Completion needs
every milestone done in order and an approved proposal. Every change is
recorded in `status_history`. University and industry accounts cannot sign in until an admin approves them.

Industry can offer support while a project is in progress, its proposal is approved and completion has not
been requested. Accepted funding can never exceed the budget. Offers are visible to the company, the
assigned university and admins; accepted support is visible to everyone signed in. No money is handled.

Overdue limits are in `StageLimits`: submitted 2 days, validated 7, assigned 7, in progress 180.

Without demo data, set `ADMIN_EMAIL` and `ADMIN_PASSWORD` for one start to create the first admin.

## Tests

`mvn test` uses in-memory H2. To use MySQL, set `TEST_DB_URL`, `TEST_DB_USERNAME` and
`TEST_DB_PASSWORD` to a **separate, empty** database.

## Security notes

- The session is a signed JWT in an HttpOnly, SameSite=Strict cookie. The role is read from the
  database on every request.
- Logout ends every session of that user: the token carries a session version that logout raises.
- Unverified and rejected reports are visible only to their author and admins.
- A Content-Security-Policy allows only the site's own scripts (when this server serves the frontend).
- Login throttling is in memory, per email and address. Behind a reverse proxy, configure
  forwarded headers.
- Set `COOKIE_SECURE=true` and `DEMO_DATA=false` outside local development.
- District checks are approximate.
