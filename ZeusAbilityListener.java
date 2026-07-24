package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class ZeusAbilityListener implements Listener {

    private final ExoticPlugin plugin;
    private final CombatListener combat;
    private final ThunderstormManager storm;
    private final Random random = new Random();

    // casterId -> the set of targets AoE-stunned by their last Zeus's Wrath cast, eligible for a retarget throw
    private final Map<UUID, Set<UUID>> eligibleRetargets = new HashMap<>();
    private final Map<UUID, Long> abilityWindowExpiry = new HashMap<>();

    public ZeusAbilityListener(ExoticPlugin plugin, CombatListener combat, ThunderstormManager storm) {
        this.plugin = plugin;
        this.combat = combat;
        this.storm = storm;
    }

    /** Called from PassiveListener's shift+right-click dispatcher. */
    public void activateZeusWrath(Player caster) {
        plugin.cooldowns().trigger(caster.getUniqueId(), "trident1");
        caster.sendMessage(Component.text("Zeus's Wrath descends.", NamedTextColor.YELLOW));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.6f);
        caster.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, caster.getLocation().add(0, 1, 0), 200, 1.5, 2.0, 1.5, 0.3);

        Set<UUID> targets = new HashSet<>();
        for (Entity e : caster.getNearbyEntities(TridentType.STORM_RADIUS, TridentType.STORM_RADIUS, TridentType.STORM_RADIUS)) {
            if (e.equals(caster)) continue; // immune to its own lightning
            boolean valid = e instanceof Monster || (e instanceof Player p && !p.equals(caster));
            if (!valid || !(e instanceof LivingEntity le)) continue;

            storm.strike(le, 1.0, 120, true, true); // 0.5 dmg (1.0 raw), 6s stun, force-applied
            targets.add(le.getUniqueId());
        }

        eligibleRetargets.put(caster.getUniqueId(), targets);
        abilityWindowExpiry.put(caster.getUniqueId(), System.currentTimeMillis() + 6000L);
    }

    /** Handles both the passive Storm-Touched throw proc and the ability's retarget bolt-chain. */
    @EventHandler
    public void onTridentLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player caster)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (target.equals(caster)) return; // immune to its own lightning
        if (!"trident1".equals(SwordUtil.getSwordId(trident.getItem()))) return;

        Long windowExpiry = abilityWindowExpiry.get(caster.getUniqueId());
        Set<UUID> eligible = eligibleRetargets.get(caster.getUniqueId());
        boolean isRetarget = windowExpiry != null && windowExpiry > System.currentTimeMillis()
                && eligible != null && eligible.contains(target.getUniqueId());

        if (isRetarget) {
            eligible.remove(target.getUniqueId()); // only once per cast per target
            startBoltChain(target);
        } else {
            // Storm-Touched: any successful throw hit calls a small lightning strike
            storm.strike(target, 2.0, 20, true, false); // 1 heart, 1s stun - won't shorten a longer stun already active
        }
    }

    /** 2s stun (overrides the AoE's 6s), then 3 bolts at 0.5s intervals; the last bolt applies a fresh 3.5s stun. */
    private void startBoltChain(LivingEntity target) {
        storm.forceStun(target, 40); // 2s, intentionally overrides the AoE's remaining 6s stun

        new BukkitRunnable() {
            int bolt = 0;

            @Override
            public void run() {
                if (bolt >= 3 || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                double dmg = 2.0 + random.nextDouble() * 2.0; // 1-2 hearts
                boolean isLastBolt = bolt == 2;
                storm.strike(target, dmg, isLastBolt ? 70 : 40, true, true); // last bolt: fresh, forced 3.5s stun
                bolt++;
            }
        }.runTaskTimer(plugin, 0L, 10L); // 0.5s interval
    }
}
