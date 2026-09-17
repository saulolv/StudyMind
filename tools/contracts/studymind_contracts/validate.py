"""The checks that turn contracts/events/v1 from documentation into a build step.

Every rule here exists because breaking it breaks something concrete downstream, and the comment
on each rule says what. A rule nobody can justify that way does not belong in this file.
"""

from __future__ import annotations

import re
from pathlib import Path

from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError

from studymind_contracts.catalog import (
    DRAFT_2020_12,
    ENVELOPE_FILE,
    SNAKE_CASE,
    Catalog,
    ContractError,
    find_refs,
)

ENVELOPE_REF = f"./{ENVELOPE_FILE}"
FILE_NAME = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*\.v(\d+)\.schema\.json$")
README_EVENTS_HEADING = "## Initial Events"
README_EVENT = re.compile(r"^- `([A-Za-z]+)`")


def validate(directory: Path) -> list[str]:
    """Return every problem found in ``directory``. An empty list means CI goes green."""
    catalog = Catalog(directory)
    problems: list[str] = []
    for check in (
        _file_names,
        _declared_dialect,
        _valid_against_metaschema,
        _resolvable_refs,
        _envelope_is_shared,
        _pinned_event_type,
        _closed_snake_case_payloads,
        _tenant_key_on_every_payload,
        _readme_matches_the_files,
    ):
        problems.extend(check(catalog))
    return problems


def _file_names(catalog: Catalog) -> list[str]:
    """`<event-name>.v<n>.schema.json` is how a breaking change gets a new file, not an edit."""
    return [
        f"{schema.file_name}: name does not match <event-name>.v<n>.schema.json"
        for schema in catalog.schemas
        if not FILE_NAME.match(schema.file_name)
    ]


def _declared_dialect(catalog: Catalog) -> list[str]:
    """Consumers pick a validator from ``$schema``; a missing or older dialect silently changes
    how ``$ref`` and ``additionalProperties`` behave."""
    problems = []
    for schema in catalog.schemas:
        if schema.document.get("$schema") != DRAFT_2020_12:
            problems.append(f"{schema.file_name}: $schema must be {DRAFT_2020_12}")
        if schema.schema_id is not None and not schema.schema_id.endswith(schema.file_name):
            problems.append(
                f"{schema.file_name}: $id ends with {schema.schema_id.rsplit('/', 1)[-1]!r}, "
                f"so a relative $ref to this file resolves to the wrong document"
            )
    return problems


def _valid_against_metaschema(catalog: Catalog) -> list[str]:
    """A schema that is not itself valid Draft 2020-12 validates nothing: most validators accept
    an unknown keyword silently, so a typo becomes a rule that never fires."""
    problems = []
    for schema in catalog.schemas:
        try:
            Draft202012Validator.check_schema(schema.document)
        except SchemaError as error:
            problems.append(f"{schema.file_name}: not valid Draft 2020-12 ({error.message})")
    return problems


def _resolvable_refs(catalog: Catalog) -> list[str]:
    """A ``$ref`` at a file that is not there fails at consume time, in a worker, in production."""
    problems = []
    for schema in catalog.schemas:
        for pointer, reference in find_refs(schema.document):
            try:
                catalog.resolve(schema.schema_id or "", reference)
            except ContractError as error:
                problems.append(f"{schema.file_name} at {pointer}: {error}")
    return problems


def _envelope_is_shared(catalog: Catalog) -> list[str]:
    """Every event carries the same envelope, which is what lets a consumer log, correlate and
    dead-letter a message it does not otherwise understand."""
    return [
        f"{schema.file_name}: does not allOf {ENVELOPE_REF}"
        for schema in catalog.events
        if ENVELOPE_REF not in [reference for _, reference in find_refs(schema.document)]
    ]


def _pinned_event_type(catalog: Catalog) -> list[str]:
    """``event_type`` is how a consumer bound to a coarse routing key decides what it is holding."""
    problems = [
        f"{schema.file_name}: does not pin event_type to a const"
        for schema in catalog.events
        if schema.event_type is None
    ]
    seen: dict[str, str] = {}
    for schema in catalog.events:
        if schema.event_type is None:
            continue
        if schema.event_type in seen:
            problems.append(
                f"{schema.file_name}: event_type {schema.event_type!r} is already pinned by "
                f"{seen[schema.event_type]}"
            )
        seen[schema.event_type] = schema.file_name
    return problems


def _closed_snake_case_payloads(catalog: Catalog) -> list[str]:
    """Python workers read these keys literally. ``additionalProperties: false`` is what makes a
    field that was renamed on one side a validation failure rather than a silently dropped value."""
    problems = []
    for schema in catalog.events:
        payload = schema.payload
        if payload is None:
            problems.append(f"{schema.file_name}: declares no payload properties")
            continue
        if payload.get("additionalProperties") is not False:
            problems.append(f"{schema.file_name}: payload must set additionalProperties: false")
        for name in payload["properties"]:
            if not _is_snake_case(name):
                problems.append(f"{schema.file_name}: payload field {name!r} is not snake_case")
    return problems


def _tenant_key_on_every_payload(catalog: Catalog) -> list[str]:
    """``user_id`` is the tenant isolation key: it has to reach the Qdrant filter and every query
    path. A payload that omits it produces a chunk nobody can safely scope."""
    problems = []
    for schema in catalog.events:
        payload = schema.payload
        if payload is None:
            continue
        if "user_id" not in payload["properties"]:
            problems.append(f"{schema.file_name}: payload has no user_id")
        elif "user_id" not in payload.get("required", []):
            problems.append(f"{schema.file_name}: user_id must be required, not optional")
    return problems


def _readme_matches_the_files(catalog: Catalog) -> list[str]:
    """The README is the only index of this folder. A file it does not list is a contract nobody
    knows exists; an entry with no file is a contract someone is about to hand-write from prose."""
    readme = catalog.directory / "README.md"
    if not readme.is_file():
        return [f"{catalog.directory.name}: no README.md"]
    listed = {
        match.group(1)
        for line in _section(readme.read_text(encoding="utf-8"), README_EVENTS_HEADING)
        if (match := README_EVENT.match(line))
    }
    if not listed:
        return [f"README.md has no events under {README_EVENTS_HEADING!r}"]
    defined = set(catalog.by_event_type())
    problems = [
        f"README.md lists {name!r}, which no schema pins as its event_type"
        for name in sorted(listed - defined)
    ]
    problems += [
        f"{catalog.by_event_type()[name].file_name} defines {name!r}, which README.md does not list"
        for name in sorted(defined - listed)
    ]
    return problems


def _section(markdown: str, heading: str) -> list[str]:
    """The lines under one Markdown heading, so a bullet elsewhere is not read as an event."""
    lines = markdown.splitlines()
    try:
        start = lines.index(heading) + 1
    except ValueError:
        return []
    body = lines[start:]
    for index, line in enumerate(body):
        if line.startswith("## "):
            return body[:index]
    return body


def _is_snake_case(name: str) -> bool:
    return bool(SNAKE_CASE.match(name))
