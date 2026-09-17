"""What the generator has to guarantee, stated as tests.

The one that matters most is :func:`test_renaming_a_field_renames_the_setter`: it is the mechanism
behind "renaming a field in a schema causes a compile failure". Everything else here supports it.
"""

from __future__ import annotations

from pathlib import Path

import pytest
from conftest import edit, payload_of

from studymind_contracts import javagen
from studymind_contracts.catalog import Catalog, ContractError

PACKAGE = "com.contentservice"


def sources(directory: Path, service: str = "content-service") -> dict[str, str]:
    files = javagen.generate(Catalog(directory), service, PACKAGE)
    return {Path(file.relative_path).name: file.content for file in files}


def test_generates_one_type_per_event_the_service_publishes(contracts: Path) -> None:
    generated = sources(contracts)

    assert set(generated) == {"ContentSubmittedPayload.java", "ContentDeletedPayload.java"}


def test_puts_them_in_the_services_wire_package(contracts: Path) -> None:
    files = javagen.generate(Catalog(contracts), "content-service", PACKAGE)

    assert all(file.relative_path.startswith("com/contentservice/events/wire/") for file in files)
    assert all("package com.contentservice.events.wire;" in file.content for file in files)


def test_refuses_a_service_that_publishes_nothing(contracts: Path) -> None:
    with pytest.raises(ContractError, match="No schema pins source_service"):
        javagen.generate(Catalog(contracts), "processing-worker", PACKAGE)


def test_carries_the_event_type_from_the_schema(contracts: Path) -> None:
    submitted = sources(contracts)["ContentSubmittedPayload.java"]

    assert 'public static final String EVENT_TYPE = "ContentSubmitted";' in submitted


def test_one_named_setter_per_contract_field(contracts: Path) -> None:
    submitted = sources(contracts)["ContentSubmittedPayload.java"]

    for setter in (
        "public Builder contentId(UUID contentId)",
        "public Builder userId(UUID userId)",
        "public Builder type(String type)",
        "public Builder storagePath(String storagePath)",
        "public Builder fileName(String fileName)",
        "public Builder sourceUrl(String sourceUrl)",
    ):
        assert setter in submitted


def test_renaming_a_field_renames_the_setter(scratch_contracts: Path) -> None:
    """The whole reason these types are generated: the old call site stops compiling."""

    def rename(document: dict) -> None:
        properties = payload_of(document)["properties"]
        properties["storage_location"] = properties.pop("storage_path")

    edit(scratch_contracts / "content-submitted.v1.schema.json", rename)
    submitted = sources(scratch_contracts)["ContentSubmittedPayload.java"]

    assert "public Builder storageLocation(String storageLocation)" in submitted
    assert "storagePath" not in submitted


def test_dropping_a_field_drops_the_setter(scratch_contracts: Path) -> None:
    def drop(document: dict) -> None:
        payload_of(document)["properties"].pop("file_name")

    edit(scratch_contracts / "content-submitted.v1.schema.json", drop)
    submitted = sources(scratch_contracts)["ContentSubmittedPayload.java"]

    assert "fileName" not in submitted


def test_required_fields_are_rejected_when_null(contracts: Path) -> None:
    deleted = sources(contracts)["ContentDeletedPayload.java"]

    assert 'Objects.requireNonNull(contentId, "content_id is required by the contract");' in deleted
    assert 'Objects.requireNonNull(userId, "user_id is required by the contract");' in deleted


def test_optional_fields_are_omitted_rather_than_sent_as_null(contracts: Path) -> None:
    """``additionalProperties: false`` plus ``type: string`` makes an explicit null invalid."""
    submitted = sources(contracts)["ContentSubmittedPayload.java"]

    assert 'if (sourceUrl != null) {\n            wire.put("source_url", sourceUrl);' in submitted
    assert 'wire.put("content_id", contentId.toString());' in submitted


def test_an_enum_becomes_a_constructor_guard(contracts: Path) -> None:
    submitted = sources(contracts)["ContentSubmittedPayload.java"]

    assert 'if (!List.of("PDF", "VIDEO").contains(type)) {' in submitted


def test_uuid_fields_become_uuid_and_reach_the_wire_as_strings(contracts: Path) -> None:
    deleted = sources(contracts)["ContentDeletedPayload.java"]

    assert "        UUID contentId," in deleted
    assert 'wire.put("content_id", contentId.toString());' in deleted


def test_a_field_type_with_no_java_mapping_is_an_error_not_a_guess(scratch_contracts: Path) -> None:
    def add_an_object_field(document: dict) -> None:
        payload_of(document)["properties"]["metadata"] = {"type": "object"}

    edit(scratch_contracts / "content-deleted.v1.schema.json", add_an_object_field)

    with pytest.raises(ContractError, match="no Java mapping yet"):
        sources(scratch_contracts)


def test_generation_is_deterministic(contracts: Path) -> None:
    assert sources(contracts) == sources(contracts)


def test_writing_twice_reports_no_second_change(contracts: Path, tmp_path: Path) -> None:
    files = javagen.generate(Catalog(contracts), "content-service", PACKAGE)

    assert len(javagen.write(files, tmp_path)) == 2
    assert javagen.write(files, tmp_path) == []


def test_the_checked_in_java_matches_the_schemas(repo_root: Path, contracts: Path) -> None:
    """If this fails, someone changed a schema and did not regenerate. CI runs the same check."""
    source_root = repo_root / "services" / "content-service" / "src" / "main" / "java"

    for file in javagen.generate(Catalog(contracts), "content-service", PACKAGE):
        checked_in = source_root / file.relative_path
        assert checked_in.is_file(), f"{file.relative_path} has never been generated"
        assert checked_in.read_text(encoding="utf-8") == file.content
