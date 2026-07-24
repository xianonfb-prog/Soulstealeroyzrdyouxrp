package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.Particle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class CombatListener implements Listener {

    private final ExoticPlugin plugin;

    // sword1 Karma: victim -> attacker -> consecutive-hits-not-returned
    private final Map<UUID, Map<UUID, Integer>> karmaCounters = new HashMap<>();
    // sword1 ability: player -> invincible-until epoch millis
    public final Map<UUID, Long> retributionActive = new HashMap<>();
    // guard against infinite reflect loops between two retribution-active players
    private final Set<UUID> reflecting = new HashSet<>();
    // sword3 ability: player -> auto-crit-until epoch millis
    public final Map<UUID, Long> hypersonicActive = new HashMap<>();
    // sword4 passive: wielder -> total landed hits with sword4 (resets each 7th)
    private final Map<UUID, Integer> shadowsHitCount = new HashMap<>();
    // tome1 ability: entities currently frozen by Ice Age - can't attack while frozen
    public final Set<UUID> frozen = new HashSet<>();

    // Entities awaiting a "true damage" hit - the very next EntityDamageEvent
    // against them has its armor reduction zeroed out. Lets true-damage abilities
    // (the Staff's beam, Hand of Zeus's lightning) go through the REAL damage event
    // instead of setHealth() directly, so the client still shows a normal gradual
    // hurt animation/health bar instead of snapping straight to zero.
    public final Set<UUID> pendingArmorIgnore = new HashSet<>();

    // Hand Of Zeus: entity UUID -> stun-expiry epoch millis. A single authoritative
    // map (rather than per-instance timers) so a stun can be safely overridden
    // mid-duration (the bolt-chain ability) without a race between two timers.
    public final Map<UUID, Long> stunned = new HashMap<>();
    // Hand Of Zeus: victim UUID -> the UUID of whoever last damaged them, used
    // for the storm's "priority to whoever hit the owner last" targeting rule.
    public final Map<UUID, UUID> lastAttackerOf = new HashMap<>();
    // players currently true-invisible (Lurker or Deception's death-save) - used to
    // re-hide them from anyone who joins mid-effect
    public final Set<UUID> trueInvisible = new HashSet<>();

    // Shared "ability currently active" windows, used only for the HUD (Active) indicator.
    public final Map<UUID, Long> swarmActive = new HashMap<>();  // sword2
    public final Map<UUID, Long> lurkerActive = new HashMap<>(); // sword4
    public final Map<UUID, Long> decreeActive = new HashMap<>(); // sword5
    public final Map<UUID, Long> bloodMoonActive = new HashMap<>(); // sword6

    public static final org.bukkit.NamespacedKey HADES_OWNER =
            new org.bukkit.NamespacedKey("exotic", "hades_owner");

    private static final List<PotionEffectType> POSITIVE_POOL = List.of(
            PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
            PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING, PotionEffectType.INVISIBILITY,
            PotionEffectType.NIGHT_VISION, PotionEffectType.ABSORPTION, PotionEffectType.LUCK,
            PotionEffectType.SLOW_FALLING
    );

    private static final List<PotionEffectType> NEGATIVE_POOL = List.of(
            PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
            PotionEffectType.POISON, PotionEffectType.GLOWING, PotionEffectType.LEVITATION,
            PotionEffectType.UNLUCK, PotionEffectType.BAD_OMEN, PotionEffectType.DARKNESS
    );

    private final Random random = new Random();

    public CombatListener(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Deals damage that ignores armor while still going through the real damage
     * event, so the client shows a normal gradual hurt animation/health bar
     * instead of the health snapping straight to a new value with no visual cue.
     */
    public void trueDamage(org.bukkit.entity.LivingEntity target, double amount) {
        pendingArmorIgnore.add(target.getUniqueId());
        target.damage(amount);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorIgnoreDamage(EntityDamageEvent event) {
        if (pendingArmorIgnore.remove(event.getEntity().getUniqueId())) {
            event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0.0);
            event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0.0);
        }
    }

    /**
     * Deception's Fatal Blow save - listens on the general EntityDamageEvent
     * (not just entity-vs-entity) since a fatal hit can come from anything:
     * fall damage, lava, mobs, players, etc. Runs at HIGH priority so it sees
     * the final computed damage before it would kill the player.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFatalBlow(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!SwordUtil.hasSwordInInventory(player, SwordType.SWORD4)) return;
        if (event.getFinalDamage() < player.getHealth()) return; // not fatal

        if (plugin.cooldowns().isOnCooldown(player.getUniqueId(), "sword4_totem")) return;
        plugin.cooldowns().trigger(player.getUniqueId(), "sword4_totem");

        event.setCancelled(true);
        player.setHealth(10.0); // 5 hearts
        player.setFireTicks(0);

        // Swap places with a random online player within 20 blocks, if any.
        List<Player> nearby = new ArrayList<>();
        for (Player other : player.getWorld().getPlayers()) {
            if (!other.equals(player) && other.getLocation().distanceSquared(player.getLocation()) <= 20 * 20) {
                nearby.add(other);
            }
        }
        if (!nearby.isEmpty()) {
            Player swapTarget = nearby.get(random.nextInt(nearby.size()));
            Location a = player.getLocation().clone();
            Location b = swapTarget.getLocation().clone();
            player.teleport(b);
            swapTarget.teleport(a);
        }

        player.sendMessage(Component.text("Deception spares you... for now.", NamedTextColor.DARK_GRAY));

        // Everyone, including the wielder, goes true-invisible for 15 seconds.
        for (Player p : Bukkit.getOnlinePlayers()) {
            trueInvisible.add(p.getUniqueId());
        }
        VisibilityManager.blackoutServer(plugin, 300L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                trueInvisible.remove(p.getUniqueId());
            }
        }, 300L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        // --- Tome of Subzero: frozen entities can't land attacks ---
        if (frozen.contains(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // --- Hand Of Zeus: stunned entities can't land attacks ---
        Long stunExpiry = stunned.get(event.getDamager().getUniqueId());
        if (stunExpiry != null && stunExpiry > System.currentTimeMillis()) {
            event.setCancelled(true);
            return;
        }

        // --- Hand Of Zeus: track who last hit whom, for storm targeting priority ---
        lastAttackerOf.put(event.getEntity().getUniqueId(), event.getDamager().getUniqueId());

        // --- Sword6 passive: Immune To Undead ---
        if (event.getEntity() instanceof Player potentialVictim
                && SwordUtil.hasSwordInInventory(potentialVictim, SwordType.SWORD6)
                && isUndead(event.getDamager())) {
            event.setCancelled(true);
            return;
        }

        // --- Sword6 Blood Moon: lifesteal for the wielder's own hits and their summons' hits ---
        UUID lifestealOwner = null;
        if (event.getDamager() instanceof Player dmgPlayer
                && SwordUtil.isSword(dmgPlayer.getInventory().getItemInMainHand(), SwordType.SWORD6)) {
            lifestealOwner = dmgPlayer.getUniqueId();
        } else if (event.getDamager() instanceof LivingEntity summon) {
            String ownerStr = summon.getPersistentDataContainer().get(HADES_OWNER, org.bukkit.persistence.PersistentDataType.STRING);
            if (ownerStr != null) lifestealOwner = UUID.fromString(ownerStr);
        }
        if (lifestealOwner != null) {
            Long until = bloodMoonActive.get(lifestealOwner);
            if (until != null && until > System.currentTimeMillis()) {
                Player owner = Bukkit.getPlayer(lifestealOwner);
                if (owner != null && owner.isOnline()) {
                    double newHealth = Math.min(owner.getAttribute(AttributeKeys.MAX_HEALTH).getValue(), owner.getHealth() + 1.0);
                    owner.setHealth(newHealth);
                }
            }
        }

        // --- Sword3 Hypersonic ability: auto-crit visuals/damage while active.
        // Runs against ANY LivingEntity target (mobs included), not just players.
        if (event.getDamager() instanceof Player attacker) {
            Long critUntil = hypersonicActive.get(attacker.getUniqueId());
            if (critUntil != null && critUntil > System.currentTimeMillis()
                    && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                event.setDamage(event.getDamage() * 1.5);
                if (event.getEntity() instanceof LivingEntity target) {
                    target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 60, 0.4, 0.6, 0.4, 0.15);
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                }
            }
        }

        // --- Sword6 - Hades' summons attack whatever their owner hits ---
        if (event.getDamager() instanceof Player redirectOwner && event.getEntity() instanceof LivingEntity redirectVictim
                && !redirectVictim.getScoreboardTags().contains("exotic_hades_summon")) {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity e : world.getEntitiesByClass(LivingEntity.class)) {
                    if (!e.getScoreboardTags().contains("exotic_hades_summon")) continue;
                    String ownerStr = e.getPersistentDataContainer().get(HADES_OWNER, org.bukkit.persistence.PersistentDataType.STRING);
                    if (ownerStr != null && ownerStr.equals(redirectOwner.getUniqueId().toString())
                            && e instanceof org.bukkit.entity.Mob summonMob) {
                        summonMob.setTarget(redirectVictim);
                    }
                }
            }
        }

        if (!(event.getEntity() instanceof Player victim)) return;

        // --- Karmic Retribution: invincibility + reflect ---
        Long until = retributionActive.get(victim.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            double dealt = event.getFinalDamage();
            event.setCancelled(true);

            if (event.getDamager() instanceof LivingEntity attackerEntity) {
                // Guard keyed by the attacker's own UUID - prevents two retribution-active
                // players from reflecting the same hit back and forth infinitely.
                if (!reflecting.contains(attackerEntity.getUniqueId())) {
                    reflecting.add(attackerEntity.getUniqueId());
                    attackerEntity.setNoDamageTicks(0); // bypass hurt-invulnerability so the reflect isn't swallowed
                    attackerEntity.damage(dealt, victim);
                    reflecting.remove(attackerEntity.getUniqueId());
                }
            }
            return;
        }

        if (!(event.getDamager() instanceof Player attacker2)) return;

        // --- Sword1 Karma passive: only applies if victim carries Judgement ---
        if (SwordUtil.hasSwordInInventory(victim, SwordType.SWORD1)) {
            Map<UUID, Integer> perAttacker = karmaCounters.computeIfAbsent(victim.getUniqueId(), k -> new HashMap<>());
            int count = perAttacker.getOrDefault(attacker2.getUniqueId(), 0) + 1;
            if (count > 5) {
                applyRandom(attacker2, NEGATIVE_POOL);
                applyRandom(victim, POSITIVE_POOL);
                count = 0; // reset after triggering
            }
            perAttacker.put(attacker2.getUniqueId(), count);
        }

        // --- Sword4 Shadows passive: every 7th hit landed WITH sword4 ---
        if (SwordUtil.isSword(attacker2.getInventory().getItemInMainHand(), SwordType.SWORD4)) {
            int hits = shadowsHitCount.getOrDefault(attacker2.getUniqueId(), 0) + 1;
            if (hits >= 7) {
                hits = 0;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 4, false, true));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 4, false, true));
            }
            shadowsHitCount.put(attacker2.getUniqueId(), hits);
        }
    }

    /** Reset the karma counter for victim->attacker when the VICTIM hits back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRetaliate(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player retaliator)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        Map<UUID, Integer> retaliatorCounters = karmaCounters.get(retaliator.getUniqueId());
        if (retaliatorCounters != null) retaliatorCounters.remove(target.getUniqueId());
    }

    private void applyRandom(Player target, List<PotionEffectType> pool) {
        PotionEffectType type = pool.get(random.nextInt(pool.size()));
        target.addPotionEffect(new PotionEffect(type, 600, 0, false, true, true));
    }

    /**
     * Light, per-weapon hit flair on every landed melee hit - just a small burst
     * of the weapon's characteristic color, nowhere near as heavy as the ability
     * particles. Runs at MONITOR so it only fires for hits that actually landed
     * (not cancelled by anything above, including our own retribution/frozen/stun checks).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitEffect(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        String itemId = SwordUtil.getSwordId(attacker.getInventory().getItemInMainHand());
        Particle.DustOptions dust = HIT_COLORS.get(itemId);
        if (dust == null) return;

        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 8, 0.25, 0.35, 0.25, 0, dust);

        HitSound sound = HIT_SOUNDS.get(itemId);
        if (sound != null) {
            target.getWorld().playSound(target.getLocation(), sound.sound(), sound.volume(), sound.pitch());
        }
    }

    private record HitSound(Sound sound, float volume, float pitch) {}

    private static final Map<String, HitSound> HIT_SOUNDS = Map.of(
            "sword1", new HitSound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.35f, 1.2f),   // Judgement - clean, balanced chime
            "sword2", new HitSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.25f, 1.8f),      // Kitty Blade - light, high chime
            "sword3", new HitSound(Sound.ENTITY_BAT_TAKEOFF, 0.3f, 1.5f),         // Hypersonic - quick airy whoosh
            "sword4", new HitSound(Sound.ENTITY_VEX_HURT, 0.2f, 0.7f),            // Deception - low whispery tick
            "sword5", new HitSound(Sound.BLOCK_BELL_USE, 0.2f, 1.5f),             // Emperor - short regal chime
            "sword6", new HitSound(Sound.PARTICLE_SOUL_ESCAPE, 0.3f, 0.6f),       // Hades' Lament - soft soul wisp
            "tome1", new HitSound(Sound.BLOCK_GLASS_HIT, 0.35f, 1.3f),            // Staff - small icy clink
            "trident1", new HitSound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.3f, 1.4f) // Hand Of Zeus - small electric crackle
    );

    private static final Map<String, Particle.DustOptions> HIT_COLORS = Map.of(
            "sword1", new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 255), 1.0f), // Judgement - white
            "sword2", new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 105, 180), 1.0f), // Kitty Blade - pink
            "sword3", new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 230, 255), 1.0f),   // Hypersonic - aqua
            "sword4", new Particle.DustOptions(org.bukkit.Color.fromRGB(20, 20, 20), 1.0f),      // Deception - black
            "sword5", new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 20, 20), 1.0f),   // Emperor - red
            "sword6", new Particle.DustOptions(org.bukkit.Color.fromRGB(60, 20, 80), 1.0f),    // Hades' Lament - underworld purple-gray
            "tome1", new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 200, 255), 1.0f),  // Staff - icy blue
            "trident1", new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 225, 60), 1.0f) // Hand Of Zeus - electric yellow
    );

    private static final Set<org.bukkit.entity.EntityType> UNDEAD_TYPES = Set.of(
            org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.HUSK,
            org.bukkit.entity.EntityType.DROWNED, org.bukkit.entity.EntityType.SKELETON,
            org.bukkit.entity.EntityType.STRAY, org.bukkit.entity.EntityType.WITHER_SKELETON,
            org.bukkit.entity.EntityType.ZOMBIE_VILLAGER, org.bukkit.entity.EntityType.PHANTOM,
            org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN, org.bukkit.entity.EntityType.WITHER,
            org.bukkit.entity.EntityType.ZOGLIN
    );

    private boolean isUndead(org.bukkit.entity.Entity entity) {
        return UNDEAD_TYPES.contains(entity.getType());
    }
}
