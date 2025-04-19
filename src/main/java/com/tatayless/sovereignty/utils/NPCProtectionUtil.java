package com.tatayless.sovereignty.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

/**
 * Utility class for NPC protection settings
 */
public class NPCProtectionUtil {

    /**
     * Apply all protection settings to an NPC entity
     * 
     * @param npc The villager entity to protect
     */
    public static void applyFullProtection(Villager npc) {
        npc.setInvulnerable(true);
        npc.setAI(false);
        npc.setPersistent(true);
        npc.setRemoveWhenFarAway(false);
        npc.setCollidable(false);
        npc.setSilent(true);
    }

    /**
     * Check if an entity has full NPC protections
     *
     * @param entity The entity to check
     * @return true if fully protected
     */
    public static boolean hasFullProtection(Entity entity) {
        if (!(entity instanceof Villager)) {
            return false;
        }

        Villager villager = (Villager) entity;
        return villager.isInvulnerable() &&
                !villager.hasAI() &&
                villager.isPersistent() &&
                !villager.getRemoveWhenFarAway() &&
                !villager.isCollidable() &&
                villager.isSilent();
    }
}
