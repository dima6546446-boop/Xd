package dev.duckvisual.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("duckvisual.json");

    public void save() {
        JsonObject root = new JsonObject();
        for (Module m : DuckVisual.modules().getModules()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("key", m.getKey());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.getSettings()) settings.add(s.getName(), s.save());
            o.add("settings", settings);
            root.add(m.getName(), o);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (Exception e) {
            DuckVisual.LOGGER.error("Failed to save config", e);
        }
    }

    public boolean load() {
        if (!Files.exists(file)) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (Module m : DuckVisual.modules().getModules()) {
                if (!root.has(m.getName())) continue;
                JsonObject o = root.getAsJsonObject(m.getName());
                if (o.has("key")) m.setKey(o.get("key").getAsInt());
                if (o.has("settings")) {
                    JsonObject settings = o.getAsJsonObject("settings");
                    for (Setting<?> s : m.getSettings()) {
                        JsonElement e = settings.get(s.getName());
                        if (e != null) {
                            try {
                                s.load(e);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                if (o.has("enabled")) m.setEnabledSilent(o.get("enabled").getAsBoolean());
            }
            return true;
        } catch (Exception e) {
            DuckVisual.LOGGER.error("Failed to load config", e);
            return false;
        }
    }
}
