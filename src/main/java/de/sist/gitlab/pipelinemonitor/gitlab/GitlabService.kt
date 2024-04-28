package de.sist.gitlab.pipelinemonitor.gitlab

import com.fasterxml.jackson.core.type.TypeReference
import com.google.common.base.Strings
import com.intellij.credentialStore.generateServiceName
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import de.sist.gitlab.pipelinemonitor.*
import de.sist.gitlab.pipelinemonitor.config.*
import de.sist.gitlab.pipelinemonitor.git.GitService
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.Edge
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.MergeRequest
import de.sist.gitlab.pipelinemonitor.gitlab.mapping.PipelineNode
import de.sist.gitlab.pipelinemonitor.notifier.NotifierService
import de.sist.gitlab.pipelinemonitor.ui.TokenDialog
import de.sist.gitlab.pipelinemonitor.ui.UntrackedRemoteNotification
import de.sist.gitlab.pipelinemonitor.ui.UntrackedRemoteNotificationState
import dev.failsafe.Failsafe
import dev.failsafe.FailsafeException
import dev.failsafe.RetryPolicy
import dev.failsafe.function.CheckedSupplier
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import org.apache.http.client.utils.URIBuilder
import java.io.IOException
import java.net.URISyntaxException
import java.time.Duration
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors

@Service(Service.Level.PROJECT)
class GitlabService(private val project: Project) : Disposable {
    private val config: ConfigProvider = ConfigProvider.instance
    private val pipelineInfos: MutableMap<Mapping, List<PipelineJobStatus>> = HashMap()
    private val openTokenDialogsByMapping: MutableSet<Mapping> = HashSet()
    private val mergeRequests: MutableList<MergeRequest> = ArrayList()
    private val gitService: GitService = project.getService(GitService::class.java)
    private var isCheckingForUnmappedRemotes = false

    @Throws(IOException::class)
    fun updatePipelineInfos(triggeredByUser: Boolean) {
        val newMappingToPipelines: MutableMap<Mapping, List<PipelineJobStatus>> = HashMap()
        for ((key, value) in loadPipelines(triggeredByUser)) {
            val jobStatuses = value.stream()
                .map { pipeline: PipelineTo ->
                    PipelineJobStatus(
                        pipeline.id,
                        pipeline.ref,
                        key.gitlabProjectId,
                        pipeline.createdAt,
                        pipeline.updatedAt,
                        pipeline.status,
                        pipeline.webUrl,
                        pipeline.source
                    )
                }
                .sorted(Comparator.comparing({ obj: PipelineJobStatus -> obj.getUpdateTime() }, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList())

            newMappingToPipelines[key] = jobStatuses
        }
        synchronized(pipelineInfos) {
            pipelineInfos.clear()
            pipelineInfos.putAll(newMappingToPipelines)
        }
    }

    fun updateFromGraphQl() {
        val localPipelineInfos: Map<Mapping, List<PipelineJobStatus>> = HashMap(pipelineInfos)
        mergeRequests.clear()
        try {
            for (mapping in localPipelineInfos.keys) {
                logger.debug("Loading merge requests for remote ", mapping.remote)
                val sourceBranches: List<String> = ArrayList(gitService.getTrackedBranches(mapping))
                val data = GraphQl.makeCall(mapping.host, ConfigProvider.getToken(mapping), mapping.projectPath, sourceBranches, true)
                if (data.isPresent) {
                    val newMergeRequests = data.get().project.mergeRequests.edges.stream().map { obj: Edge -> obj.mergeRequest }
                        .toList()
                    mergeRequests.addAll(newMergeRequests)
                    logger.debug("Loaded ", mergeRequests.size, " pipelines for remote ", mapping.remote)

                    val mergeRequestsBySourceBranch = mergeRequests.stream().collect(
                        Collectors.groupingBy { obj: MergeRequest -> obj.sourceBranch }
                    )
                    val pipelinesByIid = data.get().project.pipelines.nodes.stream()
                        .collect(
                            Collectors.groupingBy { x: PipelineNode -> x.id.substring(x.id.lastIndexOf("/") + 1).toInt() }
                        )
                    for (pipelineJobStatus in localPipelineInfos[mapping]!!) {
                        val mergeRequestsForPipeline = mergeRequestsBySourceBranch[pipelineJobStatus.branchName]
                        if (!mergeRequestsForPipeline.isNullOrEmpty()) {
                            pipelineJobStatus.mergeRequestLink = mergeRequestsForPipeline[0].webUrl
                        }
                        val pipelineNodesForPipeline = pipelinesByIid[pipelineJobStatus.id]
                        if (!pipelineNodesForPipeline.isNullOrEmpty()) {
                            val detailedStatus = pipelineNodesForPipeline[0].detailedStatus
                            if (detailedStatus != null) {
                                pipelineJobStatus.statusGroup = detailedStatus.group
                            }
                        }
                    }
                } else {
                    logger.debug("Unable to load merge requests for remote ", mapping.remote)
                }
            }
        } catch (e: Exception) {
            logger.info("Unable to load merge requests", e)
        }
    }

    fun getPipelineInfos(): Map<Mapping, List<PipelineJobStatus>> {
        synchronized(pipelineInfos) {
            return pipelineInfos
        }
    }

    fun getMergeRequests(): List<MergeRequest> {
        synchronized(pipelineInfos) {
            return mergeRequests
        }
    }

    fun checkForUnmappedRemotes(triggeredByUser: Boolean) {
        //Locks don't work here for some reason
        if (isCheckingForUnmappedRemotes) {
            return
        }
        isCheckingForUnmappedRemotes = true
        try {
            ConfigProvider.instance.aquireLock()
            val gitRepositories = gitService.allGitRepositories
            logger.debug("Checking for unmapped remotes")
            for (gitRepository in gitRepositories) {
                for (remote in gitRepository.remotes) {
                    for (url in remote.urls) {
                        if (!PipelineViewerConfigProject.getInstance(project).isEnabled) {
                            //Make sure no further remotes are processed if multiple are found and the user chose to disable for the project
                            logger.debug("Disabled for project ", project.name)
                            return
                        }
                        if (ConfigProvider.instance.getIgnoredRemotes().contains(url)) {
                            logger.debug("Remote ", url, " is ignored")
                            continue
                        }
                        if (PipelineViewerConfigApp.instance.remotesAskAgainNextTime.contains(url) && !triggeredByUser) {
                            logger.debug("Remote ", url, " is ignored until next plugin load and reload was not triggered by user. Not showing notification.")
                            continue
                        }
                        if (INCOMPATIBLE_REMOTES.stream().anyMatch { x: String? ->
                                url.lowercase(Locale.getDefault()).contains(
                                    x!!
                                )
                            }) {
                            logger.debug("Remote URL ", url, " is incompatible")
                            continue
                        }
                        if (UntrackedRemoteNotification.getAlreadyOpenNotifications(project).stream()
                                .anyMatch { x: UntrackedRemoteNotification -> x.url == url }
                        ) {
                            logger.debug("Remote URL ", url, " is already waiting for an answer")
                            continue
                        }
                        if (config.getMappingByRemoteUrl(url) == null) {
                            val hostProjectPathFromRemote = getHostProjectPathFromRemote(url)

                            if (isCiDisabledForGitlabProject(url, hostProjectPathFromRemote.orElse(null))) {
                                return
                            }
                            if (hostProjectPathFromRemote.isPresent) {
                                val host = hostProjectPathFromRemote.get().host
                                val projectPath = hostProjectPathFromRemote.get().projectPath
                                if (PipelineViewerConfigApp.instance.alwaysMonitorHosts.contains(host)) {
                                    logger.debug("Host ", host, " is in the list of hosts for which to always monitor projects")
                                    val token = ConfigProvider.getToken(host, projectPath)
                                    val optionalMapping = createMappingWithProjectNameAndId(url, host, projectPath, token, TokenType.PERSONAL, this.project)
                                    if (project.isDisposed) {
                                        return
                                    }
                                    val notifierService = project.getService(NotifierService::class.java)
                                    if (optionalMapping.isPresent) {
                                        logger.debug("Successfully created mapping ", optionalMapping.get(), ". Will use it")
                                        notifierService.showInfo("Gitlab Pipeline Viewer will monitor project " + project.name)
                                        PipelineViewerConfigApp.instance.mappings.add(optionalMapping.get())
                                        if (project.isDisposed) {
                                            return
                                        }
                                        project.getService(BackgroundUpdateService::class.java).update(project, false)
                                        continue
                                    } else {
                                        logger.info("Unable to automatically create mapping for project on host $host")
                                        notifierService.showError("Unable to automatically create mapping for project on host $host")
                                    }
                                }
                            }

                            logger.debug("Showing notification for untracked remote ", url)
                            UntrackedRemoteNotification(project, url, hostProjectPathFromRemote.orElse(null)).notify(project)
                            logger.debug("Notifying project ", project, " that a new notification is shown")
                            project.messageBus.syncPublisher(UntrackedRemoteNotificationState.UNTRACKED_REMOTE_FOUND).handle(true)
                        }
                    }
                }
            }
        } finally {
            isCheckingForUnmappedRemotes = false
        }
    }

    private fun isCiDisabledForGitlabProject(url: String, hostProjectPathFromRemote: HostAndProjectPath?): Boolean {
        if (hostProjectPathFromRemote == null) {
            logger.debug("Unable to determine if CI is enabled for $url because host and project path could not be parsed")
            return false
        }

        val host = hostProjectPathFromRemote.host
        val projectPath = hostProjectPathFromRemote.projectPath
        val data = GraphQl.makeCall(host, ConfigProvider.getToken(url, host), projectPath, emptyList(), false)

        if (data.isEmpty) {
            logger.debug("Unable to determine if CI is enabled for $url because the graphql query failed")
            return false
        }
        if (data.get().project.isJobsEnabled) {
            logger.info("CI is enabled for $url")
            return false
        }
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("de.sist.gitlab.pipelinemonitor.disabledCi")
        notificationGroup.createNotification(
            "Gitlab Pipeline Viewer - CI disabled",
            "Gitlab CI is disabled for $url. Ignoring it.",
            NotificationType.INFORMATION
        ).notify(
            project
        )
        PipelineViewerConfigApp.instance.ignoredRemotes.add(url)
        logger.info("Added $url to list of ignored remotes because CI is disabled for its gitlab project")
        return true
    }

    @Throws(IOException::class)
    private fun loadPipelines(triggeredByUser: Boolean): Map<Mapping, List<PipelineTo>> {
        val projectToPipelines: MutableMap<Mapping, List<PipelineTo>> = HashMap()
        val nonIgnoredRepositories = gitService.nonIgnoredRepositories
        if (nonIgnoredRepositories.isEmpty()) {
            logger.debug("No non-ignored git repositories")
            return emptyMap()
        }
        for (nonIgnoredRepository in nonIgnoredRepositories) {
            for (remote in nonIgnoredRepository.remotes) {
                for (url in remote.urls) {
                    val mapping = ConfigProvider.instance.getMappingByRemoteUrl(url)
                    if (mapping == null) {
                        logger.debug("No mapping found for remote url ", url)
                        continue
                    }
                    if (PipelineViewerConfigApp.instance.remotesAskAgainNextTime.contains(url) && !triggeredByUser) {
                        logger.debug("Remote ", url, " is ignored until next plugin load and reload was not triggered by user. Not loading pipelines.")
                        continue
                    }
                    logger.debug("Loading pipelines for remote ", mapping.remote)
                    val pipelines = loadPipelines(mapping)
                    logger.debug("Loaded ", pipelines.size, " pipelines for remote ", mapping.remote)

                    projectToPipelines[mapping] = pipelines
                }
            }
        }

        return projectToPipelines
    }

    @Throws(IOException::class)
    private fun loadPipelines(mapping: Mapping): List<PipelineTo> {
        val pipelines: MutableList<PipelineTo> = ArrayList()
        try {
            if (openTokenDialogsByMapping.contains(mapping)) {
                //No sense making queries
                logger.debug("Not loading pipelines. Token dialog open for ", mapping)
                return emptyList()
            }
            //Note: Gitlab GraphQL does not return the ref (branch name): https://gitlab.com/gitlab-org/gitlab/-/issues/230405
            pipelines.addAll(makePipelinesUrlCall(1, mapping))
            pipelines.addAll(makePipelinesUrlCall(2, mapping))
        } catch (e: Exception) {
            if (e is FailsafeException && e.cause is IOException) {
                throw (e.cause as IOException?)!!
            }
            var handle = false
            if (e is LoginException) {
                handle = true
            }
            if (e is FailsafeException) {
                handle = e.cause is LoginException
            }
            if (handle) {
                logger.debug("Login exception while loading pipelines", e)
                ApplicationManager.getApplication().invokeLater {
                    if (openTokenDialogsByMapping.contains(mapping)) {
                        logger.debug("Not showing another token dialog for ", mapping)
                        //Just to make sure
                        return@invokeLater
                    }
                    val tokenAndType = ActionUtil.underModalProgress(
                        project, "Reading token"
                    ) { ConfigProvider.getTokenAndType(mapping.remote, mapping.host) }
                    val oldToken = if (Strings.isNullOrEmpty(tokenAndType.left)) "<empty>" else tokenAndType.left!!
                    val oldTokenForLog = if (Strings.isNullOrEmpty(tokenAndType.left)) "<empty>" else "with length " + tokenAndType.left!!.length
                    val tokenType = tokenAndType.right
                    logger.info("Showing input dialog for token for remote " + mapping.remote + " with old token " + oldTokenForLog)
                    val preselectedTokenType = if (tokenAndType.left == null) TokenType.PERSONAL else tokenType
                    openTokenDialogsByMapping.add(mapping)

                    val clickedOk = TokenDialog.Wrapper(
                        project,
                        "Unable to log in to gitlab. Please enter the access token for access to " + mapping.remote + ". Enter nothing to delete it.",
                        oldToken,
                        preselectedTokenType
                    ) { response: Pair<String?, TokenType?> ->
                        if (Strings.isNullOrEmpty(response.left)) {
                            logger.info("No token entered, setting token to null for remote " + mapping.remote)
                            response.right?.let { ConfigProvider.saveToken(mapping, null, it, project) }
                        } else {
                            logger.info("New token entered for remote " + mapping.remote)
                            response.right?.let { ConfigProvider.saveToken(mapping, response.left, it, project) }
                        }
                    }
                        .showAndGet()

                    openTokenDialogsByMapping.remove(mapping)

                    if (!clickedOk) {
                        logger.info("Token dialog cancelled, not changing anything. Will not load pipelines until next plugin load or triggered manually")
                        PipelineViewerConfigApp.instance.remotesAskAgainNextTime.add(mapping.remote)
                        return@invokeLater
                    }

                    PipelineViewerConfigApp.instance.remotesAskAgainNextTime.remove(mapping.remote)
                    if (project.isDisposed) {
                        return@invokeLater
                    }
                    project.getService(BackgroundUpdateService::class.java).update(project, false)
                }
                return emptyList()
            }
            throw e
        }
        return pipelines
    }

    @Throws(IOException::class, LoginException::class)
    private fun makePipelinesUrlCall(page: Int, mapping: Mapping): List<PipelineTo> {
        val url: String
        try {
            val uriBuilder = URIBuilder(mapping.host + "/api/v4/projects/" + mapping.gitlabProjectId + "/pipelines")

            uriBuilder.addParameter("page", page.toString())
                .addParameter("per_page", "100")


            url = uriBuilder.build().toString()
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }

        val json = Failsafe.with(RETRY_POLICY).get(
            CheckedSupplier { makeApiCall(url, ConfigProvider.getToken(mapping)) })
        return Jackson.OBJECT_MAPPER.readValue(json, object : TypeReference<List<PipelineTo>>() {
        })
    }

    fun getGitlabHtmlBaseUrl(projectId: String): String {
        val mapping = ConfigProvider.instance.getMappingByProjectId(projectId)
        return mapping?.host + "/" + mapping?.projectPath
    }

    override fun dispose() {
    }

    class LoginException(message: String?) : Exception(message)


    companion object {
        @JvmField
        val ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE: String = generateServiceName("GitlabService", "accessToken")


        private val logger = Logger.getInstance(GitlabService::class.java)

        private val REMOTE_GIT_SSH_PATTERN: Pattern = Pattern.compile("git@(?<host>.*):(?<projectPath>.*)(\\.git)?")
        private val REMOTE_GIT_HTTP_PATTERN: Pattern = Pattern.compile("(?<scheme>https?://)(?<url>.*)(\\.git)?")
        private val REMOTE_BEST_GUESS_PATTERN: Pattern = Pattern.compile("(?<host>https?://[^/]*)/(?<projectPath>.*)")
        private val INCOMPATIBLE_REMOTES: List<String> = mutableListOf("github.com", "bitbucket.com")
        private val RETRY_POLICY: RetryPolicy<String> = RetryPolicy.builder<String>()
            .handle(IOException::class.java, LoginException::class.java)
            .withDelay(Duration.ofSeconds(1))
            .withMaxRetries(5)
            .build()

        @JvmStatic
        fun createMappingWithProjectNameAndId(
            remoteUrl: String,
            host: String,
            projectPath: String,
            token: String?,
            tokenType: TokenType,
            intellijProject: Project
        ): Optional<Mapping> {
            val data = GraphQl.makeCall(host, token, projectPath, emptyList(), false)
            if (data.isEmpty) {
                return Optional.empty()
            }

            val mapping = Mapping()
            val project = data.get().project
            logger.info("Determined project name " + project.name + " and id " + project.id + " for remote " + remoteUrl + " because GraphQl call returned a response")
            mapping.remote = remoteUrl
            mapping.gitlabProjectId = project.id
            mapping.projectName = project.name
            mapping.host = host
            mapping.projectPath = projectPath
            ConfigProvider.saveToken(mapping, token, tokenType, intellijProject)
            return Optional.of(mapping)
        }

        @Throws(IOException::class, LoginException::class)
        fun makeApiCall(url: String, accessToken: String?): String {
            var urlToUse = url
            try {
                if (accessToken != null) {
                    val uriBuilder = URIBuilder(urlToUse)
                    logger.debug("Using access token for access to ", urlToUse)
                    uriBuilder.addParameter("private_token", accessToken)
                    urlToUse = uriBuilder.build().toString()
                }
            } catch (e: URISyntaxException) {
                throw RuntimeException(e)
            }

            val response: String
            val cleanedUrl = if (accessToken == null) urlToUse else urlToUse.replace(accessToken, "<accessToken>")
            if (GitlabAccessLogger.GITLAB_ACCESS_LOGGER.isDebugEnabled) {
                GitlabAccessLogger.GITLAB_ACCESS_LOGGER.debug("Calling ", cleanedUrl)
            }
            try {
                response = HttpRequests.request(urlToUse)
                    .connectTimeout(ConfigProvider.instance.connectTimeoutSeconds * 1000)
                    .readTimeout(ConfigProvider.instance.connectTimeoutSeconds * 1000)
                    .readString()
            } catch (e: IOException) {
                if (e is HttpRequests.HttpStatusException) {
                    //Unfortunately gitlab returns a 404 if the project was found but could not be accessed. We must interpret 404 like 401
                    if (e.statusCode == 401 || e.statusCode == 404) {
                        logger.info("Unable to load pipelines. Interpreting as login error. Status code " + e.statusCode + ". Message: " + e.message)
                        throw LoginException("Unable to login to $cleanedUrl")
                    } else {
                        throw IOException("Unable to access " + cleanedUrl + ". Status code: " + e.statusCode + ". Status message: " + e.message)
                    }
                }
                throw IOException("Unable to access " + cleanedUrl + ". Error message: " + e.message, e)
            }

            return response
        }

        @JvmStatic
        fun getHostProjectPathFromRemote(remote: String): Optional<HostAndProjectPath> {
            val similarMapping = ConfigProvider.instance.getMappings().stream()
                .filter { x: Mapping -> remote.startsWith(x.host) }
                .findFirst()
            if (similarMapping.isPresent) {
                logger.debug("Found existing mapping for host ", similarMapping.get().host, " and remote ", similarMapping.get().remote)
                val host = similarMapping.get().host
                val projectPath = getCleanProjectPath(remote.substring(similarMapping.get().host.length))
                logger.debug("Found existing mapping for host ", similarMapping.get().host, " and remote ", similarMapping.get().remote)
                val hostAndProjectPath = HostAndProjectPath(host, projectPath)
                logger.info("Determined host " + hostAndProjectPath.host + " and project path " + hostAndProjectPath.projectPath + " for http remote " + remote + " from similar mapping")
                return Optional.of(hostAndProjectPath)
            }
            val sshMatcher = REMOTE_GIT_SSH_PATTERN.matcher(remote)
            if (sshMatcher.matches()) {
                val hostAndProjectPath =
                    HostAndProjectPath("https://" + sshMatcher.group("host"), StringUtils.removeEnd(sshMatcher.group("projectPath"), ".git"))
                logger.info("Determined host " + hostAndProjectPath.host + " and project path " + hostAndProjectPath.projectPath + " from ssh remote " + remote)
                return Optional.of(hostAndProjectPath)
            }
            val httpMatcher = REMOTE_GIT_HTTP_PATTERN.matcher(remote)
            if (httpMatcher.matches()) {
                if (remote.startsWith("https://gitlab.com")) {
                    val host = "https://gitlab.com"
                    val projectPath = getCleanProjectPath(remote.substring("https://gitlab.com/".length))
                    val hostAndProjectPath = HostAndProjectPath(host, projectPath)
                    logger.debug("Recognized gitlab.com HTTPS remote - determined ", hostAndProjectPath)
                    return Optional.of(hostAndProjectPath)
                }
                //For self hosted instances it's impossible to determine which part of the path is part of the host or the project.
                //So we try each part of the path and see if we get a response that looks like gitlab
                val fullUrl = httpMatcher.group("url")
                val testUrl = StringBuilder(httpMatcher.group("scheme"))
                for (part in fullUrl.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    testUrl.append(part).append("/")

                    val response: String?
                    try {
                        logger.debug("Trying URL ", testUrl)
                        response = ApplicationManager.getApplication().executeOnPooledThread<String> {
                            try {
                                return@executeOnPooledThread HttpRequests
                                    .request(testUrl.toString())
                                    .connectTimeout(ConfigProvider.instance.connectTimeoutSeconds * 1000)
                                    .readTimeout(ConfigProvider.instance.connectTimeoutSeconds * 1000)
                                    .readString()
                            } catch (e: Exception) {
                                logger.info("Unable to retrieve host and project path from remote $remote", e)
                                return@executeOnPooledThread null
                            }
                        }.get()
                    } catch (e: Exception) {
                        logger.info("Unable to retrieve host and project path from remote $remote", e)
                        return tryBestGuessForRemote(remote)
                    }
                    if (response == null) {
                        return tryBestGuessForRemote(remote)
                    }
                    if (response.lowercase(Locale.getDefault()).contains("gitlab")) {
                        val hostAndProjectPath =
                            HostAndProjectPath(StringUtils.removeEndIgnoreCase(testUrl.toString(), "/"), getCleanProjectPath(remote.substring(testUrl.length)))
                        logger.info("Determined host " + hostAndProjectPath.host + " and project path " + hostAndProjectPath.projectPath + " from http remote " + remote + " because that host returned a response containing 'gitlab'")
                        return Optional.of(hostAndProjectPath)
                    }
                    logger.debug("Response from ", testUrl, " does not contain \"gitlab\"")
                }
            }
            logger.info("Unable to parse remote $remote")
            return tryBestGuessForRemote(remote)
        }

        private fun tryBestGuessForRemote(remote: String): Optional<HostAndProjectPath> {
            logger.debug("Trying to parse helpful data for dialog from ", remote)
            val bestGuessMatcher = REMOTE_BEST_GUESS_PATTERN.matcher(remote)
            if (bestGuessMatcher.matches()) {
                val host = bestGuessMatcher.group("host")
                val projectPath = StringUtils.removeEnd(bestGuessMatcher.group("projectPath"), ".git")
                logger.debug("Best guess: Host: ", host, ". Project path: ", projectPath)
                return Optional.of(HostAndProjectPath(host, projectPath))
            }
            logger.info("Unable to find any meaningful data in remote $remote")
            return Optional.empty()
        }

        private fun getCleanProjectPath(projectPath: String): String {
            return StringUtils.removeStart(StringUtils.removeEndIgnoreCase(projectPath, ".git"), "/")
        }
    }
}
