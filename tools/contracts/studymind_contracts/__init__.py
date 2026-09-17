"""Tooling that treats contracts/events/v1 as the source of truth rather than documentation.

Two commands, both run in CI:

``validate``
    The schemas are well formed Draft 2020-12, their ``$ref``s resolve, they follow the
    conventions the whole system depends on, and the README lists exactly the files that exist.

``codegen``
    The Java wire types the publishing service builds its envelopes from are generated from the
    schemas, so renaming a field in a schema breaks the compile instead of the wire.
"""

from studymind_contracts.catalog import Catalog, EventSchema

__all__ = ["Catalog", "EventSchema"]
