package ru.clindergrief.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** /bounty — награда за голову: деньги списываются сразу, получает тот, кто убьёт цель. */
public final class Bounties implements CommandExecutor, TabCompleter, Listener {
    private final ClinderCore plugin;

    public Bounties(ClinderCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!plugin.getConfig().getBoolean("bounty.enabled", true)) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("bounty-usage"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.msg("bounty-self"));
            return true;
        }
        double amount;
        try {
            amount = Math.floor(Double.parseDouble(args[1].replace(',', '.')));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.msg("bounty-usage"));
            return true;
        }
        double min = plugin.getConfig().getDouble("bounty.min-amount", 100);
        double max = plugin.getConfig().getDouble("bounty.max-amount", 1_000_000);
        if (amount < min || amount > max || Double.isNaN(amount)) {
            player.sendMessage(plugin.msg("bounty-range", Map.of("min", Text.money(min), "max", Text.money(max))));
            return true;
        }
        if (!plugin.economy().has(player, amount) || !plugin.economy().withdrawPlayer(player, amount).transactionSuccess()) {
            player.sendMessage(plugin.msg("bounty-no-money"));
            return true;
        }
        double total = plugin.store().bounty(target.getUniqueId()) + amount;
        plugin.store().setBounty(target.getUniqueId(), total);
        Bukkit.broadcast(plugin.msg("bounty-placed", Map.of("player", player.getName(), "amount", Text.money(amount),
                "target", target.getName(), "total", Text.money(total))));
        return true;
    }

    private void list(CommandSender sender) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(plugin.store().bounties().entrySet());
        if (list.isEmpty()) {
            sender.sendMessage(plugin.msg("bounty-list-empty"));
            return;
        }
        list.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        sender.sendMessage(plugin.msg("bounty-list-header"));
        int n = 1;
        for (Map.Entry<UUID, Double> e : list.subList(0, Math.min(10, list.size()))) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            sender.sendMessage(Text.of(Text.fill(plugin.raw("bounty-list-line"),
                    Map.of("n", n++, "target", Objects.requireNonNullElse(op.getName(), "?"), "amount", Text.money(e.getValue())))));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        double amount = plugin.store().bounty(victim.getUniqueId());
        if (killer == null || killer.equals(victim) || amount <= 0) {
            return;
        }
        if (plugin.getConfig().getBoolean("bounty.block-same-ip", true) && sameIp(killer, victim)) {
            killer.sendMessage(plugin.msg("bounty-same-ip"));
            return;
        }
        plugin.store().setBounty(victim.getUniqueId(), 0);
        plugin.economy().depositPlayer(killer, amount);
        Bukkit.broadcast(plugin.msg("bounty-claimed", Map.of("killer", killer.getName(),
                "amount", Text.money(amount), "target", victim.getName())));
    }

    private static boolean sameIp(Player a, Player b) {
        InetSocketAddress x = a.getAddress();
        InetSocketAddress y = b.getAddress();
        return x != null && y != null && x.getAddress() != null && x.getAddress().equals(y.getAddress());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            out.add("list");
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
            return out.stream().filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
