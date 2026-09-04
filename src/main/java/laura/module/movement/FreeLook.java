package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.LookEvent;
import laura.event.RotationEvent;
import laura.event.TickEvent;
import laura.render.EasingList;
import laura.setting.BindSetting;
import laura.setting.BooleanSetting;
import laura.setting.MultiModeSetting;
import laura.setting.SliderSetting;
import laura.util.MathUtil;
import net.minecraft.util.math.MathHelper;

@ModuleRegister(name = "Free Look", description = "Свободный обзор камеры на удержании клавиши без поворота персонажа", category = Category.Movement)
public class FreeLook extends Module {
    private static final float RAMP_TIME = 260.0f;

    private final BindSetting b;
    private final SliderSetting c = new SliderSetting("Скорость обзора", 1.0f, 0.1f, 3.0f, 0.05f);
    private final SliderSetting d = new SliderSetting("Сглаживание", 14.0f, 2.0f, 30.0f, 0.5f);
    private final SliderSetting e = new SliderSetting("Время возврата", 450.0f, 100.0f, 1500.0f, 50.0f);
    private final SliderSetting f = new SliderSetting("Easing на старте", 0.35f, 0.0f, 1.0f, 0.05f);
    private final SliderSetting g = new SliderSetting("Лимит наклона", 90.0f, 10.0f, 90.0f, 5.0f);
    private final MultiModeSetting h = new MultiModeSetting("Поведение",
            new BooleanSetting("Возврат при отпускании", true),
            new BooleanSetting("Плавный старт", true),
            new BooleanSetting("Инвертировать Y", false));
    private float targetYaw;
    private float targetPitch;
    private float renderYaw;
    private float renderPitch;
    private float returnYaw;
    private float returnPitch;
    private long pressTime;
    private long releaseTime;
    private boolean active;
    private boolean returning;

    public FreeLook() {
        this.b = new BindSetting("Клавиша обзора", -1, 0).a(() -> {
            d(true);
        }).b(() -> {
            d(false);
        });
        a(this.b, this.c, this.d, this.e, this.f, this.g, this.h);
    }

    public BindSetting r() {
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

    public boolean y() {
        return this.active;
    }

    @Override
    public void b() {
        super.b();
        q();
    }

    @Override
    public void c() {
        super.c();
        q();
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.currentScreen != null) {
            d(false);
        }
    }

    @EventTarget
    public void a(LookEvent event) {
        if (!this.active || event.a()) {
            return;
        }
        float speed = this.c.c().floatValue();
        float limit = this.g.c().floatValue();
        float pitchSign = this.h.a("Инвертировать Y").c().booleanValue() ? -1.0f : 1.0f;
        this.targetYaw += (float) event.getYaw() * speed;
        this.targetPitch = MathHelper.clamp(this.targetPitch + ((float) event.c() * speed * pitchSign), -limit, limit);
        event.a(true);
    }

    @EventTarget(a = 3)
    public void a(RotationEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        a(System.currentTimeMillis());
        if (!this.active && !this.returning && this.renderYaw == 0.0f && this.renderPitch == 0.0f) {
            return;
        }
        event.setYaw(event.getYaw() + this.renderYaw);
        event.setPitch(MathHelper.clamp(event.getPitch() + this.renderPitch, -90.0f, 90.0f));
    }

    private void a(long now) {
        if (this.returning) {
            float duration = Math.max(60.0f, this.e.c().floatValue());
            float progress = MathUtil.b((float) (now - this.releaseTime) / duration, 0.0f, 1.0f);
            float eased = EasingList.h.ease(progress);
            this.renderYaw = this.returnYaw * (1.0f - eased);
            this.renderPitch = this.returnPitch * (1.0f - eased);
            if (progress >= 1.0f) {
                this.returning = false;
                q();
            }
            return;
        }
        float speed = this.d.c().floatValue();
        if (this.active && this.h.a("Плавный старт").c().booleanValue()) {
            float ramp = EasingList.f.ease(MathUtil.b((float) (now - this.pressTime) / RAMP_TIME, 0.0f, 1.0f));
            speed *= 1.0f - (this.f.c().floatValue() * (1.0f - ramp));
        }
        speed = Math.max(0.5f, speed);
        this.renderYaw = MathUtil.c(this.renderYaw, this.targetYaw, speed);
        this.renderPitch = MathUtil.c(this.renderPitch, this.targetPitch, speed);
        if (!this.active && Math.abs(this.renderYaw) < 0.01f && Math.abs(this.renderPitch) < 0.01f) {
            q();
        }
    }

    private void d(boolean state) {
        if (this.active == state) {
            return;
        }
        this.active = state;
        long now = System.currentTimeMillis();
        if (state) {
            this.returning = false;
            this.pressTime = now;
            return;
        }
        this.releaseTime = now;
        if (this.h.a("Возврат при отпускании").c().booleanValue()) {
            this.returning = true;
            this.returnYaw = this.renderYaw;
            this.returnPitch = this.renderPitch;
            this.targetYaw = 0.0f;
            this.targetPitch = 0.0f;
            return;
        }
        this.targetYaw = this.renderYaw;
        this.targetPitch = this.renderPitch;
    }

    private void q() {
        this.active = false;
        this.returning = false;
        this.targetYaw = 0.0f;
        this.targetPitch = 0.0f;
        this.renderYaw = 0.0f;
        this.renderPitch = 0.0f;
        this.returnYaw = 0.0f;
        this.returnPitch = 0.0f;
    }
}
