Continuemos el desarrollo del plugin **LootRefill** para **Paper 1.21.x / Java 21+**.

La Etapa 1 ya está implementada y compilando. Ya existen:

* LootRefillPlugin
* DatabaseManager SQLite
* LootManager
* LootTable
* LootEntry
* ContainerManager / ContainerRegistry
* LootContainer
* GuiManager
* MenuHolder
* AdminMenu
* LootTypesMenu
* LootEditorMenu
* LootAddItemMenu
* RefillMenu
* RefillQueueMenu
* RefillConfigMenu
* ContainerMenu
* ChatInputHandler
* comandos `/loot`
* `/loot register <tabla_id>`

NO rehacer la Etapa 1.

La siguiente etapa debe implementar un **World Scanner extremadamente eficiente**, además de preparar correctamente el sistema de contenedores y refill para que diferentes tipos de almacenamiento puedan tener diferentes intervalos.

==================================================

# OBJETIVO PRINCIPAL

==================================================

Quiero poder administrar automáticamente contenedores existentes en un mundo custom de Minecraft.

Los tipos iniciales que deben poder detectarse y administrarse son:

* CHEST
* TRAPPED_CHEST
* BARREL
* FURNACE
* BLAST_FURNACE
* SMOKER
* DISPENSER
* DROPPER

La arquitectura debe permitir agregar más tipos posteriormente.

IMPORTANTE:

NO quiero que el plugin recorra constantemente todo el mundo.

El mundo será un mapa custom grande con posiblemente miles o decenas de miles de contenedores.

El scanner debe ejecutarse de manera controlada, progresiva y sin provocar congelamientos o TPS drops importantes.

==================================================

# 1. WORLD SCANNER

==================================================

Implementar:

/loot scan <world>

Debe comenzar un escaneo del mundo indicado.

Antes de iniciar:

* comprobar que el mundo existe
* comprobar permisos
* impedir iniciar dos scans simultáneamente sobre el mismo mundo
* mostrar advertencia si ya existe un scan activo

Ejemplo:

/loot scan Exodus

Mostrar:

"§6Iniciando escaneo de Exodus..."

==================================================

# 2. NO ESCANEAR BLOQUES UNO POR UNO

==================================================

IMPORTANTE.

NO implementar:

for x
for y
for z
world.getBlockAt()

Esto sería demasiado costoso.

Siempre que sea posible, utilizar las estructuras de Bukkit/Paper que permitan acceder directamente a BlockStates / TileEntities / BlockEntities de los chunks.

El objetivo es evitar revisar los ~65.000 bloques individuales de cada chunk.

Investigar y utilizar la API apropiada disponible en Paper 1.21.x.

==================================================

# 3. PROCESAMIENTO POR BATCHES

==================================================

El scanner debe funcionar mediante tareas programadas.

Nunca procesar miles de chunks en un único tick.

Crear configuración:

scanner:
enabled: true

chunks-per-tick: 1

max-ms-per-tick: 3

pause-between-batches: 1

save-every: 100

Si es técnicamente más seguro utilizar solamente una de las estrategias de límite, priorizar:

max-ms-per-tick

pero implementar protección contra procesamiento excesivo.

La idea es:

Tick
↓
procesar una cantidad limitada
↓
medir tiempo
↓
si se aproxima al límite
↓
detener batch
↓
continuar siguiente tick

NO bloquear el hilo principal.

==================================================

# 4. MUY IMPORTANTE: CHUNKS

==================================================

El scanner debe diferenciar entre:

* chunks ya cargados
* chunks que no están cargados

NO mantener todos los chunks del mundo cargados permanentemente.

NO utilizar force-load permanente.

NO dejar miles de chunks cargados después del scan.

Si es necesario cargar temporalmente un chunk para inspeccionarlo:

1. cargarlo de manera controlada
2. inspeccionarlo
3. procesarlo
4. liberar la carga cuando sea seguro hacerlo

Investigar correctamente la API de Paper 1.21.x para realizar esto.

NO hacer:

world.setChunkForceLoaded(..., true)

para miles de chunks.

Si Paper dispone de APIs asíncronas apropiadas para chunk loading, utilizarlas cuando sea seguro.

IMPORTANTE:

La lectura/modificación de entidades y BlockStates debe respetar las restricciones de thread de Bukkit/Paper.

No acceder a Bukkit World, Chunk, BlockState o Inventory desde async threads si la API no lo permite.

==================================================

# 5. DETECCIÓN DE CONTENEDORES

==================================================

Detectar únicamente los tipos configurados.

Crear configuración:

containers:

chest:
enabled: true

trapped-chest:
enabled: true

barrel:
enabled: true

furnace:
enabled: true

blast-furnace:
enabled: true

smoker:
enabled: true

dispenser:
enabled: true

dropper:
enabled: true

Preparar arquitectura para futuros:

hopper
brewing-stand
shulker-box
etc.

NO hardcodear toda la lógica en un enorme if/else.

Utilizar un enum o registry:

ContainerType

Ejemplo:

CHEST
TRAPPED_CHEST
BARREL
FURNACE
BLAST_FURNACE
SMOKER
DISPENSER
DROPPER

==================================================

# 6. REGISTRO EN SQLITE

==================================================

Modificar/expandir la estructura de containers.

Cada contenedor debe tener como mínimo:

id
world
x
y
z
type
loot_table_id
enabled
looted
last_loot
next_refill

Agregar:

refill_enabled
refill_interval_seconds

y cualquier otro dato necesario para el sistema futuro.

Crear índices apropiados:

INDEX(world)
INDEX(world, x, z)
INDEX(type)
INDEX(next_refill)
INDEX(loot_table_id)

Evitar consultas completas de la tabla cuando no sean necesarias.

==================================================

# 7. DUPLICADOS

==================================================

El scanner NO debe registrar dos veces el mismo contenedor.

La identidad lógica de un contenedor será:

world + x + y + z

Crear una restricción UNIQUE apropiada en SQLite.

Si el contenedor ya existe:

NO duplicarlo.

Opcionalmente actualizar información relevante.

==================================================

# 8. ESCANEO INCREMENTAL

==================================================

Este punto es MUY importante.

Si el servidor se reinicia durante un scan:

NO quiero perder todo el progreso.

Guardar:

scan_state

Puede ser una tabla SQLite:

scan_jobs

con:

id
world
current_chunk_x
current_chunk_z
total_chunks
processed_chunks
found_containers
status
started_at
updated_at

Estados:

RUNNING
PAUSED
COMPLETED
CANCELLED
ERROR

Al reiniciar el servidor:

detectar scans incompletos.

NO continuar automáticamente sin verificar.

Mostrar al administrador:

"Existe un escaneo incompleto de Exodus."

Permitir:

/loot scan resume

/loot scan cancel

==================================================

# 9. PROGRESO

==================================================

Mientras escanea:

/loot scan status

Debe mostrar algo como:

LootRefill Scanner
━━━━━━━━━━━━━━━━━━━━

Mundo: Exodus

Progreso:
████████████░░░░░░░░ 62%

Chunks:
7.482 / 12.031

Contenedores:
4.281

Cofres:
3.102
Barriles:
782
Hornos:
291
Dispensers:
106

Tiempo transcurrido:
01:42

ETA:
00:58

TPS:
20.0

El cálculo de ETA puede ser aproximado.

No ejecutar comandos costosos cada tick solamente para mostrar estadísticas.

Actualizar el estado visible cada 1-2 segundos.

==================================================

# 10. CANCELACIÓN

==================================================

Implementar:

/loot scan cancel

La cancelación debe ser segura.

No dejar:

* chunks permanentemente cargados
* tasks ejecutándose
* conexiones SQLite abiertas
* estados corruptos

Guardar el progreso antes de cancelar.

==================================================

# 11. REFILL POR TIPO DE CONTENEDOR

==================================================

Este es un requisito fundamental.

NO quiero un único intervalo global.

Cada tipo de almacenamiento debe tener su propia configuración.

Ejemplo:

refill:

chest:
enabled: true
interval: 1800

trapped-chest:
enabled: true
interval: 1800

barrel:
enabled: true
interval: 3600

furnace:
enabled: true
interval: 7200

blast-furnace:
enabled: true
interval: 7200

smoker:
enabled: true
interval: 7200

dispenser:
enabled: true
interval: 3600

dropper:
enabled: true
interval: 3600

Los valores están expresados en segundos.

==================================================

# 12. REFILL ESCALONADO

==================================================

MUY IMPORTANTE.

No quiero:

"Cada 30 minutos → recorrer TODOS los cofres."

Eso puede generar un spike enorme.

Crear un sistema:

RefillQueue

El sistema debe trabajar progresivamente.

Ejemplo:

Existen:

5.000 cofres
2.000 barriles
500 hornos

No hacer:

5.000 operaciones en un tick.

Hacer:

Tick 1:
procesar 10 cofres

Tick 2:
procesar 10 cofres

Tick 3:
procesar 10 cofres

etc.

Crear configuración:

refill:
processing:
containers-per-tick: 5
max-ms-per-tick: 2
batch-delay-ticks: 1

La implementación debe priorizar el límite de tiempo.

==================================================

# 13. COLAS SEPARADAS

==================================================

Idealmente crear una cola por tipo:

ChestRefillQueue
BarrelRefillQueue
FurnaceRefillQueue
DispenserRefillQueue

O una cola genérica:

RefillQueue<ContainerType>

con scheduling independiente.

El objetivo es evitar que una cantidad enorme de cofres bloquee el procesamiento de barriles u otros tipos.

==================================================

# 14. NO PROCESAR CONTENEDORES QUE NO CORRESPONDAN

==================================================

Antes de meter un contenedor en la cola:

comprobar:

enabled
refill_enabled
loot_table_id != null
next_refill <= currentTime

Si no corresponde:

NO procesarlo.

Las consultas SQLite deben filtrar directamente.

Ejemplo conceptual:

SELECT ...
FROM containers
WHERE type = ?
AND refill_enabled = 1
AND next_refill <= ?

NO cargar todos los containers en memoria.

==================================================

# 15. CHUNKS Y REFILL

==================================================

Un refill NO debe cargar miles de chunks simultáneamente.

Si el contenedor está en un chunk descargado:

NO cargar miles de chunks de golpe.

Crear un sistema de procesamiento:

RefillQueue
↓
Container
↓
¿Chunk cargado?
↓
SÍ → procesar
NO → cargar de manera controlada
↓
procesar
↓
liberar si corresponde

Pero considerar que cargar/unload frecuente puede ser más costoso que esperar a que el chunk sea utilizado.

Diseñar una estrategia eficiente.

==================================================

# 16. CONDICIONES DE REFILL

==================================================

Preparar configuración:

refill:
conditions:

```
require-empty: true

require-no-players-nearby: true

nearby-radius: 32

refill-loaded-chunks-only: false

max-chunks-loaded-by-refill: 1
```

Estas condiciones deben estar abstraídas para poder ampliar posteriormente.

==================================================

# 17. SEPARAR SCANNER DE REFILL

==================================================

MUY IMPORTANTE.

No mezclar:

WorldScanner
con
RefillManager.

Arquitectura:

WorldScanner
↓
ContainerRegistry
↓
Database

RefillManager
↓
RefillQueue
↓
ContainerManager
↓
Database

LootManager
↓
LootTable
↓
LootGenerator

Cada componente debe tener una única responsabilidad.

==================================================

# 18. GUI

==================================================

Actualizar el AdminMenu existente para mostrar:

Contenedores registrados:

Total
Cofres
Barriles
Hornos
Dispensers
etc.

Actualizar RefillMenu para mostrar cada tipo:

┌──────────────────────────────┐
│ CHESTS                        │
│ Estado: ACTIVADO              │
│ Intervalo: 30 minutos         │
│ Pendientes: 182               │
│                              │
│ [CONFIGURAR]                  │
└──────────────────────────────┘

Y:

┌──────────────────────────────┐
│ BARRELS                       │
│ Estado: ACTIVADO              │
│ Intervalo: 60 minutos         │
│ Pendientes: 94                │
│                              │
│ [CONFIGURAR]                  │
└──────────────────────────────┘

Lo mismo para:

* Furnace
* Blast Furnace
* Smoker
* Dispenser
* Dropper

Agregar opción:

"Activar/desactivar refill"

y:

"Configurar intervalo"

No permitir valores menores que un límite razonable configurable.

==================================================

# 19. SCANNER GUI

==================================================

Crear una GUI:

ScannerMenu

Mostrar:

Estado:
IDLE
SCANNING
PAUSED
COMPLETED
CANCELLED

Botones:

[ INICIAR SCAN ]
[ PAUSAR ]
[ REANUDAR ]
[ CANCELAR ]
[ ESTADÍSTICAS ]

Mostrar progreso y estadísticas.

==================================================

# 20. COMANDOS

==================================================

Implementar:

/loot scan <world>
/loot scan status
/loot scan pause
/loot scan resume
/loot scan cancel

Mantener:

/loot register <loot_table>

/loot refill

/loot admin

/loot reload

Actualizar tab completion.

==================================================

# 21. MÉTRICAS Y DEBUG

==================================================

Crear modo:

debug: false

Si debug=true:

mostrar información relevante:

* tiempo utilizado por batch
* chunks procesados
* contenedores encontrados
* containers insertados
* containers duplicados
* chunks cargados
* chunks liberados
* tiempo de consultas SQLite

NO spamear consola cuando debug=false.

==================================================

# 22. ASYNC / MAIN THREAD

==================================================

Ser extremadamente cuidadoso con Bukkit/Paper thread safety.

Las operaciones de:

World
Chunk
Block
BlockState
Inventory
Player

deben realizarse en el hilo correcto según la API.

Las operaciones que puedan hacerse async:

* SQLite
* cálculos
* preparación de datos
* estadísticas
* lectura/escritura no-Bukkit

pueden ejecutarse async.

No mover código Bukkit a async simplemente para "optimizar".

Priorizar estabilidad sobre micro-optimizaciones.

==================================================

# 23. SQLITE

==================================================

Evitar:

INSERT individual por cada contenedor con commit individual.

Utilizar:

* transacciones
* prepared statements
* batch inserts

Ejemplo conceptual:

BEGIN TRANSACTION

INSERT...
INSERT...
INSERT...

COMMIT

Hacer commits por lotes.

No bloquear el servidor esperando SQLite.

==================================================

# 24. CACHE

==================================================

No cargar todos los containers del mundo en memoria.

Mantener solamente:

* estadísticas
* datos necesarios para el procesamiento inmediato
* pequeños caches con TTL si son útiles

La base de datos debe ser la fuente de verdad.

==================================================

# 25. PROTECCIÓN CONTRA TPS DROP

==================================================

Agregar métricas:

* tiempo de procesamiento por batch
* cantidad de operaciones
* duración máxima

Si un batch supera:

max-ms-per-tick

reducir automáticamente la cantidad de trabajo del siguiente batch.

Opcionalmente implementar un scheduler adaptativo:

Si batch tarda < 1 ms:
aumentar ligeramente capacidad.

Si tarda > 3 ms:
reducir.

Pero mantener límites máximos configurables.

NO buscar maximizar velocidad a cualquier costo.

La prioridad es:

1. estabilidad del servidor
2. TPS
3. velocidad del scan/refill

==================================================

# 26. COMPATIBILIDAD FUTURA

==================================================

La arquitectura debe permitir agregar posteriormente:

* HOPPER
* BREWING_STAND
* SHULKER_BOX
* MINECART_CHEST
* MINECART_HOPPER
* contenedores custom
* ItemsAdder

No escribir código que dependa exclusivamente de Chest.

==================================================

# 27. NO IMPLEMENTAR TODAVÍA

==================================================

NO implementar todavía:

* asignación automática por regiones
* ItemsAdder
* generación avanzada de loot
* loot tiers
* rarity
* GUI compleja de regiones
* loot por edificios

Eso será para etapas posteriores.

En esta etapa quiero:

1. Scanner optimizado
2. Registro masivo de containers
3. Persistencia
4. Tipos de containers configurables
5. Intervalos independientes
6. RefillQueue base
7. GUI de configuración
8. Protección de rendimiento
9. sistema preparado para futuras etapas

==================================================

# 28. CRITERIOS DE ACEPTACIÓN

==================================================

La etapa se considera terminada solamente si:

* Compila correctamente con Java 21.
* Funciona en Paper 1.21.x.
* `/loot scan Exodus` funciona.
* El scanner puede procesar miles de chunks progresivamente.
* No mantiene chunks permanentemente cargados.
* No utiliza loops masivos de bloques.
* Puede cancelar y reanudar scans.
* No duplica containers.
* Guarda containers en SQLite.
* Puede distinguir Chest, Barrel, Furnace, etc.
* Cada tipo puede activarse/desactivarse.
* Cada tipo tiene su propio intervalo de refill.
* El refill utiliza una cola.
* El refill se procesa progresivamente.
* No intenta rellenar miles de containers en un único tick.
* SQLite utiliza transacciones/batches.
* GUI muestra estadísticas reales.
* El servidor no recibe una avalancha de logs.
* Las operaciones Bukkit se ejecutan en el thread correcto.
* Un reinicio no corrompe la base de datos.
* No se pierde el progreso del scanner.

==================================================

# 29. ANTES DE PROGRAMAR

==================================================

Antes de modificar código:

1. Analizar la arquitectura existente de LootRefill.
2. Revisar qué clases ya existen.
3. No duplicar clases ni crear sistemas paralelos.
4. Identificar qué debe modificarse.
5. Explicar brevemente la arquitectura propuesta.
6. Explicar cómo se evitarán TPS drops.
7. Explicar cómo se manejarán los chunks.
8. Explicar cómo se manejará SQLite.
9. Explicar cómo funcionará la cola de refill.

Después de esa explicación, implementar la Etapa 2 completa.

NO destruir funcionalidades existentes de la Etapa 1.

NO reemplazar la arquitectura existente innecesariamente.

El código debe quedar limpio, modular, documentado donde sea necesario y preparado para las siguientes etapas.
