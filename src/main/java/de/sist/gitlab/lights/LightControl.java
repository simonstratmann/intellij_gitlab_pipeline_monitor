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
    private Pointer lightsPointer;
    private final Project project;
    private final Set<PipelineJobStatus> handledRuns = new HashSet<>();

    private LightsApi lightsApi;


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
            return;
        }

        Optional<PipelineJobStatus> status = statuses.stream().filter(x -> x.branchName.equals(lightsForBranch)).findFirst();

        if (status.isPresent()) {
            //Don't enable any lights twice so that when the user turned the light off it doesn't get turned on again for the same run
            if (handledRuns.contains(status.get())) {
                return;
            }
            String result = status.get().result;
            switch (result) {
                case "running":
                    lightsApi.showColor(lightsPointer, LightsApi.Light.YELLOW);
                    break;
                case "failed":
                    lightsApi.showColor(lightsPointer, LightsApi.Light.RED);
                    break;
                case "success":
                    lightsApi.showColor(lightsPointer, LightsApi.Light.GREEN);
                    break;
            }
            handledRuns.add(status.get());
        } else {
            lightsApi.turnOff(lightsPointer);
        }

    }

}
