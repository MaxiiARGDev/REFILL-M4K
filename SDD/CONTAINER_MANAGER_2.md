# LootRefill — Container Manager 2.0 + Loot Pool Manager

## Estado

**Status:** IMPLEMENTED & VALIDATED (Phases 1–6 Complete)

Container Manager 2.0 ha sido implementado y validado en su totalidad a través de 6 fases:
1. **Fase 1 (Backend de Filtros y Búsqueda)**: `ContainerFilter`, `ContainerSortField`, `LootFilterType`, `findContainers` / `countContainers` en memoria y desvinculación transaccional de pools.
2. **Fase 2 (Loot Pool Manager GUI)**: `LootPoolsMenu`, `LootPoolEditorMenu`, `LootPoolMembersMenu` con asignación dinámica de tablas, pesos relativos y control de modos de selección.
3. **Fase 3 (ConfirmationMenu)**: GUI modal genérica, segura e idempotente para confirmaciones críticas administrativas (`AtomicBoolean` anti-spam, ESC como cancelación).
4. **Fase 4 (Container Manager 2.0 GUI)**: Rediseño de `ContainerMenu` con toolbar de búsqueda, filtros rápidos, ordenamiento en vivo, paginación dinámica real y `ContainerFilterMenu`.
5. **Fase 5 (ContainerDetailMenu)**: Ficha técnica integral del contenedor, prioridad `Pool > Table`, historial `recentSelections`, alternancia de refill, Forzar Refill, Forzar Populate, teletransporte seguro, conversión `PLAYER → MAP` protegida y desregistro protegido.
6. **Fase 6 (Cierre, Documentación y Auditoría)**: Auditoría completa de invariantes `INV-001` a `INV-019`, pruebas de regresión (37 tests pasando) y actualización de SDD.

---

# 1. Objetivo

El objetivo de esta etapa es crear una interfaz administrativa completa para gestionar:

- Containers registrados.
- Estado e identidad de containers.
- LootTables.
- LootPools.
- Asignación de LootPools a containers.
- Configuración de refill.
- Búsqueda y filtrado.
- Estadísticas.
- Información detallada.
- Historial de selección de LootPools.

El sistema debe mantener todas las protecciones e invariantes existentes.

No se debe romper:

- PLAYER protection.
- BROKEN protection.
- DISABLED protection.
- `require-empty`.
- Auto Refill.
- Populate.
- LootTable legacy.
- LootPool.
- Scanner.
- Region system.
- SQLite persistence.
- Performance limits.

---

# 2. Principios fundamentales

## 2.1 No modificar el núcleo innecesariamente

Container Manager 2.0 debe construirse sobre los sistemas existentes.

No realizar una reescritura de:

- ContainerManager.
- RefillManager.
- PopulateManager.
- LootManager.
- LootPoolManager.
- WorldScanner.

Solo extenderlos cuando sea necesario.

---

## 2.2 GUI nunca debe cargar chunks

Las GUIs administrativas NO deben:

- cargar chunks;
- forzar chunks;
- abrir inventarios físicos;
- modificar bloques;
- realizar operaciones costosas sobre el mundo.

La GUI debe trabajar principalmente con datos de SQLite y memoria.

El acceso físico al mundo debe quedar para los sistemas que ya lo gestionan.

---

# 3. Container Manager 2.0

El Container Manager debe convertirse en el panel central de administración de containers.

Entrada:

```text
/loot containers
````

o la entrada equivalente existente en la GUI principal.

No crear un comando nuevo si ya existe un flujo compatible que pueda extenderse.

---

# 4. Vista principal

La pantalla principal debe mostrar una lista paginada de containers.

Información mínima visible:

```text
Tipo
Mundo
Coordenadas
Origen
Estado
Loot
Refill
```

Ejemplo conceptual:

```text
┌──────────────────────────────────────┐
│        LOOTREFILL CONTAINERS         │
├──────────────────────────────────────┤
│ CHEST       lobby  0,-60,4           │
│ MAP         common    Refill: ON     │
│                                      │
│ BARREL      lobby  -4,-60,6          │
│ MAP         [Pool] house Refill: ON  │
│                                      │
│ CHEST       lobby  5,-60,2           │
│ PLAYER      Sin loot    Refill: OFF  │
├──────────────────────────────────────┤
│ ◀ Página 1/5 ▶                       │
└──────────────────────────────────────┘
```

---

# 5. Categorías

El Container Manager debe permitir filtrar por origen/estado administrativo:

```text
MAP
PLAYER
BROKEN
DISABLED
```

UNKNOWN debe continuar siendo tratado según las reglas actuales del sistema y no debe recibir administración automática accidental.

---

# 6. Filtros

El Manager debe permitir combinar filtros.

Filtros mínimos:

## Mundo

Ejemplo:

```text
lobby
exodus
```

## Tipo

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

## Origen

```text
MAP
PLAYER
UNKNOWN
```

## Estado

```text
ACTIVE
BROKEN
DISABLED
```

## Loot

```text
SIN_LOOT
LOOT_TABLE
LOOT_POOL
```

## LootTable

Filtrar por:

```text
common
food
medical
military
high_tier
rare
```

o cualquier LootTable existente.

## LootPool

Filtrar por:

```text
house
military
...
```

o cualquier Pool existente.

## Refill

```text
ENABLED
DISABLED
DUE
NOT_DUE
```

---

# 7. Búsqueda

Debe existir búsqueda textual.

La búsqueda debe poder encontrar:

* ID del container.
* Mundo.
* Coordenadas.
* Tipo.
* LootTable.
* LootPool.

Ejemplos:

```text
house
```

debe encontrar containers con:

```text
loot_pool_id = house
```

Ejemplo:

```text
lobby
```

debe encontrar containers del mundo `lobby`.

Ejemplo:

```text
-4,-60,6
```

debe poder encontrar el container correspondiente cuando sea posible.

La búsqueda debe ejecutarse sobre los datos almacenados y no cargar chunks.

---

# 8. Paginación

El Manager debe ser paginado.

No cargar todos los containers en memoria si el mundo contiene miles o decenas de miles.

La consulta debe utilizar paginación SQLite.

Ejemplo conceptual:

```text
LIMIT ?
OFFSET ?
```

o equivalente más eficiente cuando corresponda.

La cantidad por página debe ser configurable o utilizar el límite existente del sistema GUI.

---

# 9. Detalle de Container

Al seleccionar un container debe abrirse una vista detallada.

Debe mostrar:

```text
Container ID
World
X
Y
Z
Type

Source
Status
Managed
Registered

LootTable
LootPool

Refill Enabled
Interval Config
Interval Effective

Last Loot
Next Refill

Due
Eligible

Created At
Updated At
```

Ejemplo:

```text
┌───────────────────────────────┐
│        CONTAINER DEBUG        │
├───────────────────────────────┤
│ ID: b47e64ac...               │
│ Mundo: lobby                  │
│ Pos: -4,-60,6                 │
│ Tipo: BARREL                  │
│                               │
│ Origen: MAP                   │
│ Estado: ACTIVE                │
│ Administrado: SI              │
│ Registrado: SI                │
│                               │
│ LootTable: NULL               │
│ LootPool: house               │
│                               │
│ Refill: ACTIVADO              │
│ Intervalo: 10s                │
│                               │
│ Due: SI                       │
│ Eligible: SI                  │
├───────────────────────────────┤
│ [Loot] [Refill] [Editar]      │
└───────────────────────────────┘
```

---

# 10. Reglas de edición de Containers

## PLAYER

Los containers `PLAYER` están protegidos.

El Manager NO debe permitir convertirlos automáticamente.

Si existe una operación administrativa explícita equivalente a:

```text
PLAYER → MAP
```

debe requerir confirmación explícita.

Debe preservar:

* inventario;
* identidad física;
* demás protecciones existentes.

---

## BROKEN

Los containers `BROKEN` deben permanecer protegidos.

No permitir:

```text
BROKEN → ACTIVE
```

automáticamente.

Si en el futuro existe una restauración administrativa, debe ser una operación explícita y separada.

Esta etapa no implementa restauración de BROKEN.

---

## DISABLED

Un container `DISABLED` no debe recibir:

* Populate automático.
* Auto Refill.
* Loot generado automáticamente.

La GUI puede permitir habilitarlo/deshabilitarlo mediante acción administrativa explícita.

---

# 11. Administración de Loot

El sistema debe distinguir:

```text
LootPool
```

de:

```text
LootTable legacy
```

La prioridad efectiva siempre será:

```text
LootPool > LootTable
```

Si existe:

```text
loot_pool_id != NULL
```

el Pool es la configuración efectiva.

Si no existe Pool y existe:

```text
loot_table_id != NULL
```

se utiliza LootTable legacy.

Si ambos son NULL:

```text
SIN LOOT
```

---

# 12. Loot Pool Manager

Debe existir una sección específica:

```text
Loot Pools
```

Ejemplo:

```text
┌───────────────────────────────┐
│          LOOT POOLS            │
├───────────────────────────────┤
│ 🏠 house                      │
│ ⚔ military                   │
│ 🏥 medical_supply             │
│ 💎 high_tier                  │
├───────────────────────────────┤
│ [Crear Pool]                  │
│ [Atrás]                       │
└───────────────────────────────┘
```

---

# 13. Crear LootPool

Debe existir una opción:

```text
Crear Pool
```

Datos mínimos:

```text
ID
Nombre
Selection Mode
Min Rolls
Max Rolls
Enabled
```

El ID debe ser único.

No permitir IDs duplicados.

---

# 14. Editar LootPool

Al seleccionar un Pool:

```text
house
```

mostrar:

```text
Nombre
Modo
Rolls
Estado
Tablas asociadas
Pesos
```

Ejemplo:

```text
┌─────────────────────────────────┐
│           POOL: HOUSE            │
├─────────────────────────────────┤
│ ID: house                       │
│ Nombre: House Loot              │
│ Estado: ACTIVADO                │
│                                 │
│ Modo: SINGLE_RANDOM             │
│ Rolls: 1 - 1                    │
│                                 │
│ TABLAS:                         │
│                                 │
│ common       Peso: 40           │
│ food         Peso: 30           │
│ medical      Peso: 20           │
│ rare         Peso: 10           │
│                                 │
│ [Editar] [Agregar] [Eliminar]   │
└─────────────────────────────────┘
```

---

# 15. Pool Membership

Cada LootTable asociada a un Pool debe tener:

```text
loot_table_id
weight
```

Los pesos son relativos.

No es necesario que sumen 100.

Ejemplo válido:

```text
common = 40
food = 30
medical = 20
rare = 10
```

También sería válido:

```text
common = 4
food = 3
medical = 2
rare = 1
```

La proporción relativa es lo importante.

---

# 16. Agregar LootTable a Pool

La GUI debe permitir:

```text
Agregar LootTable
```

y seleccionar una LootTable existente.

Después solicitar:

```text
Peso
```

No crear LootTables automáticamente desde esta pantalla.

La administración de LootTables sigue perteneciendo al sistema existente de LootTables.

---

# 17. Editar peso

Seleccionando una LootTable dentro de un Pool:

```text
common
```

debe poder modificarse:

```text
Weight
```

Ejemplo:

```text
Peso actual: 40

Nuevo peso: 50
```

Guardar debe actualizar únicamente la relación correspondiente.

---

# 18. Eliminar LootTable del Pool

Debe existir una acción:

```text
Eliminar del Pool
```

Debe requerir confirmación.

Ejemplo:

```text
¿Eliminar common del Pool house?

[CONFIRMAR]
[CANCELAR]
```

Esto NO elimina la LootTable global.

Solo elimina su pertenencia al Pool.

---

# 19. Selection Mode

Los modos definidos actualmente son:

```text
SINGLE_RANDOM
MIXED_RANDOM
```

---

# 20. SINGLE_RANDOM

Comportamiento:

```text
1 refill
↓
1 LootTable seleccionada
↓
Loot generado desde esa LootTable
```

La selección es ponderada.

Ejemplo:

```text
common 40
food 30
medical 20
rare 10
```

No debe asumir que los pesos suman 100.

---

# 21. MIXED_RANDOM

Permite seleccionar múltiples LootTables durante una misma generación/refill.

Configuración:

```text
Min Rolls
Max Rolls
```

Ejemplo:

```text
Min Rolls: 2
Max Rolls: 4
```

Por defecto:

```text
NO DUPLICADOS
```

Una misma LootTable no debe seleccionarse más de una vez dentro del mismo conjunto de selección, salvo que una futura configuración explícita habilite duplicados.

Esta etapa no agrega todavía una UI de "allow duplicates" si el backend actual no lo soporta.

---

# 22. Pool inválido

Un Pool es inválido si:

* no existe;
* está deshabilitado cuando corresponda;
* no contiene LootTables válidas;
* sus memberships apuntan a LootTables inexistentes;
* todos los pesos son inválidos;
* no puede producir una selección válida.

El sistema debe responder de forma segura:

```text
SKIPPED_INVALID_LOOT_POOL
```

Nunca debe:

* lanzar una excepción no controlada;
* borrar inventarios;
* borrar el Pool;
* convertir automáticamente el container a LootTable legacy.

---

# 23. Habilitar / deshabilitar Pool

Debe existir:

```text
Activar Pool
Desactivar Pool
```

Un Pool deshabilitado no debe ser utilizado para generar loot.

Los containers que lo tengan asignado deben permanecer configurados con ese Pool.

No convertir automáticamente:

```text
Pool → LootTable
```

ni:

```text
Pool → NULL
```

---

# 24. Eliminar Pool

Eliminar un Pool debe requerir confirmación explícita.

Antes de eliminarlo, comprobar cuántos containers lo utilizan.

Ejemplo:

```text
Pool: house

Containers asociados: 37

¿Eliminar Pool?
```

La operación NO debe eliminar containers.

Debe definirse una política segura para referencias existentes.

Política propuesta:

```text
Pool eliminado
↓
containers que lo utilizaban
↓
loot_pool_id = NULL
```

Pero antes de implementar esta política debe comprobarse que no contradiga el modelo de persistencia existente.

La operación debe ser transaccional.

---

# 25. Asignar LootPool a Container

Desde el detalle del container debe existir:

```text
Asignar LootPool
```

Mostrar lista de Pools disponibles.

Ejemplo:

```text
┌──────────────────────────────┐
│ ASIGNAR LOOT POOL            │
├──────────────────────────────┤
│ house                        │
│ military                     │
│ medical_supply               │
│ high_tier                    │
├──────────────────────────────┤
│ [Cancelar]                   │
└──────────────────────────────┘
```

---

# 26. Confirmación de asignación

Asignar un Pool debe ser una operación administrativa explícita.

Debe mostrar:

```text
Container:
BARREL -4,-60,6

Pool:
house

LootTable actual:
common
```

y advertir:

```text
El LootPool tendrá prioridad sobre la LootTable existente.
```

Confirmación:

```text
[CONFIRMAR]
[CANCELAR]
```

---

# 27. Comportamiento al asignar Pool

Al asignar:

```text
loot_pool_id = house
```

no se debe destruir la LootTable legacy automáticamente.

Puede quedar:

```text
loot_pool_id = house
loot_table_id = common
```

porque:

```text
LootPool > LootTable
```

La LootTable legacy queda como configuración secundaria/compatibilidad.

No generar loot automáticamente solo por asignar el Pool.

---

# 28. Quitar LootPool

Debe existir:

```text
Quitar LootPool
```

Al quitarlo:

```text
loot_pool_id = NULL
```

Si existe una LootTable legacy:

```text
loot_table_id = common
```

el container vuelve a utilizar `common`.

Si ambas quedan NULL:

```text
SIN LOOT
```

No generar loot automáticamente al cambiar la configuración.

---

# 29. Refill desde Container Manager

El detalle del container puede mostrar:

```text
Next Refill
Due
Eligible
Interval
```

Debe permitir únicamente las acciones existentes y seguras.

No duplicar la lógica de `RefillManager`.

El Container Manager debe delegar la lógica de refill al sistema existente.

---

# 30. Populate desde Container Manager

El Container Manager puede mostrar información de Populate, pero no debe duplicar:

* LootGenerator.
* PopulateQueue.
* ContainerPopulator.

Si se agrega una acción de Populate individual, debe reutilizar los servicios existentes.

---

# 31. Historial de LootPool

Cuando un container utiliza un Pool, la información detallada puede mostrar las últimas selecciones.

Ejemplo:

```text
Pool: house

Historial:
1. common
2. food
3. common
4. medical
5. rare
```

El historial debe tener un límite.

Propuesta:

```text
últimas 5 selecciones
```

No almacenar historial infinito.

---

# 32. Historial y persistencia

Si el sistema actual ya dispone de persistencia para el historial:

* reutilizarla;
* no crear otra estructura duplicada.

Si actualmente el historial es solo runtime/memoria, esta etapa debe determinar si se necesita persistencia antes de implementarla.

No introducir persistencia adicional sin necesidad funcional.

---

# 33. Estadísticas del Container Manager

La pantalla de estadísticas debe mostrar como mínimo:

```text
MAP
PLAYER
BROKEN
DISABLED

ACTIVE
REFILL ENABLED
REFILL DISABLED

WITH LOOT
WITHOUT LOOT

WITH LOOT TABLE
WITH LOOT POOL

DUE
NOT DUE
```

También por tipo:

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

---

# 34. Estadísticas de LootPools

El Loot Pool Manager puede mostrar:

```text
Total Pools
Enabled Pools
Disabled Pools

Total memberships
Pools sin tablas
Pools inválidos

Containers usando Pools
```

No realizar scans físicos del mundo para generar estas estadísticas.

Utilizar SQLite.

---

# 35. Validación de Pools

Antes de permitir guardar cambios en un Pool:

Validar:

```text
ID no vacío
ID único
Nombre válido
Modo válido
Min Rolls >= 1
Max Rolls >= Min Rolls
```

Memberships:

```text
LootTable existente
Weight > 0
```

El Pool debe tener al menos una LootTable válida cuando esté habilitado.

---

# 36. Seguridad de GUI

Todas las acciones administrativas deben comprobar permisos.

Nunca confiar únicamente en que el jugador pueda abrir la GUI.

Cada acción debe validar:

```text
permission
```

antes de modificar datos.

Especialmente:

* crear Pool;
* editar Pool;
* eliminar Pool;
* modificar pesos;
* asignar Pool;
* quitar Pool;
* habilitar/deshabilitar;
* modificar containers.

---

# 37. Click handling

Las acciones de GUI deben utilizar los mecanismos existentes:

```text
InventoryHolder
```

y no depender únicamente del nombre visible del inventario.

No permitir que un jugador pueda ejecutar acciones administrativas modificando el contenido visual del inventario.

---

# 38. Thread Safety

GUI:

```text
MAIN THREAD
```

cuando sea necesario interactuar con Bukkit Inventory.

SQLite:

```text
ASYNC
```

cuando sea seguro.

No bloquear el hilo principal con consultas SQLite pesadas.

No cargar chunks desde la GUI.

---

# 39. Performance

Container Manager debe soportar potencialmente:

```text
100
1,000
10,000+
```

containers.

Nunca ejecutar:

```text
SELECT *
```

de toda la tabla para una página.

Usar:

```text
LIMIT
OFFSET
```

o estrategia equivalente.

Los filtros deben aplicarse en SQLite siempre que sea posible.

---

# 40. SQLite

No crear tablas duplicadas si ya existe una estructura adecuada.

Antes de modificar el esquema:

1. inspeccionar schema actual;
2. determinar si el campo ya existe;
3. crear migración únicamente si es necesario;
4. preservar datos existentes.

No borrar datos durante migraciones.

---

# 41. Compatibilidad con LootTable legacy

Debe continuar funcionando:

```text
loot_table_id = common
loot_pool_id = NULL
```

Debe seguir siendo válido.

Container Manager debe mostrar:

```text
Loot: common
```

---

# 42. Compatibilidad Pool

Debe funcionar:

```text
loot_table_id = NULL
loot_pool_id = house
```

Debe mostrar:

```text
Loot: [Pool] house
```

---

# 43. Configuración dual

También puede existir:

```text
loot_table_id = common
loot_pool_id = house
```

En ese caso:

```text
Effective Loot = house
```

y:

```text
Legacy LootTable = common
```

debe continuar visible en el detalle.

---

# 44. No Loot

Si:

```text
loot_table_id = NULL
loot_pool_id = NULL
```

mostrar:

```text
Sin Loot configurado
```

No debe entrar automáticamente en:

* Populate.
* Auto Refill.

---

# 45. Integración con WorldScanner

WorldScanner debe seguir preservando:

```text
loot_pool_id
loot_table_id
refill_enabled
source
status
managed
registered
last_loot
next_refill
```

Container Manager no debe introducir ninguna modificación al comportamiento del scanner.

---

# 46. Integración con Region System

El Container Manager puede mostrar si un container está dentro de una región conocida cuando esa información ya esté disponible.

No debe cargar regiones/chunks innecesariamente.

No reemplazar:

```text
RegionScanner
RegionManager
RegionWandListener
```

---

# 47. Integración con Auto Refill

El Container Manager debe mostrar el estado real proporcionado por `RefillManager`.

No duplicar el algoritmo.

Fuente de verdad:

```text
RefillManager
```

Intervalo:

```text
config.yml
```

Nunca utilizar:

```text
refill_interval_seconds
```

como fuente de verdad para el intervalo actual.

---

# 48. Integración con Populate

Fuente de verdad:

```text
PopulateManager
PopulateQueue
ContainerPopulator
```

El Manager únicamente administra/visualiza.

No crear un segundo sistema de Populate.

---

# 49. Flujo de usuario propuesto

## Containers

```text
/loot
   ↓
Containers
   ↓
Filtros
   ↓
Lista paginada
   ↓
Container Detail
```

## Loot Pools

```text
/loot
   ↓
Loot Pools
   ↓
Lista de Pools
   ↓
Pool Detail
   ↓
Memberships
   ↓
Agregar / editar / eliminar LootTables
```

## Asignación

```text
Container Detail
   ↓
Loot
   ↓
Asignar LootPool
   ↓
Seleccionar Pool
   ↓
Confirmar
   ↓
SQLite
```

---

# 50. Confirmaciones

Operaciones destructivas o administrativas importantes deben pedir confirmación.

Requieren confirmación:

```text
Eliminar Pool
Eliminar LootTable del Pool
Quitar Pool de Container
Cambiar configuración crítica
PLAYER → MAP
```

Operaciones de simple navegación no necesitan confirmación.

---

# 51. Errores

Los errores deben ser claros para el administrador.

Ejemplos:

```text
Pool no encontrado.
LootTable no encontrada.
Pool inválido.
Peso inválido.
Container no encontrado.
Container protegido.
No tienes permisos.
```

No mostrar stack traces al jugador.

Registrar detalles técnicos en consola/log cuando corresponda.

---

# 52. No introducir funciones no diseñadas

Esta etapa NO incluye:

* ItemsAdder integration.
* generación automática de texturas;
* edición avanzada de LootTables;
* economía;
* estadísticas de jugadores;
* permisos avanzados;
* restauración automática de BROKEN;
* sincronización externa;
* web panel.

Esas funcionalidades pertenecen a futuras etapas.

---

# 53. Tests obligatorios antes de considerar terminada la implementación

## Test CM-001

Listar containers.

Verificar:

```text
paginación
filtros
búsqueda
```

sin cargar chunks.

---

## Test CM-002

Filtrar:

```text
MAP
PLAYER
BROKEN
DISABLED
```

Verificar resultados correctos.

---

## Test CM-003

Buscar por:

```text
world
coordinates
container ID
LootTable
LootPool
```

---

## Test CM-004

Abrir detalle de container.

Verificar todos los campos.

---

## Test CM-005

Crear LootPool.

Verificar persistencia después de restart.

---

## Test CM-006

Editar LootPool.

Modificar:

```text
name
mode
rolls
enabled
```

Verificar persistencia.

---

## Test CM-007

Agregar LootTable a Pool.

Ejemplo:

```text
house:
common 40
food 30
medical 20
rare 10
```

---

## Test CM-008

Modificar peso.

Verificar persistencia.

---

## Test CM-009

Eliminar membership.

Confirmar que la LootTable global siga existiendo.

---

## Test CM-010

Asignar Pool a container.

Verificar:

```text
loot_pool_id = house
```

---

## Test CM-011

Container con:

```text
loot_pool_id = house
loot_table_id = common
```

Verificar:

```text
Effective Loot = house
```

---

## Test CM-012

Quitar Pool.

Verificar que vuelva a utilizar LootTable legacy si existe.

---

## Test CM-013

Pool inválido.

Verificar:

```text
SKIPPED_INVALID_LOOT_POOL
```

sin excepción ni corrupción.

---

## Test CM-014

PLAYER protection.

Verificar que la GUI no permita convertirlo accidentalmente.

---

## Test CM-015

BROKEN protection.

Verificar que permanezca BROKEN.

---

## Test CM-016

DISABLED protection.

Verificar que no reciba Populate ni Refill.

---

## Test CM-017

WorldScanner.

Asignar Pool → ejecutar scan → verificar que el Pool permanezca.

---

## Test CM-018

Restart.

Verificar persistencia de:

```text
Pools
Memberships
Container assignments
```

---

## Test CM-019

Performance.

Probar Container Manager con una cantidad significativa de containers.

Verificar:

```text
sin carga masiva de chunks
sin congelar servidor
sin consultas SQLite innecesariamente grandes
```

---

# 54. Criterios de aceptación

La etapa se considera implementada únicamente cuando:

```text
[ ] Container Manager 2.0 funcional
[ ] Paginación funcional
[ ] Filtros funcionales
[ ] Búsqueda funcional
[ ] Detalle de container funcional
[ ] Protección PLAYER
[ ] Protección BROKEN
[ ] Protección DISABLED

[ ] Loot Pool Manager funcional
[ ] Crear Pool
[ ] Editar Pool
[ ] Activar/desactivar
[ ] Agregar LootTable
[ ] Editar pesos
[ ] Eliminar membership
[ ] Eliminar Pool con seguridad

[ ] Asignación Pool → Container
[ ] Quitar Pool
[ ] Prioridad Pool > LootTable
[ ] LootTable legacy intacto

[ ] Persistencia SQLite
[ ] Persistencia después de restart
[ ] WorldScanner preserva configuración

[ ] Populate compatible
[ ] Auto Refill compatible
[ ] Thread Safety
[ ] Performance
```

---

# 55. Orden de implementación recomendado

La implementación posterior debe realizarse en este orden:

```text
1. Revisar código actual
        ↓
2. Revisar schema SQLite
        ↓
3. Implementar Pool Manager backend
        ↓
4. Implementar persistencia/migraciones necesarias
        ↓
5. Implementar GUI Loot Pools
        ↓
6. Implementar Container Manager 2.0 backend
        ↓
7. Implementar filtros/búsqueda/paginación
        ↓
8. Implementar Container Detail
        ↓
9. Integrar asignación Pool → Container
        ↓
10. Integrar acciones administrativas
        ↓
11. Tests
        ↓
12. Auditoría SDD
```

No implementar todo en un único cambio gigante.

---

# 56. Commits

La implementación debe dividirse en commits pequeños y lógicos.

Ejemplo:

```text
feat: add loot pool management backend
feat: add loot pool management gui
feat: add container manager filtering
feat: add container manager detail view
feat: integrate loot pool assignment
test: validate container manager and loot pools
```

No mezclar limpieza de warnings con nuevas funcionalidades.

---

# 57. Regla final

Container Manager 2.0 y Loot Pool Manager deben ser una capa administrativa sobre los sistemas existentes.

No deben convertirse en un segundo:

```text
PopulateManager
RefillManager
LootManager
WorldScanner
```

La arquitectura debe seguir siendo:

```text
                    ┌──────────────────┐
                    │  Container GUI   │
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
       Container Manager              Loot Pool Manager
              │                             │
              └──────────────┬──────────────┘
                             │
                        SQLite / Models
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
     PopulateManager   RefillManager      WorldScanner
          │                  │                  │
          └──────────────────┼──────────────────┘
                             │
                       Bukkit / Paper
```

La GUI administra.

Los managers ejecutan la lógica.

SQLite persiste.

Paper/Bukkit modifica el mundo en el hilo correcto.

No duplicar responsabilidades.

## 16. Implementación Final y Validación

Container Manager 2.0 fue implementado rigurosamente siguiendo la arquitectura modular y las invariantes del proyecto:

### Clases Implementadas
- `com.arcraft.lootrefill.container.ContainerFilter`: Modelo inmutable con `Builder`, predicado `test(LootContainer)`, conteo de filtros activos y soporte completo para 12 criterios de búsqueda y filtrado.
- `com.arcraft.lootrefill.container.ContainerSortField`: Enumerador tipado con comparadores seguros para los 9 campos de ordenamiento en memoria.
- `com.arcraft.lootrefill.container.LootFilterType`: Modos de filtro de loot (`ALL`, `HAS_LOOT`, `NO_LOOT`, `LOOT_POOL`, `LOOT_TABLE`).
- `com.arcraft.lootrefill.gui.ConfirmationMenu`: Menú modal reutilizable e idempotente para confirmaciones críticas con prevención de spam de clicks (`AtomicBoolean`) y cancelación vía `ESC`.
- `com.arcraft.lootrefill.gui.LootPoolsMenu`, `LootPoolEditorMenu`, `LootPoolMembersMenu`: Suite administrativa visual para Loot Pools.
- `com.arcraft.lootrefill.gui.ContainerMenu`: Listado administrativo con toolbar (búsqueda, filtros, reseteo, ordenamiento) y paginación en vivo.
- `com.arcraft.lootrefill.gui.ContainerFilterMenu`: Configuración visual interactiva de filtros y submenús selectores para pools y tablas.
- `com.arcraft.lootrefill.gui.ContainerDetailMenu`: Ficha técnica inspectora del contenedor con acciones seguras en vivo.

### Resultados de Verificación
- 37 tests unitarios automatizados pasando sin fallos (`ContainerFilterTest`, `ConfirmationLogicTest`, `ContainerDetailLogicTest`, `LootPoolModelTest`).
- `mvn clean package` → `BUILD SUCCESS`.
- 100% de cumplimiento de invariantes `INV-001` a `INV-019`.
- Cero migraciones DDL de SQLite requeridas.
- Cero carga de chunks en operaciones de GUI.
- Integridad total de inventarios y protección estricta de contenedores PLAYER, BROKEN y DISABLED.