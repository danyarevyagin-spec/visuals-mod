package ru.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class MenuScreen extends Screen {
    private static final String[] NAMES = {
            "Fullbright", "HUD (вотермарка, координаты)", "Список эффектов",
            "Частицы в воздухе", "Target ESP", "Hit Bubble"
    };
    private static final int W = 230, ROW = 20, ACCENT = 0xFF9B5CFF;

    private int px, py, ph;
    private boolean draggingSlider = false;

    public MenuScreen() {
        super(Text.literal("Visuals"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        ph = 40 + NAMES.length * ROW + ROW + 30 + 18;
        px = (width - W) / 2;
        py = (height - ph) / 2;
    }

    private boolean get(int i) {
        return switch (i) {
            case 0 -> Config.fullbright;
            case 1 -> Config.hud;
            case 2 -> Config.effects;
            case 3 -> Config.particles;
            case 4 -> Config.targetEsp;
            default -> Config.hitBubble;
        };
    }

    private void toggle(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (i) {
            case 0 -> VisualsClient.setFullbright(mc, !Config.fullbright);
            case 1 -> Config.hud = !Config.hud;
            case 2 -> Config.effects = !Config.effects;
            case 3 -> Config.particles = !Config.particles;
            case 4 -> Config.targetEsp = !Config.targetEsp;
            default -> Config.hitBubble = !Config.hitBubble;
        }
        Config.save();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x70000000);
        ctx.fill(px, py, px + W, py + ph, 0xE0141420);
        ctx.fill(px, py, px + W, py + 2, ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, "Visuals", px + W / 2, py + 8, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "Кнопка меню: " + VisualsClient.menuKeyName(),
                px + W / 2, py + 22, 0xFF9090A0);

        int y = py + 38;
        for (int i = 0; i < NAMES.length; i++) {
            boolean on = get(i);
            boolean hover = mouseX >= px + 8 && mouseX <= px + W - 8 && mouseY >= y && mouseY < y + ROW - 2;
            ctx.fill(px + 8, y, px + W - 8, y + ROW - 2, hover ? 0xFF2A2A40 : 0xFF1F1F30);
            ctx.drawText(textRenderer, NAMES[i], px + 14, y + 6, 0xFFFFFFFF, false);
            int sx = px + W - 42;
            ctx.fill(sx, y + 4, sx + 26, y + ROW - 6, on ? ACCENT : 0xFF444455);
            int kx = on ? sx + 14 : sx + 2;
            ctx.fill(kx, y + 5, kx + 10, y + ROW - 7, 0xFFFFFFFF);
            y += ROW;
        }

        // стиль частиц
        boolean hs = mouseX >= px + 8 && mouseX <= px + W - 8 && mouseY >= y && mouseY < y + ROW - 2;
        ctx.fill(px + 8, y, px + W - 8, y + ROW - 2, hs ? 0xFF2A2A40 : 0xFF1F1F30);
        ctx.drawText(textRenderer, "Стиль частиц: " + VisualsClient.styleName(), px + 14, y + 6, 0xFFFFFFFF, false);
        y += ROW + 4;

        // плотность
        ctx.drawText(textRenderer, "Плотность: " + Config.density, px + 14, y, 0xFFFFFFFF, false);
        int bx1 = px + 14, bx2 = px + W - 14, by = y + 14;
        ctx.fill(bx1, by, bx2, by + 4, 0xFF444455);
        float t = (Config.density - 1) / 9f;
        int hx = bx1 + (int) ((bx2 - bx1) * t);
        ctx.fill(bx1, by, hx, by + 4, ACCENT);
        ctx.fill(hx - 3, by - 3, hx + 3, by + 7, 0xFFFFFFFF);

        ctx.drawCenteredTextWithShadow(textRenderer, "Новая кнопка: в чате .bind add",
                px + W / 2, py + ph - 14, 0xFF707080);
    }

    private void setDensityFromMouse(double mx) {
        int bx1 = px + 14, bx2 = px + W - 14;
        double t = Math.max(0, Math.min(1, (mx - bx1) / (double) (bx2 - bx1)));
        Config.density = 1 + (int) Math.round(t * 9);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int y = py + 38;
            for (int i = 0; i < NAMES.length; i++) {
                if (mx >= px + 8 && mx <= px + W - 8 && my >= y && my < y + ROW - 2) {
                    toggle(i);
                    return true;
                }
                y += ROW;
            }
            if (mx >= px + 8 && mx <= px + W - 8 && my >= y && my < y + ROW - 2) {
                Config.style = (Config.style + 1) % VisualsClient.styleCount();
                Config.save();
                return true;
            }
            y += ROW + 4;
            if (mx >= px + 8 && mx <= px + W - 8 && my >= y && my <= y + 22) {
                draggingSlider = true;
                setDensityFromMouse(mx);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingSlider) {
            setDensityFromMouse(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingSlider) {
            draggingSlider = false;
            Config.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Config.menuKey) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
