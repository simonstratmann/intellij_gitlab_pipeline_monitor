package de.sist.gitlab.pipelinemonitor.git;

import com.intellij.util.messages.Topic;
import git4idea.repo.GitRepository;

import java.util.List;

public interface GitInitListener {

    Topic<GitInitListener> GIT_INITIALIZED = Topic.create(".git initialized", GitInitListener.class);

    void handle(List<GitRepository> gitRepositories);

}
