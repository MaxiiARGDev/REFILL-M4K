# LootRefill — Invariants

These rules must remain true unless an explicit architectural change is approved, documented and tested.

## Container Identity

### INV-001 — PLAYER protection

`source = PLAYER` must never be automatically modified by Scanner, Populate, Auto Refill, Region Scan or background tasks.

Only an explicit administrative conversion may intentionally perform:

```text
PLAYER → MAP
```

### INV-002 — BROKEN protection

`status = BROKEN` must not participate in automatic Populate or Refill.

### INV-003 — DISABLED protection

`status = DISABLED` must not participate in automatic operations.

### INV-004 — UNKNOWN protection

`source = UNKNOWN` must be treated conservatively and must not be automatically modified.

## Inventory Safety

### INV-005 — Never clear occupied inventories

With `require-empty=true`, an occupied inventory must result in:

```text
SKIPPED_NOT_EMPTY
```

Never clear existing contents to make room for automatic loot.

### INV-006 — Skip does not block queue

A skipped container must not prevent other containers from processing.

## Loot

### INV-007 — LootPool priority

If `loot_pool_id != NULL`, LootPool has priority over `loot_table_id`.

### INV-008 — Legacy compatibility

If `loot_pool_id == NULL` and `loot_table_id != NULL`, legacy LootTable behavior must continue working.

### INV-009 — No configuration means no loot

If both IDs are NULL, no loot is generated.

## LootPool

### INV-010 — Relative weights

Weights are relative values and do not need to sum to 100.

### INV-011 — SINGLE_RANDOM

Select exactly one valid LootTable.

### INV-012 — MIXED_RANDOM default

`allow_duplicates = false` by default.

### INV-013 — MIXED_RANDOM without replacement

When duplicates are disabled, a LootTable ID cannot occur twice in the same selection.

### INV-014 — MIXED_RANDOM with replacement

When `allow_duplicates = true`, duplicates may occur.

### INV-015 — Invalid Pool safety

Invalid, disabled or empty Pools must not crash the scheduler, clear inventory or delete container data.

## Refill

### INV-016 — Config interval source of truth

Effective refill intervals come from active `config.yml`.

The historical `refill_interval_seconds` database field must not override current configuration.

### INV-017 — next_refill persistence

Changes to `next_refill` must be persisted correctly to SQLite and survive restart.

### INV-018 — Due detection

A container is due when:

```text
next_refill != NULL
AND
next_refill <= current_time
```

### INV-019 — Automatic refill eligibility

At minimum:

```text
source = MAP
status = ACTIVE
managed = true
registered = true
refill_enabled = true
valid loot configuration
due = true
```

## Performance

### INV-020 — Progressive processing

Large workloads must remain queue-based/progressive.

### INV-021 — Per-tick limits

Respect configured processing and time limits.

### INV-022 — Controlled chunks

Do not force-load an entire world for routine processing.

### INV-023 — Efficient scanning

Use the existing lightweight scanner architecture rather than unnecessary full block scans.

## Thread Safety

### INV-024 — Inventory operations

Inventory modifications must happen on the appropriate server thread.

### INV-025 — Async database

Database operations may be asynchronous when safe and should not unnecessarily block the main thread.

## Persistence

### INV-026 — SQLite persistence

Persistent state must survive server restart.

### INV-027 — Migration safety

Migrations must preserve existing data whenever reasonably possible.

## GUI

### INV-028 — Administrative permission

Administrative GUI operations require the configured/admin permission.

### INV-029 — GUI protection

GUI interactions must not allow unintended item theft, insertion or duplication.

## Commands

### INV-030 — Existing command compatibility

Existing commands remain compatible unless explicitly changed.

## Architecture

### INV-031 — Module separation

Keep responsibilities separated between:

```text
GUI
Container
Loot
Pool
Populate
Refill
Scanner
Region
Storage
```

### INV-032 — No unnecessary rewrites

Do not rewrite working subsystems merely for convenience.

## Changing an Invariant

If a future feature requires changing an invariant:

1. Identify it.
2. Explain why.
3. Update this document.
4. Add/update tests.
5. Document the architectural decision.

Never change invariant behavior silently.
