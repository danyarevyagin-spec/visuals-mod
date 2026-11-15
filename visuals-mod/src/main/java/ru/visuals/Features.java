package ru.visuals;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.ArrayList;
import java.util.List;

public final class Features {
    private Features() { }

    public static final List<Feature> ALL = new ArrayList<>();
    public static final String[] CATS = {"Основное", "Визуалы", "Фигура", "Интерфейс", "Тема"};

    public static final String[] PARTICLE_NAMES = {
            "Звёзды", "Магия", "Искры", "Руны", "Снежинки", "Души", "Огонь",
            "Лепестки", "Блёстки", "Пепел", "Свечение", "Сердца", "Ноты"
    };
    public static final ParticleEffect[] PARTICLES = {
            ParticleTypes.END_ROD, ParticleTypes.WITCH, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.ENCHANT,
            ParticleTypes.SNOWFLAKE, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.FLAME,
            ParticleTypes.CHERRY_LEAVES, ParticleTypes.HAPPY_VILLAGER, ParticleTypes.WHITE_ASH,
            ParticleTypes.GLOW, ParticleTypes.HEART, ParticleTypes.NOTE
    };
    private static final String[] BG_STYLES = {"Стекло", "Тёмный", "Без фона"};
    private static final String[] TARGET_FILTER = {"Все", "Игроки", "Мобы"};

    private static Feature feat(String id, String name, String cat, boolean toggle, boolean def) {
        Feature f = new Feature(id, name, cat, toggle, def);
        ALL.add(f);
        return f;
    }

    // ===================== ОСНОВНОЕ =====================
    public static final Feature MENU = feat("menu", "Меню", "Основное", false, false);
    public static final Setting.Info M_KEY = MENU.info(() -> "Кнопка меню: " + VisualsClient.menuKeyName());
    public static final Setting.Info M_HINT = MENU.info(() -> "Назначить: .bind add в чате");
    public static final Setting.Bool M_GREET = MENU.bool("greet", "Приветствие в чате", true);
    public static final Setting.Action M_CLEAR = MENU.action("Убрать кнопку меню", () -> {
        Config.menuKey = -1;
        Config.save();
    });

    public static final Feature CONF = feat("config", "Конфиг", "Основное", false, false);
    public static final Setting.Action C_SAVE = CONF.action("Сохранить настройки", Config::save);
    public static final Setting.Action C_RESET = CONF.action("Сбросить всё", () -> {
        resetAll();
        Config.save();
    });

    public static final Feature ABOUT = feat("about", "О моде", "Основное", false, false);
    public static final Setting.Info A_VER = ABOUT.info(() -> "Visuals 1.3.0");
    public static final Setting.Info A_MC = ABOUT.info(() -> "Minecraft 1.21.4 · Fabric");

    // ===================== ВИЗУАЛЫ =====================
    public static final Feature FULLBRIGHT = feat("fullbright", "Fullbright", "Визуалы", true, false);
    public static final Setting.Num FB_LEVEL = FULLBRIGHT.num("level", "Яркость", 1, 16, 1, 16, "");

    public static final Feature ZOOM = feat("zoom", "Zoom", "Визуалы", true, true);
    public static final Setting.Num Z_FOV = ZOOM.num("fov", "Угол обзора", 5, 60, 1, 30, "°");
    public static final Setting.Num Z_SMOOTH = ZOOM.num("smooth", "Плавность", 0, 95, 5, 60, "%");
    public static final Setting.Info Z_HINT = ZOOM.info(() -> "Удерживать C (меняется в управлении)");

    public static final Feature CROSSHAIR = feat("crosshair", "Свой прицел", "Визуалы", true, true);
    public static final Setting.Mode CH_STYLE = CROSSHAIR.mode("style", "Стиль", 0, "Крест", "Круг", "Точка", "Крест + Точка");
    public static final Setting.Num CH_SIZE = CROSSHAIR.num("size", "Размер", 2, 18, 1, 6, " px");
    public static final Setting.Num CH_GAP = CROSSHAIR.num("gap", "Зазор", 0, 12, 1, 3, " px");
    public static final Setting.Num CH_THICK = CROSSHAIR.num("thick", "Толщина", 1, 4, 1, 1, " px");
    public static final Setting.Bool CH_DYN = CROSSHAIR.bool("dyn", "Динамический", true);
    public static final Setting.Color CH_COLOR = CROSSHAIR.color("color", "Цвет", 0);

    public static final Feature TARGET = feat("target", "Target ESP", "Визуалы", true, true);
    public static final Setting.Mode TG_STYLE = TARGET.mode("style", "Частицы", 0, PARTICLE_NAMES);
    public static final Setting.Mode TG_MODE = TARGET.mode("mode", "Позиция", 3, "Ноги", "Тело", "Голова", "Сканер");
    public static final Setting.Num TG_RADIUS = TARGET.num("radius", "Радиус", 0.3, 2.5, 0.1, 0.9, "");
    public static final Setting.Num TG_COUNT = TARGET.num("count", "Количество", 4, 48, 2, 14, "");
    public static final Setting.Num TG_SPEED = TARGET.num("speed", "Скорость вращения", 0.02, 0.6, 0.02, 0.18, "");
    public static final Setting.Num TG_LIFT = TARGET.num("lift", "Подъём частиц", 0, 0.1, 0.005, 0.015, "");
    public static final Setting.Bool TG_SECOND = TARGET.bool("second", "Второе кольцо", true);
    public static final Setting.Num TG_TIME = TARGET.num("time", "Время показа", 1, 20, 1, 5, " с");
    public static final Setting.Mode TG_FILTER = TARGET.mode("filter", "Цели", 0, TARGET_FILTER);

    public static final Feature TARGET_HUD = feat("targethud", "Таргет Инфо", "Визуалы", true, true);
    public static final Setting.Num TH_RANGE = TARGET_HUD.num("range", "Дистанция наведения", 4, 64, 1, 24, " м");
    public static final Setting.Mode TH_STYLE = TARGET_HUD.mode("style", "Стиль карточки", 0, BG_STYLES);
    public static final Setting.Bool TH_ARMOR = TARGET_HUD.bool("armor", "Показывать броню", true);
    public static final Setting.Bool TH_HAND = TARGET_HUD.bool("hand", "Предметы в руках", true);
    public static final Setting.Mode TH_FILTER = TARGET_HUD.mode("filter", "Цели", 0, TARGET_FILTER);
    public static final Setting.Num TH_OPACITY = TARGET_HUD.num("opacity", "Прозрачность", 20, 100, 5, 75, "%");

    public static final Feature HIT = feat("hit", "Hit Bubble", "Визуалы", true, true);
    public static final Setting.Mode H_STYLE = HIT.mode("style", "Частицы", 1, PARTICLE_NAMES);
    public static final Setting.Mode H_SHAPE = HIT.mode("shape", "Форма", 0, "Сфера", "Кольцо", "Фонтан", "Спираль");
    public static final Setting.Num H_COUNT = HIT.num("count", "Количество", 10, 150, 5, 50, "");
    public static final Setting.Num H_RADIUS = HIT.num("radius", "Радиус", 0.2, 1.5, 0.1, 0.4, "");
    public static final Setting.Num H_SPEED = HIT.num("speed", "Скорость", 0.02, 0.5, 0.02, 0.12, "");
    public static final Setting.Bool H_DEATH = HIT.bool("death", "Взрыв при смерти цели", true);
    public static final Setting.Num H_DEATH_MULT = HIT.num("deathmult", "Сила взрыва", 1, 5, 1, 2, "x");
    public static final Setting.Mode H_FILTER = HIT.mode("filter", "Цели", 0, TARGET_FILTER);

    public static final Feature JUMPCIRCLE = feat("jumpcircle", "Круги при прыжке", "Визуалы", true, true);
    public static final Setting.Num JC_RADIUS = JUMPCIRCLE.num("radius", "Радиус круга", 0.5, 3.0, 0.1, 1.4, " м");
    public static final Setting.Color JC_COLOR = JUMPCIRCLE.color("color", "Цвет круга", 0);

    public static final Feature PART = feat("particles", "Частицы в мире", "Визуалы", true, true);
    public static final Setting.Mode P_STYLE = PART.mode("style", "Стиль", 0, PARTICLE_NAMES);
    public static final Setting.Mode P_MOVE = PART.mode("move", "Движение", 0, "Парение", "Падение", "Подъём", "Вихрь");
    public static final Setting.Num P_DENSITY = PART.num("density", "Плотность", 1, 20, 1, 3, "");
    public static final Setting.Num P_RADIUS = PART.num("radius", "Радиус", 4, 32, 2, 12, " м");
    public static final Setting.Num P_HEIGHT = PART.num("height", "Высота", 2, 20, 1, 8, " м");
    public static final Setting.Num P_SPEED = PART.num("speed", "Скорость", 0.2, 3, 0.1, 1, "x");
    public static final Setting.Bool P_NIGHT = PART.bool("night", "Только ночью", false);
    public static final Setting.Bool P_OUTDOOR = PART.bool("outdoor", "Только под открытым небом", false);

    // ===================== ФИГУРА (КОСМЕТИКА) =====================
    public static final Feature FIGURE = feat("figure", "Фигура", "Фигура", true, true);
    public static final Setting.Bool FG_HAT = FIGURE.bool("hat", "Китайская шляпа", true);
    public static final Setting.Num FG_HAT_SIZE = FIGURE.num("hatsize", "Размер шляпы", 0.4, 1.0, 0.05, 0.62, "");
    public static final Setting.Bool FG_WINGS = FIGURE.bool("wings", "Крылья", true);
    public static final Setting.Num FG_WINGS_SIZE = FIGURE.num("wingssize", "Размер крыльев", 0.5, 1.8, 0.1, 1.0, "x");
    public static final Setting.Bool FG_HALO = FIGURE.bool("halo", "Нимб", false);
    public static final Setting.Bool FG_EARS = FIGURE.bool("ears", "Кошачьи ушки", false);
    public static final Setting.Bool FG_CROWN = FIGURE.bool("crown", "Корона", false);
    public static final Setting.Bool FG_ONLY_SELF = FIGURE.bool("onlyself", "Только на себе", false);

    public static final Feature FG_COLORS = feat("fg_colors", "Цвета фигуры", "Фигура", false, false);
    public static final Setting.Color FG_COLOR1 = FG_COLORS.color("color1", "Основной цвет (верх)", 0);
    public static final Setting.Color FG_COLOR2 = FG_COLORS.color("color2", "Второй цвет (край)", 2);
    public static final Setting.Color FG_WINGS_COL = FG_COLORS.color("wingscol", "Цвет крыльев", 8);
    public static final Setting.Bool FG_RAINBOW = FG_COLORS.bool("rainbow", "Переливание (RGB)", false);

    // ===================== ИНТЕРФЕЙС =====================
    public static final Feature WM = feat("watermark", "Вотермарка", "Интерфейс", true, true);
    public static final Setting.Mode W_STYLE = WM.mode("style", "Стиль", 0, BG_STYLES);
    public static final Setting.Mode W_POS = WM.mode("pos", "Позиция", 0, "Верх центр", "Верх слева", "Верх справа");
    public static final Setting.Bool W_NAME = WM.bool("name", "Название", true);
    public static final Setting.Bool W_USER = WM.bool("user", "Ник", true);
    public static final Setting.Bool W_FPS = WM.bool("fps", "FPS", true);
    public static final Setting.Bool W_PING = WM.bool("ping", "Пинг", true);
    public static final Setting.Bool W_TIME = WM.bool("time", "Время", true);
    public static final Setting.Bool W_LINE = WM.bool("line", "Акцентная линия", true);
    public static final Setting.Num W_RADIUS = WM.num("radius", "Скругление", 0, 10, 1, 6, "");
    public static final Setting.Num W_OPACITY = WM.num("opacity", "Прозрачность", 10, 100, 5, 70, "%");

    public static final Feature COORDS = feat("coords", "Координаты", "Интерфейс", true, true);
    public static final Setting.Mode C_POS = COORDS.mode("pos", "Позиция", 0, "Лево низ", "Лево верх", "Право низ");
    public static final Setting.Mode C_STYLE = COORDS.mode("style", "Стиль", 0, BG_STYLES);
    public static final Setting.Bool C_DEC = COORDS.bool("dec", "Дробные координаты", false);
    public static final Setting.Bool C_DIR = COORDS.bool("dir", "Направление", true);
    public static final Setting.Bool C_BIOME = COORDS.bool("biome", "Биом", false);
    public static final Setting.Bool C_NETHER = COORDS.bool("nether", "Координаты другого мира", false);
    public static final Setting.Bool C_SPEED = COORDS.bool("speed", "Скорость", false);
    public static final Setting.Num C_RADIUS = COORDS.num("radius", "Скругление", 0, 10, 1, 6, "");
    public static final Setting.Num C_OPACITY = COORDS.num("opacity", "Прозрачность", 10, 100, 5, 60, "%");

    public static final Feature EFFECTS = feat("effects", "Эффекты", "Интерфейс", true, true);
    public static final Setting.Mode E_POS = EFFECTS.mode("pos", "Позиция", 0, "Право верх", "Лево верх");
    public static final Setting.Mode E_STYLE = EFFECTS.mode("style", "Стиль", 0, BG_STYLES);
    public static final Setting.Bool E_TIMER = EFFECTS.bool("timer", "Таймер", true);
    public static final Setting.Bool E_LEVEL = EFFECTS.bool("level", "Уровень", true);
    public static final Setting.Bool E_DOT = EFFECTS.bool("dot", "Цветная точка", true);
    public static final Setting.Bool E_BLINK = EFFECTS.bool("blink", "Мигать в конце", true);
    public static final Setting.Num E_RADIUS = EFFECTS.num("radius", "Скругление", 0, 10, 1, 6, "");
    public static final Setting.Num E_OPACITY = EFFECTS.num("opacity", "Прозрачность", 10, 100, 5, 60, "%");

    public static final Feature ARMOR = feat("armor", "Броня", "Интерфейс", true, false);
    public static final Setting.Mode A_POS = ARMOR.mode("pos", "Позиция", 0, "Лево центр", "Право центр");
    public static final Setting.Mode A_DUR = ARMOR.mode("dur", "Прочность", 1, "Полоска", "Процент", "Число", "Нет");
    public static final Setting.Bool A_HAND = ARMOR.bool("hand", "Предмет в руке", true);
    public static final Setting.Bool A_BG = ARMOR.bool("bg", "Фон", true);
    public static final Setting.Num A_OPACITY = ARMOR.num("opacity", "Прозрачность", 10, 100, 5, 60, "%");

    public static final Feature KEYSTROKES = feat("keystrokes", "Клавиши (WASD)", "Интерфейс", true, false);
    public static final Setting.Mode KS_STYLE = KEYSTROKES.mode("style", "Стиль", 0, BG_STYLES);
    public static final Setting.Bool KS_CPS = KEYSTROKES.bool("cps", "Показывать CPS", true);
    public static final Setting.Num KS_OPACITY = KEYSTROKES.num("opacity", "Прозрачность", 10, 100, 5, 65, "%");

    // ===================== ТЕМА =====================
    public static final Feature TH_COLORS = feat("th_colors", "Цвета", "Тема", false, false);
    public static final Setting.Mode T_THEME = TH_COLORS.mode("theme", "Тема", 0, Theme.NAMES);
    public static final Setting.Color T_ACCENT = TH_COLORS.color("accent", "Акцент", 0);

    public static final Feature TH_SHAPE = feat("th_shape", "Форма", "Тема", false, false);
    public static final Setting.Num T_RADIUS = TH_SHAPE.num("radius", "Скругление", 0, 14, 1, 10, " px");
    public static final Setting.Mode T_SIZE = TH_SHAPE.mode("size", "Размер окна", 1, "Малый", "Обычный", "Большой");
    public static final Setting.Mode T_DENSITY = TH_SHAPE.mode("density", "Плотность строк", 1, "Компактно", "Обычно", "Просторно");

    public static final Feature TH_GLASS = feat("th_glass", "Жидкое стекло", "Тема", false, false);
    public static final Setting.Bool T_BLUR = TH_GLASS.bool("blur", "Размытие фона", true);
    public static final Setting.Num T_OPACITY = TH_GLASS.num("opacity", "Прозрачность окна", 20, 95, 1, 62, "%");
    public static final Setting.Bool T_GLOW = TH_GLASS.bool("glow", "Свет сверху", true);
    public static final Setting.Bool T_SHEEN = TH_GLASS.bool("sheen", "Бегущий отсвет", true);
    public static final Setting.Num T_SHEEN_STR = TH_GLASS.num("sheenstr", "Сила отсвета", 0, 100, 5, 45, "%");
    public static final Setting.Bool T_BORDER = TH_GLASS.bool("border", "Светлая обводка", true);
    public static final Setting.Num T_BORDER_STR = TH_GLASS.num("borderstr", "Яркость обводки", 10, 100, 5, 60, "%");

    public static final Feature TH_ANIM = feat("th_anim", "Анимации", "Тема", false, false);
    public static final Setting.Bool T_ANIM = TH_ANIM.bool("on", "Плавные анимации", true);
    public static final Setting.Num T_SPEED = TH_ANIM.num("speed", "Скорость", 0.5, 3, 0.1, 1, "x");
    public static final Setting.Mode T_APPEAR = TH_ANIM.mode("appear", "Появление", 0, "Масштаб", "Снизу", "Затухание");

    static {
        T_SHEEN_STR.showIf(() -> T_SHEEN.value);
        T_BORDER_STR.showIf(() -> T_BORDER.value);
        T_SPEED.showIf(() -> T_ANIM.value);
        T_APPEAR.showIf(() -> T_ANIM.value);
        H_DEATH_MULT.showIf(() -> H_DEATH.value);
        FG_HAT_SIZE.showIf(() -> FG_HAT.value);
        FG_WINGS_SIZE.showIf(() -> FG_WINGS.value);
    }

    public static void resetAll() {
        for (Feature f : ALL) {
            if (f.enabled != null) f.enabled.reset();
            for (Setting s : f.settings) s.reset();
        }
    }
}
