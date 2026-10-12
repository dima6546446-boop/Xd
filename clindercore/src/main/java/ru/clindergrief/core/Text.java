package ru.clindergrief.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/** Форматирование сообщений: коды &a/&l и HEX &#rrggbb, подстановка {ключей}. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();

    private Text() {
    }

    public static Component of(String legacy) {
        return LEGACY.deserialize(legacy == null ? "" : legacy);
    }

    /** Для названий и описаний предметов: без курсива, который Minecraft добавляет по умолчанию. */
    public static Component item(String legacy) {
        return of(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String fill(String template, Map<String, ?> values) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, ?> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }

    public static String money(double amount) {
        if (amount == Math.rint(amount)) {
            return String.format("%,d", (long) amount).replace(',', ' ');
        }
        return String.format("%,.2f", amount).replace(',', ' ');
    }

    /** Время в формате «12 ч 05 мин». */
    public static String duration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h > 0) {
            return h + " ч " + String.format("%02d", m) + " мин";
        }
        return m + " мин";
    }
}
