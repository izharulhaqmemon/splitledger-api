# SplitLedger API

A backend REST API for splitting group expenses and settling debts, built with **Java 17 + Spring Boot 3**.
It goes beyond CRUD: exact-to-the-paisa split math, idempotent writes, an append-only ledger and a smart settle-up planner.

> DecodeLabs Full Stack Internship, Project 2: Backend API Development.

## Run it (2 minutes)

Requirements: **JDK 17+** and **Maven 3.9+** (or just open the folder in IntelliJ IDEA / VS Code and run `SplitLedgerApplication`).

```bash
mvn spring-boot:run
```

Then open these in your **browser**:

| URL | What you see |
|---|---|
| http://localhost:8080 | Redirects to **Swagger UI**: every endpoint, click **Try it out** to call it |
| http://localhost:8080/groups/1 | A demo group (seeded on first start) as JSON |
| http://localhost:8080/groups/1/balances | Who is owed / who owes |
| http://localhost:8080/groups/1/settlements/suggested | The smart settle-up plan |
| http://localhost:8080/groups/1/ledger | Full history |
| http://localhost:8080/h2-console | Database viewer (JDBC URL `jdbc:h2:file:./data/splitledger`, user `sa`, empty password) |

POST endpoints cannot be typed into the address bar. Use Swagger UI, the `requests.http` file, Postman or `curl`.

Run the tests: `mvn test`

## Endpoints

| Method | Endpoint | Purpose | Success |
|---|---|---|---|
| GET | `/groups` | List groups | 200 |
| POST | `/groups` | Create group (members optional) | 201 + `Location` |
| GET | `/groups/{id}` | Group and members | 200 |
| POST | `/groups/{id}/members` | Add member | 201 |
| POST | `/groups/{id}/expenses` | Add expense (4 split modes) | 201, or 200 on idempotent replay |
| GET | `/groups/{id}/expenses?page=&size=&paidBy=` | List, paginated, filterable | 200 |
| GET | `/groups/{id}/expenses/{expenseId}` | One expense with shares | 200 |
| GET | `/groups/{id}/balances` | Net balance per member | 200 |
| GET | `/groups/{id}/settlements/suggested` | Settle-up plan | 200 |
| POST | `/groups/{id}/settlements` | Record a payment | 201, or 200 on replay |
| GET | `/groups/{id}/ledger` | Append-only history | 200 |
| GET | `/health` | Liveness | 200 |

## Example

```bash
# add an expense: Ali paid 24,000, split by percentage
curl -X POST http://localhost:8080/groups/1/expenses \
  -H "Content-Type: application/json" -H "Idempotency-Key: hotel-001" \
  -d '{"description":"Hotel","amount":24000.00,"paidBy":1,"splitType":"PERCENTAGE",
       "splits":[{"memberId":1,"value":50},{"memberId":2,"value":30},{"memberId":3,"value":20}]}'
```

Split modes: `EQUAL`, `EXACT` (amount per member), `PERCENTAGE` (must total 100), `SHARES` (relative weights).

## Status codes

| Code | When |
|---|---|
| 201 | Resource created |
| 200 | Read OK, or an idempotent replay (`Idempotent-Replay: true` header) |
| 400 | **Syntactic** error: bad JSON, missing or invalid fields (`fieldErrors` lists each one) |
| 404 | Group or expense not found |
| 409 | Duplicate member name, or an `Idempotency-Key` reused with a different request |
| 422 | **Semantic** error: JSON is valid but the logic is not (percentages total 90, payer not in group, overpaying a debt) |
| 429 | Rate limit exceeded (`Retry-After` header) |
| 500 | Unexpected error (details are logged, never leaked) |

Every error uses one shape:

```json
{ "timestamp": "...", "status": 422, "error": "Unprocessable Entity",
  "message": "Percentages must add up to 100 but add up to 90", "path": "/groups/1/expenses" }
```

## Design decisions

- **Money is integer minor units.** `1500.50` is stored as `150050`. No floats, no rounding drift.
- **Splits always add up exactly.** Leftover paisa are handed out with the largest-remainder method, so 100.00 split three ways is 33.34 / 33.33 / 33.33, never 99.99.
- **Two validation gates.** Bean Validation checks the format (400). The service checks the logic (422). The client is never trusted.
- **Idempotent POST.** Send `Idempotency-Key` and a retried request returns the original result instead of creating a duplicate. Reusing a key with a different payload is a 409.
- **Append-only ledger.** There is no update or delete endpoint for expenses or settlements, so the history is a trustworthy audit trail.
- **Settle-up planner.** Net balances are matched biggest debtor to biggest creditor, which needs at most *n - 1* payments. (The true minimum is NP-hard, so this is the standard practical approach.) Verified by randomized tests over thousands of groups.
- **Rate limiting.** Fixed window per client IP, 120 requests per minute by default (`app.rate-limit.requests-per-minute`).
- **Layers.** `controller -> service -> repository`, pure logic (`SplitCalculator`, `DebtSimplifier`) kept free of Spring so it is easy to test.

## Project structure

```
src/main/java/com/izhar/splitledger
  web/         controllers, global error handler, rate limiter
  service/     business rules
  logic/       Money, SplitCalculator, DebtSimplifier (pure, no Spring)
  domain/      JPA entities
  repository/  Spring Data repositories
  dto/         request and response records
  config/      OpenAPI config, demo data seeder
```

## Switch to PostgreSQL

```bash
DB_URL=jdbc:postgresql://localhost:5432/splitledger DB_USER=postgres DB_PASSWORD=secret \
  mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Docker

```bash
docker build -t splitledger-api .
docker run -p 8080:8080 splitledger-api
```

When deploying to Render or Railway, set `SEED_DEMO=true` if you want the demo group on a fresh database.
The default H2 file database is wiped on redeploy there, so use the PostgreSQL profile for permanent data.
