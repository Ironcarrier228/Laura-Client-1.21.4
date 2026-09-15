package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.PacketEvent;
import laura.event.TickEvent;
import laura.util.ChatUtil;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;

/**
 * PvPSafe (из WildClient)
 * <p>
 * Защищает от Combat Log: если игрок в бою (получал урон последние 10 секунд),
 * блокирует кнопки "Disconnect" / "Back to Title Screen" в игровом меню.
 * <p>
 * Хук на disconnect реализован через PvPSafeMixin (см. platform.inject.mixin.PvPSafeMixin).
 */
@ModuleRegister(
        name = "PvPSafe",
        description = "Защита от Combat Log (блокирует выход в ПВП)",
        category = Category.Misc
)
public class PvPSafe extends Module {
    private long lastDamageTime = 0L;
    private static final long COMBAT_TIMEOUT_MS = 10_000L; // 10 секунд после урона

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof HealthUpdateS2CPacket packet)) return;

        // Если новое HP меньше предыдущего — игрок получил урон
        float newHp = packet.health();
        // Сравниваем с текущим HP игрока (если доступен)
        if (mc.player != null && newHp < mc.player.getHealth()) {
            lastDamageTime = System.currentTimeMillis();
        } else if (newHp > 0 && mc.player != null && newHp < mc.player.getMaxHealth()) {
            // Любое обновление HP = возможный урон
            lastDamageTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        // При включении модуля в бою — сразу отмечаем время
        if (mc.player != null && mc.player.getHealth() < mc.player.getMaxHealth()) {
            lastDamageTime = System.currentTimeMillis();
        }
    }

    @Override
    public void b() {
        super.b();
        lastDamageTime = System.currentTimeMillis();
        ChatUtil.sendMessage("PvPSafe", "Активен — защита от Combat Log");
    }

    @Override
    public void c() {
        super.c();
        ChatUtil.sendMessage("PvPSafe", "Выключен");
    }

    public boolean isInCombat() {
        return System.currentTimeMillis() - lastDamageTime < COMBAT_TIMEOUT_MS;
    }
}
