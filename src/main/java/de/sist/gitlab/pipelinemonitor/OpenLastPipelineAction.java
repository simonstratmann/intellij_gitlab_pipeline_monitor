
package de.sist.gitlab.pipelinemonitor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 */
public class OpenLastPipelineAction extends AnAction {

    Logger logger = Logger.getInstance(OpenLastPipelineAction.class);


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) {
            logger.error("No project in event " + e);
            return;
        }
        final PipelineJobStatus latestShown = ServiceManager.getService(project, PipelineFilter.class).getLatestShown();
        if (latestShown != null) {
            logger.info("Opening URL " + latestShown.getPipelineLink());
            UrlOpener.openUrl(latestShown.getPipelineLink());
        }
    }
}
