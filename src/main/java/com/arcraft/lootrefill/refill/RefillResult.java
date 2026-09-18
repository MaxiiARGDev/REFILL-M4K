package com.arcraft.lootrefill.refill;

public enum RefillResult {
    SUCCESS("§aRefill completado con éxito"),
    SKIPPED_NOT_EMPTY("§cOmitido: Contenedor no está vacío"),
    SKIPPED_PLAYER_NEARBY("§cOmitido: Jugador cerca del contenedor"),
    SKIPPED_CHUNK_NOT_LOADED("§eOmitido: Chunk no cargado"),
    SKIPPED_INVALID_BLOCK("§cOmitido: Bloque físico no existe o es inválido"),
    SKIPPED_TYPE_MISMATCH("§cOmitido: El tipo de bloque físico no coincide con el registrado"),
    SKIPPED_DISABLED("§8Omitido: Contenedor o tipo deshabilitado"),
    SKIPPED_NO_LOOT_TABLE("§cOmitido: Sin tabla de loot asignada o válida"),
    SKIPPED_BURNING("§6Omitido: Horno en uso o fundiendo"),
    SKIPPED_DOUBLE_CHEST_PAIR("§7Omitido: Doble cofre ya rellenado en este ciclo"),
    ERROR("§4Error durante el proceso de refill");

    private final String description;

    RefillResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
