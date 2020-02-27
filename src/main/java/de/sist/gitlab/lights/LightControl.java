package de.sist.gitlab.lights;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import de.sist.gitlab.PipelineJobStatus;
import de.sist.gitlab.ReloadListener;
import de.sist.gitlab.config.PipelineViewerConfig;
import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Files;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LightControl {

    private final Logger logger = Logger.getInstance(LightControl.class);
    private static Pointer lightsPointer;
    private static LightsApi lightsApi;

    private final Project project;
    private final Set<PipelineJobStatus> handledRuns = new HashSet<>();


    public LightControl(Project project) {
        this.project = project;

        initialize(project);
    }

    public void initialize(Project project) {
        if (lightsApi != null) {
            return;
        }
        File temporaryFolder = Files.newTemporaryFolder();
        try {
            File dllFile = new File(temporaryFolder, "USBaccess.dll");
            FileUtils.copyInputStreamToFile(LightsApi.class.getResourceAsStream("/USBaccess.dll"), dllFile);
            System.setProperty("jna.library.path", temporaryFolder.getAbsolutePath());
            dllFile.deleteOnExit();
            lightsApi = Native.load(dllFile.getAbsolutePath(), LightsApi.class);
        } catch (IOException e) {
            logger.error(e);
            return;
        }

        lightsPointer = lightsApi.FCWInitObject();
        lightsApi.FCWOpenCleware(lightsPointer);

        project.getMessageBus().connect().subscribe(ReloadListener.RELOAD, statuses -> ApplicationManager.getApplication().invokeLater(() -> showState(statuses)));
    }

    public void showState(List<PipelineJobStatus> statuses) {
        String lightsForBranch = PipelineViewerConfig.getInstance(project).getShowLightsForBranch();
        if (lightsForBranch == null) {
            logger.debug("No branch to watch lights for set");
            return;
        }

        Optional<PipelineJobStatus> status = statuses.stream().filter(x -> x.branchName.equals(lightsForBranch)).findFirst();

        if (status.isPresent()) {
            //Don't enable any lights twice so that when the user turned the light off it doesn't get turned on again for the same run
            if (handledRuns.contains(status.get())) {
                logger.debug("Already shown light for " + status.get());
                return;
            }

            //When showing yellow don't turn off red or green so that the info is still kept visible even after a new run has started

            String result = status.get().result;
            switch (result) {
                case "running":
                    logger.debug("Showing yellow light for running pipeline " + status.get());
                    lightsApi.turnOnColor(lightsPointer, LightsApi.Light.YELLOW, false);
                    break;
                case "failed":
                    logger.debug("Showing red light for failed pipeline " + status.get());
                    lightsApi.turnOnColor(lightsPointer, LightsApi.Light.RED, false);
                    lightsApi.turnOffColor(lightsPointer, LightsApi.Light.GREEN);
                    break;
                case "success":
                    logger.debug("Showing green light for successful pipeline " + status.get());
                    lightsApi.turnOnColor(lightsPointer, LightsApi.Light.GREEN, false);
                    lightsApi.turnOffColor(lightsPointer, LightsApi.Light.RED);
                    break;
            }
            handledRuns.add(status.get());
        } else {
            logger.debug("No pipeline found for " + lightsForBranch);
            lightsApi.turnAllOff(lightsPointer);
        }
    }

    public static void turnOffAllLights() {
        lightsApi.turnOffColor(lightsPointer, LightsApi.Light.RED);
        lightsApi.turnOffColor(lightsPointer, LightsApi.Light.YELLOW);
        lightsApi.turnOffColor(lightsPointer, LightsApi.Light.GREEN);
    }

}
