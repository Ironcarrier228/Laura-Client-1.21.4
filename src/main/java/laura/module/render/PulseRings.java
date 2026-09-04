package laura.module.render;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.DrawEvent;
import laura.event.TickEvent;
import laura.render.ColorUtil;
import laura.setting.BooleanSetting;
import laura.setting.ColorSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.util.ProjectUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@ModuleRegister(name = "Pulse Rings", description = "Расходящиеся круги при приземлении и пульсация под целью", category = Category.Render)
public class PulseRings extends Module {
    private static final long LANDING_LIFETIME = 900L;

    private final MultiModeSetting b = new MultiModeSetting("Эффекты",
            new BooleanSetting("Круг при приземлении", true),
            new BooleanSetting("Пульс под целью", true),
            new BooleanSetting("Только игроки", false));
    private final SliderSetting c = new SliderSetting("Радиус", 1.1f, 0.4f, 3.0f, 0.1f);
    private final SliderSetting d = new SliderSetting("Скорость пульса", 1.4f, 0.4f, 3.0f, 0.1f);
    private final ColorSetting e = new ColorSetting("Цвет", Integer.valueOf(ColorUtil.convertToARGB(90, 200, 255, 255)));
    private final Map<Integer, Float> fallState = new HashMap<>();
    private final List<a> landings = new ArrayList<>();

    public PulseRings() {
        a(this.b, this.c, this.d, this.e);
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

    public ColorSetting t() {
        return this.e;
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || !d(entity)) {
                continue;
            }
            Integer id = Integer.valueOf(entity.getId());
            Float previous = this.fallState.get(id);
            this.fallState.put(id, Float.valueOf(entity.fallDistance));
            if (previous == null) {
                continue;
            }
            if (previous.floatValue() > 0.3f && entity.fallDistance <= 0.0f && entity.isOnGround()) {
                this.landings.add(new a(entity.getPos(), now));
            }
        }
        Iterator<a> iterator = this.landings.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().b > LANDING_LIFETIME) {
                iterator.remove();
            }
        }
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (!event.b() || mc.world == null || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float radius = this.c.c().floatValue();
        int color = this.e.c().intValue();
        if (this.b.a("Круг при приземлении").c().booleanValue()) {
            Iterator<a> iterator = this.landings.iterator();
            while (iterator.hasNext()) {
                a landing = iterator.next();
                float progress = (float) (now - landing.b) / (float) LANDING_LIFETIME;
                if (progress > 1.0f) {
                    iterator.remove();
                    continue;
                }
                a(event, landing.c, radius * (0.35f + progress), ColorUtil.applyAlphaToColor(color, 0.85f * (1.0f - progress)), 1.5f);
            }
        }
        if (this.b.a("Пульс под целью").c().booleanValue() && mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() != null && hit.getEntity() != mc.player && hit.getEntity().isAlive()) {
            float pulse = (now % 1000L) / 1000.0f;
            pulse = (pulse * this.d.c().floatValue()) % 1.0f;
            a(event, hit.getEntity().getPos(), radius * (0.45f + (0.55f * pulse)), ColorUtil.applyAlphaToColor(color, 0.75f * (1.0f - pulse)), 1.25f);
        }
    }

    private boolean d(Entity entity) {
        return !this.b.a("Только игроки").c().booleanValue() || entity instanceof PlayerEntity;
    }

    private void a(DrawEvent event, Vec3d center, float radius, int color, float thickness) {
        if (radius <= 0.0f) {
            return;
        }
        Vector2f first = null;
        Vector2f previous = null;
        for (int i = 0; i <= 32; i++) {
            double angle = ((double) i / 32.0d) * 6.283185307179586d;
            Vector2f point = ProjectUtil.project(center.x + (Math.sin(angle) * ((double) radius)), center.y + 0.06d,
                    center.z + (Math.cos(angle) * ((double) radius)));
            if (!ProjectUtil.isOnScreen(point)) {
                previous = null;
                continue;
            }
            if (i == 0) {
                first = point;
            }
            if (previous != null) {
                a(event, previous, point, color, thickness);
            }
            previous = point;
        }
        if (previous != null && first != null) {
            a(event, previous, first, color, thickness);
        }
    }

    private void a(DrawEvent event, Vector2f from, Vector2f to, int color, float thickness) {
        float left = Math.min(from.x(), to.x());
        float top = Math.min(from.y(), to.y());
        float width = Math.abs(to.x() - from.x());
        float height = Math.abs(to.y() - from.y());
        if (width <= thickness) {
            width = thickness;
        }
        if (height <= thickness) {
            height = thickness;
        }
        event.getDraw2DProcessor().a(event.i().getMatrices(), left, top, width, height, 0.0f,
                ColorUtil.applyAlphaToColor(color, MathHelper.clamp(((color >> 24) & 255) / 255.0f, 0.0f, 1.0f)));
    }

    @Override
    public void c() {
        super.c();
        this.landings.clear();
        this.fallState.clear();
    }

    private static final class a {
        private final Vec3d c;
        private final long b;

        private a(Vec3d position, long time) {
            this.c = position;
            this.b = time;
        }
    }
}
