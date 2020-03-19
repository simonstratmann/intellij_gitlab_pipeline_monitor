package de.sist.gitlab.lights;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.io.File;

public class LightsWindows implements LightsApi {

    private final Logger logger = Logger.getInstance(LightsWindows.class);
    private static Pointer lightsPointer;
    private static LightsWindowsLibrary lightsApi;

    public LightsWindows() {
        initialize();
    }

    public void initialize() {
        if (lightsApi != null) {
            return;
        }
        File file = LightsControl.loadResource("USBaccess.dll");
        if (file == null) {
            return;
        }
        try {
            System.setProperty("jna.library.path", LightsControl.getPluginPath().getAbsolutePath());
            lightsApi = Native.load(file.getAbsolutePath(), LightsWindowsLibrary.class);
            lightsPointer = lightsApi.FCWInitObject();
            lightsApi.FCWOpenCleware(lightsPointer);
        } catch (Exception e) {
            logger.error(e);
        }
    }

    @Override
    public void turnOnColor(Light light, boolean turnOthersOff) {
        if (lightsApi == null) {
            return;
        }
        logger.debug("Turning on " + light);
        lightsApi.turnOnColor(lightsPointer, light, turnOthersOff);
    }

    @Override
    public void turnOffColor(Light light) {
        if (lightsApi == null) {
            return;
        }
        logger.debug("Turning off " + light);
        lightsApi.turnOffColor(lightsPointer, light);
    }

    @Override
    public void turnAllOff() {
        turnOffColor(Light.RED);
        turnOffColor(Light.YELLOW);
        turnOffColor(Light.GREEN);
    }
}
