package platform.inject.mixin;


import laura.core.Laura;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Session.class})
public class SessionMixin {
    @ModifyReturnValue(method = {"getUsername"}, at = {@At("RETURN")})
    private String username(String original) {
        return (Laura.getInstance() == null || Laura.getInstance().getModuleProcessor().h().a() == null) ? original : Laura.getInstance().getModuleProcessor().h().a().b();
    }
}
