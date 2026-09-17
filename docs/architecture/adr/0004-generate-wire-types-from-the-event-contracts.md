# ADR-0004: event wire types are generated from the JSON Schemas

- **Status:** Accepted
- **Date:** 2026-09-17

## Context

`contracts/events/v1/` is described as the source of truth, but the code did not treat it as one.
`RabbitEventPublisher` built each payload as a map of string literals:

```java
body.put("storage_path", event.storagePath());
```

The contract test caught a payload that violated the schema, which is real protection. It did not
catch the failure that actually matters: a field renamed *in the schema* and not in Java. The test
validates the published envelope against the schema, so if a schema renames `storage_path` to
`storage_location`, the publisher keeps emitting the old key and the test fails — good — but only
after someone has run it, and the failure points at the envelope rather than at the line to change.
Worse, the reverse — a rename in Java that the schema does not know about — was silent until a
Python worker received a key it did not expect.

The same hand-written duplication was about to be repeated in Python, in three workers.

## Decision

Generate the Java payload types from the schemas, and check the output in.

`tools/contracts` is a Python package with two commands:

- `studymind-contracts validate` — the schemas are valid Draft 2020-12, their `$ref`s resolve, they
  follow the project's conventions, and the README index matches the files;
- `studymind-contracts codegen` — writes one Java record per event a service publishes into
  `com.<service>.events.wire`.

Each generated record exposes a **builder with one setter named after each contract field**:

```java
ContentSubmittedPayload.builder()
        .contentId(event.contentId())
        .storagePath(event.storagePath())
        .build()
        .toWireMap();
```

The naming is the mechanism. A positional record constructor would still compile after a rename —
six strings are six strings. A named setter does not: rename `storage_path` in the schema,
regenerate, and `.storagePath(…)` no longer exists.

The generated type also enforces what the schema says: required fields are null-checked in the
compact constructor, enumerated fields are checked against the schema's `enum`, and optional fields
are omitted from the wire map rather than sent as `null` — which `additionalProperties: false` plus
`type: string` would reject.

CI runs `codegen --check` and fails if the checked-in files differ from what the schemas produce, so
"changed a schema, forgot to regenerate" is caught too.

### What is *not* generated

**The envelope.** Its seven fields are derived inside the adapter — `event_id` is a fresh UUID,
`occurred_at` comes from an injected `Clock`, `correlation_id` from the MDC — and none of them vary
per event. Generating a type whose every field the adapter overwrites buys nothing.

**The domain event records.** `ContentSubmitted` and `ContentDeleted` stay hand-written, with their
`ContentType` enum, their `pdf(…)` and `video(…)` factories, and their place in the sealed
`EventPayload` hierarchy that makes `RabbitEventPublisher`'s switch exhaustive. The generated types
sit at the adapter edge, where the domain becomes bytes. Replacing the domain records with
generated ones would push `String type` and nullable-everything into the middle of the service.

**The OpenAPI models.** Considered and rejected for now. The response records in
`com.contentservice.api.dto` are not plain field bags — each has an `of(Content)` factory that maps
the entity, and a generator either loses those or forces a second mapping layer of exactly the same
size. The drift risk they carry is smaller too: the API is checked by `ContentControllerTest`
asserting real JSON, by the Spectral ruleset in CI, and by a human reading `/swagger-ui`. This is
worth revisiting if a second consumer of these models appears — a generated TypeScript client for
the Angular SPA would change the calculation, because then the same shape would be hand-written
twice in two languages.

## Consequences

**What got better.** A schema rename is a compile error, demonstrated: renaming `storage_path`
breaks `RabbitEventPublisher.java`. The wire contract has exactly one definition. The validator and
generator are one Python package that the workers will use for their own types, so the second
language does not start from scratch.

**What got harder.**

- There is now a build step outside Maven. `codegen` runs by hand and is verified in CI, rather than
  being bound to a Maven phase. That was deliberate: binding it would make the Java build depend on
  a Python toolchain, and each service is an independent Maven project with no shared parent to hang
  that on.
- Generated code is in the repository and reviewable, which is the point, but it does mean a schema
  change produces a two-part diff.
- The generated package is excluded from the JaCoCo coverage gate. It is verified by
  `ContentEventContractTest`, which validates real published envelopes against the real schema
  files — a better test than unit tests of generated accessors would be.

## What would make this wrong

- If the type mapping needs to grow far enough — nested objects, polymorphic payloads, `oneOf` —
  that the generator becomes a JSON Schema compiler. At that point an off-the-shelf generator is
  the right answer, and the escape hatch is that the output shape is already pinned by tests.
- If a generated TypeScript or Python client makes a single mature generator worth adopting for
  events and OpenAPI together.
