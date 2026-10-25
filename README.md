# Cinema tickets

The Java implementation is in [`cinema-tickets-java`](cinema-tickets-java). It uses the supplied DWP starter contracts to validate purchases, request payment, and reserve seats.

## Build and test

Requirements: JDK 21 and Maven 3.9 or later. Ensure `mvn -version` reports the intended JDK.

Compilation uses `--release 21` to constrain the language level, bytecode, and Java SE APIs even when building with a newer JDK.

From the repository root:

```sh
mvn -f cinema-tickets-java/pom.xml clean verify
```

The test suite uses JUnit Jupiter and Mockito. Tests verify payment amounts and seat quantities, rejection without provider calls, purchase limits, repeated ticket types, large inputs, dependency construction, and independence between purchases. Behaviour tests mock the payment and reservation interfaces. A separate smoke test exercises the no-argument constructor with the supplied placeholder providers; no real payment or booking is made.

Mockito uses its proxy mock maker and reflection member accessor because the test doubles only implement interfaces and need no access to encapsulated JDK internals. These settings avoid dynamic Java-agent attachment. Mocking concrete classes or accessing strongly encapsulated modules would require revisiting this configuration.

Two additional tests use recording providers to check all 19,683 adult/child/infant count combinations from 0 through 26, then all 2,925 valid combinations again with individual tickets and adults last. A zero count in this matrix means the type is absent; explicit zero-quantity entries remain invalid. These bounded checks supplement the named examples and malformed-input tests. They do not prove behaviour for every possible input or settle ambiguities in the specification.


## Business behaviour

| Type | Price | Seats per ticket |
| --- | --- | --- |
| Adult | £25 | 1 |
| Child | £15 | 1 |
| Infant | £0 | 0 |

A purchase contains at most 25 tickets, including infants, and must include an adult. An account ID greater than zero is treated as valid and sufficiently funded.

The entire purchase is validated before either external service is called. A valid purchase makes one payment request followed by one seat reservation request. The supplied assumptions guarantee both providers succeed; retries, refunds, and distributed transaction handling are outside this exercise.

## Input interpretations

- Every request must specify a non-null type and a strictly positive quantity. To omit a ticket type, leave out its request instead of supplying zero.
- Null or empty purchase arrays and null entries are rejected.
- Repeated requests for the same ticket type are allowed; their quantities contribute to the same purchase limit and totals.
- Child and infant tickets require an adult in the same purchase, regardless of request order.
- The specification says infants sit on an adult's lap but supplies no numerical infant-to-adult ratio. This implementation enforces adult presence only. A stricter ratio would require clarification of the business rule.
- Invalid purchases throw `InvalidPurchaseException` with an explanatory message and do not invoke either provider.

## Design

`TicketServiceImpl` keeps purchase totals local to each call and accepts payment and reservation interfaces through its constructor for testing or integration. Its no-argument constructor remains available and uses the supplied provider implementations. The supplied provider bodies are placeholders, so this project demonstrates orchestration rather than a live booking system.

The public purchase method delegates to private validation/calculation helpers, then sends an immutable pair of derived totals to the providers.

`TicketTypeRequest` is final with private final fields and read-only accessors. Purchase validation belongs to the service so invalid requests are rejected consistently at the purchase boundary.

The ticket limit is checked before addition, preventing large input quantities from overflowing the running count. Once validated, the maximum payment is £625, so integer arithmetic is sufficient for the stated whole-pound prices and purchase limit.

The `TicketService` interface and all `thirdparty.*` source files are unchanged. This package contains only the Java project.
