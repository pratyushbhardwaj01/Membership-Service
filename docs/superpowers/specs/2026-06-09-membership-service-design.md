# Membership Service — Design Spec
**Date:** 2026-06-09  
**Status:** Approved

---

## Overview

A Spring Boot 4 / Java 21 backend for FirstClub's tiered membership program. Users subscribe to a `(plan × tier)` combination, each with its own price. Tiers unlock configurable benefits. All state is held in-memory using `ConcurrentHashMap`.

---

## Domain Model

### Enums

| Enum | Values |
|------|--------|
| `PlanType` | `MONTHLY`, `QUARTERLY`, `YEARLY` |
| `MembershipTier` | `SILVER`, `GOLD`, `PLATINUM` |
| `SubscriptionStatus` | `ACTIVE`, `CANCELLED`, `EXPIRED` |

### Value Objects

**`TierBenefit`**
```
boolean freeDelivery
int     discountPercent
boolean exclusiveDeals
boolean prioritySupport
```

**`TierCriteria`** (input to tier evaluation)
```
int        orderCount
BigDecimal totalOrderValue
String     cohort
```

### Entities

**`User`**
```
String id          (UUID, generated)
String name
String email
```

**`Subscription`**
```
String             id            (UUID, generated)
String             userId
PlanType           planType
MembershipTier     tier
BigDecimal         price         (snapshot at subscription time)
LocalDateTime      startDate
LocalDateTime      expiryDate    (computed from planType: +1/3/12 months)
SubscriptionStatus status
```

### Config Components

**`BenefitRegistry`** (`@Component`)  
Single source of truth: `Map<MembershipTier, TierBenefit>`.

| Tier | freeDelivery | discountPercent | exclusiveDeals | prioritySupport |
|------|-------------|-----------------|----------------|-----------------|
| SILVER | false | 5 | false | false |
| GOLD | true | 10 | true | false |
| PLATINUM | true | 15 | true | true |

**`PlanPriceMatrix`** (`@Component`)  
`Map<PlanType, Map<MembershipTier, BigDecimal>>` — prices for every `(plan × tier)` combination.

| Plan | SILVER | GOLD | PLATINUM |
|------|--------|------|----------|
| MONTHLY | ₹99 | ₹199 | ₹299 |
| QUARTERLY | ₹269 | ₹539 | ₹809 |
| YEARLY | ₹999 | ₹1999 | ₹2999 |

---

## Repository Layer

Both repositories wrap `ConcurrentHashMap` and are Spring `@Repository` components.

**`UserRepository`**: `findById`, `save`, `findAll`  
**`SubscriptionRepository`**: `findById`, `findByUserId`, `save`, `findAll`

**Concurrency strategy:**  
- Reads and independent writes use `ConcurrentHashMap` without extra locking.
- Mutation of an existing subscription (tier change, cancellation) uses `computeIfPresent()` — atomic per-key update that eliminates TOCTOU races without locking the whole map.

---

## Tier Evaluation — Strategy Pattern

```
TierEvaluationStrategy (interface)
  MembershipTier evaluate(TierCriteria criteria)

OrderCountStrategy
  PLATINUM if orderCount >= 20
  GOLD     if orderCount >= 10
  SILVER   otherwise

SpendStrategy
  PLATINUM if totalOrderValue >= 10000
  GOLD     if totalOrderValue >= 5000
  SILVER   otherwise

CohortStrategy
  PLATINUM if cohort == "VIP"
  SILVER   if cohort == "STUDENT"
  GOLD     otherwise

CompositeTierEvaluator
  Runs all strategies, returns the highest-ranked result.
  New criteria → add one Strategy implementation and register it.
```

---

## Service Layer

**`PlanService`**
- `getAvailablePlans()` — returns all `(PlanType × MembershipTier × price × benefits)` combinations.
- `getPrice(PlanType, MembershipTier)` — price lookup.

**`SubscriptionService`**
- `subscribe(userId, planType, tier)` — creates a new `Subscription`. Rejects if user already has an ACTIVE subscription.
- `changeTier(subscriptionId, newTier)` — updates tier and recalculates price atomically via `computeIfPresent()`. Expiry date is unchanged.
- `cancel(subscriptionId)` — sets status to `CANCELLED`.
- `getSubscription(userId)` — returns the active subscription.

**`TierEvaluatorService`**
- `computeTier(TierCriteria)` — delegates to `CompositeTierEvaluator`, returns recommended `MembershipTier`.

---

## API Layer

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/users` | Create a user |
| `GET` | `/api/plans` | List all plan+tier combinations with pricing and benefits |
| `POST` | `/api/subscriptions` | Subscribe user to a plan+tier |
| `GET` | `/api/subscriptions?userId={id}` | Get active subscription for a user |
| `PATCH` | `/api/subscriptions/{id}/tier` | Upgrade or downgrade tier |
| `DELETE` | `/api/subscriptions/{id}` | Cancel a subscription |
| `POST` | `/api/tiers/evaluate` | Compute recommended tier from manual criteria |

### Sample Payloads

**POST `/api/users`**
```json
{ "name": "Pratyush", "email": "p@example.com" }
```

**POST `/api/subscriptions`**
```json
{ "userId": "abc-123", "planType": "MONTHLY", "tier": "GOLD" }
```

**PATCH `/api/subscriptions/{id}/tier`**
```json
{ "tier": "PLATINUM" }
```

**POST `/api/tiers/evaluate`**
```json
{ "orderCount": 15, "totalOrderValue": 8000, "cohort": "VIP" }
```
Response: `{ "recommendedTier": "PLATINUM" }`

---

## Package Structure

Single root package `com.example.membershipservice` (the existing `membership_service` package is removed — all code migrates here).

```
com.example.membershipservice
  ├── controller/
  │   ├── UserController
  │   ├── PlanController
  │   ├── SubscriptionController
  │   └── TierController
  ├── service/
  │   ├── PlanService
  │   ├── SubscriptionService
  │   └── TierEvaluatorService
  ├── repository/
  │   ├── UserRepository
  │   └── SubscriptionRepository
  ├── model/
  │   ├── entity/
  │   │   ├── User
  │   │   └── Subscription
  │   ├── enums/
  │   │   ├── PlanType
  │   │   ├── MembershipTier
  │   │   └── SubscriptionStatus
  │   └── value/
  │       ├── TierBenefit
  │       └── TierCriteria
  ├── config/
  │   ├── BenefitRegistry
  │   └── PlanPriceMatrix
  └── strategy/
      ├── TierEvaluationStrategy
      ├── OrderCountStrategy
      ├── SpendStrategy
      ├── CohortStrategy
      └── CompositeTierEvaluator
```

---

## Error Handling

- `404` if user or subscription not found.
- `409` if user tries to subscribe while already having an ACTIVE subscription.
- `400` for invalid enum values (`PlanType`, `MembershipTier`) — handled by Spring's default `@ExceptionHandler`.

---

## Decisions & Trade-offs

| Decision | Rationale |
|----------|-----------|
| Pure in-memory (no DB) | Zero setup, showcases Java concurrency directly |
| Plan type as enum, not class hierarchy | Plan behaviour is identical across types; only duration and price differ |
| `computeIfPresent()` for mutations | Atomic per-key update without global lock |
| Composite strategy for tier evaluation | Adding a new criterion = one new class, zero changes elsewhere |
| Price snapshot on subscription | Protects user from price changes mid-cycle |
| Benefits as value object per tier | Immutable, easy to read, trivially extensible |
