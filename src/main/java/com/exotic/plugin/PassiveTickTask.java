package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PassiveTickTask extends BukkitRunnable {

    private final ExoticPlugin plugin;
    private final CombatListener combat;
    private final ThunderstormManager storm;
    private static final UUID KITTY_HEALTH_MOD = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001");
    private static final UUID HADES_HEALTH_MOD = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000002");

    public PassiveTickTask(ExoticPlugin plugin, CombatListener combat, ThunderstormManager storm) {
        this.plugin = plugin;
        this.combat = combat;
        this.storm = storm;
    }

    @Override
    public void run() {
        storm.tick();

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hasSword3 = SwordUtil.hasSwordInInventory(player, SwordType.SWORD3);
            boolean hasSword2 = SwordUtil.hasSwordInInventory(player, SwordType.SWORD2);
            boolean hasSword5 = SwordUtil.hasSwordInInventory(player, SwordType.SWORD5);
            boolean hasSword6 = SwordUtil.hasSwordInInventory(player, SwordType.SWORD6);

            // Sword3 - Swift: infinite Speed 2 while carried
            if (hasSword3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, false));
            }

            // Sword5 - Emperor's Charisma: permanent Hero of the Village 255
            if (hasSword5) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, 254, true, false, false));
            }

            // Sword2 - Kitty Love: permanent +2 hearts while carried
            AttributeInstance maxHealth = player.getAttribute(AttributeKeys.MAX_HEALTH);
            if (maxHealth != null) {
                boolean hasModifier = maxHealth.getModifiers().stream().anyMatch(m -> m.getUniqueId().equals(KITTY_HEALTH_MOD));
                if (hasSword2 && !hasModifier) {
                    maxHealth.addModifier(new AttributeModifier(KITTY_HEALTH_MOD, "exotic.kitty_love", 4.0,
                            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
                } else if (!hasSword2 && hasModifier) {
                    maxHealth.getModifiers().stream()
                            .filter(m -> m.getUniqueId().equals(KITTY_HEALTH_MOD))
                            .findFirst().ifPresent(maxHealth::removeModifier);
                }
            }

            // Sword2 - Kitty Love: cobweb slowdown negation (approximation: boost velocity out of the web)
            if (hasSword2 && player.getLocation().getBlock().getType() == Material.COBWEB) {
                player.setVelocity(player.getVelocity().multiply(1.0).setY(Math.max(player.getVelocity().getY(), 0.1)));
                player.setVelocity(player.getVelocity().multiply(2.2));
            }

            // Sword6 - Reaper's Toll: +1 heart per alive summoned undead, recalculated live
            if (hasSword6 || countAliveSummons(player) > 0) {
                AttributeInstance hadesHealth = player.getAttribute(AttributeKeys.MAX_HEALTH);
                if (hadesHealth != null) {
                    hadesHealth.getModifiers().stream()
                            .filter(m -> m.getUniqueId().equals(HADES_HEALTH_MOD))
                            .findFirst().ifPresent(hadesHealth::removeModifier);
                    long alive = countAliveSummons(player);
                    if (alive > 0) {
                        hadesHealth.addModifier(new AttributeModifier(HADES_HEALTH_MOD, "exotic.reapers_toll", alive * 2.0,
                                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
                    }
                }
            }

            updateActionBar(player);
            plugin.scoreboards().update(player);
        }
    }

    private long countAliveSummons(Player owner) {
        long count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : world.getEntitiesByClass(org.bukkit.entity.LivingEntity.class)) {
                if (!e.getScoreboardTags().contains("exotic_hades_summon")) continue;
                String ownerStr = e.getPersistentDataContainer().get(CombatListener.HADES_OWNER, org.bukkit.persistence.PersistentDataType.STRING);
                if (ownerStr != null && ownerStr.equals(owner.getUniqueId().toString()) && !e.isDead()) count++;
            }
        }
        return count;
    }

    private void updateActionBar(Player player) {
        String itemId = SwordUtil.getSwordId(player.getInventory().getItemInMainHand());
        if (itemId == null) return;

        ExoticItem item = ExoticItem.byId(itemId);
        if (item == null) return;

        String name = item.styledName();
        long remaining = plugin.cooldowns().remainingMs(player.getUniqueId(), itemId);
        boolean active = isCurrentlyActive(player.getUniqueId(), itemId);

        Component status;
        if (active) {
            status = Component.text(name + " - (Active)", NamedTextColor.LIGHT_PURPLE);
        } else if (remaining > 0) {
            status = Component.text(name + " - Cooldown: " + (remaining / 1000 + 1) + "s", NamedTextColor.RED);
        } else {
            status = Component.text(name + " - Ready (Shift + Right-Click)", NamedTextColor.GREEN);
        }

        if (itemId.equals("sword6")) {
            long alive = countAliveSummons(player);
            boolean bloodMoon = combat.bloodMoonActive.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
            int cap = bloodMoon ? 9 : 5;
            long summonCd = plugin.cooldowns().remainingMs(player.getUniqueId(), "sword6_summon") / 1000;
            String summonStatus = summonCd > 0 ? (summonCd + 1) + "s" : "Ready";
            status = status.append(Component.text("  |  Undead: " + alive + "/" + cap
                    + "  Summon: " + summonStatus, NamedTextColor.DARK_GRAY));
        }

        player.sendActionBar(status);
    }

    private boolean isCurrentlyActive(UUID uuid, String itemId) {
        long now = System.currentTimeMillis();
        return switch (itemId) {
            case "sword1" -> combat.retributionActive.getOrDefault(uuid, 0L) > now;
            case "sword2" -> combat.swarmActive.getOrDefault(uuid, 0L) > now;
            case "sword3" -> combat.hypersonicActive.getOrDefault(uuid, 0L) > now;
            case "sword4" -> combat.lurkerActive.getOrDefault(uuid, 0L) > now;
            case "sword5" -> combat.decreeActive.getOrDefault(uuid, 0L) > now;
            case "sword6" -> combat.bloodMoonActive.getOrDefault(uuid, 0L) > now;
            default -> false;
        };
    }
}
