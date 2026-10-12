package ru.clindergrief.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Хранение данных игроков (data.yml), наград за головы и жалоб. */
public final class DataStore {
    private final ClinderCore plugin;
    private final File file;
    private YamlConfiguration data;

    public DataStore(ClinderCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    /** Прямой доступ к data.yml (ключи кейсов, прогресс заданий). */
    public YamlConfiguration raw() {
        return data;
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить data.yml", e);
        }
    }

    private String p(UUID id, String key) {
        return "players." + id + "." + key;
    }

    // --- ежедневная награда
    public long dailyLastDay(UUID id) {
        return data.getLong(p(id, "daily.last-day"), Long.MIN_VALUE);
    }

    public int dailyStreak(UUID id) {
        return data.getInt(p(id, "daily.streak"), 0);
    }

    public void setDaily(UUID id, long day, int streak) {
        data.set(p(id, "daily.last-day"), day);
        data.set(p(id, "daily.streak"), streak);
        save();
    }

    // --- титул
    public String tag(UUID id) {
        return data.getString(p(id, "tag"));
    }

    public void setTag(UUID id, String tag) {
        data.set(p(id, "tag"), tag);
        save();
    }

    // --- награды за головы
    public double bounty(UUID target) {
        return data.getDouble("bounties." + target, 0);
    }

    public void setBounty(UUID target, double amount) {
        data.set("bounties." + target, amount <= 0 ? null : amount);
        save();
    }

    public Map<UUID, Double> bounties() {
        Map<UUID, Double> out = new LinkedHashMap<>();
        ConfigurationSection s = data.getConfigurationSection("bounties");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    out.put(UUID.fromString(k), s.getDouble(k));
                } catch (IllegalArgumentException ignored) {
                    // повреждённая запись — пропускаем
                }
            }
        }
        return out;
    }

    // --- жалобы
    public List<Map<?, ?>> reports() {
        return new ArrayList<>(data.getMapList("reports"));
    }

    public void addReport(Map<String, Object> report, int keepLast) {
        List<Map<?, ?>> list = reports();
        list.add(report);
        while (list.size() > keepLast) {
            list.remove(0);
        }
        data.set("reports", list);
        save();
    }

    public void clearReports() {
        data.set("reports", null);
        save();
    }
}
