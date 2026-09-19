# LootRefill — Etapa 4.3: Container Manager 2.0

## Objetivo
Rediseñar `Contenedores` para administrar grandes cantidades de registros mediante categorías, filtros, búsqueda y paginación.

## Menú principal
Organizar como:
```text
MAP
PLAYER
BROKEN
POR TIPO
LOOT POOLS
REFILL
FILTROS
```

Mostrar cantidades reales desde SQLite.

## MAP
Subcategorías:
- CHEST
- TRAPPED_CHEST
- BARREL
- FURNACE
- BLAST_FURNACE
- SMOKER
- DISPENSER
- DROPPER

## PLAYER
Solo informativo y claramente identificado. Las acciones normales nunca deben convertirlo accidentalmente en MAP.

## BROKEN
Permitir consultar:
- coordenadas
- tipo
- loot asociado
- fecha/estado

No restaurar automáticamente.

## Filtros
Combinar:
```text
Origen: MAP / PLAYER / BROKEN
Estado: ACTIVE / DISABLED / BROKEN
Loot: Pool / Legacy Table / Sin Loot
Refill: habilitado / deshabilitado
Tipo: todos los tipos soportados
```

## Búsqueda
Permitir buscar por:
- ID
- mundo
- coordenadas
- tipo
- Loot Pool
- Loot Table

Para grandes cantidades usar consultas SQLite filtradas y paginadas.

## Paginación
Nunca cargar cientos/miles de contenedores innecesariamente en una sola GUI.
Mostrar página actual y navegación anterior/siguiente.

## Información individual
Mostrar:
```text
ID
Tipo
Origen
Estado
Managed
Registered
Loot Pool
Loot Table Legacy
Refill
Último refill
Próximo refill
Último resultado
```

Con Stage 4.2, mostrar últimas selecciones del Pool.

## Acciones
Para MAP:
- cambiar Pool
- cambiar Legacy Table
- activar/desactivar refill
- ver ubicación
- ver estado
- ver historial

Cambios destructivos deben pedir confirmación.

## Ubicación
Mostrar mundo, X/Y/Z y chunk. Teleport opcional solo con permiso administrativo.

## Estadísticas
Mostrar:
```text
MAP
PLAYER
BROKEN
MAP activos
Con Pool
Con Legacy
Sin Loot
Refill activo
Refill desactivado
```

## Integración
Los cambios hechos mediante Region Wand deben reflejarse inmediatamente.

## Seguridad
```text
PLAYER → nunca convertir automáticamente a MAP
BROKEN → no restaurar automáticamente
UNKNOWN → no administrar como MAP
```

## Rendimiento
La GUI no debe cargar inventarios ni chunks. Consultas grandes deben ser paginadas.

## Criterios
- Categorías claras.
- Filtros combinables.
- Búsqueda.
- Paginación.
- Información individual completa.
- PLAYER claramente protegido.
- Funciona con miles de registros.
