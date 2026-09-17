# ADR-0003: Redis is removed until a service needs it

- **Status:** Accepted
- **Date:** 2026-09-17

## Context

`docker-compose.yml` provisioned a Redis 7 container with a persistent volume, and the README listed
it under "Cache". Nothing referenced it:

- no service declared a Redis dependency, client or configuration property;
- no `.env.example` variable configured it;
- no document said what it would hold, who would write it, or when an entry would expire.

Three plausible jobs were discussed, none of them assigned:

1. a refresh-token revocation list for api-gateway-auth;
2. consumer idempotency keys, so a redelivered `event_id` is processed once;
3. an embedding cache, so re-indexing unchanged text does not pay for the same vector twice.

Each is a real need. None of them is a need *today*, and each has a design question attached that
nobody has answered — which is how "we have Redis" quietly becomes the answer to questions that
have better ones.

The cost of leaving it was not the memory. It was that a reader cloning the repository sees a cache
in the architecture and reasonably assumes the system depends on it, and that every one of the
three jobs above can now be "solved" by reaching for the thing that is already running.

## Decision

Remove the `redis` service and the `redis_data` volume from `docker-compose.yml`, and remove Redis
from the README's tech stack. Nothing else changes, because nothing else referenced it.

Redis comes back in the same pull request as the first code that uses it, not before.

### What each job needs before Redis is the answer

**Refresh-token revocation (api-gateway-auth).** The default is a `refresh_token` table in
`auth_db`: the token hash, its user, its expiry, and whether it was revoked. Logout sets a column.
That is one row read per refresh call, on a service that already has a database open, and it
survives a restart — which a revocation list must. Redis becomes the better answer only when refresh
traffic makes that read a measured problem, and even then the durable table stays as the source of
truth.

**Consumer idempotency (the Python workers).** Needs a decision first about what "already processed"
means per worker — an `event_id` seen set, or a natural key such as `content_id` plus a target
state. A worker that upserts by `content_id` may need no dedupe store at all. Deciding that is
prerequisite to choosing where the keys live.

**Embedding cache (embedding-worker).** Needs the hit rate measured before it is worth a component.
The cache key is a hash of chunk text plus model name; whether that ever hits depends on how much
material is re-indexed, which is unknown until the workers exist.

## Consequences

**What got better.** `docker compose up -d` starts six containers instead of seven, and every one
of them has a service that talks to it. The architecture diagram and the running system agree.
Three future decisions are written down with their prerequisites instead of being pre-empted by an
idle container.

**What got harder.** Whoever implements refresh-token revocation, worker idempotency or an
embedding cache has to re-add the service, the volume, the `.env.example` entries and the health
check. That is a few lines, and it comes with the obligation to say in the pull request what is
being stored and for how long.

## What would make this wrong

- If any of the three jobs above arrives with a measured need — and, for revocation, if the durable
  table proves too slow at real refresh volumes.
- If session state, rate limiting at the gateway, or SSE fan-out across multiple chat-llm-service
  instances lands, all of which want a shared in-memory store and none of which want a Postgres
  table.

The trigger is the same in every case: a component is added when code needs it, not when the
architecture diagram has room for it.
