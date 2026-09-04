package laura.module.movement;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.SliderSetting;
import net.minecraft.util.hit.BlockHitResult;

@ModuleRegister(name = "Fast Break", description = "Ускоряет разрушение блоков, обрабатывая добычу несколько раз за тик", category = Category.Movement)
public class FastBreak extends Module {
    private final SliderSetting b = new SliderSetting("Интенсивность копания", 2.0f, 1.0f, 5.0f, 0.25f);

    public FastBreak() {
        a(this.b);
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.options.attackKey.isPressed() && mc.interactionManager.isBreakingBlock()) {
            if (mc.crosshairTarget instanceof BlockHitResult hit) {
                for (int i = 0; i < this.b.c().intValue() - 1; i++) {
                    mc.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(), hit.getSide());
                }
            }
        }
    }
}
