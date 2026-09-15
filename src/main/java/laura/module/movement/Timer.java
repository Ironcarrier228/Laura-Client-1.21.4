package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.KeyEvent;
import laura.event.PacketEvent;
import laura.event.TickEvent;
import laura.setting.BindSetting;
import laura.setting.BooleanSetting;
import laura.setting.ModeSetting;
import laura.setting.SliderSetting;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;

/**
 * Портировано из Wexside (модуль Timer).
 * Ускоряет игру через множитель тиков (см. RenderTickCounterDynamicMixin).
 * Режимы: «Умный» (с накоплением и сбросом), «Бёрст» (окно дрифта) и «Грим» (зарядка от пинг-пакетов).
 */
@ModuleRegister(name = "Timer", description = "Ускоряет игру", category = Category.Movement)
public class Timer extends Module {
    public static float speed = 1.0f;

    private final ModeSetting mode = new ModeSetting("Режим", "Умный", "Умный", "Бёрст", "Грим");
    private final SliderSetting speedSetting = new SliderSetting("Скорость", 2.0f, 0.0f, 10.0f, 0.01f)
            .a(() -> !this.mode.l("Умный"));
    private final BooleanSetting smartReset = new BooleanSetting("Умный сброс", true)
            .a(() -> !this.mode.l("Умный"));
    private final SliderSetting decay = new SliderSetting("Скорость убывания", 3.8f, 0.15f, 5.0f, 0.1f)
            .a(() -> !this.mode.l("Умный") || !this.smartReset.c().booleanValue());
    private final SliderSetting driftWindow = new SliderSetting("Окно дрифта", 110.0f, 40.0f, 120.0f, 1.0f)
            .a(() -> !this.mode.l("Бёрст"));
    private final SliderSetting burstSpeed = new SliderSetting("Скорость бёрста", 3.0f, 1.5f, 6.0f, 0.1f)
            .a(() -> !this.mode.l("Бёрст"));
    private final SliderSetting chargeSpeed = new SliderSetting("Скорость зарядки", 0.6f, 0.1f, 0.95f, 0.05f)
            .a(() -> !this.mode.l("Бёрст"));
    private final SliderSetting flagMargin = new SliderSetting("Запас до флага", 15.0f, 0.0f, 40.0f, 1.0f)
            .a(() -> !this.mode.l("Бёрст"));
    private final SliderSetting grimSpeed = new SliderSetting("Скорость грима", 2.0f, 1.0f, 6.0f, 0.1f)
            .a(() -> !this.mode.l("Грим"));
    private final BindSetting boostKey = new BindSetting("Кнопка буста", -1)
            .a(() -> !this.mode.l("Грим"));
    private final BooleanSetting airOnly = new BooleanSetting("Ускорять в воздухе", false)
            .a(() -> this.mode.l("Грим"));

    private float accumulator = 0.0f;
    private boolean draining = false;
    private double driftBalance = 0.0d;
    private long lastFrameNanos = 0L;
    private boolean charging = false;
    private boolean movedLastFrame = false;
    private float grimCharge = 0.0f;
    private long grimChargeWindowStart = 0L;
    private long grimLastFlagTime = 0L;
    private boolean boostKeyPressed = false;

    public Timer() {
        a(this.mode, this.speedSetting, this.smartReset, this.decay, this.driftWindow, this.burstSpeed,
                this.chargeSpeed, this.flagMargin, this.grimSpeed, this.boostKey, this.airOnly);
    }

    @Override
    public void b() {
        super.b();
        resetState();
    }

    @Override
    public void c() {
        super.c();
        speed = 1.0f;
        resetState();
    }

    @EventTarget
    public void a(KeyEvent event) {
        int key = this.boostKey.c().intValue();
        if (key != -1 && event.getKey() == key) {
            if (event.getAction() == 1) {
                this.boostKeyPressed = true;
            } else if (event.getAction() == 0) {
                this.boostKeyPressed = false;
            }
        }
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (this.mode.l("Грим")) {
            updateGrim();
        } else if (this.airOnly.c().booleanValue() && mc.player.isOnGround()) {
            speed = 1.0f;
            resetState();
        } else if (this.mode.l("Бёрст")) {
            updateBurst();
        } else if (!this.smartReset.c().booleanValue()) {
            speed = this.speedSetting.c().floatValue();
        } else {
            if (this.draining) {
                this.accumulator -= this.decay.c().floatValue();
                speed = 1.0f;
                if (this.accumulator <= 0.0f) {
                    this.accumulator = 0.0f;
                    this.draining = false;
                }
            } else {
                speed = this.speedSetting.c().floatValue();
                this.accumulator += this.decay.c().floatValue();
                if (this.accumulator >= 100.0f) {
                    this.accumulator = 100.0f;
                    this.draining = true;
                }
            }
        }
    }

    @EventTarget
    public void a(PacketEvent event) {
        if (event.isSend() || mc.player == null) {
            return;
        }
        if (this.mode.l("Грим")) {
            handleGrimPacket(event);
            return;
        }
        if (this.mode.l("Бёрст")) {
            boolean positionFlag = event.getPacket() instanceof PlayerPositionLookS2CPacket;
            boolean velocityFlag = event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity
                    && velocity.getEntityId() == mc.player.getId();
            if (positionFlag || velocityFlag) {
                this.driftBalance = -this.driftWindow.c().floatValue();
                this.charging = true;
                this.lastFrameNanos = 0L;
            }
        }
    }

    private void updateBurst() {
        long now = System.nanoTime();
        boolean moving = mc.player != null
                && (Math.abs(mc.player.getX() - mc.player.prevX) > 0.001d
                || Math.abs(mc.player.getZ() - mc.player.prevZ) > 0.001d
                || !mc.player.isOnGround());
        if (this.lastFrameNanos == 0L) {
            this.lastFrameNanos = now;
            this.driftBalance = -this.driftWindow.c().floatValue();
            this.charging = false;
            this.movedLastFrame = moving;
            speed = this.burstSpeed.c().floatValue();
            return;
        }
        double frameMillis = (now - this.lastFrameNanos) / 1000000.0d;
        this.lastFrameNanos = now;
        if (frameMillis > 300.0d || frameMillis < 0.0d) {
            frameMillis = 50.0d;
        }
        if (moving && !this.movedLastFrame) {
            this.driftBalance = -this.driftWindow.c().floatValue();
            this.charging = false;
        }
        this.movedLastFrame = moving;
        this.driftBalance += 50.0d - frameMillis;
        if (this.driftBalance < -this.driftWindow.c().floatValue()) {
            this.driftBalance = -this.driftWindow.c().floatValue();
        }
        if (this.charging) {
            speed = this.chargeSpeed.c().floatValue();
            if (this.driftBalance <= -this.driftWindow.c().floatValue() + 2.0d) {
                this.charging = false;
            }
        } else {
            double worstCase = Math.max(0.0d, 50.0d - frameMillis);
            if (this.driftBalance + worstCase >= -this.flagMargin.c().floatValue()) {
                this.charging = true;
                speed = this.chargeSpeed.c().floatValue();
            } else {
                speed = this.burstSpeed.c().floatValue();
            }
        }
    }

    private void updateGrim() {
        long now = System.currentTimeMillis();
        int key = this.boostKey.c().intValue();
        boolean boostAllowed = key == -1 || this.boostKeyPressed;
        if (this.grimCharge > 0.0f && boostAllowed && now - this.grimLastFlagTime >= 2000L) {
            speed = Math.max(this.grimSpeed.c().floatValue(), 1.0f);
            this.grimCharge = MathHelper.clamp(this.grimCharge - ((0.0025f * this.grimSpeed.c().floatValue()) - 0.0025f), 0.0f, 1.0f);
        } else {
            speed = 1.0f;
        }
    }

    private void handleGrimPacket(PacketEvent event) {
        long now = System.currentTimeMillis();
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.grimLastFlagTime = now;
            speed = 1.0f;
            this.grimCharge = 0.0f;
        } else if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocity && velocity.getEntityId() == mc.player.getId()) {
            speed = 1.0f;
            this.grimCharge = 0.0f;
        } else if (event.getPacket() instanceof CommonPingS2CPacket && now - this.grimLastFlagTime > 2000L) {
            if (now - this.grimChargeWindowStart > 25000L) {
                this.grimChargeWindowStart = now;
                this.grimCharge = 0.0f;
                return;
            }
            if (!isMoving()) {
                this.grimCharge = MathHelper.clamp(this.grimCharge + 0.005f, 0.0f, 1.0f);
            }
            event.a(true);
        }
    }

    private boolean isMoving() {
        return mc.player != null && mc.player.input != null
                && (mc.player.input.movementForward != 0.0f || mc.player.input.movementSideways != 0.0f);
    }

    private void resetState() {
        this.accumulator = 0.0f;
        this.draining = false;
        this.driftBalance = 0.0d;
        this.lastFrameNanos = 0L;
        this.charging = false;
        this.movedLastFrame = false;
        this.grimCharge = 0.0f;
        this.grimChargeWindowStart = System.currentTimeMillis();
        this.grimLastFlagTime = 0L;
    }
}
