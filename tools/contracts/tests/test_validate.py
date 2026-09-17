"""Each test here breaks one thing and asserts the build notices.

A validator nobody has watched fail is a validator that passes everything, so every rule in
``validate`` has a test that makes it fire.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest
from conftest import edit, payload_of

from studymind_contracts.catalog import Catalog, ContractError
from studymind_contracts.validate import validate


def problems_after(directory: Path, file_name: str, mutate) -> list[str]:
    edit(directory / file_name, mutate)
    return validate(directory)


def test_the_real_contracts_are_valid(contracts: Path) -> None:
    assert validate(contracts) == []


def test_the_real_contracts_cover_every_event_the_flow_needs(contracts: Path) -> None:
    catalog = Catalog(contracts)

    assert set(catalog.by_event_type()) == {
        "ContentSubmitted",
        "ContentDeleted",
        "ChunksCreated",
        "ContentIndexed",
        "AnswerGenerated",
    }


def test_a_file_that_is_not_json_stops_the_build(scratch_contracts: Path) -> None:
    (scratch_contracts / "content-deleted.v1.schema.json").write_text("{not json", encoding="utf-8")

    with pytest.raises(ContractError, match="not valid JSON"):
        validate(scratch_contracts)


def test_a_malformed_schema_stops_the_build(scratch_contracts: Path) -> None:
    def break_a_keyword(document: dict) -> None:
        payload_of(document)["properties"]["content_id"]["type"] = 12

    problems = problems_after(scratch_contracts, "content-deleted.v1.schema.json", break_a_keyword)

    assert any("not valid Draft 2020-12" in problem for problem in problems)


def test_a_ref_pointing_at_a_missing_file_stops_the_build(scratch_contracts: Path) -> None:
    def point_nowhere(document: dict) -> None:
        document["allOf"][0]["$ref"] = "./event-envelope.v2.schema.json"

    problems = problems_after(scratch_contracts, "content-deleted.v1.schema.json", point_nowhere)

    assert any("does not resolve" in problem for problem in problems)


def test_deleting_the_envelope_breaks_every_event_that_refs_it(scratch_contracts: Path) -> None:
    (scratch_contracts / "event-envelope.v1.schema.json").unlink()

    problems = validate(scratch_contracts)

    assert len([p for p in problems if "does not resolve" in p]) == 5


def test_an_older_dialect_stops_the_build(scratch_contracts: Path) -> None:
    def downgrade(document: dict) -> None:
        document["$schema"] = "http://json-schema.org/draft-07/schema#"

    problems = problems_after(scratch_contracts, "content-indexed.v1.schema.json", downgrade)

    assert any("$schema must be" in problem for problem in problems)


def test_an_id_that_disagrees_with_its_file_name_stops_the_build(scratch_contracts: Path) -> None:
    def rename_the_id(document: dict) -> None:
        document["$id"] = "https://studymind/contracts/events/v1/something-else.v1.schema.json"

    problems = problems_after(scratch_contracts, "content-indexed.v1.schema.json", rename_the_id)

    assert any("relative $ref to this file resolves to the wrong document" in p for p in problems)


def test_a_file_name_outside_the_convention_stops_the_build(scratch_contracts: Path) -> None:
    (scratch_contracts / "content-deleted.v1.schema.json").rename(
        scratch_contracts / "ContentDeleted.schema.json"
    )

    problems = validate(scratch_contracts)

    assert any("<event-name>.v<n>.schema.json" in problem for problem in problems)


def test_an_event_that_does_not_carry_the_envelope_stops_the_build(scratch_contracts: Path) -> None:
    def drop_the_envelope(document: dict) -> None:
        document["allOf"] = [branch for branch in document["allOf"] if "$ref" not in branch]

    problems = problems_after(scratch_contracts, "chunks-created.v1.schema.json", drop_the_envelope)

    assert any("does not allOf" in problem for problem in problems)


def test_an_unpinned_event_type_stops_the_build(scratch_contracts: Path) -> None:
    def unpin(document: dict) -> None:
        for branch in document["allOf"]:
            branch.get("properties", {}).pop("event_type", None)

    problems = problems_after(scratch_contracts, "chunks-created.v1.schema.json", unpin)

    assert any("does not pin event_type" in problem for problem in problems)


def test_two_schemas_claiming_one_event_type_stops_the_build(scratch_contracts: Path) -> None:
    def steal_the_name(document: dict) -> None:
        for branch in document["allOf"]:
            if "event_type" in branch.get("properties", {}):
                branch["properties"]["event_type"]["const"] = "ContentSubmitted"

    problems = problems_after(scratch_contracts, "content-deleted.v1.schema.json", steal_the_name)

    assert any("is already pinned by" in problem for problem in problems)


def test_an_open_payload_stops_the_build(scratch_contracts: Path) -> None:
    def open_it_up(document: dict) -> None:
        payload_of(document)["additionalProperties"] = True

    problems = problems_after(scratch_contracts, "content-indexed.v1.schema.json", open_it_up)

    assert any("additionalProperties: false" in problem for problem in problems)


def test_a_camel_case_payload_field_stops_the_build(scratch_contracts: Path) -> None:
    def rename_to_camel(document: dict) -> None:
        properties = payload_of(document)["properties"]
        properties["contentId"] = properties.pop("content_id")

    problems = problems_after(scratch_contracts, "content-indexed.v1.schema.json", rename_to_camel)

    assert any("is not snake_case" in problem for problem in problems)


def test_a_payload_without_the_tenant_key_stops_the_build(scratch_contracts: Path) -> None:
    def drop_user_id(document: dict) -> None:
        payload = payload_of(document)
        payload["properties"].pop("user_id")
        payload["required"].remove("user_id")

    problems = problems_after(scratch_contracts, "chunks-created.v1.schema.json", drop_user_id)

    assert any("has no user_id" in problem for problem in problems)


def test_an_optional_tenant_key_stops_the_build(scratch_contracts: Path) -> None:
    def make_it_optional(document: dict) -> None:
        payload_of(document)["required"].remove("user_id")

    problems = problems_after(scratch_contracts, "chunks-created.v1.schema.json", make_it_optional)

    assert any("must be required, not optional" in problem for problem in problems)


def test_a_schema_the_readme_does_not_list_stops_the_build(scratch_contracts: Path) -> None:
    readme = scratch_contracts / "README.md"
    readme.write_text(
        readme.read_text(encoding="utf-8").replace(
            "- `ContentDeleted` -> emitted by `content-service`\n", ""
        ),
        encoding="utf-8",
    )

    problems = validate(scratch_contracts)

    assert any("which README.md does not list" in problem for problem in problems)


def test_a_readme_entry_with_no_schema_stops_the_build(scratch_contracts: Path) -> None:
    readme = scratch_contracts / "README.md"
    readme.write_text(
        readme.read_text(encoding="utf-8").replace(
            "## Routing", "- `ProcessingFailed` -> not written yet\n\n## Routing"
        ),
        encoding="utf-8",
    )

    problems = validate(scratch_contracts)

    assert any("'ProcessingFailed'" in problem for problem in problems)


def test_a_missing_readme_stops_the_build(scratch_contracts: Path) -> None:
    (scratch_contracts / "README.md").unlink()

    assert validate(scratch_contracts) == ["v1: no README.md"]


def test_an_empty_directory_is_an_error_not_a_pass(tmp_path: Path) -> None:
    (tmp_path / "empty").mkdir()

    with pytest.raises(ContractError, match=re.escape("No *.schema.json files")):
        validate(tmp_path / "empty")
