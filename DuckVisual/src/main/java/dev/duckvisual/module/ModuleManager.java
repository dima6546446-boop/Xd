package dev.duckvisual.module;

import dev.duckvisual.module.impl.hud.*;
import dev.duckvisual.module.impl.misc.*;
import dev.duckvisual.module.impl.visual.*;
import dev.duckvisual.module.impl.world.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // Visuals
        add(new ChinaHat());
        add(new JumpCircles());
        add(new Trails());
        add(new TargetESP());
        add(new HitParticles());
        add(new HitColor());
        add(new Arrows());
        add(new Crosshair());
        add(new ViewModel());
        add(new SwingAnimation());
        add(new AspectRatio());
        add(new NoRender());
        // Hud
        add(new Watermark());
        add(new ArrayListHud());
        add(new Keybinds());
        add(new Potions());
        add(new TargetHud());
        add(new Information());
        add(new Notifications());
        // World
        add(new Fullbright());
        add(new TimeChanger());
        add(new WorldParticles());
        // Misc
        add(new Theme());
        add(new ClickGui());
    }

    private void add(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModules(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) {
        for (Module m : modules) if (m.getClass() == clazz) return (T) m;
        throw new IllegalArgumentException(clazz.getName());
    }

    public Module get(String name) {
        for (Module m : modules) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }
}
