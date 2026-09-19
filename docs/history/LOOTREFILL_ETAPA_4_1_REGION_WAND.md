# LootRefill — Etapa 4.1: Region Wand

## Objetivo
Agregar una herramienta administrativa para seleccionar dos puntos A/B, escanear únicamente esa región en busca de almacenamiento y registrar los contenedores nuevos como `MAP`, sin romper Stage 2.1, 3 ni 4.

## Wand
Comando:
```text
/loot wand
```
Permiso: `lootrefill.admin`.

El wand debe identificarse mediante `PersistentDataContainer`, no solo por nombre/material.

- Click izquierdo: Punto A.
- Click derecho: Punto B.
- `/loot region clear`: limpia selección.
- `/loot region info`: muestra mundo, A/B, límites, tamaño y volumen.

A y B deben pertenecer al mismo mundo.

## Escaneo
Comando:
```text
/loot region scan
```

Debe procesar solo chunks que intersecten la región, usar los tipos habilitados y reutilizar la detección existente. No debe modificar inventarios, bloques ni asignar loot.

Detectar:
- CHEST
- TRAPPED_CHEST
- BARREL
- FURNACE
- BLAST_FURNACE
- SMOKER
- DISPENSER
- DROPPER

Debe ser escalonado y respetar límites de tiempo por tick.

## Preview y registro
Comando:
```text
/loot region assign
```
Primero mostrar preview y pedir confirmación.

Al confirmar, solo los contenedores nuevos pasan a:
```text
source=MAP
status=ACTIVE
managed=true
registered=true
```

No asignar todavía LootTable ni LootPool y no modificar contenido.

### Regla crítica
`PLAYER` nunca debe convertirse automáticamente en `MAP`, incluso con `--force`.

`MAP` existente no debe duplicarse ni perder configuración. `BROKEN` no se restaura automáticamente.

## Double Chest
Mantener deduplicación existente: un double chest físico no debe registrarse dos veces.

## Comandos
```text
/loot wand
/loot region info
/loot region clear
/loot region scan
/loot region assign
```
Actualizar `/loot help`.

## Rendimiento
No iterar todos los bloques si puede utilizar tile entities. Evitar cargas innecesarias de chunks y cualquier modificación Bukkit async.

## Criterios
1. Scan de una casa detecta solo su almacenamiento.
2. Preview no modifica nada.
3. Confirmación registra nuevos como MAP.
4. PLAYER permanece PLAYER.
5. Repetir scan no duplica.
6. Regiones grandes no producen caída apreciable de TPS.

## Fuera de alcance
Loot Pools, selección aleatoria, MIXED_RANDOM e historial.
