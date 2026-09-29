# API Reference

All browser-facing requests go through the NGINX gateway at `http://localhost:8080`.
Protected routes require `Authorization: Bearer <accessToken>`. JSON errors use
`{ "timestamp", "status", "error", "message" }`; invalid credentials/tokens
return `401`, missing roles return `403`, invalid requests return `400`, missing
resources return `404`, and invalid state transitions return `409`.

## Authentication

| Method | Path | Authorization | Behavior |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Verify BCrypt password and issue a 15-minute signed JWT. |
| `GET` | `/api/auth/me` | Any valid JWT | Return the authenticated subject, role, and user ID claims. |
| `POST` | `/api/auth/users` | `ADMIN` | Provision an `OPERATOR`; passwords are BCrypt-hashed before persistence. |

The initial `ADMIN` user is inserted once from `AUTH_BOOTSTRAP_USERNAME` and
`AUTH_BOOTSTRAP_PASSWORD`. `JWT_SECRET` must be at least 32 random UTF-8 bytes
and must be identical in all four services. Configure all three values outside
source control in `.env`. Tokens use HS256 with issuer, issued-at, and expiry
claims. The React client keeps access tokens in memory only; reload requires a
new login.

`ADMIN` can provision operators and access statistics/administrative actions.
`OPERATOR` can run the operations APIs. Health and Prometheus scrape endpoints
are public for internal monitoring. Payment, Fraud, and Notification validate
the same signed token as Order; synchronous calls relay the bearer token and the
payment notification worker captures it before enqueueing.

## Orders

Base path: `/api/orders` (Order Service, port 8082)

| Method | Path | Behavior | Success |
|---|---|---|---|
| `POST` | `/api/orders` | Create an order and synchronously request payment | `201` with order DTO |
| `GET` | `/api/orders` | Filter and page orders (`OPERATOR`, `ADMIN`) | `200` with stable page DTO |
| `GET` | `/api/orders/{id}` | Read one order | `200` |
| `POST` | `/api/orders/{id}/cancel` | Cancel an order | `204` |
| `PATCH` | `/api/orders/{id}/status` | Set status to `CANCELLED` only | `200` with order DTO |
| `GET` | `/api/orders/statistics` | Global counts and paid volume | `200` |

`GET /api/orders` accepts `page` (default `0`), `size` (default `20`, maximum
`100`), `sort` (`id`, `createdAt`, `totalAmount`, or `status`; default
`createdAt`), `direction` (`ASC` or `DESC`; default `DESC`), and optional
`status`, `userId`, and substring `itemName` filters. Example:

```http
GET /api/orders?status=PAID&itemName=recorder&page=0&size=20&sort=createdAt&direction=DESC
```

The page response is a stable DTO with `content`, `number`, `size`, `totalPages`,
and `totalElements`. Status changes through PATCH intentionally do not allow
clients to mark an order paid or rewrite a payment result. The older POST cancel
route remains available for compatibility.

The create DTO accepts `userId`, `itemName`, `quantity`, and `totalAmount`.
The response preserves the order fields (`id`, `userId`, `itemName`, `quantity`,
`totalAmount`, `status`, `createdAt`) without accepting client-supplied IDs or
status values. Statistics are global, independent of the current page/filter.

## Payment, Fraud, and Notification

Each collection endpoint supports `page` (0-based, default 0), `size` (default 20,
maximum 100), `sort`, and `direction`. Responses use an explicit stable page DTO:
`content`, `number`, `size`, `totalPages`, and `totalElements`.

### Payments (`/api/payments`)

- `POST /`: process payment (`201`, or `402` when declined); accepts positive
	`orderId` and `amount` and returns a response DTO.
- `GET /`: filter by `status`, `orderId`, `minimumAmount`, `maximumAmount`; sort
	by `id`, `createdAt`, `amount`, or `status`.
- `GET /{id}`, `GET /order/{orderId}`: read one or page a specific order’s
	payments.
- `GET /statistics` (`ADMIN`): total, pending/completed/failed counts and
	completed amount.
- `PATCH /{id}/status` (`ADMIN`): only a `PENDING` payment may become `FAILED`.
- `DELETE /{id}` (`ADMIN`): only pending records may be deleted; settled
	financial records are immutable.

### Fraud Checks (`/api/fraud-checks`)

- `POST /`: validate positive order/payment IDs and amount, run risk evaluation,
	return the decision and score.
- `GET /`: filter by `decision`, `orderId`, `paymentId`, `minimumAmount`, and
	`maximumAmount`; sort by `id`, `createdAt`, `riskScore`, `amount`, or `decision`.
- `GET /{id}`, `GET /payment/{paymentId}`, `GET /order/{orderId}`: detail and
	paged relationship reads.
- `GET /statistics` (`ADMIN`): total, approve/review/decline counts and average
	risk score.
- `PATCH /{id}/decision` (`ADMIN`): resolve only `REVIEW` to `APPROVE` or
	`DECLINE`; terminal outcomes cannot be rewritten.
- `DELETE /{id}` (`ADMIN`): only unresolved review records may be removed.

### Notifications (`/api/notifications`)

- `POST /`: validate and enqueue delivery; returns `202 Accepted`.
- `GET /`: filter by delivery `status`, `paymentId`, `orderId`, `type`, amount
	range; sort by `id`, `createdAt`, `amount`, `status`, or `type`.
- `GET /{id}`, `GET /payment/{paymentId}`, `GET /order/{orderId}`: detail and
	paged relationship reads.
- `PUT /{id}` (`ADMIN`): correct metadata for a failed record; sent records are
	immutable.
- `GET /statistics` (`ADMIN`): total, sent, and failed counts.
- `POST /{id}/retry`: enqueue a new attempt only when the prior record failed;
	delivery state remains worker-owned.
- `DELETE /{id}` (`ADMIN`): remove a notification record.

Chaos endpoints (`/internal/chaos/**`) are available only in the `chaos` profile
and require `ADMIN`; never enable that profile in production.

## Security Boundary

The React route guard is a usability layer; every protected API independently
validates JWT signature, issuer, and expiry and enforces roles in Spring Security.
The access token is held in browser memory rather than persistent web storage.
The bootstrap administrator password is used only for initial account creation;
only the BCrypt hash is stored, and it is never returned by an API.