package ru.clindergrief.core;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Замена нецензурных слов в чате (регулярные выражения из config.yml). */
public final class ChatFilter implements Listener {
    private final ClinderCore plugin;
    private List<Pattern> patterns = List.of();
    private String source;

    public ChatFilter(ClinderCore plugin) {
        this.plugin = plugin;
    }

    private List<Pattern> patterns() {
        List<String> raw = plugin.getConfig().getStringList("chat-filter.patterns");
        String key = String.join("\n", raw);
        if (!key.equals(source)) {
            List<Pattern> list = new ArrayList<>();
            for (String r : raw) {
                try {
                    list.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                } catch (PatternSyntaxException e) {
                    plugin.getLogger().warning("Неверный шаблон фильтра чата: " + r);
                }
            }
            patterns = list;
            source = key;
        }
        return patterns;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat-filter.enabled", true)
                || e.getPlayer().hasPermission("clindercore.chatfilter.bypass")) {
            return;
        }
        String replacement = plugin.getConfig().getString("chat-filter.replacement", "***");
        Component msg = e.message();
        for (Pattern p : patterns()) {
            msg = msg.replaceText(TextReplacementConfig.builder().match(p).replacement(replacement).build());
        }
        e.message(msg);
    }
}
