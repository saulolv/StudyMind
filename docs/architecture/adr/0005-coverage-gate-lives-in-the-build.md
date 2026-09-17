# ADR-0005: the coverage gate is in the POM, not only in CI

- **Status:** Accepted
- **Date:** 2026-09-17

## Context

The project requires backend test coverage above 90%. That number can be enforced in two places: in
the CI workflow, by parsing a report and failing a step; or in the build, by binding
`jacoco:check` to `verify` in each service's POM.

Enforcing it only in CI produces a specific, repeated experience: the work is done, the branch is
pushed, and the build fails for a reason that was knowable twenty minutes earlier. It also splits
the definition of "passing" — `./mvnw verify` says yes, CI says no — and once those disagree, the
local command stops being trusted.

The scaffold services complicate it. `api-gateway-auth` and `chat-llm-service` have a
`@SpringBootApplication` class and nothing else. JaCoCo skips a module that produced no execution
data, so a 90% rule on a service with no tests passes vacuously.

## Decision

The gate lives in each service's POM: `jacoco-maven-plugin`, `prepare-agent` plus `report` plus
`check`, with `LINE` and `BRANCH` both at `${coverage.minimum}` = 0.90, bound to `verify`. CI runs
`./mvnw -B -ntp verify` and nothing more. The command a developer runs and the command CI runs are
the same command.

Two exclusions, and only two:

- **`**/*Application.class`** — a `main` method that calls `SpringApplication.run`. Covering it
  means a `@SpringBootTest`, which for these services means requiring Postgres and RabbitMQ to run
  the unit tests. That trade is not worth one line.
- **`**/events/wire/*.class`** — the types generated from the event contracts
  ([ADR-0004](0004-generate-wire-types-from-the-event-contracts.md)). They are verified by
  `ContentEventContractTest`, which validates real published envelopes against the real schema
  files. Unit-testing generated accessors would raise the number without raising the confidence.

Because JaCoCo skips silently when a module ran no tests, CI adds one step the POM cannot: a service
with production classes beyond its `Application` class and no test classes fails the job explicitly,
rather than passing a gate that never ran.

`@SpringBootTest` `contextLoads` tests were removed from both scaffolds as part of this. They
asserted nothing about this codebase, they required a live Postgres, and they were the reason the
two services could not be built on a clean machine.

## Consequences

**What got better.** `./mvnw verify` is the whole truth locally. Coverage is enforced per service,
so a new service starts under the same rule rather than inheriting an exemption. The scaffolds build
from a clean clone with no Docker running.

**What got harder.**

- Branch coverage at 90% is the binding constraint, not line coverage. Reaching it means testing the
  refusal paths — a malformed header, a file name that sanitises to nothing, a URL with no video id
  — which is more work than testing the happy path and is most of why the number is worth having.
- A developer cannot push a spike with the tests left for later. That is the intended cost.
- The two scaffold services currently have no tests at all. This is allowed, because they have no
  production code either; the CI step above is what stops that state from surviving the first real
  class.

## What would make this wrong

- If the number starts being met by tests written to move it rather than to describe behaviour.
  Coverage is a floor on which code is exercised, not evidence that it is exercised meaningfully;
  the review is still the thing that catches an assertion-free test.
- If a service acquires a large body of genuinely untestable adapter code, at which point the honest
  response is a narrower exclusion with a written reason, not a lower threshold.
