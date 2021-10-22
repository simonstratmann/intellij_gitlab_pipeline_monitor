package de.sist.gitlab.pipelinemonitor.notifier;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.awt.RelativePoint;
import de.sist.gitlab.pipelinemonitor.DateTime;
import de.sist.gitlab.pipelinemonitor.PipelineFilter;
import de.sist.gitlab.pipelinemonitor.PipelineJobStatus;
import de.sist.gitlab.pipelinemonitor.ReloadListener;
import de.sist.gitlab.pipelinemonitor.UrlOpener;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;
import de.sist.gitlab.pipelinemonitor.git.GitService;
import de.sist.gitlab.pipelinemonitor.lights.LightsControl;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class NotifierService {

    private static final Logger logger = Logger.getInstance(NotifierService.class);

    private static final List<String> KNOWN_STATUSES = Arrays.asList("pending", "running", "canceled", "failed", "success", "skipped");
    private final PipelineFilter statusFilter;
    private final Project project;

    private final List<Balloon> openBalloons = new ArrayList<>();
    private final GitService gitService;

    private Set<PipelineJobStatus> shownNotifications;

    private final NotificationGroup errorNotificationGroup;

    public NotifierService(Project project) {
        this.project = project;
        statusFilter = project.getService(PipelineFilter.class);

        errorNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("de.sist.gitlab.pipelinemonitor.error");

        project.getMessageBus().connect().subscribe(ReloadListener.RELOAD, this::showStatusNotifications);
        gitService = project.getService(GitService.class);
    }

    public void showError(String error) {
        Notification notification = errorNotificationGroup.createNotification(error, NotificationType.ERROR);
        Notifications.Bus.notify(notification, project);

    }

    private void showStatusNotifications(Map<Mapping, List<PipelineJobStatus>> mappingToPipelines) {
//        enableDebugModeIfApplicable();
        if (shownNotifications == null) {
            //Don't show notifications for pipeline statuses from before the program was started
            shownNotifications = mappingToPipelines.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
            return;
        }

        List<PipelineJobStatus> filteredStatuses = new ArrayList<>();
        for (Map.Entry<Mapping, List<PipelineJobStatus>> entry : mappingToPipelines.entrySet()) {
            statusFilter.filterPipelines(entry.getKey(), entry.getValue(), true)
                    .stream().filter(x ->
                    !shownNotifications.contains(x)
                            && getDisplayTypeForStatus(x.result) != NotificationDisplayType.NONE
            ).forEach(filteredStatuses::add);
        }
        //Don't spam the GUI, never show more than the newest 3
        List<PipelineJobStatus> statusesToShow = filteredStatuses.subList(Math.max(0, filteredStatuses.size() - 3), filteredStatuses.size());
        for (int i = 0; i < statusesToShow.size(); i++) {
            if (openBalloons.size() >= 3) {
                logger.debug("Hiding old balloon to show a newer one");
                openBalloons.get(0).hide();
            }

            PipelineJobStatus status = filteredStatuses.get(i);
            showBalloonForStatus(status, i);
        }
        //Mark the other ones as read so they aren't spammed later
        shownNotifications.addAll(statusesToShow);
    }

    @SuppressWarnings("unused")
    private void enableDebugModeIfApplicable() {
        logger.debug("Showing all notifications for developer");
        if ("strat".equals(System.getProperty("user.name"))) {
            shownNotifications = new HashSet<>();
        }
    }

    private void showBalloonForStatus(PipelineJobStatus status, int index) {
        NotificationGroup notificationGroup = getNotificationGroupForStatus(status);

        NotificationType notificationType;
        String content;
        if (List.of("failed", "canceled", "skipped").contains(status.result)) {
            notificationType = NotificationType.ERROR;
        } else {
            notificationType = NotificationType.INFORMATION;
        }

        if (getDisplayTypeForStatus(status.result) == NotificationDisplayType.TOOL_WINDOW) {
            Notifications.Bus.notify(notificationGroup.createNotification(status.getBranchNameDisplay() + ": " + status.result, notificationType));
            return;
        }
        content = status.getBranchNameDisplay() + ": <span style=\"color:" + getColorForStatus(status.result) + "\">" + status.result + "</span>"
                + "<br>Created: " + DateTime.formatDateTime(status.creationTime)
                + "<br>Last update: " + DateTime.formatDateTime(status.updateTime);
        if (gitService.getNonIgnoredRepositories().size() > 1) {
            content = ConfigProvider.getInstance().getMappingByProjectId(status.getProjectId()).getProjectName() + " " + content;
        }

        Notification notification = notificationGroup.createNotification("GitLab branch status", content, notificationType);

        notification.addAction(new NotificationAction("Open in Browser") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                UrlOpener.openUrl(status.pipelineLink);
                notification.expire();
                LightsControl.turnOffAllLights();
            }
        });

        logger.debug("Showing notification for status ", status);
        showBalloon(notification, getDisplayTypeForStatus(status.result), index);
        shownNotifications.add(status);
    }

    private NotificationGroup getNotificationGroupForStatus(PipelineJobStatus status) {
        if (KNOWN_STATUSES.contains(status.getResult())) {
            return NotificationGroupManager.getInstance().getNotificationGroup("de.sist.gitlab.pipelinemonitor.pipelineStatus." + status.result);
        }
        return NotificationGroupManager.getInstance().getNotificationGroup("de.sist.gitlab.pipelinemonitor.pipelineStatus.other");
    }

    private void showBalloon(Notification notification, NotificationDisplayType displayType, int index) {
        IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(project);
        if (ideFrame == null) {
            logger.error("ideFrame is null");
        } else {
            Rectangle bounds = ideFrame.getComponent().getBounds();

            boolean hideOnClickOutside = displayType != NotificationDisplayType.STICKY_BALLOON;
            Balloon balloon = NotificationsManagerImpl.createBalloon(ideFrame, notification, false, hideOnClickOutside, BalloonLayoutData.fullContent(), project);
            Dimension preferredSize = new Dimension(450, 100);
            ((BalloonImpl) balloon).getContent().setPreferredSize(preferredSize);

            //Show each balloon above the previous one and keep a bit of space between
            int lowerYBound = bounds.y + bounds.height - 111;
            lowerYBound -= index * 110;

            Point pointForRelativePosition = new Point(bounds.x + bounds.width - 259, lowerYBound);
            balloon.addListener(new JBPopupListener() {
                @Override
                public void onClosed(@NotNull LightweightWindowEvent event) {
                    openBalloons.remove(balloon);
                }
            });
            balloon.show(new RelativePoint(ideFrame.getComponent(), pointForRelativePosition), Balloon.Position.above);
            ((BalloonImpl) balloon).startFadeoutTimer(15_000);
            openBalloons.add(balloon);
        }
    }

    private String getColorForStatus(String result) {
        switch (result) {
            case "running":
                return "orange";
            case "pending":
                return "grey";
            case "success":
                return "green";
            case "failed":
                return "red";
            case "skipped":
            case "canceled":
                return "blue";
        }
        return "";

    }

    @NotNull
    private String getDisplayIdForStatus(String status) {
        return "GitLab Pipeline Viewer - status " + status;
    }


    private NotificationDisplayType getDisplayTypeForStatus(String status) {
        return NotificationsConfigurationImpl.getSettings(getDisplayIdForStatus(status)).getDisplayType();
    }


}
