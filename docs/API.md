# API Reference

All browser-facing requests go through the NGINX gateway at `http://localhost:8080`.
JSON errors use `{ "timestamp", "status", "error", "message" }`. Order request
validation and malformed query parameters return `400`; missing order IDs return
`404`.

## Orders

Base path: `/api/orders` (Order Service, port 8082)

| Method | Path | Behavior | Success |
|---|---|---|---|
| `POST` | `/api/orders` | Create an order and synchronously request payment | `201` with order DTO |
| `GET` | `/api/orders` | Filter and page orders | `200` with Spring `Page` JSON |
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

## Other Services

| Service | Existing endpoints | Notes |
|---|---|---|
| Payment (`/api/payments`) | `POST /`, `GET /`, `GET /{id}`, `GET /order/{orderId}` | Payment creation returns `201`, or `402` for a failed payment. |
| Fraud (`/api/fraud-checks`) | `POST /`, `GET /payment/{paymentId}`, `GET /order/{orderId}` | `POST /` evaluates a payment and returns the decision/risk score. |
| Notification (`/api/notifications`) | `POST /`, `GET /payment/{paymentId}`, `GET /order/{orderId}` | `POST /` enqueues work and returns `202 Accepted`. |
| Payment chaos (`/internal/chaos`) | `POST /leak`, `POST /reset` | Development/chaos controls; do not expose in a production gateway. |

The payment, fraud, and notification list endpoints currently return unpaged
collections. Order pagination is the first paged/filterable resource; consistent
query contracts for the remaining services are follow-up work.

## Security Boundary

The current login and route guard are client-side learning examples. The Spring
services do not issue or verify JWTs, hash user passwords, or enforce roles. Do
not send credentials or rely on the browser route guard as authorization. Before
exposing these APIs, add server-side authentication/authorization and propagate
service credentials across synchronous and queued calls.