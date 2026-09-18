package com.arcraft.lootrefill.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class LootGenerator {

    private LootGenerator() {}

    /**
     * Genera una lista de ItemStacks basada en la LootTable indicada, aplicando selección
     * ponderada relativa, rangos de ítems y división por tamaño máximo de stack.
     */
    public static List<ItemStack> generate(LootTable table) {
        if (table == null || !table.isEnabled() || table.getMaxItems() <= 0) {
            return Collections.emptyList();
        }

        List<LootEntry> activeEntries = new ArrayList<>();
        int totalWeight = 0;

        for (LootEntry entry : table.getEntries()) {
            if (entry != null && entry.isEnabled() && entry.getWeight() > 0) {
                activeEntries.add(entry);
                totalWeight += entry.getWeight();
            }
        }

        if (activeEntries.isEmpty() || totalWeight <= 0) {
            return Collections.emptyList();
        }

        Random random = ThreadLocalRandom.current();
        int minItems = Math.max(0, table.getMinItems());
        int maxItems = Math.max(minItems, table.getMaxItems());
        int rolls = minItems == maxItems ? minItems : minItems + random.nextInt(maxItems - minItems + 1);

        List<ItemStack> result = new ArrayList<>(rolls);

        for (int i = 0; i < rolls; i++) {
            int pick = random.nextInt(totalWeight);
            int current = 0;

            for (LootEntry entry : activeEntries) {
                current += entry.getWeight();
                if (pick < current) {
                    ItemStack baseItem = entry.getItem();
                    if (baseItem == null || baseItem.getType() == Material.AIR) {
                        break;
                    }

                    int minAmount = Math.max(1, entry.getMinAmount());
                    int maxAmount = Math.max(minAmount, entry.getMaxAmount());
                    int totalAmount = minAmount == maxAmount ? minAmount : minAmount + random.nextInt(maxAmount - minAmount + 1);

                    int maxStackSize = baseItem.getMaxStackSize();
                    if (maxStackSize <= 0) maxStackSize = 64;

                    // Si la cantidad total supera el stack máximo del ítem, dividirlo en varios stacks
                    while (totalAmount > 0) {
                        int stackAmount = Math.min(totalAmount, maxStackSize);
                        ItemStack item = baseItem.clone();
                        item.setAmount(stackAmount);
                        result.add(item);
                        totalAmount -= stackAmount;
                    }
                    break;
                }
            }
        }

        return result;
    }
}
