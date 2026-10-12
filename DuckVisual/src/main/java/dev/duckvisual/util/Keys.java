package dev.duckvisual.util;

import net.minecraft.client.util.InputUtil;

public final class Keys {
    private Keys() {}

    public static String name(int key) {
        if (key <= 0) return "None";
        String s = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
        return s.length() > 10 ? s.substring(0, 10) : s;
    }
}
