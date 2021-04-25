package de.sist.gitlab;

import com.intellij.util.messages.Topic;
import de.sist.gitlab.config.Mapping;

import java.util.EventListener;
import java.util.List;
import java.util.Map;

public interface ReloadListener extends EventListener {

    Topic<ReloadListener> RELOAD = Topic.create("Reload triggered", ReloadListener.class);

    void reload(Map<Mapping, List<PipelineJobStatus>> pipelineInfos);

}
