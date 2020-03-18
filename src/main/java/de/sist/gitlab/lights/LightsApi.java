package de.sist.gitlab.lights;

@SuppressWarnings("unused")
public interface LightsApi {

    enum Light {
        RED(0x10, 0),
        YELLOW(0x11, 1),
        GREEN(0x12, 2);

        int index;
        int indexLinux;

        Light(int index, int indexLinux) {
            this.index = index;
            this.indexLinux = indexLinux;
        }
    }

    void turnOnColor(Light light, boolean turnOthersOff);

    void turnOffColor(Light light);

    void turnAllOff();

}
