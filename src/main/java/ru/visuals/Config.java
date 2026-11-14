package ru.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static boolean fullbright = false; // не сохраняется
    public static boolean hud = true;
    public static boolean effects = true;
    public static boolean particles = true;
    public static boolean targetEsp = true;
    public static boolean hitBubble = true;
    public static int style = 0;     // стиль частиц
    public static int density = 3;   // 1..10
    public static int menuKey = -1;  // -1 = не назначена

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("visuals.json");

    private static class Data {
        boolean hud = true, effects = true, particles = true, targetEsp = true, hitBubble = true;
        int style = 0, density = 3, menuKey = -1;
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            Data d = GSON.fromJson(Files.readString(FILE), Data.class);
            if (d == null) return;
            hud = d.hud; effects = d.effects; particles = d.particles;
            targetEsp = d.targetEsp; hitBubble = d.hitBubble;
            style = d.style; density = Math.max(1, Math.min(10, d.density));
            menuKey = d.menuKey;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Data d = new Data();
            d.hud = hud; d.effects = effects; d.particles = particles;
            d.targetEsp = targetEsp; d.hitBubble = hitBubble;
            d.style = style; d.density = density; d.menuKey = menuKey;
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
