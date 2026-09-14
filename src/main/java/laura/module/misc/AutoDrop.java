package laura.module.misc;

import laura.core.Category;
import laura.core.EventTarget;
import laura.core.Module;
import laura.core.ModuleRegister;
import laura.event.TickEvent;
import laura.setting.BooleanSetting;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Портировано из Wexside (модуль AutoDrop).
 * Автоматически выбрасывает выбранный «мусор» из инвентаря по одному слоту за тик.
 */
@ModuleRegister(name = "AutoDrop", description = "Автоматически выбрасывает мусор", category = Category.Misc)
public class AutoDrop extends Module {
    private final BooleanSetting stone = new BooleanSetting("Камень", false);
    private final BooleanSetting cobblestone = new BooleanSetting("Булыжник", false);
    private final BooleanSetting granite = new BooleanSetting("Гранит", false);
    private final BooleanSetting sticks = new BooleanSetting("Палки", false);
    private final BooleanSetting deepslate = new BooleanSetting("Сланец", false);
    private final BooleanSetting andesite = new BooleanSetting("Андезит", false);
    private final BooleanSetting netherrack = new BooleanSetting("Незерак", false);
    private final BooleanSetting basalt = new BooleanSetting("Базальт", false);
    private final BooleanSetting blackstone = new BooleanSetting("Чернит", false);
    private final BooleanSetting soulBlocks = new BooleanSetting("Блоки душ", false);
    private final BooleanSetting netherOres = new BooleanSetting("Руды ада", false);
    private final BooleanSetting gravel = new BooleanSetting("Гравий", false);

    private int checkedSlot = 9;

    public AutoDrop() {
        a(this.stone, this.cobblestone, this.granite, this.sticks, this.deepslate, this.andesite,
                this.netherrack, this.basalt, this.blackstone, this.soulBlocks, this.netherOres, this.gravel);
    }

    @EventTarget
    public void a(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (this.checkedSlot > 44) {
            this.checkedSlot = 9;
            return;
        }
        Slot slot = mc.player.playerScreenHandler.getSlot(this.checkedSlot);
        if (slot.hasStack() && shouldDrop(slot.getStack().getItem())) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, this.checkedSlot, 1, SlotActionType.THROW, mc.player);
        }
        this.checkedSlot++;
    }

    private boolean shouldDrop(Item item) {
        if (item == Items.STONE && this.stone.c().booleanValue()) {
            return true;
        }
        if (item == Items.COBBLESTONE && this.cobblestone.c().booleanValue()) {
            return true;
        }
        if (item == Items.GRANITE && this.granite.c().booleanValue()) {
            return true;
        }
        if (item == Items.STICK && this.sticks.c().booleanValue()) {
            return true;
        }
        if (item == Items.ANDESITE && this.andesite.c().booleanValue()) {
            return true;
        }
        if ((item == Items.DEEPSLATE || item == Items.COBBLED_DEEPSLATE) && this.deepslate.c().booleanValue()) {
            return true;
        }
        if (item == Items.NETHERRACK && this.netherrack.c().booleanValue()) {
            return true;
        }
        if ((item == Items.BASALT || item == Items.SMOOTH_BASALT || item == Items.POLISHED_BASALT) && this.basalt.c().booleanValue()) {
            return true;
        }
        if ((item == Items.BLACKSTONE || item == Items.GILDED_BLACKSTONE) && this.blackstone.c().booleanValue()) {
            return true;
        }
        if ((item == Items.SOUL_SAND || item == Items.SOUL_SOIL) && this.soulBlocks.c().booleanValue()) {
            return true;
        }
        if ((item == Items.NETHER_QUARTZ_ORE || item == Items.NETHER_GOLD_ORE || item == Items.QUARTZ || item == Items.GOLD_NUGGET)
                && this.netherOres.c().booleanValue()) {
            return true;
        }
        return item == Items.GRAVEL && this.gravel.c().booleanValue();
    }
}
