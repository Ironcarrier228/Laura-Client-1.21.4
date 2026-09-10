package laura.render;

import laura.util.MathUtil;

/**
 * Real-time spring animation.
 *
 * <p>Simulates a damped spring (mass = 1) using wall clock time
 * ({@code System.nanoTime()}), so the motion is smooth and frame-rate
 * independent. Call {@link #get()} once per frame — it advances the
 * simulation and returns the current value.</p>
 *
 * <p>Unlike {@link AnimationUtil} (eased tween), a spring produces natural
 * overshoot and settle, which the GUI uses for hover, press and entrance
 * feedback.</p>
 */
public class Spring {
    public static final float DEFAULT_STIFFNESS = 260.0f;
    public static final float DEFAULT_DAMPING = 24.0f;

    private float value;
    private float velocity;
    private float target;
    private float stiffness;
    private float damping;
    private long lastTime = System.nanoTime();

    public Spring() {
        this(DEFAULT_STIFFNESS, DEFAULT_DAMPING);
    }

    public Spring(float stiffness, float damping) {
        this.stiffness = stiffness;
        this.damping = damping;
    }

    /** Fast, crisp feedback (hover / press states). */
    public static Spring snappy() {
        return new Spring(420.0f, 30.0f);
    }

    /** Soft, gentle motion (entrances). */
    public static Spring soft() {
        return new Spring(140.0f, 20.0f);
    }

    /** Playful motion with visible overshoot. */
    public static Spring bouncy() {
        return new Spring(320.0f, 15.0f);
    }

    /** Smoothly steer the spring toward a new target. */
    public void to(float target) {
        this.target = target;
    }

    /** Shift the target by an amount (accumulating springs, e.g. scroll). */
    public void add(float amount) {
        this.target += amount;
    }

    /** Instantly jump to a value, cancelling any in-flight motion. */
    public void snap(float value) {
        this.value = value;
        this.target = value;
        this.velocity = 0.0f;
        this.lastTime = System.nanoTime();
    }

    /** Kick the spring with an impulse (click pulse). */
    public void kick(float impulse) {
        this.velocity += impulse;
    }

    public float getTarget() {
        return this.target;
    }

    public float getValue() {
        return this.value;
    }

    public float getStiffness() {
        return this.stiffness;
    }

    public float getDamping() {
        return this.damping;
    }

    public void setStiffness(float stiffness) {
        this.stiffness = stiffness;
    }

    public void setDamping(float damping) {
        this.damping = damping;
    }

    /**
     * Advance the simulation in real time and return the value.
     * Call exactly once per frame for this spring.
     */
    public float get() {
        long now = System.nanoTime();
        float dt = MathUtil.b((now - this.lastTime) / 1.0E9f, 0.0f, 0.1f);
        this.lastTime = now;
        if (dt <= 0.0f) {
            return this.value;
        }
        int steps = Math.max(1, (int) Math.ceil(dt / (1.0f / 240.0f)));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float force = (this.stiffness * (this.target - this.value)) - (this.damping * this.velocity);
            this.velocity += force * h;
            this.value += this.velocity * h;
        }
        return this.value;
    }

    public boolean isSettled() {
        return Math.abs(this.target - this.value) < 0.001f && Math.abs(this.velocity) < 0.001f;
    }
}
