package laura.module.render;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.DrawEvent;
import laura.render.ColorUtil;
import laura.setting.BooleanSetting;
import laura.setting.ColorSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.util.MathUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

@ModuleRegister(name = "Electric Arcs", description = "Рисует электрические дуги между вами и целью", category = Category.Render)
public class ElectricArcs extends Module {
    private final MultiModeSetting b = new MultiModeSetting("Цели",
            new BooleanSetting("Цель под прицелом", true),
            new BooleanSetting("Игроки рядом", false),
            new BooleanSetting("Друзья", false));
    private final SliderSetting c = new SliderSetting("Дальность", 20.0f, 5.0f, 64.0f, 1.0f);
    private final SliderSetting d = new SliderSetting("Количество сегментов", 10.0f, 4.0f, 24.0f, 1.0f);
    private final SliderSetting e = new SliderSetting("Разброс", 0.35f, 0.05f, 1.0f, 0.05f);
    private final SliderSetting f = new SliderSetting("Толщина", 2.0f, 1.0f, 5.0f, 0.5f);
    private final ColorSetting g = new ColorSetting("Цвет дуги", Integer.valueOf(ColorUtil.convertToARGB(90, 200, 255, 255)));

    public ElectricArcs() {
        a(this.b, this.c, this.d, this.e, this.f, this.g);
    }

    public MultiModeSetting q() {
        return this.b;
    }

    public SliderSetting r() {
        return this.c;
    }

    public SliderSetting s() {
        return this.d;
    }

    public SliderSetting t() {
        return this.e;
    }

    public SliderSetting u() {
        return this.f;
    }

    public ColorSetting v() {
        return this.g;
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (!event.c() || mc.world == null || mc.player == null) {
            return;
        }
        if (this.b.a("Цель под прицелом").c().booleanValue() && mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity focused && focused != mc.player && focused.isAlive()) {
            a(event, focused);
        }
        if (this.b.a("Игроки рядом").c().booleanValue()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity target && target != mc.player && target.isAlive()
                        && mc.player.distanceTo(target) <= this.c.c().floatValue()) {
                    if (this.b.a("Друзья").c().booleanValue()
                            || !Laura.getInstance().getModuleProcessor().e().d(target.getName().getString())) {
                        a(event, target);
                    }
                }
            }
        }
    }

    private void a(DrawEvent event, LivingEntity target) {
        Vec3d camera = mc.getEntityRenderDispatcher().camera.getPos();
        Vec3d start = MathUtil.a(mc.player, event.g()).add(0.0d, mc.player.getStandingEyeHeight(), 0.0d).subtract(camera);
        Vec3d end = MathUtil.a(target, event.g()).add(0.0d, target.getHeight() * 0.55d, 0.0d).subtract(camera);
        int segments = Math.max(2, (int) this.d.c().floatValue());
        float spread = this.e.c().floatValue();
        float width = this.f.c().floatValue();
        int color = this.g.c().intValue();
        long seed = System.currentTimeMillis() / 45L;
        for (int pass = 0; pass < 2; pass++) {
            float passScale = pass == 0 ? 1.0f : 0.45f;
            int passColor = pass == 0 ? color : ColorUtil.combineColorWithAlpha(color, 110);
            float passWidth = pass == 0 ? width : width * 2.6f;
            Vec3d previous = start;
            for (int i = 1; i <= segments; i++) {
                float t = (float) i / (float) segments;
                Vec3d next = t >= 1.0f ? end : b(start, end, t, spread, seed + (long) i + (7L * (long) pass));
                Vec3d control = a(previous, next, spread * 0.6f, (seed * 3L) + (long) i);
                event.getDraw3DProcessor().a(event.h(), previous, next, control, passColor, passWidth * passScale);
                previous = next;
            }
        }
    }

    private Vec3d b(Vec3d start, Vec3d end, float t, float spread, long seed) {
        double x = start.x + ((end.x - start.x) * ((double) t));
        double y = start.y + ((end.y - start.y) * ((double) t));
        double z = start.z + ((end.z - start.z) * ((double) t));
        double offset = ((double) spread) * 0.55d;
        return new Vec3d(x + (a(seed) * offset), y + (a(seed + 1L) * offset), z + (a(seed + 2L) * offset));
    }

    private Vec3d a(Vec3d start, Vec3d end, float spread, long seed) {
        double middleX = (start.x + end.x) / 2.0d;
        double middleY = (start.y + end.y) / 2.0d;
        double middleZ = (start.z + end.z) / 2.0d;
        double offset = ((double) spread) * 0.35d;
        return new Vec3d(middleX + (a(seed + 11L) * offset), middleY + (a(seed + 12L) * offset), middleZ + (a(seed + 13L) * offset));
    }

    private double a(long seed) {
        long value = (seed * 6364136223846793005L) + 1442695040888963407L;
        value = (value ^ (value >>> 33)) * 6364136223846793005L;
        return (((double) ((int) (value >>> 32))) / 2.147483648E9d);
    }
}
