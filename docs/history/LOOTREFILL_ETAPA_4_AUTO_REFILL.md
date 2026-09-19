# LootRefill — ETAPA 4: AUTO REFILL

## Objetivo

Implementar el sistema automático de refill de LootRefill.

El sistema debe reutilizar la arquitectura existente de las etapas anteriores y permitir que cada tipo de contenedor tenga su propio intervalo de refill, procesando los contenedores de forma escalonada para evitar picos de TPS.

La prioridad es:

- rendimiento
- estabilidad
- protección absoluta de contenedores de jugadores
- persistencia en SQLite
- procesamiento distribuido
- no cargar innecesariamente chunks
- no modificar inventarios que no correspondan

---

# 1. Requisitos funcionales

Cada contenedor MAP administrado tendrá:

- loot table asignada
- último loot generado
- próximo refill
- estado
- tipo
- origen
- coordenadas
- mundo
- flags de administración/refill

El sistema debe determinar cuándo corresponde volver a llenar cada contenedor.

Ejemplo:

```text
CHEST          → 30 minutos
TRAPPED_CHEST  → 30 minutos
BARREL         → 60 minutos
DISPENSER      → 60 minutos
DROPPER        → 60 minutos
FURNACE        → 120 minutos
BLAST_FURNACE  → 120 minutos
SMOKER         → 120 minutos
```

Los valores deben salir de `config.yml`, nunca estar hardcodeados.

---

# 2. Configuración

Mantener la configuración existente y utilizar:

```yaml
refill:
  processing:
    containers-per-tick: 5
    max-ms-per-tick: 2
    batch-delay-ticks: 1

  conditions:
    require-empty: true
    require-no-players-nearby: true
    nearby-radius: 32.0
    refill-loaded-chunks-only: false
    max-chunks-loaded-by-refill: 1

  intervals:
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
```

`interval` está expresado en segundos.

Agregar, si hace falta:

```yaml
refill:
  enabled: true

  processing:
    containers-per-tick: 5
    max-ms-per-tick: 2
    batch-delay-ticks: 1
    max-refills-per-cycle: 0

  conditions:
    require-empty: true
    require-no-players-nearby: true
    nearby-radius: 32.0
    refill-loaded-chunks-only: false
    max-chunks-loaded-by-refill: 1

  chunks:
    unload-after-refill: true
    only-load-needed-chunks: true
```

`max-refills-per-cycle: 0` significa sin límite adicional.

No agregar configuraciones innecesarias.

---

# 3. Base de datos

La tabla `containers` ya existe.

Debe utilizarse para persistir:

```text
last_loot
next_refill
```

Si todavía no existen, crear una migración SQLite segura.

Tipo recomendado:

```sql
last_loot INTEGER NULL
next_refill INTEGER NULL
```

Usar Unix epoch en milisegundos.

Agregar índice:

```sql
CREATE INDEX IF NOT EXISTS idx_containers_next_refill
ON containers(next_refill);
```

La consulta de candidatos debe filtrar desde SQLite.

Ejemplo conceptual:

```sql
SELECT *
FROM containers
WHERE source = 'MAP'
  AND status = 'ACTIVE'
  AND managed = 1
  AND registered = 1
  AND refill_enabled = 1
  AND loot_table_id IS NOT NULL
  AND next_refill IS NOT NULL
  AND next_refill <= ?
ORDER BY next_refill ASC
LIMIT ?;
```

También filtrar por tipo cuando sea conveniente.

No cargar todos los contenedores en memoria.

---

# 4. Reglas absolutas de seguridad

El Auto Refill solamente puede actuar sobre:

```text
source = MAP
status = ACTIVE
managed = true
registered = true
refill_enabled = true
loot_table_id != null
```

Nunca procesar:

```text
PLAYER
UNKNOWN
BROKEN
DISABLED
managed = false
registered = false
refill_enabled = false
loot_table_id = null
```

Esto debe validarse:

1. al consultar SQLite
2. al sacar el contenedor de la queue
3. justo antes de modificar el inventario

La validación final es obligatoria.

---

# 5. Protección contra reemplazo de identidad

El sistema no debe asumir que un registro SQLite sigue representando el mismo bloque.

Antes de hacer refill:

```text
world + x + y + z
```

debe corresponder a un bloque existente.

Además, el tipo físico debe coincidir con `ContainerType`.

Ejemplo:

```text
SQLite dice CHEST
Bloque actual = BARREL
→ NO REFILL
→ marcar/registrar inconsistencia según la arquitectura existente
```

Nunca convertir automáticamente un PLAYER en MAP.

Nunca reutilizar una identidad MAP rota para un nuevo bloque colocado por un jugador.

---

# 6. Scheduler

Crear una clase dedicada:

```text
RefillManager
```

Responsabilidades:

- iniciar/detener Auto Refill
- consultar candidatos
- crear batches
- alimentar `RefillQueue`
- respetar configuración
- actualizar estadísticas
- manejar errores sin detener el sistema completo

No crear un scheduler independiente por cada contenedor.

No crear miles de BukkitTasks.

Debe existir un scheduler central.

---

# 7. RefillQueue

Reutilizar o completar la clase `RefillQueue` existente.

La queue debe trabajar de manera escalonada.

Configuración:

```yaml
containers-per-tick: 5
max-ms-per-tick: 2
batch-delay-ticks: 1
```

Cada tick:

1. comprobar límite de tiempo
2. comprobar límite de contenedores
3. obtener siguiente candidato
4. validar
5. comprobar condiciones
6. cargar chunk si corresponde
7. obtener bloque
8. comprobar inventario
9. generar loot
10. modificar inventario
11. actualizar SQLite
12. continuar hasta alcanzar el límite

Nunca superar intencionalmente el presupuesto configurado.

---

# 8. No cargar todo Exodus

CRÍTICO.

El sistema NO debe:

- recorrer todos los chunks
- recorrer todos los contenedores cada tick
- cargar todos los chunks del mundo
- usar `getLoadedChunks()` como fuente principal de trabajo
- ejecutar un escaneo completo para cada refill

SQLite es la fuente de candidatos.

Solo cargar el chunk necesario para un contenedor candidato.

---

# 9. Chunks descargados

Respetar:

```yaml
refill:
  conditions:
    refill-loaded-chunks-only: false
```

### Si `true`

Solo procesar contenedores cuyo chunk ya esté cargado.

No cargar chunks nuevos.

Si está descargado:

```text
SKIPPED_CHUNK_NOT_LOADED
```

El contenedor debe conservar su `next_refill` o recibir un pequeño retry delay, según la implementación.

No perder el refill.

### Si `false`

El sistema puede cargar el chunk necesario.

Debe utilizar la API asíncrona existente:

```java
world.getChunkAtAsync(x, z, false)
```

No usar:

```java
world.getChunkAt(x, z)
```

para cargar masivamente desde el scheduler.

---

# 10. Límite de chunks cargados

Respetar:

```yaml
max-chunks-loaded-by-refill: 1
```

El sistema no debe provocar una carga masiva accidental.

Si varios candidatos requieren chunks distintos, procesarlos gradualmente.

No cargar 100, 500 o 1000 chunks en un mismo ciclo.

---

# 11. Unload

Si el plugin cargó un chunk exclusivamente para hacer refill:

```yaml
chunks:
  unload-after-refill: true
```

debe solicitar su descarga después del procesamiento:

```java
world.unloadChunkRequest(chunkX, chunkZ);
```

Pero:

### IMPORTANTE

No descargar un chunk que:

- estaba cargado antes
- tiene jugadores
- está siendo utilizado por otro sistema
- es necesario para otro proceso
- está marcado como force-loaded

Registrar internamente si el plugin fue responsable de cargarlo.

Solo descargar chunks cargados exclusivamente por LootRefill.

---

# 12. Condición: contenedor vacío

Respetar:

```yaml
require-empty: true
```

Si el inventario contiene cualquier item:

```text
SKIPPED_NOT_EMPTY
```

No:

- borrar
- reemplazar
- mezclar
- completar stacks
- agregar loot encima

El contenido del jugador debe permanecer intacto.

Si `require-empty: false` se implementa posteriormente, debe definirse explícitamente el comportamiento antes de activarlo. En esta etapa, mantener `true`.

---

# 13. Condición: jugadores cercanos

Respetar:

```yaml
require-no-players-nearby: true
nearby-radius: 32.0
```

Antes de rellenar:

```text
buscar jugadores alrededor de la ubicación
```

Si hay un jugador dentro del radio:

```text
SKIPPED_PLAYER_NEARBY
```

No modificar el contenedor.

No considerar jugadores en otros mundos.

La distancia debe calcularse de forma eficiente.

No recorrer todos los jugadores online si puede evitarse.

---

# 14. Contenedores especiales

Actualmente:

```text
CHEST
TRAPPED_CHEST
BARREL
DISPENSER
DROPPER
FURNACE
BLAST_FURNACE
SMOKER
```

### Furnaces

Stage 3 indicó que:

```text
FURNACE
BLAST_FURNACE
SMOKER
```

están deshabilitados para automatic populate hasta tener manejo especializado.

Para Stage 4:

NO activar automáticamente estos tres tipos solamente por existir en config.

Mantenerlos deshabilitados para Auto Refill hasta que exista una implementación especializada segura.

Si el código ya soporta loot directo en su inventario y se decide habilitarlos, debe hacerse mediante una opción explícita:

```yaml
furnace:
  enabled: false
```

Por defecto:

```text
false
```

---

# 15. Dispensers y Droppers

Deben utilizar el mismo sistema general de inventario.

El loot se genera mediante `LootGenerator`.

No requieren lógica especial salvo validación de tipo.

---

# 16. Double Chest

Un double chest está compuesto por dos bloques físicos.

No se debe generar loot dos veces.

Utilizar la misma lógica de deduplicación de Stage 3.

Si ambos registros pertenecen al mismo double chest:

```text
→ tratar como una única unidad lógica
```

Si el sistema ya utiliza un identificador lógico para double chest, reutilizarlo.

No crear una segunda implementación paralela.

---

# 17. Generación del loot

Reutilizar:

```text
LootGenerator
ContainerPopulator
LootTable
LootEntry
```

No duplicar la lógica de generación.

El flujo debe ser:

```text
RefillManager
      ↓
RefillQueue
      ↓
validaciones
      ↓
LootGenerator
      ↓
ContainerPopulator
      ↓
inventario
```

El loot debe conservar:

- pesos
- rolls
- min/max amount
- max stack size
- slots aleatorios

---

# 18. Actualización de tiempos

Después de un refill exitoso:

```text
last_loot = now
next_refill = now + interval
```

El intervalo depende del `ContainerType`.

Ejemplo:

```text
CHEST:
next_refill = now + 1800 segundos
```

Guardar ambos valores en SQLite.

No calcular el próximo refill simplemente desde la hora del servidor cada vez que se inicia el plugin.

La persistencia debe sobrevivir reinicios.

---

# 19. ¿Qué pasa si un refill es omitido?

Ejemplo:

```text
next_refill = 12:00
jugador está cerca
```

A las 12:00 no se puede rellenar.

No mover inmediatamente el siguiente refill una hora hacia adelante.

Mantener la prioridad del contenedor.

Cuando vuelva a cumplir las condiciones:

```text
→ procesar
→ next_refill = now + interval
```

Esto evita que un contenedor pierda permanentemente ciclos de loot por estar siendo utilizado.

Agregar un pequeño retry delay en memoria o SQLite si es necesario para evitar revisar el mismo contenedor en cada tick.

Ejemplo:

```text
retry = 30 segundos
```

No modificar `last_loot` por un skip.

---

# 20. Estados de resultado

Crear un enum similar a:

```java
public enum RefillResult {
    SUCCESS,
    SKIPPED_NOT_EMPTY,
    SKIPPED_PLAYER_NEARBY,
    SKIPPED_CHUNK_NOT_LOADED,
    SKIPPED_INVALID_BLOCK,
    SKIPPED_TYPE_MISMATCH,
    SKIPPED_DISABLED,
    SKIPPED_NO_LOOT_TABLE,
    SKIPPED_BURNING,
    ERROR
}
```

No es obligatorio usar exactamente estos nombres si ya existe una estructura equivalente.

Los resultados deben permitir estadísticas y debug.

---

# 21. Manejo de errores

Un error en un contenedor no debe detener toda la queue.

Ejemplo:

```text
Container A → SUCCESS
Container B → ERROR
Container C → SUCCESS
```

El sistema continúa con C.

Registrar el error si:

```yaml
plugin:
  debug: true
```

No llenar el log con errores repetitivos cada tick.

---

# 22. Estadísticas

Agregar estadísticas al menos:

```text
Refills exitosos
Skipped: no vacío
Skipped: jugador cercano
Skipped: chunk no cargado
Skipped: bloque inválido
Skipped: tipo incorrecto
Errores
Contenedores pendientes
Chunks cargados por LootRefill
Chunks descargados por LootRefill
Tiempo promedio de procesamiento
```

Mostrar información básica en:

```text
/loot refill
```

Si existe una GUI de refill, integrarla.

---

# 23. Comandos

Mantener:

```text
/loot refill
```

Extender si es necesario:

```text
/loot refill status
/loot refill pause
/loot refill resume
/loot refill run
```

### `/loot refill`

Debe mostrar:

```text
Auto Refill: ACTIVADO
Pendientes: 143
Procesados este ciclo: 5
Último refill: ...
Próximo ciclo: ...
TPS: ...
```

### `/loot refill pause`

Pausa solamente el Auto Refill.

No debe afectar:

- scanner
- populate manual
- loot editor

### `/loot refill resume`

Reanuda.

### `/loot refill run`

Ejecuta un ciclo manual de los candidatos vencidos respetando:

- límites
- condiciones
- protección MAP/PLAYER
- queues

No hacer un refill masivo instantáneo.

---

# 24. GUI

Actualizar `RefillMenu`.

Mostrar:

```text
Auto Refill
Estado
Contenedores pendientes
Procesados
Skipped
Errores
Intervalos
Condiciones
```

Permitir, si ya existe la infraestructura:

- activar/desactivar Auto Refill
- abrir configuración
- ver queue
- ejecutar ciclo manual
- pausar/reanudar

No hacer modificaciones de configuración sin persistencia.

---

# 25. Compatibilidad con Stage 3

No romper:

```text
/loot populate
/loot assign
/loot register
```

El Auto Refill debe utilizar los datos generados por Stage 3.

Cuando un contenedor es asignado mediante:

```text
/loot assign
```

debe recibir correctamente su primer:

```text
next_refill
```

No necesariamente debe rellenarse inmediatamente.

La decisión recomendada:

```text
last_loot = NULL
next_refill = now
```

Esto permite que el contenedor sea candidato al siguiente ciclo.

Si el contenedor está ocupado, se respeta `require-empty`.

---

# 26. Scanner y Auto Refill

El scanner y Auto Refill son sistemas separados.

Mientras:

```text
/loot scan
```

está ejecutándose:

- Auto Refill puede continuar si no hay conflicto
- no modificar registros de identidad de forma peligrosa
- no hacer refill de contenedores recién descubiertos sin loot table

Un contenedor descubierto por scanner:

```text
MAP
ACTIVE
managed según scanner
registered según arquitectura existente
loot_table_id = NULL
```

No debe ser rellenado automáticamente hasta tener loot table.

---

# 27. Reinicio del servidor

Al reiniciar:

1. cargar configuración
2. abrir SQLite
3. verificar migraciones
4. cargar estado del Auto Refill
5. no resetear `last_loot`
6. no resetear `next_refill`
7. iniciar scheduler central
8. consultar candidatos vencidos
9. procesarlos gradualmente

Si hay 5000 contenedores vencidos:

```text
NO procesar los 5000 al iniciar.
```

Procesarlos progresivamente.

---

# 28. Rendimiento

Objetivo:

```text
max-ms-per-tick: 2
containers-per-tick: 5
```

No asumir que ambos límites siempre se pueden cumplir simultáneamente.

La prioridad es:

```text
1. seguridad
2. límite de tiempo
3. estabilidad
4. límite de contenedores
```

Si el procesamiento alcanza el presupuesto:

```text
→ detener inmediatamente el batch
→ continuar en el siguiente tick
```

No bloquear el hilo principal.

---

# 29. Thread safety

Regla fundamental:

### Main thread

Todo acceso/modificación Bukkit relacionado con:

- mundos
- chunks
- bloques
- inventories
- players

debe hacerse de acuerdo con las APIs seguras de Paper/Bukkit y, cuando corresponda, en main thread.

### Async

Se puede hacer async:

- SQLite
- consultas
- cálculos puros
- preparación de candidatos

Nunca modificar inventarios Bukkit desde un thread async.

---

# 30. No usar fuerza bruta

NO implementar:

```java
for (Chunk chunk : world.getLoadedChunks()) {
    ...
}
```

como mecanismo principal.

NO implementar:

```java
for (Chunk chunk : allWorldChunks) {
    ...
}
```

NO ejecutar:

```text
populate todos los contenedores
```

cada intervalo.

La base de datos debe indicar qué contenedores están vencidos.

---

# 31. Flujo completo esperado

```text
Servidor inicia
      ↓
LootRefill habilita Auto Refill
      ↓
SQLite
      ↓
buscar next_refill <= now
      ↓
crear candidatos
      ↓
RefillQueue
      ↓
máximo 5 / tick
      ↓
máximo 2 ms / tick
      ↓
validar MAP
      ↓
validar ACTIVE
      ↓
validar managed
      ↓
validar registered
      ↓
validar refill_enabled
      ↓
validar loot table
      ↓
validar bloque
      ↓
validar tipo
      ↓
validar chunk
      ↓
validar jugador cercano
      ↓
validar inventario vacío
      ↓
LootGenerator
      ↓
ContainerPopulator
      ↓
inventario
      ↓
last_loot = now
next_refill = now + interval
      ↓
SQLite
```

---

# 32. Testing obligatorio

Crear un mundo:

```text
LootRefillTest
```

No probar inicialmente sobre Exodus.

## Test 1 — Chest

Crear MAP chest con:

```text
/loot register common
```

Vacío.

Verificar que se llene automáticamente cuando `next_refill <= now`.

---

## Test 2 — Intervalo

Configurar temporalmente:

```yaml
chest:
  enabled: true
  interval: 30
```

Verificar:

```text
primer refill
↓
esperar
↓
vaciar chest
↓
segundo refill
```

Confirmar que `next_refill` se actualiza correctamente.

---

## Test 3 — Player container

Colocar chest como jugador.

Debe permanecer:

```text
PLAYER
managed=false
refill_enabled=false
```

Nunca debe recibir loot automático.

---

## Test 4 — Player container con contenido

Colocar objetos.

Esperar varios ciclos.

Los objetos deben permanecer intactos.

---

## Test 5 — MAP ocupado

MAP chest con objetos.

Auto Refill:

```text
SKIPPED_NOT_EMPTY
```

No borrar contenido.

---

## Test 6 — Jugador cercano

MAP chest vacío.

Acercarse al chest.

Cuando vence:

```text
SKIPPED_PLAYER_NEARBY
```

Alejarse.

En el siguiente intento válido:

```text
SUCCESS
```

---

## Test 7 — Chunk descargado

Crear MAP chest en un chunk que pueda descargarse.

Probar:

```yaml
refill-loaded-chunks-only: true
```

Debe posponer.

Luego:

```yaml
refill-loaded-chunks-only: false
```

Debe poder cargar solamente el chunk necesario.

---

## Test 8 — Broken

Crear MAP chest.

Romperlo.

Debe quedar:

```text
BROKEN
```

No debe recibir refill.

---

## Test 9 — Reemplazo

Romper MAP chest.

Colocar PLAYER chest exactamente en la misma ubicación.

Verificar:

```text
MAP original = BROKEN
PLAYER nuevo = PLAYER
```

El Auto Refill no debe tocar el nuevo chest.

---

## Test 10 — Tipo incorrecto

MAP chest registrado.

Romperlo.

Colocar barrel en la misma coordenada.

No debe heredar:

```text
loot_table
MAP
managed
refill
```

---

## Test 11 — Double chest

Crear double chest administrado.

Verificar que el refill genere loot una sola vez.

No duplicar el loot por cada mitad.

---

## Test 12 — Reinicio

Configurar un intervalo corto.

Dejar un contenedor con:

```text
next_refill futuro
```

Reiniciar servidor.

Verificar que la fecha se conserve.

Después del vencimiento debe procesarse normalmente.

---

## Test 13 — Muchos contenedores

Crear al menos:

```text
100
500
1000
```

contenedores MAP.

Forzar que estén vencidos.

Verificar:

- TPS estable
- máximo de procesamiento por tick
- queue progresiva
- no carga masiva de chunks
- SQLite estable

---

# 33. Logging de debug

Con:

```yaml
plugin:
  debug: true
```

permitir mensajes como:

```text
[Refill] Container 123 → SUCCESS
[Refill] Container 124 → SKIPPED_NOT_EMPTY
[Refill] Container 125 → SKIPPED_PLAYER_NEARBY
[Refill] Container 126 → SKIPPED_CHUNK_NOT_LOADED
```

Con debug false:

No registrar cada contenedor individual.

---

# 34. Migraciones

Las migraciones deben ser compatibles con instalaciones existentes.

No borrar:

```text
loot_tables
loot_entries
containers
populate_jobs
scan_jobs
```

Agregar únicamente lo necesario.

Antes de ejecutar SQL destructivo, verificar existencia de columnas/tablas.

No perder datos existentes.

---

# 35. Criterios de aceptación

Stage 4 se considera completada cuando:

- [ ] Auto Refill funciona automáticamente.
- [ ] Los intervalos son independientes por tipo.
- [ ] `next_refill` persiste en SQLite.
- [ ] `last_loot` persiste en SQLite.
- [ ] PLAYER nunca recibe Auto Refill.
- [ ] BROKEN nunca recibe Auto Refill.
- [ ] UNKNOWN nunca recibe Auto Refill.
- [ ] DISABLED nunca recibe Auto Refill.
- [ ] Solo MAP administrados pueden recibir refill.
- [ ] Loot table es obligatoria.
- [ ] Contenedores ocupados no se sobrescriben.
- [ ] Jugadores cercanos bloquean el refill.
- [ ] Chunks descargados se manejan correctamente.
- [ ] No hay carga masiva de chunks.
- [ ] Double chests no se duplican.
- [ ] Furnaces no se activan accidentalmente.
- [ ] La queue respeta el presupuesto por tick.
- [ ] El servidor no recibe un pico grande de TPS al vencer muchos contenedores.
- [ ] El sistema sobrevive reinicios.
- [ ] `/loot refill` muestra estado.
- [ ] `/loot refill pause` funciona.
- [ ] `/loot refill resume` funciona.
- [ ] `/loot refill run` respeta las queues.
- [ ] Los errores individuales no detienen el sistema.
- [ ] Stage 3 continúa funcionando.

---

# 36. Resultado esperado

Al finalizar Stage 4, LootRefill deberá comportarse como un sistema de loot persistente y escalable:

```text
               SQLITE
                  │
        ┌─────────┴─────────┐
        │   next_refill     │
        └─────────┬─────────┘
                  │
                  ▼
          REFILL MANAGER
                  │
                  ▼
            REFILL QUEUE
                  │
       ┌──────────┼──────────┐
       ▼          ▼          ▼
     CHEST       BARREL     OTHER
       │          │          │
       └──────────┼──────────┘
                  ▼
          5 contenedores/tick
             máximo 2 ms
                  │
                  ▼
          validaciones
                  │
                  ▼
          LootGenerator
                  │
                  ▼
        ContainerPopulator
                  │
                  ▼
             INVENTORY
                  │
                  ▼
               SQLITE
```

La implementación debe ser incremental, limpia y compatible con todo lo construido en Stage 1, Stage 2, Stage 2.1 y Stage 3.
