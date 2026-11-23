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

Flyway creates the tables. Never edit an applied migration; add `V2__...sql` instead.

## API

| Method | Path | Who |
|---|---|---|
| GET | /api/auth/me | anyone (204 when signed out; sets the CSRF cookie) |
| POST | /api/auth/register, /login, /logout | anyone |
| GET | /api/problems, /api/problems/{caseId} | signed in |
| POST | /api/problems (multipart: `problem` JSON, optional `photo`) | citizen |
| POST | /api/problems/{caseId}/feedback | reporting citizen, once solved |
| GET | /api/photos/{name} | signed in |
| GET | /api/admin/stats, /universities, /accounts/pending | admin |
| POST | /api/admin/accounts/{id}/approve, /reject | admin |
| POST | /api/admin/problems/{caseId}/validate, /reject, /assign | admin |

Changes need the `X-XSRF-TOKEN` header, copied from the `XSRF-TOKEN` cookie.

## Workflow rules

SUBMITTED -> VALIDATED or REJECTED (reason required) -> ASSIGNED (approved university). Every change is
recorded in `status_history`. University and industry accounts cannot sign in until an admin approves them.

Without demo data, set `ADMIN_EMAIL` and `ADMIN_PASSWORD` for one start to create the first admin.

## Tests

`mvn test` uses in-memory H2. To use MySQL, set `TEST_DB_URL`, `TEST_DB_USERNAME` and
`TEST_DB_PASSWORD` to a **separate, empty** database.

## Security notes

- The session is a signed JWT in an HttpOnly, SameSite=Strict cookie. The role is read from the
  database on every request.
- Logout clears the cookie. A copied token would stay valid until it expires (8 hours).
- Login throttling is in memory, per email and address. Behind a reverse proxy, configure
  forwarded headers.
- Set `COOKIE_SECURE=true` and `DEMO_DATA=false` outside local development.
- District checks are approximate.
