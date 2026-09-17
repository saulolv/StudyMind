"""Generates the Java wire types a publishing service builds its envelopes from.

The point is not to save typing. It is that ``storage_path`` exists in exactly one place. A
generated payload type is built through a builder with one named setter per contract field, so
renaming or removing a field in a schema stops the publisher from compiling instead of quietly
changing what the Python workers receive.

The envelope itself is still assembled by hand in the adapter: its fields are derived rather than
passed in, and nothing about it varies per event.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from studymind_contracts.catalog import Catalog, ContractError, EventSchema

WIRE_SUBPACKAGE = "events.wire"
HEADER = (
    "// Generated from contracts/events/v1/{source} by tools/contracts. Do not edit.\n"
    "// Regenerate with: python -m studymind_contracts codegen\n"
)


@dataclass(frozen=True)
class Field:
    """One payload property, as both the wire key and the Java name derived from it."""

    wire_name: str
    java_name: str
    java_type: str
    required: bool
    allowed_values: tuple[str, ...] = ()

    @property
    def element_type(self) -> str:
        return self.java_type[5:-1] if self.java_type.startswith("List<") else self.java_type

    @property
    def is_list(self) -> bool:
        return self.java_type.startswith("List<")


@dataclass(frozen=True)
class GeneratedFile:
    relative_path: str
    content: str


def generate(catalog: Catalog, service: str, package: str) -> list[GeneratedFile]:
    """One file per event ``service`` emits, in ``<package>.events.wire``."""
    schemas = catalog.emitted_by(service)
    if not schemas:
        raise ContractError(f"No schema pins source_service to {service!r}")
    target_package = f"{package}.{WIRE_SUBPACKAGE}"
    directory = target_package.replace(".", "/")
    return [
        GeneratedFile(f"{directory}/{class_name(schema)}.java", _render(schema, target_package))
        for schema in schemas
    ]


def write(files: list[GeneratedFile], source_root: Path) -> list[Path]:
    """Write the generated files under ``source_root``, returning only the ones that changed."""
    changed = []
    for file in files:
        path = source_root / file.relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        existing = path.read_text(encoding="utf-8") if path.is_file() else None
        if existing != file.content:
            path.write_text(file.content, encoding="utf-8")
            changed.append(path)
    return changed


def fields_of(schema: EventSchema) -> list[Field]:
    payload = schema.payload
    if payload is None:
        raise ContractError(f"{schema.file_name} declares no payload properties")
    required = set(payload.get("required", []))
    return [
        Field(
            wire_name=name,
            java_name=camel_case(name),
            java_type=_java_type(schema.file_name, name, definition),
            required=name in required,
            allowed_values=tuple(definition.get("enum", ())),
        )
        for name, definition in payload["properties"].items()
    ]


def class_name(schema: EventSchema) -> str:
    if not schema.event_type:
        raise ContractError(f"{schema.file_name} does not pin event_type")
    return f"{schema.event_type}Payload"


def camel_case(wire_name: str) -> str:
    head, *rest = wire_name.split("_")
    return head + "".join(word[:1].upper() + word[1:] for word in rest)


def _render(schema: EventSchema, package: str) -> str:
    fields = fields_of(schema)
    name = class_name(schema)
    components = ",\n".join(f"        {f.java_type} {f.java_name}" for f in fields)
    lines = [
        HEADER.format(source=schema.file_name).rstrip("\n"),
        f"package {package};",
        "",
        *(f"import {module};" for module in _imports(fields)),
        "",
        "/**",
        f" * Payload of the {schema.event_type} event, generated from",
        f" * {{@code contracts/events/v1/{schema.file_name}}}.",
        " *",
        " * <p>Built through {@link #builder()} rather than positionally: one named setter per",
        " * contract field is what makes a renamed or dropped field a compile error.",
        " */",
        f"public record {name}(",
        f"{components}) {{",
        "",
        f'    public static final String EVENT_TYPE = "{schema.event_type}";',
        "",
        *_compact_constructor(name, fields),
        *_to_wire_map(fields),
        *_builder(name, fields),
        "}",
    ]
    return "\n".join(lines) + "\n"


def _compact_constructor(name: str, fields: list[Field]) -> list[str]:
    guards: list[str] = []
    for field in (f for f in fields if f.required):
        guards.append(
            f"        Objects.requireNonNull({field.java_name}, "
            f'"{field.wire_name} is required by the contract");'
        )
    for field in (f for f in fields if f.allowed_values):
        allowed = ", ".join(f'"{value}"' for value in field.allowed_values)
        guard = f"!List.of({allowed}).contains({field.java_name})"
        if not field.required:
            guard = f"{field.java_name} != null && {guard}"
        guards += [
            f"        if ({guard}) {{",
            "            throw new IllegalArgumentException(",
            f'                    "{field.wire_name} is not a value the contract allows: " '
            f"+ {field.java_name});",
            "        }",
        ]
    if not guards:
        return []
    return [f"    public {name} {{", *guards, "    }", ""]


def _to_wire_map(fields: list[Field]) -> list[str]:
    lines = [
        "    /** The snake_case body the contract describes: optional fields absent, not null. */",
        "    public Map<String, Object> toWireMap() {",
        "        Map<String, Object> wire = new LinkedHashMap<>();",
    ]
    for field in fields:
        put = f'        wire.put("{field.wire_name}", {_wire_value(field)});'
        if field.required:
            lines.append(put)
        else:
            lines += [
                f"        if ({field.java_name} != null) {{",
                "    " + put,
                "        }",
            ]
    return [*lines, "        return wire;", "    }", ""]


def _wire_value(field: Field) -> str:
    if field.is_list and field.element_type in ("UUID", "Instant"):
        return f"{field.java_name}.stream().map(Object::toString).toList()"
    if field.element_type in ("UUID", "Instant"):
        return f"{field.java_name}.toString()"
    return field.java_name


def _builder(name: str, fields: list[Field]) -> list[str]:
    lines = [
        "    public static Builder builder() {",
        "        return new Builder();",
        "    }",
        "",
        "    /** One setter per contract field, named after it. That naming is the whole point. */",
        "    public static final class Builder {",
        "",
        *(f"        private {f.java_type} {f.java_name};" for f in fields),
        "",
    ]
    for field in fields:
        lines += [
            f"        public Builder {field.java_name}({field.java_type} {field.java_name}) {{",
            f"            this.{field.java_name} = {field.java_name};",
            "            return this;",
            "        }",
            "",
        ]
    arguments = ", ".join(field.java_name for field in fields)
    return [
        *lines,
        f"        public {name} build() {{",
        f"            return new {name}({arguments});",
        "        }",
        "    }",
    ]


def _imports(fields: list[Field]) -> list[str]:
    modules = {"java.util.LinkedHashMap", "java.util.Map"}
    if any(field.required for field in fields):
        modules.add("java.util.Objects")
    if any(field.allowed_values for field in fields) or any(field.is_list for field in fields):
        modules.add("java.util.List")
    for field in fields:
        if field.element_type == "UUID":
            modules.add("java.util.UUID")
        if field.element_type == "Instant":
            modules.add("java.time.Instant")
    return sorted(modules)


def _java_type(file_name: str, field: str, definition: dict) -> str:
    kind = definition.get("type")
    if kind == "array":
        return f"List<{_java_type(file_name, field, definition.get('items', {}))}>"
    scalar = {
        ("string", "uuid"): "UUID",
        ("string", "date-time"): "Instant",
        ("string", "uri"): "String",
        ("string", None): "String",
        ("integer", "int64"): "Long",
        ("integer", None): "Integer",
        ("number", None): "Double",
        ("boolean", None): "Boolean",
    }.get((kind, definition.get("format")))
    if scalar is None:
        raise ContractError(
            f"{file_name}: payload field {field!r} is {kind!r}/{definition.get('format')!r}, "
            f"which has no Java mapping yet"
        )
    return scalar
