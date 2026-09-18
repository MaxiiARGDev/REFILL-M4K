package com.arcraft.lootrefill.refill;

import org.bukkit.scheduler.BukkitRunnable;

public class RefillTask extends BukkitRunnable {

    private final RefillManager refillManager;

    public RefillTask(RefillManager refillManager) {
        this.refillManager = refillManager;
    }

    @Override
    public void run() {
        if (!refillManager.isEnabled()) {
            return;
        }
        // El procesamiento escalonado y continuo por tick es gestionado por RefillQueue.
    }
}
