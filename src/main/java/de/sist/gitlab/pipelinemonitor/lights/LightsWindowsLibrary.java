package de.sist.gitlab.pipelinemonitor.lights;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface LightsWindowsLibrary extends LightsApi, Library {

    Pointer FCWInitObject();

    int FCWOpenCleware(Pointer cwHandle);

    int FCWCloseCleware(Pointer cwHandle);

    int FCWGetSerialNumber(Pointer cwHandle, int index);

    int FCWSetSwitch(Pointer cwHandle, int deviceNumber, int switchNumber, int on);

    default void setSwitch(Pointer cw, Light light, boolean state) {
        FCWSetSwitch(cw, 0, light.index, state ? 1 : 0);
    }

    @Override
    void turnOnColor(Light light, boolean turnOthersOff);

    @Override
    void turnOffColor(Light light);

    @Override
    void turnAllOff();

    default void turnOnColor(Pointer cw, Light light, boolean turnOthersOff) {
        FCWSetSwitch(cw, 0, light.index, 1);
        if (turnOthersOff) {
            //With a stream we get this warning from IntelliJ:
            //#Attempt to execute an *invokeinterface* instruction on a private method de.sist.gitlab.pipelinemonitor.lights.LightsWindowsLibrary.lambda$turnOnColor$1(Pointer cw, LightsApi.Light x)
            for (Light x : Light.values()) {
                if (x != light) {
                    FCWSetSwitch(cw, 0, x.index, 0);
                }
            }
        }
    }

    default void turnOffColor(Pointer cw, Light light) {
        FCWSetSwitch(cw, 0, light.index, 0);
    }

    default void turnAllOff(Pointer cw) {
        for (Light x : Light.values()) {
            FCWSetSwitch(cw, 0, x.index, 0);
        }
    }

}
