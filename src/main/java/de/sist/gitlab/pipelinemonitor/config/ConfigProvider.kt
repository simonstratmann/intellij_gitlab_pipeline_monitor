package de.sist.gitlab.pipelinemonitor.config

import com.google.common.base.Strings
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import de.sist.gitlab.pipelinemonitor.gitlab.GitlabService
import org.apache.commons.lang3.tuple.Pair
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 *
 */
@Service
class ConfigProvider {
    var isConfigOpen: Boolean = false
        private set
    private val lock: Lock = ReentrantLock()
    private val configOpenCondition: Condition = lock.newCondition()

    /**
     * It may happen that with the config open the dialog for untracked remotes is opened. The user chooses to monitor something but the open log dialog is not updated.
     * The user applies the config and the list of tracked remotes is reset, resulting in a loop. Therefore we use this class to track if the config is open, not allowing
     * any dialogs to be opened for that time.
     */
    fun aquireLock() {
        lock.lock()
        try {
            if (isConfigOpen) {
                logger.debug("Config open, waiting")
                try {
                    configOpenCondition.await()
                    logger.debug("Config closed now, continuing")
                } catch (e: InterruptedException) {
                    logger.error("Error while aquiring config lock", e)
                }
            } else {
                logger.debug("Config not open, not waiting")
            }
        } finally {
            lock.unlock()
        }
    }

    fun setConfigOpen() {
        logger.debug("Setting config open")
        lock.lock()
        isConfigOpen = true
        lock.unlock()
    }

    @Synchronized
    fun setConfigClosed() {
        logger.debug("Setting config closed, signalling waiting threads")
        lock.lock()
        isConfigOpen = false
        configOpenCondition.signalAll()
        lock.unlock()
    }

    fun getMappings(): List<Mapping> {
        return PipelineViewerConfigApp.instance.mappings
    }

    fun getMappingByRemoteUrl(remote: String): Mapping? {
        return getMappings().stream().filter { x: Mapping? -> x!!.remote == remote }.findFirst().orElse(null)
    }

    fun getMappingByProjectId(projectId: String): Mapping? {
        return getMappings().stream().filter { x: Mapping? -> x!!.gitlabProjectId == projectId }.findFirst().orElse(null)
    }

    fun getBranchesToIgnore(project: Project?): List<String> {
        return PipelineViewerConfigProject.getInstance(project).branchesToIgnore
    }

    fun getIgnoredRemotes(): List<String> {
        return PipelineViewerConfigApp.instance.ignoredRemotes
    }

    fun getBranchesToWatch(project: Project?): List<String> {
        return PipelineViewerConfigProject.getInstance(project).branchesToWatch
    }

    fun getShowLightsForBranch(project: Project?): String? {
        return PipelineViewerConfigProject.getInstance(project).showLightsForBranch
    }

    val connectTimeoutSeconds: Int
        get() = PipelineViewerConfigApp.instance.connectTimeout

    fun getMergeRequestTargetBranch(project: Project?): String {
        val value = PipelineViewerConfigProject.getInstance(project).mergeRequestTargetBranch
        return if (!Strings.isNullOrEmpty(value)) value else PipelineViewerConfigApp.instance.mergeRequestTargetBranch!!
    }

    val isShowNotificationForWatchedBranches: Boolean
        get() = PipelineViewerConfigApp.instance.isShowNotificationForWatchedBranches

    val isShowConnectionErrorNotifications: Boolean
        get() = PipelineViewerConfigApp.instance.isShowConnectionErrorNotifications

    companion object {
        private val logger = Logger.getInstance(ConfigProvider::class.java)

        private val saveLock: Lock = ReentrantLock()

        @JvmStatic
        val instance: ConfigProvider
            get() = ApplicationManager.getApplication().getService(ConfigProvider::class.java)

        @JvmStatic
        fun saveToken(mapping: Mapping, token: String?, tokenType: TokenType, project: Project?) {
            object : Task.Backgroundable(project, "Saving token") {
                override fun run(indicator: ProgressIndicator) {
                    saveLock.lock()
                    val passwordKey = if (tokenType == TokenType.PERSONAL) mapping.host else mapping.remote
                    logger.debug(
                        "Saving token with length ",
                        (token?.length ?: 0),
                        (if (tokenType == TokenType.PERSONAL) " for host " else " for remote "),
                        passwordKey
                    )
                    val credentialAttributes = CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + passwordKey, passwordKey)
                    PasswordSafe.instance.setPassword(credentialAttributes, if (token == null) null else Strings.emptyToNull(token))
                    if (tokenType == TokenType.PERSONAL) {
                        //Delete token saved for this remote as it's now superseded by the personal access token
                        PasswordSafe.instance
                            .setPassword(CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + mapping.remote, mapping.remote), null)
                    }
                    saveLock.unlock()
                }
            }.queue()
        }

        fun getToken(mapping: Mapping): String? {
            return getToken(mapping.remote, mapping.host)
        }

        @JvmStatic
        fun getToken(remote: String, host: String?): String? {
            return getTokenAndType(remote, host).left
        }

        @JvmStatic
        fun getTokenAndType(remote: String, host: String?): Pair<String, TokenType?> {
            saveLock.lock()
            var tokenType: TokenType?
            val tokenCA = CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + remote, remote)
            var token = PasswordSafe.instance.getPassword(tokenCA)

            if (!Strings.isNullOrEmpty(token)) {
                tokenType = TokenType.PROJECT
            } else {
                //Didn't find a remote on token level, try host
                val hostCA = CredentialAttributes(GitlabService.ACCESS_TOKEN_CREDENTIALS_ATTRIBUTE + host, host)
                token = PasswordSafe.instance.getPassword(hostCA)
                tokenType = TokenType.PERSONAL
            }
            if (Strings.isNullOrEmpty(token)) {
                logger.debug("Found no token for remote ", remote, (if (host == null) ":" else " and host $host"))
                tokenType = null
            } else {
                logger.debug("Found token with length ", token?.length, " for remote ", remote, (if (host == null) ":" else " and host $host"))
            }
            saveLock.unlock()
            return Pair.of(token, tokenType)
        }

        @JvmStatic
        fun isNotEqualIgnoringEmptyOrNull(a: String?, b: String?): Boolean {
            return Strings.nullToEmpty(a) != Strings.nullToEmpty(b)
        }
    }
}
