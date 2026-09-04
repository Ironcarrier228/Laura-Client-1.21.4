package laura.module.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import laura.config.ThemeInfo;
import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.DrawEvent;
import laura.event.TickEvent;
import laura.render.ColorUtil;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ModuleRegister(name = "Trails", description = "Рисует затухающий шлейф за движущимися сущностями", category = Category.Render)
public class Trails extends Module {
    private final MultiModeSetting b = new MultiModeSetting("Источники",
            new BooleanSetting("Вы", true),
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", false));
    private final SliderSetting c = new SliderSetting("Длина шлейфа", 24.0f, 6.0f, 60.0f, 1.0f);
    private final SliderSetting d = new SliderSetting("Толщина", 2.0f, 1.0f, 5.0f, 0.5f);
    private final SliderSetting e = new SliderSetting("Дальность прорисовки", 48.0f, 8.0f, 128.0f, 4.0f);
    private final BooleanSetting f = new BooleanSetting("Цвет клиента", true);
    private final Map<UUID, List<Vec3d>> trails = new ConcurrentHashMap<>();

    public Trails() {
        a(this.b, this.c, this.d, this.e, this.f);
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

    public BooleanSetting u() {
        return this.f;
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        int maxLength = Math.max(2, (int) this.c.c().floatValue());
        float range = this.e.c().floatValue();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || !d(entity)) {
                continue;
            }
            if (entity != mc.player && mc.player.distanceTo(entity) > range) {
                continue;
            }
            Vec3d position = entity.getPos().add(0.0d, entity.getHeight() * 0.25d, 0.0d);
            List<Vec3d> trail = this.trails.computeIfAbsent(entity.getUuid(), id -> new ObjectArrayList<>());
            if (!trail.isEmpty() && ((Vec3d) trail.get(trail.size() - 1)).squaredDistanceTo(position) < 0.0016d) {
                continue;
            }
            trail.add(position);
            while (trail.size() > maxLength) {
                trail.remove(0);
            }
        }
        this.trails.keySet().removeIf(id -> !e(id));
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (!event.c() || mc.world == null || mc.player == null) {
            return;
        }
        float width = this.d.c().floatValue();
        int color = this.f.c().booleanValue()
                ? ColorUtil.combineColorWithAlpha(Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor(), 255)
                : ColorUtil.convertToARGB(90, 200, 255, 255);
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || !d(entity)) {
                continue;
            }
            List<Vec3d> trail = this.trails.get(entity.getUuid());
            if (trail == null || trail.size() < 2) {
                continue;
            }
            Vec3d previous = null;
            for (int i = 0; i < trail.size(); i++) {
                Vec3d current = trail.get(i);
                if (previous != null) {
                    float fade = (float) i / (float) trail.size();
                    int segmentColor = ColorUtil.applyAlphaToColor(color, 0.06f + (0.7f * fade * fade));
                    event.getDraw3DProcessor().a(event.h(), previous, current, null, segmentColor, width * (0.35f + (0.65f * fade)));
                }
                previous = current;
            }
        }
    }

    private boolean d(Entity entity) {
        if (entity == mc.player) {
            return this.b.a("Вы").c().booleanValue();
        }
        return entity instanceof PlayerEntity ? this.b.a("Игроки").c().booleanValue() : this.b.a("Мобы").c().booleanValue();
    }

    private boolean e(UUID id) {
        for (Entity entity : mc.world.getEntities()) {
            if (entity.getUuid().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void c() {
        super.c();
        this.trails.clear();
    }
}
