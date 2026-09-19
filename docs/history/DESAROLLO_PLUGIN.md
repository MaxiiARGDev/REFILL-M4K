Quiero desarrollar un plugin de Minecraft para **Paper 1.21.x** llamado **LootRefill**.

El objetivo final del plugin es administrar cofres y barriles de un mundo custom, asignarles distintos tipos de loot y permitir un sistema de refill automático por tiempo.

IMPORTANTE:
En esta primera etapa NO quiero implementar todavía todo el sistema de escaneo/refill. Quiero construir una **base sólida y modular**, comenzando por el **menú GUI de administración** y las estructuras de datos necesarias para que posteriormente podamos conectar el scanner, las loot tables y el sistema de refill.

## 1. Arquitectura

Utilizar Java y una arquitectura modular.

Propuesta:

src/main/java/
└── [package]/
├── LootRefillPlugin.java
│
├── command/
│   └── LootCommand.java
│
├── gui/
│   ├── AdminMenu.java
│   ├── LootTypesMenu.java
│   ├── LootEditorMenu.java
│   ├── RefillMenu.java
│   ├── ContainerMenu.java
│   └── GuiManager.java
│
├── loot/
│   ├── LootTable.java
│   ├── LootEntry.java
│   ├── LootManager.java
│   └── LootTableStorage.java
│
├── container/
│   ├── LootContainer.java
│   ├── ContainerManager.java
│   └── ContainerRegistry.java
│
├── refill/
│   ├── RefillManager.java
│   └── RefillTask.java
│
├── storage/
│   └── DatabaseManager.java
│
└── util/
├── ItemBuilder.java
├── GuiItem.java
└── MessageUtil.java

Podés modificar esta estructura si existe una arquitectura mejor, pero mantené una clara separación entre GUI, loot, containers, refill y persistencia.

## 2. Comando principal

Crear:

/loot

y subcomandos administrativos:

/loot admin
/loot reload
/loot scan
/loot refill
/loot help

Por ahora:

/loot admin

debe abrir el menú principal.

Implementar permisos:

lootrefill.admin

Los comandos deben tener tab completion.

## 3. Menú principal

Crear un menú GUI llamado:

"✦ LootRefill - Administración"

Usar un inventario de 54 slots.

Diseño visual limpio y profesional utilizando materiales vanilla.

El menú debe contener:

### Loot Types

Icono: CHEST

Nombre:

"§6Tipos de Loot"

Lore:

"§7Administrá las tablas de loot"
"§7disponibles para los contenedores."

Al hacer click:

Abrir LootTypesMenu.

### Containers

Icono: BARREL

Nombre:

"§eContenedores"

Lore:

"§7Administrá los cofres y barriles"
"§7registrados en el sistema."

Abrir ContainerMenu.

### Refill

Icono: CLOCK

Nombre:

"§bSistema de Refill"

Lore:

"§7Configurá y ejecutá refills."
"§7Administrá las colas de refill."

Abrir RefillMenu.

### Scanner

Icono: COMPASS

Nombre:

"§aScanner del Mundo"

Lore:

"§7Escanear contenedores del mundo."
"§8Disponible en una próxima etapa."

Por ahora mostrar información o dejarlo preparado, pero NO implementar todavía el scanner completo.

### Statistics

Icono: BOOK

Nombre:

"§dEstadísticas"

Mostrar:

* Contenedores registrados
* Cofres
* Barriles
* Loot tables
* Contenedores pendientes de refill
* Último scan
* Último refill

Por ahora puede mostrar valores obtenidos de los managers.

### Reload

Icono: REDSTONE

Nombre:

"§cRecargar configuración"

Ejecutar reload de configuraciones.

## 4. Menú Loot Types

Título:

"✦ LootRefill - Tipos de Loot"

Este menú debe mostrar todas las Loot Tables existentes.

Ejemplo:

COMMON
FOOD
MEDICAL
MILITARY
HIGH_TIER
RARE

Cada Loot Table debe aparecer como un item GUI.

Al hacer click izquierdo:

Abrir editor de esa Loot Table.

Click derecho:

Mostrar opciones adicionales.

Shift + click:

Eliminar, solicitando confirmación.

Agregar un botón:

"§a+ Crear Loot Table"

Al hacer click debe iniciar un flujo para crear una nueva tabla.

Si es necesario pedir un nombre mediante chat, implementar un sistema seguro de captura de input y permitir cancelar escribiendo:

cancel

## 5. Loot Table

Crear una clase:

LootTable

Debe tener como mínimo:

* id
* displayName
* description
* enabled
* minItems
* maxItems
* entries

LootEntry debe tener:

* item
* weight/chance
* minAmount
* maxAmount
* enabled

Preparar la arquitectura para soportar posteriormente:

* Items vanilla
* ItemsAdder
* items custom
* comandos como recompensa si posteriormente se decide agregarlo

NO acoplar LootTable directamente a Bukkit Inventory.

## 6. Editor de Loot Table

Título:

"✦ Editando: <nombre>"

El editor debe permitir:

* Cambiar nombre
* Activar/desactivar
* Configurar cantidad mínima de objetos
* Configurar cantidad máxima
* Agregar items
* Eliminar items
* Editar chance/peso
* Editar cantidad mínima
* Editar cantidad máxima
* Guardar

Agregar:

"§aGuardar cambios"

y:

"§cCancelar"

Para agregar un item, permitir tomar un item del inventario del administrador o usar una interfaz posterior.

Idealmente crear un flujo:

1. Click "Agregar item"
2. El jugador coloca el item en un slot especial
3. Configura:

   * Weight
   * Min amount
   * Max amount
4. Confirmar
5. El item queda registrado en la Loot Table.

## 7. Menú Refill

Título:

"✦ LootRefill - Refill"

Mostrar:

### Estado global

* Sistema activado/desactivado
* Intervalo configurado
* Contenedores pendientes
* Próximo refill

### Acciones

"§aEjecutar Refill"

Ejecuta refill manual.

"§eCola de Refill"

Muestra los contenedores pendientes.

"§bConfiguración"

Permite configurar:

* Intervalo global
* Radio de seguridad
* Si requiere que el cofre esté vacío
* Si puede regenerarse aunque haya jugadores cerca

### Cola de refill

Mostrar una lista de contenedores:

World
X
Y
Z
Tipo
Loot Table
Tiempo restante

Permitir seleccionar un contenedor y ejecutar:

* Refill ahora
* Cancelar refill
* Resetear estado

## 8. Sistema de Containers

Crear una clase:

LootContainer

Debe representar un contenedor registrado.

Datos mínimos:

* UUID/id interno
* world
* x
* y
* z
* containerType
* lootTableId
* enabled
* looted
* lastLoot
* nextRefill

containerType:

CHEST
BARREL
TRAPPED_CHEST

NO asumir que todos los containers tienen loot.

Debe ser posible registrar posteriormente un container manualmente.

## 9. Persistencia

No depender exclusivamente de memoria.

Crear una capa:

DatabaseManager

Preferentemente utilizar SQLite.

Separar completamente la persistencia de la lógica del plugin.

Tablas iniciales:

loot_tables
loot_entries
containers

Diseñar el esquema para que posteriormente pueda agregarse:

refill_history
scan_history

El plugin debe guardar y cargar correctamente la información después de reiniciar el servidor.

## 10. GUI Manager

Crear un sistema centralizado para las GUIs.

Debe:

* Identificar correctamente qué menú está abierto
* Manejar clicks
* Evitar que los jugadores puedan retirar items decorativos
* Evitar exploits mediante shift-click
* Evitar drag events
* Evitar double-click exploits
* Permitir cerrar y volver al menú anterior
* Mantener una navegación consistente

No depender de comparar únicamente el título del inventario para identificar una GUI.

Utilizar una identificación segura mediante clases, holders personalizados o un sistema equivalente.

## 11. Diseño

La interfaz debe tener estética de panel administrativo.

Usar:

* BLACK_STAINED_GLASS_PANE
* GRAY_STAINED_GLASS_PANE
* GOLD
* CHEST
* BARREL
* CLOCK
* COMPASS
* BOOK
* REDSTONE
* LIME_DYE
* RED_DYE

Crear bordes y espacios visuales.

No llenar todo de información innecesaria.

El menú debe sentirse como un verdadero panel de administración.

## 12. Seguridad

Todos los comandos y menús deben comprobar:

lootrefill.admin

No permitir que jugadores normales puedan abrir o utilizar los menús administrativos.

No permitir manipular accidentalmente items reales del jugador dentro de las interfaces.

Validar todos los datos antes de guardarlos.

Evitar NullPointerException cuando un mundo, container o Loot Table no exista.

## 13. Configuración

Crear:

plugins/LootRefill/config.yml

Con algo similar:

plugin:
debug: false

refill:
enabled: true
interval: 1800
require-empty: true
require-no-players-nearby: true
nearby-radius: 32

containers:
allow-chests: true
allow-barrels: true
allow-trapped-chests: true

storage:
type: sqlite

messages:
prefix: "&6&lLootRefill &8» "

No hardcodear configuraciones que deberían estar en config.yml.

## 14. Preparación para futuras versiones

La arquitectura debe quedar preparada para agregar posteriormente:

V2:

/loot scan <world>

Scanner de todos los cofres/barriles del mapa.

V3:

Asignación automática de Loot Tables por:

* mundo
* región
* coordenadas
* tipo de edificio

V4:

Refill automático individual.

V5:

Compatibilidad con ItemsAdder.

V6:

Sistema de estadísticas e historial.

V7:

Sistema de loot avanzado con:

* weighted loot
* guaranteed items
* loot tiers
* rolls
* rarity
* blacklist
* cooldown individual
* protección contra jugadores cerca

## 15. Reglas importantes

NO implementar todo de golpe.

Primero crear una versión funcional de:

1. Plugin principal
2. /loot admin
3. GUI principal
4. Loot Types
5. Crear Loot Table
6. Editar Loot Table
7. Persistencia SQLite
8. Refill GUI básico
9. Container model
10. Managers y arquitectura preparada para scanner/refill

El código debe compilar en **Paper 1.21.x**.

Utilizar APIs modernas de Paper/Bukkit.

Evitar NMS salvo que sea estrictamente necesario.

Evitar dependencias innecesarias.

Antes de escribir código, analizar la arquitectura y explicar brevemente qué clases se crearán y cómo se relacionarán.

Después implementar el código completo de esta primera etapa.

El resultado debe ser un proyecto Maven o Gradle listo para compilar como:

LootRefill.jar
