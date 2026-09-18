# LootRefill — Agent Development Rules

## 1. Purpose

LootRefill is a modular Minecraft Paper plugin responsible for managing map containers, loot generation, loot pools, population and automatic refill.

This repository uses a lightweight Spec-Driven Development (SDD) workflow.

Before modifying the project, the agent MUST read:

1. `SDD/PROJECT_SPEC.md`
2. `SDD/INVARIANTS.md`
3. `SDD/ARCHITECTURE.md`
4. The specification of the current stage, if one exists.

---

## 2. Source of Truth

The current codebase and the SDD documents are the source of truth.

Historical implementation documents in the repository may describe previous stages and decisions.

Do not assume that an old stage document overrides the current SDD.

If there is a conflict:

1. Current code behavior must be inspected.
2. `SDD/INVARIANTS.md` has priority for protected behavior.
3. `SDD/PROJECT_SPEC.md` defines the current project contract.
4. The current stage specification defines the requested feature.

If a conflict cannot be resolved safely, stop and report it instead of guessing.

---

## 3. Before Coding

Before implementing a feature:

1. Read the relevant SDD files.
2. Inspect the existing implementation.
3. Identify the modules affected by the change.
4. Identify existing invariants that could be affected.
5. Avoid unnecessary architectural changes.
6. Reuse existing systems when possible.
7. Do not rewrite working systems without a concrete reason.

For large changes, briefly state:

- What will change.
- Which files/modules are affected.
- Which existing behavior must remain untouched.

---

## 4. Preserve Existing Systems

New features must integrate with the existing architecture.

Do not silently replace or remove:

- Container identity and protection.
- MAP / PLAYER / BROKEN / DISABLED handling.
- SQLite persistence.
- LootTable legacy support.
- LootPool support.
- PopulateQueue.
- RefillQueue.
- Round-robin refill processing.
- Chunk loading/unloading controls.
- `require-empty` protection.
- Config-driven refill intervals.
- Region selection and scanning.
- Existing GUI protection.
- Existing commands.

If an existing system must change, explain why and update the appropriate SDD documentation.

---

## 5. Minecraft Thread Safety

Bukkit/Paper API operations that require the main server thread must remain on the main thread.

Do not perform unsafe Bukkit world, block, entity or inventory operations asynchronously.

Database operations and CPU-only calculations may be asynchronous when safe.

Performance is a core requirement of LootRefill.

---

## 6. Performance

LootRefill is designed to operate on large custom worlds.

Avoid:

- Loading an entire world into memory.
- Force-loading large numbers of chunks.
- Iterating every block of a world unnecessarily.
- Performing large synchronous loops on the main thread.
- Synchronous database operations inside high-frequency processing loops.
- Unbounded queues.

Respect configured limits such as:

- containers-per-tick
- max-ms-per-tick
- batch-delay-ticks
- max-refills-per-cycle
- max-chunks-loaded-by-refill

Do not remove these protections to simplify an implementation.

---

## 7. Container Protection

Container origin and state are fundamental to the plugin.

PLAYER containers must never be modified automatically unless an explicit administrative action intentionally converts them to MAP.

BROKEN containers must not participate in automatic refill or populate.

DISABLED containers must not participate in automatic refill or populate.

UNKNOWN containers must be treated safely and must not be modified automatically.

Never assume that every physical container found in the world belongs to LootRefill.

---

## 8. Inventory Protection

When `require-empty: true`:

- Never clear an occupied inventory.
- Never overwrite existing items.
- Return the appropriate skipped result.
- Continue processing other containers.

A skipped container must not block the refill queue.

---

## 9. Loot System

LootRefill supports two loot configuration modes:

### Legacy LootTable

```text
loot_table_id != NULL
loot_pool_id == NULL