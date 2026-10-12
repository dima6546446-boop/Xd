package ru.clindergrief.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Map;

/** /links, /top, /clindercore reload. */
public final class Misc implements CommandExecutor, TabCompleter {
    private final ClinderCore plugin;

    public Misc(ClinderCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "links" -> {
                sender.sendMessage(plugin.msg("links-header"));
                for (String line : plugin.getConfig().getStringList("links")) {
                    sender.sendMessage(Text.of(" " + line));
                }
            }
            case "top" -> {
                Leaderboards.Type type = Leaderboards.parse(args.length > 0 ? args[0] : "money");
                if (type == null) {
                    sender.sendMessage(plugin.msg("top-usage"));
                    return true;
                }
                sender.sendMessage(plugin.msg("top-header", Map.of("type", type.title)));
                int n = 1;
                for (Leaderboards.Entry e : plugin.leaderboards().get(type)) {
                    sender.sendMessage(Text.of(Text.fill(plugin.raw("top-line"),
                            Map.of("n", n++, "name", e.name(), "value", Leaderboards.format(type, e.value())))));
                }
            }
            case "clindercore" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("mine")) {
                    plugin.mine().reset();
                    sender.sendMessage(Text.of("&aШахта обновлена."));
                } else if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    plugin.reload();
                    sender.sendMessage(plugin.msg("reloaded"));
                } else {
                    sender.sendMessage(Text.of("&7ClinderCore " + plugin.getPluginMeta().getVersion() + " &8| &f/cg reload, /cg mine"));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("top")) {
            return List.of("money", "playtime", "kills");
        }
        if (args.length == 1 && command.getName().equalsIgnoreCase("clindercore")) {
            return List.of("reload", "mine");
        }
        return List.of();
    }
}
