# LootRefill — Project Specification

## 1. Project

- Name: LootRefill
- Type: Minecraft Paper plugin
- Language: Java
- Java: 21+
- Build: Maven
- Persistence: SQLite
- Main package: `com.arcraft.lootrefill`

## 2. Purpose

LootRefill manages storage containers in custom Minecraft worlds.

It provides:
- Map container discovery.
- Container identity and protection.
- LootTables.
- LootPools.
- Loot generation.
- Populate.
- Automatic refill.
- Region operations.
- Administrative GUIs.
- SQLite persistence.
- Progressive processing designed to avoid TPS impact.

## 3. Main Modules

```text
command
container
gui
loot
pool
populate
refill
region
scanner
storage
util
```

## 4. Core Systems

### Container
Responsible for identity, source, status, loot configuration, refill state and persistence.

Key concepts:
`LootContainer`, `ContainerManager`, `ContainerRegistry`, `ContainerBlockListener`, `ContainerType`, `ContainerSource`, `ContainerStatus`.

### Loot
Responsible for fixed LootTables and item generation.

Key concepts:
`LootTable`, `LootEntry`, `LootManager`, `LootTableStorage`, `LootGenerator`.

### Loot Pools
Responsible for dynamic selection of LootTables.

Key concepts:
`LootPool`, `LootPoolEntry`, `LootPoolManager`, `LootPoolStorage`, `PoolSelectionMode`.

Modes:
- `SINGLE_RANDOM`
- `MIXED_RANDOM`

### Populate
Responsible for initial population.

Key concepts:
`PopulateManager`, `PopulateQueue`, `PopulateJob`, `PopulateStats`, `PopulateResult`, `ContainerPopulator`.

### Refill
Responsible for scheduled/manual refill.

Key concepts:
`RefillManager`, `RefillQueue`, `RefillTask`, `RefillResult`, `RefillStats`.

### Scanner
Discovers containers without unnecessarily scanning every block.

Key concepts:
`RegionFileScanner`, `WorldScanner`, `ScanJob`.

### Region
Handles two-point region selection and region operations.

### GUI
Provides administrative interfaces without bypassing business rules.

Key components:
- `MenuHolder`: Base abstract class for menus with click cancelling, borders and close event handling.
- `GuiManager`: Enforces `lootrefill.admin` permission, cancels drags, double-clicks, and invalid slot clicks.
- `ChatInputHandler`: Captures asynchronous chat input safely returning to the main thread.
- `AdminMenu`: Main administrative dashboard.
- `ContainerMenu`: Container Manager 2.0 main paginated listing with search, sorting, active filter indicator and quick actions.
- `ContainerFilterMenu`: Visual interactive configuration for all 12 `ContainerFilter` criteria with pool/table sub-selectors.
- `ContainerDetailMenu`: Full technical inspector and management card for individual containers.
- `LootPoolsMenu`, `LootPoolEditorMenu`, `LootPoolMembersMenu`: Dynamic Loot Pool management suite.
- `ConfirmationMenu`: Reusable modal for critical administrative confirmations (`PLAYER → MAP`, container unregister, pool deletion, table removal) with anti-spam idempotency.

### Storage
SQLite persists:
- `loot_tables`
- `loot_entries`
- `loot_pools`
- `loot_pool_entries`
- `containers`
- `scan_jobs`
- `populate_jobs`

## 5. Container Model

A container conceptually contains:

```text
id
world
x
y
z
containerType
source
status
managed
registered
lootTableId
lootPoolId
refillEnabled
refillIntervalSeconds
looted
lastLoot
nextRefill
createdAt
updatedAt
```

Supported types currently include:

```text
CHEST
TRAPPED_CHEST
BARREL
FURNACE
BLAST_FURNACE
SMOKER
DISPENSER
DROPPER
```

## 6. Container Sources

```text
MAP
PLAYER
UNKNOWN
```

## 7. Container Status

```text
ACTIVE
BROKEN
DISABLED
```

## 8. Loot Configuration

Pool has priority:

```text
loot_pool_id != NULL
    → LootPool

loot_pool_id == NULL && loot_table_id != NULL
    → Legacy LootTable

both NULL
    → no loot
```

## 9. Populate

With:

```yaml
require-empty: true
```

behavior is:

```text
EMPTY
  → may populate

NOT EMPTY
  → SKIPPED_NOT_EMPTY
```

Existing contents must never be cleared automatically.

## 10. Auto Refill

Minimum eligibility:

```text
source = MAP
status = ACTIVE
managed = true
registered = true
refill_enabled = true
valid loot configuration
next_refill <= current time
```

Additional configured conditions may apply.

## 11. Refill Intervals

The active `config.yml` is the source of truth.

Historical `refill_interval_seconds` must not override current configuration.

```text
next_refill = current_time + configured_interval
```

The result must be persisted.

## 12. Loot Pool Behavior

Weights are relative and do not need to total 100.

### SINGLE_RANDOM
Select exactly one valid LootTable.

### MIXED_RANDOM
Select between `min_rolls` and `max_rolls`.

Default:

```text
allow_duplicates = false
```

When false, selection is without replacement.

## 13. Performance

The plugin processes work progressively using configured limits such as:

```text
containers-per-tick
max-ms-per-tick
batch-delay-ticks
max-refills-per-cycle
```

Chunk loading is controlled.

## 14. Threading

CPU/database work may be asynchronous when safe.

Bukkit/Paper world, block and inventory operations must respect server-thread requirements.

## 15. Compatibility

Legacy LootTable containers must continue working.

Existing container identity/protection rules must remain valid.

LootPools must integrate with existing Populate and Refill.

## 16. Validated Functionality

```text
Stage 1       Foundation                         ✓
Stage 2       Scanner + Refill Queue             ✓
Stage 2.1     MAP / PLAYER / BROKEN              ✓
Stage 3       Populate + LootGenerator           ✓
Stage 4       Auto Refill                        ✓
Stage 4.1     Region Wand                        ✓
Stage 4.1.1   PLAYER → MAP conversion            ✓
FIX 4.4.1     Interval + next_refill persistence ✓
Stage 4.2     Loot Pools                         ✓
MIXED_RANDOM  no-replacement correction          ✓
CM 2.0 F1     Backend Filtering, Search & Paging ✓
CM 2.0 F2     Loot Pool Manager GUI Suite        ✓
CM 2.0 F3     ConfirmationMenu Modal & Safety    ✓
CM 2.0 F4     Container Manager 2.0 Main GUI     ✓
CM 2.0 F5     Container Detail Inspector Menu    ✓
CM 2.0 F6     Final Audit & Documentation Close  ✓
```

These are established functionality and must be preserved.

## 17. Development Goal

LootRefill should remain modular, performant, persistent, backward-compatible, safe and maintainable as new systems are added.
