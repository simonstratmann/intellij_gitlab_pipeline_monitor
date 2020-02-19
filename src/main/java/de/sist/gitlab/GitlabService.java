package de.sist.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import de.sist.gitlab.config.PipelineViewerConfig;
import de.sist.gitlab.notifier.IncompleteConfigListener;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GitlabService {

    Logger logger = Logger.getInstance(GitlabService.class);
    private static final String PIPELINE_URL = "https://172.29.151.142/api/v4/projects/%d/pipelines?per_page=100";
    private final HttpClientService httpClient;
    private final PipelineViewerConfig config;
    private final Project project;

    public GitlabService(Project project) {
        this.project = project;
        httpClient = ServiceManager.getService(project, HttpClientService.class);
        config = project.getService(PipelineViewerConfig.class);
    }

    public List<PipelineJobStatus> getStatuses() throws IOException {
        List<PipelineTo> pipelines;
        try {
            pipelines = getPipelines();
        } catch (IOException e) {
            logger.error("Unable to connect to gitlab", e);
            throw e;
        }

        Set<PipelineJobStatus> statuses = new HashSet<>();
        for (PipelineTo pipeline : pipelines.stream().sorted(Comparator.comparing(PipelineTo::getUpdatedAt).reversed()).collect(Collectors.toList())) {
            statuses.add(new PipelineJobStatus(pipeline.getRef(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(), pipeline.getStatus(), pipeline.getWebUrl()));
        }

        return new ArrayList<>(statuses);
    }

    public List<PipelineTo> getPipelines() throws IOException {
        Integer gitlabProjectId = PipelineViewerConfig.getInstance(project).getGitlabProjectId();
        if (gitlabProjectId == null) {
            project.getMessageBus().syncPublisher(IncompleteConfigListener.CONFIG_INCOMPLETE).handleIncompleteConfig("Incomplete config - project ID not set");
            logger.info("Gitlab project ID not set");
        }
        String url = String.format(PIPELINE_URL, gitlabProjectId);
        List<PipelineTo> pipelines = makeUrlCall(url);
        pipelines.addAll(makeUrlCall(url + "&page=2"));
        return pipelines;
    }

    private List<PipelineTo> makeUrlCall(String pipelineUrl) throws IOException {
        Call call = httpClient.getClient().newCall(new Request.Builder().url(pipelineUrl).build());
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
