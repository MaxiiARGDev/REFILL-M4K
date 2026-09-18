package com.arcraft.lootrefill.container;

public enum ContainerStatus {
    /**
     * El almacenamiento existe en la posición y su tipo físico coincide.
     */
    ACTIVE,

    /**
     * El almacenamiento fue destruido. El registro se conserva en la base de datos
     * pero no participa en refill ni populate.
     */
    BROKEN,

    /**
     * El almacenamiento existe físicamente pero fue desactivado administrativamente.
     */
    DISABLED;

    public static ContainerStatus fromString(String name) {
        if (name == null) return ACTIVE;
        try {
            return ContainerStatus.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
