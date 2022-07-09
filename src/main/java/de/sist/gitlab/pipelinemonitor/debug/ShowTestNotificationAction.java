// (C) 2022 PPI AG
package de.sist.gitlab.pipelinemonitor.debug;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import de.sist.gitlab.pipelinemonitor.PipelineJobStatus;
import de.sist.gitlab.pipelinemonitor.notifier.NotifierService;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;

/**
 * @author PPI AG
 */
public class ShowTestNotificationAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        e.getProject().getService(NotifierService.class).showBalloonForStatus(new PipelineJobStatus(1, "123", "123", ZonedDateTime.now(), ZonedDateTime.now(), "failed", "http://www.google.de", "source"), 0);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        if (System.getProperty("gitlabPipelineViewerDebugging") == null) {
            e.getPresentation().setVisible(false);
        }
        e.getPresentation().setVisible(true);
    }
}
