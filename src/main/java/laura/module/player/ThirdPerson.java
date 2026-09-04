package laura.module.player;

import laura.core.*;
import laura.core.Module;
import laura.event.KeyEvent;
import laura.event.TickEvent;
import laura.lib.javassist.TokenId;
import laura.setting.BindSetting;
import laura.setting.ModeSetting;
import laura.util.Look;
import laura.util.MathUtil;
import laura.util.Rotation;
import net.minecraft.client.option.Perspective;

@ModuleRegister(name = "Third Person", description = "Свободный обзор от третьего лица без изменения направления движения", category = Category.Player)
public class ThirdPerson extends Module {
    private final ModeSetting b = new ModeSetting("Режим активации осмотра", "По нажатию", "По нажатию", "По зажатию");
    private boolean isActive;
    private Rotation rotation;

    public ThirdPerson() {
        BindSetting d = new BindSetting("Кнопка осмотра", Integer.valueOf(TokenId.Q_), 0).a(() -> {
            if (this.b.l("По зажатию")) {
                d(true);
            } else {
                d(!this.isActive);
            }
        }).b(() -> {
            if (this.isActive && this.b.l("По зажатию")) {
                d(false);
            }
        });
        a(this.b, d);
    }

    @EventTarget
    public void a(KeyEvent event) {
        if (this.isActive && event.getKey() == mc.options.togglePerspectiveKey.getDefaultKey().getCode()) {
            event.a(true);
        }
    }

    @EventTarget
    public void a(TickEvent event) {
        if (this.isActive) {
            if (mc.currentScreen != null) {
                d(false);
            } else {
                Laura.getInstance().getModuleProcessor().k().startAiming(
                        new Rotation(mc.player.getYaw(), MathUtil.b(mc.player.getPitch(), -89.0f, 89.0f)), 360.0f, 0,
                        1);
            }
        }
    }

    private void d(boolean active) {
        if (active) {
            this.rotation = new Rotation(Look.b(), Look.c());
        } else {
            Look.a(this.rotation.c());
            Look.b(this.rotation.d());
        }
        mc.options.setPerspective(active ? Perspective.THIRD_PERSON_BACK : Perspective.FIRST_PERSON);
        this.isActive = active;
    }
}
