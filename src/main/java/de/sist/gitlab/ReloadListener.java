package de.sist.gitlab;

import com.intellij.util.messages.Topic;

import java.util.EventListener;
import java.util.List;

public interface ReloadListener extends EventListener {

    Topic<ReloadListener> RELOAD = Topic.create("Reload triggered", ReloadListener.class);

    void reload(List<PipelineJobStatus> statuses);

}
