package de.sist.gitlab.lights;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

import java.util.Arrays;

public interface LightsApi extends Library {

    public enum Light {
        RED(0x10),
        YELLOW(0x11),
        GREEN(0x12);

        private int index;

        Light(int index) {
            this.index = index;
        }
    }


    Pointer FCWInitObject();

    int FCWOpenCleware(Pointer cwHandle);

    int FCWCloseCleware(Pointer cwHandle);

    int FCWGetSerialNumber(Pointer cwHandle, int index);

    int FCWSetSwitch(Pointer cwHandle, int deviceNumber, int switchNumber, int on);

    default void setSwitch(Pointer cw, Light light, boolean state) {
        FCWSetSwitch(cw, 0, light.index, state ? 1 : 0);
    }

    default void turnOnColor(Pointer cw, Light light, boolean turnOthersOff) {
        FCWSetSwitch(cw, 0, light.index, 1);
        if (turnOthersOff) {
            Arrays.stream(Light.values()).filter(x -> x != light).forEach(x -> {
                FCWSetSwitch(cw, 0, x.index, 0);
            });
        }
    }

    default void turnOffColor(Pointer cw, Light light) {
        FCWSetSwitch(cw, 0, light.index, 0);
    }

    default void turnAllOff(Pointer cw) {
        Arrays.stream(Light.values()).forEach(x -> {
            FCWSetSwitch(cw, 0, x.index, 0);
        });
    }

}
