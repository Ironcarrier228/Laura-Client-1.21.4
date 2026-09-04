package laura.module.combat;

import laura.core.*;
import laura.core.Module;
import laura.event.AttackEvent;

@ModuleRegister(name = "No Friend Damage", description = "Не позволяет наносить урон вашим друзьям", category = Category.Combat)
public class NoFriendDamage extends Module {
    @EventTarget
    public void a(AttackEvent event) {
        event.a(Laura.getInstance().getModuleProcessor().e().d(event.b().getName().getString()));
    }
}
