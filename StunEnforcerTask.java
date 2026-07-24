package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;

/**
 * Runs every tick, zeroing velocity for every entity currently stunned by
 * Hand Of Zeus and cleaning up expired entries. Kept as one central task
 * (rather than a per-instance timer like IceCageTask) specifically because
 * the bolt-chain ability needs to safely OVERRIDE an in-progress stun's
 * duration without a race between two separate expiring timers.
 */
public class StunEnforcerTask extends BukkitRunnable {

    private final CombatListener combat;

    public StunEnforcerTask(CombatListener combat) {
        this.combat = combat;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<java.util.UUID, Long>> it = combat.stunned.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<java.util.UUID, Long> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
                continue;
            }
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null) continue;
            Vector v = entity.getVelocity();
            entity.setVelocity(new Vector(0, Math.min(v.getY(), 0), 0));
        }
    }
}
