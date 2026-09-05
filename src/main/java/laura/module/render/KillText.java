package laura.module.render;

import laura.config.ThemeInfo;
import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Laura;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.AttackEvent;
import laura.event.DrawEvent;
import laura.event.TickEvent;
import laura.render.ColorUtil;
import laura.render.EasingList;
import laura.render.Fonts;
import laura.setting.SliderSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

@ModuleRegister(name = "Kill Text", description = "Крупная надпись YOU KILLED при убийстве отслеживаемой цели", category = Category.Render)
public class KillText extends Module {
    private static final long APPEAR_MS = 150L;
    private static final long HOLD_MS = 1000L;
    private static final long FADE_MS = 300L;
    private static final long TOTAL_MS = APPEAR_MS + HOLD_MS + FADE_MS;
    private static final long ATTACK_WINDOW_MS = 2500L;
    private static final float HUD_TEXT_SIZE = 8.0f;

    private final SliderSetting offsetX = new SliderSetting("Смещение X", 0.0f, -200.0f, 200.0f, 1.0f);
    private final SliderSetting offsetY = new SliderSetting("Смещение Y", 0.0f, -150.0f, 150.0f, 1.0f);
    private final SliderSetting scale = new SliderSetting("Масштаб", 1.75f, 1.5f, 2.0f, 0.05f);

    private final Map<Integer, Float> healthMap = new HashMap<>();
    private int lastAttackedId = -1;
    private String lastAttackedName = "";
    private long lastAttackTime;

    private String activeName;
    private long activeStart;

    public KillText() {
        a(this.offsetX, this.offsetY, this.scale);
    }

    public SliderSetting q() {
        return this.offsetX;
    }

    public SliderSetting r() {
        return this.offsetY;
    }

    public SliderSetting s() {
        return this.scale;
    }

    @EventTarget
    public void a(AttackEvent event) {
        Entity entity = event.b();
        if (!(entity instanceof PlayerEntity player) || player == mc.player) {
            return;
        }
        this.lastAttackedId = player.getId();
        this.lastAttackedName = player.getName().getString();
        this.lastAttackTime = System.currentTimeMillis();
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.world == null || mc.player == null) {
            this.healthMap.clear();
            return;
        }
        long now = System.currentTimeMillis();
        DamageIndicator damageIndicator = Laura.getInstance().getModuleProcessor().t().be();
        boolean reuseHits = damageIndicator != null && damageIndicator.m();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player) {
                continue;
            }
            Integer id = Integer.valueOf(living.getId());
            float health = living.getHealth();
            Float last = this.healthMap.put(id, Float.valueOf(health));
            if (last == null) {
                continue;
            }
            boolean died = (last.floatValue() > 0.0f && health <= 0.0f) || living.isDead();
            if (!died || !(living instanceof PlayerEntity)) {
                continue;
            }
            boolean tracked = living.getId() == this.lastAttackedId && (now - this.lastAttackTime) <= ATTACK_WINDOW_MS;
            if (!tracked && reuseHits && damageIndicator.v() == living.getId()
                    && (now - damageIndicator.u()) <= ATTACK_WINDOW_MS) {
                tracked = true;
            }
            if (tracked) {
                showKill(living.getName().getString(), now);
            }
        }
        this.healthMap.keySet().removeIf(id -> {
            for (Entity entity : mc.world.getEntities()) {
                if (entity.getId() == id.intValue()) {
                    return false;
                }
            }
            if (id.intValue() == this.lastAttackedId && (now - this.lastAttackTime) <= ATTACK_WINDOW_MS) {
                showKill(this.lastAttackedName, now);
            }
            return true;
        });
        if (this.activeName != null && now - this.activeStart > TOTAL_MS) {
            this.activeName = null;
        }
    }

    @EventTarget
    public void a(DrawEvent event) {
        if (!event.b() || mc.options.hudHidden || this.activeName == null) {
            return;
        }
        renderKillText(event.h(), System.currentTimeMillis());
    }

    private void showKill(String name, long now) {
        if (name == null || name.isBlank()) {
            return;
        }
        this.activeName = name;
        this.activeStart = now;
    }

    private void renderKillText(MatrixStack matrices, long now) {
        long elapsed = now - this.activeStart;
        if (elapsed < 0L || elapsed > TOTAL_MS) {
            this.activeName = null;
            return;
        }
        float alpha;
        float yShift;
        float animScale = 1.0f;
        if (elapsed < APPEAR_MS) {
            float t = elapsed / (float) APPEAR_MS;
            float eased = EasingList.s.ease(MathHelper.clamp(t, 0.0f, 1.0f));
            alpha = MathHelper.clamp(t, 0.0f, 1.0f);
            yShift = (1.0f - eased) * 28.0f;
        } else if (elapsed < APPEAR_MS + HOLD_MS) {
            float holdT = (elapsed - APPEAR_MS) / (float) HOLD_MS;
            alpha = 1.0f;
            yShift = 0.0f;
            animScale = 1.0f + (0.025f * (float) Math.sin(holdT * Math.PI * 2.0d));
        } else {
            float t = (elapsed - APPEAR_MS - HOLD_MS) / (float) FADE_MS;
            float eased = EasingList.f.ease(MathHelper.clamp(t, 0.0f, 1.0f));
            alpha = 1.0f - eased;
            yShift = -18.0f * eased;
        }
        if (alpha <= 0.01f) {
            return;
        }

        float size = HUD_TEXT_SIZE * this.scale.c().floatValue() * animScale;
        String text = "YOU KILLED " + this.activeName;
        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();
        float textW = Fonts.e.a(text, size);
        float x = ((screenW - textW) / 2.0f) + this.offsetX.c().floatValue();
        float y = (screenH * 0.72f) + this.offsetY.c().floatValue() + yShift;

        int accent = Laura.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor();
        int color = ColorUtil.applyAlphaToColor(accent, alpha);
        int outline = ColorUtil.applyAlphaToColor(ColorUtil.b(accent, 0.35f), alpha);

        Fonts.e.a(matrices, text, x, y, size, 0.05f, color, -1, -1.0f, 0.5f, 0.0f, outline, 0.14f);
    }

    @Override
    public void c() {
        super.c();
        this.healthMap.clear();
        this.activeName = null;
        this.lastAttackedId = -1;
    }
}
