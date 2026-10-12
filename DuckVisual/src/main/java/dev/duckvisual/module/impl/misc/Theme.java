package dev.duckvisual.module.impl.misc;

import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.ColorSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;

/** Global client colors used by every visual. Always on. */
public class Theme extends Module {
    public final ModeSetting mode = add(new ModeSetting("Mode", "Gradient", "Gradient", "Static", "Rainbow"));
    public final ColorSetting first = add(new ColorSetting("First Color", 0xFFFFD43B));
    public final ColorSetting second = add(new ColorSetting("Second Color", 0xFFFF8A1F));
    public final NumberSetting speed = add(new NumberSetting("Speed", 1, 0.1, 5, 0.1));

    public Theme() {
        super("Theme", "Client color scheme", Category.MISC);
    }

    @Override
    public boolean isHidden() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // the theme can't be turned off
        super.setEnabled(true);
    }
}
