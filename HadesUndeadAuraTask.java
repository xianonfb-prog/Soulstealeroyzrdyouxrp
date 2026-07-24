package com.exotic.plugin;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs for the lifetime of a Hades' Lament summon: a recurring particle aura
 * so it's visually obvious the mob is Hades-summoned, plus a "follow the
 * owner when there's nothing to fight" behavior vanilla mobs don't have.
 */
public class HadesUndeadAuraTask extends BukkitRunnable {

    private final ExoticPlugin plugin;
    private final LivingEntity mob;
    private final Player owner;

    public HadesUndeadAuraTask(ExoticPlugin plugin, LivingEntity mob, Player owner) {
        this.plugin = plugin;
        this.mob = mob;
        this.owner = owner;
    }

    @Override
    public void run() {
        if (!mob.isValid() || mob.isDead()) {
            cancel();
            return;
        }
        if (!owner.isOnline()) {
            cancel();
            return;
        }

        mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, mob.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.01);
        mob.getWorld().spawnParticle(Particle.SOUL, mob.getLocation().add(0, 0.2, 0), 5, 0.3, 0.1, 0.3, 0.01);
        mob.getWorld().spawnParticle(Particle.SMOKE, mob.getLocation().add(0, 0.1, 0), 3, 0.25, 0.05, 0.25, 0.005);

        if (mob instanceof Mob livingMob) {
            LivingEntity target = livingMob.getTarget();
            if (target == null) {
                double distSq = mob.getLocation().distanceSquared(owner.getLocation());
                if (distSq > 40 * 40) {
                    // Too far even to path - teleport in close, like a dog snapping back to its owner.
                    org.bukkit.Location near = owner.getLocation().clone().add(
                            (Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3);
                    mob.teleport(near);
                } else if (distSq > 8 * 8) {
                    livingMob.getPathfinder().moveTo(owner.getLocation(), 1.2);
                }
            }
        }
    }
}
