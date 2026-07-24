package com.exotic.plugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class ThunderstormManager {

    private final ExoticPlugin plugin;
    private final CombatListener combat;
    private final Map<UUID, Long> nextStrikeAt = new HashMap<>();
    private final Set<UUID> activeStorms = new HashSet<>();
    private final Random random = new Random();

    public ThunderstormManager(ExoticPlugin plugin, CombatListener combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    /** Called once per second from PassiveTickTask. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean holding = "trident1".equals(SwordUtil.getSwordId(player.getInventory().getItemInMainHand()));

            if (holding) {
                if (activeStorms.add(player.getUniqueId())) {
                    onStormStart(player);
                }
                maybeStrike(player);
            } else if (activeStorms.remove(player.getUniqueId())) {
                // "Thunderstorm clears whenever they unequip the weapon" - immediate, no lingering.
                onStormEnd(player);
                nextStrikeAt.remove(player.getUniqueId());
            }
        }
    }

    private void onStormStart(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.WEATHER_RAIN, 0.6f, 0.8f);
        nextStrikeAt.put(player.getUniqueId(), System.currentTimeMillis() + randomInterval());
    }

    private void onStormEnd(Player player) {
        // No message - just silently stop the storm's random strikes.
    }

    private long randomInterval() {
        return 10_000L + random.nextInt(2001); // 10-12 seconds
    }

    private void maybeStrike(Player owner) {
        long due = nextStrikeAt.getOrDefault(owner.getUniqueId(), 0L);
        if (System.currentTimeMillis() < due) return;
        nextStrikeAt.put(owner.getUniqueId(), System.currentTimeMillis() + randomInterval());

        LivingEntity target = pickTarget(owner);
        if (target == null) return;

        double damage = 1.0 + random.nextDouble(); // 0.5-1 heart
        strike(target, damage, 30, true); // 1.5s stun
    }

    /** Priority to whoever hit the owner last; falls back to a random hostile mob/player in radius. */
    private LivingEntity pickTarget(Player owner) {
        UUID lastAttackerId = combat.lastAttackerOf.get(owner.getUniqueId());
        if (lastAttackerId != null) {
            Entity candidate = Bukkit.getEntity(lastAttackerId);
            if (candidate instanceof LivingEntity le && le.isValid() && !le.isDead()
                    && !le.equals(owner)
                    && le.getWorld().equals(owner.getWorld())
                    && le.getLocation().distanceSquared(owner.getLocation()) <= TridentType.STORM_RADIUS * (double) TridentType.STORM_RADIUS) {
                return le;
            }
        }

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity e : owner.getNearbyEntities(TridentType.STORM_RADIUS, TridentType.STORM_RADIUS, TridentType.STORM_RADIUS)) {
            if (e.equals(owner)) continue; // immune to its own lightning
            if (e instanceof Monster || (e instanceof Player p && !p.equals(owner))) {
                candidates.add((LivingEntity) e);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Shared lightning strike: visual-only bolt (no real fire/weather), true damage
     * ignoring armor, a stun, a screenshake-style Nausea pulse, and ground-to-magma
     * scarring instead of fire. Used by the passive, the AoE ability, the throw
     * proc, and the bolt-chain. Always skips the trident's own owner.
     */
    public void strike(LivingEntity target, double damage, int stunTicks, boolean placeMagma) {
        strike(target, damage, stunTicks, placeMagma, false);
    }

    /**
     * @param forceOverride true for ability-triggered strikes (the AoE cast, the bolt-chain) which are
     *                       allowed to set the stun to exactly this duration, including shortening it
     *                       intentionally (the retarget's 2s overriding the AoE's 6s). false for the
     *                       passive's random strikes, which must never clobber a longer stun already in
     *                       progress - only extend it if this duration is actually longer.
     */
    public void strike(LivingEntity target, double damage, int stunTicks, boolean placeMagma, boolean forceOverride) {
        World world = target.getWorld();
        world.strikeLightningEffect(target.getLocation()); // visual/sound only - no real damage or fire
        world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 100, 0.5, 1.0, 0.5, 0.25);
        world.spawnParticle(Particle.FLASH, target.getLocation(), 3, 0, 0, 0, 0);
        world.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 1f);
        world.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2f, 1f);

        combat.trueDamage(target, damage);
        stun(target, stunTicks, forceOverride);

        if (placeMagma) placeMagmaScar(target.getLocation());
    }

    /** Pure stun with no damage/visual - used by the retarget-chain's initial 2s override. Always forces. */
    public void forceStun(LivingEntity target, int ticks) {
        stun(target, ticks, true);
    }

    private void stun(LivingEntity target, int ticks, boolean forceOverride) {
        long newExpiry = System.currentTimeMillis() + (ticks * 50L);
        Long currentExpiry = combat.stunned.get(target.getUniqueId());

        if (!forceOverride && currentExpiry != null && currentExpiry > newExpiry) {
            // A longer ability-triggered stun is already in progress - never shorten it via a passive strike.
            return;
        }
        combat.stunned.put(target.getUniqueId(), newExpiry);
        // Screenshake proxy: a brief Nausea pulse layered on top of the real hurt-flash from trueDamage().
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, Math.min(ticks, 60), 1, false, false));
    }

    private void placeMagmaScar(Location center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = center.clone().add(dx, -1, dz).getBlock();
                if (block.getType().isSolid()) {
                    block.setType(Material.MAGMA_BLOCK);
                }
            }
        }
    }
}
