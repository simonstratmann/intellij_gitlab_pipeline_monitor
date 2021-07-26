// (C) 2021 PPI AG
package de.sist.gitlab.pipelinemonitor.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import de.sist.gitlab.pipelinemonitor.BackgroundUpdateService;
import de.sist.gitlab.pipelinemonitor.HostAndProjectPath;
import de.sist.gitlab.pipelinemonitor.config.ConfigChangedListener;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigProject;
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * @author PPI AG
 */
public class UntrackedRemoteNotification extends Notification {

    private static final Logger logger = Logger.getInstance(GitlabService.class);

    private final String url;
    private final Project project;


    public UntrackedRemoteNotification(Project project, NotificationGroup notificationGroup, String url) {
        super(notificationGroup.getDisplayId(), "Untracked remote found", "The remote " + url + " is not tracked by Gitlab Pipeline Viewer.", NotificationType.INFORMATION);
        this.url = url;
        this.project = project;

        final AnAction openDialog = new AnAction("Choose How to Handle Remote") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openDialogForUnmappedRemote(url);
                expire();
            }
        };
        addAction(openDialog);
        final AnAction ignoreRemote = new AnAction("Ignore This Remote") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                logger.info("Ignoring remote " + url + " for project " + project.getName());
                ConfigProvider.getInstance().getIgnoredRemotes().add(url);
                expire();
            }
        };
        addAction(ignoreRemote);
    }

    private void openDialogForUnmappedRemote(String url) {
        logger.info("Showing dialog for untracked " + url);

        final Optional<HostAndProjectPath> hostProjectPathFromRemote = GitlabService.getHostProjectPathFromRemote(url);

        final UnmappedRemoteDialog.Response response;
        final Disposable disposable = Disposer.newDisposable();
        try {
            if (hostProjectPathFromRemote.isPresent()) {
                response = new UnmappedRemoteDialog(url, hostProjectPathFromRemote.get().getHost(), hostProjectPathFromRemote.get().getProjectPath(), disposable).showDialog();
            } else {
                response = new UnmappedRemoteDialog(url, disposable).showDialog();
            }

            if (response.getCancel() != UnmappedRemoteDialog.Cancel.ASK_AGAIN) {
                PipelineViewerConfigApp.getInstance().getRemotesAskAgainNextTime().remove(url);
            }
            if (response.getCancel() == UnmappedRemoteDialog.Cancel.IGNORE_REMOTE) {
                ConfigProvider.getInstance().getIgnoredRemotes().add(url);
                logger.info("Added " + url + " to list of ignored remotes");
                return;
            } else if (response.getCancel() == UnmappedRemoteDialog.Cancel.IGNORE_PROJECT) {
                PipelineViewerConfigProject.getInstance(project).setEnabled(false);
                logger.info("Disabling pipeline viewer for project " + project.getName());
                ApplicationManager.getApplication().getMessageBus().syncPublisher(ConfigChangedListener.CONFIG_CHANGED).configChanged();
                return;
            } else if (response.getCancel() == UnmappedRemoteDialog.Cancel.ASK_AGAIN) {
                logger.debug("User chose to be asked again about url ", url);
                PipelineViewerConfigApp.getInstance().getRemotesAskAgainNextTime().add(url);
                return;
            }

            final Mapping mapping = response.getMapping();

            logger.info("Adding mapping " + mapping);
            ConfigProvider.getInstance().getMappings().add(mapping);
            ServiceManager.getService(project, BackgroundUpdateService.class).update(project, false);
        } finally {
            Disposer.dispose(disposable);
        }
    }

    public String getUrl() {
        return url;
    }
}
