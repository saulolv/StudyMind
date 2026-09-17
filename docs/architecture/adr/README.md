# Architecture decision records

One file per decision that was hard to make and would be expensive to reverse. Each records what
was decided, what it cost, and what would make it wrong — the last part matters most, because a
decision with no stated failure condition cannot be revisited honestly.

A decision that is obvious, cheap to change, or already visible in the code does not get an ADR.

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-merge-document-and-video-into-content-service.md) | document-service and video-service are one content-service | Accepted |
| [0002](0002-chunks-are-a-storage-artifact.md) | Chunks live in Qdrant, not in a relational table | Accepted |
| [0003](0003-remove-redis-until-something-needs-it.md) | Redis is removed until a service needs it | Accepted |
| [0004](0004-generate-wire-types-from-the-event-contracts.md) | Event wire types are generated from the JSON Schemas | Accepted |
| [0005](0005-coverage-gate-lives-in-the-build.md) | The 90% coverage gate is in the POM, not only in CI | Accepted |

## Writing a new one

Copy the shape of an existing file. Number it sequentially, never renumber, and never delete one:
a superseded decision gets `Status: Superseded by ADR-00XX` and stays, because the reasoning that
was overturned is the most useful part of the record.
