package de.sist.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import git4idea.GitReference;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GitlabService {

    Logger logger = Logger.getInstance(GitlabService.class);
    private static final String PIPELINE_URL = "https://172.29.151.142/api/v4/projects/210/pipelines?per_page=100";
    private final HttpClientService httpClient;
    private GitRepository currentRepository;

    public GitlabService(Project project) {
        httpClient = ServiceManager.getService(project, HttpClientService.class);
    }

    public void setCurrentRepository(GitRepository currentRepository) {
        //Must be set initially because reading it prevents the background process from working
        this.currentRepository = currentRepository;
    }

    public List<PipelineJobStatus> getStatuses() throws IOException {
        if (currentRepository == null) {
            //Should've been caught before and we never should've landed here
            logger.error("No current git repository found");
            return Collections.emptyList();
        }

        GitBranchesCollection branches = currentRepository.getBranches();
        Set<String> trackedBranches = branches.getLocalBranches().stream().filter(x -> branches.getRemoteBranches().stream().anyMatch(remote -> remote.getNameForRemoteOperations().equals(x.getName()))).map(GitReference::getName).collect(Collectors.toSet());

        logger.debug("Determined tracked branches: " + trackedBranches);


        List<PipelineTo> pipelines;
        try {
            pipelines = getPipelines();
        } catch (IOException e) {
            logger.error("Unable to connect to gitlab", e);
            throw e;
        }
        Set<PipelineTo> pipelinesMatchingTrackedBranches = pipelines.stream().filter(x -> trackedBranches.contains(x.getRef())).collect(Collectors.toSet());

        List<PipelineJobStatus> statuses = new ArrayList<>();
        for (PipelineTo pipeline : pipelinesMatchingTrackedBranches.stream().sorted(Comparator.comparing(PipelineTo::getUpdatedAt).reversed()).collect(Collectors.toList())) {
            statuses.add(new PipelineJobStatus(pipeline.getRef(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(), pipeline.getStatus(), pipeline.getWebUrl()));
        }

        logger.debug("Found statuses matching tracked pipelines: " + statuses);

        return statuses;
    }

    public List<PipelineTo> getPipelines() throws IOException {
        Call call = httpClient.getClient().newCall(new Request.Builder().url(PIPELINE_URL).build());
        Response response = call.execute();
        if (!response.isSuccessful()) {
            logger.error("Error contacting gitlab: " + response);
            throw new IOException("Error loading pipelines: " + response);
        }
        try (ResponseBody body = response.body()) {
            if (body == null) {
                throw new IOException("Empty body: " + response);
            }
            String json = body.string();

            return Jackson.OBJECT_MAPPER.readValue(json, new TypeReference<List<PipelineTo>>() {
            });
        }

    }

}
