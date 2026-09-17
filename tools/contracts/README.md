# studymind-contracts

Makes `contracts/events/v1/` a build step instead of a folder of documentation.

Two commands, both run in CI:

```bash
studymind-contracts validate         # the schemas are usable and the README indexes them
studymind-contracts codegen          # write the Java wire types the publishers build from
studymind-contracts codegen --check  # fail instead of writing, for CI
```

Either can be run as `python -m studymind_contracts …`. Both find the repository root by walking up
until they see `contracts/events/v1`, so they work from anywhere in the tree.

## validate

Every check exists because breaking it breaks something concrete downstream, and the docstring on
each rule in [`validate.py`](studymind_contracts/validate.py) says what:

- the file is `<event-name>.v<n>.schema.json` and its `$id` ends with that name;
- it declares Draft 2020-12 and is itself valid against the metaschema;
- it `allOf`s the envelope, and **every `$ref` resolves to a file that exists** — resolution goes
  through a registry built from the local files only, so a `$ref` that would need the network fails;
- `event_type` is pinned to a `const`, and no two schemas claim the same one;
- the payload is `additionalProperties: false` with snake_case field names;
- **every payload has a required `user_id`** — the tenant isolation key;
- the README under `## Initial Events` lists exactly the events that have files.

## codegen

One Java record per event a service publishes, written into `com.<service>.events.wire` and checked
in. Which services publish, and where their code goes, is the `PUBLISHERS` table in
[`cli.py`](studymind_contracts/cli.py).

Each generated record exposes a builder with **one setter named after each contract field**. That
naming is the whole mechanism: rename `storage_path` in the schema, regenerate, and `.storagePath(…)`
no longer exists, so the publisher stops compiling. A positional constructor would not catch it.
The record also null-checks required fields, validates enumerated ones against the schema's `enum`,
and omits optional fields from the wire map rather than sending `null`.

See [ADR-0004](../../docs/architecture/adr/0004-generate-wire-types-from-the-event-contracts.md)
for why the envelope and the domain records are *not* generated.

## Working on it

```bash
cd tools/contracts
python -m venv .venv
.venv/bin/pip install -e ".[dev]"     # Windows: .venv\Scripts\pip

pytest
ruff check . && ruff format --check .
```

The tests are the interesting part: each one breaks a real schema in a temporary copy of
`contracts/events/v1/` and asserts the build notices. A validator nobody has watched fail is a
validator that passes everything.

Adding a rule means adding both the check and the test that makes it fire.

## Layout

| File | |
| --- | --- |
| `catalog.py` | loading the schemas as a set, and the registry their `$ref`s resolve through |
| `validate.py` | the rules, one function each |
| `javagen.py` | schema to Java record |
| `cli.py` | the two commands, and the `PUBLISHERS` table |

Python types for the workers are the obvious next target for `javagen`'s sibling, and the reason the
generator takes a target package rather than assuming one.
