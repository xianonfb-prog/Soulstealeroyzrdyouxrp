package com.exotic.plugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The 6 Exotic swords: identity, per-item stats, and lore.
 * Most swords share 8 Attack Damage / 1.6 Attack Speed / Unbreakable / Sharpness V
 * + Fire Aspect II, but Hades' Lament breaks that mold (5/3, unenchantable) so
 * these are now per-item fields instead of hardcoded constants.
 */
public enum SwordType implements ExoticItem {

    SWORD1("sword1", "Judgement", Material.NETHERITE_SWORD, NamedTextColor.WHITE,
            List.of("The Equalization, Karma."),
            "The Scales Have Been Balanced.",
            8.0, 1.6, true, true,
            List.of("Karma"),
            List.of("Karmic Retribution (Shift+Right-Click)")),

    SWORD2("sword2", "Pretty Kitty Princess Blade", Material.NETHERITE_SWORD, NamedTextColor.LIGHT_PURPLE,
            List.of("The First Princess, Prince."),
            "A New Princess Has Claimed Her Throne.",
            8.0, 1.6, true, true,
            List.of("Kitty Love"),
            List.of("Kitty Swarm (Shift+Right-Click)")),

    SWORD3("sword3", "Hypersonic Devastator", Material.NETHERITE_SWORD, NamedTextColor.AQUA,
            List.of("The Fastest, Exo"),
            "Sound Itself Could Not Keep Pace.",
            8.0, 1.6, true, true,
            List.of("Swift"),
            List.of("Speed Is My Specialty (Shift+Right-Click)")),

    SWORD4("sword4", "Deception", Material.IRON_SWORD, NamedTextColor.BLACK,
            List.of("The Great Deceiver, Magma."),
            "The Shadows Have Chosen Their Vessel.",
            8.0, 1.6, true, true,
            List.of("Shadows", "Fatal Blow"),
            List.of("Lurker (Shift+Right-Click)")),

    SWORD5("sword5", "Bane Of The Emperor", Material.NETHERITE_SWORD, NamedTextColor.RED,
            List.of("The True Emperor, Kaizer"),
            "Countless Empires Rise And Fall.",
            8.0, 1.6, true, true,
            List.of("Emperor's Charisma"),
            List.of("Imperial Decree (Shift+Right-Click)")),

    SWORD6("sword6", "Hades' Lament", Material.NETHERITE_SWORD, NamedTextColor.DARK_GRAY,
            List.of("The Ungodly, Azazel"),
            "The Underworld Answers No Prayer.",
            5.0, 3.0, true, false,
            List.of("Immune To Undead", "Reaper's Toll (1 Heart / Summon Alive)"),
            List.of("Summon Undead (Left-Click)", "Blood Moon (Shift+Right-Click)"));

    private static final NamespacedKey SWORD1_MODEL = new NamespacedKey("exotic", "sword1");
    private static final NamespacedKey SWORD2_MODEL = new NamespacedKey("exotic", "sword2");
    private static final NamespacedKey SWORD3_MODEL = new NamespacedKey("exotic", "sword3");
    private static final NamespacedKey SWORD4_MODEL = new NamespacedKey("exotic", "sword4");
    private static final NamespacedKey SWORD5_MODEL = new NamespacedKey("exotic", "sword5");
    private static final NamespacedKey SWORD6_MODEL = new NamespacedKey("exotic", "sword6");

    public static final NamespacedKey KEY_SWORD_ID = new NamespacedKey("exotic", "sword_id");
    public static final NamespacedKey KEY_OWNER = new NamespacedKey("exotic", "owner");

    private final String id;
    private final String displayName;
    private final Material material;
    private final NamedTextColor color;
    private final List<String> subtitleLore;
    private final String announcement;
    private final double attackDamage;
    private final double attackSpeed;
    private final boolean unbreakable;
    private final boolean enchantable; // Sharpness V + Fire Aspect II if true
    private final List<String> passiveLines;
    private final List<String> abilityLines;

    SwordType(String id, String displayName, Material material, NamedTextColor color,
              List<String> subtitleLore, String announcement,
              double attackDamage, double attackSpeed, boolean unbreakable, boolean enchantable,
              List<String> passiveLines, List<String> abilityLines) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.color = color;
        this.subtitleLore = subtitleLore;
        this.announcement = announcement;
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
        this.unbreakable = unbreakable;
        this.enchantable = enchantable;
        this.passiveLines = passiveLines;
        this.abilityLines = abilityLines;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String announcement() { return announcement; }
    public NamedTextColor color() { return color; }

    public static SwordType byId(String id) {
        for (SwordType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public ItemStack build() {
        ItemStack item = new ItemStack(material);
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
                .append(Component.text(fmt(attackDamage), NamedTextColor.WHITE)));
        lore.add(Component.text("Attack Speed: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(fmt(attackSpeed), NamedTextColor.WHITE)));
        for (String p : passiveLines) {
            lore.add(Component.text("Passive: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(p, NamedTextColor.WHITE)));
        }
        for (String a : abilityLines) {
            lore.add(Component.text("Ability: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(a, NamedTextColor.WHITE)));
        }
        meta.lore(lore);

        if (enchantable) {
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        }

        if (unbreakable) meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        meta.setAttributeModifiers(null);
        meta.addAttributeModifier(AttributeKeys.ATTACK_DAMAGE,
                new AttributeModifier(UUID.nameUUIDFromBytes((id + "-dmg").getBytes()),
                        "exotic.attack_damage", attackDamage - 1.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
        meta.addAttributeModifier(AttributeKeys.ATTACK_SPEED,
                new AttributeModifier(UUID.nameUUIDFromBytes((id + "-spd").getBytes()),
                        "exotic.attack_speed", attackSpeed - 4.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));

        meta.getPersistentDataContainer().set(KEY_SWORD_ID, PersistentDataType.STRING, id);

        meta.setItemModel(switch (id) {
            case "sword1" -> SWORD1_MODEL;
            case "sword2" -> SWORD2_MODEL;
            case "sword3" -> SWORD3_MODEL;
            case "sword4" -> SWORD4_MODEL;
            case "sword5" -> SWORD5_MODEL;
            case "sword6" -> SWORD6_MODEL;
            default -> null;
        });

        item.setItemMeta(meta);
        return item;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
