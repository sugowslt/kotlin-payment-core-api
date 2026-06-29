# Project1 ERD Draft (Payment Core API)

## 1) 엔티티 개요

### Customer
- id (PK, bigint)
- email (varchar, unique)
- name (varchar)
- status (enum: ACTIVE, SUSPENDED)
- createdAt, updatedAt

### Order
- id (PK, bigint)
- customerId (FK -> Customer.id)
- orderNo (varchar, unique)
- amount (decimal(18,2))
- currency (varchar(3))
- status (enum: CREATED, CONFIRMED, CANCELED)
- createdAt, updatedAt

### Payment
- id (PK, bigint)
- orderId (FK -> Order.id)
- paymentNo (varchar, unique)
- idempotencyKey (varchar, unique)
- amount (decimal(18,2))
- method (enum: CARD, BANK_TRANSFER, ACCOUNT_BALANCE)
- status (enum: PENDING, APPROVED, FAILED, CANCELED)
- approvedAt (datetime, nullable)
- canceledAt (datetime, nullable)
- failureCode (varchar, nullable)
- cancelReason (varchar, nullable)
- createdAt, updatedAt

## 2) 관계
- Customer 1 : N Order
- Order 1 : N Payment

## 3) 상태값 및 상태 전이

### Payment.status
- PENDING -> APPROVED
- PENDING -> FAILED
- APPROVED -> CANCELED
- FAILED -> (종료)
- CANCELED -> (종료)

전이 제한 규칙
- FAILED 또는 CANCELED 상태에서는 APPROVED로 전이 불가
- APPROVED 상태에서만 CANCELED 가능

## 4) 비즈니스 규칙
1. 결제 금액(Payment.amount)은 주문 금액(Order.amount)과 동일해야 한다.
2. 동일 idempotencyKey로는 결제 레코드를 1건만 생성할 수 있다.
3. 주문 상태가 CANCELED면 신규 결제를 생성할 수 없다.
4. 고객 상태가 SUSPENDED면 결제 생성/승인을 거절한다.

## 5) 인덱스 후보
1. payment(idempotency_key) UNIQUE
2. payment(order_id, created_at)
3. payment(status, created_at)
4. orders(customer_id, created_at)

## 6) JPA 구현 힌트
- Payment.status, method는 enum string 매핑
- 금액은 BigDecimal 사용
- 낙관적 락이 필요하면 version 컬럼 추가 고려
