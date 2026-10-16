# Product

## Register

product

## Brand

**Chic Charmosa**
Small family lingerie store (underwear, bras, panties) sold by category with variants of color, size and model. This system is the internal management tool: stock, customers, orders and finance. Customers never see it.

## Problem

Sales happen through Instagram and WhatsApp, delivery is by mail (Correios) or in person. Stock lives in a notebook, money in and out is not recorded, and customer history is buried in chat threads. Nobody knows exactly what is out of stock, who has not paid yet, which customers buy the most, or what the real profit is. The owner works from a phone, often with unstable internet, and more family members will need different levels of access over time.

## Solution

An offline-first management system. Phase 1 is a Spring Boot REST API that is the source of truth: it validates business rules, keeps an audit trail and exposes sync endpoints. Phase 2 is a responsive React web app (installable PWA, mobile-first) that works as a thin client: it saves everything in a local IndexedDB cache, queues changes made offline and syncs when the connection comes back. The front end is hosted on Vercel; the API is hosted separately.

The public online store is out of scope. Only the internal CRM + ERP exists.

## Users

Roles are not fixed in code. The administrator creates roles and ticks permissions one by one. Hiring someone new means creating a user and picking a role.

**Administrator** — governs the system: users, roles, permissions, devices, audit trail, stock divergence alerts. Can see everything.

**Any other user** — gets exactly the permissions of their role. Screens without permission are hidden in the UI, but the API is the one that enforces it.

Family members (owner, sister, father) are only examples of possible roles, not the source of truth.

## Product Purpose

Chic Charmosa Manager digitizes stock, customers, orders and finance for the store and keeps working without internet.

Success looks like:
- A sale is registered in under a minute, with or without internet
- Stock balance is always explainable: every unit in or out has a movement behind it
- The administrator can see who changed what, from which device, and when
- The daily cash closing matches the money actually received
- Zero lost records because of a dropped connection

## Journey End-to-End

1. **Access**: administrator creates a user and a role → user logs in → the device is registered → app downloads the data that user is allowed to see
2. **Catalog and stock**: register categories, products and variants (color, size, model, price, cost) → register incoming merchandise, which creates stock movements
3. **Customers**: register customer and addresses → purchase history comes from their orders
4. **Order**: create order (stock is reserved) → mark as paid (money enters the cash) → ship by mail with tracking code or deliver in person → delivered, or cancelled (stock returns)
5. **Finance**: record income and expenses in the store account or the personal account → daily cash closing → profit report
6. **Sync**: changes made offline are queued → push/pull when online → conflicts and stock divergences are audited and shown to the administrator

## Tech Stack (Locked for V1)

| Layer | Technologies |
|-------|-------------|
| Backend | Java 21, Spring Boot 3.5, Maven, Spring Data JPA (Hibernate), PostgreSQL, Flyway, Spring Security + JJWT, Spring HATEOAS, Bean Validation, springdoc-openapi |
| Local environment | Docker Compose (PostgreSQL) |
| API tests | Insomnia collection, plus few automated tests on critical rules |
| Frontend (phase 2) | React + Vite + TypeScript, vite-plugin-pwa, IndexedDB (Dexie), TanStack Query, Tailwind |
| Hosting | Frontend on Vercel. API: local + tunnel for the exam demo, final host still open |

Architecture: layered (controller → service → repository → entity), plus dto (records), assembler (HATEOAS links), security, config and exception. Base package `br.com.chiccharmosa`, main class `ChicCharmosaApplication`. Flyway owns the schema, Hibernate runs with `ddl-auto=validate`.

## Domain Model

| Module | Entities |
|--------|----------|
| Access | `User`, `Role`, `Permission`, `Device`, `RefreshToken` |
| Audit and sync | `AuditLog`, `SyncOperation` (applied operations, makes push idempotent) |
| Catalog | `Category`, `Product`, `ProductVariant` |
| Stock | `Supplier`, `StockEntry`, `StockEntryItem`, `StockMovement` |
| CRM | `Customer`, `Address` |
| Sales | `Order`, `OrderItem`, `Payment`, `Shipment`, `Coupon` |
| Finance | `FinancialAccount`, `FinancialCategory`, `FinancialEntry`, `CashClosing` |

Every table has a client-generated UUID as primary key, `createdAt`, `updatedAt` and `deletedAt` (soft delete). Money is `BigDecimal`, timestamps are stored in UTC.

## Business Rules

- **BR-01** Stock balance of a variant is the sum of its stock movements. It is never edited directly.
- **BR-02** Creating an order reserves stock (out movement). Cancelling creates a reversal movement. A new stock entry adds to the balance.
- **BR-03** A variant with balance zero or below is shown as `SOLD_OUT`.
- **BR-04** Order status flow: `PENDING → PAID → SHIPPED → DELIVERED`. `CANCELLED` is allowed from `PENDING` or `PAID`. Any other transition is rejected.
- **BR-05** Marking an order as `PAID` creates the income entry in the finance module.
- **BR-06** Shipment is `POST_OFFICE` (with tracking code) or `IN_PERSON`. Payment is `PIX`, `CARD` or `CASH`.
- **BR-07** A coupon respects its validity period and usage limit. The discount applies to the items subtotal only, never to shipping.
- **BR-08** Finance is split by account: `STORE` or `PERSONAL`.
- **BR-09** Cash closing is daily and unique per account per day. It sums the day's entries by payment method.
- **BR-10** Profit = sales revenue − cost of the sold goods − expenses. The unit cost is copied to the order item at sale time. Shipping is a pass-through: sales revenue is `subtotal − discount` (shipping excluded) and expenses in the `Shipping` category are left out of the calculation.
- **BR-11** Online, two simultaneous sales of the last unit are serialized by a row lock; the second gets `409` with stock insufficient.
- **BR-12** Offline sales are accepted at sync even if the stock runs out. The order is kept, the balance may go negative and a stock divergence alert is raised for the administrator.
- **BR-13** An order has an optional shipping cost (≥ 0, default 0), editable only while the order is `PENDING`. `total = subtotal − discount + shippingCost`. The payment amount and the income entry use this total, shipping included.

## Security

- Login with e-mail and password (BCrypt). Returns a JWT access token (15 minutes) and a refresh token (7 days) stored hashed in the database and revocable.
- Refresh exists because the app must keep working offline and renew the session when it reconnects. Disabling a user or device revokes the refresh token.
- Permissions are read from the database on every request, not from the token, so changing a role takes effect immediately.
- Each endpoint requires a permission such as `product:view`, `product:edit`, `stock:adjust`, `order:create`, `order:cancel`, `customer:edit`, `finance:view`, `finance:edit`, `audit:view`, `user:manage`, `role:manage`.
- The API rechecks permission for every operation in a sync push. A rejected operation returns with the reason.
- Pull only returns data the user may see.
- Hardening: CORS limited to the front end origin, JWT secret from environment variable, login rate limit, Swagger disabled in production, errors without stack trace, database not publicly reachable.

## Sync

- Endpoints: `POST /api/sync/push` and `GET /api/sync/pull?since=`. They reuse the same services and rules as the REST resources.
- **Push** receives a batch of operations, each with its own UUID. Already applied operations are skipped, so resending a batch never duplicates data. Each operation returns `APPLIED`, `IGNORED` (a newer version exists) or `REJECTED` (with reason).
- **Conflict rule**: last change wins, on the whole record. It compares the time the user made the change, capped at the server time (a future time is never accepted). The audit log keeps the before and after.
- **Stock movements are append-only**, never last-change-wins.
- **Order status** follows BR-04. An invalid transition is rejected and shown to the administrator.
- **Arrival order decides stock disputes**: whoever syncs first gets the unit. The other order is kept and flagged (BR-12).
- **Pull** is paginated, includes soft deleted records, is filtered by permission and uses the server `updatedAt` as cursor.
- `AuditLog` is written by the API during push, never by the client. It stores user, device, entity, before, after, the time the user acted and the time it synced. Only users with `audit:view` can read it.
- The client updates its own local stock right after selling, so the same device never sells the same unit twice. Logout clears the local database.

## API Design

- Resources in the plural with clear URIs (`/api/orders/{id}`), standard HTTP verbs and status codes (200, 201 with `Location`, 204, 400, 401, 403, 404, 409, 422).
- Richardson level 3: HAL responses with `_links` (self, collection, related resources and the actions available for the current state, for example a `PENDING` order exposes `pay` and `cancel`, a `DELIVERED` one does not).
- Paginated listings with navigation links. Search endpoints where they make sense (by name, category, status, date, customer).
- Sync endpoints are batch operations and are not hypermedia resources. This is an intended exception, explained in the presentation.

## Exam Requirements (Spring Boot, due 2026-11-01)

- REST API with create, read, update, delete, list and find by id
- Spring Data JPA, repositories, relationships, real relational database
- Flyway: structure, primary and foreign keys, constraints, seed data for tests
- JWT authentication, protected endpoints, role-based access control
- Richardson maturity model with HATEOAS and visible evidence
- Insomnia collection for all endpoints
- Front end consuming the API (phase 2, minimum flows: login, products, stock entry, customers, orders, cash closing)
- Three videos: architecture, endpoint tests, front end (code and running app)
- README with project name, description, technologies, how to run, database setup, initial user, authentication
- Cite the materials used

## Out of Scope

Public online store, customer login, WhatsApp automation, invoice issuing, real payment gateway, field-level merge of conflicts.

## Brand Personality

Calm, clean, direct. A work tool, not a storefront.

## Design Principles

1. **Phone first** — designed for one hand on a phone, desktop comes for free
2. **Speed is a feature** — registering a sale never waits for the network
3. **State transparency** — every record and the sync itself show clearly what is saved, pending or failed
4. **Zero ambiguity** — labels and feedback leave no room to guess, for example when stock is divergent
5. **Data integrity over looks** — when form and function conflict, function wins

## Accessibility & Inclusion

- WCAG AA minimum
- Tap targets of at least 48dp
- Reduced motion support
- Brazilian Portuguese as the interface language
- Responsive from phone to desktop

## Assumptions to Review

- Java 21 (use 17 if 21 is not installed)
- Boot 3.5.x on purpose, even if the project generator offers a newer major version
- Error messages shown to the user are in Portuguese, all identifiers are in English
- The API temporary demo host (tunnel or free tier) is decided in the implementation plan
- Shipping treated as pass-through (BR-10, BR-13) hides the cost of free shipping: when the store pays the carrier and charges the customer 0, that expense does not reduce the reported profit
