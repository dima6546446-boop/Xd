package dev.duckvisual.gui;

import dev.duckvisual.DuckVisual;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.HudModule;
import dev.duckvisual.module.Module;
import dev.duckvisual.module.impl.hud.Watermark;
import dev.duckvisual.module.impl.misc.ClickGui;
import dev.duckvisual.setting.BindSetting;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.module.impl.misc.Theme;
import dev.duckvisual.setting.ColorSetting;
import dev.duckvisual.setting.ModeSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.setting.Setting;
import dev.duckvisual.util.Animation;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Keys;
import dev.duckvisual.util.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Duck Visual menu: a single window with a category sidebar and a two-column grid of
 * module cards. Right-click a card to expand its settings, middle-click to bind a key.
 * HUD elements can be dragged around the screen while the menu is open.
 */
public class ClickGuiScreen extends Screen {
    private static final int W = 480, H = 310, SIDEBAR = 112;
    private static final int BG = 0xF00E0E12, SIDE_BG = 0xF0131318, CARD = 0xFF18181F, CARD_HOVER = 0xFF1D1D25;
    private static final int TEXT = 0xFFFFFFFF, SUBTEXT = 0xFF8C8C99;

    private static float windowX = -1, windowY = -1;
    private static Category current = Category.VISUALS;
    private static final Map<Category, Float> scroll = new HashMap<>();
    private static final Map<Module, Float> expand = new HashMap<>();
    private static final Map<Module, Boolean> expanded = new HashMap<>();
    private static final Map<ColorSetting, Boolean> pickerOpen = new HashMap<>();
    private static final Map<Category, Float> tabAnim = new HashMap<>();

    private float scrollAnim;
    private static final float OPEN_TIME = 0.18f, CLOSE_TIME = 0.11f;

    /** 0..1, driven by wall-clock time so open/close speed does not depend on FPS. */
    private float openAnim;
    private boolean closing;
    private long animStart = System.nanoTime();
    private float closeFrom = 1;
    private String search = "";
    private boolean searchFocused;

    private Module binding;
    private BindSetting bindingSetting;
    private static boolean paletteTab;
    private float paletteAnim;
    private boolean draggingWindow;
    private float dragX, dragY;
    private HudModule draggingHud;
    private Slider activeSlider;
    private float contentHeight;

    /** Clickable regions recorded during render and consumed by mouse handlers. */
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(float x, float y, float w, float h, Consumer<Integer> action) {
        boolean inside(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    /** A slider currently being dragged; {@code setter} receives 0..1. */
    private record Slider(float x, float w, Consumer<Float> setter) {}

    public ClickGuiScreen() {
        super(Text.literal(DuckVisual.NAME));
    }

    @Override
    protected void init() {
        if (windowX < 0 || windowX > width - 40 || windowY > height - 40) {
            windowX = (width - W) / 2f;
            windowY = (height - H) / 2f;
        }
        scrollAnim = scroll.getOrDefault(current, 0f);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (DuckVisual.modules().get(ClickGui.class).realBlur.get() && openAnim > 0.3f && client != null && client.world != null) {
            applyBlur();
        }
        if (DuckVisual.modules().get(ClickGui.class).blur.get()) {
            int a = (int) (140 * openAnim);
            ctx.fillGradient(0, 0, width, height, ColorUtil.withAlpha(0xFF050508, a), ColorUtil.withAlpha(0xFF0A0A10, a));
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float elapsed = (System.nanoTime() - animStart) / 1_000_000_000f;
        if (closing) {
            float p = Math.min(1, elapsed / CLOSE_TIME);
            openAnim = closeFrom * (1 - p * p);
            if (p >= 1) {
                super.close();
                return;
            }
        } else {
            openAnim = Math.min(1, elapsed / OPEN_TIME);
        }
        renderBackground(ctx, mouseX, mouseY, delta);
        hits.clear();

        // HUD drag outlines
        for (Module m : DuckVisual.modules().getModules()) {
            if (m instanceof HudModule hud && hud.isEnabled()) {
                boolean hov = hud == draggingHud || (hud.isHovered(mouseX, mouseY) && !insideWindow(mouseX, mouseY));
                if (hov) {
                    Render2D.roundedRect(ctx, hud.getX() - 2, hud.getY() - 2, hud.getWidth() + 4, hud.getHeight() + 4, 3,
                            ColorUtil.withAlpha(ColorUtil.theme(), 40));
                }
            }
        }
        if (draggingHud != null) {
            float nx = mouseX - dragX, ny = mouseY - dragY;
            float hw = draggingHud.getWidth(), hh = draggingHud.getHeight();
            // snap to screen center lines and edges (hold Shift to move freely)
            boolean free = hasShiftDown();
            boolean snapX = false, snapY = false;
            if (!free) {
                if (Math.abs(nx + hw / 2 - width / 2f) < 5) { nx = width / 2f - hw / 2; snapX = true; }
                if (Math.abs(ny + hh / 2 - height / 2f) < 5) { ny = height / 2f - hh / 2; snapY = true; }
                if (Math.abs(nx - 4) < 5) nx = 4;
                if (Math.abs(nx + hw - (width - 4)) < 5) nx = width - 4 - hw;
                if (Math.abs(ny - 4) < 5) ny = 4;
                if (Math.abs(ny + hh - (height - 4)) < 5) ny = height - 4 - hh;
            }
            draggingHud.x.set((double) nx);
            draggingHud.y.set((double) ny);
            draggingHud.clamp(width, height);
            if (snapX) Render2D.rect(ctx, width / 2f - 0.5f, 0, 1, height, ColorUtil.withAlpha(ColorUtil.theme(), 120));
            if (snapY) Render2D.rect(ctx, 0, height / 2f - 0.5f, width, 1, ColorUtil.withAlpha(ColorUtil.theme(), 120));
        }
        if (draggingWindow) {
            windowX = mouseX - dragX;
            windowY = mouseY - dragY;
        }
        float ease = Animation.easeOutCubic(openAnim);
        float x = windowX, y = windowY;
        ctx.getMatrices().push();
        float cx = x + W / 2f, cy = y + H / 2f;
        float scale = (0.9f + 0.1f * ease) * fitScale();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.getMatrices().translate(-cx, -cy, 0);
        // mouse in window space (undo the open-scale animation)
        float mx = cx + (mouseX - cx) / scale, my = cy + (mouseY - cy) / scale;

        Render2D.shadow(ctx, x, y, W, H, 8, 12, ColorUtil.withAlpha(ColorUtil.theme(), (int) (110 * openAnim)));
        Render2D.roundedRect(ctx, x, y, W, H, 8, ColorUtil.fade(BG, openAnim));
        Render2D.roundedRect(ctx, x, y, SIDEBAR, H, 8, ColorUtil.fade(SIDE_BG, openAnim));
        Render2D.rect(ctx, x + SIDEBAR - 8, y, 8, H, ColorUtil.fade(SIDE_BG, openAnim));
        Render2D.rect(ctx, x + SIDEBAR, y + 8, 1, H - 16, ColorUtil.fade(0x20FFFFFF, openAnim));

        renderSidebar(ctx, x, y, mx, my);
        renderContent(ctx, x + SIDEBAR + 8, y + 8, W - SIDEBAR - 16, H - 16, mx, my);

        ctx.getMatrices().pop();

        if (binding != null || bindingSetting != null) {
            String s = "Press a key or mouse button to bind "
                    + (binding != null ? binding.getName() : bindingSetting.getName()) + " (Esc = clear)";
            float tw = Render2D.width(s) + 16;
            Render2D.roundedRect(ctx, width / 2f - tw / 2, 10, tw, 18, 4, 0xF0111116);
            Render2D.centeredText(ctx, s, width / 2f, 15, ColorUtil.theme());
        }
    }

    /** Shrinks the whole window on small screens / large GUI scales so it always fits. */
    private float fitScale() {
        return Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
    }

    private boolean insideWindow(double mx, double my) {
        double[] p = toWindow(mx, my);
        return p[0] >= windowX && p[0] <= windowX + W && p[1] >= windowY && p[1] <= windowY + H;
    }

    // ------------------------------------------------------------------ sidebar

    private void renderSidebar(DrawContext ctx, float x, float y, float mx, float my) {
        Watermark.duck(ctx, x + 10, y + 11, 16);
        Render2D.gradientText(ctx, "Duck", x + 31, y + 10, 0);
        Render2D.text(ctx, "Visual", x + 31 + Render2D.width("Duck "), y + 10, TEXT);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 31, y + 20, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        Render2D.text(ctx, "v" + DuckVisual.VERSION + " · 1.21.4", 0, 0, SUBTEXT);
        ctx.getMatrices().pop();
        // window drag handle
        hits.add(new Hit(x, y, SIDEBAR, 34, b -> {
            draggingWindow = true;
            dragX = (float) lastMouseX - windowX;
            dragY = (float) lastMouseY - windowY;
        }));

        float ty = y + 46;
        for (Category c : Category.values()) {
            boolean sel = c == current && search.isEmpty() && !paletteTab;
            boolean hov = mx >= x + 6 && mx <= x + SIDEBAR - 6 && my >= ty && my <= ty + 22;
            float a = Animation.approach(tabAnim.getOrDefault(c, 0f), sel ? 1 : hov ? 0.4f : 0, 12);
            tabAnim.put(c, a);
            if (a > 0.01f) {
                Render2D.roundedRect(ctx, x + 6, ty, SIDEBAR - 12, 22, 5, ColorUtil.withAlpha(ColorUtil.theme(), (int) (40 * a)));
                Render2D.roundedRect(ctx, x + 6, ty + 5, 2, 12, 1, ColorUtil.fade(ColorUtil.theme(), a));
            }
            int col = ColorUtil.lerp(SUBTEXT, TEXT, a);
            Render2D.roundedRect(ctx, x + 14, ty + 4, 14, 14, 4, sel ? ColorUtil.theme() : 0xFF24242D);
            Render2D.centeredText(ctx, c.icon, x + 21.5f, ty + 7, sel ? 0xFF111111 : SUBTEXT);
            Render2D.text(ctx, c.title, x + 34, ty + 7, col);
            int count = (int) DuckVisual.modules().getModules(c).stream().filter(Module::isEnabled).count();
            String cs = String.valueOf(count);
            Render2D.text(ctx, cs, x + SIDEBAR - 14 - Render2D.width(cs), ty + 7, ColorUtil.withAlpha(SUBTEXT, 160));
            hits.add(new Hit(x + 6, ty, SIDEBAR - 12, 22, b -> {
                scroll.put(current, scrollAnim);
                current = c;
                paletteTab = false;
                search = "";
                scrollAnim = scroll.getOrDefault(c, 0f);
            }));
            ty += 26;
        }

        // palette tab
        {
            boolean sel = paletteTab && search.isEmpty();
            boolean hov = mx >= x + 6 && mx <= x + SIDEBAR - 6 && my >= ty && my <= ty + 22;
            paletteAnim = Animation.approach(paletteAnim, sel ? 1 : hov ? 0.4f : 0, 12);
            float a = paletteAnim;
            if (a > 0.01f) {
                Render2D.roundedRect(ctx, x + 6, ty, SIDEBAR - 12, 22, 5, ColorUtil.withAlpha(ColorUtil.theme(), (int) (40 * a)));
                Render2D.roundedRect(ctx, x + 6, ty + 5, 2, 12, 1, ColorUtil.fade(ColorUtil.theme(), a));
            }
            Render2D.roundedGradient(ctx, x + 14, ty + 4, 14, 14, 4, ColorUtil.theme(0), ColorUtil.theme(10), ColorUtil.theme(10), ColorUtil.theme(0));
            Render2D.text(ctx, "Palette", x + 34, ty + 7, ColorUtil.lerp(SUBTEXT, TEXT, a));
            String cnt = String.valueOf(Theme.PRESETS.length);
            Render2D.text(ctx, cnt, x + SIDEBAR - 14 - Render2D.width(cnt), ty + 7, ColorUtil.withAlpha(SUBTEXT, 160));
            hits.add(new Hit(x + 6, ty, SIDEBAR - 12, 22, b -> {
                paletteTab = true;
                search = "";
            }));
        }

        // search field
        float sy = y + H - 30;
        Render2D.roundedRect(ctx, x + 8, sy, SIDEBAR - 16, 20, 5, searchFocused ? 0xFF22222B : 0xFF1A1A21);
        if (searchFocused) Render2D.roundedRect(ctx, x + 8, sy + 19, SIDEBAR - 16, 1, 0, ColorUtil.theme());
        String shown = search.isEmpty() && !searchFocused ? "Search..." : search
                + ((searchFocused && System.currentTimeMillis() / 500 % 2 == 0) ? "_" : "");
        Render2D.text(ctx, shown, x + 14, sy + 6, search.isEmpty() && !searchFocused ? SUBTEXT : TEXT);
        hits.add(new Hit(x + 8, sy, SIDEBAR - 16, 20, b -> searchFocused = true));
    }

    // ------------------------------------------------------------------ content

    private void renderContent(DrawContext ctx, float x, float y, float w, float h, float mx, float my) {
        if (paletteTab && search.isEmpty()) {
            renderPalette(ctx, x, y, w, h, mx, my);
            return;
        }
        List<Module> modules = search.isEmpty()
                ? DuckVisual.modules().getModules(current)
                : DuckVisual.modules().getModules().stream()
                .filter(m -> m.getName().toLowerCase().contains(search.toLowerCase())).toList();

        String title = search.isEmpty() ? current.title : "Search: " + search;
        Render2D.text(ctx, title, x + 2, y + 3, TEXT);
        String hint = "RMB - settings · MMB - bind";
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w - Render2D.width(hint) * 0.75f - 2, y + 4, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        Render2D.text(ctx, hint, 0, 0, SUBTEXT);
        ctx.getMatrices().pop();

        float top = y + 16, viewH = h - 16;
        float target = MathHelper.clamp(scroll.getOrDefault(current, 0f), -Math.max(0, contentHeight - viewH), 0);
        scroll.put(current, target);
        scrollAnim = Animation.approach(scrollAnim, target, 14);

        int sx1 = (int) x, sy1 = (int) top, sx2 = (int) (x + w), sy2 = (int) (top + viewH);
        ctx.enableScissor(sx1, sy1, sx2, sy2);
        float colW = (w - 6) / 2f;
        float[] colY = {top + scrollAnim, top + scrollAnim};
        boolean mouseInView = mx >= x && mx <= x + w && my >= top && my <= top + viewH;
        for (Module m : modules) {
            int col = colY[0] <= colY[1] ? 0 : 1;
            float cardX = x + col * (colW + 6);
            float hgt = renderCard(ctx, m, cardX, colY[col], colW, mx, my, mouseInView);
            colY[col] += hgt + 6;
        }
        ctx.disableScissor();
        contentHeight = Math.max(colY[0], colY[1]) - (top + scrollAnim);

        // scrollbar
        if (contentHeight > viewH) {
            float barH = viewH * viewH / contentHeight;
            float barY = top + (-scrollAnim / (contentHeight - viewH)) * (viewH - barH);
            Render2D.roundedRect(ctx, x + w + 3, barY, 2, barH, 1, ColorUtil.withAlpha(ColorUtil.theme(), 150));
        }
    }

    /** Grid of theme presets plus the animation mode chips. */
    private void renderPalette(DrawContext ctx, float x, float y, float w, float h, float mx, float my) {
        Theme theme = DuckVisual.modules().get(Theme.class);
        Render2D.text(ctx, "Palette", x + 2, y + 3, TEXT);

        // mode chips
        float cx = x + w;
        String[] modes = {"Rainbow", "Static", "Gradient"};
        for (String mode : modes) {
            float cw = Render2D.width(mode) + 10;
            cx -= cw;
            boolean sel = theme.mode.is(mode);
            Render2D.roundedRect(ctx, cx, y, cw, 13, 4, sel ? ColorUtil.theme() : 0xFF22222B);
            Render2D.text(ctx, mode, cx + 5, y + 3, sel ? 0xFF111111 : SUBTEXT);
            hits.add(new Hit(cx, y, cw, 13, b -> theme.mode.set(mode)));
            cx -= 4;
        }

        int cols = 5;
        float gap = 6, top = y + 20;
        float tw = (w - gap * (cols - 1)) / cols, th = 36;
        int n = Theme.PRESETS.length + 1;
        for (int i = 0; i < n; i++) {
            float tx = x + (i % cols) * (tw + gap), ty = top + (i / cols) * (th + gap);
            boolean custom = i == Theme.PRESETS.length;
            String name = custom ? "Custom" : Theme.PRESETS[i].name();
            int c1 = custom ? theme.first.get() | 0xFF000000 : Theme.PRESETS[i].first();
            int c2 = custom ? theme.second.get() | 0xFF000000 : Theme.PRESETS[i].second();
            boolean sel = theme.palette.is(name);
            boolean hov = mx >= tx && mx <= tx + tw && my >= ty && my <= ty + th;
            if (sel) Render2D.shadow(ctx, tx, ty, tw, th, 5, 4, ColorUtil.withAlpha(c1, 120));
            Render2D.roundedRect(ctx, tx, ty, tw, th, 5, sel ? 0xFF24242D : hov ? CARD_HOVER : CARD);
            Render2D.roundedGradient(ctx, tx + 3, ty + 3, tw - 6, 18, 4, c1, c2, c2, c1);
            if (sel) Render2D.centeredText(ctx, "\u2714", tx + tw / 2, ty + 8, 0xFF111111);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(tx + tw / 2 - Render2D.width(name) * 0.4f, ty + 25, 0);
            ctx.getMatrices().scale(0.8f, 0.8f, 1);
            Render2D.text(ctx, name, 0, 0, sel ? TEXT : SUBTEXT);
            ctx.getMatrices().pop();
            hits.add(new Hit(tx, ty, tw, th, b -> {
                theme.palette.set(name);
                if (custom && b == 1) {
                    // right click on Custom jumps to the Theme module to edit its colors
                    paletteTab = false;
                    current = Category.MISC;
                    expanded.put(theme, true);
                }
            }));
        }
        float hy = top + ((n + cols - 1) / cols) * (th + gap);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 2, hy, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1);
        Render2D.text(ctx, "RMB on Custom - edit your own colors", 0, 0, SUBTEXT);
        ctx.getMatrices().pop();
    }

    private float renderCard(DrawContext ctx, Module m, float x, float y, float w, float mx, float my, boolean mouseInView) {
        boolean showDesc = DuckVisual.modules().get(ClickGui.class).descriptions.get();
        float headH = showDesc ? 30 : 22;
        float ex = Animation.approach(expand.getOrDefault(m, 0f), expanded.getOrDefault(m, false) ? 1 : 0, 14);
        expand.put(m, ex);

        float settingsH = 0;
        for (Setting<?> s : m.getSettings()) if (s.isVisible()) settingsH += settingHeight(s);
        float total = headH + (settingsH + (settingsH > 0 ? 6 : 0)) * ex;

        boolean hover = mouseInView && mx >= x && mx <= x + w && my >= y && my <= y + headH;
        Render2D.roundedRect(ctx, x, y, w, total, 5, hover ? CARD_HOVER : CARD);
        if (m.animation > 0.01f) {
            Render2D.roundedRect(ctx, x, y, w, total, 5, ColorUtil.withAlpha(ColorUtil.theme(), (int) (14 * m.animation)));
        }

        // title + description
        Render2D.text(ctx, m.getName(), x + 7, y + 7, TEXT);
        if (m.getKey() > 0 && !(m instanceof ClickGui)) {
            String k = Keys.name(m.getKey());
            float kx = x + 10 + Render2D.width(m.getName());
            ctx.getMatrices().push();
            ctx.getMatrices().translate(kx, y + 8, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            Render2D.roundedRect(ctx, -2, -2, Render2D.width(k) + 4, 12, 2, 0xFF2A2A33);
            Render2D.text(ctx, k, 0, 0, SUBTEXT);
            ctx.getMatrices().pop();
        }
        if (binding == m) Render2D.text(ctx, "...", x + 10 + Render2D.width(m.getName()), y + 7, ColorUtil.theme());
        if (showDesc) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + 7, y + 18, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1);
            String desc = m.getDescription();
            float maxW = (w - 46) / 0.7f;
            while (Render2D.width(desc) > maxW && desc.length() > 3) desc = desc.substring(0, desc.length() - 4) + "..";
            Render2D.text(ctx, desc, 0, 0, SUBTEXT);
            ctx.getMatrices().pop();
        }

        // toggle switch
        if (!(m instanceof ClickGui)) {
            float sw = 20, sh = 10, swX = x + w - sw - 7, swY = y + headH / 2 - sh / 2;
            float t = m.animation;
            Render2D.roundedRect(ctx, swX, swY, sw, sh, 5, ColorUtil.lerp(0xFF2C2C36, ColorUtil.theme(), t));
            Render2D.circle(ctx, swX + 5 + (sw - 10) * t, swY + 5, 3.5f, ColorUtil.lerp(0xFF8C8C99, 0xFFFFFFFF, t));
        } else {
            Render2D.text(ctx, "⚙", x + w - 14, y + headH / 2 - 4, SUBTEXT);
        }
        if (mouseInView) {
            hits.add(new Hit(x, y, w, headH, b -> {
                if (b == 0) m.toggle();
                else if (b == 1) expanded.put(m, !expanded.getOrDefault(m, false));
                else if (b == 2) {
                    bindingSetting = null;
                    binding = m;
                }
            }));
        }

        if (ex > 0.01f && settingsH > 0) {
            float sy = y + headH;
            Render2D.rect(ctx, x + 7, sy, w - 14, 1, 0x18FFFFFF);
            sy += 3;
            // clip settings while the card animates open
            float clipBottom = y + total;
            for (Setting<?> s : m.getSettings()) {
                if (!s.isVisible()) continue;
                float sh = settingHeight(s);
                if (sy > clipBottom) break;
                renderSetting(ctx, s, x + 7, sy, w - 14, mx, my, mouseInView && sy + sh <= clipBottom + 1);
                sy += sh;
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ settings

    private float settingHeight(Setting<?> s) {
        if (s instanceof NumberSetting) return 22;
        if (s instanceof BindSetting) return 15;
        if (s instanceof ColorSetting c) return pickerOpen.getOrDefault(c, false) ? 16 + 4 * 10 + 2 : 16;
        return 15;
    }

    private void renderSetting(DrawContext ctx, Setting<?> s, float x, float y, float w, float mx, float my, boolean interact) {
        if (s instanceof BindSetting bs) {
            Render2D.text(ctx, s.getName(), x, y + 3, SUBTEXT);
            String v = bindingSetting == bs ? "..." : Keys.name(bs.get());
            float vw = Render2D.width(v) + 8;
            Render2D.roundedRect(ctx, x + w - vw, y + 1, vw, 12, 3, bindingSetting == bs ? ColorUtil.withAlpha(ColorUtil.theme(), 80) : 0xFF24242D);
            Render2D.text(ctx, v, x + w - vw + 4, y + 3, bs.get() > 0 ? ColorUtil.theme() : SUBTEXT);
            if (interact) hits.add(new Hit(x, y, w, 14, btn -> {
                if (btn == 1) bs.set(-1);
                else {
                    binding = null;
                    bindingSetting = bs;
                }
            }));
        } else if (s instanceof BooleanSetting b) {
            Render2D.text(ctx, s.getName(), x, y + 3, b.get() ? TEXT : SUBTEXT);
            float bx = x + w - 10;
            Render2D.roundedRect(ctx, bx, y + 2, 10, 10, 3, b.get() ? ColorUtil.theme() : 0xFF2C2C36);
            if (b.get()) Render2D.centeredText(ctx, "✔", bx + 5, y + 3, 0xFF111111);
            if (interact) hits.add(new Hit(x, y, w, 14, btn -> b.toggle()));
        } else if (s instanceof NumberSetting n) {
            Render2D.text(ctx, s.getName(), x, y + 2, SUBTEXT);
            String v = n.display();
            Render2D.text(ctx, v, x + w - Render2D.width(v), y + 2, TEXT);
            float by = y + 14, bh = 3;
            float frac = (float) ((n.get() - n.getMin()) / (n.getMax() - n.getMin()));
            Render2D.roundedRect(ctx, x, by, w, bh, 1.5f, 0xFF2C2C36);
            Render2D.roundedGradient(ctx, x, by, Math.max(bh, w * frac), bh, 1.5f,
                    ColorUtil.theme(0), ColorUtil.theme(8), ColorUtil.theme(8), ColorUtil.theme(0));
            Render2D.circle(ctx, x + w * frac, by + 1.5f, 3, 0xFFFFFFFF);
            if (interact) hits.add(new Hit(x, y, w, 20, btn -> activeSlider = new Slider(x, w,
                    f -> n.set(n.getMin() + (n.getMax() - n.getMin()) * f))));
        } else if (s instanceof ModeSetting mo) {
            Render2D.text(ctx, s.getName(), x, y + 3, SUBTEXT);
            String v = mo.get();
            float vw = Render2D.width(v) + 8;
            Render2D.roundedRect(ctx, x + w - vw, y + 1, vw, 12, 3, 0xFF24242D);
            Render2D.text(ctx, v, x + w - vw + 4, y + 3, ColorUtil.theme());
            if (interact) hits.add(new Hit(x, y, w, 14, btn -> mo.cycle(btn == 0)));
        } else if (s instanceof ColorSetting c) {
            Render2D.text(ctx, s.getName(), x, y + 3, SUBTEXT);
            Render2D.roundedRect(ctx, x + w - 18, y + 2, 18, 10, 3, c.get() | 0xFF000000);
            if (interact) hits.add(new Hit(x, y, w, 14, btn -> pickerOpen.put(c, !pickerOpen.getOrDefault(c, false))));
            if (pickerOpen.getOrDefault(c, false)) renderPicker(ctx, c, x, y + 16, w, interact);
        }
    }

    private void renderPicker(DrawContext ctx, ColorSetting c, float x, float y, float w, boolean interact) {
        float bh = 6;
        // hue
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            int c1 = 0xFF000000 | Color.HSBtoRGB(i / (float) steps, 1, 1);
            int c2 = 0xFF000000 | Color.HSBtoRGB((i + 1) / (float) steps, 1, 1);
            Render2D.horizontalGradient(ctx, x + w * i / steps, y, w / steps + 0.5f, bh, c1, c2);
        }
        marker(ctx, x + w * c.getHue(), y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, c::setHue)));
        y += 10;
        Render2D.horizontalGradient(ctx, x, y, w, bh, 0xFF000000 | Color.HSBtoRGB(c.getHue(), 0, c.getBrightness()),
                0xFF000000 | Color.HSBtoRGB(c.getHue(), 1, c.getBrightness()));
        marker(ctx, x + w * c.getSaturation(), y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, c::setSaturation)));
        y += 10;
        Render2D.horizontalGradient(ctx, x, y, w, bh, 0xFF000000,
                0xFF000000 | Color.HSBtoRGB(c.getHue(), c.getSaturation(), 1));
        marker(ctx, x + w * c.getBrightness(), y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, c::setBrightness)));
        y += 10;
        Render2D.horizontalGradient(ctx, x, y, w, bh, c.get() & 0xFFFFFF, c.get() | 0xFF000000);
        marker(ctx, x + w * c.getAlpha() / 255f, y, bh);
        if (interact) hits.add(new Hit(x, y - 2, w, bh + 4, b -> activeSlider = new Slider(x, w, f -> c.setAlpha((int) (f * 255)))));
    }

    private static void marker(DrawContext ctx, float x, float y, float h) {
        Render2D.rect(ctx, x - 1.5f, y - 1, 3, h + 2, 0xFF000000);
        Render2D.rect(ctx, x - 0.5f, y - 1, 1, h + 2, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ input

    private double lastMouseX, lastMouseY;

    /** Converts screen mouse coords into the scaled window space used while rendering. */
    private double[] toWindow(double mouseX, double mouseY) {
        float scale = (0.9f + 0.1f * Animation.easeOutCubic(openAnim)) * fitScale();
        float cx = windowX + W / 2f, cy = windowY + H / 2f;
        return new double[]{cx + (mouseX - cx) / scale, cy + (mouseY - cy) / scale};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (binding != null || bindingSetting != null) {
            // middle / side mouse buttons can be bound; left and right click cancel
            if (button >= 2) applyBind(Keys.MOUSE + button);
            binding = null;
            bindingSetting = null;
            return true;
        }
        if (!insideWindow(mouseX, mouseY)) {
            searchFocused = false;
            for (Module m : DuckVisual.modules().getModules()) {
                if (m instanceof HudModule hud && hud.isEnabled() && hud.isHovered(mouseX, mouseY) && button == 0) {
                    draggingHud = hud;
                    dragX = (float) (mouseX - hud.getX());
                    dragY = (float) (mouseY - hud.getY());
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double[] p = toWindow(mouseX, mouseY);
        boolean hitSearch = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.inside(p[0], p[1])) {
                h.action.accept(button);
                hitSearch = h.y > windowY + H - 40 && h.x < windowX + SIDEBAR;
                if (activeSlider != null) mouseDragged(mouseX, mouseY, button, 0, 0);
                break;
            }
        }
        if (!hitSearch) searchFocused = false;
        return true;
    }

    private void applyBind(int key) {
        if (bindingSetting != null) {
            bindingSetting.set(key);
        } else if (binding != null) {
            binding.setKey(key);
            if (binding instanceof ClickGui && (key == -1 || key >= Keys.MOUSE)) binding.setKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingWindow = false;
        draggingHud = null;
        activeSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (activeSlider != null) {
            double[] p = toWindow(mouseX, mouseY);
            activeSlider.setter.accept(MathHelper.clamp((float) ((p[0] - activeSlider.x) / activeSlider.w), 0f, 1f));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (insideWindow(mouseX, mouseY)) {
            scroll.put(current, scroll.getOrDefault(current, 0f) + (float) vertical * 24);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (binding != null || bindingSetting != null) {
            applyBind(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    || keyCode == GLFW.GLFW_KEY_BACKSPACE ? -1 : keyCode);
            binding = null;
            bindingSetting = null;
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        int guiKey = DuckVisual.modules().get(ClickGui.class).getKey();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == guiKey) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && search.length() < 16 && chr >= ' ') {
            search += chr;
            scroll.put(current, 0f);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closeFrom = openAnim;
        animStart = System.nanoTime();
        draggingWindow = false;
        draggingHud = null;
        activeSlider = null;
        searchFocused = false;
        binding = null;
        bindingSetting = null;
    }

    @Override
    public void removed() {
        DuckVisual.config().saveAsync();
    }
}
