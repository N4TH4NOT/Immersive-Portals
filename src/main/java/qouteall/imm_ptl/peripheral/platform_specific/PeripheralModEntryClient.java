package qouteall.imm_ptl.peripheral.platform_specific;

import net.fabricmc.api.ClientModInitializer;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

public class PeripheralModEntryClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        // 26.2 classifies cutout geometry from baked sprite materials.
        PeripheralModMain.initClient();
    }
}
