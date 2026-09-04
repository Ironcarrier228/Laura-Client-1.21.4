package platform;


import laura.core.Laura;
import laura.ui.shader.ShaderKeys;
import net.fabricmc.api.ClientModInitializer;

public class Initializer implements ClientModInitializer {


    public void onInitializeClient() {
        // Has to happen before the client runs its first resource reload,
        // otherwise the custom shaders are never compiled and stay null.
        ShaderKeys.register();
        new Laura();
    }
}
