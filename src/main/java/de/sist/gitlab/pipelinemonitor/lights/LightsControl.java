package de.sist.gitlab.pipelinemonitor.lights;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.pipelinemonitor.PipelineJobStatus;
import de.sist.gitlab.pipelinemonitor.ReloadListener;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LightsControl {

    private static final Logger logger = Logger.getInstance(LightsControl.class);
    private static LightsApi lightsApi;

    private final Project project;
    private final Set<PipelineJobStatus> handledRuns = new HashSet<>();


    public LightsControl(Project project) {
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
            logger.debug("Determined OS to be windows");
            lightsApi = project.getService(LightsWindows.class);
        } else if (osName.toLowerCase().contains("nux")) {
            logger.debug("Determined OS to be linux");
            lightsApi = project.getService(LightsLinux.class);
        } else if (osName.toLowerCase().contains("mac")) {
            logger.debug("Determined OS to be Mac OS X. Not supported");
            return;
        } else {
            logger.error("Unable to determine OS from property " + osName);
            return;
        }

        String lightsForBranch = ConfigProvider.getInstance().getShowLightsForBranch(project);
        if (!Strings.isNullOrEmpty(lightsForBranch)) {
            //Only subscribe if a branch should be watched
            project.getMessageBus().connect().subscribe(ReloadListener.RELOAD, (ReloadListener) pipelineInfos -> ApplicationManager.getApplication().invokeLater(() -> showState(pipelineInfos)));
        }
    }

    public void showState(Map<Mapping, List<PipelineJobStatus>> statuses) {
        String lightsForBranch = ConfigProvider.getInstance().getShowLightsForBranch(project);
        if (Strings.isNullOrEmpty(lightsForBranch)) {
            //IntelliJ doesn't seem to allow to unsubscribe from an event, so if the user changed the config not to watch a branch this will still be called
            logger.debug("No branch to watch lights for set");
            return;
        }
        if (lightsApi == null) {
            return;
        }
        String projectId = null;
        if (lightsForBranch.contains(";")) {
            final String[] split = lightsForBranch.split(";");
            lightsForBranch = split[0];
            projectId = split[1];
        }

        Optional<PipelineJobStatus> status = Optional.empty();
        for (Map.Entry<Mapping, List<PipelineJobStatus>> entry : statuses.entrySet()) {
            if (projectId != null && !projectId.equals(entry.getKey().getGitlabProjectId())) {
                continue;
            }
            for (PipelineJobStatus pipelineJobStatus : entry.getValue()) {
                if (pipelineJobStatus.branchName.equals(lightsForBranch)) {
                    status = Optional.of(pipelineJobStatus);
                    break;
                }
            }
        }


        if (status.isPresent()) {
            //Don't enable any lights twice so that when the user turned the light off it doesn't get turned on again for the same run
            if (handledRuns.contains(status.get())) {
                logger.debug("Already shown light for ", status.get());
                return;
            }

            //When showing yellow don't turn off red or green so that the info is still kept visible even after a new run has started
            String result = status.get().result;
            switch (result) {
                case "running":
                    logger.debug("Showing build state light for running pipeline ", status.get());
                    lightsApi.turnOnColor(LightsWindowsLibrary.Light.YELLOW, false);
                    break;
                case "failed":
                    logger.debug("Showing failure pipeline ", status.get());
                    lightsApi.turnOnColor(LightsWindowsLibrary.Light.RED, true);
                    break;
                case "success":
                    logger.debug("Showing success for pipeline ", status.get());
                    lightsApi.turnOnColor(LightsWindowsLibrary.Light.GREEN, true);
                    break;
            }
            handledRuns.add(status.get());
        } else {
            logger.debug("No pipeline found for ", lightsForBranch);
            lightsApi.turnAllOff();
        }
    }

    public static void turnOffAllLights() {
        if (lightsApi == null) {
            return;
        }
        logger.debug("Turning off all lights");
        lightsApi.turnOffColor(LightsWindowsLibrary.Light.RED);
        lightsApi.turnOffColor(LightsWindowsLibrary.Light.YELLOW);
        lightsApi.turnOffColor(LightsWindowsLibrary.Light.GREEN);
    }

    @SuppressWarnings("UnstableApiUsage")
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
                Resources.asByteSource(LightsWindowsLibrary.class.getResource("/" + resourceName)).copyTo(Files.asByteSink(file));
            } catch (IOException e) {
                logger.error(e);
                return null;
            }
        }
        return file;
    }

    public static File getPluginPath() {
        File systemPath = new File(PathManager.getSystemPath());
        final File gitlabPipelineViewerPath = new File(systemPath, "gitlabPipelineViewer");
        logger.debug("Determined plugin storage path to be ", gitlabPipelineViewerPath);
        return gitlabPipelineViewerPath;
    }

}
