# Banking System (Microservices)

An event-driven banking backend built with **Spring Boot 4**, **Spring Cloud Gateway**, **Kafka**, **MySQL** and **Redis**. It covers account management, money transfers with fraud detection and OTP verification, online payments through Razorpay, and notifications.

## Architecture

```
                        +-------------------+
   Client / Postman --> |    api-gateway    |  :8080  (routing + Redis rate limiting)
                        +---------+---------+
                                  |
        +-------------------------+--------------------------+
        |                         |                          |
+-------v--------+      +---------v----------+      +--------v--------+
| account-service|      | transection-service|      | payment-service |
|     :8081      |      |       :8082        |      |      :8083      |
+-------+--------+      +---------+----------+      +-----------------+
        ^                         |
        |          Kafka          |
        |   +---------------------v--------------------+
        +---+  fraud-detection-service | notification  +
            +------------------------------------------+
```

| Service | Port | Responsibility | Database |
|---|---|---|---|
| `api-gateway` | 8080 | Single entry point, routing, rate limiting (Redis) | - |
| `account-service` | 8081 | Create accounts, debit / credit balances | `account_db` |
| `transection-service` | 8082 | Transfers, transaction status, OTP verification | `transaction_db` |
| `payment-service` | 8083 | Online payments via Razorpay | `payment_db` |
| `fraud-detection-service` | see `application.yml` | Fraud checks on every transfer (velocity, amount, balance) | Redis |
| `notification-service` | see `application.yml` | Sends notifications for transaction and OTP events | - |

## Tech Stack

- Java 21, Maven
- Spring Boot 4.1.x, Spring Data JPA, Spring Validation, Actuator
- Spring Cloud 5.x (Gateway, OpenFeign)
- Apache Kafka (Spring Kafka) with Zookeeper
- MySQL 8 (local installation)
- Redis (rate limiting, fraud counters, OTP storage)
- Razorpay Java SDK (test mode)

## Transfer Flow (Saga)

1. Client calls `POST /api/v1/transections/transfer` through the gateway.
2. `transection-service` saves the transaction as `PROCESSING` and publishes `transection.initiated`.
3. `fraud-detection-service` runs the checks:
    - **Velocity:** too many transactions per minute for one account
    - **Amount:** unusually high compared to the account's average
    - **Balance:** amount above the allowed percentage of the sender's balance
4. **Clean transaction:** result goes to `fraud.check.clean`, and the transfer continues (debit sender, credit receiver in `account-service`).
5. **Suspicious transaction:** event goes to `verification.required`, an OTP is generated and stored in Redis (5 minute expiry), the status becomes `PENDING_VERIFICATION`, and the OTP event goes to `transection.otp.generated`.
6. On completion, `transection.completed` is published and notifications are sent.

### Kafka Topics

| Topic | Producer | Consumer |
|---|---|---|
| `transection.initiated` | transection-service | fraud-detection-service |
| `fraud.check.clean` | fraud-detection-service | transection-service |
| `verification.required` | fraud-detection-service | transection-service |
| `transection.otp.generated` | transection-service | notification-service |
| `transection.completed` | transection-service / account-service | account-service, notification-service |
| `fraud.detected` | fraud-detection-service | account-service |

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker (for Kafka, Zookeeper and Redis)
- MySQL 8 running locally on port `3306`

## Getting Started

### 1. Clone

```bash
git clone https://github.com/yahiya06/banking-system.git
cd banking-system
```

### 2. Start infrastructure

If you use your own local MySQL, start only the other containers so port `3306` does not clash:

```bash
docker compose up -d redis zookeeper kafka
```

Check they are running with `docker ps`.

### 3. Configure the database

Each service connects with a URL like:

```
jdbc:mysql://localhost:3306/account_db?createDatabaseIfNotExist=true
```

Databases are created automatically. Set your own MySQL username and password in each service's `application.yml`.

### 4. Set secrets as environment variables

Never commit keys to Git. Use environment variables:

```yaml
razorpay:
  key-id: ${RAZORPAY_KEY_ID}
  key-secret: ${RAZORPAY_KEY_SECRET}
```

In IntelliJ: *Run Configuration -> Environment variables*.

### 5. Start the services

Start in this order and wait for each to finish starting:

1. `account-service`
2. `transection-service`
3. `payment-service`
4. `fraud-detection-service`
5. `notification-service`
6. `api-gateway` (last)

Run from the IDE, or per service:

```bash
cd account-service
mvn spring-boot:run
```

## API Examples

All requests go through the gateway at `http://localhost:8080`.

### Create account

```http
POST /api/v1/accounts
Content-Type: application/json

{
  "accountHolderName": "yahiya",
  "email": "yahiya@testmail.com",
  "phone": "2222222222",
  "accountType": "SAVINGS",
  "initialDeposit": 10000
}
```

### Transfer money

```http
POST /api/v1/transections/transfer
Content-Type: application/json

{
  "senderAccountNumber": "030914478888",
  "receiverAccountNumber": "055380719956",
  "amount": 1000,
  "description": "test transaction"
}
```

The response returns the transaction with status `PROCESSING`. It later becomes `COMPLETED`, `FAILED`, or `PENDING_VERIFICATION` (when fraud checks require OTP).

### Gateway routes

| Path | Target |
|---|---|
| `/api/v1/accounts/**` | account-service (8081) |
| `/api/v1/transections/**` | transection-service (8082) |
| `/api/v1/payments/**` | payment-service (8083) |

Check loaded routes at `http://localhost:8080/actuator/gateway/routes`.

## Health Checks

```
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
```

## Troubleshooting

| Problem | Cause and fix |
|---|---|
| `NoClassDefFoundError: com.fasterxml.jackson.databind...` | Spring Boot 4 uses Jackson 3. Use `JacksonJsonSerializer` / `JacksonJsonDeserializer` instead of `JsonSerializer` / `JsonDeserializer`. |
| `No type information in headers and no default type provided` | Wrap the consumer with `ErrorHandlingDeserializer`, set `spring.json.use.type.headers: false` and a `spring.json.value.default.type`. |
| Gateway returns `404` | Gateway 5.x reads routes from `spring.cloud.gateway.server.webflux.routes`. Check the prefix and the port of each route. |
| `NotBlank ... validating type BigDecimal` | `@NotBlank` is for `String` only. Use `@NotNull` for numbers and enums. |
| Transaction stuck in `PENDING_VERIFICATION` | Kafka topic names on producer and consumer sides must match exactly. |
| `Column '...' cannot be null` | Make the field nullable in the entity or provide a value. |
| Port `3306` already in use | Do not start the Docker MySQL when using a local MySQL. |
| `Unknown database` / access denied | Check the JDBC URL, username and password in `application.yml`. |

## Project Structure

```
banking-system/
├── account-service/
├── api-gateway/
├── fraud-detection-service/
├── notification-service/
├── payment-service/
├── transection-service/
└── docker-compose.yml
```

## Roadmap

- Service discovery (Eureka) and centralized config
- JWT authentication at the gateway
- Dockerfiles for each service so everything runs with one `docker compose up`
- Transactional rollback when credit fails after debit
- Integration tests with Testcontainers

## Author

[yahiya06](https://github.com/yahiya06)
Yahiya Khan | [LinkedIn](https://www.linkedin.com/in/yahiya-khan-b80391218)
