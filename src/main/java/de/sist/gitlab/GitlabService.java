package de.sist.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import de.sist.gitlab.config.ConfigChangedListener;
import de.sist.gitlab.config.ConfigProvider;
import de.sist.gitlab.config.Mapping;
import de.sist.gitlab.config.PipelineViewerConfigProject;
import de.sist.gitlab.git.GitService;
import de.sist.gitlab.ui.UnmappedRemoteDialog;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GitlabService {

    private static final Logger logger = Logger.getInstance(GitlabService.class);

    private static final Pattern REMOTE_GIT_SSH_PATTERN = Pattern.compile("git@(?<host>.*):(?<projectPath>.*)\\.git");
    private static final Pattern REMOTE_GIT_HTTP_PATTERN = Pattern.compile("(?<scheme>https?:\\/\\/)(?<url>.*)\\.git");
    private static final String ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE = CredentialAttributesKt.generateServiceName("GitlabService", "accessToken");
    private static final List<String> INCOMPATIBLE_REMOTES = Arrays.asList("github.com", "bitbucket.com");

    private final ConfigProvider config = ServiceManager.getService(ConfigProvider.class);
    private final Project project;
    private final Map<Mapping, List<PipelineJobStatus>> pipelineInfos = new HashMap<>();


    public GitlabService(Project project) {
        this.project = project;
    }

    public void updatePipelineInfos() throws IOException {
        synchronized (pipelineInfos) {

            final Map<Mapping, List<PipelineJobStatus>> newMappingToPipelines = new HashMap<>();
            for (Map.Entry<Mapping, List<PipelineTo>> entry : getPipelines().entrySet()) {
                final List<PipelineJobStatus> jobStatuses = entry.getValue().stream()
                        .map(pipeline -> new PipelineJobStatus(pipeline.getRef(), entry.getKey().getGitlabProjectId(), pipeline.getCreatedAt(), pipeline.getUpdatedAt(), pipeline.getStatus(), pipeline.getWebUrl()))
                        .sorted(Comparator.comparing(PipelineJobStatus::getUpdateTime, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                        .collect(Collectors.toList());
                newMappingToPipelines.put(entry.getKey(), jobStatuses);
            }
            pipelineInfos.clear();
            pipelineInfos.putAll(newMappingToPipelines);
        }
    }

    public Map<Mapping, List<PipelineJobStatus>> getPipelineInfos() {
        synchronized (pipelineInfos) {
            return pipelineInfos;
        }
    }

    public synchronized void checkForUnmappedRemotes(List<GitRepository> gitRepositories) {
        ConfigProvider.getInstance().aquireLock();
        logger.debug("Checking for unmapped remotes");
        final Set<String> handledMappings = new HashSet<>();
        for (GitRepository gitRepository : gitRepositories) {
            for (GitRemote remote : gitRepository.getRemotes()) {
                for (String url : remote.getUrls()) {
                    if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
                        //Make sure no further remotes are processed if multiple are found and the user chose to disable for the project
                        return;
                    }
                    if (ConfigProvider.getInstance().getIgnoredRemotes().contains(url)) {
                        continue;
                    }
                    if (INCOMPATIBLE_REMOTES.stream().anyMatch(x -> url.toLowerCase().contains(x))) {
                        continue;
                    }
                    if (!handledMappings.contains(url)) {
                        if (config.getMappingByRemoteUrl(url) == null) {
                            handleUnknownRemote(url);
                        }
                        handledMappings.add(url);
                    }
                }
            }
        }
    }

    private void handleUnknownRemote(String url) {
        logger.info("Found unkown remote " + url);

        final UnmappedRemoteDialog.Response response = new UnmappedRemoteDialog(url).showDialog();

        if (response.getCancel() == UnmappedRemoteDialog.Cancel.IGNORE_REMOTE) {
            ConfigProvider.getInstance().getIgnoredRemotes().add(url);
            logger.info("Added " + url + " to list of ignored remotes");
            return;
        } else if (response.getCancel() == UnmappedRemoteDialog.Cancel.IGNORE_PROJECT) {
            PipelineViewerConfigProject.getInstance(project).setEnabled(false);
            logger.info("Disabling pipeline viewer for project " + project.getName());
            ApplicationManager.getApplication().getMessageBus().syncPublisher(ConfigChangedListener.CONFIG_CHANGED).configChanged();
            return;
        } else if (response.getCancel() == UnmappedRemoteDialog.Cancel.ASK_AGAIN) {
            logger.debug("User chose to be asked again about url " + url);
            return;
        }

        final Mapping mapping = new Mapping();
        mapping.setRemote(url);

        if (!Strings.isNullOrEmpty(response.getAccessToken())) {
            logger.info("Saved access token for URL " + url);
            PasswordSafe.getInstance().set(new CredentialAttributes(ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE, url), new Credentials(url, response.getAccessToken()));
        }

        //Retrieve host and project path
        fillMappingWithHostAndProjectPath(url, mapping);
        if (mapping.getHost() == null) {
            return;
        }

        //Retrieve project ID and path
        fillMappingWithProjectNameAndId(url, mapping);
        if (mapping.getProjectName() == null) {
            return;
        }
        logger.info("Adding mapping " + mapping);
        ConfigProvider.getInstance().getMappings().add(mapping);
    }

    @SuppressWarnings("unchecked")
    private void fillMappingWithProjectNameAndId(String url, Mapping mapping) {
        final String graphQlUrl = mapping.getHost() + "/api/graphql";

        final String graphQlQuery = String.format("{\"query\": \"query {  project(fullPath:\\\"%s\\\") {    name    id  }}\"}", mapping.getProjectPath());
        try {
            final String accessToken = PasswordSafe.getInstance().getPassword(new CredentialAttributes(ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE, mapping.getRemote()));

            final Map<String, Object> graphQlResponse = HttpRequests.post(graphQlUrl, "application/json")
                    .connect(request -> {
                        if (accessToken != null) {
                            request.getConnection().setRequestProperty("Authorization", "Bearer " + accessToken);
                        }
                        request.write(graphQlQuery);
                        return Jackson.OBJECT_MAPPER.readValue(request.getInputStream(), new TypeReference<Map<String, Object>>() {
                        });
                    });
            final Map<String, Object> data = (Map<String, Object>) graphQlResponse.get("data");
            final Map<String, Object> project = (Map<String, Object>) data.get("project");
            final String name = (String) project.get("name");
            String id = (String) project.get("id");
            if (id.startsWith("gid://")) {
                id = id.substring(id.lastIndexOf("/") + 1);
            }
            logger.info("Determined project name " + name + " and id " + id + " for remote " + url);
            mapping.setGitlabProjectId(id);
            mapping.setProjectName(name);
        } catch (
                IOException e) {
            logger.error("Error reading project data using URL " + graphQlUrl + " and query " + graphQlQuery);
        }
    }

    private void fillMappingWithHostAndProjectPath(String url, Mapping mapping) {
        final Optional<HostAndProjectPath> hostProjectPathFromRemote = getHostProjectPathFromRemote(url);
        if (!hostProjectPathFromRemote.isPresent()) {
            final String input = Messages.showInputDialog(project, "Unable to determine host and project path from the URL. Please enter them in the format '<host>;<projectPath>' (e.g. 'https://gitlab.com;user/project').", "Gitlab Pipeline Viewer", null, url, null);
            if (input == null || !input.contains(";")) {
                logger.error("User didn't prove host and project path for remote " + url);
                return;
            }
            final String[] split = input.split(";");
            mapping.setHost(split[0]);
            mapping.setProjectPath(split[1]);
        } else {
            mapping.setHost(hostProjectPathFromRemote.get().getHost());
            mapping.setProjectPath(hostProjectPathFromRemote.get().getProjectPath());
        }
    }

    private Map<Mapping, List<PipelineTo>> getPipelines() throws IOException {
        final Map<Mapping, List<PipelineTo>> projectToPipelines = new HashMap<>();
        for (GitRepository nonIgnoredRepository : GitService.getInstance(project).getNonIgnoredRepositories()) {
            for (GitRemote remote : nonIgnoredRepository.getRemotes()) {
                for (String url : remote.getUrls()) {
                    final Mapping mapping = ConfigProvider.getInstance().getMappingByRemoteUrl(url);
                    if (mapping == null) {
                        continue;
                    }
                    logger.debug("Loading pipelines for remote " + mapping.getRemote());
                    List<PipelineTo> pipelines = makePipelinesUrlCall(1, mapping);
                    pipelines.addAll(makePipelinesUrlCall(2, mapping));
                    logger.debug("Loaded " + pipelines.size() + " pipelines for remote " + mapping.getRemote());

                    projectToPipelines.put(mapping, pipelines);
                }
            }
        }

        return projectToPipelines;
    }

    private List<PipelineTo> makePipelinesUrlCall(int page, Mapping mapping) throws IOException {
        String url;
        try {
            URIBuilder uriBuilder = new URIBuilder(mapping.getHost() + "/api/v4/projects/" + mapping.getGitlabProjectId() + "/pipelines");

            uriBuilder.addParameter("page", String.valueOf(page))
                    .addParameter("per_page", "100");
            final String accessToken = PasswordSafe.getInstance().getPassword(new CredentialAttributes(ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE, mapping.getRemote()));
            if (accessToken != null) {
                logger.debug("Using access tokeen for access to " + uriBuilder);
                uriBuilder.addParameter("private_token", accessToken);
            }

            url = uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String json = null;
        try {
            json = HttpRequests.request(url).readString();
        } catch (HttpRequests.HttpStatusException e) {
            if (e.getStatusCode() == 401) {
                logger.error("Unable to login to url " + url + ". Deleting saved access token");
                PasswordSafe.getInstance().setPassword(new CredentialAttributes(ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE, mapping.getRemote()), null);
            }
        }

        return Jackson.OBJECT_MAPPER.readValue(json, new TypeReference<List<PipelineTo>>() {
        });
    }

    public String getGitlabHtmlBaseUrl(String projectId) {
        final Mapping mapping = ConfigProvider.getInstance().getMappingByProjectId(projectId);
        return mapping.getHost() + "/" + mapping.getProjectPath();
    }

    protected static Optional<HostAndProjectPath> getHostProjectPathFromRemote(String remote) {
        final Matcher sshMatcher = REMOTE_GIT_SSH_PATTERN.matcher(remote);
        if (sshMatcher.matches()) {
            final HostAndProjectPath hostAndProjectPath = new HostAndProjectPath(sshMatcher.group("host"), sshMatcher.group("projectPath"));
            logger.info("Determined host " + hostAndProjectPath.getHost() + " and project path " + hostAndProjectPath.getProjectPath() + " from ssh remote " + remote);
            return Optional.of(hostAndProjectPath);
        }
        final Matcher httpMatcher = REMOTE_GIT_HTTP_PATTERN.matcher(remote);
        if (httpMatcher.matches()) {
            if (remote.startsWith("https://gitlab.com")) {
                return Optional.of(new HostAndProjectPath("https://gitlab.com", remote.substring("https://gitlab.com".length())));
            }
            //For self hosted instances it's impossible to determine which part of the path is part of the host or the project.
            //So we try each part of the path and see if we get a response that looks like gitlab
            final String fullUrl = httpMatcher.group("url");
            String testUrl = httpMatcher.group("scheme");
            for (String part : fullUrl.split("/")) {
                testUrl += part + "/";
                final RequestBuilder request = HttpRequests.request(testUrl);

                final String response;
                try {
                    response = request.readString();
                } catch (IOException e) {
                    logger.error("Unable to retrieve host and project path from remote " + remote, e);
                    return Optional.empty();
                }
                if (response.toLowerCase().contains("gitlab")) {
                    final HostAndProjectPath hostAndProjectPath = new HostAndProjectPath(testUrl, fullUrl.substring(testUrl.length()));
                    logger.info("Determined host " + hostAndProjectPath.getHost() + " and project path " + hostAndProjectPath.getProjectPath() + " from http remote " + remote);
                    return Optional.of(hostAndProjectPath);
                }
            }
        }
        logger.error("Unable to parse remote " + remote);
        return Optional.empty();
    }

}
