# Developer Guide

## 1. Architecture

The browser app is React/Vite. NGINX serves the built frontend and forwards `/api/*` to the API gateway; the gateway routes each API prefix to one of four Spring Boot 3.3 / Java 21 services. Each service owns a separate PostgreSQL database. OpenTelemetry traces, Prometheus metrics, and structured logs feed the existing observability stack.

## 2. Frontend Structure

- `frontend/src/main.jsx` installs Redux, authentication context, and browser routing.
- `frontend/src/App.jsx` defines public `/login` and the protected Order Desk shell.
- `frontend/src/pages/` contains the login and operational dashboard.
- `frontend/src/components/` contains reusable order form, row/list, and route guard.
- `frontend/src/features/orders/ordersSlice.js` owns order API thunks, page metadata, request states, and selectors.

## 3. Backend Services

- Order Service owns orders, `app_users`, login/token issuance, and order statistics.
- Payment Service owns payment settlement and performs the synchronous fraud check.
- Fraud Service owns risk evaluations and the review-decision lifecycle.
- Notification Service owns delivery records and its bounded asynchronous worker.
- The gateway is NGINX (`gateway/nginx.conf`); routes should be added there when a new browser-facing service path is introduced.

## 4. Browser-to-Database Request Flow

1. The React API thunk builds a same-origin `/api/...` request.
2. `ordersSlice.js` adds the in-memory bearer token and parses JSON/error responses.
3. Frontend NGINX forwards `/api/` to the gateway; gateway NGINX selects the owning service.
4. Spring Security validates the JWT before the controller executes.
5. The controller validates a request DTO, calls its service, and maps entities into response DTOs.
6. The service applies business rules and calls its repository; JPA executes SQL against that service's database.

## 5. Authentication

`POST /api/auth/login` is public. Order Service loads the user by normalized username, verifies the submitted password against its BCrypt hash through Spring Security's `AuthenticationManager`, then signs a 15-minute HS256 token. Claims contain `sub` (username), `userId`, `roles`, `iss`, `iat`, and `exp`. The other services use Spring's OAuth2 resource-server filter to check the signature, issuer, and time claims.

The frontend stores only the access token in a module-scoped variable (`frontend/src/auth/accessToken.js`), not local/session storage. This reduces persistence if browser storage is exposed, at the cost of requiring sign-in after reload. On expiry or a protected API `401`, AuthContext clears the token and redirects through the route guard.

## 6. Authorization / RBAC

- `OPERATOR` can use the normal order, payment, fraud, and notification workflows.
- `ADMIN` can provision operators, view protected service statistics, and perform administrative correction/deletion actions.
- Per-service `SecurityConfig` defines URL/method role rules. Order cancellation/status changes, Payment mutation/deletion, Fraud review resolution/deletion, and Notification correction/deletion are admin-restricted.
- `401` means the request has no valid identity; `403` means its valid token lacks the required role. The React route guard is only a UX check; backend filters remain authoritative.

Order statistics permit both `ADMIN` and `OPERATOR`; Payment, Fraud, and Notification statistics are `ADMIN` only. Check the owning service's `SecurityConfig` when changing a rule.

## 7. Domain Concepts and State

Order Service creates an order as `CREATED`, asks Payment Service to process it, then records `PAID` or `PAYMENT_FAILED`. Payment first persists `PENDING`, calls Fraud synchronously, simulates settlement, and queues a notification. Fraud decisions are `APPROVE`, `REVIEW`, or `DECLINE`; only `REVIEW` may be resolved. Notifications are `SENT` or `FAILED`; delivery status is set by the worker, and failed deliveries may be corrected/retried. Payment/fraud/delivery IDs are cross-service identifiers, not JPA relationships.

The notification queues are in-memory demonstrations, not durable brokers. The order/payment workflow also crosses HTTP while a local transaction is open; neither behavior provides a distributed transaction or durable delivery guarantee.

## 8. Add a Backend Endpoint

1. Add/adjust the request and response DTO in the owning controller; do not expose entities as write payloads.
2. Put business rules in that service, persistence/search in its repository, and transaction boundaries on service methods.
3. Validate body/query values at the controller boundary. Page numbers are zero-based, page size defaults to 20 and is capped at 100, and sort properties must be allow-listed.
4. Add the route's role rule to that service's `SecurityConfig` and test `401`, `403`, validation, and business-state behavior.
5. Add gateway routing only if the path is not already covered; preserve trace context and relay the bearer token for downstream calls.

## 9. Add a Frontend Page

Add a page under `frontend/src/pages/`, route it in `App.jsx`, and put reusable controls in `frontend/src/components/`. Keep shared server state in a Redux slice/thunk, local form state in the component, and let `ProtectedRoute` check the required role for navigation. Remember that the API still enforces that role independently.

## 10. Validation and Errors

Use Jakarta validation on request DTOs/query parameters, then enforce business transitions in the service as well. Global exception advice maps invalid requests to `400`, missing records to `404`, invalid state changes to `409`, and unexpected failures to a generic `500`; details stay in service logs. Spring Security produces `401`/`403` before controller advice.

## 11. Pagination, Queries, and Indexes

List endpoints return `{ content, number, size, totalPages, totalElements }`. Search predicates execute in SQL; statistics use repository aggregates rather than loading a full table. Entity indexes cover common status/order/payment lookups. Confirm new indexes against realistic query plans, since indexes cost write time and storage.

## 12. Tests

Backend tests live under each service's `src/test/java`: service tests for business transitions, MVC tests for HTTP/role contracts, and H2 `@DataJpaTest` tests for JPQL paging/filtering/aggregates. H2 tests verify query behavior without Docker but do not replace a PostgreSQL/Compose integration test.

Frontend unit tests use Node's built-in test runner under `frontend/src`; Playwright journeys live under `frontend/e2e`. The E2E suite stubs authentication/API responses to test browser behavior without requiring the backend stack.

## 13. Run Checks

From `frontend/`:

```sh
npm ci
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

From the repository root, run one Java 21 service suite at a time:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -f microservices/order/pom.xml clean verify
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -f microservices/payment/pom.xml clean verify
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -f microservices/fraud/pom.xml clean verify
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -f microservices/notification/pom.xml clean verify
```

Start the full stack with `docker compose up -d --build` after configuring `.env`.

## 14. Local Configuration

Copy `.env.example` to `.env`. Set `POSTGRES_USER`/`POSTGRES_PASSWORD`, a randomly generated `JWT_SECRET` (at least 32 bytes; `openssl rand -hex 32`), `AUTH_BOOTSTRAP_USERNAME`, and a unique `AUTH_BOOTSTRAP_PASSWORD` of at least 12 characters. Alert delivery uses the SMTP/Slack variables; the chaos profile is disabled by default. Never commit `.env` or share the JWT signing secret between unrelated deployments.

`npm run dev` proxies `/api` to `http://localhost:8080`; override with `VITE_API_PROXY_TARGET` when the gateway is elsewhere. Playwright starts Vite itself.

## 15. Change Safety

Keep financial and decision records immutable after terminal states, preserve the shared JWT key/issuer across services, do not persist browser access tokens casually, and never trust frontend role checks. Keep page sizes bounded, maintain allow-listed sorts, relay identity through synchronous and queued calls, and preserve the existing telemetry configuration when changing service or gateway routes.
