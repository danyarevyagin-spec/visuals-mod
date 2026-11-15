package ru.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import ru.visuals.mixin.SimpleOptionAccessor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class VisualsClient implements ClientModInitializer {
    private static final String CATEGORY = "category.visuals";
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private KeyBinding zoomKey;

    // zoom
    private boolean zoomActive = false;
    private float zoomCur;
    private int savedFov = 70;

    // fullbright
    private static boolean fbApplied = false;
    private static double fbLevel = -1;
    private static double savedGamma = 1.0;

    // .bind add: 0 нет, 1 ждём отпускания клавиш, 2 ловим клавишу
    private int bindState = 0;
    private boolean menuWasDown = false;

    // цель (частицы)
    private Entity target = null;
    private int targetTicks = 0;
    private int age = 0;

    // таргет инфо (наведение)
    private LivingEntity hoveredTarget = null;
    private float animatedTargetHp = 0f;

    // круги при прыжке и клики
    private static final List<JumpCircle> JUMP_CIRCLES = new LinkedList<>();
    private static final List<Long> LEFT_CLICKS = new LinkedList<>();
    private static final List<Long> RIGHT_CLICKS = new LinkedList<>();

    // прочее
    private Object lastWorld = null;
    private double prevX, prevZ, speed;

    @Override
    public void onInitializeClient() {
        Config.load();

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.visuals.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

        ClientSendMessageEvents.ALLOW_CHAT.register(this::onChat);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register(this::renderHud);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::renderWorld);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::restoreAll);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new FigureRenderer(playerRenderer));
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) onAttack(entity);
            return ActionResult.PASS;
        });
    }

    public static void onPlayerJump(Vec3d pos) {
        if (Features.JUMPCIRCLE.on()) {
            JUMP_CIRCLES.add(new JumpCircle(pos, System.currentTimeMillis()));
        }
    }

    public static void onMouseClick(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) LEFT_CLICKS.add(now);
        else if (button == 1) RIGHT_CLICKS.add(now);
    }

    private static int swatchColor(int index) {
        if (index <= 0) return Theme.accent();
        return Theme.SWATCHES[Math.min(index, Theme.SWATCHES.length) - 1];
    }

    // ================= чат-команды =================

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
        return false;
    }

    private static void say(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(text), false);
    }

    // ================= тик =================

    private boolean isDown(MinecraftClient mc, int key) {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private void tick(MinecraftClient mc) {
        if (mc.options == null) return;
        applyFullbright(mc);
        if (mc.player == null || mc.world == null) {
            lastWorld = null;
            return;
        }
        age++;
        greet(mc);
        handleBind(mc);
        handleMenuKey(mc);
        handleZoom(mc);
        trackSpeed(mc);
        spawnParticles(mc);
    }

    private void greet(MinecraftClient mc) {
        if (lastWorld == mc.world) return;
        lastWorld = mc.world;
        if (!Features.M_GREET.value) return;
        if (Config.menuKey < 0) say(mc, "§dVisuals§r загружен. Напиши §f.bind add§r, чтобы выбрать кнопку меню.");
        else say(mc, "§dVisuals§r загружен. Меню: §f" + menuKeyName() + "§r.");
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

    private void trackSpeed(MinecraftClient mc) {
        double x = mc.player.getX(), z = mc.player.getZ();
        double inst = Math.hypot(x - prevX, z - prevZ) * 20.0;
        if (inst > 100) inst = 0;
        speed = speed * 0.7 + inst * 0.3;
        prevX = x;
        prevZ = z;
    }

    // ================= Fullbright / Zoom =================

    @SuppressWarnings("unchecked")
    private static void writeGamma(MinecraftClient mc, double v) {
        SimpleOption<Double> o = mc.options.getGamma();
        ((SimpleOptionAccessor<Double>) (Object) o).visuals$setValue(v);
    }

    @SuppressWarnings("unchecked")
    private static void writeFov(MinecraftClient mc, int v) {
        SimpleOption<Integer> o = mc.options.getFov();
        ((SimpleOptionAccessor<Integer>) (Object) o).visuals$setValue(v);
    }

    private void applyFullbright(MinecraftClient mc) {
        boolean want = Features.FULLBRIGHT.on();
        double level = Features.FB_LEVEL.get();
        if (want) {
            if (!fbApplied) {
                savedGamma = mc.options.getGamma().getValue();
                fbApplied = true;
                fbLevel = -1;
            }
            if (fbLevel != level) {
                writeGamma(mc, level);
                fbLevel = level;
            }
        } else if (fbApplied) {
            writeGamma(mc, savedGamma);
            fbApplied = false;
        }
    }

    private void handleZoom(MinecraftClient mc) {
        boolean want = Features.ZOOM.on() && zoomKey.isPressed() && mc.currentScreen == null;
        if (want && !zoomActive) {
            savedFov = mc.options.getFov().getValue();
            zoomCur = savedFov;
            zoomActive = true;
        }
        if (!zoomActive) return;
        float targetFov = want ? Features.Z_FOV.getf() : savedFov;
        float k = Math.max(0.04f, 1f - Features.Z_SMOOTH.getf() / 100f);
        zoomCur += (targetFov - zoomCur) * k;
        if (!want && Math.abs(targetFov - zoomCur) < 0.5f) {
            writeFov(mc, savedFov);
            zoomActive = false;
            return;
        }
        writeFov(mc, Math.round(zoomCur));
    }

    private void restoreAll(MinecraftClient mc) {
        if (mc.options == null) return;
        if (fbApplied) {
            writeGamma(mc, savedGamma);
            fbApplied = false;
        }
        if (zoomActive) {
            writeFov(mc, savedFov);
            zoomActive = false;
        }
    }

    // ================= частицы и 3D мир =================

    private static boolean passes(Entity e, int mode) {
        if (mode == 0) return true;
        if (mode == 1) return e instanceof PlayerEntity;
        return e instanceof LivingEntity && !(e instanceof PlayerEntity);
    }

    private void onAttack(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (passes(entity, Features.TG_FILTER.index)) {
            target = entity;
            targetTicks = Features.TG_TIME.geti() * 20;
        }

        if (Features.HIT.on() && passes(entity, Features.H_FILTER.index)) {
            burst(mc, entity, Features.PARTICLES[Features.H_STYLE.index], Features.H_COUNT.geti(),
                    Features.H_RADIUS.get(), Features.H_SPEED.get(), Features.H_SHAPE.index);
        }
    }

    private void burst(MinecraftClient mc, Entity e, ParticleEffect eff, int count, double rad, double speed, int shape) {
        Random r = mc.world.random;
        double cx = e.getX(), cy = e.getY() + e.getHeight() / 2.0, cz = e.getZ();
        switch (shape) {
            case 0 -> {
                for (int i = 0; i < count; i++) {
                    double dx = r.nextGaussian(), dy = r.nextGaussian(), dz = r.nextGaussian();
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len < 1e-4) continue;
                    dx /= len; dy /= len; dz /= len;
                    mc.world.addParticle(eff, cx + dx * rad, cy + dy * rad, cz + dz * rad,
                            dx * speed, dy * speed, dz * speed);
                }
            }
            case 1 -> {
                for (int i = 0; i < count; i++) {
                    double a = Math.PI * 2 * i / count;
                    double cs = Math.cos(a), sn = Math.sin(a);
                    mc.world.addParticle(eff, cx + cs * rad, cy, cz + sn * rad, cs * speed, 0.0, sn * speed);
                }
            }
            case 2 -> {
                for (int i = 0; i < count; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double rr = r.nextDouble() * rad;
                    mc.world.addParticle(eff, cx + Math.cos(a) * rr, e.getY() + 0.1, cz + Math.sin(a) * rr,
                            Math.cos(a) * speed * 0.3, speed * (0.8 + r.nextDouble()), Math.sin(a) * speed * 0.3);
                }
            }
            default -> {
                for (int i = 0; i < count; i++) {
                    double t = i / (double) count;
                    double a = t * Math.PI * 6;
                    double y = e.getY() + t * e.getHeight();
                    mc.world.addParticle(eff, cx + Math.cos(a) * rad, y, cz + Math.sin(a) * rad,
                            Math.cos(a) * speed * 0.3, 0.01, Math.sin(a) * speed * 0.3);
                }
            }
        }
    }

    private static boolean isNight(MinecraftClient mc) {
        long t = mc.world.getTimeOfDay() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    private void spawnParticles(MinecraftClient mc) {
        Random r = mc.world.random;

        // ---- частицы в воздухе ----
        if (Features.PART.on()
                && !(Features.P_NIGHT.value && !isNight(mc))
                && !(Features.P_OUTDOOR.value && !mc.world.isSkyVisible(mc.player.getBlockPos()))) {
            ParticleEffect eff = Features.PARTICLES[Features.P_STYLE.index];
            int n = Features.P_DENSITY.geti();
            double rad = Features.P_RADIUS.get(), hgt = Features.P_HEIGHT.get(), sp = Features.P_SPEED.get();
            int move = Features.P_MOVE.index;
            for (int i = 0; i < n; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(r.nextDouble()) * rad;
                double x = mc.player.getX() + Math.cos(ang) * d;
                double z = mc.player.getZ() + Math.sin(ang) * d;
                double y = mc.player.getY() - 1 + r.nextDouble() * hgt;
                double vx = (r.nextDouble() - 0.5) * 0.01 * sp;
                double vz = (r.nextDouble() - 0.5) * 0.01 * sp;
                double vy;
                switch (move) {
                    case 0 -> vy = 0.008 * sp;
                    case 1 -> vy = -0.06 * sp;
                    case 2 -> vy = 0.05 * sp;
                    default -> {
                        vx = -Math.sin(ang) * 0.05 * sp;
                        vz = Math.cos(ang) * 0.05 * sp;
                        vy = 0.01 * sp;
                    }
                }
                mc.world.addParticle(eff, x, y, z, vx, vy, vz);
            }
        }

        // ---- Target ESP (также срабатывает при наведении) ----
        Entity espTarget = target;
        if (espTarget == null && hoveredTarget != null && passes(hoveredTarget, Features.TG_FILTER.index)) {
            espTarget = hoveredTarget;
        }
        if (espTarget == null) return;
        if (!espTarget.isAlive()) {
            if (espTarget == target && Features.HIT.on() && Features.H_DEATH.value) {
                double m = Features.H_DEATH_MULT.get();
                burst(mc, target, Features.PARTICLES[Features.H_STYLE.index],
                        (int) (Features.H_COUNT.get() * m), Features.H_RADIUS.get() * (0.8 + m * 0.4),
                        Features.H_SPEED.get() * (0.8 + m * 0.3), 0);
            }
            if (espTarget == target) target = null;
            return;
        }
        if (espTarget == target && (--targetTicks <= 0 || target.isRemoved())) {
            target = null;
            return;
        }
        if (!Features.TARGET.on()) return;

        ParticleEffect eff = Features.PARTICLES[Features.TG_STYLE.index];
        int n = Features.TG_COUNT.geti();
        double rad = Features.TG_RADIUS.get() * Math.max(0.6, espTarget.getWidth());
        double rot = age * Features.TG_SPEED.get();
        double lift = Features.TG_LIFT.get();
        double h = espTarget.getHeight();
        double y = switch (Features.TG_MODE.index) {
            case 0 -> espTarget.getY() + 0.05;
            case 1 -> espTarget.getY() + h / 2.0;
            case 2 -> espTarget.getY() + h + 0.1;
            default -> espTarget.getY() + (0.5 + 0.5 * Math.sin(age * 0.15)) * h;
        };
        int rings = Features.TG_SECOND.value ? 2 : 1;
        for (int ring = 0; ring < rings; ring++) {
            double rr = ring == 0 ? rad : rad * 0.7;
            double dir = ring == 0 ? 1 : -1;
            for (int i = 0; i < n; i++) {
                double a = dir * rot + i * (Math.PI * 2 / n);
                mc.world.addParticle(eff, espTarget.getX() + Math.cos(a) * rr, y, espTarget.getZ() + Math.sin(a) * rr, 0, lift, 0);
            }
        }
    }

    private void renderWorld(WorldRenderContext ctx) {
        if (!Features.JUMPCIRCLE.on() || JUMP_CIRCLES.isEmpty()) return;
        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;
        Vec3d cam = ctx.camera().getPos();

        int col = swatchColor(Features.JC_COLOR.index);
        float r = ((col >> 16) & 255) / 255f;
        float g = ((col >> 8) & 255) / 255f;
        float b = (col & 255) / 255f;
        float maxRad = Features.JC_RADIUS.getf();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_COLOR);

        Tessellator tess = Tessellator.getInstance();
        long now = System.currentTimeMillis();
        Iterator<JumpCircle> it = JUMP_CIRCLES.iterator();

        while (it.hasNext()) {
            JumpCircle c = it.next();
            float p = (now - c.time) / 900.0f;
            if (p >= 1.0f) {
                it.remove();
                continue;
            }
            float rad = 0.15f + p * maxRad;
            float alpha = (1.0f - p) * 0.85f;

            matrices.push();
            matrices.translate(c.pos.x - cam.x, c.pos.y - cam.y + 0.02, c.pos.z - cam.z);
            Matrix4f mat = matrices.peek().getPositionMatrix();

            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
            int segs = 40;
            for (int i = 0; i <= segs; i++) {
                double a = (2.0 * Math.PI * i) / segs;
                float cs = (float) Math.cos(a);
                float sn = (float) Math.sin(a);
                buf.vertex(mat, cs * Math.max(0.01f, rad - 0.1f), 0f, sn * Math.max(0.01f, rad - 0.1f)).color(r, g, b, 0f);
                buf.vertex(mat, cs * rad, 0f, sn * rad).color(r, g, b, alpha);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
            matrices.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ================= для меню =================

    public static String menuKeyName() {
        if (Config.menuKey < 0) return "не назначена";
        return InputUtil.fromKeyCode(Config.menuKey, 0).getLocalizedText().getString();
    }

    // ================= HUD =================

    private void panel(DrawContext ctx, int x, int y, int w, int h, int style, int r, float opacity) {
        if (style == 0) {
            Gfx.glass(ctx, x, y, x + w, y + h, r, opacity, 1f, false);
        } else if (style == 1) {
            Gfx.roundRect(ctx, x, y, x + w, y + h, r, 0xE0000000 | Theme.cur().bg());
            if (Features.T_BORDER.value) Gfx.stroke(ctx, x, y, x + w, y + h, r, Theme.cur().line());
        }
    }

    private void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.getDebugHud().shouldShowDebugHud()) return;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        updateHoveredTarget(mc);

        if (Features.CROSSHAIR.on()) renderCrosshair(ctx, mc, sw, sh);
        if (Features.TARGET_HUD.on()) renderTargetHud(ctx, mc, sw, sh);
        if (Features.WM.on()) renderWatermark(ctx, mc, sw);
        if (Features.COORDS.on()) renderCoords(ctx, mc, sw, sh);
        if (Features.EFFECTS.on()) renderEffects(ctx, mc, sw);
        if (Features.ARMOR.on()) renderArmor(ctx, mc, sw, sh);
        if (Features.KEYSTROKES.on()) renderKeystrokes(ctx, mc);
    }

    private void updateHoveredTarget(MinecraftClient mc) {
        double maxDist = Features.TH_RANGE.get();
        Entity cam = mc.getCameraEntity();
        if (cam == null) {
            hoveredTarget = null;
            return;
        }
        Vec3d start = cam.getCameraPosVec(1.0f);
        Vec3d dir = cam.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(maxDist));
        Box box = cam.getBoundingBox().stretch(dir.multiply(maxDist)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(
                cam, start, end, box,
                e -> !e.isSpectator() && e.canHit() && e instanceof LivingEntity && e != mc.player && passes(e, Features.TH_FILTER.index),
                maxDist * maxDist
        );

        LivingEntity found = hit != null ? (LivingEntity) hit.getEntity() : null;
        if (found != hoveredTarget) {
            hoveredTarget = found;
            if (found != null) animatedTargetHp = found.getHealth();
        }
    }

    private void renderCrosshair(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        int cx = sw / 2;
        int cy = sh / 2;
        int size = Features.CH_SIZE.geti();
        float gap = Features.CH_GAP.getf();
        int thick = Features.CH_THICK.geti();
        int col = 0xFF000000 | swatchColor(Features.CH_COLOR.index);

        if (Features.CH_DYN.value) {
            float cd = 1.0f - mc.player.getAttackCooldownProgress(0.0f);
            gap += cd * 5.0f;
            if (mc.player.isSprinting()) gap += 1.5f;
        }

        int g = Math.round(gap);
        int half = thick / 2;
        int style = Features.CH_STYLE.index;

        if (style == 0 || style == 3) {
            drawOutlinedRect(ctx, cx - half, cy - g - size, thick, size, col);
            drawOutlinedRect(ctx, cx - half, cy + g, thick, size, col);
            drawOutlinedRect(ctx, cx - g - size, cy - half, size, thick, col);
            drawOutlinedRect(ctx, cx + g, cy - half, size, thick, col);
        }
        if (style == 2 || style == 3) {
            drawOutlinedRect(ctx, cx - half, cy - half, thick, thick, col);
        }
        if (style == 1) {
            int rad = g + size / 2 + 2;
            int segs = 36;
            for (int i = 0; i < segs; i++) {
                double a = (2.0 * Math.PI * i) / segs;
                int px = cx + (int) Math.round(Math.cos(a) * rad);
                int py = cy + (int) Math.round(Math.sin(a) * rad);
                ctx.fill(px, py, px + thick, py + thick, col);
            }
        }
    }

    private static void drawOutlinedRect(DrawContext ctx, int x, int y, int w, int h, int col) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
        ctx.fill(x, y, x + w, y + h, col);
    }

    private void renderTargetHud(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        if (hoveredTarget == null || !hoveredTarget.isAlive()) return;
        Theme.P th = Theme.cur();
        int acc = 0xFF000000 | Theme.accent();

        float hp = hoveredTarget.getHealth();
        float maxHp = Math.max(1f, hoveredTarget.getMaxHealth());
        float abs = hoveredTarget.getAbsorptionAmount();
        animatedTargetHp += (hp - animatedTargetHp) * 0.25f;

        List<ItemStack> gear = new ArrayList<>();
        if (Features.TH_ARMOR.value) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = hoveredTarget.getEquippedStack(slot);
                if (!st.isEmpty()) gear.add(st);
            }
        }
        if (Features.TH_HAND.value) {
            ItemStack main = hoveredTarget.getMainHandStack();
            ItemStack off = hoveredTarget.getOffHandStack();
            if (!main.isEmpty()) gear.add(main);
            if (!off.isEmpty()) gear.add(off);
        }

        String name = hoveredTarget.getName().getString();
        if (name.length() > 20) name = name.substring(0, 20) + "…";
        String hpText = abs > 0
                ? String.format(Locale.ROOT, "%.1f + %.1f HP", hp, abs)
                : String.format(Locale.ROOT, "%.1f / %.0f HP", hp, maxHp);

        int gearW = gear.size() * 18;
        int w = Math.max(136, Math.max(mc.textRenderer.getWidth(name) + 18, gearW + 16));
        int h = gear.isEmpty() ? 36 : 56;
        int x = sw / 2 + 16;
        int y = sh / 2 - h / 2;

        int style = Features.TH_STYLE.index;
        panel(ctx, x, y, w, h, style, 8, Features.TH_OPACITY.getf() / 100f);
        if (style != 2) {
            Gfx.roundRect(ctx, x, y + 6, x + 2, y + h - 6, 1, acc);
        }

        int textCol = style == 2 ? 0xFFFFFFFF : th.text();
        ctx.drawText(mc.textRenderer, name, x + 8, y + 6, textCol, style == 2);

        float frac = Math.max(0f, Math.min(1f, animatedTargetHp / maxHp));
        int hpCol = 0xFF000000 | Theme.mix(0xFF5555, 0x55FF55, frac);
        ctx.drawText(mc.textRenderer, hpText, x + 8, y + 17, hpCol, style == 2);

        int bx = x + 8, by = y + 28, bw = w - 16, bh = 4;
        Gfx.roundRect(ctx, bx, by, bx + bw, by + bh, 2, th.light() ? 0x30000000 : 0x30FFFFFF);
        int fillW = Math.max(2, Math.round(bw * frac));
        Gfx.roundRect(ctx, bx, by, bx + fillW, by + bh, 2, hpCol);

        if (!gear.isEmpty()) {
            int ix = x + 7;
            int iy = y + 35;
            for (ItemStack st : gear) {
                ctx.drawItem(st, ix, iy);
                ctx.drawStackOverlay(mc.textRenderer, st, ix, iy);
                ix += 18;
            }
        }
    }

    private void renderKeystrokes(DrawContext ctx, MinecraftClient mc) {
        Theme.P th = Theme.cur();
        int style = Features.KS_STYLE.index;
        float op = Features.KS_OPACITY.getf() / 100f;
        int x = 6, y = 26, s = 20, gap = 2;

        drawKeyBox(ctx, mc, th, style, op, "W", x + s + gap, y, s, s, mc.options.forwardKey.isPressed());
        drawKeyBox(ctx, mc, th, style, op, "A", x, y + s + gap, s, s, mc.options.leftKey.isPressed());
        drawKeyBox(ctx, mc, th, style, op, "S", x + s + gap, y + s + gap, s, s, mc.options.backKey.isPressed());
        drawKeyBox(ctx, mc, th, style, op, "D", x + (s + gap) * 2, y + s + gap, s, s, mc.options.rightKey.isPressed());

        int fullW = s * 3 + gap * 2;
        int row2Y = y + (s + gap) * 2;
        if (Features.KS_CPS.value) {
            long now = System.currentTimeMillis();
            LEFT_CLICKS.removeIf(t -> now - t > 1000);
            RIGHT_CLICKS.removeIf(t -> now - t > 1000);
            int hw = (fullW - gap) / 2;
            drawKeyBox(ctx, mc, th, style, op, "LMB " + LEFT_CLICKS.size(), x, row2Y, hw, 16, mc.options.attackKey.isPressed());
            drawKeyBox(ctx, mc, th, style, op, "RMB " + RIGHT_CLICKS.size(), x + hw + gap, row2Y, hw, 16, mc.options.useKey.isPressed());
            row2Y += 16 + gap;
        }
        drawKeyBox(ctx, mc, th, style, op, "SPACE", x, row2Y, fullW, 14, mc.options.jumpKey.isPressed());
    }

    private void drawKeyBox(DrawContext ctx, MinecraftClient mc, Theme.P th, int style, float op,
                            String label, int x, int y, int w, int h, boolean pressed) {
        if (pressed) {
            Gfx.roundRect(ctx, x, y, x + w, y + h, 5, 0xCC000000 | Theme.accent());
        } else {
            panel(ctx, x, y, w, h, style, 5, op);
        }
        int col = pressed ? 0xFFFFFFFF : (style == 2 ? 0xFFFFFFFF : th.text());
        int tw = mc.textRenderer.getWidth(label);
        ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, col, style == 2 && !pressed);
    }

    private void renderWatermark(DrawContext ctx, MinecraftClient mc, int sw) {
        Theme.P th = Theme.cur();
        List<String> parts = new ArrayList<>();
        if (Features.W_NAME.value) parts.add("Visuals");
        if (Features.W_USER.value) parts.add(mc.player.getName().getString());
        if (Features.W_FPS.value) parts.add(mc.getCurrentFps() + " fps");
        if (Features.W_PING.value && !mc.isInSingleplayer() && mc.getNetworkHandler() != null) {
            PlayerListEntry pe = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (pe != null) parts.add(pe.getLatency() + " ms");
        }
        if (Features.W_TIME.value) parts.add(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        if (parts.isEmpty()) return;

        String text = String.join("  |  ", parts);
        int style = Features.W_STYLE.index;
        int w = mc.textRenderer.getWidth(text) + 16;
        int h = 16;
        int x = switch (Features.W_POS.index) {
            case 1 -> 4;
            case 2 -> sw - w - 4;
            default -> (sw - w) / 2;
        };
        int y = 4;
        int r = Features.W_RADIUS.geti();
        panel(ctx, x, y, w, h, style, r, Features.W_OPACITY.getf() / 100f);
        if (Features.W_LINE.value && style != 2) {
            ctx.fill(x + r + 2, y + h - 1, x + w - r - 2, y + h, 0xFF000000 | Theme.accent());
        }
        int col = style == 2 ? 0xFFFFFFFF : th.text();
        ctx.drawText(mc.textRenderer, text, x + 8, y + 4, col, style == 2);
    }

    private void renderCoords(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        Theme.P th = Theme.cur();
        List<String> lines = new ArrayList<>();
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        boolean dec = Features.C_DEC.value;
        lines.add(dec ? String.format(java.util.Locale.ROOT, "XYZ: %.1f / %.1f / %.1f", x, y, z)
                : String.format("XYZ: %d / %d / %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        if (Features.C_NETHER.value) {
            var key = mc.world.getRegistryKey();
            if (key == World.OVERWORLD) {
                lines.add(String.format("Незер: %d / %d", (int) Math.floor(x / 8), (int) Math.floor(z / 8)));
            } else if (key == World.NETHER) {
                lines.add(String.format("Обычный мир: %d / %d", (int) Math.floor(x * 8), (int) Math.floor(z * 8)));
            }
        }
        if (Features.C_DIR.value) {
            Direction f = mc.player.getHorizontalFacing();
            lines.add("Направление: " + f.asString().toUpperCase());
        }
        if (Features.C_BIOME.value) {
            String biome = mc.world.getBiome(mc.player.getBlockPos()).getKey()
                    .map(k -> {
                        Identifier id = k.getValue();
                        return Text.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
                    }).orElse("?");
            lines.add("Биом: " + biome);
        }
        if (Features.C_SPEED.value) lines.add(String.format(java.util.Locale.ROOT, "Скорость: %.1f б/с", speed));

        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, mc.textRenderer.getWidth(s));
        int w = maxW + 14;
        int h = lines.size() * 11 + 6;
        int bx = Features.C_POS.index == 2 ? sw - w - 4 : 4;
        int by = Features.C_POS.index == 1 ? 24 : sh - h - 4;
        int style = Features.C_STYLE.index;
        panel(ctx, bx, by, w, h, style, Features.C_RADIUS.geti(), Features.C_OPACITY.getf() / 100f);
        int col = style == 2 ? 0xFFFFFFFF : th.text();
        int ly = by + 4;
        for (String s : lines) {
            ctx.drawText(mc.textRenderer, s, bx + 7, ly, col, style == 2);
            ly += 11;
        }
    }

    private void renderEffects(DrawContext ctx, MinecraftClient mc, int sw) {
        Theme.P th = Theme.cur();
        var list = mc.player.getStatusEffects();
        if (list.isEmpty()) return;

        List<String> names = new ArrayList<>();
        List<String> times = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Float> alphas = new ArrayList<>();
        int maxName = 0, maxTime = 0;
        for (StatusEffectInstance e : list) {
            String name = e.getEffectType().value().getName().getString();
            if (Features.E_LEVEL.value && e.getAmplifier() > 0) {
                int lvl = e.getAmplifier() + 1;
                name += " " + (lvl < ROMAN.length ? ROMAN[lvl] : String.valueOf(lvl));
            }
            String time = "";
            if (Features.E_TIMER.value) {
                if (e.isInfinite()) time = "∞";
                else {
                    int s = e.getDuration() / 20;
                    time = String.format("%02d:%02d", s / 60, s % 60);
                }
            }
            float a = 1f;
            if (Features.E_BLINK.value && !e.isInfinite() && e.getDuration() < 200) {
                a = (float) (0.55 + 0.45 * Math.sin(age * 0.6));
            }
            names.add(name);
            times.add(time);
            colors.add(e.getEffectType().value().getColor());
            alphas.add(a);
            maxName = Math.max(maxName, mc.textRenderer.getWidth(name));
            maxTime = Math.max(maxTime, mc.textRenderer.getWidth(time));
        }
        int dot = Features.E_DOT.value ? 10 : 0;
        int w = 8 + dot + maxName + (maxTime > 0 ? 10 + maxTime : 0) + 8;
        int h = names.size() * 11 + 6;
        int bx = Features.E_POS.index == 1 ? 4 : sw - w - 4;
        int by = 24;
        int style = Features.E_STYLE.index;
        panel(ctx, bx, by, w, h, style, Features.E_RADIUS.geti(), Features.E_OPACITY.getf() / 100f);
        int ly = by + 4;
        for (int i = 0; i < names.size(); i++) {
            float a = alphas.get(i);
            int tcol = Gfx.mul(style == 2 ? 0xFFFFFFFF : th.text(), a);
            int x = bx + 8;
            if (Features.E_DOT.value) {
                Gfx.roundRect(ctx, x, ly + 1, x + 6, ly + 7, 3, Gfx.mul(0xFF000000 | colors.get(i), a));
                x += 10;
            }
            ctx.drawText(mc.textRenderer, names.get(i), x, ly, tcol, style == 2);
            if (!times.get(i).isEmpty()) {
                ctx.drawText(mc.textRenderer, times.get(i), bx + w - 8 - mc.textRenderer.getWidth(times.get(i)), ly, tcol, style == 2);
            }
            ly += 11;
        }
    }

    private void renderArmor(DrawContext ctx, MinecraftClient mc, int sw, int sh) {
        Theme.P th = Theme.cur();
        List<ItemStack> items = new ArrayList<>();
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = mc.player.getEquippedStack(s);
            if (!st.isEmpty()) items.add(st);
        }
        if (Features.A_HAND.value) {
            ItemStack st = mc.player.getMainHandStack();
            if (!st.isEmpty()) items.add(st);
        }
        if (items.isEmpty()) return;

        int mode = Features.A_DUR.index;
        List<String> texts = new ArrayList<>();
        List<Float> fracs = new ArrayList<>();
        int maxW = 0;
        for (ItemStack st : items) {
            String t = "";
            float frac = 1f;
            if (st.isDamageable()) {
                int max = st.getMaxDamage();
                int left = max - st.getDamage();
                frac = left / (float) max;
                if (mode == 1) t = Math.round(frac * 100) + "%";
                else if (mode == 2) t = String.valueOf(left);
            } else if (st.getCount() > 1 && mode != 3) {
                t = "x" + st.getCount();
            }
            texts.add(t);
            fracs.add(frac);
            maxW = Math.max(maxW, mc.textRenderer.getWidth(t));
        }
        int rowH = 18;
        int w = 6 + 16 + (maxW > 0 ? 4 + maxW : 0) + 6;
        int h = items.size() * rowH + 4;
        int bx = Features.A_POS.index == 1 ? sw - w - 6 : 6;
        int by = (sh - h) / 2;
        if (Features.A_BG.value) {
            Gfx.glass(ctx, bx, by, bx + w, by + h, 6, Features.A_OPACITY.getf() / 100f, 1f, false);
        }
        int y = by + 2;
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = items.get(i);
            ctx.drawItem(st, bx + 6, y);
            if (mode == 0) ctx.drawStackOverlay(mc.textRenderer, st, bx + 6, y);
            if (!texts.get(i).isEmpty()) {
                int col = st.isDamageable() ? (0xFF000000 | Theme.mix(0xFF5555, 0x55FF55, fracs.get(i))) : th.text();
                ctx.drawText(mc.textRenderer, texts.get(i), bx + 6 + 16 + 4, y + 5, col, !Features.A_BG.value);
            }
            y += rowH;
        }
    }

    private record JumpCircle(Vec3d pos, long time) {}
}
