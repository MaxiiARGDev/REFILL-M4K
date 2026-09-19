# LootRefill — Etapa 3
## Populate + Loot Generation + asignación controlada de Loot Tables

### Estado previo

Las etapas 1, 2 y 2.1 ya están implementadas y probadas. No rehacerlas.

Esta etapa implementa el primer sistema real de generación y colocación de loot.

## Objetivos

- Generar loot ponderado desde LootTable.
- Poblar únicamente contenedores MAP/ACTIVE administrados.
- Implementar Populate escalonado.
- Implementar modo PREVIEW sin modificar inventarios.
- Permitir asignar Loot Tables por tipo de contenedor.
- Mantener protección absoluta de PLAYER/BROKEN/DISABLED/UNKNOWN.
- Preparar `last_loot`, `next_refill` y `refill_enabled` para la Etapa 4.
- Mantener compatibilidad Paper 1.21.x / Java 21.

## 1. Regla de seguridad

Populate solo puede modificar un contenedor si:

```text
source = MAP
status = ACTIVE
managed = true
registered = true
loot_table_id != null
```

Además, el bloque físico debe existir y su tipo debe coincidir con el tipo registrado.

Nunca modificar:

```text
PLAYER
UNKNOWN
BROKEN
DISABLED
managed = false
registered = false
loot_table_id = null
```

La validación debe repetirse justo antes de modificar el inventario.

## 2. Arquitectura

Separar responsabilidades:

```text
LootTable
    ↓
LootGenerator
    ↓
List<ItemStack>
    ↓
PopulateQueue
    ↓
ContainerPopulator
    ↓
Bukkit Inventory
```

Crear, si no existen:

```text
loot/LootGenerator.java
populate/PopulateManager.java
populate/PopulateQueue.java
populate/PopulateTask.java
populate/PopulateResult.java
```

`LootTable` no debe modificar directamente inventarios Bukkit.

## 3. LootGenerator

Crear `LootGenerator`.

Debe:

- recibir una LootTable
- ignorar entries deshabilitadas
- seleccionar entries por peso relativo
- respetar minItems/maxItems
- respetar minAmount/maxAmount
- generar ItemStacks válidos
- devolver una lista de ItemStacks

Ejemplo:

```text
Bread = 50
Apple = 30
Iron = 15
Rare = 5
```

No exigir que los pesos sumen 100.

Validar:

```text
weight > 0
minAmount >= 1
maxAmount >= minAmount
minItems >= 0
maxItems >= minItems
```

Si una cantidad supera el máximo stack size, dividir correctamente en varios ItemStacks.

## 4. Randomización

No crear `new Random()` repetidamente por cada item.

Usar un RNG apropiado y reutilizable.

Los resultados entre contenedores deben poder variar.

## 5. Inventarios

Crear `ContainerPopulator`.

Para Populate inicial:

```yaml
populate:
  require-empty: true
```

Si el inventario contiene cualquier item:

```text
SKIPPED_NOT_EMPTY
```

No borrar ni sobrescribir items existentes.

Nunca usar `clear()` como parte de Populate.

## 6. Slots aleatorios

Los items no deben aparecer siempre desde slot 0.

Barajar los slots disponibles y distribuir el loot en posiciones aleatorias.

No implementar todavía algoritmos complejos de balanceo.

## 7. Tipos

Soportar inicialmente:

```text
CHEST
TRAPPED_CHEST
BARREL
DISPENSER
DROPPER
```

Los tipos:

```text
FURNACE
BLAST_FURNACE
SMOKER
```

deben permanecer registrados/asignables, pero NO deben recibir loot automáticamente en esta etapa hasta definir correctamente sus slots y condiciones.

Preparar la arquitectura para habilitarlos posteriormente.

## 8. Configuración Populate

Integrar sin romper la configuración existente:

```yaml
populate:
  enabled: true
  require-empty: true

  processing:
    containers-per-tick: 5
    max-ms-per-tick: 2
    batch-delay-ticks: 1

  container-types:
    chest:
      enabled: true
    trapped-chest:
      enabled: true
    barrel:
      enabled: true
    furnace:
      enabled: false
    blast-furnace:
      enabled: false
    smoker:
      enabled: false
    dispenser:
      enabled: true
    dropper:
      enabled: true
```

Si ya existe una sección equivalente, integrarla en vez de duplicarla.

## 9. Preview

Implementar:

```text
/loot populate <world> preview
```

Debe:

- no modificar inventarios
- no guardar cambios de loot
- no iniciar Populate real
- mostrar cantidad de contenedores elegibles
- mostrar skipped
- mostrar distribución por tipo y Loot Table
- indicar explícitamente que no hubo modificaciones

Ejemplo:

```text
LootRefill Populate Preview
━━━━━━━━━━━━━━━━━━━━━━━━━━━━

World: lobby

MAP containers: 191
Eligible: 187
Skipped: 4

CHEST: 58
BARREL: 127
DISPENSER/DROPPER: 2

No containers were modified.
```

## 10. Populate real

Implementar:

```text
/loot populate <world>
```

Pedir una confirmación global antes de modificar inventarios.

Si se usa comando:

```text
/loot populate lobby confirm
```

No pedir confirmación individual por contenedor.

## 11. PopulateQueue

Nunca modificar cientos o miles de inventarios en un solo tick.

Usar cola progresiva:

```text
Tick 1 → hasta 5
Tick 2 → hasta 5
Tick 3 → hasta 5
...
```

Respetar:

```yaml
populate.processing.containers-per-tick
populate.processing.max-ms-per-tick
```

Prioridad:

1. estabilidad
2. TPS
3. velocidad

## 12. Presupuesto temporal

Medir `System.nanoTime()` por batch.

Si se alcanza `max-ms-per-tick`, detener el batch y continuar en el siguiente tick.

No monopolizar el main thread.

## 13. Chunks

Antes de modificar un container:

- comprobar si el chunk está cargado
- si está cargado, procesar
- si no está cargado, utilizar una estrategia controlada compatible con Paper 1.21.x
- no cargar cientos de chunks simultáneamente
- no dejar chunks permanentemente cargados

Las operaciones Bukkit deben ejecutarse en el thread apropiado.

## 14. Revalidación física

Justo antes de insertar loot comprobar:

```text
world existe
block existe
tipo físico coincide
registro existe
source == MAP
status == ACTIVE
managed == true
registered == true
loot_table existe
```

Si falla cualquier condición:

```text
SKIPPED_INVALID
```

No modificar el inventario.

## 15. Protección PLAYER

Incluso si un PLAYER:

- fue encontrado por un scan posterior
- está en una coordenada usada anteriormente por MAP
- tiene el mismo tipo
- aparece en una cola antigua

debe rechazarse.

Regla absoluta:

```text
source != MAP → NO POPULATE
```

## 16. Protección BROKEN / DISABLED

Nunca poblar:

```text
status = BROKEN
status = DISABLED
managed = false
registered = false
```

## 17. Sin Loot Table

Si:

```text
loot_table_id = null
```

resultado:

```text
SKIPPED_NO_LOOT_TABLE
```

No generar loot.

## 18. Asignación por tipo

Mantener:

```text
/loot register <loot_table>
```

para asignación individual.

Agregar:

```text
/loot assign <world> <container_type> <loot_table>
```

Ejemplos:

```text
/loot assign lobby chest common
/loot assign lobby barrel food
/loot assign lobby dispenser military
```

Solo modificar:

```text
MAP
ACTIVE
managed = true
registered = true
```

No tocar PLAYER/BROKEN/DISABLED.

## 19. Preview de asignación

Implementar:

```text
/loot assign <world> <container_type> <loot_table> preview
```

Mostrar:

```text
Assignment Preview

World: lobby
Type: CHEST
Loot Table: COMMON

MAP containers found: 58
Eligible: 58
Already assigned: 0

No changes made.
```

Confirmación:

```text
/loot assign lobby chest common confirm
```

## 20. No sobreescribir Loot Tables existentes

Por defecto, `/loot assign` solo debe afectar containers sin Loot Table.

No sobrescribir:

```text
loot_table_id != null
```

Agregar `--force` únicamente si la arquitectura lo necesita posteriormente.

## 21. Resultados

Crear estadísticas:

```text
processed
populated
skipped
skipped_not_empty
skipped_no_loot_table
skipped_player
skipped_broken
skipped_disabled
skipped_invalid
errors
```

Resumen:

```text
Populate completado

Procesados: 187
Poblados: 180
Saltados: 7
Errores: 0
```

## 22. GUI

Agregar al panel administrativo un acceso:

```text
Populate / Generar Loot
```

Crear `PopulateMenu`.

Mostrar:

```text
World
MAP containers
Eligible
Sin Loot Table
Último Populate
```

Botones:

```text
[ PREVIEW ]
[ POPULATE ]
[ HISTORIAL ]
```

Seleccionar mundos válidos mediante GUI cuando corresponda.

## 23. Persistencia

Después de poblar correctamente:

```text
last_loot
```

y preparar:

```text
next_refill
```

No iniciar todavía el refill automático.

Crear `PopulateJob` con:

```text
id
world
status
total
processed
populated
skipped
started_at
updated_at
completed_at
```

Estados:

```text
RUNNING
PAUSED
COMPLETED
CANCELLED
ERROR
```

Si es posible, persistir el job en SQLite para recuperación segura.

## 24. Cancelación

Implementar:

```text
/loot populate cancel
```

Debe:

- detener la cola
- guardar estado
- no dejar tasks activas
- no corromper SQLite
- no dejar chunks permanentemente cargados

## 25. Duplicación

Si un container entra dos veces accidentalmente en una cola:

- no generar loot dos veces
- identificar por ID del container
- comprobar estado antes de procesar

## 26. SQLite

Utilizar:

- prepared statements
- transacciones
- batches cuando corresponda

No hacer commits innecesarios por cada container.

No bloquear el main thread esperando SQLite.

## 27. Items custom

NO implementar ItemsAdder todavía.

Preparar una abstracción futura, por ejemplo:

```text
LootItemProvider
```

para soportar posteriormente:

```text
VANILLA
ITEMSADDER
COMMAND
CUSTOM
```

No añadir la dependencia de ItemsAdder en esta etapa.

## 28. Double Chest

Revisar cómo Paper representa un doble cofre.

No:

- generar loot dos veces
- duplicar el inventario
- romper la identidad MAP/PLAYER

Preferir un único inventario lógico para un Double Chest si es compatible con la API actual.

Probar:

- cofre simple
- cofre doble
- romper una mitad
- romper la otra mitad
- colocar un cofre nuevo

## 29. Furnace / Blast Furnace / Smoker

No poblarlos todavía por defecto.

Preparar soporte futuro para:

```text
inventory slots
burning
cooking
require-empty
```

No introducir loot de forma que interfiera con la fundición.

## 30. Logging

Con:

```yaml
plugin:
  debug: false
```

no escribir un log por cada container.

Mostrar solo:

```text
Populate iniciado
Populate completado
Resumen
Errores importantes
```

Con debug:

```text
[DEBUG] Populated MAP CHEST ...
[DEBUG] Skipped PLAYER BARREL ...
[DEBUG] Skipped BROKEN CHEST ...
```

## 31. Prueba recomendada

Crear:

```text
LootTest
```

Con:

```text
20 CHEST
20 BARREL
5 DISPENSER
5 DROPPER
5 FURNACE
```

Escanear.

Asignar:

```text
CHEST → COMMON
BARREL → FOOD
DISPENSER → MILITARY
DROPPER → MILITARY
```

## 32. Prueba Preview

Ejecutar:

```text
/loot populate LootTest preview
```

Comprobar:

- ningún inventario fue modificado
- las estadísticas son correctas
- PLAYER no es elegible
- BROKEN no es elegible
- sin Loot Table aparece como skipped

## 33. Prueba Populate

Ejecutar:

```text
/loot populate LootTest
```

Comprobar:

- solo MAP recibe loot
- solo ACTIVE recibe loot
- solo managed/registered recibe loot
- PLAYER permanece intacto
- BROKEN permanece intacto
- DISABLED permanece intacto
- inventarios ocupados no se sobrescriben
- slots variados
- resultados diferentes entre containers

## 34. Prueba de contenedores de jugador

Después del scan colocar:

```text
CHEST
BARREL
FURNACE
DISPENSER
DROPPER
```

Ejecutar Populate.

Esperado:

```text
PLAYER → no tocar
```

## 35. Prueba de inventario ocupado

Colocar manualmente un item en un MAP container.

Ejecutar Populate.

Esperado:

```text
SKIPPED_NOT_EMPTY
```

El item debe permanecer.

## 36. Prueba de reinicio

Ejecutar Populate sobre un mundo suficientemente grande y reiniciar durante el proceso.

Verificar:

- no duplicación de loot
- SQLite íntegra
- no tasks huérfanas
- no chunks permanentemente cargados
- recuperación segura del job si está implementada

## 37. Criterios de aceptación

La etapa está terminada cuando:

- LootGenerator funciona.
- Weighted loot funciona.
- Min/max de cantidad funciona.
- Min/max de rolls funciona.
- Items deshabilitados no participan.
- Populate solo toca MAP/ACTIVE/managed/registered.
- PLAYER nunca recibe loot.
- BROKEN nunca recibe loot.
- DISABLED nunca recibe loot.
- Inventarios ocupados no se sobrescriben.
- Loot se distribuye en slots aleatorios.
- Populate utiliza una cola escalonada.
- Populate respeta el presupuesto de tiempo.
- Preview no modifica inventarios.
- `/loot assign` funciona.
- No sobrescribe tablas existentes por defecto.
- SQLite conserva resultados.
- No existen duplicaciones de procesamiento.
- Double Chest se maneja correctamente.
- Furnace/Blast Furnace/Smoker permanecen protegidos.
- Compila con Java 21.
- Funciona en Paper 1.21.x.
- No rompe las etapas anteriores.

## 38. Antes de programar

Primero:

1. Revisar toda la arquitectura existente.
2. No rehacer componentes funcionales.
3. Revisar LootTable y LootEntry.
4. Revisar ContainerType.
5. Revisar LootContainer.
6. Revisar ContainerManager.
7. Revisar DatabaseManager.
8. Revisar RefillQueue.
9. Revisar GUIs.
10. Explicar archivos que se modificarán.
11. Explicar LootGenerator.
12. Explicar PopulateQueue.
13. Explicar protección PLAYER/BROKEN/DISABLED.
14. Explicar chunks/threading.
15. Explicar Double Chest.
16. Explicar Furnace/Blast Furnace/Smoker.
17. Explicar migraciones SQLite.

Después implementar la Etapa 3 completa.

Al finalizar indicar:

- archivos creados
- archivos modificados
- migraciones SQLite
- pruebas realizadas
- resultado de compilación
- ruta del JAR
- limitaciones o comportamientos especiales detectados
