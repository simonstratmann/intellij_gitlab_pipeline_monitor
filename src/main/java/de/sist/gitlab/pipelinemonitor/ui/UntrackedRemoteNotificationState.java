package de.sist.gitlab.pipelinemonitor.ui;

import com.intellij.util.messages.Topic;

public interface UntrackedRemoteNotificationState {

    Topic<UntrackedRemoteNotificationState> UNTRACKED_REMOTE_FOUND = Topic.create("Untracked remote found", UntrackedRemoteNotificationState.class);

    void handle(boolean isOpen);

}
