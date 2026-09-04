package laura.ui.widget;

import laura.core.Laura;
import laura.core.Interface;
import laura.core.InterfaceC0020Opcode;
import laura.event.DrawEvent;
import laura.render.ScaleUtil;
import laura.ui.element.DragInfo;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

public class ArmorWidget extends Widget implements Interface {
    public ArmorWidget() {
        super(new DragInfo("Броня", 0.0f, 0.0f, 0.0f, 0.0f));
        j().setWidget(this);
    }

    @Override
    public void a(DrawEvent event) {
        if (event.b() && !mc.options.hudHidden && !mc.player.isSpectator()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            int count = 0;
            for (EquipmentSlot slot : armorSlots) {
                if (!mc.player.getEquippedStack(slot).isEmpty()) {
                    count++;
                }
            }
            if (count > 0) {
                ScaleUtil.b(event.i());
                event.i().getMatrices().push();
                event.i().getMatrices().translate(0.0f, (-16.0f) * Laura.getInstance().getModuleProcessor().t().Q().s().c(), 0.0f);
                int startX = ((mc.getWindow().getScaledWidth() / 2) - 91) + InterfaceC0020Opcode.bJ + 4;
                int startY = mc.getWindow().getScaledHeight() - 22;
                int epta = startX + ((mc.player.getMainArm() != Arm.LEFT || mc.player.getOffHandStack().isEmpty()) ? 0 : 30);
                event.i().drawGuiTexture(RenderLayer::getGuiTextured, Identifier.ofVanilla("hud/hotbar"), InterfaceC0020Opcode.bJ, 22, 0, 0, epta, startY, (count * 20) + 1, 22);
                int index = 0;
                for (EquipmentSlot slot2 : armorSlots) {
                    ItemStack stack = mc.player.getEquippedStack(slot2);
                    if (!stack.isEmpty()) {
                        int x = epta + 3 + (index * 20);
                        int y = startY + 3;
                        event.i().drawItem(stack, x, y);
                        event.i().drawStackOverlay(mc.textRenderer, stack, x, y);
                        index++;
                    }
                }
                event.i().getMatrices().pop();
                ScaleUtil.c(event.i());
            }
        }
        super.a(event);
    }
}
