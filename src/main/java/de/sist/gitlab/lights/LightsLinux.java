// (C) 2020 PPI AG
package de.sist.gitlab.lights;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class LightsLinux implements LightsApi {

    private static final Logger logger = Logger.getInstance(LightsLinux.class);

    public static final String OFF = " 0";
    public static final String ON = " 1";
    private File clewarecontrol;
    private static Thread thread;

    public LightsLinux() {
        initialize();
    }

    public void initialize() {
        clewarecontrol = LightControl.loadResource("clewarecontrol");
        if (clewarecontrol == null) {
            return;
        }
        boolean executable = clewarecontrol.setExecutable(true);
        if (!executable) {
            logger.error("Unable to set lights control executable");
        }
    }

    public void turnOffColor(LightsWindowsLibrary.Light light) {
        runCommand("-as " + light.indexLinux + OFF);
    }

    public void turnOnColor(LightsWindowsLibrary.Light light, boolean turnOthersOff) {
        StringBuilder stringBuilder = new StringBuilder("-as " + light.indexLinux + ON);

        if (turnOthersOff) {
            Arrays.stream(LightsWindowsLibrary.Light.values())
                    .filter(x -> x != light)
                    .forEach(x ->
                            stringBuilder.append(" -as ").append(x.indexLinux).append(OFF)
                    );
        }
        final String parameter = stringBuilder.toString();

        runCommand(parameter);
    }

    public void turnAllOff() {
        runCommand(" -as " + LightsWindowsLibrary.Light.RED.indexLinux + OFF
                + " -as " + LightsWindowsLibrary.Light.YELLOW.indexLinux + OFF
                + " -as " + LightsWindowsLibrary.Light.GREEN.indexLinux + OFF);
    }

    private void runCommand(String parameter) {
        if (clewarecontrol == null) {
            logger.debug("Lights control not initialized");
            return;
        }
        if (thread != null && thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        thread = new Thread(() -> {
            try {
                Runtime.getRuntime().exec(clewarecontrol.getAbsolutePath() + " -c 1 " + parameter).waitFor();
            } catch (IOException | InterruptedException e) {
                logger.error(e);
            }
        });
        thread.start();
    }

}
