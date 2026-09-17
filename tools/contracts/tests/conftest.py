from __future__ import annotations

import json
import shutil
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[3]
CONTRACTS = REPO_ROOT / "contracts" / "events" / "v1"


@pytest.fixture
def repo_root() -> Path:
    return REPO_ROOT


@pytest.fixture
def contracts() -> Path:
    """The real contracts directory. Tests that read it must not write to it."""
    return CONTRACTS


@pytest.fixture
def scratch_contracts(tmp_path: Path) -> Path:
    """A throwaway copy of the real contracts, for tests that break one on purpose."""
    target = tmp_path / "v1"
    shutil.copytree(CONTRACTS, target)
    return target


def edit(path: Path, mutate) -> None:
    """Apply ``mutate`` to a schema file in place."""
    document = json.loads(path.read_text(encoding="utf-8"))
    mutate(document)
    path.write_text(json.dumps(document, indent=2), encoding="utf-8")


def payload_of(document: dict) -> dict:
    for branch in document["allOf"]:
        payload = branch.get("properties", {}).get("payload")
        if payload is not None:
            return payload
    raise AssertionError("fixture schema has no payload")
