package de.sist.gitlab.notifier;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

public interface IncompleteConfigListener extends EventListener {

    Topic<IncompleteConfigListener> CONFIG_INCOMPLETE = Topic.create("Config incomplete", IncompleteConfigListener.class);

    void handleIncompleteConfig(String message);
}
