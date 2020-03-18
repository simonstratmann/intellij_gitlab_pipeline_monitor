package de.sist.gitlab.lights;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.PipelineJobStatus;
import de.sist.gitlab.ReloadListener;
import de.sist.gitlab.config.PipelineViewerConfig;
import jdk.internal.joptsimple.internal.Strings;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LightControl {

    private static final Logger logger = Logger.getInstance(LightControl.class);
    private static LightsApi lightsApi;

    private final Project project;
    private final Set<PipelineJobStatus> handledRuns = new HashSet<>();


    public LightControl(Project project) {
        this.project = project;

        initialize(project);
    }

    public void initialize(Project project) {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            logger.error("Unable to determine OS from empty os.name property");
            return;
        }
        if (osName.toLowerCase().contains("windows")) {
            lightsApi = project.getService(LightsWindows.class);
        } else if (osName.toLowerCase().contains("nux")) {
            lightsApi = project.getService(LightsLinux.class);
        } else {
            logger.error("Unable to determine OS from property " + osName);
            return;
        }

        String lightsForBranch = PipelineViewerConfig.getInstance(project).getShowLightsForBranch();
        if (!Strings.isNullOrEmpty(lightsForBranch)) {
            //Only subscribe if a branch should be watched
            project.getMessageBus().connect().subscribe(ReloadListener.RELOAD, statuses -> ApplicationManager.getApplication().invokeLater(() -> showState(statuses)));
        }
    }

    public void showState(List<PipelineJobStatus> statuses) {
        String lightsForBranch = PipelineViewerConfig.getInstance(project).getShowLightsForBranch();
        if (Strings.isNullOrEmpty(lightsForBranch)) {
            //IntelliJ doesn't seem to allow to unsubscribe from an event, so if the user changed the config not to watch a branch this will still be called
            logger.debug("No branch to watch lights for set");
            return;
        }
        if (lightsApi == null) {
            return;
        }

        Optional<PipelineJobStatus> status = statuses.stream()
                .filter(x -> x.branchName.equals(lightsForBranch))
                .findFirst();

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
                    lightsApi.turnOnColor(LightsWindowsLibrary.Light.YELLOW, false);
                    break;
                case "failed":
                    logger.debug("Showing red light for failed pipeline " + status.get());
                    lightsApi.turnOnColor(LightsWindowsLibrary.Light.RED, false);
                    lightsApi.turnOffColor(LightsWindowsLibrary.Light.GREEN);
                    break;
                case "success":
                    logger.debug("Showing green light for successful pipeline " + status.get());
                    lightsApi.turnOnColor(LightsWindowsLibrary.Light.GREEN, false);
                    lightsApi.turnOffColor(LightsWindowsLibrary.Light.RED);
                    break;
            }
            handledRuns.add(status.get());
        } else {
            logger.debug("No pipeline found for " + lightsForBranch);
            lightsApi.turnAllOff();
        }
    }

    public static void turnOffAllLights() {
        if (lightsApi == null) {
            return;
        }
        lightsApi.turnOffColor(LightsWindowsLibrary.Light.RED);
        lightsApi.turnOffColor(LightsWindowsLibrary.Light.YELLOW);
        lightsApi.turnOffColor(LightsWindowsLibrary.Light.GREEN);
    }

    public static File loadResource(String resourceName) {
        File pluginPath = getPluginPath();
        if (!pluginPath.exists()) {
            boolean folderCreated = pluginPath.mkdir();
            if (!folderCreated) {
                logger.error("Unable to create folder " + pluginPath);
                return null;
            }
        }
        File file = new File(pluginPath, resourceName);
        if (!file.exists()) {
            try {
                FileUtils.copyInputStreamToFile(LightsWindowsLibrary.class.getResourceAsStream("/" + resourceName), file);
            } catch (IOException e) {
                logger.error(e);
                return null;
            }
        }
        return file;
    }

    public static File getPluginPath() {
        File systemPath = new File(PathManager.getSystemPath());
        return new File(systemPath, "gitlabPipelineViewer");
    }

}
