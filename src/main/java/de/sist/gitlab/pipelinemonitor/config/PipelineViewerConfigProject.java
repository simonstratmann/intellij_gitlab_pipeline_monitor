package de.sist.gitlab.pipelinemonitor.config;

import com.google.common.base.Strings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@State(name = "PipelineViewerConfigProject", storages = {@Storage("PipelineViewerConfig.xml")})
public class PipelineViewerConfigProject implements PersistentStateComponent<PipelineViewerConfigProject> {

    private List<String> branchesToIgnore = new ArrayList<>();
    private List<String> branchesToWatch = new ArrayList<>();
    private String showLightsForBranch;
    private String mergeRequestTargetBranch;
    private boolean enabled = true;
    //Not displayed in the settings but in the tool window
    private boolean showPipelinesForAll = true;

    public List<String> getBranchesToIgnore() {
        return branchesToIgnore;
    }

    public void setBranchesToIgnore(List<String> branchesToIgnore) {
        this.branchesToIgnore = new ArrayList<>(new HashSet<>(branchesToIgnore));
    }

    @NotNull
    public List<String> getBranchesToWatch() {
        return branchesToWatch;
    }


    public void setBranchesToWatch(List<String> branchesToWatch) {
        this.branchesToWatch = branchesToWatch;
    }


    public String getShowLightsForBranch() {
        return Strings.emptyToNull(showLightsForBranch);
    }


    public void setShowLightsForBranch(String showLightsForBranch) {
        this.showLightsForBranch = showLightsForBranch;
    }


    public String getMergeRequestTargetBranch() {
        return Strings.emptyToNull(mergeRequestTargetBranch);
    }


    public void setMergeRequestTargetBranch(String mergeRequestTargetBranch) {
        this.mergeRequestTargetBranch = mergeRequestTargetBranch;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShowPipelinesForAll() {
        return showPipelinesForAll;
    }

    public void setShowPipelinesForAll(boolean showPipelinesForAll) {
        this.showPipelinesForAll = showPipelinesForAll;
    }

    public @Nullable PipelineViewerConfigProject getState() {
        return this;
    }

    public void loadState(@NotNull PipelineViewerConfigProject state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static PipelineViewerConfigProject getInstance(Project project) {
        return ServiceManager.getService(project, PipelineViewerConfigProject.class);
    }
}
