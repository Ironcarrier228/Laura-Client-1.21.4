package platform;


import laura.core.Laura;
import net.fabricmc.api.ClientModInitializer;

public class Initializer implements ClientModInitializer {


    public void onInitializeClient() {
        new Laura();
    }
}
