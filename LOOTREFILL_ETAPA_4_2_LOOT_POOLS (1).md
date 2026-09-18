# LootRefill — Etapa 4.2: Loot Pools

## Objetivo
Permitir que un contenedor utilice un `LootPool` en lugar de una única LootTable fija. En cada refill el pool vuelve a seleccionar aleatoriamente una o varias tablas.

Flujo:
```text
Container → LootPool → selección ponderada → LootTable(s) → LootGenerator
```

## LootTable
Continúa definiendo los items, pesos, rolls, cantidades y generación de slots.

## LootPool
Crear entidad:
```text
id
name
selection_mode
min_rolls
max_rolls
enabled
created_at
updated_at
```

El pool contiene LootTables y sus pesos.

Ejemplo:
```text
HOUSE
COMMON=40
FOOD=30
MEDICAL=20
RARE=10
```

Los pesos no tienen que sumar 100.

## Modos

### SINGLE_RANDOM
Una sola tabla por refill:
```text
FOOD → COMMON → FOOD → RARE
```
Las repeticiones están permitidas.

### MIXED_RANDOM
Seleccionar entre `min_rolls` y `max_rolls` tablas:
```text
COMMON + FOOD + MEDICAL
```

Por defecto las repeticiones también están permitidas.

## Configuración
```yaml
loot-pools:
  house:
    enabled: true
    selection:
      mode: SINGLE_RANDOM
      rolls:
        min: 1
        max: 1
    tables:
      common:
        weight: 40
      food:
        weight: 30
      medical:
        weight: 20
      rare:
        weight: 10

  military:
    enabled: true
    selection:
      mode: MIXED_RANDOM
      rolls:
        min: 2
        max: 4
    tables:
      common:
        weight: 10
      food:
        weight: 10
      medical:
        weight: 20
      military:
        weight: 45
      high_tier:
        weight: 10
      rare:
        weight: 5
```

Si SQLite es la fuente principal de configuración, mantener YAML como defaults/fallback y evitar dos fuentes de verdad.

## Contenedor
Agregar:
```text
loot_pool_id
```

Compatibilidad:
```text
loot_pool_id != null → usar Pool
loot_pool_id == null && loot_table_id != null → usar Legacy Table
sin ambos → no generar
```

No romper los datos existentes.

## Refill
Cada refill debe volver a sortear el pool:
```text
15:00 FOOD
15:15 COMMON
15:30 MEDICAL
15:45 FOOD
```

El pool no debe quedar convertido permanentemente en una tabla.

## MIXED_RANDOM
Puede producir:
```text
FOOD + COMMON
```
o:
```text
MEDICAL + COMMON + FOOD
```
Los items de todas las tablas seleccionadas pueden coexistir.

## Historial
Guardar opcionalmente las últimas 5 selecciones para administración/debug:
```text
FOOD
COMMON
MEDICAL
FOOD
RARE
```

## Asignación
Agregar:
```text
/loot region assign-pool <pool>
```
con preview y confirmación.

Puede existir también una asignación individual equivalente si encaja con los comandos actuales.

## Validación
Pool inexistente, deshabilitado, sin tablas o con tablas/pesos inválidos debe producir un resultado seguro como:
```text
SKIPPED_INVALID_LOOT_POOL
```
sin limpiar inventarios ni romper el scheduler.

## Populate y Auto Refill
Ambos deben poder usar Pools. Conservar `require-empty`, round-robin, control de chunks y protección PLAYER/BROKEN.

## Criterios
- Variación entre tablas.
- Repeticiones permitidas.
- MIXED_RANDOM funciona.
- Pool inválido no rompe el sistema.
- Legacy sigue funcionando.
