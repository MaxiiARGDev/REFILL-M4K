# LootRefill — FIX 4.4.1: Auto Refill / Intervalos / Persistencia

El diagnóstico confirmó la causa del problema de Auto Refill.

**NO implementar todavía Loot Pools ni nuevas funcionalidades.** Este trabajo es exclusivamente para corregir Auto Refill y la persistencia de `next_refill`.

## 1. Problema confirmado

Los contenedores tienen actualmente:

- `config.yml` → intervalo de 10 segundos.
- SQLite → `refill_interval_seconds = 1800`.

El código está utilizando el valor histórico de SQLite en lugar del intervalo actual de `config.yml`.

Además, `ContainerManager.saveContainersBatch()` no está actualizando correctamente `next_refill` dentro de `ON CONFLICT DO UPDATE`.

Esto provocó que:

- 17 contenedores tuvieran `next_refill` aproximadamente 19 minutos en el futuro.
- otros contenedores tuvieran `next_refill = NULL`.
- `/loot refill status` mostrara 0 contenedores vencidos.
- Auto Refill no procesara nada.

## 2. Config.yml debe ser la fuente de verdad

Para Auto Refill y Populate, el intervalo efectivo debe obtenerse de la configuración actual:

```yaml
refill:
  intervals:
    chest:
      interval: 10
    trapped-chest:
      interval: 10
    barrel:
      interval: 10
    dispenser:
      interval: 10
    dropper:
      interval: 10
```

Utilizar:

```java
plugin.getRefillManager().getIntervalForType(type)
```

o el método equivalente existente.

**NO utilizar como prioridad:**

```java
container.getRefillIntervalSeconds()
```

si ese valor solamente representa un valor histórico almacenado en SQLite.

## 3. Cambio de configuración

Si mañana cambio:

```yaml
interval: 10
```

a:

```yaml
interval: 900
```

los contenedores deben utilizar el nuevo intervalo de 900 segundos sin tener que reasignar manualmente la LootTable.

El valor histórico `refill_interval_seconds` puede permanecer en SQLite por compatibilidad, pero **NO debe tener prioridad** sobre la configuración actual.

## 4. Populate

Cuando `ContainerPopulator.populate(...)` termina correctamente:

```text
next_refill = now + intervalo_actual_de_config.yml
```

Ejemplo:

```text
intervalo actual = 10 segundos
next_refill = now + 10000 ms
```

NO utilizar 1800 segundos solamente porque ese valor esté almacenado en el contenedor.

## 5. Asignación de LootTable

Cuando `/loot assign ...` asigna una LootTable a un contenedor:

Si:

```text
refill_enabled = true
```

debe establecer:

```text
next_refill = now
```

según la lógica actual del plugin.

Y este valor debe quedar correctamente persistido en SQLite.

## 6. Fix `saveContainersBatch()`

Revisar:

```text
ContainerManager.saveContainersBatch(...)
```

En:

```sql
ON CONFLICT DO UPDATE SET
```

agregar explícitamente:

```sql
next_refill = excluded.next_refill
```

si todavía no existe.

Revisar también que todos los campos que `LootContainer` modifica y que deben persistirse estén incluidos correctamente.

**NO hacer cambios innecesarios en otras columnas.**

## 7. Contenedores sin Loot

Mantener exactamente este comportamiento:

```text
loot_table_id = NULL
loot_pool_id = NULL
next_refill = NULL
```

Un contenedor sin LootTable ni LootPool:

- no es elegible para Auto Refill;
- no debe aparecer como vencido;
- no debe entrar en RefillQueue;
- no debe generar errores ni spam.

## 8. No implementar Loot Pools todavía

No implementar:

- LootPool.
- MIXED_RANDOM.
- SINGLE_RANDOM.
- weighted pool selection.
- historial de pools.
- nuevas GUIs de pools.
- asignación regional de pools.

Eso será una etapa posterior.

## 9. Scheduler

**NO modificar la arquitectura actual del scheduler** salvo que sea estrictamente necesario para corregir este problema.

Mantener:

- RefillManager.
- RefillQueue.
- round-robin.
- containers-per-tick.
- max-ms-per-tick.
- batch-delay-ticks.
- chunk management.
- require-empty.

No cambiar la protección de:

- PLAYER.
- BROKEN.
- DISABLED.
- UNKNOWN.

## 10. Diagnóstico

Mantener temporalmente los comandos:

```text
/loot container debug <id>
/loot refill debug
```

`/loot container debug` debe mostrar claramente:

```text
Intervalo almacenado: 1800s
Intervalo config: 10s
Intervalo efectivo: 10s
```

cuando corresponda.

También mostrar:

- `next_refill`
- `last_loot`
- `isDue()`
- `isEligibleForRefill()`
- motivo exacto cuando no sea elegible.

## 11. Pruebas obligatorias

Después de compilar y desplegar:

### TEST A — Populate

Utilizar los contenedores actuales que tienen:

```text
LootTable = common
refill_enabled = true
```

Ejecutar Populate.

Comprobar que `next_refill` quede aproximadamente:

```text
ahora + 10 segundos
```

**NO** aproximadamente 30 minutos.

### TEST B — Auto Refill

Esperar aproximadamente 15–20 segundos.

Ejecutar:

```text
/loot refill status
```

El sistema debe detectar o haber procesado los contenedores vencidos.

### TEST C — require-empty

Los contenedores están llenos porque fueron poblados.

La configuración tiene:

```yaml
require-empty: true
```

Por lo tanto, Auto Refill debe producir:

```text
SKIPPED_NOT_EMPTY
```

y NO:

```text
SUCCESS
```

Nunca debe borrar el inventario.

### TEST D — Persistencia

Después de que un contenedor tenga un `next_refill` válido:

1. Reiniciar el servidor.
2. Ejecutar `/loot container debug <id>`.
3. Verificar que `next_refill` siga correctamente persistido.
4. Comprobar que Auto Refill continúe funcionando.

### TEST E — TPS

Durante la prueba:

```text
TPS ≈ 20
```

No debe producirse una caída significativa.

## 12. No hacer una solución temporal

**NO solucionar el problema poniendo manualmente todos los `next_refill` en `now`.**

La corrección debe ser estructural:

```text
config.yml
     ↓
intervalo efectivo
     ↓
Populate / Auto Refill
     ↓
next_refill
     ↓
SQLite
```

## 13. Build

Ejecutar:

```text
mvn clean package
```

Debe terminar con:

```text
BUILD SUCCESS
```

Después entregar un walkthrough indicando:

- archivos modificados;
- métodos modificados;
- causa corregida;
- cómo se determina ahora el intervalo efectivo;
- cómo se persiste `next_refill`;
- resultado de los tests A, B, C, D y E.

**NO avanzar a Loot Pools hasta que este fix esté probado.**
