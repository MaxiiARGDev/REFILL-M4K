# LootRefill — Architecture

## 1. High-Level Architecture

```text
Minecraft / Paper
        │
        ▼
LootRefillPlugin
        │
        ├── Commands
        ├── GUI
        ├── Scanner
        ├── Region
        ├── ContainerManager
        ├── PopulateManager
        └── RefillManager
                    │
                    ▼
             Loot selection
              /          \
         LootPool      LootTable
              \          /
               ▼        ▼
                LootGenerator
                      │
                      ▼
                 Bukkit Inventory
                      │
                      ▼
                    SQLite
```

## 2. Bootstrap

`LootRefillPlugin` initializes and wires the major services/managers.

Initialization order must respect dependencies.

## 3. Container Layer

Main concepts:

```text
LootContainer
ContainerManager
ContainerRegistry
ContainerBlockListener
ContainerType
ContainerSource
ContainerStatus
```

Responsibilities:
- Represent registered containers.
- Track origin and status.
- Persist state.
- Enforce identity rules.
- Provide data to Populate and Refill.

## 4. Loot Layer

```text
LootTable
LootEntry
LootManager
LootTableStorage
LootGenerator
```

Flow:

```text
LootTable
   ↓
LootEntry selection
   ↓
Amount / stack logic
   ↓
Generated ItemStacks
```

`LootGenerator` does not own container identity or persistence.

## 5. LootPool Layer

```text
LootPool
LootPoolEntry
LootPoolManager
LootPoolStorage
PoolSelectionMode
```

Flow:

```text
Container
   ↓
LootPool
   ↓
Weighted selection
   ↓
Selected LootTable(s)
   ↓
LootGenerator
```

The Pool layer decides which tables are used; the generator decides what items those tables produce.

## 6. Populate

```text
Populate command
      ↓
PopulateManager
      ↓
Candidate selection
      ↓
PopulateQueue
      ↓
ContainerPopulator
      ↓
LootPool or LootTable
      ↓
LootGenerator
      ↓
Inventory
      ↓
next_refill
      ↓
SQLite
```

Populate remains progressive.

## 7. Refill

```text
Scheduler
   ↓
Due containers
   ↓
Eligibility
   ↓
Per-type queues
   ↓
Round-robin
   ↓
RefillQueue
   ↓
Chunk handling
   ↓
Inventory conditions
   ↓
LootPool / LootTable
   ↓
LootGenerator
   ↓
Inventory
   ↓
next_refill
   ↓
SQLite
```

A skipped container does not block queue processing.

## 8. Chunk Management

Refill and scanner systems use controlled chunk loading.

Relevant configuration includes:

```text
refill.chunks.only-load-needed-chunks
refill.chunks.unload-after-refill
refill.conditions.max-chunks-loaded-by-refill
```

Avoid unnecessary chunk loads.

## 9. World Scanner

```text
World
 ↓
Region files
 ↓
Generated chunk coordinates
 ↓
Async chunk load where appropriate
 ↓
Tile entity inspection
 ↓
Container detection
 ↓
ContainerManager
 ↓
SQLite
```

The scanner must not iterate every block unnecessarily.

## 10. Region System

```text
Admin
 ↓
Region Wand
 ↓
Point A + Point B
 ↓
RegionSelection
 ↓
RegionScanner
 ↓
Container classification
 ↓
Explicit administrative operation
```

Region operations preserve container protection.

## 11. Persistence

Conceptual SQLite relationships:

```text
loot_tables
    │
    └── loot_entries

loot_pools
    │
    └── loot_pool_entries

containers
    ├── loot_table_id
    └── loot_pool_id

scan_jobs
populate_jobs
```

Runtime queues may be rebuilt from persistent state after restart.

## 12. Configuration

For refill intervals:

```text
config.yml
    ↓
RefillManager
    ↓
Effective interval
    ↓
next_refill
    ↓
SQLite
```

Historical stored interval values do not override active configuration.

## 13. Command Layer

`LootCommand` exposes the administrative `/loot` command tree.

Existing commands should remain compatible unless explicitly changed.

## 14. GUI Layer

```text
Player
 ↓
Admin GUI
 ↓
Manager / Service
 ↓
Business logic
 ↓
Persistence / Runtime
```

GUI code should not duplicate database business logic.

## 15. Design Principles

### Separation of concerns
Each module has a clear responsibility.

### Explicit state
Container source/status/registration state is explicit.

### Progressive processing
Large operations are divided into controlled work units.

### Safety
Player containers and existing inventory contents are protected.

### Backward compatibility
Legacy LootTables remain supported.

### Minimal coupling
New systems integrate through existing services/managers.

### Performance
No feature should require abandoning established per-tick/chunk limits.
