# Task Manager API

Java 21 / Spring Boot 3 REST API for the Daymark React frontend. It uses PostgreSQL, Spring Data JPA, Flyway, stateless Spring Security, BCrypt, and signed short-lived JWT access tokens. Every task operation is scoped to the authenticated user.

## Requirements and startup

Install JDK 21, Maven 3.9+, and PostgreSQL 13+. Create the local database and app user in `psql`:

```sql
CREATE USER task_app WITH PASSWORD 'choose-a-local-password';
CREATE DATABASE task_manager OWNER task_app;
```

Copy `.env.example` as a reference, then set the environment variables in your PowerShell session or IDE run configuration. Spring Boot does not load `.env` files automatically. Never commit real credentials.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/task_manager?sslmode=disable'
$env:DB_USERNAME = 'task_app'
$env:DB_PASSWORD = 'your-local-password'
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$secretBytes = New-Object byte[] 48
$rng.GetBytes($secretBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)
$rng.Dispose()
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173'
mvn clean install
mvn test
mvn spring-boot:run
```

The API listens at `http://localhost:8080`; check `http://localhost:8080/actuator/health`. Tests use isolated H2 in PostgreSQL compatibility mode and run the Flyway migration; they need neither PostgreSQL nor real secrets.

Flyway applies versioned PostgreSQL SQL migrations. Hibernate uses `ddl-auto: validate`, so it checks rather than changes the schema. Keep production schema changes in reviewed, immutable Flyway migrations; do not use Hibernate `update` in production.

## Deploy to Render

The backend is a separate Git repository. Push this `backend/` repository to its own GitHub repo, then create a Render Blueprint from that repo. The root `render.yaml` provisions the Docker web service and a same-region Render Postgres database. Render's `DATABASE_URL` is converted at startup to a pgJDBC URL, credentials are not logged, and TLS is required for the database connection. Render generates `JWT_SECRET`; during Blueprint setup, provide `CORS_ALLOWED_ORIGINS` as the exact deployed frontend origin, for example `https://your-app.vercel.app` (no trailing slash and no wildcard).

The Blueprint uses free web compute, which can spin down while idle, and the smallest paid Postgres plan so the database persists. Render's free Postgres expires after 30 days and is deleted after its 14-day recovery window; do not choose the free database plan for data you need to keep. Review Render's current pricing before applying the Blueprint.

For a manually created Render Postgres instance, set the web service's `DATABASE_URL` to its **internal** connection string and keep the web service and database in the same Render region. Set the health-check path to `/actuator/health`. For local development, use the separate `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` settings shown above.

## Security

- JWT access tokens are HMAC-SHA256 signed, expire after 15 minutes by default, and require a `JWT_SECRET` of at least 32 UTF-8 bytes. Set `JWT_EXPIRATION_MINUTES` to adjust within 1-1440 minutes.
- BCrypt strength 12 is used. Passwords, hashes, and tokens are not returned or logged.
- Logout is client-side token removal. There is no refresh token or server-side blacklist, so a stolen access token remains usable until it expires.
- The current frontend uses `sessionStorage`, which remains exposed to successful XSS. For production, consider HttpOnly, Secure, SameSite cookies and implement matching CSRF protection.
- CORS uses explicit origins from `CORS_ALLOWED_ORIGINS`; credentials are disabled.
- Request bodies never establish ownership. The verified principal supplies user IDs for database queries; inaccessible task IDs return the same 404 as missing IDs.

## Frontend connection

In `frontend/.env`, set and then restart Vite:

```env
VITE_API_MODE=rest
VITE_API_BASE_URL=http://localhost:8080/api
```

Registration accepts `{ fullName, email, password }`; `{ name, email, password }` is also accepted for the current frontend. The backend stores/returns `fullName`, and the frontend adapter maps it to its `name` display property. Login returns `{ token, tokenType, user }`; `/auth/me` returns the safe user directly. Task list responses use a `content` page envelope; the frontend requests pages of 100 until all are loaded for its current client-side filtering/statistics.

## Endpoints

All paths include `/api`. All task and profile endpoints require `Authorization: Bearer <token>`. Errors return `{ timestamp, status, errorCode, message, fieldErrors, path }`; validation failures include field errors.

| Method | Path | Auth | Request / result |
| --- | --- | --- | --- |
| POST | `/auth/register` | No | `{ fullName, email, password }` or `{ name, email, password }`; 201 safe user, duplicate 409 |
| POST | `/auth/login` | No | `{ email, password }`; 200 `{ token, tokenType, user }`, invalid credentials 401 |
| GET | `/auth/me` | Bearer | 200 safe user |
| GET | `/users/me` | Bearer | 200 safe user |
| POST | `/tasks` | Bearer | Task JSON; 201 task and `Location` |
| GET | `/tasks` | Bearer | Optional `search,status,priority,sortBy,direction,page,size`; 200 page envelope |
| GET | `/tasks/{id}` | Bearer | 200 owned task; missing/other owner 404 |
| PUT | `/tasks/{id}` | Bearer | Complete task JSON; 200 updated task |
| PATCH | `/tasks/{id}/status` | Bearer | `{ "status": "COMPLETED" }`; 200 task |
| DELETE | `/tasks/{id}` | Bearer | 204; missing/other owner 404 |
| GET | `/tasks/statistics` | Bearer | User-scoped totals and completion percentage |
| GET | `/actuator/health` | No | Health status |

Task JSON fields: `title` (required, max 150), `description` (optional, max 2000), `priority` (`LOW`, `MEDIUM`, `HIGH`), `status` (`PENDING`, `IN_PROGRESS`, `COMPLETED`), and optional ISO `dueDate` (`YYYY-MM-DD`). Responses include `id`, `createdAt`, and `updatedAt`, never owner or password fields.

List defaults: `page=0`, `size=20`, `sortBy=dueDate`, `direction=ASC`; size is 1-100. Allowed `sortBy`: `title`, `priority`, `status`, `dueDate`, `createdAt`, `updatedAt`. Page result: `{ content, page, size, totalElements, totalPages, first, last }`. Statistics: `{ totalTasks, pendingTasks, inProgressTasks, completedTasks, overdueTasks, completionPercentage }`; overdue excludes completed tasks and requires a due date before the server's current date. Empty users return zero counts and 0%.

## Postman smoke test

1. Register: `POST http://localhost:8080/api/auth/register`, JSON `{ "fullName": "Demo User", "email": "demo@example.com", "password": "StrongPassword123" }`.
2. Login: `POST http://localhost:8080/api/auth/login`, JSON `{ "email": "demo@example.com", "password": "StrongPassword123" }`; copy `token`.
3. For subsequent requests set `Authorization: Bearer <token>`.
4. Create: `POST /api/tasks`, JSON `{ "title": "Complete Java assignment", "description": "Finish the Spring Boot assignment", "priority": "HIGH", "status": "PENDING", "dueDate": "2026-10-20" }`.
5. List: `GET /api/tasks?status=PENDING&priority=HIGH&page=0&size=10&sortBy=dueDate&direction=ASC`; fetch one with `GET /api/tasks/{id}`.
6. Update with complete JSON using `PUT /api/tasks/{id}`; set status using `PATCH /api/tasks/{id}/status` with `{ "status": "COMPLETED" }`.
7. Check `GET /api/tasks/statistics`; delete using `DELETE /api/tasks/{id}`.
8. Verify missing/invalid tokens return 401 and another user's task ID returns 404.
