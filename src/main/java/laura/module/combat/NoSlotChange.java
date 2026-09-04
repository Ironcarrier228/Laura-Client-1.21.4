package laura.module.combat;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.PacketEvent;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

@ModuleRegister(name = "No Slot Change", description = "Не даёт серверу принудительно менять активный слот в хотбаре", category = Category.Combat)
public class NoSlotChange extends Module {
    @EventTarget
    public void a(PacketEvent event) {
        if (event.isReceive() && (event.getPacket() instanceof UpdateSelectedSlotS2CPacket)) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            event.a(true);
        }
    }
}
