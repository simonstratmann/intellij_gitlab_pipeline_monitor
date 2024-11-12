package de.sist.gitlab.pipelinemonitor.config

import com.google.common.base.Joiner
import com.google.common.base.MoreObjects
import com.google.common.base.Strings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient
import java.time.Instant
import java.util.*

@Suppress("unused")
@State(name = "PipelineViewerConfigApp", storages = [Storage("PipelineViewerConfigApp.xml")])
class PipelineViewerConfigApp : PersistentStateComponent<PipelineViewerConfigApp?> {
    enum class DisplayType {
        ICON,
        LINK,
        ID
    }

    enum class MrPipelineDisplayType {
        SOURCE_BRANCH,
        TITLE
    }

    @JvmField
    var mappings: MutableList<Mapping> = ArrayList()
    var mergeRequestTargetBranch: String? = null
        get() = Strings.emptyToNull(field)
    var statusesToWatch: List<String> = ArrayList()
    var isShowNotificationForWatchedBranches: Boolean = true
    var isShowConnectionErrorNotifications: Boolean = true
    var isShowConnectionErrors: Boolean = true

    @JvmField
    @get:Synchronized
    @set:Synchronized
    var ignoredRemotes: MutableList<String> = ArrayList()
    var isShowForTags: Boolean = true

    @JvmField
    var maxLatestTags: Int? = null

    @JvmField
    var urlOpenerCommand: String? = null

    @JvmField
    @Transient
    val remotesAskAgainNextTime: MutableSet<String> = HashSet()
    var displayType: DisplayType? = DisplayType.ICON
        get() = if (field == null) DisplayType.ICON else field

    @JvmField
    var connectTimeout: Int = 10
    var mrPipelineDisplayType: MrPipelineDisplayType? = MrPipelineDisplayType.SOURCE_BRANCH
        get() = if (field == null) MrPipelineDisplayType.SOURCE_BRANCH else field

    @JvmField
    var mrPipelinePrefix: String = "MR: "
    val gitlabInstanceInfos: MutableMap<String, GitlabInfo> = HashMap()

    @JvmField
    var maxAgeDays: Int? = null
    var isOnlyForRemoteBranchesExist: Boolean = false

    @JvmField
    var alwaysMonitorHosts: Set<String> = HashSet()
    var isShowProgressBar: Boolean = true

    @JvmField
    var refreshDelay: Int = 30

    val alwaysMonitorHostsAsString: String
        get() = listToString(alwaysMonitorHosts)

    fun setAlwaysMonitorHostsFromString(alwaysMonitorHosts: String) {
        this.alwaysMonitorHosts = stringToSet(alwaysMonitorHosts)
    }

    override fun getState(): PipelineViewerConfigApp {
        return this
    }

    override fun loadState(state: PipelineViewerConfigApp) {
        XmlSerializerUtil.copyBean(state, this)
    }

    class GitlabInfo {
        var lastCheck: Instant = Instant.now()
        var isSupportsRef: Boolean = false

        constructor()

        constructor(lastCheck: Instant, supportsRef: Boolean) {
            this.lastCheck = lastCheck
            this.isSupportsRef = supportsRef
        }


        override fun toString(): String {
            return MoreObjects.toStringHelper(this)
                .add("lastCheck", lastCheck)
                .add("supportsRef", isSupportsRef)
                .toString()
        }
    }

    companion object {
        fun listToString(strings: Collection<String>): String {
            return Joiner.on(";").join(strings)
        }

        fun stringToSet(string: String): Set<String> {
            if (Strings.isNullOrEmpty(string)) {
                return HashSet()
            }
            val split = string.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (split.size == 0) {
                return HashSet()
            }
            return HashSet(Arrays.asList(*split))
        }

        @JvmStatic
        val instance: PipelineViewerConfigApp
            get() = ApplicationManager.getApplication().getService(PipelineViewerConfigApp::class.java)
    }
}
