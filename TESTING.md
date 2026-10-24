# Testing

## Build

Use JDK 21 and Maven 3.9 or later. From this directory:

```sh
mvn -B -f cinema-tickets-java/pom.xml clean verify
```

The suite contains 50 JUnit test invocations, including parameterized cases and two bounded combination checks. It uses isolated provider doubles; no live payments or seat reservations occur.

## Development methodology

The initial implementation preceded its tests. Tests were subsequently expanded and used to protect refactoring. The accurate description is test-after implementation followed by regression-tested refactoring. Behaviour examples are documented below; collaborative BDD discovery with stakeholders was not performed. Passing tests alone do not establish TDD or BDD process history.

## Behaviour examples and executable evidence

For each example below, **Given** establishes the account and request, **When** is a call to `TicketService.purchaseTickets`, and **Then** describes the public outcome. Accounts are positive unless a row says otherwise.

| Given | Then | Existing executable evidence |
| --- | --- | --- |
| 2 adults, 3 children, 2 infants | One payment of £95 and one reservation for 5 seats | `chargesAndReservesExactlyOnce`: all types |
| 1 adult and 1 infant | £25 payment, 1 seat | `chargesAndReservesExactlyOnce`: infant needs no payment or seat |
| 25 adult tickets | £625 payment, 25 seats | `chargesAndReservesExactlyOnce`: at the limit |
| 26 adult tickets | Invalid-purchase exception and no provider calls | `rejectsInvalidPurchaseWithoutCallingProviders`: one over the limit |
| 13 adults and 13 infants | Rejection because all 26 tickets count towards the cap | `rejectsInvalidPurchaseWithoutCallingProviders`: infants count towards limit |
| Children and infants without an adult | Rejection and no provider calls | `rejectsInvalidPurchaseWithoutCallingProviders`: children and infants without adult |
| A valid adult entry followed by a null entry | Reject the whole purchase without external effects | `rejectsInvalidPurchaseWithoutCallingProviders`: later null request |
| Infant and child entries precede the adult entry | Same totals as the equivalent purchase in another order | `chargesAndReservesExactlyOnce`: adult can appear last; split/reordered matrix |
| Multiple entries of the same ticket type | Aggregate their quantities for payment, seats and the cap | Repeated-type examples and split/reordered matrix |
| An adult purchase is followed by a separate child-only purchase | Reject the second purchase; the earlier adult does not qualify it | `adultFromEarlierPurchaseDoesNotSatisfyLaterPurchase` |
| Null, zero or negative account ID | Rejection and no provider calls | `rejectsInvalidAccountWithoutCallingProviders` |

See [named behaviour tests](cinema-tickets-java/src/test/java/uk/gov/dwp/uc/pairtest/TicketServiceImplTest.java) and [bounded requirement checks](cinema-tickets-java/src/test/java/uk/gov/dwp/uc/pairtest/TicketServiceRequirementsTest.java).

Two examples represent interpretations rather than explicit numeric rules: zero-quantity entries are rejected, and one adult with two infants is accepted. Keep these visible when discussing requirements instead of presenting them as agreed acceptance criteria.

## Test scope

- Behaviour tests call the public service contract and assert exception types, payment amounts, seat counts and forbidden provider interactions.
- The aggregate matrix covers 19,683 count combinations from 0 through 26, with absent types omitted.
- A further 2,925 valid combinations are split into individual ticket requests with adults last.
- Extreme integers, nulls, empty requests, repeated calls and construction are covered by named tests outside that matrix.
- Provider internals are excluded by the exercise's assumptions. The no-argument-constructor smoke test only calls the supplied placeholders.
- Coverage demonstrates exercised lines and branches. It cannot prove the requirements interpretation, TDD history, production resilience or every possible input.
