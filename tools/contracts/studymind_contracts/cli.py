"""The two commands CI runs over the contracts."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from studymind_contracts import javagen
from studymind_contracts.catalog import Catalog, ContractError
from studymind_contracts.validate import validate

# Every service that publishes events, and where its generated wire types belong. A service is
# listed here only once it publishes: the generator refuses a source_service no schema pins.
PUBLISHERS = {
    "content-service": {
        "source_root": "services/content-service/src/main/java",
        "package": "com.contentservice",
    },
}


def repository_root(start: Path | None = None) -> Path:
    """Walk up until the contracts directory is in sight, so the commands work from anywhere."""
    current = (start or Path.cwd()).resolve()
    for candidate in (current, *current.parents):
        if (candidate / "contracts" / "events" / "v1").is_dir():
            return candidate
    raise ContractError(f"No contracts/events/v1 directory at or above {current}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="studymind-contracts", description=__doc__)
    parser.add_argument("--root", type=Path, default=None, help="repository root (default: found)")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate", help="check every event schema and the README index")
    codegen = commands.add_parser("codegen", help="write the Java wire types from the schemas")
    codegen.add_argument(
        "--check",
        action="store_true",
        help="fail instead of writing when the generated files are out of date",
    )

    args = parser.parse_args(argv)
    try:
        root = args.root.resolve() if args.root else repository_root()
        if args.command == "validate":
            return _validate(root)
        return _codegen(root, check=args.check)
    except ContractError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


def _validate(root: Path) -> int:
    directory = root / "contracts" / "events" / "v1"
    problems = validate(directory)
    if problems:
        print(f"{len(problems)} contract problem(s) in {directory.as_posix()}:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    catalog = Catalog(directory)
    print(f"{len(catalog.events)} event contracts valid:")
    for schema in catalog.events:
        print(f"  {schema.event_type} <- {schema.file_name}")
    return 0


def _codegen(root: Path, *, check: bool) -> int:
    catalog = Catalog(root / "contracts" / "events" / "v1")
    stale: list[Path] = []
    for service, target in PUBLISHERS.items():
        source_root = root / target["source_root"]
        files = javagen.generate(catalog, service, target["package"])
        if check:
            for file in files:
                path = source_root / file.relative_path
                if not path.is_file() or path.read_text(encoding="utf-8") != file.content:
                    stale.append(path)
            continue
        for path in javagen.write(files, source_root):
            print(f"wrote {path.relative_to(root).as_posix()}")
    if stale:
        print("generated wire types are out of date:", file=sys.stderr)
        for path in stale:
            print(f"  - {path.relative_to(root).as_posix()}", file=sys.stderr)
        print("run: python -m studymind_contracts codegen", file=sys.stderr)
        return 1
    if check:
        print("generated wire types are up to date")
    return 0
