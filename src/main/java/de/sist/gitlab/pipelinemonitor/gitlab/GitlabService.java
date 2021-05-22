package de.sist.gitlab.pipelinemonitor.gitlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.io.HttpRequests;
import de.sist.gitlab.pipelinemonitor.BackgroundUpdateService;
import de.sist.gitlab.pipelinemonitor.HostAndProjectPath;
import de.sist.gitlab.pipelinemonitor.Jackson;
import de.sist.gitlab.pipelinemonitor.PipelineJobStatus;
import de.sist.gitlab.pipelinemonitor.PipelineTo;
import de.sist.gitlab.pipelinemonitor.config.ConfigChangedListener;
import de.sist.gitlab.pipelinemonitor.config.ConfigProvider;
import de.sist.gitlab.pipelinemonitor.config.Mapping;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigApp;
import de.sist.gitlab.pipelinemonitor.config.PipelineViewerConfigProject;
import de.sist.gitlab.pipelinemonitor.git.GitService;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.Data;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.Edge;
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.MergeRequest;
import de.sist.gitlab.pipelinemonitor.ui.UnmappedRemoteDialog;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public static final String ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE = CredentialAttributesKt.generateServiceName("GitlabService", "accessToken");

    private static final Logger logger = Logger.getInstance(GitlabService.class);

    private static final Pattern REMOTE_GIT_SSH_PATTERN = Pattern.compile("git@(?<host>.*):(?<projectPath>.*)(\\.git)?");
    private static final Pattern REMOTE_GIT_HTTP_PATTERN = Pattern.compile("(?<scheme>https?:\\/\\/)(?<url>.*)(\\.git)?");
    private static final List<String> INCOMPATIBLE_REMOTES = Arrays.asList("github.com", "bitbucket.com");

    private final ConfigProvider config = ServiceManager.getService(ConfigProvider.class);
    private final Project project;
    private final Map<Mapping, List<PipelineJobStatus>> pipelineInfos = new HashMap<>();
    private boolean isUnmappedRemotesDialogOpen;


    public GitlabService(Project project) {
        this.project = project;
    }

    public void updatePipelineInfos() throws IOException {
        synchronized (pipelineInfos) {

            final Map<Mapping, List<PipelineJobStatus>> newMappingToPipelines = new HashMap<>();
            for (Map.Entry<Mapping, List<PipelineTo>> entry : loadPipelines().entrySet()) {
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

    public void updateMergeRequests() {
        synchronized (pipelineInfos) {
            try {
                for (Map.Entry<Mapping, List<PipelineJobStatus>> entry : pipelineInfos.entrySet()) {

                    for (Mapping mapping : pipelineInfos.keySet()) {
                        logger.debug("Loading merge requests for remote " + mapping.getRemote());
                        final List<String> sourceBranches = entry.getValue().stream().map(x -> x.branchName).collect(Collectors.toList());
                        final Optional<Data> data = GraphQl.makeCall(mapping.getHost(), ConfigProvider.getToken(mapping), mapping.getProjectPath(), sourceBranches);
                        if (data.isPresent()) {
                            final List<MergeRequest> mergeRequests = data.get().getProject().getMergeRequests().getEdges().stream().map(Edge::getMergeRequest).collect(Collectors.toList());
                            logger.debug("Loaded " + mergeRequests.size() + " pipelines for remote " + mapping.getRemote());

                            final Map<String, List<MergeRequest>> mergeRequestsBySourceBranch = mergeRequests.stream().collect(Collectors.groupingBy(MergeRequest::getSourceBranch));
                            for (PipelineJobStatus pipelineJobStatus : pipelineInfos.get(mapping)) {
                                final List<MergeRequest> mergeRequestsForPipeline = mergeRequestsBySourceBranch.get(pipelineJobStatus.branchName);
                                if (mergeRequestsForPipeline != null && mergeRequestsForPipeline.size() > 0) {
                                    pipelineJobStatus.mergeRequestLink = mergeRequestsForPipeline.get(0).getWebUrl();
                                }
                            }
                        } else {
                            logger.debug("Unable to load pipelines for remote " + mapping.getRemote());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to load merge requests", e);
            }
        }
    }

    public Map<Mapping, List<PipelineJobStatus>> getPipelineInfos() {
        synchronized (pipelineInfos) {
            return pipelineInfos;
        }
    }


    public void checkForUnmappedRemotes(List<GitRepository> gitRepositories, boolean triggeredByUser) {
        //Locks don't work here for some reason
        if (isUnmappedRemotesDialogOpen) {
            return;
        }
        isUnmappedRemotesDialogOpen = true;
        try {
            ConfigProvider.getInstance().aquireLock();
            logger.debug("Checking for unmapped remotes");
            final Set<String> handledMappings = new HashSet<>();
            for (GitRepository gitRepository : gitRepositories) {
                for (GitRemote remote : gitRepository.getRemotes()) {
                    for (String url : remote.getUrls()) {
                        if (!PipelineViewerConfigProject.getInstance(project).isEnabled()) {
                            //Make sure no further remotes are processed if multiple are found and the user chose to disable for the project
                            logger.debug("Disabled for project " + project.getName());
                            return;
                        }
                        if (ConfigProvider.getInstance().getIgnoredRemotes().contains(url)) {
                            logger.debug("Remote " + url + " is ignored");
                            continue;
                        }
                        if (PipelineViewerConfigApp.getInstance().getRemotesAskAgainNextTime().contains(url) && !triggeredByUser) {
                            logger.debug("Remote " + url + " is ignored until next plugin load and reload was not triggered by user");
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
        } finally {
            isUnmappedRemotesDialogOpen = false;
        }
    }

    private void handleUnknownRemote(String url) {
        logger.info("Found unkown remote " + url);

        final Optional<HostAndProjectPath> hostProjectPathFromRemote = getHostProjectPathFromRemote(url);

        final UnmappedRemoteDialog.Response response;
        if (hostProjectPathFromRemote.isPresent()) {
            response = new UnmappedRemoteDialog(url, hostProjectPathFromRemote.get().getHost(), hostProjectPathFromRemote.get().getProjectPath()).showDialog();
        } else {
            response = new UnmappedRemoteDialog(url).showDialog();
        }

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
            PipelineViewerConfigApp.getInstance().getRemotesAskAgainNextTime().add(url);
            return;
        }

        final Mapping mapping = new Mapping();
        mapping.setRemote(url);
        mapping.setHost(response.getGitlabHost());
        mapping.setProjectPath(response.getProjectPath());
        if (!Strings.isNullOrEmpty(response.getAccessToken())) {
            logger.info("Saved access token for URL " + url);
            ConfigProvider.saveToken(mapping, response.getAccessToken());
        }

        //Retrieve project name and ID
        fillMappingWithProjectNameAndId(url, mapping);
        if (mapping.getProjectName() == null) {
            logger.info("Aborting with incomplete mapping " + mapping);
            return;
        }
        if (!mapping.isValid()) {
            logger.info("Aborting with incomplete mapping " + mapping);
            return;
        }

        logger.info("Adding mapping " + mapping);
        ConfigProvider.getInstance().getMappings().add(mapping);
    }

    private void fillMappingWithProjectNameAndId(String url, Mapping mapping) {
        final Optional<Data> data = GraphQl.makeCall(mapping.getHost(), ConfigProvider.getToken(mapping), mapping.getProjectPath(), Collections.emptyList());
        if (!data.isPresent()) {
            return;
        }

        final de.sist.gitlab.pipelinemonitor.gitlab.mapping.Project project = data.get().getProject();
        logger.info("Determined project name " + project.getName() + " and id " + project.getId() + " for remote " + url);
        mapping.setGitlabProjectId(project.getId());
        mapping.setProjectName(project.getName());
    }

    private Map<Mapping, List<PipelineTo>> loadPipelines() throws IOException {
        final Map<Mapping, List<PipelineTo>> projectToPipelines = new HashMap<>();
        for (GitRepository nonIgnoredRepository : GitService.getInstance(project).getNonIgnoredRepositories()) {
            for (GitRemote remote : nonIgnoredRepository.getRemotes()) {
                for (String url : remote.getUrls()) {
                    final Mapping mapping = ConfigProvider.getInstance().getMappingByRemoteUrl(url);
                    if (mapping == null) {
                        continue;
                    }
                    logger.debug("Loading pipelines for remote " + mapping.getRemote());
                    final List<PipelineTo> pipelines = loadPipelines(mapping);
                    logger.debug("Loaded " + pipelines.size() + " pipelines for remote " + mapping.getRemote());

                    projectToPipelines.put(mapping, pipelines);
                }
            }
        }

        return projectToPipelines;
    }

    private List<PipelineTo> loadPipelines(Mapping mapping) throws IOException {
        final List<PipelineTo> pipelines = new ArrayList<>();
        try {
            //Note: Gitlab GraphQL does not return the ref (branch name): https://gitlab.com/gitlab-org/gitlab/-/issues/230405
            pipelines.addAll(makePipelinesUrlCall(1, mapping));
            pipelines.addAll(makePipelinesUrlCall(2, mapping));
        } catch (LoginException e) {
            ApplicationManager.getApplication().invokeLater(() -> {

                String oldToken = ConfigProvider.getToken(mapping);
                oldToken = Strings.isNullOrEmpty(oldToken) ? "<empty>" : oldToken;
                logger.info("Login exception while loading pipelines, showing input dialog for token for remote " + mapping.getRemote());
                final String accessToken = Messages.showInputDialog(project, "Unable to log in to gitlab. Please enter the access token for access to " + mapping.getRemote() + ". Current token: " + oldToken, "Gitlab Pipeline Viewer", null, null, null);
                ConfigProvider.saveToken(mapping, accessToken);
                if (Strings.isNullOrEmpty(accessToken)) {
                    logger.info("No token entered, setting token to null for remore " + mapping.getRemote());
                } else {
                    ServiceManager.getService(project, BackgroundUpdateService.class).update(project, false);
                    logger.info("New token entered for remote " + mapping.getRemote());
                }

            });
            return Collections.emptyList();
        }
        return pipelines;
    }

    private List<PipelineTo> makePipelinesUrlCall(int page, Mapping mapping) throws IOException, LoginException {
        final String accessToken = ConfigProvider.getToken(mapping);
        String url;
        try {
            URIBuilder uriBuilder = new URIBuilder(mapping.getHost() + "/api/v4/projects/" + mapping.getGitlabProjectId() + "/pipelines");

            uriBuilder.addParameter("page", String.valueOf(page))
                    .addParameter("per_page", "100");

            if (accessToken != null) {
                logger.debug("Using access token for access to " + uriBuilder);
                uriBuilder.addParameter("private_token", accessToken);
            } else {
                logger.debug("No access token set for remote " + mapping.getRemote());
            }

            url = uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        String json;
        try {
            logger.debug("Calling URL " + url.replace(Strings.nullToEmpty(accessToken), "<accessToken>"));
            json = HttpRequests.request(url).readString();
        } catch (HttpRequests.HttpStatusException e) {
            //Unfortunately gitlab returns a 404 if the project was found but could not be accessed. We must interpret 404 like 401
            if (e.getStatusCode() == 401 || e.getStatusCode() == 404) {
                logger.info("Unable to load pipelines. Status code " + e.getStatusCode() + ". Message: " + e.getMessage());
                throw new LoginException();
            } else {
                throw new IOException("Unable to load pipelines from " + url + ". Status code: " + e.getStatusCode() + ". Status message: " + e.getMessage());
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
            final HostAndProjectPath hostAndProjectPath = new HostAndProjectPath(sshMatcher.group("host"), StringUtils.removeEnd(sshMatcher.group("projectPath"), ".git"));
            logger.info("Determined host " + hostAndProjectPath.getHost() + " and project path " + hostAndProjectPath.getProjectPath() + " from ssh remote " + remote);
            return Optional.of(hostAndProjectPath);
        }
        final Matcher httpMatcher = REMOTE_GIT_HTTP_PATTERN.matcher(remote);
        if (httpMatcher.matches()) {
            if (remote.startsWith("https://gitlab.com")) {
                final String host = "https://gitlab.com";
                final String projectPath = getCleanProjectPath(remote.substring("https://gitlab.com/".length()));
                final HostAndProjectPath hostAndProjectPath = new HostAndProjectPath(host, projectPath);
                logger.debug("Recognized gitlab.com HTTPS remote - determined " + hostAndProjectPath);
                return Optional.of(hostAndProjectPath);
            }
            //For self hosted instances it's impossible to determine which part of the path is part of the host or the project.
            //So we try each part of the path and see if we get a response that looks like gitlab
            final String fullUrl = httpMatcher.group("url");
            StringBuilder testUrl = new StringBuilder(httpMatcher.group("scheme"));
            for (String part : fullUrl.split("/")) {
                testUrl.append(part).append("/");

                final String response;
                try {
                    logger.debug("Trying URL " + testUrl);
                    response = ApplicationManager.getApplication().executeOnPooledThread(() -> HttpRequests.request(testUrl.toString()).readString()).get();
                } catch (Exception e) {
                    logger.error("Unable to retrieve host and project path from remote " + remote, e);
                    return Optional.empty();
                }
                if (response.toLowerCase().contains("gitlab")) {
                    final HostAndProjectPath hostAndProjectPath = new HostAndProjectPath(testUrl.toString(), getCleanProjectPath(fullUrl.substring(part.length())));
                    logger.info("Determined host " + hostAndProjectPath.getHost() + " and project path " + hostAndProjectPath.getProjectPath() + " from http remote " + remote);
                    return Optional.of(hostAndProjectPath);
                }
            }
        }
        logger.error("Unable to parse remote " + remote);
        return Optional.empty();
    }

    private static String getCleanProjectPath(String projectPath) {
        return StringUtils.removeStart(StringUtils.removeEndIgnoreCase(projectPath, ".git"), "/");
    }

    private static class LoginException extends Exception {
    }


}
