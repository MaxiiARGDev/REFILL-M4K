# Container Manager 2.0 — Feasibility Audit

## Executive Summary

Esta auditoría técnica evalúa la factibilidad del diseño especificado en `SDD/CONTAINER_MANAGER_2.md` frente a la implementación real del repositorio `LootRefill` (Java 21, Paper 1.21.4, SQLite).

El resultado global del análisis es **ALTAMENTE FACTIBLE Y COMPATIBLE**.
La base del plugin ya cuenta con las entidades centrales (`LootContainer`, `LootPool`, `LootPoolEntry`), esquemas completos en SQLite con índices avanzados, y los motores de selección ponderada e integración de loot en Populate y Refill.

Sin embargo, el diseño propuesto en `CONTAINER_MANAGER_2.md` asume erróneamente que ciertas características ya tienen UI o endpoints interactivos, y omite consideraciones de concurrencia y paginación en SQLite. Concretamente:
1. **LootPool Backend**: Existe al 85% (`LootPoolManager`, `LootPoolStorage`, tabla `loot_pools` y `loot_pool_entries`). Carece al 100% de interfaz gráfica (GUIs) para crear, editar, asociar tablas o ajustar pesos/rolls interactivamente.
2. **Container Manager**: Existe al 40% (`ContainerMenu.java` básico con paginación en memoria de 28 slots, asignación básica de tabla y toggling). Carece de paginación SQLite dinámica (`LIMIT/OFFSET`), filtrado multidimensional, búsqueda textual y vista detallada/inspector.
3. **Persistencia**: El esquema SQLite actual (`DatabaseManager`) es robusto y **NO requiere migraciones estructurales obligatorias** para cumplir con la etapa; ya contiene todas las columnas (`loot_pool_id`, `allow_duplicates`, `managed`, `registered`, `source`, `status`, `next_refill`, etc.).

---

## Current Implementation

| Componente | Estado Actual | Ubicación |
|---|---|---|
| `ContainerManager` | Activo | [ContainerManager.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/container/ContainerManager.java) |
| `ContainerRegistry` | Activo | [ContainerRegistry.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/container/ContainerRegistry.java) |
| `ContainerMenu` | Básico | [ContainerMenu.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/gui/ContainerMenu.java) |
| `LootPoolManager` | Activo (Backend) | [LootPoolManager.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/pool/LootPoolManager.java) |
| `LootPoolStorage` | Activo (SQLite) | [LootPoolStorage.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/pool/LootPoolStorage.java) |
| `DatabaseManager` | Completo | [DatabaseManager.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/storage/DatabaseManager.java) |
| Comandos Container/Pool | Parcial | [LootCommand.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/command/LootCommand.java) |
| GUI Loot Pools | **Inexistente** | - |

---

## LootPool Audit

### Lo que Ya Existe
- **Modelo de Datos**: `LootPool.java` y `LootPoolEntry.java` soportan `id`, `name`, `selection_mode`, `min_rolls`, `max_rolls`, `allow_duplicates`, `enabled`, timestamps y relación con `table_id` y `weight`.
- **Almacenamiento**: Tablas `loot_pools` y `loot_pool_entries` en SQLite con transacciones atómicas de guardado (`savePool`), carga (`loadAllPools`) y borrado (`deletePool`).
- **Modos de Selección**:
  - `SINGLE_RANDOM`: Selección ponderada estocástica de exactamente 1 tabla (INV-011).
  - `MIXED_RANDOM`: Rolls entre `min` y `max`. Soporta `allow_duplicates = false` (sin reemplazo, INV-013) y `allow_duplicates = true` (con reemplazo, INV-014).
- **Pesos Relativos**: Algoritmo `selectWeightedEntry` con suma acumulativa real sin forzar suma 100 (INV-010).
- **Seguridad**: Si un pool es inválido o no tiene tablas, retorna lista vacía y `ContainerPopulator` / `RefillManager` retornan `SKIPPED_INVALID_LOOT_POOL` (INV-015).
- **Historial**: `LootContainer.recentSelections` almacena en un `ConcurrentLinkedDeque` en memoria las últimas 5 tablas seleccionadas.

### Lo que Falta
- No hay interfaz gráfica (GUI) para listar pools, crearlos, modificar rolls, alternar `enabled`, cambiar modo, añadir/quitar tablas o editar pesos.
- En comandos de consola/chat solo existen `/loot assign-pool` y `/loot register-pool`. No hay comandos CLI para crear o editar pools (solo cargados desde `config.yml` en arranque inicial si SQLite está vacío).
- Persistencia del historial de selecciones: es exclusivamente en memoria (runtime).

---

## Container Manager Audit

### Lo que Ya Existe
- Representación unificada en `LootContainer.java` con todas las banderas requeridas.
- En `ContainerMenu.java`:
  - Listado paginado de 28 ítems en slots centrales.
  - Indicadores visuales de origen (`MAP`, `PLAYER`), estado (`ACTIVE`, `BROKEN`, `DISABLED`), administración y refill.
  - Formateo seguro de loot: `[Pool] <id>`, `<tabla>` o `"Sin Loot configurado"`.
  - Acción clic izquierdo para asignar tabla de loot vía `ChatInputHandler`.
  - Acción clic derecho para alternar habilitado (`enabled`).
  - Shift + Clic para desregistrar.

### Lo que Falta
- **Paginación en Memoria vs Base de Datos**: `ContainerMenu` vuelca `new ArrayList<>(containerManager.getAllContainers())` en memoria. Si el servidor tiene 50,000 contenedores, esto causará presión en el Garbage Collector y lag spikes. Debe implementarse paginación y conteo condicional en SQLite.
- **Filtros**: No existe ningún filtro (por mundo, tipo, origen, estado, tipo de loot, estado de refill).
- **Búsqueda**: No existe búsqueda textual por ID, coordenadas o nombres de tablas/pools.
- **Vista de Detalle / Inspector**: Al hacer clic en un contenedor, se reacciona directamente sin abrir una ficha técnica detallada (Container Detail / Debug GUI).
- **Asignación de LootPool en GUI**: Solo permite asignar Loot Tables legacy mediante chat; no hay selector ni asignación de Loot Pools.
- **Confirmaciones**: El desregistro por Shift+Click es inmediato, sin pantalla de confirmación.

---

## SQLite Audit

### Tablas y Columnas Existentes
1. **`containers`**:
   - Columnas: `id` (PK), `world`, `x`, `y`, `z`, `container_type`, `loot_table_id`, `loot_pool_id`, `enabled`, `looted`, `last_loot`, `next_refill`, `refill_enabled`, `refill_interval_seconds`, `source`, `status`, `managed`, `registered`, `created_at`, `updated_at`.
   - Índices: `idx_containers_loc` (UNIQUE), `idx_containers_world`, `idx_containers_world_xz`, `idx_containers_type`, `idx_containers_next_refill`, `idx_containers_loot_table`, `idx_containers_loot_pool`, `idx_containers_source_status`, `idx_containers_refill_eligible`.
2. **`loot_pools`**:
   - Columnas: `id` (PK), `name`, `selection_mode`, `min_rolls`, `max_rolls`, `allow_duplicates`, `enabled`, `created_at`, `updated_at`.
3. **`loot_pool_entries`**:
   - Columnas: `id` (PK), `pool_id` (FK CASCADE), `table_id` (FK CASCADE), `weight`.

### Evaluación de Migraciones
- **¿Se requiere migración de esquema?**: **NO**.
- El esquema ya está 100% alineado con las especificaciones de `CONTAINER_MANAGER_2.md`.
- No es necesario agregar columnas ni alterar tablas. Solo se requiere agregar métodos de consulta optimizados con `LIMIT` y `OFFSET` y filtros dinámicos en `ContainerManager` / `DatabaseManager`.

---

## Populate Integration

- `PopulateManager.java` y `ContainerPopulator.java` ya manejan la dualidad `LootPool` > `LootTable`.
- Si `ContainerManager 2.0` agrega una acción para forzar populate de un contenedor individual:
  - **REUTILIZAR DIRECTAMENTE**: `ContainerPopulator.populate(container)`.
  - **PROHIBIDO**: Crear generadores de ítems o lógicas alternativas de asignación de bloques.
  - La verificación física (`require-empty`, validación de TileEntity y chunk) debe permanecer encapsulada en `ContainerPopulator`.

---

## Refill Integration

- `RefillManager.java` ya dispone del cálculo de intervalos en vivo según `config.yml` (`getEffectiveIntervalForContainer(container)`).
- La visualización en el Container Detail debe consultar directamente:
  - `refillManager.getEffectiveIntervalForContainer(c)` para el intervalo efectivo.
  - `c.isDue()` y `c.isEligibleForRefill()` para el estado operativo.
  - `plugin.getRefillManager().refillContainer(c)` si se ofrece acción manual de refill.
- No se debe duplicar la lógica de cálculo temporal ni de validación de jugadores cercanos/inventario vacío.

---

## GUI Audit

- **Infraestructura Base**:
  - `MenuHolder.java`: Clase base abstracta con slots, bordes, asignación de callbacks por ítem y prevención de exploits.
  - `GuiManager.java`: Cancelación estricta de clics fuera de rango, clics dobles, arrastres y validación obligatoria del permiso `lootrefill.admin`.
  - `ChatInputHandler.java`: Captura de texto vía chat Bukkit con timeout y reapertura automática del menú.
- **Estrategia Recomendada**:
  - **Nuevas GUIs requeridas**:
    1. `LootPoolsMenu.java`: Lista paginada de pools con botón de crear.
    2. `LootPoolEditorMenu.java`: Edición de propiedades de un pool específico (nombre, modo, rolls, estado).
    3. `LootPoolMembersMenu.java`: Gestión de tablas del pool, pesos, añadir y eliminar.
    4. `ContainerDetailMenu.java`: Vista técnica e inspectora del contenedor con botones de acción (asignar pool/tabla, alternar refill, ejecutar refill/populate forzado, desregistrar con confirmación).
    5. `ContainerFilterMenu.java`: Selector visual para aplicar filtros combinados a la búsqueda de contenedores.
    6. `ConfirmationMenu.java`: GUI genérica y reutilizable de confirmación (`CONFIRMAR` / `CANCELAR`) para acciones destructivas o críticas (borrar pool, desregistrar container, PLAYER -> MAP).
  - **GUI a Extender**:
    - `ContainerMenu.java`: Transformarla en el visualizador del Container Manager 2.0 integrando filtros y navegación hacia `ContainerDetailMenu`.

---

## Security Audit

- **Permisos**: Todas las aperturas y clics en `GuiManager` ya validan `player.hasPermission("lootrefill.admin")`. Cualquier nuevo menú heredado de `MenuHolder` heredará automáticamente esta protección.
- **Acciones Críticas**:
  - `PLAYER -> MAP`: El SDD prohíbe conversiones accidentales. Si se ofrece en `ContainerDetailMenu`, debe pasar obligatoriamente por `ConfirmationMenu`.
  - `BROKEN -> ACTIVE`: Debe continuar prohibido en la GUI. Los contenedores rotos solo pueden ser inspeccionados o desregistrados.
  - `DISABLED`: Permitir habilitación/deshabilitación solo mediante acción administrativa explícita.
  - `Eliminar Pool`: Requiere confirmación y actualización transaccional de los contenedores que lo referenciaban (`UPDATE containers SET loot_pool_id = NULL WHERE loot_pool_id = ?`).

---

## Thread Safety

- **Acceso a Inventarios / Bukkit API**: **EXCLUSIVAMENTE EN EL MAIN THREAD**.
  - La apertura de inventarios, clics, renderizado de ItemStacks y consulta de bloques del mundo deben ejecutarse en el hilo principal.
- **Consultas de Datos (SQLite)**:
  - Cuando se filtren o paginen miles de registros, la consulta SQL (`SELECT ... LIMIT ? OFFSET ?`) puede ejecutarse de forma asíncrona, abriendo o actualizando el menú en el main thread mediante `Bukkit.getScheduler().runTask(plugin, () -> ...)`.
- **Carga de Chunks**: **PROHIBIDA EN GUI**. La GUI administrativa bajo ninguna circunstancia debe invocar `world.loadChunk()` ni `world.getChunkAt()`.

---

## Performance

- **Problema de Escala**: El código actual de `ContainerManager.getAllContainers()` retorna la colección completa de memoria. Para 10,000+ contenedores esto es ineficiente si se busca o filtra en cada clic.
- **Solución Técnica**:
  - Diseñar un método de búsqueda paginada:
    ```sql
    SELECT * FROM containers
    WHERE (world = ? OR ? IS NULL)
      AND (container_type = ? OR ? IS NULL)
      AND (source = ? OR ? IS NULL)
      AND (status = ? OR ? IS NULL)
    ORDER BY created_at DESC
    LIMIT ? OFFSET ?;
    ```
  - Los índices ya existentes en `DatabaseManager` (`idx_containers_world`, `idx_containers_type`, `idx_containers_source_status`) garantizan que estas consultas se resuelvan en sub-milisegundos en SQLite.

---

## Invariants Evaluation

| Invariante | Descripción | Estado frente al nuevo SDD |
|---|---|---|
| **INV-001** | PLAYER protection | **OK**: No se permite conversión automática. |
| **INV-002** | BROKEN protection | **OK**: Se mantiene bloqueada la reactivación automática. |
| **INV-003** | DISABLED protection | **OK**: No recibe refill/populate; solo toggling manual admin. |
| **INV-007** | LootPool priority | **OK**: Pool tiene prioridad sobre LootTable en asignación y visualización. |
| **INV-008** | Legacy compatibility | **OK**: Si `loot_pool_id == null`, la tabla legacy permanece activa. |
| **INV-010** - **INV-015** | Reglas de LootPool | **OK**: El motor actual ya cumple la totalidad de invariantes de pool. |
| **INV-016** | Config interval source | **OK**: El detalle debe leer `config.yml` vía `RefillManager`. |
| **INV-024** | Main thread Bukkit operations | **OK**: La GUI no interactuará con el mundo de forma asíncrona. |
| **INV-029** | GUI protection | **OK**: Se preserva `GuiManager` y la cancelación de eventos. |

---

## SDD Issues & Observaciones Previas

1. **Ambigüedad en Borrado de Pools (Sección 24)**:
   - El SDD propone: `Pool eliminado -> containers con ese pool -> loot_pool_id = NULL`.
   - *Aclaración necesaria*: En SQLite, `loot_pool_entries` tiene `ON DELETE CASCADE` contra `loot_pools(id)`, pero en `containers` no existe clave foránea explícita para evitar bloqueos si el contenedor fue escaneado antes. Por tanto, el borrado de un Pool debe ejecutar un update explícito: `UPDATE containers SET loot_pool_id = NULL WHERE loot_pool_id = ?`.
2. **Persistencia de Historial (Secciones 31 y 32)**:
   - Actualmente `LootContainer.recentSelections` es un deque volátil en memoria (últimas 5 selecciones).
   - El SDD plantea si se requiere tabla en SQLite.
   - *Recomendación*: **Mantenerlo en memoria**. Persistir cada roll individual en disco para miles de cofres generaría escrituras excesivas sin beneficio administrativo real.
3. **Paginación en Memoria vs SQLite (Secciones 8 y 39)**:
   - El código actual de `ContainerManager` mantiene un mapa concurrente en memoria (`containersById`).
   - Para un volumen moderado (hasta 5,000 contenedores), filtrar en streams en memoria es extremadamente rápido (< 2ms) y no toca disco.
   - Para volúmenes mayores (10,000+), es recomendable añadir la opción de filtrado directo por consulta SQLite.

---

## Proposed Architecture

```text
                           ┌────────────────────────┐
                           │       AdminMenu        │
                           └───────────┬────────────┘
                                       │
                ┌──────────────────────┴──────────────────────┐
                ▼                                             ▼
     ┌─────────────────────┐                       ┌─────────────────────┐
     │  ContainerMenu 2.0  │                       │    LootPoolsMenu    │
     │  (Lista, Filtros,   │                       │  (Catálogo de Pools)│
     │   Paginación)       │                       └──────────┬──────────┘
     └──────────┬──────────┘                                  │
                ▼                                             ▼
     ┌─────────────────────┐                       ┌─────────────────────┐
     │ ContainerDetailMenu │                       │  LootPoolEditorMenu │
     │ (Ficha, Loot, Admin)│                       │  (Props, Tablas)    │
     └──────────┬──────────┘                       └──────────┬──────────┘
                │                                             │
                ├─────────────────────────────────────────────┘
                ▼
     ┌─────────────────────┐
     │  ConfirmationMenu   │  (Operaciones críticas: Borrado, PLAYER->MAP)
     └──────────┬──────────┘
                │
                ▼
     ┌─────────────────────┐        ┌──────────────────────┐
     │  ContainerManager   │◄──────►│   LootPoolManager    │
     └──────────┬──────────┘        └──────────┬───────────┘
                │                              │
                └──────────────┬───────────────┘
                               ▼
                    ┌──────────────────────┐
                    │   DatabaseManager    │ (data.db SQLite)
                    └──────────────────────┘
```

---

## Proposed Implementation Phases

### Fase 1: Backend de Soporte para Consultas y Filtrado
- Agregar en `ContainerManager` métodos de filtrado y búsqueda (por mundo, tipo, estado, origen, loot y coincidencia de texto).
- Implementar en `LootPoolManager` el método transaccional de desvinculación segura al eliminar un pool (`clearPoolFromContainers(poolId)`).

### Fase 2: Loot Pool Manager GUI
- Implementar `LootPoolsMenu`: visualización de todos los pools registrados, estado y total de entradas.
- Implementar `LootPoolEditorMenu`: edición de nombre, modo (`SINGLE_RANDOM` / `MIXED_RANDOM`), rolls y estado.
- Implementar `LootPoolMembersMenu`: adición de tablas existentes, ajuste de peso con `ChatInputHandler` y eliminación de membresía.
- Conectar `LootPoolsMenu` en el botón slot 21 o disponible de `AdminMenu`.

### Fase 3: Confirmation GUI Reutilizable
- Implementar `ConfirmationMenu`: diálogo de seguridad de doble botón (`CONFIRMAR` verde, `CANCELAR` rojo) parametrizable con mensaje explicativo y callbacks de acción.

### Fase 4: Container Manager 2.0 — Lista y Filtros
- Actualizar `ContainerMenu`:
  - Agregar botón de Filtros (`ContainerFilterMenu`).
  - Agregar botón de Búsqueda por chat (`ChatInputHandler`).
  - Soporte de navegación limpia y paginada.

### Fase 5: Container Manager 2.0 — Vista de Detalle y Asignación
- Implementar `ContainerDetailMenu`:
  - Renderizado completo de la ficha técnica (diagnóstico `isDue`, `isEligibleForRefill`, intervalos, origen, estado).
  - Botón interactivo "Asignar Loot Pool": abre selector de pools y aplica la asignación con confirmación.
  - Botón "Quitar Loot Pool": revierte a LootTable legacy o Sin Loot.
  - Botón "Acción de Refill": ejecuta `refillContainer(c)` de forma segura.

### Fase 6: Pruebas Funcionales y Validación
- Ejecución de los tests obligatorios `CM-001` a `CM-019` descritos en el SDD.
- Verificación de ausencia de advertencias y compilación limpia con `mvn clean package`.

---

## New Classes Potentially Required

1. `com.arcraft.lootrefill.gui.LootPoolsMenu` (Gestor de Pools).
2. `com.arcraft.lootrefill.gui.LootPoolEditorMenu` (Editor de propiedades de Pool).
3. `com.arcraft.lootrefill.gui.LootPoolMembersMenu` (Editor de tablas y pesos).
4. `com.arcraft.lootrefill.gui.ContainerDetailMenu` (Ficha técnica del contenedor).
5. `com.arcraft.lootrefill.gui.ContainerFilterMenu` (Selector de filtros para la lista).
6. `com.arcraft.lootrefill.gui.ConfirmationMenu` (Confirmaciones seguras de acciones críticas).
7. `com.arcraft.lootrefill.container.ContainerFilter` (Objeto POJO para almacenar criterios activos de filtro y búsqueda).

---

## Existing Classes To Reuse

- [MenuHolder.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/gui/MenuHolder.java): Base para todos los nuevos menús.
- [GuiManager.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/gui/GuiManager.java): Manejador central de clics, permisos y `ChatInputHandler`.
- [ContainerManager.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/container/ContainerManager.java): Proveedor de contenedores y persistencia.
- [LootPoolManager.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/pool/LootPoolManager.java): Operaciones CRUD de pools y cálculo estocástico.
- [LootPoolStorage.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/pool/LootPoolStorage.java): Persistencia atómica de pools en SQLite.
- [RefillManager.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/refill/RefillManager.java): Fuente de verdad para intervalos y acciones forzadas de refill.
- [ContainerPopulator.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/populate/ContainerPopulator.java): Población segura de contenedores.
- [AdminMenu.java](file:///c:/Users/rataatrevida/Desktop/REFILL-M4K/src/main/java/com/arcraft/lootrefill/gui/AdminMenu.java): Enlace principal a los nuevos menús.

---

## Database Changes Potentially Required

- **Ninguno a nivel DDL**: Las tablas `containers`, `loot_pools` y `loot_pool_entries` ya contienen todos los campos y relaciones necesarias.
- **Nivel DML / Queries**: Se requerirá una sentencia de desvinculación al eliminar un pool:
  ```sql
  UPDATE containers SET loot_pool_id = NULL WHERE loot_pool_id = ?;
  ```

---

## Risks

1. **Riesgo de Bloqueo por Búsqueda Sincrónica**:
   - Si la búsqueda de contenedores o pools se realiza en SQLite bloqueando el main thread, un texto amplio con comodines (`LIKE '%...%'`) podría congelar el tick.
   - *Mitigación*: Mantener búsquedas en memoria sobre `containersById` mientras el tamaño del mapa sea menor a 10,000 registros; o despachar consultas pesadas a `Bukkit.getScheduler().runTaskAsynchronously()` antes de reabrir el menú.
2. **Riesgo de Infracción de Invariantes al Desregistrar**:
   - Desregistrar un contenedor `MAP` sin confirmación puede dejar botín huérfano o re-descubrirlo como nuevo en el próximo escaneo.
   - *Mitigación*: Uso obligatorio de `ConfirmationMenu`.
3. **Riesgo de Corrupción de Duplicados en Pools**:
   - Al agregar una tabla al pool que ya existía, se debe actualizar el peso en lugar de insertar una entrada duplicada.

---

## Final Recommendation

1. **Aprobar la implementación de `SDD/CONTAINER_MANAGER_2.md`** con la aclaración de mantener el historial de selección en memoria (sin crear nuevas tablas SQLite) y garantizar que la desvinculación de pools eliminados sea transaccional.
2. Proceder según el plan de 6 fases escalonadas, validando cada fase con pruebas funcionales y compilación Maven antes de avanzar a la siguiente.

---

## 12. Resultados de Implementación y Cierre (Fases 1 a 6)

- **Fase 1 (Backend de Filtros y Paginación)**: Implementado en `ContainerFilter`, `ContainerSortField`, `LootFilterType` y `ContainerManager`. Pruebas unitarias añadidas.
- **Fase 2 (Loot Pool Manager GUI)**: Implementado en `LootPoolsMenu`, `LootPoolEditorMenu`, `LootPoolMembersMenu`.
- **Fase 3 (ConfirmationMenu)**: Implementado en `ConfirmationMenu` con hooks en `MenuHolder` y `GuiManager`.
- **Fase 4 (Container Manager 2.0 GUI)**: Implementado en `ContainerMenu` (toolbar, live pagination, ordenamiento dinámico) y `ContainerFilterMenu` con submenús selectores.
- **Fase 5 (ContainerDetailMenu)**: Implementado en `ContainerDetailMenu` con acciones en vivo, conversión `PLAYER → MAP` protegida, `RefillManager.refillContainer`, `ContainerPopulator.populate` y desregistro con confirmación.
- **Fase 6 (Cierre y Auditoría)**:
  - Auditoría formal de invariantes `INV-001` a `INV-019`: **100% CUMPLIDAS**.
  - Total de tests automatizados pasando: **37 tests** (0 fallos, 0 errores).
  - Estado de compilación: `mvn clean package` → `BUILD SUCCESS`.
  - Integridad arquitectónica garantizada sin migraciones DDL ni sobrecarga de chunks.
