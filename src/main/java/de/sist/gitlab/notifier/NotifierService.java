package de.sist.gitlab.notifier;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.awt.RelativePoint;
import de.sist.gitlab.DateTime;
import de.sist.gitlab.PipelineJobStatus;
import de.sist.gitlab.ReloadListener;
import de.sist.gitlab.StatusFilter;
import de.sist.gitlab.config.GitlabConfigurable;
import org.jetbrains.annotations.NotNull;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NotifierService {

    private static final Logger logger = Logger.getInstance(NotifierService.class);

    private static final List<String> KNOWN_STATUSES = Arrays.asList("pending", "running", "canceled", "failed", "success", "skipped");
    private final StatusFilter statusFilter;
    private final Project project;

    private List<Balloon> openBalloons = new ArrayList<>();

    private Set<PipelineJobStatus> shownNotifications;

    private final Map<String, String> statusesToNotificationGroupIds = new HashMap<>();
    private final NotificationGroup errorNotificationGroup;

    public NotifierService(Project project) {
        this.project = project;
        statusFilter = project.getService(StatusFilter.class);

        KNOWN_STATUSES.forEach(x -> {
            statusesToNotificationGroupIds.put(x, createNotificationGroupForStatus(x).getDisplayId());
        });

        errorNotificationGroup = createNotificationGroup("GitLab Pipeline Viewer - Error", NotificationDisplayType.STICKY_BALLOON);

        project.getMessageBus().connect().subscribe(ReloadListener.RELOAD, this::showStatusNotifications);
        project.getMessageBus().connect().subscribe(IncompleteConfigListener.CONFIG_INCOMPLETE, this::showIncompleteConfigNotification);
    }

    public void showError(String error) {
        Notification notification = errorNotificationGroup.createNotification(error, NotificationType.ERROR);
        Notifications.Bus.notify(notification, project);
    }

    private void showStatusNotifications(List<PipelineJobStatus> statuses) {
        enableDebugModeIfApplicable();
        if (shownNotifications == null) {
            //Don't show notifications for pipeline statuses from before the program was started
            shownNotifications = new HashSet<>(statuses);
            return;
        }

        List<PipelineJobStatus> filteredStatuses = statusFilter.filterPipelines(statuses)
                .stream().filter(x ->
                        !shownNotifications.contains(x)
                                && getDisplayTypeForStatus(x.result) != NotificationDisplayType.NONE
                ).collect(Collectors.toList());
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
    }

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
        if (Stream.of("failed", "canceled", "skipped").anyMatch(s -> status.result.equals(s))) {
            notificationType = NotificationType.ERROR;
        } else {
            notificationType = NotificationType.INFORMATION;
        }

        if (getDisplayTypeForStatus(status.result) == NotificationDisplayType.TOOL_WINDOW) {
            Notifications.Bus.notify(notificationGroup.createNotification(status.branchName + ": " + status.result, notificationType));
            return;
        }
        content = status.branchName + ": <span style=\"color:" + getColorForStatus(status.result) + "\">" + status.result + "</span>"
                + "<br>Created: " + DateTime.formatDateTime(status.creationTime)
                + "<br>Last update: " + DateTime.formatDateTime(status.updateTime);

        Notification notification = notificationGroup.createNotification("GitLab branch status", null, content, notificationType);


        notification.addAction(new NotificationAction("Open in Browser") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                com.intellij.ide.BrowserUtil.browse(status.pipelineLink);
                notification.expire();
            }
        });

        logger.debug("Showing notification for status " + status);
        showBalloon(notification, getDisplayTypeForStatus(status.result), index);
        shownNotifications.add(status);
    }

    private NotificationGroup getNotificationGroupForStatus(PipelineJobStatus status) {
        if (!statusesToNotificationGroupIds.containsKey(status.result)) {
            statusesToNotificationGroupIds.put(status.result, createNotificationGroupForStatus(status.result).getDisplayId());
        }

        NotificationGroup notificationGroup = NotificationGroup.findRegisteredGroup(statusesToNotificationGroupIds.get(status.result));
        if (notificationGroup == null) {
            String message = "Unable to find registered notification group for status " + status.result;
            logger.error(message);
            throw new RuntimeException(message);
        }
        return notificationGroup;
    }

    private void showIncompleteConfigNotification(String message) {

        Notification notification = errorNotificationGroup.createNotification("GitLab pipeline viewer", message, NotificationType.ERROR, null);
        notification.addAction(new NotificationAction("Open Settings") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                notification.hideBalloon();
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GitlabConfigurable.class);
            }
        });
        Notifications.Bus.notify(notification, project);
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
    private NotificationGroup createNotificationGroupForStatus(String status) {
        String displayId = getDisplayIdForStatus(status);
        return createNotificationGroup(displayId, NotificationDisplayType.BALLOON);
    }

    @NotNull
    private String getDisplayIdForStatus(String status) {
        return "GitLab Pipeline Viewer - status " + status;
    }

    private NotificationGroup createNotificationGroup(String displayId, NotificationDisplayType defaultDisplayType) {
        NotificationDisplayType displayType;
        boolean shouldLog;
        if (NotificationsConfigurationImpl.getInstanceImpl().isRegistered(displayId)) {
            NotificationSettings notificationSettings = NotificationsConfigurationImpl.getSettings(displayId);
            shouldLog = notificationSettings.isShouldLog();
            displayType = notificationSettings.getDisplayType();
        } else {
            displayType = defaultDisplayType;
            shouldLog = true;
        }
        return new NotificationGroup(displayId, displayType, shouldLog,
                "GitLab pipeline viewer", IconLoader.getIcon("/toolWindow/gitlab-icon.png"));
    }

    private NotificationDisplayType getDisplayTypeForStatus(String status) {
        return NotificationsConfigurationImpl.getSettings(getDisplayIdForStatus(status)).getDisplayType();
    }


}
