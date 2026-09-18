package com.arcraft.lootrefill.container;

public enum ContainerSource {
    /**
     * Contenedor que pertenece al mapa original o fue registrado administrativamente.
     * Puede ser administrado por LootRefill.
     */
    MAP,

    /**
     * Contenedor colocado por un jugador.
     * Debe ser ignorado automáticamente por Populate y Refill.
     */
    PLAYER,

    /**
     * Origen desconocido o indeterminado. Por seguridad nunca se administra.
     */
    UNKNOWN;

    public static ContainerSource fromString(String name) {
        if (name == null) return UNKNOWN;
        try {
            return ContainerSource.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
