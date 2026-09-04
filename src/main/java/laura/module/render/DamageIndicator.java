package laura.module.render;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.DrawEvent;
import laura.event.TickEvent;
import laura.render.ColorUtil;
import laura.render.Draw2DProcessor;
import laura.render.Fonts;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.ui.shader.GradientUtil;
import laura.util.ProjectUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@ModuleRegister(name = "Damage Indicator", description = "Летающие цифры урона и хитмаркер на прицеле", category = Category.Render)
public class DamageIndicator extends Module {
    private static final long LIFETIME = 1000L;
    private static final long HITMARKER_LIFETIME = 260L;
    private static final float CRITICAL_DAMAGE = 6.0f;

    private final MultiModeSetting b = new MultiModeSetting("Источники",
            new BooleanSetting("Урон по игрокам", true),
            new BooleanSetting("Урон по вам", true),
            new BooleanSetting("Урон по мобам", false),
            new BooleanSetting("Хитмаркер", true));
    private final SliderSetting c = new SliderSetting("Размер текста", 9.0f, 6.0f, 14.0f, 0.5f);
    private final BooleanSetting d = new BooleanSetting("Градиент", true);
    private final BooleanSetting e = new BooleanSetting("Подсветка критов", true);
    private final Map<Integer, Float> healthMap = new HashMap<>();
    private final List<a> popups = new ArrayList<>();
    private long lastHit;

    public DamageIndicator() {
        a(this.b, this.c, this.d, this.e);
    }

    public MultiModeSetting q() {
        return this.b;
    }

    public SliderSetting r() {
        return this.c;
    }

    public BooleanSetting s() {
        return this.d;
    }

    public BooleanSetting t() {
        return this.e;
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        a(mc.player, mc.player.getHealth(), now);
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living) {
                a(living, living.getHealth(), now);
            }
        }
        Iterator<a> iterator = this.popups.iterator();
        while (iterator.hasNext()) {
            a popup = iterator.next();
            if (now - popup.b > LIFETIME) {
                iterator.remove();
            }
        }
        this.healthMap.keySet().removeIf(id -> !d(id.intValue()));
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (!event.b() || mc.world == null || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        MatrixStack matrices = event.i().getMatrices();
        Draw2DProcessor draw = event.getDraw2DProcessor();
        float size = this.c.c().floatValue();
        for (a popup : this.popups) {
            float progress = (float) (now - popup.b) / (float) LIFETIME;
            if (progress < 0.0f || progress > 1.0f) {
                continue;
            }
            Vector2f screen = ProjectUtil.project(popup.c.x, (popup.c.y + 1.45d) + ((double) (0.65f * progress)), popup.c.z);
            if (!ProjectUtil.isOnScreen(screen)) {
                continue;
            }
            float alpha = MathHelper.clamp(1.0f - (progress * progress), 0.0f, 1.0f);
            int color = this.d.c().booleanValue()
                    ? GradientUtil.a(3, (int) ((progress * 220.0f) + 10.0f), popup.e, ColorUtil.b(popup.e, 0.45f), now)
                    : popup.e;
            Fonts.d.a(matrices, popup.d, screen.x(), screen.y(), size, ColorUtil.applyAlphaToColor(color, alpha), 0.4f);
        }
        if (this.b.a("Хитмаркер").c().booleanValue() && this.lastHit != 0L) {
            long passed = now - this.lastHit;
            if (passed > HITMARKER_LIFETIME) {
                this.lastHit = 0L;
            } else {
                a(draw, matrices, (float) passed / (float) HITMARKER_LIFETIME);
            }
        }
    }

    private void a(LivingEntity entity, float health, long now) {
        Integer id = Integer.valueOf(entity.getId());
        Float last = this.healthMap.get(id);
        this.healthMap.put(id, Float.valueOf(health));
        if (last == null || last.floatValue() - health <= 0.0f) {
            return;
        }
        float damage = last.floatValue() - health;
        boolean onYou = entity == mc.player;
        boolean onPlayer = entity instanceof PlayerEntity;
        boolean show;
        int color;
        if (onYou) {
            show = this.b.a("Урон по вам").c().booleanValue();
            color = ColorUtil.convertToARGB(255, 70, 70, 255);
        } else {
            show = onPlayer ? this.b.a("Урон по игрокам").c().booleanValue() : this.b.a("Урон по мобам").c().booleanValue();
            color = onPlayer ? ColorUtil.convertToARGB(255, 190, 60, 255) : ColorUtil.convertToARGB(150, 220, 255, 255);
        }
        if (!show) {
            return;
        }
        boolean critical = this.e.c().booleanValue() && damage >= CRITICAL_DAMAGE;
        if (critical) {
            color = ColorUtil.convertToARGB(255, 245, 66, 255);
        }
        this.popups.add(new a(entity.getPos(), (critical ? "! " : "") + String.format("%.1f", Float.valueOf(damage)), now, color));
        if (!onYou && onPlayer) {
            this.lastHit = now;
        }
    }

    private boolean d(int id) {
        if (mc.player.getId() == id) {
            return true;
        }
        for (Entity entity : mc.world.getEntities()) {
            if (entity.getId() == id) {
                return true;
            }
        }
        return false;
    }

    private void a(Draw2DProcessor draw, MatrixStack matrices, float progress) {
        float centerX = mc.getWindow().getScaledWidth() / 2.0f;
        float centerY = mc.getWindow().getScaledHeight() / 2.0f;
        float length = 3.0f + (2.5f * progress);
        float offset = 3.0f + (5.0f * progress);
        int color = ColorUtil.applyAlphaToColor(-1, MathHelper.clamp(1.0f - progress, 0.0f, 1.0f));
        for (int i = 0; i < 4; i++) {
            matrices.push();
            matrices.translate(centerX, centerY, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((45.0f + (90.0f * (float) i)) + (12.0f * progress)));
            draw.a(matrices, offset, -0.5f, length, 1.0f, 0.0f, color);
            matrices.pop();
        }
    }

    @Override
    public void c() {
        super.c();
        this.popups.clear();
        this.healthMap.clear();
        this.lastHit = 0L;
    }

    private static final class a {
        private final Vec3d c;
        private final String d;
        private final long b;
        private final int e;

        private a(Vec3d position, String text, long time, int color) {
            this.c = position;
            this.d = text;
            this.b = time;
            this.e = color;
        }
    }
}
