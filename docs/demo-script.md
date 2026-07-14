# Demo Script

Target: **under 60 seconds.**

This script assumes `./gradlew yawnDoctorReport` has already been run and the
dashboard build is up to date.

---

**1. Open the dashboard.**

> "Yawn Doctor is a Detekt plugin for high-impact ORM and transaction risks in
> Kotlin services."

**2. Select the `UserRepository.kt` finding (YAWN001).**

> "This `list()` call is nested inside `forEach`. Each call creates a separate
> database round trip — the N+1 pattern. The finding shows the exact source
> line, the explanation, and remediation steps."

**3. Click the `BrandRepository.kt` finding (YAWN002).**

> "This code hydrates full entities from the database only to count them with
> `.size`. A database-level count projection would avoid unnecessary memory and
> bandwidth."

**4. Click the `FulfillmentService.kt` finding (YAWN003).**

> "This `shippingClient.reserve()` call happens while a database transaction is
> open. If the transaction later rolls back, the shipping reservation is already
> made — an ambiguous partial failure. The dashboard suggests moving I/O outside
> the transaction or using a transactional outbox."

**5. Show the command-line output.**

> "You can also run the same analysis from the terminal with colour-coded
> output via `./gradlew :demo-codebase:yawnDoctorDemo`."

---

## Verification checklist

Before the demo:

- [ ] `./gradlew :doctor-rules:test` passes (29 tests).
- [ ] `./gradlew yawnDoctorReport` completes without error.
- [ ] Dashboard loads at `http://localhost:3000`.
- [ ] All 6 findings are present.
- [ ] All 3 rule IDs (YAWN001–003) are visible.
- [ ] Source highlighting renders correctly.
