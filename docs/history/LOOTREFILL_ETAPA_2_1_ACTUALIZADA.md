# LootRefill — Etapa 2.1
## Identidad de contenedores, protección de almacenadores de jugadores y consistencia del registro

### Objetivo

Extender la Etapa 2 existente de LootRefill **sin rehacer el scanner ni la arquitectura actual**.

Esta etapa debe resolver una cuestión fundamental antes de implementar Populate y el refill real:

> LootRefill debe distinguir entre los almacenadores originales del mapa y cualquier almacenador que un jugador coloque posteriormente.

Esto aplica a **todos los tipos de almacenamiento administrados por LootRefill**, no solamente a cofres.

Tipos actuales:

- CHEST
- TRAPPED_CHEST
- BARREL
- FURNACE
- BLAST_FURNACE
- SMOKER
- DISPENSER
- DROPPER

La arquitectura debe permitir agregar nuevos tipos posteriormente sin rehacer el sistema.

---

# 1. Regla fundamental

La existencia de un bloque de almacenamiento en el mundo **NO significa que LootRefill pueda administrarlo**.

Un almacenamiento solo puede ser administrado por LootRefill si cumple todas las condiciones necesarias:

```text
registered = true
managed = true
source = MAP
status = ACTIVE
loot_table_id != null
```

Para una operación de refill también:

```text
refill_enabled = true
next_refill <= currentTime
```

Si un contenedor es colocado por un jugador:

```text
source = PLAYER
managed = false
registered = false
```

y debe quedar completamente fuera de:

- Populate
- Refill
- Loot generation
- RefillQueue
- asignación automática
- modificaciones automáticas de inventario

---

# 2. Esto aplica a TODOS los tipos

No limitar esta lógica a:

```text
CHEST
BARREL
```

Debe funcionar igual para:

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

Ejemplo:

```text
MAP CHEST       → administrable
PLAYER CHEST    → ignorar

MAP BARREL      → administrable
PLAYER BARREL   → ignorar

MAP FURNACE     → administrable
PLAYER FURNACE  → ignorar

MAP DISPENSER   → administrable
PLAYER DISPENSER → ignorar
```

La lógica debe estar basada en `ContainerType` y en el origen del contenedor, no en código específico para cofres.

---

# 3. Modelo de ContainerType

Mantener la arquitectura existente:

```text
ContainerType
```

Debe ser la fuente central para determinar los tipos administrables.

No crear una clase especial solamente para Chest.

Preparar el diseño para agregar posteriormente:

```text
HOPPER
BREWING_STAND
SHULKER_BOX
CHEST_MINECART
HOPPER_MINECART
```

sin modificar el sistema de identidad MAP/PLAYER.

---

# 4. Origen del contenedor

Agregar al modelo `LootContainer`:

```text
source
```

Valores:

```text
MAP
PLAYER
UNKNOWN
```

### MAP

Contenedor que pertenece al mapa y que puede ser administrado por LootRefill.

### PLAYER

Contenedor colocado por un jugador.

Debe ser ignorado automáticamente.

### UNKNOWN

Origen que no pueda determinarse.

Por seguridad:

```text
UNKNOWN = NO administrar
```

No permitir que UNKNOWN entre en Populate ni Refill.

---

# 5. Estado del contenedor

Agregar:

```text
status
```

Valores:

```text
ACTIVE
BROKEN
DISABLED
```

### ACTIVE

El almacenamiento registrado existe actualmente en la posición y su tipo coincide.

### BROKEN

El almacenamiento MAP registrado fue destruido.

El registro NO debe eliminarse.

### DISABLED

El almacenamiento existe pero fue desactivado administrativamente.

---

# 6. Modelo recomendado

`LootContainer` debe contener como mínimo:

```text
id
world
x
y
z
type
source
status
managed
registered
loot_table_id
refill_enabled
last_loot
next_refill
created_at
updated_at
```

La identidad física continúa siendo:

```text
world + x + y + z
```

Crear una restricción UNIQUE apropiada para impedir duplicados físicos.

IMPORTANTE:

La coordenada identifica la ubicación de un registro, pero **no demuestra que un nuevo bloque colocado posteriormente sea el mismo almacenamiento original**.

---

# 7. Scan inicial = origen MAP

El primer escaneo administrativo de un mundo debe establecer qué almacenadores existentes pertenecen al mapa.

Ejemplo:

```text
/loot scan lobby
```

Durante este scan inicial, todos los tipos habilitados encontrados deben registrarse como:

```text
source = MAP
registered = true
managed = true
status = ACTIVE
```

Esto incluye todos los tipos habilitados:

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

NO asumir posteriormente que cualquier almacenamiento encontrado durante otro scan pertenece al mapa.

---

# 8. BlockBreakEvent para TODOS los tipos

Implementar un listener genérico basado en `ContainerType`.

Cuando se rompe un almacenamiento:

```text
¿Es un ContainerType administrable?
        |
        +-- NO → ignorar
        |
        +-- SÍ
              |
              v
       ¿Existe registro?
              |
              +-- NO → ignorar
              |
              +-- SÍ
                    |
                    v
             ¿source == MAP?
                    |
             +------+------+
             |             |
            NO            SÍ
             |             |
       comportamiento    status = BROKEN
          normal         managed = false
                         refill_enabled = false
```

NO eliminar el registro MAP de SQLite.

NO programar refill.

NO generar loot.

---

# 9. BlockPlaceEvent para TODOS los tipos

Cuando un jugador coloque cualquier almacenamiento administrable:

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

debe quedar identificado como:

```text
source = PLAYER
managed = false
registered = false
```

No asignar automáticamente una Loot Table.

No agregarlo a la RefillQueue.

No modificar su inventario.

No convertirlo automáticamente en MAP.

---

# 10. Coordenada reutilizada

Caso obligatorio:

```text
1. MAP CHEST
2. jugador lo rompe
3. registro original → BROKEN
4. jugador coloca CHEST en exactamente la misma coordenada
```

El nuevo CHEST debe ser:

```text
source = PLAYER
managed = false
registered = false
```

NO debe recuperar automáticamente:

```text
loot_table_id
source = MAP
managed = true
```

Lo mismo debe ocurrir con:

```text
MAP BARREL → PLAYER BARREL
MAP FURNACE → PLAYER FURNACE
MAP DISPENSER → PLAYER DISPENSER
```

y cualquier futuro `ContainerType`.

---

# 11. Cambio de tipo en la misma posición

Caso:

```text
MAP CHEST
↓
BREAK
↓
PLAYER BARREL
```

El sistema NO debe considerar que el BARREL es el CHEST original.

Antes de cualquier administración:

```text
tipo registrado == tipo físico actual
```

debe cumplirse.

Si no coincide:

```text
NO loot
NO refill
NO populate
```

---

# 12. Revalidación antes de modificar inventarios

Antes de cualquier operación que pueda modificar un almacenamiento:

```text
1. registro existe
2. world existe
3. bloque existe
4. bloque actual corresponde a ContainerType
5. tipo físico == tipo registrado
6. source == MAP
7. status == ACTIVE
8. managed == true
9. registered == true
10. loot_table_id != null
```

Para refill:

```text
11. refill_enabled == true
12. next_refill <= currentTime
```

Si cualquiera falla:

```text
NO modificar inventario.
```

Si el bloque ya no existe:

```text
status = BROKEN
managed = false
refill_enabled = false
```

Si el tipo no coincide:

```text
NO administrar.
```

---

# 13. Casos especiales por tipo

El sistema de identidad MAP/PLAYER debe ser genérico, pero las condiciones de uso pueden variar por tipo.

No implementar todavía toda la lógica específica de refill, pero dejar preparada una arquitectura de condiciones.

Por ejemplo, posteriormente:

```yaml
furnace:
  require-empty: true
  require-not-burning: true

blast-furnace:
  require-empty: true
  require-not-burning: true

smoker:
  require-empty: true
  require-not-burning: true
```

Mientras que:

```yaml
chest:
  require-empty: true

barrel:
  require-empty: true
```

No asumir que todos los contenedores tienen el mismo comportamiento.

---

# 14. Containers de jugadores deben quedar completamente fuera

Un contenedor `PLAYER` no debe aparecer en:

```text
RefillQueue
```

ni en:

```text
Populate
```

ni en:

```text
loot assignment
```

ni en:

```text
automatic loot generation
```

ni en:

```text
refill statistics
```

como contenedor administrable.

Puede aparecer en estadísticas separadas:

```text
Player containers: 382
```

pero nunca debe contarse como contenedor gestionado.

---

# 15. Registro manual

Mantener:

```text
/loot register <loot_table>
```

Esta acción representa una decisión administrativa explícita.

Cuando un administrador ejecuta el comando mirando un almacenamiento:

```text
source = MAP
registered = true
managed = true
status = ACTIVE
loot_table_id = <tabla>
```

Esto debe funcionar para TODOS los tipos compatibles.

Ejemplo:

```text
/loot register military
```

sobre:

```text
CHEST
BARREL
FURNACE
DISPENSER
```

etc.

El plugin debe validar que el bloque apuntado sea un `ContainerType` soportado.

Mostrar:

```text
Contenedor registrado como MAP.
Tipo: BARREL
Loot Table: MILITARY
```

---

# 16. Scans posteriores

El scanner existente NO debe convertir automáticamente todos los almacenadores encontrados en MAP.

Después del scan inicial:

```text
PLAYER → sigue siendo PLAYER
BROKEN → sigue siendo BROKEN
```

Nunca hacer:

```text
scan → PLAYER → MAP
```

ni:

```text
scan → BROKEN → MAP
```

Si un administrador quiere incorporar un nuevo almacenamiento del mundo al sistema, debe hacerlo mediante una acción administrativa explícita.

---

# 17. GUI ContainerMenu

Actualizar la GUI existente.

Mostrar:

```text
Tipo:
CHEST

Origen:
MAP

Estado:
ACTIVE

Administrado:
SÍ

Loot Table:
MILITARY

Refill:
ACTIVADO
```

Para PLAYER:

```text
Tipo:
BARREL

Origen:
PLAYER

Estado:
ACTIVE

Administrado:
NO

§cLootRefill no administra este almacenamiento.
```

Para BROKEN:

```text
Tipo:
CHEST

Origen:
MAP

Estado:
BROKEN

Administrado:
NO

§cEl almacenamiento original fue destruido.
```

---

# 18. Estadísticas

Actualizar las estadísticas del panel principal:

```text
Contenedores MAP
Contenedores PLAYER
Contenedores BROKEN
Contenedores DISABLED
Contenedores administrables
```

Desglosar por tipo:

```text
MAP:
  Chest: 100
  Barrel: 50
  Furnace: 20
  Dispenser: 15

PLAYER:
  Chest: 200
  Barrel: 80
  Furnace: 30
```

No mezclar ambos grupos.

---

# 19. SQLite y migraciones

Modificar la base de datos existente sin eliminar información.

Agregar una versión de esquema:

```text
schema_version
```

Si es necesario agregar columnas:

```text
source
status
managed
registered
created_at
updated_at
```

hacer una migración segura.

NO borrar:

```text
data.db
```

NO recrear la base completa.

NO perder:

- loot tables
- loot entries
- containers
- scan jobs

---

# 20. Integridad de SQLite

Crear constraints apropiados.

Evitar duplicados.

Mantener:

```text
UNIQUE(world, x, y, z)
```

si la arquitectura actual lo permite.

Las operaciones de actualización de estado deben ser atómicas cuando sea necesario.

---

# 21. Protección contra concurrencia

Pueden ocurrir situaciones como:

```text
RefillQueue
     +
BlockBreakEvent
```

al mismo tiempo.

Ejemplo:

```text
Refill intenta procesar CHEST
↓
jugador rompe CHEST
```

El sistema debe volver a validar el contenedor justo antes de modificar el inventario.

Nunca asumir que el registro de SQLite sigue siendo válido solamente porque estaba en la cola.

---

# 22. Bukkit / Paper thread safety

Mantener las restricciones existentes:

Operaciones relacionadas con:

```text
World
Chunk
Block
BlockState
Inventory
Player
```

deben ejecutarse en el thread correcto según Paper.

No mover eventos Bukkit a async.

SQLite y cálculos puros pueden ejecutarse async cuando sea seguro.

---

# 23. Protección especial para hornos

Los siguientes tipos:

```text
FURNACE
BLAST_FURNACE
SMOKER
```

pueden estar siendo utilizados por jugadores.

No implementar todavía el refill específico, pero preparar el sistema para comprobar posteriormente:

```text
burning
cooking
inventory empty
```

No modificar un horno que esté siendo utilizado.

---

# 24. Doble cofre

Revisar cuidadosamente cómo Paper representa un:

```text
DOUBLE CHEST
```

No generar registros duplicados accidentalmente.

Si el sistema necesita representar una entidad física como dos bloques, decidir una estrategia consistente.

La identidad y el origen MAP/PLAYER deben continuar siendo correctos.

Probar:

- cofre simple
- cofre doble
- romper una mitad
- romper la otra mitad
- colocar un cofre nuevo en la posición

No implementar lógica compleja innecesaria si Paper ya proporciona una representación adecuada.

---

# 25. Protección de Populate y Refill

Aunque Populate todavía no esté implementado completamente, dejar los managers preparados para rechazar:

```text
PLAYER
UNKNOWN
BROKEN
DISABLED
managed = false
registered = false
```

Las consultas SQL deben filtrar los estados cuando sea posible.

Ejemplo:

```sql
SELECT *
FROM containers
WHERE source = 'MAP'
  AND status = 'ACTIVE'
  AND managed = 1
  AND registered = 1
  AND refill_enabled = 1;
```

---

# 26. No implementar restauración todavía

NO implementar todavía:

```text
auto restore
```

ni recrear bloques destruidos.

Solamente guardar:

```text
BROKEN
```

para que en una etapa futura podamos implementar:

```text
/loot container restore
```

o:

```yaml
restore-broken-map-containers: true
```

---

# 27. Configuración futura

No es obligatorio cambiar toda la configuración en esta etapa, pero dejar preparada la arquitectura para algo como:

```yaml
containers:

  chest:
    enabled: true
    refill: true

  barrel:
    enabled: true
    refill: true

  furnace:
    enabled: true
    refill: false

  blast-furnace:
    enabled: true
    refill: false

  smoker:
    enabled: true
    refill: false

  dispenser:
    enabled: true
    refill: true

  dropper:
    enabled: true
    refill: true
```

La propiedad `enabled` actual puede continuar significando detección/soporte mientras no exista una configuración separada.

Si se modifica semántica, documentarla claramente.

---

# 28. Pruebas obligatorias

Crear un mundo de prueba.

Ejemplo:

```text
LootTest
```

Colocar antes del scan:

```text
10 CHEST
10 TRAPPED_CHEST
10 BARREL
10 FURNACE
10 BLAST_FURNACE
10 SMOKER
10 DISPENSER
10 DROPPER
```

Ejecutar:

```text
/loot scan LootTest
```

Verificar que se registren correctamente como:

```text
MAP
ACTIVE
managed = true
registered = true
```

---

# 29. Prueba de contenedores de jugador

Después del scan:

colocar:

```text
5 CHEST
5 TRAPPED_CHEST
5 BARREL
5 FURNACE
5 BLAST_FURNACE
5 SMOKER
5 DISPENSER
5 DROPPER
```

Verificar que sean:

```text
PLAYER
managed = false
registered = false
```

No deben aparecer como administrables.

---

# 30. Prueba de destrucción

Romper un contenedor MAP de cada tipo.

Esperado:

```text
status = BROKEN
managed = false
refill_enabled = false
```

El registro debe permanecer en SQLite.

---

# 31. Prueba de reemplazo

Después de romper un contenedor MAP:

colocar el mismo tipo en exactamente la misma posición.

Esperado:

```text
nuevo contenedor = PLAYER
```

No recuperar el registro MAP automáticamente.

---

# 32. Prueba de cambio de tipo

Romper:

```text
MAP CHEST
```

y colocar:

```text
PLAYER BARREL
```

en la misma posición.

Esperado:

```text
NO loot
NO refill
NO populate
```

---

# 33. Prueba de persistencia

Reiniciar el servidor.

Verificar que:

```text
MAP
PLAYER
BROKEN
DISABLED
```

mantengan su estado correctamente.

---

# 34. Prueba de RefillQueue

Agregar temporalmente contenedores MAP y PLAYER a una situación donde puedan ser candidatos.

Verificar que:

```text
MAP + ACTIVE + managed = true
```

pueda entrar.

Pero:

```text
PLAYER
BROKEN
DISABLED
UNKNOWN
```

NO entre.

---

# 35. Prueba de /loot register

Probar:

```text
/loot register common
```

sobre:

- Chest
- Barrel
- Furnace
- Blast Furnace
- Smoker
- Dispenser
- Dropper

Verificar que todos puedan registrarse correctamente si están soportados.

---

# 36. Debug

Con:

```yaml
plugin:
  debug: false
```

NO escribir un log por cada BlockPlace o BlockBreak.

Con:

```yaml
plugin:
  debug: true
```

permitir mensajes útiles como:

```text
[DEBUG] MAP container broken:
Exodus 125 68 -532
Type: CHEST

[DEBUG] PLAYER container placed:
Exodus 125 68 -532
Type: CHEST

[DEBUG] Ignoring PLAYER container during refill:
Exodus 125 68 -532
Type: BARREL
```

---

# 37. Compatibilidad

Mantener:

- Paper 1.21.x
- Java 21+
- SQLite
- Maven/Gradle existente
- arquitectura modular de Etapa 1 y Etapa 2

NO rehacer:

- RegionFileScanner
- WorldScanner
- RefillQueue
- LootManager

salvo que sea estrictamente necesario.

NO eliminar funcionalidades existentes.

NO introducir NMS salvo necesidad real.

---

# 38. Criterios de aceptación

La etapa está terminada cuando:

- LootRefill distingue MAP y PLAYER.
- La distinción funciona para TODOS los ContainerType actuales.
- Los contenedores colocados por jugadores son ignorados.
- Romper cualquier contenedor MAP lo marca como BROKEN.
- El registro BROKEN permanece en SQLite.
- Un nuevo contenedor colocado en la misma posición queda como PLAYER.
- Un cambio de tipo en una posición no recupera el registro anterior.
- PLAYER nunca entra en Populate/Refill.
- BROKEN nunca entra en Populate/Refill.
- DISABLED nunca entra en Populate/Refill.
- UNKNOWN nunca entra en Populate/Refill.
- El scan inicial registra los contenedores existentes como MAP.
- Los scans posteriores no convierten PLAYER en MAP.
- `/loot register` permite registro administrativo explícito para todos los tipos soportados.
- La GUI muestra tipo, origen y estado.
- SQLite migra instalaciones existentes sin borrar datos.
- No existen duplicados físicos.
- BlockPlaceEvent y BlockBreakEvent funcionan para todos los tipos.
- No existen operaciones Bukkit inseguras en async.
- El plugin compila en Paper 1.21.x / Java 21.

---

# 39. Antes de programar

Primero:

1. Revisar todo el código existente de Etapa 1 y Etapa 2.
2. Identificar:
   - ContainerType
   - LootContainer
   - ContainerManager
   - ContainerRegistry
   - WorldScanner
   - RefillQueue
   - DatabaseManager
   - ContainerMenu
3. Explicar qué archivos serán modificados.
4. Explicar la migración SQLite.
5. Explicar cómo se distinguirán MAP y PLAYER para todos los tipos.
6. Explicar el flujo genérico BlockBreakEvent / BlockPlaceEvent.
7. Explicar cómo se evitará que PLAYER/BROKEN/DISABLED/UNKNOWN entren en las colas.
8. Explicar el tratamiento de cofres dobles.
9. Explicar cómo se evitarán condiciones de carrera entre eventos y RefillQueue.
10. No rehacer componentes que ya funcionan.

Después implementar esta etapa completa.

Al finalizar:

- indicar archivos modificados
- indicar migraciones SQLite
- indicar pruebas realizadas
- indicar resultado de compilación
- indicar ruta del JAR generado
- indicar cualquier limitación detectada
