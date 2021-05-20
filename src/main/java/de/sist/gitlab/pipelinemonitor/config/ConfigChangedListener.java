package de.sist.gitlab.pipelinemonitor.config;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

public interface ConfigChangedListener extends EventListener {

    Topic<ConfigChangedListener> CONFIG_CHANGED = Topic.create("Config changed", ConfigChangedListener.class);

    void configChanged();

}
