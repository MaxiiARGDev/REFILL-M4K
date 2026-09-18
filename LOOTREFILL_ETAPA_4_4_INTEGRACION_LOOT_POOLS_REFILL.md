# LootRefill — Etapa 4.4: Integración Loot Pools + Auto Refill

## Objetivo
Integrar Loot Pools con el Auto Refill actual sin modificar la arquitectura de scheduler, round-robin, condiciones y protección de contenedores.

## Flujo
```text
MAP + ACTIVE + managed + registered
→ refill_enabled
→ next_refill vencido
→ RefillQueue
→ LootPool
→ Weighted Random
→ 1..N LootTables
→ LootGenerator
→ ContainerPopulator
→ SUCCESS
→ actualizar last_loot y next_refill
```

## Condiciones existentes
Mantener:
```text
source=MAP
status=ACTIVE
managed=true
registered=true
refill_enabled=true
```

Excluir:
```text
PLAYER
BROKEN
DISABLED
UNKNOWN
```

Respetar tipo físico, contenedores habilitados, hornos en uso, jugadores cercanos, `require-empty`, chunks y límites de procesamiento.

## require-empty
Con:
```yaml
require-empty: true
```
un contenedor con contenido produce:
```text
SKIPPED_NOT_EMPTY
```
Nunca hacer `inventory.clear()` para forzar el refill.

## Selección por ciclo
Cada refill debe seleccionar nuevamente:
```text
FOOD
→ COMMON
→ MEDICAL
→ FOOD
```

## SINGLE_RANDOM
Una tabla por refill según pesos.

## MIXED_RANDOM
Entre min/max rolls y varias tablas en un mismo inventario.

## Round-robin
No alterar el scheduler:
```text
CHEST
TRAPPED_CHEST
BARREL
DISPENSER
DROPPER
...
```
Un skip no bloquea otros tipos.

## Límites
Conservar:
```yaml
containers-per-tick: 5
max-ms-per-tick: 2
batch-delay-ticks: 1
```
Nunca procesar 125 de golpe en un tick.

## Chunks
Mantener carga/descarga controlada y no cargar todo Exodus simultáneamente.

## Estadísticas
Distinguir:
```text
SUCCESS
SKIPPED_NOT_EMPTY
SKIPPED_PLAYER_NEARBY
SKIPPED_CHUNK_NOT_LOADED
SKIPPED_DISABLED
SKIPPED_BURNING
SKIPPED_INVALID_LOOT_POOL
ERROR
```

Opcionalmente contar tablas seleccionadas:
```text
COMMON
FOOD
MEDICAL
MILITARY
HIGH_TIER
RARE
```

## Debug
Con `plugin.debug=true`:
```text
[Refill] CHEST | pool=house | tables=FOOD | SUCCESS
[Refill] BARREL | pool=house | tables=COMMON,MEDICAL | SUCCESS
```
No generar spam con debug=false.

## Prueba de 125
Usar:
```text
25 CHEST
25 TRAPPED_CHEST
25 BARREL
25 DISPENSER
25 DROPPER
```

Medir:
- TPS inicial
- TPS mínimo
- TPS final
- procesados
- éxitos
- skips
- errores
- tiempo total
- máximo de cola
- chunks cargados/descargados

## Prueba de variación
Pool:
```text
COMMON=50
FOOD=50
```
Comprobar que aparecen ambas tablas y que las repeticiones son posibles.

## Prueba MIXED_RANDOM
```yaml
mode: MIXED_RANDOM
rolls:
  min: 1
  max: 3
```
Verificar múltiples tablas en un mismo refill.

## Compatibilidad
Prioridad:
```text
loot_pool_id != null → Pool
loot_pool_id == null → loot_table_id legacy
```
No migrar automáticamente todos los registros.

## Criterios finales
- Region Wand funciona.
- Registro MAP seguro.
- PLAYER protegido.
- Container Manager categorizado.
- Pools ponderados.
- SINGLE_RANDOM.
- MIXED_RANDOM.
- Nueva selección en cada refill.
- Round-robin intacto.
- require-empty intacto.
- Chunks controlados.
- 125 contenedores procesables sin colapso de TPS.
- Legacy compatible.
