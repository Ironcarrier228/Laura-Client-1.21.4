package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;

/**
 * AutoLeave (из WildClient)
 * <p>
 * Автоматически выходит с сервера если HP падает ниже заданного порога.
 */
@ModuleRegister(
        name = "AutoLeave",
        description = "Авто-выход при низком HP",
        category = Category.Misc
)
public class AutoLeave extends Module {
    private final SliderSetting hpThreshold = new SliderSetting(
            "Порог HP (%)", 20.0F, 5.0F, 90.0F, 1.0F, false
    );

    public AutoLeave() {
        a(hpThreshold);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isAlive()) return;

        float hpPercent = (mc.player.getHealth() / mc.player.getMaxHealth()) * 100.0F;
        float threshold = hpThreshold.h();

        if (hpPercent <= threshold) {
            laura.util.ChatUtil.sendMessage("AutoLeave", "HP " + String.format("%.0f", hpPercent) + "% <= " + String.format("%.0f", threshold) + "%, выхожу...");
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(
                        net.minecraft.text.Text.literal("AutoLeave: HP too low")
                );
            }
            a(false); // выключаем модуль
        }
    }
}
