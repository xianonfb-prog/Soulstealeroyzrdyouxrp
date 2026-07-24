package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public enum TridentType implements ExoticItem {

    TRIDENT1("trident1", "Hand Of Zeus", NamedTextColor.YELLOW,
            List.of("Thunder Incarnate, Night"),
            "The Sky Itself Answers His Call.");

    private static final NamespacedKey MODEL_KEY = new NamespacedKey("exotic", "trident1");

    public static final double ATTACK_DAMAGE = 8.0;
    public static final double ATTACK_SPEED = 1.6;
    public static final int STORM_RADIUS = 25;

    private final String id;
    private final String displayName;
    private final NamedTextColor color;
    private final List<String> subtitleLore;
    private final String announcement;

    TridentType(String id, String displayName, NamedTextColor color, List<String> subtitleLore, String announcement) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.subtitleLore = subtitleLore;
        this.announcement = announcement;
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public String announcement() { return announcement; }
    @Override public NamedTextColor color() { return color; }

    public static TridentType byId(String id) {
        for (TridentType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    @Override
    public ItemStack build() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(TextStyle.toSmallCaps(displayName), color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : subtitleLore) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        }
        lore.add(Component.text(""));
        lore.add(Component.text("Attack Damage: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("8", NamedTextColor.WHITE)));
        lore.add(Component.text("Attack Speed: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("1.6", NamedTextColor.WHITE)));
        lore.add(Component.text("Passive: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Thunderstorm Aura (25 Block Radius)", NamedTextColor.WHITE)));
        lore.add(Component.text("Passive: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Storm-Touched (Thrown Hits Call Lightning)", NamedTextColor.WHITE)));
        lore.add(Component.text("Ability: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Zeus's Wrath (Shift+Right-Click)", NamedTextColor.WHITE)));
        meta.lore(lore);

        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        meta.setAttributeModifiers(null);
        meta.addAttributeModifier(AttributeKeys.ATTACK_DAMAGE,
                new AttributeModifier(UUID.nameUUIDFromBytes((id + "-dmg").getBytes()),
                        "exotic.attack_damage", ATTACK_DAMAGE - 1.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        meta.addAttributeModifier(AttributeKeys.ATTACK_SPEED,
                new AttributeModifier(UUID.nameUUIDFromBytes((id + "-spd").getBytes()),
                        "exotic.attack_speed", ATTACK_SPEED - 4.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

        meta.getPersistentDataContainer().set(SwordType.KEY_SWORD_ID, PersistentDataType.STRING, id);
        meta.setItemModel(MODEL_KEY);

        item.setItemMeta(meta);
        return item;
    }
}
