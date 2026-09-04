package laura.module.render;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.DrawEvent;
import laura.event.TickEvent;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.util.MathUtil;
import laura.util.ProjectUtil;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@ModuleRegister(name = "Ambient Particles", description = "Атмосферные фоновые частицы, зависящие от биома и освещения", category = Category.Render)
public class AmbientParticles extends Module {
    private static final int POLLEN = 0;
    private static final int FIREFLY = 1;
    private static final int SNOWFLAKE = 2;
    private static final int CAVE_DUST = 3;
    private static final long SPAWN_INTERVAL = 90L;
    private static final List<RegistryKey<Biome>> SNOWY_BIOMES = List.of(BiomeKeys.SNOWY_PLAINS, BiomeKeys.SNOWY_TAIGA,
            BiomeKeys.SNOWY_BEACH, BiomeKeys.GROVE, BiomeKeys.SNOWY_SLOPES, BiomeKeys.ICE_SPIKES,
            BiomeKeys.JAGGED_PEAKS, BiomeKeys.FROZEN_PEAKS, BiomeKeys.FROZEN_RIVER, BiomeKeys.FROZEN_OCEAN,
            BiomeKeys.DEEP_FROZEN_OCEAN);
    private static final List<RegistryKey<Biome>> FOREST_BIOMES = List.of(BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST,
            BiomeKeys.DARK_FOREST, BiomeKeys.FLOWER_FOREST, BiomeKeys.OLD_GROWTH_BIRCH_FOREST,
            BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.TAIGA, BiomeKeys.JUNGLE,
            BiomeKeys.SPARSE_JUNGLE, BiomeKeys.BAMBOO_JUNGLE, BiomeKeys.PALE_GARDEN, BiomeKeys.CHERRY_GROVE,
            BiomeKeys.MEADOW, BiomeKeys.WINDSWEPT_FOREST);
    private static final List<RegistryKey<Biome>> SWAMP_BIOMES = List.of(BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP);
    private static final List<RegistryKey<Biome>> OPEN_BIOMES = List.of(BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS,
            BiomeKeys.DESERT, BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.WINDSWEPT_SAVANNA,
            BiomeKeys.BADLANDS, BiomeKeys.ERODED_BADLANDS, BiomeKeys.WOODED_BADLANDS, BiomeKeys.BEACH,
            BiomeKeys.MUSHROOM_FIELDS);
    private static final List<RegistryKey<Biome>> CAVE_BIOMES = List.of(BiomeKeys.DEEP_DARK, BiomeKeys.DRIPSTONE_CAVES,
            BiomeKeys.LUSH_CAVES);

    private final MultiModeSetting b = new MultiModeSetting("Частицы",
            new BooleanSetting("Пыльца днём", true),
            new BooleanSetting("Светлячки ночью", true),
            new BooleanSetting("Снежинки", true),
            new BooleanSetting("Пыль в пещерах", false));
    private final SliderSetting c = new SliderSetting("Количество", 26.0f, 4.0f, 80.0f, 1.0f);
    private final SliderSetting d = new SliderSetting("Радиус", 10.0f, 3.0f, 24.0f, 0.5f);
    private final SliderSetting e = new SliderSetting("Размер", 1.6f, 0.5f, 4.0f, 0.1f);
    private final SliderSetting f = new SliderSetting("Яркость", 0.55f, 0.05f, 1.0f, 0.05f);
    private final SliderSetting g = new SliderSetting("Скорость", 1.0f, 0.1f, 3.0f, 0.1f);
    private final MultiModeSetting h = new MultiModeSetting("Фильтры",
            new BooleanSetting("Учитывать освещение", true),
            new BooleanSetting("Не рисовать сквозь стены", true));
    private final List<a> particles = new ArrayList<>();
    private long lastSpawn;

    public AmbientParticles() {
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

    public SliderSetting w() {
        return this.g;
    }

    public MultiModeSetting x() {
        return this.h;
    }

    @Override
    public void c() {
        super.c();
        this.particles.clear();
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.world == null || mc.player == null) {
            this.particles.clear();
            return;
        }
        long now = System.currentTimeMillis();
        float radius = this.d.c().floatValue();
        Vec3d eye = mc.player.getEyePos();
        Iterator<a> iterator = this.particles.iterator();
        while (iterator.hasNext()) {
            a particle = iterator.next();
            if (now - particle.h > particle.i || particle.c.squaredDistanceTo(eye) > (radius * radius * 2.25d)) {
                iterator.remove();
            }
        }
        b(now);
        if (now - this.lastSpawn >= SPAWN_INTERVAL) {
            this.lastSpawn = now;
            c(now);
        }
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (!event.b() || mc.world == null || mc.player == null || this.particles.isEmpty()) {
            return;
        }
        Draw2DProcessor draw = event.getDraw2DProcessor();
        MatrixStack matrices = event.i().getMatrices();
        Vec3d camera = mc.getEntityRenderDispatcher().camera.getPos();
        boolean occlusion = this.h.a("Не рисовать сквозь стены").c().booleanValue();
        float radius = this.d.c().floatValue();
        float size = this.e.c().floatValue();
        float brightness = this.f.c().floatValue();
        long now = System.currentTimeMillis();
        for (a particle : this.particles) {
            float age = (float) (now - particle.h) / (float) particle.i;
            if (age < 0.0f || age > 1.0f) {
                continue;
            }
            double x = particle.c.x + (Math.sin((((double) now) / 900.0d) + ((double) particle.j)) * 0.07d);
            double y = particle.c.y + (Math.cos((((double) now) / 1150.0d) + (((double) particle.j) * 1.7d)) * 0.06d);
            double z = particle.c.z + (Math.sin((((double) now) / 1400.0d) + (((double) particle.j) * 0.6d)) * 0.07d);
            Vec3d position = new Vec3d(x, y, z);
            double distance = camera.distanceTo(position);
            if (distance < 0.4d || distance > ((double) radius)) {
                continue;
            }
            if (occlusion) {
                if (now - particle.l > 220L) {
                    particle.k = mc.world.raycast(new RaycastContext(camera, position, RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
                    particle.l = now;
                }
                if (!particle.k) {
                    continue;
                }
            }
            Vector2f screen = ProjectUtil.project(x, y, z);
            if (!ProjectUtil.isOnScreen(screen)) {
                continue;
            }
            float fade = MathUtil.b(age * 5.0f, 0.0f, 1.0f) * MathUtil.b((1.0f - age) * 3.5f, 0.0f, 1.0f);
            float distanceFade = MathUtil.b(1.0f - ((((float) distance) - 1.5f) / Math.max(1.0f, radius)), 0.2f, 1.0f);
            float alpha = brightness * fade * distanceFade;
            if (particle.e == FIREFLY) {
                alpha *= 0.2f + (0.8f * MathUtil.b((float) (0.5d + (0.5d * Math.sin((((double) now) / 430.0d) + (((double) particle.j) * 2.3d)))), 0.0f, 1.0f));
            }
            if (alpha <= 0.02f) {
                continue;
            }
            float pixels = MathUtil.b(size * particle.g * (7.0f / (float) distance), 0.7f, 16.0f);
            a(draw, matrices, screen, pixels, ColorUtil.applyAlphaToColor(particle.f, MathUtil.b(alpha, 0.0f, 1.0f)));
        }
    }

    private void b(long now) {
        float speed = this.g.c().floatValue();
        for (a particle : this.particles) {
            particle.c = particle.c.add(particle.d.x * 0.05d * ((double) speed), particle.d.y * 0.05d * ((double) speed),
                    particle.d.z * 0.05d * ((double) speed));
        }
    }

    private void c(long now) {
        int max = (int) this.c.c().floatValue();
        if (this.particles.size() >= max) {
            return;
        }
        Vec3d origin = mc.player.getEyePos();
        float radius = this.d.c().floatValue();
        long time = mc.world.getTimeOfDay() % 24000L;
        boolean night = time > 12800L && time < 23200L;
        boolean occlusion = this.h.a("Не рисовать сквозь стены").c().booleanValue();
        for (int attempt = 0; attempt < 10 && this.particles.size() < max; attempt++) {
            Vec3d candidate = origin.add(MathUtil.a(-1.0f, 1.0f) * ((double) radius),
                    MathUtil.a(-0.45f, 0.55f) * (radius * 0.7d), MathUtil.a(-1.0f, 1.0f) * ((double) radius));
            BlockPos pos = BlockPos.ofFloored(candidate);
            if (!mc.world.getBlockState(pos).isAir()) {
                continue;
            }
            int light = mc.world.getLightLevel(pos);
            boolean sky = mc.world.isSkyVisible(pos);
            int type = a(mc.world.getBiome(pos), pos, night, light, sky);
            if (type < 0) {
                continue;
            }
            if (occlusion && mc.world.raycast(new RaycastContext(origin, candidate, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mc.player)).getType() != HitResult.Type.MISS) {
                continue;
            }
            this.particles.add(new a(candidate, e(type), type, b(type), c(type), now, d(type),
                    (long) MathUtil.a(0.0f, 1000.0f)));
        }
    }

    private int a(RegistryEntry<Biome> biome, BlockPos pos, boolean night, int light, boolean sky) {
        boolean reactLight = this.h.a("Учитывать освещение").c().booleanValue();
        boolean snowy = a(biome, SNOWY_BIOMES);
        boolean forest = a(biome, FOREST_BIOMES) || a(biome, SWAMP_BIOMES);
        boolean open = a(biome, OPEN_BIOMES);
        if (snowy && this.b.a("Снежинки").c().booleanValue() && (!reactLight || sky)) {
            return SNOWFLAKE;
        }
        if (night && forest && this.b.a("Светлячки ночью").c().booleanValue() && (!reactLight || light <= 9)) {
            return FIREFLY;
        }
        if (!night && this.b.a("Пыльца днём").c().booleanValue() && (forest || open)
                && (!reactLight || sky || b(pos) || light >= 9)) {
            return POLLEN;
        }
        if (this.b.a("Пыль в пещерах").c().booleanValue() && (a(biome, CAVE_BIOMES) || (!sky && light <= 7))) {
            return CAVE_DUST;
        }
        return -1;
    }

    private boolean a(RegistryEntry<Biome> biome, List<RegistryKey<Biome>> keys) {
        for (RegistryKey<Biome> key : keys) {
            if (biome.matchesKey(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean b(BlockPos pos) {
        for (int offset = 1; offset <= 5; offset++) {
            if (mc.world.getBlockState(pos.up(offset)).getBlock() instanceof LeavesBlock) {
                return true;
            }
        }
        return false;
    }

    private Vec3d e(int type) {
        switch (type) {
            case FIREFLY:
                return new Vec3d(MathUtil.a(-0.35f, 0.35f), MathUtil.a(-0.16f, 0.2f), MathUtil.a(-0.35f, 0.35f));
            case SNOWFLAKE:
                return new Vec3d(MathUtil.a(-0.16f, 0.16f), MathUtil.a(-0.42f, -0.2f), MathUtil.a(-0.16f, 0.16f));
            case CAVE_DUST:
                return new Vec3d(MathUtil.a(-0.09f, 0.09f), MathUtil.a(-0.05f, 0.03f), MathUtil.a(-0.09f, 0.09f));
            default:
                return new Vec3d(MathUtil.a(-0.22f, 0.22f), MathUtil.a(-0.05f, 0.1f), MathUtil.a(-0.22f, 0.22f));
        }
    }

    private int b(int type) {
        switch (type) {
            case FIREFLY:
                return ColorUtil.convertToARGB(196, 255, 128, 255);
            case SNOWFLAKE:
                return ColorUtil.convertToARGB(232, 242, 255, 255);
            case CAVE_DUST:
                return ColorUtil.convertToARGB(184, 174, 158, 255);
            default:
                return ColorUtil.convertToARGB(255, 244, 202, 255);
        }
    }

    private float c(int type) {
        switch (type) {
            case FIREFLY:
                return 1.15f;
            case SNOWFLAKE:
                return 1.3f;
            case CAVE_DUST:
                return 0.85f;
            default:
                return 1.0f;
        }
    }

    private long d(int type) {
        switch (type) {
            case FIREFLY:
                return 7600L;
            case SNOWFLAKE:
                return 5200L;
            case CAVE_DUST:
                return 9000L;
            default:
                return 8200L;
        }
    }

    private void a(Draw2DProcessor draw, MatrixStack matrices, Vector2f screen, float pixels, int color) {
        float halo = pixels * 2.8f;
        float haloAlpha = (((color >> 24) & 255) / 255.0f) * 0.16f;
        draw.a(matrices, screen.x() - (halo / 2.0f), screen.y() - (halo / 2.0f), halo, halo, halo / 2.0f,
                ColorUtil.applyAlphaToColor(color, haloAlpha));
        draw.a(matrices, screen.x() - (pixels / 2.0f), screen.y() - (pixels / 2.0f), pixels, pixels, pixels / 2.0f, color);
    }

    private static final class a {
        private final Vec3d d;
        private final int e;
        private final int f;
        private final float g;
        private final long h;
        private final long i;
        private final long j;
        private Vec3d c;
        private boolean k;
        private long l;

        private a(Vec3d position, Vec3d velocity, int type, int color, float size, long born, long lifetime, long seed) {
            this.c = position;
            this.d = velocity;
            this.e = type;
            this.f = color;
            this.g = size;
            this.h = born;
            this.i = lifetime;
            this.j = seed;
            this.k = true;
            this.l = born;
        }
    }
}
