package ru.clindergrief.core;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * ClinderCore — собственные системы сервера ClinderGrief:
 * ежедневные награды, награды за активную игру, титулы, жалобы, награды за головы,
 * топы для голограмм и скорборда, автообъявления, фильтр чата, объявления о входе.
 */
public final class ClinderCore extends JavaPlugin {
    private Economy economy;
    private DataStore store;
    private Leaderboards leaderboards;
    private Activity activity;
    private Tags tags;
    private Bounties bounties;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Не найдена экономика Vault (нужен EssentialsX). Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();
        store = new DataStore(this);
        activity = new Activity(this);
        tags = new Tags(this);
        bounties = new Bounties(this);
        leaderboards = new Leaderboards(this);

        Daily daily = new Daily(this);
        Reports reports = new Reports(this);
        Misc misc = new Misc(this);
        bind("daily", daily);
        bind("tags", tags);
        bind("report", reports);
        bind("reports", reports);
        bind("bounty", bounties);
        bind("links", misc);
        bind("top", misc);
        bind("clindercore", misc);

        var pm = getServer().getPluginManager();
        pm.registerEvents(activity, this);
        pm.registerEvents(tags, this);
        pm.registerEvents(bounties, this);
        pm.registerEvents(new ChatFilter(this), this);
        pm.registerEvents(new JoinAnnouncer(this), this);

        activity.start();
        leaderboards.start();
        new Broadcaster(this).start();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ClinderExpansion(this).register();
            getLogger().info("Плейсхолдеры %clinder_*% зарегистрированы в PlaceholderAPI.");
        }
        getLogger().info("ClinderCore запущен.");
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.save();
        }
    }

    private void bind(String name, Object handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) handler);
        if (handler instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }

    public void reload() {
        reloadConfig();
        store.load();
        tags.reload();
        leaderboards.refresh();
    }

    // --- сообщения
    public String raw(String key) {
        return getConfig().getString("messages." + key, key);
    }

    public net.kyori.adventure.text.Component msg(String key, Map<String, ?> values) {
        return Text.of(getConfig().getString("prefix", "") + Text.fill(raw(key), values));
    }

    public net.kyori.adventure.text.Component msg(String key) {
        return msg(key, Map.of());
    }

    public Economy economy() {
        return economy;
    }

    public DataStore store() {
        return store;
    }

    public Leaderboards leaderboards() {
        return leaderboards;
    }

    public Bounties bounties() {
        return bounties;
    }

    public Activity activity() {
        return activity;
    }
}
