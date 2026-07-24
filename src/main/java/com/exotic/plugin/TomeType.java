package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Staff Of Absolute Zero (formerly Tome Of Subzero) - unlike the swords this
 * has no enchants, isn't enchantable at all, and its base ability fires on a
 * plain left-click instead of shift+right-click.
 */
public enum TomeType implements ExoticItem {

    TOME1("tome1", "Staff Of Absolute Zero", NamedTextColor.DARK_BLUE,
            List.of("The Frozen Warlock, Ico"),
            "Winter Has Found Its Warlock.");

    private static final org.bukkit.NamespacedKey TOME1_MODEL = new org.bukkit.NamespacedKey("exotic", "tome1");

    public static final double ATTACK_DAMAGE = 5.0;
    public static final double ATTACK_SPEED = 1.9;

    private final String id;
    private final String displayName;
    private final NamedTextColor color;
    private final List<String> subtitleLore;
    private final String announcement;

    TomeType(String id, String displayName, NamedTextColor color, List<String> subtitleLore, String announcement) {
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

    public static TomeType byId(String id) {
        for (TomeType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    @Override
    public ItemStack build() {
        ItemStack item = new ItemStack(Material.STICK);
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
                .append(Component.text("5", NamedTextColor.WHITE)));
        lore.add(Component.text("Attack Speed: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("1.9", NamedTextColor.WHITE)));
        lore.add(Component.text("Ability: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Absolute Zero Beam (Left-Click)", NamedTextColor.WHITE)));
        lore.add(Component.text("Ability: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("Ice Age (Shift+Right-Click)", NamedTextColor.WHITE)));
        meta.lore(lore);

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

        // Not enchantable - blocked separately in PassiveListener's enchant-prevention hook
        meta.getPersistentDataContainer().set(SwordType.KEY_SWORD_ID, PersistentDataType.STRING, id);
        meta.setItemModel(TOME1_MODEL);

        item.setItemMeta(meta);
        return item;
    }
}
