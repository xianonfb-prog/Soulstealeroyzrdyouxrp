package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final ExoticPlugin plugin;
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("trial", "cancel", "complete", "give");

    public CommandHandler(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /exotic <trial|cancel|complete|give|list|progress|collection> ...", NamedTextColor.RED));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (ADMIN_SUBCOMMANDS.contains(sub) && !sender.hasPermission("exotic.admin")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "trial" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /exotic trial <itemId> <player>", NamedTextColor.RED));
                    return true;
                }
                ExoticItem item = ExoticItem.byId(args[1]);
                if (item == null) {
                    sender.sendMessage(Component.text("Unknown item id: " + args[1], NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found or offline: " + args[2], NamedTextColor.RED));
                    return true;
                }
                boolean started = plugin.trials().start(target, item);
                if (started) {
                    sender.sendMessage(Component.text("Started " + item.styledName() + " trial for " + target.getName(), NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text(target.getName() + " already has an active trial.", NamedTextColor.RED));
                }
                return true;
            }
            case "cancel" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /exotic cancel <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found or offline: " + args[1], NamedTextColor.RED));
                    return true;
                }
                plugin.trials().cancel(target);
                sender.sendMessage(Component.text("Cancelled active trial for " + target.getName(), NamedTextColor.YELLOW));
                return true;
            }
            case "complete" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /exotic complete <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found or offline: " + args[1], NamedTextColor.RED));
                    return true;
                }
                plugin.trials().forceComplete(target);
                sender.sendMessage(Component.text("Force-completed active trial for " + target.getName(), NamedTextColor.GREEN));
                return true;
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /exotic give <itemId> <player>", NamedTextColor.RED));
                    return true;
                }
                ExoticItem type = ExoticItem.byId(args[1]);
                if (type == null) {
                    sender.sendMessage(Component.text("Unknown item id: " + args[1], NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found or offline: " + args[2], NamedTextColor.RED));
                    return true;
                }
                ItemStack built = type.build();
                SwordUtil.bindToOwner(built, target.getUniqueId());
                target.getInventory().addItem(built);
                sender.sendMessage(Component.text("Gave " + type.styledName() + " to " + target.getName(), NamedTextColor.GREEN));
                return true;
            }
            case "list" -> {
                sender.sendMessage(Component.text("Exotic items:", NamedTextColor.GOLD));
                for (SwordType st : SwordType.values()) {
                    sender.sendMessage(Component.text(" - " + st.id() + ": " + st.displayName(), NamedTextColor.GRAY));
                }
                for (TomeType tt : TomeType.values()) {
                    sender.sendMessage(Component.text(" - " + tt.id() + ": " + tt.displayName(), NamedTextColor.GRAY));
                }
                for (TridentType td : TridentType.values()) {
                    sender.sendMessage(Component.text(" - " + td.id() + ": " + td.displayName(), NamedTextColor.GRAY));
                }
                return true;
            }
            case "progress" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can check their own progress.", NamedTextColor.RED));
                    return true;
                }
                TrialSystem.ActiveTrial trial = plugin.trials().get(player);
                if (trial == null) {
                    sender.sendMessage(Component.text("You have no active trial.", NamedTextColor.YELLOW));
                    return true;
                }
                ExoticItem item = ExoticItem.byId(trial.swordId);
                sender.sendMessage(Component.text("Trial: " + item.styledName(), NamedTextColor.GOLD));
                var required = TrialSystem.REQUIREMENTS.get(trial.swordId);
                for (var entry : trial.progress.entrySet()) {
                    int need = required.get(entry.getKey());
                    int have = entry.getValue();
                    NamedTextColor color = have >= need ? NamedTextColor.GREEN : NamedTextColor.WHITE;
                    sender.sendMessage(Component.text(" - " + entry.getKey().label + ": ", NamedTextColor.GRAY)
                            .append(Component.text(have + "/" + need, color)));
                }
                return true;
            }
            case "collection" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found or offline: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage(Component.text("Usage: /exotic collection <player>", NamedTextColor.RED));
                    return true;
                }

                sender.sendMessage(Component.text(target.getName() + "'s Exotic Collection:", NamedTextColor.GOLD));
                List<ExoticItem> all = new ArrayList<>();
                all.addAll(List.of(SwordType.values()));
                all.addAll(List.of(TomeType.values()));
                all.addAll(List.of(TridentType.values()));
                for (ExoticItem item : all) {
                    boolean owns = playerOwns(target, item.id());
                    NamedTextColor color = owns ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;
                    String mark = owns ? "\u2714 " : "\u2717 ";
                    sender.sendMessage(Component.text(" " + mark + item.styledName(), color));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Usage: /exotic <trial|cancel|complete|give|list|progress|collection>", NamedTextColor.RED));
                return true;
            }
        }
    }

    private boolean playerOwns(Player player, String itemId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && itemId.equals(SwordUtil.getSwordId(stack))) return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("trial", "cancel", "complete", "give", "list", "progress", "collection"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("trial") || args[0].equalsIgnoreCase("give"))) {
            for (SwordType type : SwordType.values()) options.add(type.id());
            for (TomeType type : TomeType.values()) options.add(type.id());
            for (TridentType type : TridentType.values()) options.add(type.id());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("cancel") || args[0].equalsIgnoreCase("complete") || args[0].equalsIgnoreCase("collection"))) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("trial") || args[0].equalsIgnoreCase("give"))) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        }
        String current = args[args.length - 1].toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(current)).collect(Collectors.toList());
    }
}
