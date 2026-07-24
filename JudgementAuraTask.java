package com.exotic.plugin;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Runs for the duration of Karmic Retribution: every 2 seconds, deals 1.5
 * hearts (ignoring armor) to everything within 5 blocks, and continuously
 * renders a light-yellow radius ring + shield dome around the wielder.
 */
public class JudgementAuraTask extends BukkitRunnable {

    private static final double RADIUS = 5.0;
    private static final Particle.DustOptions LIGHT_YELLOW = new Particle.DustOptions(Color.fromRGB(255, 250, 160), 1.2f);

    private final ExoticPlugin plugin;
    private final CombatListener combat;
    private final Player wielder;
    private long elapsed = 0;

    public JudgementAuraTask(ExoticPlugin plugin, CombatListener combat, Player wielder) {
        this.plugin = plugin;
        this.combat = combat;
        this.wielder = wielder;
    }

    public void start() {
        runTaskTimer(plugin, 0L, 4L); // every 4 ticks for the visuals
    }

    @Override
    public void run() {
        elapsed += 4;

        if (!wielder.isOnline() || combat.retributionActive.getOrDefault(wielder.getUniqueId(), 0L) <= System.currentTimeMillis()) {
            cancel();
            return;
        }

        renderShieldAndRing();

        // Damage tick every 2 seconds (every 5th run at a 4-tick interval = 40 ticks)
        if (elapsed % 40 == 0) {
            for (Entity e : wielder.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
                if (e.equals(wielder)) continue;
                boolean valid = e instanceof Monster || (e instanceof Player p && !p.equals(wielder));
                if (!valid || !(e instanceof LivingEntity target)) continue;
                combat.trueDamage(target, 3.0); // 1.5 hearts, ignores armor
            }
        }
    }

    private void renderShieldAndRing() {
        var loc = wielder.getLocation();

        // Ground ring marking the 5-block radius
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            double x = Math.cos(angle) * RADIUS;
            double z = Math.sin(angle) * RADIUS;
            wielder.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, 0.1, z), 1, 0, 0, 0, 0, LIGHT_YELLOW);
        }

        // Shield dome around the wielder
        for (int i = 0; i < 10; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double height = Math.random() * 2.2;
            double r = 1.1;
            double x = Math.cos(angle) * r;
            double z = Math.sin(angle) * r;
            wielder.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, height, z), 1, 0, 0, 0, 0, LIGHT_YELLOW);
        }
    }
}
