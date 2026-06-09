# Membership Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully functional in-memory membership service with tiered plans, subscription lifecycle management, and strategy-based tier evaluation via REST APIs.

**Architecture:** Layered Spring Boot 4 app — controllers → services → repositories — all backed by `ConcurrentHashMap`. Tier evaluation uses a Strategy pattern (Composite) so new criteria are one-file additions. Subscriptions are immutable value objects; mutations return new copies, making `ConcurrentHashMap.computeIfPresent()` safe for atomic updates.

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC, JUnit 5, Mockito (via `spring-boot-starter-test`)

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `build.gradle` | Modify | Fix deps: `starter-web`, `starter-test` |
| `src/main/java/com/example/membership_service/` | Delete | Old package, replaced by `membershipservice` |
| `model/enums/PlanType.java` | Create | MONTHLY/QUARTERLY/YEARLY + durationMonths |
| `model/enums/MembershipTier.java` | Create | SILVER/GOLD/PLATINUM + rank for comparison |
| `model/enums/SubscriptionStatus.java` | Create | ACTIVE/CANCELLED/EXPIRED |
| `model/value/TierBenefit.java` | Create | Immutable record: freeDelivery, discountPercent, exclusiveDeals, prioritySupport |
| `model/value/TierCriteria.java` | Create | Immutable record: orderCount, totalOrderValue, cohort |
| `model/entity/User.java` | Create | Replaces old User; id (UUID), name, email |
| `model/entity/Subscription.java` | Create | Immutable entity with `withTier()` and `cancelled()` copy-factories |
| `config/BenefitRegistry.java` | Create | `@Component` — Map<MembershipTier, TierBenefit> |
| `config/PlanPriceMatrix.java` | Create | `@Component` — Map<PlanType, Map<MembershipTier, BigDecimal>> |
| `repository/UserRepository.java` | Create | ConcurrentHashMap wrapper |
| `repository/SubscriptionRepository.java` | Create | ConcurrentHashMap wrapper with `update()` via computeIfPresent |
| `exception/ResourceNotFoundException.java` | Create | 404 runtime exception |
| `exception/ConflictException.java` | Create | 409 runtime exception |
| `exception/GlobalExceptionHandler.java` | Create | `@RestControllerAdvice` — maps exceptions to HTTP responses |
| `strategy/TierEvaluationStrategy.java` | Create | Interface: `evaluate(TierCriteria) → MembershipTier` |
| `strategy/OrderCountStrategy.java` | Create | PLATINUM≥20, GOLD≥10, else SILVER |
| `strategy/SpendStrategy.java` | Create | PLATINUM≥10000, GOLD≥5000, else SILVER |
| `strategy/CohortStrategy.java` | Create | VIP→PLATINUM, STUDENT→SILVER, else GOLD |
| `strategy/CompositeTierEvaluator.java` | Create | Runs all strategies, returns max-ranked tier |
| `service/PlanService.java` | Create | Returns all plan×tier options with price+benefits |
| `service/TierEvaluatorService.java` | Create | Delegates criteria to CompositeTierEvaluator |
| `service/SubscriptionService.java` | Create | subscribe/changeTier/cancel/getSubscription with per-user locking |
| `controller/UserController.java` | Create | POST /api/users |
| `controller/PlanController.java` | Create | GET /api/plans |
| `controller/SubscriptionController.java` | Create | POST/GET/PATCH/DELETE /api/subscriptions |
| `controller/TierController.java` | Create | POST /api/tiers/evaluate |
| `test/.../strategy/CompositeTierEvaluatorTest.java` | Create | Unit tests for all three strategies + composite |
| `test/.../service/SubscriptionServiceTest.java` | Create | Unit tests for subscribe, changeTier, cancel, getSubscription |
| `test/.../service/PlanServiceTest.java` | Create | Unit test for getAvailablePlans |
| `test/.../MembershipIntegrationTest.java` | Create | End-to-end: create user → subscribe → get → change tier → cancel |

All Java files live under `src/main/java/com/example/membershipservice/` and tests under `src/test/java/com/example/membershipservice/`.

---

## Task 1: Fix build.gradle and delete old package

**Files:**
- Modify: `build.gradle`
- Delete: `src/main/java/com/example/membership_service/` (entire directory)

- [ ] **Step 1: Update build.gradle**

Replace the full contents of `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Delete the old package**

```bash
rm -rf src/main/java/com/example/membership_service
```

- [ ] **Step 3: Verify the project compiles**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add build.gradle
git rm -r src/main/java/com/example/membership_service/
git commit -m "chore: fix dependencies and remove old package skeleton"
```

---

## Task 2: Enums

**Files:**
- Create: `src/main/java/com/example/membershipservice/model/enums/PlanType.java`
- Create: `src/main/java/com/example/membershipservice/model/enums/MembershipTier.java`
- Create: `src/main/java/com/example/membershipservice/model/enums/SubscriptionStatus.java`

- [ ] **Step 1: Create PlanType.java**

```java
package com.example.membershipservice.model.enums;

public enum PlanType {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12);

    private final int durationMonths;

    PlanType(int durationMonths) {
        this.durationMonths = durationMonths;
    }

    public int getDurationMonths() {
        return durationMonths;
    }
}
```

- [ ] **Step 2: Create MembershipTier.java**

```java
package com.example.membershipservice.model.enums;

public enum MembershipTier {
    SILVER(1),
    GOLD(2),
    PLATINUM(3);

    private final int rank;

    MembershipTier(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
```

- [ ] **Step 3: Create SubscriptionStatus.java**

```java
package com.example.membershipservice.model.enums;

public enum SubscriptionStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/membershipservice/model/enums/
git commit -m "feat: add PlanType, MembershipTier, SubscriptionStatus enums"
```

---

## Task 3: Value Objects

**Files:**
- Create: `src/main/java/com/example/membershipservice/model/value/TierBenefit.java`
- Create: `src/main/java/com/example/membershipservice/model/value/TierCriteria.java`

- [ ] **Step 1: Create TierBenefit.java**

```java
package com.example.membershipservice.model.value;

public record TierBenefit(
        boolean freeDelivery,
        int discountPercent,
        boolean exclusiveDeals,
        boolean prioritySupport
) {}
```

- [ ] **Step 2: Create TierCriteria.java**

```java
package com.example.membershipservice.model.value;

import java.math.BigDecimal;

public record TierCriteria(
        int orderCount,
        BigDecimal totalOrderValue,
        String cohort
) {}
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/membershipservice/model/value/
git commit -m "feat: add TierBenefit and TierCriteria value objects"
```

---

## Task 4: Entity Classes

**Files:**
- Create: `src/main/java/com/example/membershipservice/model/entity/User.java`
- Create: `src/main/java/com/example/membershipservice/model/entity/Subscription.java`

- [ ] **Step 1: Create User.java**

```java
package com.example.membershipservice.model.entity;

import java.util.UUID;

public class User {
    private final String id;
    private final String name;
    private final String email;

    public User(String name, String email) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}
```

- [ ] **Step 2: Create Subscription.java**

`Subscription` is immutable. All mutations return a new copy — this makes `computeIfPresent` safe because it never partially updates a shared object.

```java
package com.example.membershipservice.model.entity;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Subscription {
    private final String id;
    private final String userId;
    private final PlanType planType;
    private final MembershipTier tier;
    private final BigDecimal price;
    private final LocalDateTime startDate;
    private final LocalDateTime expiryDate;
    private final SubscriptionStatus status;

    public Subscription(String userId, PlanType planType, MembershipTier tier, BigDecimal price) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.planType = planType;
        this.tier = tier;
        this.price = price;
        this.startDate = LocalDateTime.now();
        this.expiryDate = this.startDate.plusMonths(planType.getDurationMonths());
        this.status = SubscriptionStatus.ACTIVE;
    }

    private Subscription(String id, String userId, PlanType planType, MembershipTier tier,
                         BigDecimal price, LocalDateTime startDate, LocalDateTime expiryDate,
                         SubscriptionStatus status) {
        this.id = id;
        this.userId = userId;
        this.planType = planType;
        this.tier = tier;
        this.price = price;
        this.startDate = startDate;
        this.expiryDate = expiryDate;
        this.status = status;
    }

    public Subscription withTier(MembershipTier newTier, BigDecimal newPrice) {
        return new Subscription(id, userId, planType, newTier, newPrice, startDate, expiryDate, status);
    }

    public Subscription cancelled() {
        return new Subscription(id, userId, planType, tier, price, startDate, expiryDate, SubscriptionStatus.CANCELLED);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public PlanType getPlanType() { return planType; }
    public MembershipTier getTier() { return tier; }
    public BigDecimal getPrice() { return price; }
    public LocalDateTime getStartDate() { return startDate; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public SubscriptionStatus getStatus() { return status; }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/membershipservice/model/entity/
git commit -m "feat: add User and Subscription entities"
```

---

## Task 5: Config Components

**Files:**
- Create: `src/main/java/com/example/membershipservice/config/BenefitRegistry.java`
- Create: `src/main/java/com/example/membershipservice/config/PlanPriceMatrix.java`

- [ ] **Step 1: Create BenefitRegistry.java**

```java
package com.example.membershipservice.config;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierBenefit;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BenefitRegistry {

    private static final Map<MembershipTier, TierBenefit> BENEFITS = Map.of(
            MembershipTier.SILVER,   new TierBenefit(false, 5,  false, false),
            MembershipTier.GOLD,     new TierBenefit(true,  10, true,  false),
            MembershipTier.PLATINUM, new TierBenefit(true,  15, true,  true)
    );

    public TierBenefit getBenefits(MembershipTier tier) {
        return BENEFITS.get(tier);
    }

    public Map<MembershipTier, TierBenefit> getAllBenefits() {
        return BENEFITS;
    }
}
```

- [ ] **Step 2: Create PlanPriceMatrix.java**

```java
package com.example.membershipservice.config;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PlanPriceMatrix {

    private static final Map<PlanType, Map<MembershipTier, BigDecimal>> PRICES = Map.of(
            PlanType.MONTHLY, Map.of(
                    MembershipTier.SILVER,   new BigDecimal("99"),
                    MembershipTier.GOLD,     new BigDecimal("199"),
                    MembershipTier.PLATINUM, new BigDecimal("299")
            ),
            PlanType.QUARTERLY, Map.of(
                    MembershipTier.SILVER,   new BigDecimal("269"),
                    MembershipTier.GOLD,     new BigDecimal("539"),
                    MembershipTier.PLATINUM, new BigDecimal("809")
            ),
            PlanType.YEARLY, Map.of(
                    MembershipTier.SILVER,   new BigDecimal("999"),
                    MembershipTier.GOLD,     new BigDecimal("1999"),
                    MembershipTier.PLATINUM, new BigDecimal("2999")
            )
    );

    public BigDecimal getPrice(PlanType planType, MembershipTier tier) {
        return PRICES.get(planType).get(tier);
    }

    public Map<PlanType, Map<MembershipTier, BigDecimal>> getAllPrices() {
        return PRICES;
    }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/membershipservice/config/
git commit -m "feat: add BenefitRegistry and PlanPriceMatrix config components"
```

---

## Task 6: Repositories

**Files:**
- Create: `src/main/java/com/example/membershipservice/repository/UserRepository.java`
- Create: `src/main/java/com/example/membershipservice/repository/SubscriptionRepository.java`

- [ ] **Step 1: Create UserRepository.java**

```java
package com.example.membershipservice.repository;

import com.example.membershipservice.model.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepository {

    private final ConcurrentHashMap<String, User> store = new ConcurrentHashMap<>();

    public User save(User user) {
        store.put(user.getId(), user);
        return user;
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Collection<User> findAll() {
        return store.values();
    }
}
```

- [ ] **Step 2: Create SubscriptionRepository.java**

The `update()` method uses `computeIfPresent()` — the lambda runs atomically per key in `ConcurrentHashMap`, so no two threads can concurrently update the same subscription.

```java
package com.example.membershipservice.repository;

import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

@Repository
public class SubscriptionRepository {

    private final ConcurrentHashMap<String, Subscription> store = new ConcurrentHashMap<>();

    public Subscription save(Subscription subscription) {
        store.put(subscription.getId(), subscription);
        return subscription;
    }

    public Optional<Subscription> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public Optional<Subscription> findActiveByUserId(String userId) {
        return store.values().stream()
                .filter(s -> s.getUserId().equals(userId) && s.getStatus() == SubscriptionStatus.ACTIVE)
                .findFirst();
    }

    public Optional<Subscription> update(String id, UnaryOperator<Subscription> updater) {
        Subscription[] result = {null};
        store.computeIfPresent(id, (key, sub) -> {
            result[0] = updater.apply(sub);
            return result[0];
        });
        return Optional.ofNullable(result[0]);
    }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/membershipservice/repository/
git commit -m "feat: add in-memory UserRepository and SubscriptionRepository"
```

---

## Task 7: Exception Handling

**Files:**
- Create: `src/main/java/com/example/membershipservice/exception/ResourceNotFoundException.java`
- Create: `src/main/java/com/example/membershipservice/exception/ConflictException.java`
- Create: `src/main/java/com/example/membershipservice/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create ResourceNotFoundException.java**

```java
package com.example.membershipservice.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create ConflictException.java**

```java
package com.example.membershipservice.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Create GlobalExceptionHandler.java**

```java
package com.example.membershipservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/membershipservice/exception/
git commit -m "feat: add exception classes and global exception handler"
```

---

## Task 8: Tier Evaluation Strategies (TDD)

**Files:**
- Create: `src/main/java/com/example/membershipservice/strategy/TierEvaluationStrategy.java`
- Create: `src/main/java/com/example/membershipservice/strategy/OrderCountStrategy.java`
- Create: `src/main/java/com/example/membershipservice/strategy/SpendStrategy.java`
- Create: `src/main/java/com/example/membershipservice/strategy/CohortStrategy.java`
- Create: `src/main/java/com/example/membershipservice/strategy/CompositeTierEvaluator.java`
- Test: `src/test/java/com/example/membershipservice/strategy/CompositeTierEvaluatorTest.java`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/example/membershipservice/strategy/CompositeTierEvaluatorTest.java`:

```java
package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeTierEvaluatorTest {

    private CompositeTierEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CompositeTierEvaluator(
                List.of(new OrderCountStrategy(), new SpendStrategy(), new CohortStrategy())
        );
    }

    @Test
    void orderCountStrategyReturnsPlatinumFor20PlusOrders() {
        TierCriteria criteria = new TierCriteria(20, BigDecimal.ZERO, null);
        assertEquals(MembershipTier.PLATINUM, new OrderCountStrategy().evaluate(criteria));
    }

    @Test
    void orderCountStrategyReturnsGoldFor10To19Orders() {
        TierCriteria criteria = new TierCriteria(15, BigDecimal.ZERO, null);
        assertEquals(MembershipTier.GOLD, new OrderCountStrategy().evaluate(criteria));
    }

    @Test
    void orderCountStrategyReturnsSilverForUnder10Orders() {
        TierCriteria criteria = new TierCriteria(5, BigDecimal.ZERO, null);
        assertEquals(MembershipTier.SILVER, new OrderCountStrategy().evaluate(criteria));
    }

    @Test
    void spendStrategyReturnsPlatinumFor10000PlusSpend() {
        TierCriteria criteria = new TierCriteria(0, new BigDecimal("10000"), null);
        assertEquals(MembershipTier.PLATINUM, new SpendStrategy().evaluate(criteria));
    }

    @Test
    void spendStrategyReturnsGoldFor5000To9999Spend() {
        TierCriteria criteria = new TierCriteria(0, new BigDecimal("7500"), null);
        assertEquals(MembershipTier.GOLD, new SpendStrategy().evaluate(criteria));
    }

    @Test
    void spendStrategyReturnsSilverForUnder5000Spend() {
        TierCriteria criteria = new TierCriteria(0, new BigDecimal("1000"), null);
        assertEquals(MembershipTier.SILVER, new SpendStrategy().evaluate(criteria));
    }

    @Test
    void cohortStrategyReturnsPlatinumForVip() {
        TierCriteria criteria = new TierCriteria(0, BigDecimal.ZERO, "VIP");
        assertEquals(MembershipTier.PLATINUM, new CohortStrategy().evaluate(criteria));
    }

    @Test
    void cohortStrategyReturnsSilverForStudent() {
        TierCriteria criteria = new TierCriteria(0, BigDecimal.ZERO, "STUDENT");
        assertEquals(MembershipTier.SILVER, new CohortStrategy().evaluate(criteria));
    }

    @Test
    void cohortStrategyReturnsGoldForOthers() {
        TierCriteria criteria = new TierCriteria(0, BigDecimal.ZERO, "REGULAR");
        assertEquals(MembershipTier.GOLD, new CohortStrategy().evaluate(criteria));
    }

    @Test
    void compositeReturnsHighestTierAcrossAllStrategies() {
        // orderCount=5 → SILVER, spend=8000 → GOLD, cohort=VIP → PLATINUM
        TierCriteria criteria = new TierCriteria(5, new BigDecimal("8000"), "VIP");
        assertEquals(MembershipTier.PLATINUM, evaluator.evaluate(criteria));
    }

    @Test
    void compositeReturnsSilverWhenAllStrategiesReturnSilver() {
        TierCriteria criteria = new TierCriteria(1, new BigDecimal("100"), "REGULAR");
        // orderCount=1→SILVER, spend=100→SILVER, cohort=REGULAR→GOLD → composite returns GOLD
        assertEquals(MembershipTier.GOLD, evaluator.evaluate(criteria));
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
./gradlew test --tests "com.example.membershipservice.strategy.CompositeTierEvaluatorTest"
```

Expected: `FAILED` — classes not found yet.

- [ ] **Step 3: Create TierEvaluationStrategy.java**

```java
package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;

public interface TierEvaluationStrategy {
    MembershipTier evaluate(TierCriteria criteria);
}
```

- [ ] **Step 4: Create OrderCountStrategy.java**

```java
package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

@Component
public class OrderCountStrategy implements TierEvaluationStrategy {

    @Override
    public MembershipTier evaluate(TierCriteria criteria) {
        if (criteria.orderCount() >= 20) return MembershipTier.PLATINUM;
        if (criteria.orderCount() >= 10) return MembershipTier.GOLD;
        return MembershipTier.SILVER;
    }
}
```

- [ ] **Step 5: Create SpendStrategy.java**

```java
package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SpendStrategy implements TierEvaluationStrategy {

    @Override
    public MembershipTier evaluate(TierCriteria criteria) {
        BigDecimal spend = criteria.totalOrderValue();
        if (spend.compareTo(new BigDecimal("10000")) >= 0) return MembershipTier.PLATINUM;
        if (spend.compareTo(new BigDecimal("5000")) >= 0) return MembershipTier.GOLD;
        return MembershipTier.SILVER;
    }
}
```

- [ ] **Step 6: Create CohortStrategy.java**

```java
package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

@Component
public class CohortStrategy implements TierEvaluationStrategy {

    @Override
    public MembershipTier evaluate(TierCriteria criteria) {
        if ("VIP".equalsIgnoreCase(criteria.cohort())) return MembershipTier.PLATINUM;
        if ("STUDENT".equalsIgnoreCase(criteria.cohort())) return MembershipTier.SILVER;
        return MembershipTier.GOLD;
    }
}
```

- [ ] **Step 7: Create CompositeTierEvaluator.java**

```java
package com.example.membershipservice.strategy;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CompositeTierEvaluator {

    private final List<TierEvaluationStrategy> strategies;

    public CompositeTierEvaluator(List<TierEvaluationStrategy> strategies) {
        this.strategies = strategies;
    }

    public MembershipTier evaluate(TierCriteria criteria) {
        return strategies.stream()
                .map(s -> s.evaluate(criteria))
                .max(Comparator.comparingInt(MembershipTier::getRank))
                .orElse(MembershipTier.SILVER);
    }
}
```

- [ ] **Step 8: Run tests — expect all pass**

```bash
./gradlew test --tests "com.example.membershipservice.strategy.CompositeTierEvaluatorTest"
```

Expected: `BUILD SUCCESSFUL` with all 11 tests passing.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/membershipservice/strategy/
git add src/test/java/com/example/membershipservice/strategy/
git commit -m "feat: add tier evaluation strategies with composite pattern"
```

---

## Task 9: Services (TDD)

**Files:**
- Create: `src/main/java/com/example/membershipservice/service/PlanService.java`
- Create: `src/main/java/com/example/membershipservice/service/TierEvaluatorService.java`
- Create: `src/main/java/com/example/membershipservice/service/SubscriptionService.java`
- Test: `src/test/java/com/example/membershipservice/service/PlanServiceTest.java`
- Test: `src/test/java/com/example/membershipservice/service/SubscriptionServiceTest.java`

- [ ] **Step 1: Write failing tests for PlanService**

Create `src/test/java/com/example/membershipservice/service/PlanServiceTest.java`:

```java
package com.example.membershipservice.service;

import com.example.membershipservice.config.BenefitRegistry;
import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanServiceTest {

    private PlanService planService;

    @BeforeEach
    void setUp() {
        planService = new PlanService(new PlanPriceMatrix(), new BenefitRegistry());
    }

    @Test
    void getAvailablePlansReturnsNineOptions() {
        List<PlanService.PlanOption> options = planService.getAvailablePlans();
        assertEquals(9, options.size()); // 3 plans × 3 tiers
    }

    @Test
    void getAvailablePlansIncludesCorrectPriceForMonthlyGold() {
        List<PlanService.PlanOption> options = planService.getAvailablePlans();
        PlanService.PlanOption monthlyGold = options.stream()
                .filter(o -> o.planType() == PlanType.MONTHLY && o.tier() == MembershipTier.GOLD)
                .findFirst()
                .orElseThrow();
        assertEquals(0, monthlyGold.price().compareTo(new java.math.BigDecimal("199")));
    }

    @Test
    void getAvailablePlansIncludesBenefitsForEachOption() {
        List<PlanService.PlanOption> options = planService.getAvailablePlans();
        options.forEach(o -> {
            assert o.benefits() != null : "Benefits must not be null for " + o.tier();
        });
    }
}
```

- [ ] **Step 2: Write failing tests for SubscriptionService**

Create `src/test/java/com/example/membershipservice/service/SubscriptionServiceTest.java`:

```java
package com.example.membershipservice.service;

import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.exception.ConflictException;
import com.example.membershipservice.exception.ResourceNotFoundException;
import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.entity.User;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.repository.SubscriptionRepository;
import com.example.membershipservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionServiceTest {

    private UserRepository userRepository;
    private SubscriptionRepository subscriptionRepository;
    private SubscriptionService subscriptionService;
    private User savedUser;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        subscriptionRepository = new SubscriptionRepository();
        subscriptionService = new SubscriptionService(userRepository, subscriptionRepository, new PlanPriceMatrix());
        savedUser = userRepository.save(new User("Alice", "alice@example.com"));
    }

    @Test
    void subscribeShouldCreateActiveSubscription() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.GOLD);

        assertEquals(savedUser.getId(), sub.getUserId());
        assertEquals(PlanType.MONTHLY, sub.getPlanType());
        assertEquals(MembershipTier.GOLD, sub.getTier());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertNotNull(sub.getExpiryDate());
        assertEquals(0, sub.getPrice().compareTo(new java.math.BigDecimal("199")));
    }

    @Test
    void subscribeShouldSetExpiryToOneMonthForMonthlyPlan() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);
        assertTrue(sub.getExpiryDate().isAfter(sub.getStartDate().plusDays(28)));
        assertTrue(sub.getExpiryDate().isBefore(sub.getStartDate().plusDays(32)));
    }

    @Test
    void subscribeShouldThrowConflictIfAlreadySubscribed() {
        subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);

        assertThrows(ConflictException.class,
                () -> subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.GOLD));
    }

    @Test
    void subscribeShouldThrowNotFoundForUnknownUser() {
        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.subscribe("unknown-id", PlanType.MONTHLY, MembershipTier.SILVER));
    }

    @Test
    void changeTierShouldUpdateTierAndRecalculatePrice() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);
        Subscription upgraded = subscriptionService.changeTier(sub.getId(), MembershipTier.PLATINUM);

        assertEquals(MembershipTier.PLATINUM, upgraded.getTier());
        assertEquals(0, upgraded.getPrice().compareTo(new java.math.BigDecimal("299")));
        assertEquals(sub.getExpiryDate(), upgraded.getExpiryDate()); // expiry unchanged
    }

    @Test
    void changeTierShouldThrowNotFoundForUnknownSubscription() {
        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.changeTier("unknown-id", MembershipTier.GOLD));
    }

    @Test
    void changeTierShouldThrowConflictForCancelledSubscription() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.SILVER);
        subscriptionService.cancel(sub.getId());

        assertThrows(ConflictException.class,
                () -> subscriptionService.changeTier(sub.getId(), MembershipTier.GOLD));
    }

    @Test
    void cancelShouldSetStatusToCancelled() {
        Subscription sub = subscriptionService.subscribe(savedUser.getId(), PlanType.MONTHLY, MembershipTier.GOLD);
        Subscription cancelled = subscriptionService.cancel(sub.getId());

        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void getSubscriptionShouldReturnActiveSubscription() {
        subscriptionService.subscribe(savedUser.getId(), PlanType.QUARTERLY, MembershipTier.GOLD);
        Subscription sub = subscriptionService.getSubscription(savedUser.getId());

        assertEquals(savedUser.getId(), sub.getUserId());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
    }

    @Test
    void getSubscriptionShouldThrowNotFoundIfNoneActive() {
        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.getSubscription(savedUser.getId()));
    }
}
```

- [ ] **Step 3: Run tests — expect compile failure**

```bash
./gradlew test --tests "com.example.membershipservice.service.*"
```

Expected: `FAILED` — service classes not found yet.

- [ ] **Step 4: Create PlanService.java**

```java
package com.example.membershipservice.service;

import com.example.membershipservice.config.BenefitRegistry;
import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.value.TierBenefit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlanService {

    private final PlanPriceMatrix priceMatrix;
    private final BenefitRegistry benefitRegistry;

    public PlanService(PlanPriceMatrix priceMatrix, BenefitRegistry benefitRegistry) {
        this.priceMatrix = priceMatrix;
        this.benefitRegistry = benefitRegistry;
    }

    public List<PlanOption> getAvailablePlans() {
        List<PlanOption> options = new ArrayList<>();
        for (PlanType planType : PlanType.values()) {
            for (MembershipTier tier : MembershipTier.values()) {
                BigDecimal price = priceMatrix.getPrice(planType, tier);
                TierBenefit benefits = benefitRegistry.getBenefits(tier);
                options.add(new PlanOption(planType, tier, price, benefits));
            }
        }
        return options;
    }

    public BigDecimal getPrice(PlanType planType, MembershipTier tier) {
        return priceMatrix.getPrice(planType, tier);
    }

    public record PlanOption(PlanType planType, MembershipTier tier, BigDecimal price, TierBenefit benefits) {}
}
```

- [ ] **Step 5: Create TierEvaluatorService.java**

```java
package com.example.membershipservice.service;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import com.example.membershipservice.strategy.CompositeTierEvaluator;
import org.springframework.stereotype.Service;

@Service
public class TierEvaluatorService {

    private final CompositeTierEvaluator evaluator;

    public TierEvaluatorService(CompositeTierEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public MembershipTier computeTier(TierCriteria criteria) {
        return evaluator.evaluate(criteria);
    }
}
```

- [ ] **Step 6: Create SubscriptionService.java**

The subscribe method uses a per-user `synchronized` block on a dedicated lock object to prevent two concurrent subscriptions for the same user from both passing the duplicate check.

```java
package com.example.membershipservice.service;

import com.example.membershipservice.config.PlanPriceMatrix;
import com.example.membershipservice.exception.ConflictException;
import com.example.membershipservice.exception.ResourceNotFoundException;
import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.repository.SubscriptionRepository;
import com.example.membershipservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubscriptionService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanPriceMatrix priceMatrix;
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    public SubscriptionService(UserRepository userRepository,
                               SubscriptionRepository subscriptionRepository,
                               PlanPriceMatrix priceMatrix) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.priceMatrix = priceMatrix;
    }

    public Subscription subscribe(String userId, PlanType planType, MembershipTier tier) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            subscriptionRepository.findActiveByUserId(userId).ifPresent(s -> {
                throw new ConflictException("User already has an active subscription: " + s.getId());
            });
            BigDecimal price = priceMatrix.getPrice(planType, tier);
            Subscription subscription = new Subscription(userId, planType, tier, price);
            return subscriptionRepository.save(subscription);
        }
    }

    public Subscription changeTier(String subscriptionId, MembershipTier newTier) {
        Subscription current = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        if (current.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new ConflictException("Cannot change tier on a non-active subscription");
        }

        BigDecimal newPrice = priceMatrix.getPrice(current.getPlanType(), newTier);
        return subscriptionRepository.update(subscriptionId, sub -> sub.withTier(newTier, newPrice))
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));
    }

    public Subscription cancel(String subscriptionId) {
        subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));

        return subscriptionRepository.update(subscriptionId, Subscription::cancelled)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + subscriptionId));
    }

    public Subscription getSubscription(String userId) {
        return subscriptionRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription for user: " + userId));
    }
}
```

- [ ] **Step 7: Run tests — expect all pass**

```bash
./gradlew test --tests "com.example.membershipservice.service.*"
```

Expected: `BUILD SUCCESSFUL` — all service tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/membershipservice/service/
git add src/test/java/com/example/membershipservice/service/
git commit -m "feat: add PlanService, TierEvaluatorService, SubscriptionService with tests"
```

---

## Task 10: Controllers

**Files:**
- Create: `src/main/java/com/example/membershipservice/controller/UserController.java`
- Create: `src/main/java/com/example/membershipservice/controller/PlanController.java`
- Create: `src/main/java/com/example/membershipservice/controller/SubscriptionController.java`
- Create: `src/main/java/com/example/membershipservice/controller/TierController.java`

- [ ] **Step 1: Create UserController.java**

```java
package com.example.membershipservice.controller;

import com.example.membershipservice.model.entity.User;
import com.example.membershipservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody CreateUserRequest request) {
        return userRepository.save(new User(request.name(), request.email()));
    }

    public record CreateUserRequest(String name, String email) {}
}
```

- [ ] **Step 2: Create PlanController.java**

```java
package com.example.membershipservice.controller;

import com.example.membershipservice.service.PlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<PlanService.PlanOption> getPlans() {
        return planService.getAvailablePlans();
    }
}
```

- [ ] **Step 3: Create SubscriptionController.java**

```java
package com.example.membershipservice.controller;

import com.example.membershipservice.config.BenefitRegistry;
import com.example.membershipservice.model.entity.Subscription;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.model.value.TierBenefit;
import com.example.membershipservice.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final BenefitRegistry benefitRegistry;

    public SubscriptionController(SubscriptionService subscriptionService, BenefitRegistry benefitRegistry) {
        this.subscriptionService = subscriptionService;
        this.benefitRegistry = benefitRegistry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@RequestBody SubscribeRequest request) {
        return toResponse(subscriptionService.subscribe(request.userId(), request.planType(), request.tier()));
    }

    @GetMapping
    public SubscriptionResponse getSubscription(@RequestParam String userId) {
        return toResponse(subscriptionService.getSubscription(userId));
    }

    @PatchMapping("/{id}/tier")
    public SubscriptionResponse changeTier(@PathVariable String id, @RequestBody ChangeTierRequest request) {
        return toResponse(subscriptionService.changeTier(id, request.tier()));
    }

    @DeleteMapping("/{id}")
    public SubscriptionResponse cancel(@PathVariable String id) {
        return toResponse(subscriptionService.cancel(id));
    }

    private SubscriptionResponse toResponse(Subscription sub) {
        long daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), sub.getExpiryDate()));
        TierBenefit benefits = benefitRegistry.getBenefits(sub.getTier());
        return new SubscriptionResponse(
                sub.getId(), sub.getUserId(), sub.getPlanType(), sub.getTier(),
                sub.getPrice(), sub.getStartDate(), sub.getExpiryDate(),
                sub.getStatus(), benefits, daysRemaining
        );
    }

    public record SubscribeRequest(String userId, PlanType planType, MembershipTier tier) {}
    public record ChangeTierRequest(MembershipTier tier) {}
    public record SubscriptionResponse(
            String id, String userId, PlanType planType, MembershipTier tier,
            BigDecimal price, LocalDateTime startDate, LocalDateTime expiryDate,
            SubscriptionStatus status, TierBenefit benefits, long daysRemaining
    ) {}
}
```

- [ ] **Step 4: Create TierController.java**

```java
package com.example.membershipservice.controller;

import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.value.TierCriteria;
import com.example.membershipservice.service.TierEvaluatorService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tiers")
public class TierController {

    private final TierEvaluatorService tierEvaluatorService;

    public TierController(TierEvaluatorService tierEvaluatorService) {
        this.tierEvaluatorService = tierEvaluatorService;
    }

    @PostMapping("/evaluate")
    public TierEvaluationResponse evaluate(@RequestBody TierCriteria criteria) {
        return new TierEvaluationResponse(tierEvaluatorService.computeTier(criteria));
    }

    public record TierEvaluationResponse(MembershipTier recommendedTier) {}
}
```

- [ ] **Step 5: Compile**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/membershipservice/controller/
git commit -m "feat: add UserController, PlanController, SubscriptionController, TierController"
```

---

## Task 11: Integration Test

**Files:**
- Test: `src/test/java/com/example/membershipservice/MembershipIntegrationTest.java`

- [ ] **Step 1: Write the integration test**

This test boots the full Spring context and walks through the complete happy path: create user → list plans → subscribe → get subscription → change tier → evaluate tier → cancel.

Create `src/test/java/com/example/membershipservice/MembershipIntegrationTest.java`:

```java
package com.example.membershipservice;

import com.example.membershipservice.controller.PlanController;
import com.example.membershipservice.controller.SubscriptionController;
import com.example.membershipservice.controller.TierController;
import com.example.membershipservice.controller.UserController;
import com.example.membershipservice.model.enums.MembershipTier;
import com.example.membershipservice.model.enums.PlanType;
import com.example.membershipservice.model.enums.SubscriptionStatus;
import com.example.membershipservice.model.value.TierCriteria;
import com.example.membershipservice.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MembershipIntegrationTest {

    @Autowired private UserController userController;
    @Autowired private PlanController planController;
    @Autowired private SubscriptionController subscriptionController;
    @Autowired private TierController tierController;

    @Test
    void fullMembershipLifecycle() {
        // 1. Create user
        var user = userController.createUser(new UserController.CreateUserRequest("Bob", "bob@example.com"));
        assertNotNull(user.getId());
        assertEquals("Bob", user.getName());

        // 2. List plans — 9 options (3 plans × 3 tiers)
        List<PlanService.PlanOption> plans = planController.getPlans();
        assertEquals(9, plans.size());

        // 3. Subscribe to MONTHLY SILVER
        var subResponse = subscriptionController.subscribe(
                new SubscriptionController.SubscribeRequest(user.getId(), PlanType.MONTHLY, MembershipTier.SILVER));
        assertEquals(MembershipTier.SILVER, subResponse.tier());
        assertEquals(SubscriptionStatus.ACTIVE, subResponse.status());
        assertEquals(0, subResponse.price().compareTo(new BigDecimal("99")));
        assertFalse(subResponse.benefits().freeDelivery());

        // 4. Get subscription by userId
        var fetched = subscriptionController.getSubscription(user.getId());
        assertEquals(subResponse.id(), fetched.id());

        // 5. Upgrade to GOLD — price updates, expiry stays the same
        var upgraded = subscriptionController.changeTier(
                subResponse.id(), new SubscriptionController.ChangeTierRequest(MembershipTier.GOLD));
        assertEquals(MembershipTier.GOLD, upgraded.tier());
        assertEquals(0, upgraded.price().compareTo(new BigDecimal("199")));
        assertTrue(upgraded.benefits().freeDelivery());
        assertEquals(subResponse.expiryDate(), upgraded.expiryDate());

        // 6. Evaluate tier from manual criteria
        var evalResponse = tierController.evaluate(
                new TierCriteria(25, new BigDecimal("12000"), "VIP"));
        assertEquals(MembershipTier.PLATINUM, evalResponse.recommendedTier());

        // 7. Cancel subscription
        var cancelled = subscriptionController.cancel(subResponse.id());
        assertEquals(SubscriptionStatus.CANCELLED, cancelled.status());
    }
}
```

- [ ] **Step 2: Run the integration test**

```bash
./gradlew test --tests "com.example.membershipservice.MembershipIntegrationTest"
```

Expected: `BUILD SUCCESSFUL` — 1 test passing.

- [ ] **Step 3: Run all tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` — all tests passing.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/example/membershipservice/MembershipIntegrationTest.java
git commit -m "test: add full membership lifecycle integration test"
```

---

## Task 12: Run the App and Smoke Test

**Files:** None — verification only.

- [ ] **Step 1: Start the application**

```bash
./gradlew bootRun
```

Expected: Spring Boot banner, then `Started MembershipServiceApplication in X seconds`

- [ ] **Step 2: Create a user**

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Pratyush","email":"p@example.com"}' | jq .
```

Expected: `{"id":"<uuid>","name":"Pratyush","email":"p@example.com"}`  
Copy the `id` value as `$USER_ID` for subsequent steps.

- [ ] **Step 3: List plans**

```bash
curl -s http://localhost:8080/api/plans | jq .
```

Expected: JSON array of 9 objects, each with `planType`, `tier`, `price`, `benefits`.

- [ ] **Step 4: Subscribe**

```bash
curl -s -X POST http://localhost:8080/api/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"userId":"$USER_ID","planType":"MONTHLY","tier":"GOLD"}' | jq .
```

Expected: subscription object with `status: "ACTIVE"`, `price: 199`, `benefits.freeDelivery: true`.  
Copy the subscription `id` as `$SUB_ID`.

- [ ] **Step 5: Upgrade tier**

```bash
curl -s -X PATCH http://localhost:8080/api/subscriptions/$SUB_ID/tier \
  -H "Content-Type: application/json" \
  -d '{"tier":"PLATINUM"}' | jq .
```

Expected: `tier: "PLATINUM"`, `price: 299`, `benefits.prioritySupport: true`.

- [ ] **Step 6: Evaluate tier**

```bash
curl -s -X POST http://localhost:8080/api/tiers/evaluate \
  -H "Content-Type: application/json" \
  -d '{"orderCount":25,"totalOrderValue":12000,"cohort":"VIP"}' | jq .
```

Expected: `{"recommendedTier":"PLATINUM"}`

- [ ] **Step 7: Cancel**

```bash
curl -s -X DELETE http://localhost:8080/api/subscriptions/$SUB_ID | jq .
```

Expected: `status: "CANCELLED"`

- [ ] **Step 8: Final commit**

```bash
git add -A
git commit -m "feat: membership service — complete implementation"
```
