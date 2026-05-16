// (C) 2022 PPI AG
package de.sist.gitlab.pipelinemonitor.debug;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import de.sist.gitlab.pipelinemonitor.PipelineJobStatus;
import de.sist.gitlab.pipelinemonitor.lights.LightsApi;
import de.sist.gitlab.pipelinemonitor.lights.LightsControl;
import de.sist.gitlab.pipelinemonitor.lights.LightsLinux;
import de.sist.gitlab.pipelinemonitor.notifier.NotifierService;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;

/**
 * @author PPI AG
 */
public class ShowLightsAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        LightsLinux lightsLinux = e.getProject().getService(LightsLinux.class);
        lightsLinux.initialize();
        lightsLinux.turnAllOff();
        lightsLinux.turnOnColor(LightsApi.Light.RED, true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        if (System.getProperty("gitlabPipelineViewerDebugging") == null) {
            e.getPresentation().setVisible(false);
        }
        e.getPresentation().setVisible(true);
    }
}
