package ru.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;
import ru.visuals.mixin.SimpleOptionAccessor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class VisualsClient implements ClientModInitializer {
    private static final String CATEGORY = "category.visuals";
    private static final double ZOOM_FOV = 30.0;
    private static final double FULLBRIGHT_GAMMA = 16.0;

    private static final ParticleEffect[] STYLES = {
            ParticleTypes.END_ROD, ParticleTypes.WITCH, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.ENCHANT
    };
    private static final String[] STYLE_NAMES = {"Звёзды", "Магия", "Искры", "Руны"};

    private KeyBinding zoomKey;
    private boolean zooming = false;
    private int savedFov = 70;
    private static double savedGamma = 1.0;

    // .bind add
    private int bindState = 0; // 0 нет, 1 ждём отпускания клавиш, 2 ловим клавишу
    private boolean menuWasDown = false;

    // цель
    private Entity target = null;
    private int targetTicks = 0;
    private int age = 0;

    @Override
    public void onInitializeClient() {
        Config.load();

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visuals.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

        ClientSendMessageEvents.ALLOW_CHAT.register(this::onChat);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register(this::renderHud);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::restoreAll);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) onAttack(entity);
            return ActionResult.PASS;
        });
    }

    // ---------- чат-команды ----------

    private boolean onChat(String message) {
        String m = message.trim().toLowerCase();
        if (!m.startsWith(".bind")) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (m.equals(".bind add")) {
            bindState = 1;
            say(mc, "§dVisuals§r: нажми кнопку для открытия меню (Esc — отмена).");
        } else if (m.equals(".bind clear")) {
            Config.menuKey = -1;
            Config.save();
            say(mc, "§dVisuals§r: кнопка меню удалена.");
        } else {
            say(mc, "§dVisuals§r: .bind add — назначить кнопку меню, .bind clear — убрать.");
        }
        return false; // сообщение не уходит на сервер
    }

    private static void say(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(text), false);
    }

    // ---------- тик ----------

    private boolean isDown(MinecraftClient mc, int key) {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private void tick(MinecraftClient mc) {
        if (mc.options == null || mc.player == null || mc.world == null) return;
        age++;

        handleBind(mc);
        handleMenuKey(mc);
        handleZoom(mc);
        spawnParticles(mc);
    }

    private void handleBind(MinecraftClient mc) {
        if (bindState == 0 || mc.currentScreen != null) return;
        if (bindState == 1) {
            boolean any = false;
            for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
                if (isDown(mc, k)) { any = true; break; }
            }
            if (!any) bindState = 2;
            return;
        }
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            if (!isDown(mc, k)) continue;
            bindState = 0;
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                say(mc, "§dVisuals§r: отменено.");
            } else {
                Config.menuKey = k;
                Config.save();
                say(mc, "§dVisuals§r: меню теперь на кнопке §f" + menuKeyName() + "§r.");
            }
            menuWasDown = true;
            return;
        }
    }

    private void handleMenuKey(MinecraftClient mc) {
        if (Config.menuKey < 0) return;
        boolean down = isDown(mc, Config.menuKey);
        if (down && !menuWasDown && mc.currentScreen == null && bindState == 0) {
            mc.setScreen(new MenuScreen());
        }
        menuWasDown = down;
    }

    private void handleZoom(MinecraftClient mc) {
        boolean want = zoomKey.isPressed() && mc.currentScreen == null;
        if (want && !zooming) {
            savedFov = mc.options.getFov().getValue();
            zooming = true;
        }
        if (zooming) {
            if (want) {
                mc.options.getFov().setValue((int) ZOOM_FOV);
            } else {
                mc.options.getFov().setValue(savedFov);
                zooming = false;
            }
        }
    }

    // ---------- частицы ----------

    private ParticleEffect style() {
        return STYLES[Math.floorMod(Config.style, STYLES.length)];
    }

    private void onAttack(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        target = entity;
        targetTicks = 100;

        if (Config.hitBubble) {
            Random r = mc.world.random;
            double cx = entity.getX();
            double cy = entity.getY() + entity.getHeight() / 2.0;
            double cz = entity.getZ();
            for (int i = 0; i < 50; i++) {
                double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-4) continue;
                dx /= len; dy /= len; dz /= len;
                mc.world.addParticle(style(),
                        cx + dx * 0.4, cy + dy * 0.4, cz + dz * 0.4,
                        dx * 0.12, dy * 0.12, dz * 0.12);
            }
        }
    }

    private void spawnParticles(MinecraftClient mc) {
        Random r = mc.world.random;

        if (Config.particles) {
            for (int i = 0; i < Config.density; i++) {
                double x = mc.player.getX() + (r.nextDouble() - 0.5) * 24;
                double y = mc.player.getY() + r.nextDouble() * 8 - 1;
                double z = mc.player.getZ() + (r.nextDouble() - 0.5) * 24;
                mc.world.addParticle(style(), x, y, z,
                        (r.nextDouble() - 0.5) * 0.01, 0.01, (r.nextDouble() - 0.5) * 0.01);
            }
        }

        if (target != null) {
            if (targetTicks-- <= 0 || !target.isAlive() || target.isRemoved()) {
                target = null;
            } else if (Config.targetEsp) {
                double radius = Math.max(0.5, target.getWidth() * 0.9);
                int n = 14;
                double baseY = target.getY() + 0.05;
                for (int i = 0; i < n; i++) {
                    double a = age * 0.18 + i * (Math.PI * 2 / n);
                    double x = target.getX() + Math.cos(a) * radius;
                    double z = target.getZ() + Math.sin(a) * radius;
                    mc.world.addParticle(style(), x, baseY, z, 0, 0.02, 0);
                }
            }
        }
    }

    // ---------- fullbright ----------

    @SuppressWarnings("unchecked")
    private static void writeGamma(MinecraftClient mc, double value) {
        SimpleOption<Double> gamma = mc.options.getGamma();
        ((SimpleOptionAccessor<Double>) (Object) gamma).visuals$setValue(value);
    }

    public static void setFullbright(MinecraftClient mc, boolean on) {
        if (mc.options == null || on == Config.fullbright) return;
        if (on) {
            savedGamma = mc.options.getGamma().getValue();
            writeGamma(mc, FULLBRIGHT_GAMMA);
        } else {
            writeGamma(mc, savedGamma);
        }
        Config.fullbright = on;
    }

    private void restoreAll(MinecraftClient mc) {
        if (mc.options == null) return;
        setFullbright(mc, false);
        if (zooming) {
            mc.options.getFov().setValue(savedFov);
            zooming = false;
        }
    }

    // ---------- для меню ----------

    public static String menuKeyName() {
        if (Config.menuKey < 0) return "не назначена";
        return InputUtil.fromKeyCode(Config.menuKey, 0).getLocalizedText().getString();
    }

    public static String styleName() {
        return STYLE_NAMES[Math.floorMod(Config.style, STYLE_NAMES.length)];
    }

    public static int styleCount() {
        return STYLES.length;
    }

    // ---------- HUD ----------

    private void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Config.hud && !Config.effects) return;
        if (mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        if (mc.currentScreen instanceof MenuScreen) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        if (Config.hud) {
            // вотермарка сверху по центру
            String mark = "Visuals  |  " + mc.player.getName().getString() + "  |  "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            int w = mc.textRenderer.getWidth(mark);
            int x = (sw - w) / 2;
            ctx.fill(x - 6, 3, x + w + 6, 15, 0xA0141420);
            ctx.fill(x - 6, 3, x + w + 6, 4, 0xFF9B5CFF);
            ctx.drawText(mc.textRenderer, mark, x, 6, 0xFFFFFFFF, false);

            // координаты слева снизу
            Direction f = mc.player.getHorizontalFacing();
            String info = String.format("%d %d %d  |  %s  |  %d fps",
                    (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()),
                    (int) Math.floor(mc.player.getZ()), f.asString().toUpperCase(), mc.getCurrentFps());
            int iw = mc.textRenderer.getWidth(info);
            ctx.fill(2, sh - 14, iw + 10, sh - 2, 0xA0141420);
            ctx.drawText(mc.textRenderer, info, 6, sh - 11, 0xFFFFFFFF, false);
        }

        if (Config.effects) {
            var list = mc.player.getStatusEffects();
            if (!list.isEmpty()) {
                int w = 110;
                int x = sw - w - 4;
                int y = 4;
                int h = 14 + list.size() * 11;
                ctx.fill(x, y, x + w, y + h, 0xA0141420);
                ctx.fill(x, y, x + w, y + 1, 0xFF9B5CFF);
                ctx.drawText(mc.textRenderer, "Эффекты", x + 4, y + 4, 0xFFFFFFFF, false);
                int ly = y + 15;
                for (StatusEffectInstance e : list) {
                    String name = e.getEffectType().value().getName().getString();
                    String time;
                    if (e.isInfinite()) {
                        time = "∞";
                    } else {
                        int s = e.getDuration() / 20;
                        time = String.format("%02d:%02d", s / 60, s % 60);
                    }
                    ctx.drawText(mc.textRenderer, name, x + 4, ly, 0xFFC0C0D0, false);
                    ctx.drawText(mc.textRenderer, time, x + w - 4 - mc.textRenderer.getWidth(time), ly, 0xFFFFFFFF, false);
                    ly += 11;
                }
            }
        }
    }
}
