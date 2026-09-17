"""The exit codes CI depends on."""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest
from conftest import edit, payload_of

from studymind_contracts.catalog import ContractError
from studymind_contracts.cli import main, repository_root


def test_validate_passes_on_this_repository(repo_root: Path) -> None:
    assert main(["--root", str(repo_root), "validate"]) == 0


def test_codegen_check_passes_on_this_repository(repo_root: Path) -> None:
    assert main(["--root", str(repo_root), "codegen", "--check"]) == 0


def test_validate_fails_loudly_on_a_broken_contract(tmp_path: Path, repo_root: Path) -> None:
    root = _clone_repo_shape(tmp_path, repo_root)

    def open_the_payload(document: dict) -> None:
        payload_of(document)["additionalProperties"] = True

    edit(root / "contracts/events/v1/content-deleted.v1.schema.json", open_the_payload)

    assert main(["--root", str(root), "validate"]) == 1


def test_validate_reports_an_unusable_directory_as_an_error(tmp_path: Path) -> None:
    assert main(["--root", str(tmp_path), "validate"]) == 2


def test_codegen_check_fails_when_the_java_is_stale(tmp_path: Path, repo_root: Path) -> None:
    root = _clone_repo_shape(tmp_path, repo_root)

    def rename(document: dict) -> None:
        properties = payload_of(document)["properties"]
        properties["storage_location"] = properties.pop("storage_path")

    edit(root / "contracts/events/v1/content-submitted.v1.schema.json", rename)

    assert main(["--root", str(root), "codegen", "--check"]) == 1


def test_codegen_writes_then_agrees_with_itself(tmp_path: Path, repo_root: Path) -> None:
    root = _clone_repo_shape(tmp_path, repo_root)
    shutil.rmtree(root / "services/content-service/src/main/java/com/contentservice/events/wire")

    assert main(["--root", str(root), "codegen", "--check"]) == 1
    assert main(["--root", str(root), "codegen"]) == 0
    assert main(["--root", str(root), "codegen", "--check"]) == 0


def test_finds_the_repository_root_from_anywhere_inside_it(repo_root: Path) -> None:
    assert repository_root(repo_root / "services" / "content-service") == repo_root


def test_says_so_when_there_is_no_repository_above(tmp_path: Path) -> None:
    with pytest.raises(ContractError, match="No contracts/events/v1 directory"):
        repository_root(tmp_path)


def _clone_repo_shape(tmp_path: Path, repo_root: Path) -> Path:
    """Only the two directories the commands touch, so a test cannot write to the real tree."""
    root = tmp_path / "repo"
    shutil.copytree(repo_root / "contracts", root / "contracts")
    shutil.copytree(
        repo_root / "services/content-service/src/main/java",
        root / "services/content-service/src/main/java",
    )
    return root
