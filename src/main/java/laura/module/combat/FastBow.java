package laura.module.combat;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.SliderSetting;
import net.minecraft.item.BowItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Портировано из Wexside (модуль FastBow).
 * Автоматически «отпускает» тетиву лука с минимальной задержкой,
 * позволяя спамить стрелами без полного натяжения.
 */
@ModuleRegister(name = "FastBow", description = "Быстрая стрельба из лука", category = Category.Combat)
public class FastBow extends Module {
    private final SliderSetting delay = new SliderSetting("Задержка", 10.0f, 1.0f, 10.0f, 1.0f);

    public FastBow() {
        a(this.delay);
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) {
            return;
        }
        if (!(mc.player.getMainHandStack().getItem() instanceof BowItem) || !mc.player.isUsingItem()) {
            return;
        }
        if (mc.player.getItemUseTime() < this.delay.c().intValue()) {
            return;
        }
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
        mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        mc.player.stopUsingItem();
    }
}
