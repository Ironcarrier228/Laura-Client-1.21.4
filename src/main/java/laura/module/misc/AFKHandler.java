package laura.module.misc;

import laura.core.Laura;
import laura.core.EventTarget;
import laura.core.Interface;
import laura.event.InputEvent;
import laura.event.TickEvent;
import laura.handler.BaseHandler;

import laura.util.MathUtil;
import laura.util.Rotation;

import java.util.concurrent.ThreadLocalRandom;


public class AFKHandler extends BaseHandler implements Interface {
    private int b = -1;

    public int b() {
        return this.b;
    }

    public void a(int ticks) {
        this.b = ticks;
    }

    public boolean a() {
        return this.b > 0;
    }

    @EventTarget
    public void a(TickEvent event) {
        if (this.b > 0) {
            this.b--;
        }
    }

    @EventTarget
    public void a(InputEvent e) {
        if (this.b > 0) {
            e.setForward(ThreadLocalRandom.current().nextBoolean() ? 1.0f : -1.0f);
            e.setStrafe(ThreadLocalRandom.current().nextBoolean() ? 1.0f : -1.0f);
            Laura.getInstance().getModuleProcessor().k().startAiming(new Rotation(mc.player.getYaw() + MathUtil.a(-2.0f, 2.0f), MathUtil.b(mc.player.getPitch() + MathUtil.a(-1.0f, 1.0f), -90.0f, 90.0f)), 150.0f, 10, 1);
        }
    }
}
