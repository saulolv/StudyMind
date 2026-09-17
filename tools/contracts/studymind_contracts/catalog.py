"""Loading and navigating the event contracts as a set, not as isolated files."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path

from referencing import Registry, Resource
from referencing.exceptions import Unresolvable

ENVELOPE_FILE = "event-envelope.v1.schema.json"
DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema"
SNAKE_CASE = re.compile(r"^[a-z][a-z0-9]*(_[a-z0-9]+)*$")


class ContractError(Exception):
    """A contract is unusable. Carries the message a reader can act on, nothing else."""


@dataclass(frozen=True)
class EventSchema:
    """One ``*.schema.json`` file, parsed, with the parts every check needs to reach."""

    path: Path
    document: dict

    @property
    def file_name(self) -> str:
        return self.path.name

    @property
    def schema_id(self) -> str | None:
        return self.document.get("$id")

    @property
    def is_envelope(self) -> bool:
        return self.file_name == ENVELOPE_FILE

    @property
    def branches(self) -> list[dict]:
        """The ``allOf`` members, or the document itself when it does not use ``allOf``."""
        return list(self.document.get("allOf", [])) or [self.document]

    def const_of(self, field: str) -> str | None:
        """The ``const`` pinned on an envelope field, e.g. ``event_type``."""
        for branch in self.branches:
            value = branch.get("properties", {}).get(field, {})
            if isinstance(value, dict) and "const" in value:
                return value["const"]
        return None

    @property
    def event_type(self) -> str | None:
        return self.const_of("event_type")

    @property
    def source_service(self) -> str | None:
        return self.const_of("source_service")

    @property
    def payload(self) -> dict | None:
        for branch in self.branches:
            payload = branch.get("properties", {}).get("payload")
            if isinstance(payload, dict) and "properties" in payload:
                return payload
        return None


class Catalog:
    """Every schema in one directory, plus the registry their ``$ref``s resolve through."""

    def __init__(self, directory: Path) -> None:
        if not directory.is_dir():
            raise ContractError(f"Not a contracts directory: {directory}")
        self.directory = directory
        self.schemas = [
            EventSchema(path, _read_json(path)) for path in sorted(directory.glob("*.schema.json"))
        ]
        if not self.schemas:
            raise ContractError(f"No *.schema.json files under {directory}")
        self.registry = self._build_registry()

    @property
    def events(self) -> list[EventSchema]:
        """The concrete events: everything except the shared envelope."""
        return [schema for schema in self.schemas if not schema.is_envelope]

    def by_event_type(self) -> dict[str, EventSchema]:
        return {
            schema.event_type: schema for schema in self.events if schema.event_type is not None
        }

    def emitted_by(self, service: str) -> list[EventSchema]:
        return [schema for schema in self.events if schema.source_service == service]

    def resolve(self, base_uri: str, reference: str) -> dict:
        """Resolve one ``$ref``, raising :class:`ContractError` when it points nowhere."""
        try:
            return self.registry.resolver(base_uri).lookup(reference).contents
        except Unresolvable as error:
            raise ContractError(f"{reference} does not resolve: {error}") from error

    def _build_registry(self) -> Registry:
        """A registry of the local files only, so a ``$ref`` that needs the network fails."""
        resources = []
        for schema in self.schemas:
            if schema.schema_id is None:
                raise ContractError(f"{schema.file_name} has no $id")
            resources.append((schema.schema_id, Resource.from_contents(schema.document)))
        return Registry().with_resources(resources)


def _read_json(path: Path) -> dict:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError(f"{path.name} is not valid JSON: {error}") from error
    if not isinstance(document, dict):
        raise ContractError(f"{path.name} is not a JSON object")
    return document


def find_refs(node: object, pointer: str = "") -> list[tuple[str, str]]:
    """Every ``$ref`` in a schema, paired with the JSON pointer it was found at."""
    found: list[tuple[str, str]] = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "$ref" and isinstance(value, str):
                found.append((pointer or "/", value))
            else:
                found.extend(find_refs(value, f"{pointer}/{key}"))
    elif isinstance(node, list):
        for index, value in enumerate(node):
            found.extend(find_refs(value, f"{pointer}/{index}"))
    return found
