# SDD Audit

## Summary

Se ha realizado una auditoría exhaustiva e integral del repositorio **LootRefill** contrastando la documentación normativa (`AGENTS.md`, `SDD/PROJECT_SPEC.md`, `SDD/INVARIANTS.md`, `SDD/ARCHITECTURE.md`) con el código fuente Java y los archivos de configuración (`config.yml`, `plugin.yml`).

En términos generales, el proyecto se encuentra en un estado arquitectónico sólido y maduro:
- La separación en módulos (`container`, `loot`, `pool`, `populate`, `refill`, `scanner`, `region`, `storage`, `gui`, `command`, `util`) está bien definida.
- La protección de identidades (`MAP` vs `PLAYER`, `ACTIVE` vs `BROKEN` vs `DISABLED`) y la seguridad de inventarios (`require-empty: true`) están implementadas y activas en el núcleo.
- El procesamiento escalonado y progresivo mediante colas por tick con límites de tiempo (`max-ms-per-tick`) y control de carga/descarga de chunks se cumple tanto en `WorldScanner`, como en `PopulateQueue` y `RefillQueue`.
- La persistencia SQLite con migraciones defensivas y soporte relacional para LootTables y LootPools está operativa.

Sin embargo, la auditoría ha detectado varias inconsistencias críticas y riesgos de compatibilidad entre subsistemas más recientes (especialmente la integración entre **LootPools**, **PopulateManager**, **WorldScanner** y las interfaces **GUI**), que deben ser atendidos antes de avanzar a nuevas etapas de desarrollo.

---

## Architecture

El diseño documentado en `SDD/ARCHITECTURE.md` se corresponde fielmente con la estructura real de paquetes y clases:
- **`com.arcraft.lootrefill`**: Contiene la clase principal `LootRefillPlugin`, encargada del ciclo de vida y la inyección de dependencias.
- **Módulos presentes**:
  - `container`: Gestión del modelo `LootContainer`, registro en memoria/DB, filtrado por tipo y listener de bloques (`ContainerBlockListener`).
  - `loot`: Modelos `LootTable` y `LootEntry`, motor estático `LootGenerator`, y almacenamiento `LootTableStorage`.
  - `pool`: Modelos `LootPool` y `LootPoolEntry`, selector estocástico `LootPoolManager`, persistencia `LootPoolStorage`.
  - `populate`: Motor de llenado seguro `ContainerPopulator`, cola progresiva `PopulateQueue`, control general `PopulateManager`.
  - `refill`: Scheduler round-robin continuo `RefillQueue`, orquestador de condiciones `RefillManager`, estados `RefillStats` y `RefillResult`.
  - `scanner`: Lector de cabeceras MCA `RegionFileScanner`, ejecutor progresivo de inspección `WorldScanner`.
  - `region`: Manejo de selección A/B (`RegionSelection`), varita (`RegionWandListener`), escaneo regional (`RegionScanner`) y conversiones administrativas.
  - `storage`: Administrador central JDBC `DatabaseManager` (SQLite, WAL, migraciones).
  - `gui`: 13 clases basadas en `MenuHolder`, con `GuiManager` y captura de chat `ChatInputHandler`.
  - `command`: Controlador unificado `LootCommand`.
  - `util`: Herramientas auxiliares (`ItemBuilder`, `MessageUtil`, `GuiItem`).

**Observación arquitectónica**:
Existe un acoplamiento menor en `PopulateManager` y `RegionScanner`, donde se implementan métodos de formateo y manejo de comandos de chat (`showPreview`, `handleAssignCommand`, `handleAssignPoolCommand`), los cuales deberían residir idealmente en la capa de comandos o en servicios de reporte dedicados para mantener la lógica de negocio pura.

---

## Containers

El ciclo de vida e identidad de los contenedores funciona de la siguiente manera:

1. **MAP**:
   - Representa contenedores del mapa original o registrados explícitamente mediante `/loot register`, `/loot register-pool` o `/loot region assign confirm`.
   - Poseen `source = MAP`, `status = ACTIVE`, `managed = true`, `registered = true`.
   - Son los únicos elegibles para Populate y Auto Refill.
2. **PLAYER**:
   - Al colocar un jugador un bloque contenedor, `ContainerBlockListener` lo detecta y lo registra en SQLite con `source = PLAYER`, `managed = false`, `registered = false`, `loot_table_id = null`, `loot_pool_id = null`.
   - Nunca son modificados automáticamente ni participan en Populate o Refill.
   - Solo pueden convertirse a MAP mediante la orden administrativa explícita `/loot region assign confirm-player`.
3. **BROKEN**:
   - Si un bloque MAP se rompe, se actualiza a `status = BROKEN`, `managed = false`, `refill_enabled = false`. Su registro se conserva en la base de datos para no perder trazabilidad, pero queda totalmente excluido de Populate y Refill.
   - Si un bloque PLAYER se rompe, se desregistra y elimina de la base de datos.
4. **DISABLED**:
   - Contenedores con `status = DISABLED` o `enabled = false` no son procesados.
5. **UNKNOWN**:
   - Cualquier contenedor que no sea estrictamente `MAP` es descartado por defecto.
6. **Elegibilidad y Due**:
   - `isDue()`: `next_refill != null && next_refill <= System.currentTimeMillis()`.
   - `isEligibleForRefill()`: `isAdministrable() && refillEnabled && hasLootConfigured() && isDue()`.

**Riesgos e Inconsistencias Encontradas**:
1. **Pérdida de `loot_pool_id` en `WorldScanner`**:
   En `WorldScanner.inspectChunk()` (líneas 260-280), cuando se detecta un contenedor existente (`existing != null`), se instancia un nuevo objeto `LootContainer` llamando al constructor legacy de 19 parámetros que no recibe `lootPoolId` (dejándolo en `null`). Al sincronizar el lote con `saveContainersBatch()`, `loot_pool_id` se sobreescribe con `NULL` en la base de datos SQLite, **borrando todas las configuraciones de pools previamente asignadas al re-escanear**.
2. **Falta de limpieza de `loot_pool_id` al colocar bloque PLAYER sobre coordenada previa**:
   En `ContainerRegistry.registerPlayerBlock()` (línea 99), al reutilizar una coordenada previamente registrada se ejecuta `existing.setLootTableId(null)`, pero **no se invoca `existing.setLootPoolId(null)`**, por lo que el contenedor PLAYER retiene el pool ID en memoria y SQLite.
3. **Falta de limpieza de `loot_pool_id` en `registerMapBlock`**:
   En `ContainerRegistry.registerMapBlock()`, al reasignar una tabla legacy se establece `existing.setLootTableId(lootTableId)`, pero no se limpia `loot_pool_id`. Como LootPool tiene prioridad (INV-007), el contenedor continuará generando botín desde el pool y no desde la tabla asignada.

---

## Loot

El subsistema de Loot Table tradicional opera con las siguientes premisas:
- **`LootTable`**: Contiene `id`, `displayName`, `description`, `enabled`, `minItems`, `maxItems` y una lista de `LootEntry`.
- **`LootEntry`**: Almacena `item` (ItemStack de Bukkit serializado a Base64 mediante `serializeAsBytes()`), peso (`weight`), `minAmount`, `maxAmount`, `customType` y comando opcional.
- **`LootGenerator`**:
  - Aplica selección ponderada aleatoria (`ThreadLocalRandom`).
  - Respeta rangos configurados de cantidad (`minAmount` a `maxAmount`).
  - Si la cantidad calculada supera el tamaño máximo del stack (`maxStackSize`), divide de forma segura el botín en múltiples ItemStacks independientes (`while (totalAmount > 0) ...`).
- **`LootManager`**:
  - Mantiene las tablas cargadas en memoria y crea las tablas base por defecto (`common`, `food`, `medical`, `military`, `high_tier`, `rare`) si la base de datos está vacía.

**Observación**:
Existe código duplicado de generación en `LootTable.generateLoot()` frente a `LootGenerator.generate(table)`. `LootTable.generateLoot()` no implementa la partición en múltiples stacks cuando la cantidad supera `maxStackSize`, mientras que `LootGenerator` sí lo hace. Actualmente `ContainerPopulator` y `RefillManager` utilizan correctamente `LootGenerator`.

---

## Loot Pools

El sistema de Loot Pools (Etapa 4.2) implementa selección estocástica multinivel:
- **`LootPool`**: Modelo con `id`, `name`, `selectionMode`, `minRolls`, `maxRolls`, `allowDuplicates`, `enabled` y colección de `LootPoolEntry`.
- **`PoolSelectionMode`**:
  - `SINGLE_RANDOM`: Selecciona exactamente 1 `LootTable` válida según los pesos relativos de sus entradas.
  - `MIXED_RANDOM`: Determina una cantidad aleatoria de rolls entre `min_rolls` y `max_rolls`.
    - Con `allow_duplicates = false` (por defecto): Selección sin reemplazo mediante una lista clonada de candidatos (`poolCandidates.remove(picked)` y actualización de peso residual `currentWeight -= picked.getWeight()`). Garantiza que una tabla no sea seleccionada más de una vez en un mismo ciclo.
    - Con `allow_duplicates = true`: Selección con reemplazo iterando sobre la lista completa de entradas.
- **Pesos Relativos**: Los pesos son relativos y no requieren sumar 100.
- **Tolerancia a Fallos**: Si un pool no existe, está deshabilitado o no tiene tablas activas, devuelve una lista vacía de forma segura sin generar excepciones ni vaciar inventarios.

---

## Populate

El flujo real de Populate es:
`Comando (/loot populate <mundo> confirm)` → `PopulateManager` → `PopulateQueue` (scheduler por tick) → `ContainerPopulator.populate()` → `LootPool` / `LootTable` → `LootGenerator` → `Inventory` → Persistencia SQLite (`next_refill`, `last_loot`, `looted`).

Comportamientos verificados:
- **`require-empty`**:
  - Se lee desde `populate.require-empty` (por defecto `true`).
  - Si el inventario contiene al menos 1 ítem no nulo/no aire, devuelve `SKIPPED_NOT_EMPTY`.
  - **Bajo ninguna circunstancia se invoca `inv.clear()`**. Los ítems existentes se protegen estrictamente.
- **Doble Cofre**:
  - `ContainerPopulator` y `PopulateQueue` utilizan `processedDoubleChests` identificando el par mediante la coordenada mínima (`Math.min(x1, x2)` y `Math.min(z1, z2)`). Si una mitad ya fue poblada en la sesión, la otra devuelve `SKIPPED_DOUBLE_CHEST_ALREADY_POPULATED`.
- **Actualización de `next_refill`**:
  - Al poblar con éxito, calcula `next_refill = now + (interval * 1000L)` utilizando el intervalo configurado en `config.yml` para el tipo de contenedor, reinicia `looted = false` y persiste los cambios.

**Inconsistencias Críticas Encontradas**:
1. **EXCLUSIÓN TOTAL DE LOOT POOLS EN POPULATE**:
   En `PopulateManager.java`:
   - En `showPreview()` (línea 90):
     ```java
     String tableId = c.getLootTableId();
     if (tableId == null || tableId.trim().isEmpty()) {
         skippedNoTable++;
         continue;
     }
     ```
   - En `startPopulate()` (línea 151):
     ```java
     && c.getLootTableId() != null
     && !c.getLootTableId().trim().isEmpty()
     ```
   **Cualquier contenedor configurado exclusivamente con un `loot_pool_id` (y con `loot_table_id == null`) es catalogado como `skippedNoTable` y queda 100% descartado del Populate.** A pesar de que `ContainerPopulator.populate()` sí implementa soporte para Loot Pools (línea 70), el filtro de candidatos de `PopulateManager` impide que lleguen a la cola de ejecución.
2. **Hilo Bukkit en `PopulateQueue`**:
   En `PopulateQueue.processContainer()` (líneas 118-127), cuando el chunk no está cargado:
   ```java
   world.getChunkAtAsync(chunkX, chunkZ, false).thenAccept(chunk -> {
       if (chunk != null) {
           PopulateResult result = populator.populate(container);
           ...
   ```
   A diferencia de `RefillQueue` y `RegionScanner`, **no se encapsula la ejecución dentro de `Bukkit.getScheduler().runTask(plugin, ...)`**, lo cual delega la ejecución de bloques e inventarios al hilo que resuelve el CompletableFuture, generando un riesgo de thread safety según la versión de Paper.

---

## Auto Refill

El subsistema de Auto Refill opera de forma continua y desacoplada del TPS:
1. **Detección de contenedores vencidos**:
   `ContainerManager.getContainersDueForRefill(type, currentTime, limit)` ejecuta una consulta indexada sobre SQLite:
   `WHERE container_type = ? AND source = 'MAP' AND status = 'ACTIVE' AND managed = 1 AND registered = 1 AND refill_enabled = 1 AND ((loot_table_id IS NOT NULL AND loot_table_id != '') OR (loot_pool_id IS NOT NULL AND loot_pool_id != '')) AND next_refill IS NOT NULL AND next_refill <= ? ORDER BY next_refill ASC LIMIT ?`.
2. **Round-Robin**:
   `RefillQueue` mantiene 8 colas separadas en un `EnumMap<ContainerType, Deque<LootContainer>>` (`ROUND_ROBIN_TYPES`). En cada tick avanza `currentTypeIndex = (currentTypeIndex + 1) % 8` para garantizar que ningún tipo de contenedor monopolice el procesamiento.
3. **Límites de tiempo por tick**:
   Monitorea `System.nanoTime() - startNanos < maxNanos` (por defecto 2 ms) y `processedCount < maxPerTick` (por defecto 5 contenedores).
4. **Condiciones de Refill**:
   - `require-empty: true`: si el inventario tiene contenido, retorna `SKIPPED_NOT_EMPTY`.
   - `require-no-players-nearby`: valida radio cúbico/esférico de jugadores (`SKIPPED_PLAYER_NEARBY`).
   - Hornos en combustión: si `furnace.getBurnTime() > 0`, retorna `SKIPPED_BURNING`.
   - Doble cofre: sincroniza el timestamp sin duplicar el botín (`SKIPPED_DOUBLE_CHEST_PAIR`).
5. **Control de Chunks**:
   - Si el chunk está cargado, procesa de inmediato en el main thread.
   - Si no está cargado y `refillLoadedChunksOnly = false`, verifica el semáforo `currentlyLoadingChunks < maxChunksLoadedByRefill`. Si hay cupo, invoca `world.getChunkAtAsync()` y despacha la ejecución de vuelta al main thread con `runTask()`.
   - Si `unload-after-refill: true` y no hay jugadores en el mundo, solicita la descarga del chunk.
6. **Manejo de omisiones (Skip Cooldown)**:
   Los contenedores omitidos temporalmente (por jugador cerca, cofre ocupado, etc.) se agregan a un mapa en memoria `retryCooldowns` con 30 segundos de espera (`RETRY_COOLDOWN_MS = 30_000L`), evitando re-consultarlos en cada tick sin modificar su `next_refill` en la base de datos y sin bloquear la cola.
7. **Intervalo y Persistencia**:
   `RefillManager.getIntervalForType(type)` consulta `config.yml` en tiempo real. Al completarse el refill, actualiza `next_refill = now + (interval * 1000L)` y lo persiste en SQLite.

**Inconsistencias Encontradas**:
1. En `RefillQueueMenu.java` (línea 81), la acción de posponer refill calcula el nuevo tiempo como `now + (container.getRefillIntervalSeconds() * 1000L)` usando el campo de la base de datos en lugar de consultar `refillManager.getIntervalForType(container.getContainerType())`, contraviniendo INV-016.
2. `RefillTask.java` contiene un runnable vacío con un comentario, ya que toda la ejecución fue migrada a `RefillQueue`. Puede conservarse por retrocompatibilidad o removerse de forma limpia si no tiene dependientes.

---

## Persistence

Revisión del esquema SQLite (`data.db`):
- **Tablas**:
  - `loot_tables` (`id` PK, `display_name`, `description`, `enabled`, `min_items`, `max_items`).
  - `loot_entries` (`id` PK, `table_id` FK CASCADE, `item_data`, `weight`, `min_amount`, `max_amount`, `enabled`, `custom_type`, `command`).
  - `loot_pools` (`id` PK, `name`, `selection_mode`, `min_rolls`, `max_rolls`, `allow_duplicates`, `enabled`, `created_at`, `updated_at`).
  - `loot_pool_entries` (`id` PK, `pool_id` FK CASCADE, `table_id` FK CASCADE, `weight`).
  - `containers` (`id` PK, `world`, `x`, `y`, `z`, `container_type`, `loot_table_id`, `loot_pool_id`, `enabled`, `looted`, `last_loot`, `next_refill`, `refill_enabled`, `refill_interval_seconds`, `source`, `status`, `managed`, `registered`, `created_at`, `updated_at`).
  - `scan_jobs` (`id` PK, coordenadas actuales, progreso, contenedores encontrados, estado, timestamps).
  - `populate_jobs` (`id` PK, mundo, estado, contadores de procesados/poblados/saltados, timestamps).
- **Índices**:
  Cuenta con índices únicos sobre `(world, x, y, z)` y claves secundarias sobre `world`, `(world, x, z)`, `container_type`, `next_refill`, `loot_table_id`, `loot_pool_id`, `(source, status, managed)` y `idx_containers_refill_eligible`.
- **Migraciones**:
  `DatabaseManager` migra dinámicamente columnas faltantes con `PRAGMA table_info` y recrea la tabla `containers` si `next_refill` posee una restricción histórica `NOT NULL`.
- **Sincronización Memoria-SQLite**:
  `ContainerManager` utiliza dos mapas concurrentes (`containersById` y `containersByKey`). Las modificaciones individuales guardan sincrónicamente o asincrónicamente mediante `saveContainer()`, y los procesos masivos usan transacciones manuales con rollback (`conn.setAutoCommit(false)`).

---

## Commands

El comando principal `/loot` está implementado en `LootCommand.java`.

Subcomandos activos y verificados:
- `/loot admin`: Abre el menú principal (`AdminMenu`).
- `/loot wand`: Entrega la varita de selección de región.
- `/loot region <info|clear|scan|assign|assign-pool>`: Gestión completa de regiones.
- `/loot reload`: Recarga configuración YAML y tablas de loot/pools.
- `/loot scan <mundo> [status|pause|resume|cancel]`: Control del escáner de mundos.
- `/loot populate <mundo> [preview|confirm|cancel]`: Ejecución del llenado seguro.
- `/loot assign <mundo> <tipo> <tabla> [preview|confirm|--force]`: Asignación de tablas a contenedores MAP.
- `/loot assign-pool <mundo> <tipo> <pool> [preview|confirm|--force]`: Asignación de Loot Pools a contenedores MAP.
- `/loot refill [status|pause|resume|run|debug]`: Monitoreo y control del scheduler de refill.
- `/loot register <tabla>` (alias `/loot set`): Registra el contenedor apuntado con una tabla.
- `/loot register-pool <pool>` (alias `/loot set-pool`): Registra el contenedor apuntado con un pool.
- `/loot containers reset <mundo> [confirm]`: Reinicio seguro de contenedores de un mundo específico.
- `/loot container debug [id]`: Diagnóstico exhaustivo de un contenedor (ubicación, identidad, loot, next_refill, elegibilidad).
- `/loot refill debug`: Inspección técnica de colas internas por tick y estado de candidatos en SQLite.
- `/loot help`: Despliega la ayuda contextual.

Tab-completer: Totalmente implementado para todos los subcomandos, argumentos dinámicos de mundos, tipos de contenedor, tablas y pools.

---

## GUI

Las interfaces gráficas implementadas bajo `com.arcraft.lootrefill.gui` son:
1. `AdminMenu`: Panel central de control administrativo.
2. `ContainerMenu`: Navegación y consulta de contenedores registrados.
3. `LootTypesMenu`: Catálogo y administración de Loot Tables.
4. `LootEditorMenu`: Editor de parámetros y entradas de una tabla.
5. `LootAddItemMenu`: Selector interactivo de ítems desde el inventario del administrador con ajuste de pesos y cantidades.
6. `RefillMenu`: Panel de control de intervalos por tipo de contenedor, pausar/reanudar y ciclo manual.
7. `RefillConfigMenu`: Ajuste de condiciones de seguridad (`require-empty`, `radius`, `require-no-players-nearby`).
8. `RefillQueueMenu`: Visualización de contenedores en cola para refill.
9. `ScannerMenu`: Monitor visual y control del progreso del escaneo de chunks.
10. `PopulateMenu`: Panel para selección de mundo, vista previa y ejecución de populate.

**Seguridad de GUIs (INV-028 e INV-029)**:
- `GuiManager` cancela clics dobles (`ClickType.DOUBLE_CLICK`), clics superiores, arrastre de ítems (`InventoryDragEvent`) y shift-clicks hacia el menú.
- Solo `LootAddItemMenu` permite hacer clic en el inventario inferior del jugador para clonar el ítem sin retirarlo del inventario (`allowPlayerInventoryClick` cancela el evento).

**Inconsistencias Encontradas en GUIs**:
1. **Falta de panel GUI para Loot Pools**: No existe menú gráfico para listar, crear o editar Loot Pools; solo pueden gestionarse mediante `config.yml`, comandos (`/loot assign-pool`, `/loot register-pool`) o SQLite. En `AdminMenu` no hay acceso a Pools.
2. **Visualización de Loot Pools en `ContainerMenu` y `RefillQueueMenu`**:
   Si un contenedor tiene asignado un Loot Pool (`loot_pool_id != null` y `loot_table_id == null`), `ContainerMenu` muestra `"§7Loot Table: §enull"` y `RefillQueueMenu` muestra `"§7Tabla de Loot: §anull"`.

---

## Invariants

Evaluación estricta de cada regla definida en `SDD/INVARIANTS.md`:

| ID | Invariant | Status | Evidence |
|---|---|---|---|
| **INV-001** | PLAYER protection | **VALIDATED** | `ContainerBlockListener`, `ContainerRegistry`, `PopulateManager`, `RefillManager`, `WorldScanner` y `RegionScanner` excluyen estrictamente `source = PLAYER`. Solo `/loot region assign confirm-player` realiza la conversión administrativa intencional. |
| **INV-002** | BROKEN protection | **VALIDATED** | `ContainerPopulator` (L55) y `RefillManager` (L194) rechazan contenedores con `status = BROKEN`. `WorldScanner` no los reactiva. |
| **INV-003** | DISABLED protection | **VALIDATED** | Contenedores con `status = DISABLED` o `enabled = false` son omitidos en `ContainerPopulator` (L58) y consultas SQL (`status = 'ACTIVE'`). |
| **INV-004** | UNKNOWN protection | **VALIDATED** | Tratamiento conservador; descartados en consultas SQL (`source = 'MAP'`) y verificaciones en código. |
| **INV-005** | Never clear occupied inventories | **VALIDATED** | Con `require-empty: true`, si `!isInventoryEmpty(inv)` se retorna `SKIPPED_NOT_EMPTY`. `inv.clear()` nunca es invocado. |
| **INV-006** | Skip does not block queue | **VALIDATED** | En `PopulateQueue` se registra en estadísticas y continúa. En `RefillQueue` se aplica `retryCooldowns` en memoria (30s) sin bloquear la cola round-robin. |
| **INV-007** | LootPool priority | **PARTIAL** | En `ContainerPopulator` y `RefillManager` el pool tiene prioridad sobre la tabla (`if pool != null ... else if table != null`). Sin embargo, en `PopulateManager` los contenedores con solo pool son excluidos de la lista de candidatos antes de llegar al populator. |
| **INV-008** | Legacy compatibility | **VALIDATED** | Contenedores con `loot_pool_id == null` y `loot_table_id != null` funcionan correctamente en todos los flujos. |
| **INV-009** | No configuration means no loot | **VALIDATED** | Si ambos IDs son nulos, `ContainerPopulator` y `RefillManager` retornan `SKIPPED_NO_LOOT_TABLE` sin modificar el inventario. |
| **INV-010** | Relative weights | **VALIDATED** | `LootGenerator` y `LootPoolManager` calculan el acumulado real `totalWeight` sin requerir que sume 100. |
| **INV-011** | SINGLE_RANDOM | **VALIDATED** | `LootPoolManager.selectTables` selecciona exactamente 1 tabla válida ponderada. |
| **INV-012** | MIXED_RANDOM default | **VALIDATED** | `allow_duplicates = false` por defecto en constructores, migración SQLite y `config.yml`. |
| **INV-013** | MIXED_RANDOM without replacement | **VALIDATED** | Cuando `allow_duplicates = false`, las tablas seleccionadas se eliminan de los candidatos (`poolCandidates.remove(picked)`) y se descuenta su peso. |
| **INV-014** | MIXED_RANDOM with replacement | **VALIDATED** | Cuando `allow_duplicates = true`, se seleccionan entradas con reemplazo en un bucle iterativo. |
| **INV-015** | Invalid Pool safety | **VALIDATED** | Pools nulos, vacíos o deshabilitados retornan `SKIPPED_INVALID_LOOT_POOL` de forma segura sin borrar datos ni crashear. |
| **INV-016** | Config interval source of truth | **PARTIAL** | El núcleo (`RefillManager`, `ContainerPopulator`, `ContainerRegistry`) lee `config.yml` vía `getIntervalForType()`. Sin embargo, `RefillQueueMenu.java` (L81) utiliza el valor almacenado `container.getRefillIntervalSeconds()`. |
| **INV-017** | next_refill persistence | **VALIDATED** | Los cambios en `next_refill` se guardan en SQLite mediante `saveContainer` / `saveContainersBatch` y sobreviven a reinicios. |
| **INV-018** | Due detection | **VALIDATED** | `isDue()` y las consultas SQL de refill filtran estrictamente `next_refill IS NOT NULL AND next_refill <= ?`. |
| **INV-019** | Automatic refill eligibility | **VALIDATED** | Verificado en `isEligibleForRefill()`, `getContainersDueForRefill()` y validaciones de `RefillManager`. |
| **INV-020** | Progressive processing | **VALIDATED** | Procesamiento por lotes y tiempo en `WorldScanner`, `PopulateQueue`, `RefillQueue` y `RegionScanner`. |
| **INV-021** | Per-tick limits | **VALIDATED** | Se respetan `max-ms-per-tick` y límites de contenedores/chunks por tick midiendo con `System.nanoTime()`. |
| **INV-022** | Controlled chunks | **VALIDATED** | No hay carga masiva sincrónica. Se usa `getChunkAtAsync()` respetando `maxChunksLoadedByRefill` y se descargan los chunks procesados. |
| **INV-023** | Efficient scanning | **VALIDATED** | `RegionFileScanner` analiza cabeceras MCA binarias sin cargar bloques. `WorldScanner` y `RegionScanner` solo leen tile entities. |
| **INV-024** | Inventory operations | **PARTIAL** | `RefillQueue` y `RegionScanner` encapsulan callbacks asíncronos en `runTask()`. `PopulateQueue` omite `runTask()` en `getChunkAtAsync().thenAccept()`, arriesgando ejecuciones fuera del main thread. |
| **INV-025** | Async database | **VALIDATED** | Operaciones pesadas (escaneo MCA, inserciones por lote, búsquedas) se ejecutan asíncronamente sin congelar el hilo del servidor. |
| **INV-026** | SQLite persistence | **VALIDATED** | Todas las entidades y trabajos (`scan_jobs`, `populate_jobs`) persisten en `data.db` y se recargan en `onEnable()`. |
| **INV-027** | Migration safety | **VALIDATED** | Verificado en `DatabaseManager.migrateContainersTable()` y `migrateLootPoolsTable()`. |
| **INV-028** | Administrative permission | **VALIDATED** | `lootrefill.admin` se valida en comandos, interacción con varita, apertura de menú y clics en `GuiManager`. |
| **INV-029** | GUI protection | **VALIDATED** | Cancelación estricta de clics, arrastres, doble clic y shift-clicks en `GuiManager`. |
| **INV-030** | Existing command compatibility | **VALIDATED** | Todos los comandos documentados existen y mantienen su sintaxis y retrocompatibilidad. |
| **INV-031** | Module separation | **VALIDATED** | Los 11 paquetes mantienen responsabilidades delimitadas y claras. |
| **INV-032** | No unnecessary rewrites | **VALIDATED** | La base de código conserva las etapas anteriores intactas. |

---

## Recommended Documentation Changes

1. **Actualizar `SDD/PROJECT_SPEC.md`**:
   - En la Sección 5 (*Container Model*), documentar formalmente la lista de historial reciente `recentSelections` presente en `LootContainer`.
   - En la Sección 13 (*Performance*), especificar la existencia del semáforo de reintento temporal en memoria (`retryCooldowns` / 30 segundos) para contenedores omitidos, clarificando que no se modifica `next_refill` al ocurrir una omisión por jugador cercano o cofre lleno.
2. **Actualizar `SDD/ARCHITECTURE.md`**:
   - En la Sección 6 (*Populate*), detallar que la cola de Populate utiliza `processedDoubleChests` para la deduplicación física de cofres dobles, al igual que Refill.
   - En la Sección 10 (*Region System*), documentar los comandos `/loot region assign confirm-player` y `/loot region assign-pool <pool>`.
   - En la Sección 14 (*GUI Layer*), listar formalmente los 10 menús existentes e indicar que la administración de Loot Pools actualmente no dispone de menú GUI propio, operando vía comandos y configuración.

---

## Recommended Code Changes

*(Lista de problemas detectados durante la auditoría para su resolución futura; NO implementados en esta auditoría)*:

1. **Habilitar compatibilidad de Loot Pools en `PopulateManager`**:
   - Modificar las condiciones de filtrado en `PopulateManager.showPreview()` y `PopulateManager.startPopulate()` para incluir contenedores que tengan `loot_pool_id != null` o `loot_table_id != null` (utilizando `c.hasLootConfigured()`), resolviendo la violación parcial de **INV-007**.
2. **Prevenir borrado de `loot_pool_id` en `WorldScanner`**:
   - En `WorldScanner.inspectChunk()`, llamar al constructor completo de `LootContainer` pasando `existing != null ? existing.getLootPoolId() : null`, evitando que un re-escaneo del mundo elimine los Loot Pools asignados en SQLite.
3. **Limpieza estricta de `loot_pool_id` al registrar contenedores PLAYER**:
   - En `ContainerRegistry.registerPlayerBlock()`, invocar `existing.setLootPoolId(null)` cuando se sobreescribe una coordenada previa.
4. **Garantizar ejecución en el Main Thread en `PopulateQueue`**:
   - En `PopulateQueue.processContainer()`, encapsular la ejecución dentro de `Bukkit.getScheduler().runTask(plugin, () -> ...)` tras la resolución de `world.getChunkAtAsync()`, satisfaciendo al 100% **INV-024**.
5. **Corrección de fuente de verdad en `RefillQueueMenu`**:
   - En `RefillQueueMenu.java` (línea 81), sustituir `container.getRefillIntervalSeconds()` por `plugin.getRefillManager().getIntervalForType(container.getContainerType())`, respetando **INV-016**.
6. **Manejo visual de Loot Pools en GUIs**:
   - En `ContainerMenu.java` y `RefillQueueMenu.java`, actualizar el formateo del ítem para mostrar el Loot Pool activo si `loot_pool_id` está presente, en lugar de mostrar `§anull`.
   - Considerar la implementación futura de un menú GUI para la gestión de Loot Pools.

---

## Audit Resolution

### AUDIT-001 — Populate LootPool

Status: FIXED (Validated in production/server)

The Populate candidate filtering now recognizes containers configured through `loot_pool_id` as valid loot containers while preserving legacy `loot_table_id` support.
Functional verification confirmed on server: containers configured solely with `loot_pool_id` appear in `/loot populate <world> preview`, generate expected loot upon execution, and persist their state correctly while maintaining legacy `loot_table_id` compatibility.

### AUDIT-002 — WorldScanner LootPool preservation

Status: FIXED (Validated in production/server)

World rescanning preserves existing LootPool configuration and other administrative container state.
Functional verification confirmed on server: after executing a full world scan, existing containers with `loot_pool_id` retain their pool configuration in SQLite and memory without reverting to NULL, preserving all administrative flags (MAP origin, ACTIVE status, refill timers).

### AUDIT-003 — Populate thread safety

Status: FIXED (Validated in production/server)

Bukkit/Paper inventory modifications are performed on the appropriate server thread while keeping the existing asynchronous/progressive architecture.
Functional verification confirmed on server: batch population across multiple chunks executed cleanly without `AsyncCatcher` warnings, Bukkit thread concurrency violations, inventory corruption, or server lag spikes.

### AUDIT-004 — RefillQueueMenu interval source

Status: FIXED (Validated in production/server)

Refill postponement uses the active `config.yml` interval instead of the historical database interval.
Functional verification confirmed on server: modifying interval settings in `config.yml` (e.g. 10s) and postponing a refill through `RefillQueueMenu` immediately calculates `next_refill` using the newly configured interval rather than the historical database value.
