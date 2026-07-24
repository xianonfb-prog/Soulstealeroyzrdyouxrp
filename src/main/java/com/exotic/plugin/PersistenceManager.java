package com.exotic.plugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple YAML-backed persistence for trial progress and ability cooldowns.
 * Without this, a server restart wipes every active trial and every cooldown
 * back to zero - this saves on disable and loads on enable so that doesn't happen.
 */
public class PersistenceManager {

    private final ExoticPlugin plugin;
    private final File file;

    public PersistenceManager(ExoticPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        // Cooldowns
        for (var playerEntry : plugin.cooldowns().snapshot().entrySet()) {
            String base = "cooldowns." + playerEntry.getKey();
            for (var swordEntry : playerEntry.getValue().entrySet()) {
                yaml.set(base + "." + swordEntry.getKey(), swordEntry.getValue());
            }
        }

        // Trials
        for (var playerEntry : plugin.trials().snapshot().entrySet()) {
            String base = "trials." + playerEntry.getKey();
            TrialSystem.ActiveTrial trial = playerEntry.getValue();
            yaml.set(base + ".swordId", trial.swordId);
            for (var progressEntry : trial.progress.entrySet()) {
                yaml.set(base + ".progress." + progressEntry.getKey().name(), progressEntry.getValue());
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save Exotic state: " + e.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // Cooldowns
        Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
        if (yaml.isConfigurationSection("cooldowns")) {
            for (String uuidStr : yaml.getConfigurationSection("cooldowns").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Long> perSword = new HashMap<>();
                    for (String swordId : yaml.getConfigurationSection("cooldowns." + uuidStr).getKeys(false)) {
                        perSword.put(swordId, yaml.getLong("cooldowns." + uuidStr + "." + swordId));
                    }
                    cooldowns.put(uuid, perSword);
                } catch (IllegalArgumentException ignored) {
                    // malformed UUID key, skip
                }
            }
        }
        plugin.cooldowns().restore(cooldowns);

        // Trials
        Map<UUID, TrialSystem.ActiveTrial> trials = new HashMap<>();
        if (yaml.isConfigurationSection("trials")) {
            for (String uuidStr : yaml.getConfigurationSection("trials").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String swordId = yaml.getString("trials." + uuidStr + ".swordId");
                    if (swordId == null || !TrialSystem.REQUIREMENTS.containsKey(swordId)) continue;

                    TrialSystem.ActiveTrial trial = new TrialSystem.ActiveTrial(swordId);
                    String progressBase = "trials." + uuidStr + ".progress";
                    if (yaml.isConfigurationSection(progressBase)) {
                        for (String objName : yaml.getConfigurationSection(progressBase).getKeys(false)) {
                            try {
                                TrialSystem.ObjectiveType type = TrialSystem.ObjectiveType.valueOf(objName);
                                trial.progress.put(type, yaml.getInt(progressBase + "." + objName));
                            } catch (IllegalArgumentException ignored) {
                                // objective no longer exists, skip
                            }
                        }
                    }
                    trials.put(uuid, trial);
                } catch (IllegalArgumentException ignored) {
                    // malformed UUID key, skip
                }
            }
        }
        plugin.trials().restore(trials);

        plugin.getLogger().info("Exotic: restored " + cooldowns.size() + " players' cooldowns and "
                + trials.size() + " active trials from disk.");
    }
}
