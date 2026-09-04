package laura.module.render;

import laura.ambience.Ambience;
import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.AmbienceEvent;
import laura.render.ColorUtil;
import laura.setting.BooleanSetting;
import laura.setting.ColorSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.util.MathUtil;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.List;

@ModuleRegister(name = "Volumetric Fog", description = "Объёмный туман, реагирующий на биом, время суток и погоду", category = Category.Render)
public class VolumetricFog extends Module {
    private static final List<RegistryKey<Biome>> SWAMP_BIOMES = List.of(BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP);
    private static final List<RegistryKey<Biome>> CAVE_BIOMES = List.of(BiomeKeys.DEEP_DARK, BiomeKeys.DRIPSTONE_CAVES,
            BiomeKeys.LUSH_CAVES);
    private static final List<RegistryKey<Biome>> SNOWY_BIOMES = List.of(BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_TAIGA,
            BiomeKeys.SNOWY_BEACH, BiomeKeys.GROVE, BiomeKeys.SNOWY_SLOPES, BiomeKeys.ICE_SPIKES,
            BiomeKeys.JAGGED_PEAKS, BiomeKeys.FROZEN_PEAKS, BiomeKeys.FROZEN_RIVER, BiomeKeys.FROZEN_OCEAN,
            BiomeKeys.DEEP_FROZEN_OCEAN);
    private static final List<RegistryKey<Biome>> FOREST_BIOMES = List.of(BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST,
            BiomeKeys.DARK_FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST,
            BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.TAIGA, BiomeKeys.JUNGLE,
            BiomeKeys.SPARSE_JUNGLE, BiomeKeys.BAMBOO_JUNGLE, BiomeKeys.PALE_GARDEN, BiomeKeys.CHERRY_GROVE,
            BiomeKeys.MEADOW, BiomeKeys.WINDSWEPT_FOREST);
    private static final List<RegistryKey<Biome>> OPEN_BIOMES = List.of(BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS,
            BiomeKeys.DESERT, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.WINDSWEPT_SAVANNA,
            BiomeKeys.BADLANDS, BiomeKeys.ERODED_BADLANDS, BiomeKeys.WOODED_BADLANDS, BiomeKeys.BEACH,
            BiomeKeys.RIVER, BiomeKeys.OCEAN, BiomeKeys.DEEP_OCEAN, BiomeKeys.WARM_OCEAN, BiomeKeys.LUKEWARM_OCEAN,
            BiomeKeys.COLD_OCEAN, BiomeKeys.MUSHROOM_FIELDS);

    private final MultiModeSetting b = new MultiModeSetting("Реакция",
            new BooleanSetting("Биомы", true),
            new BooleanSetting("Время суток", true),
            new BooleanSetting("Погода", true),
            new BooleanSetting("Пещеры", true));
    private final SliderSetting c = new SliderSetting("Плотность", 55.0f, 0.0f, 100.0f, 1.0f);
    private final SliderSetting d = new SliderSetting("Дальность начала", 0.35f, 0.02f, 0.9f, 0.01f);
    private final SliderSetting e = new SliderSetting("Мягкость затухания", 0.85f, 0.3f, 1.0f, 0.05f);
    private final SliderSetting f = new SliderSetting("Подмес своего цвета", 0.45f, 0.0f, 1.0f, 0.05f);
    private final ColorSetting g = new ColorSetting("Цвет тумана", Integer.valueOf(ColorUtil.convertToARGB(196, 210, 226, 255)));
    private final BooleanSetting h = new BooleanSetting("Красить небо", false);

    public VolumetricFog() {
        a(this.b, this.c, this.d, this.e, this.f, this.g, this.h);
    }

    public MultiModeSetting r() {
        return this.b;
    }

    public SliderSetting s() {
        return this.c;
    }

    public SliderSetting t() {
        return this.d;
    }

    public SliderSetting u() {
        return this.e;
    }

    public SliderSetting v() {
        return this.f;
    }

    public ColorSetting w() {
        return this.g;
    }

    public BooleanSetting x() {
        return this.h;
    }

    @EventTarget(a = 3)
    public void a(AmbienceEvent.b event) {
        if (mc.world == null || mc.player == null || event.getCamera() == null || q()) {
            return;
        }
        if (event.getCamera().getSubmersionType() != CameraSubmersionType.NONE) {
            return;
        }
        a profile = a(event.getCamera().getBlockPos());
        float view = Math.max(24.0f, event.c());
        float start = view * MathUtil.b(this.d.c().floatValue() * (1.4f - (0.75f * profile.b)), 0.01f, 0.95f);
        float end = start + (((view * 1.05f) - start) * this.e.c().floatValue());
        FogShape shape = profile.b > 0.55f ? FogShape.CYLINDER : event.d().shape();
        float[] rgba = ColorUtil.a(profile.c);
        event.a(start, Math.max(end, start + 4.0f), shape, rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    @EventTarget(a = 3)
    public void a(AmbienceEvent.a event) {
        if (!this.h.c().booleanValue() || mc.world == null || mc.player == null || q()) {
            return;
        }
        float[] rgba = ColorUtil.a(a(mc.player.getBlockPos()).c);
        event.setRed(rgba[0]);
        event.setGreen(rgba[1]);
        event.setBlue(rgba[2]);
        event.setAlpha(1.0f);
        event.a(true);
    }

    private a a(BlockPos pos) {
        RegistryEntry<Biome> biome = mc.world.getBiome(pos);
        long time = mc.world.getTimeOfDay() % 24000L;
        boolean reactBiome = this.b.a("Биомы").c().booleanValue();
        boolean reactTime = this.b.a("Время суток").c().booleanValue();
        boolean cave = this.b.a("Пещеры").c().booleanValue()
                && (a(biome) || !mc.world.isSkyVisible(pos));
        float daylight = reactTime ? a(time) : 0.65f;
        float dusk = reactTime ? b(time) : 0.0f;
        float biomeDensity = reactBiome ? c(biome) : 0.0f;
        float weather = 0.0f;
        if (this.b.a("Погода").c().booleanValue() && mc.world.isRaining()) {
            weather = mc.world.isThundering() ? 0.2f : 0.12f;
        }
        float base = this.c.c().floatValue() / 100.0f;
        float density = MathUtil.b((base * (0.55f + (0.45f * (1.0f - daylight)))) + biomeDensity + weather + (cave ? 0.3f : 0.0f), 0.0f, 1.0f);
        int color = reactBiome ? d(biome) : biome.value().getFogColor();
        color = ColorUtil.lerpColor(color, ColorUtil.convertToARGB(255, 214, 148, 92), dusk * 0.55f);
        color = ColorUtil.lerpColor(color, ColorUtil.convertToARGB(255, 42, 52, 74), (1.0f - daylight) * (cave ? 0.35f : 0.5f));
        if (cave) {
            color = ColorUtil.lerpColor(color, ColorUtil.convertToARGB(255, 46, 52, 62), 0.5f);
        }
        color = ColorUtil.lerpColor(color, this.g.c().intValue(), this.f.c().floatValue());
        return new a(density, color);
    }

    private boolean a(RegistryEntry<Biome> biome) {
        return a(biome, CAVE_BIOMES);
    }

    private boolean a(RegistryEntry<Biome> biome, List<RegistryKey<Biome>> keys) {
        for (RegistryKey<Biome> key : keys) {
            if (biome.matchesKey(key)) {
                return true;
            }
        }
        return false;
    }

    private float a(long time) {
        if (time >= 1000L && time <= 11500L) {
            return 1.0f;
        }
        if (time >= 13500L && time <= 23000L) {
            return 0.0f;
        }
        if (time < 1000L) {
            return MathUtil.b((float) time / 1000.0f, 0.0f, 1.0f);
        }
        if (time < 13500L) {
            return MathUtil.b((13500.0f - (float) time) / 2000.0f, 0.0f, 1.0f);
        }
        return MathUtil.b(((float) time - 23000.0f) / 1000.0f, 0.0f, 1.0f);
    }

    private float b(long time) {
        float dawn = 1.0f - (MathUtil.b(Math.abs((float) time - 1000.0f) / 1600.0f, 0.0f, 1.0f));
        float dusk = 1.0f - (MathUtil.b(Math.abs((float) time - 12600.0f) / 1600.0f, 0.0f, 1.0f));
        return MathUtil.b(Math.max(dawn, dusk), 0.0f, 1.0f);
    }

    private float c(RegistryEntry<Biome> biome) {
        if (a(biome, SWAMP_BIOMES)) {
            return 0.35f;
        }
        if (a(biome, CAVE_BIOMES)) {
            return 0.3f;
        }
        if (a(biome, SNOWY_BIOMES)) {
            return 0.12f;
        }
        if (a(biome, FOREST_BIOMES)) {
            return 0.08f;
        }
        if (a(biome, OPEN_BIOMES)) {
            return -0.18f;
        }
        return 0.0f;
    }

    private int d(RegistryEntry<Biome> biome) {
        if (a(biome, SWAMP_BIOMES)) {
            return ColorUtil.convertToARGB(92, 116, 88, 255);
        }
        if (a(biome, CAVE_BIOMES)) {
            return ColorUtil.convertToARGB(58, 64, 74, 255);
        }
        if (a(biome, SNOWY_BIOMES)) {
            return ColorUtil.convertToARGB(212, 226, 240, 255);
        }
        if (a(biome, FOREST_BIOMES)) {
            return ColorUtil.convertToARGB(168, 190, 168, 255);
        }
        if (a(biome, OPEN_BIOMES)) {
            return ColorUtil.convertToARGB(220, 208, 178, 255);
        }
        return biome.value().getFogColor();
    }

    private boolean q() {
        Ambience ambience = Laura.getInstance().getModuleProcessor().t().aF();
        return ambience != null && ambience.m() && ambience.q().c().booleanValue();
    }

    private static final class a {
        private final float b;
        private final int c;

        private a(float density, int color) {
            this.b = density;
            this.c = color;
        }
    }
}
