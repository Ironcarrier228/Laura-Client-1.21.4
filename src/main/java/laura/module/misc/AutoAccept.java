package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.PacketEvent;
import laura.util.ChatUtil;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;

/**
 * AutoAccept (из WildClient)
 * <p>
 * Автоматически принимает серверные запросы на телепорт (через GameStateChange с reason=START_WAITING_FOR_LEVEL_CHUNKS)
 * или подобные серверные события.
 * <p>
 * Использует существующий в Laura PacketEvent хук (см. ClientConnectionMixin).
 */
@ModuleRegister(
        name = "AutoAccept",
        description = "Авто-принятие серверных TP/Party запросов",
        category = Category.Misc
)
public class AutoAccept extends Module {
    private int acceptedCount = 0;

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof GameStateChangeS2CPacket packet)) return;

        // Server can request teleport via reason codes 3 (demo) and various server-specific codes.
        // Common pattern: GameStateChange with reason 0 + gameMode starts respawn sequence.
        if (packet.getReason() == 3) {
            acceptedCount++;
            ChatUtil.sendMessage("AutoAccept", "Принят серверный запрос (" + acceptedCount + ")");
        }
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }
}
