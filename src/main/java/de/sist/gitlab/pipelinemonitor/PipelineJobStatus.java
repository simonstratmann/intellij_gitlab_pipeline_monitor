package de.sist.gitlab.pipelinemonitor;

import com.google.common.base.Objects;

import java.time.ZonedDateTime;
import java.util.StringJoiner;

public class PipelineJobStatus {

    public String branchName;
    public final String projectId;
    public final ZonedDateTime creationTime;
    public final ZonedDateTime updateTime;
    public final String result;
    public final String pipelineLink;
    public String mergeRequestLink;
    public final String source;
    private String branchNameDisplay;

    public PipelineJobStatus(String ref, String projectId, ZonedDateTime creationTime, ZonedDateTime updatedAt, String result, String webUrl, String source) {
        this.branchName = ref;
        this.projectId = projectId;
        this.pipelineLink = webUrl;
        this.creationTime = creationTime;
        this.updateTime = updatedAt;
        this.result = result;
        this.source = source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PipelineJobStatus)) {
            return false;
        }
        PipelineJobStatus that = (PipelineJobStatus) o;
        return Objects.equal(branchName, that.branchName) &&
                Objects.equal(projectId, that.projectId) &&
                Objects.equal(creationTime, that.creationTime) &&
                Objects.equal(result, that.result) &&
                Objects.equal(source, that.source) &&
                Objects.equal(pipelineLink, that.pipelineLink)
                ;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getProjectId() {
        return projectId;
    }

    public ZonedDateTime getCreationTime() {
        return creationTime;
    }

    public ZonedDateTime getUpdateTime() {
        return updateTime;
    }

    public String getResult() {
        return result;
    }

    public String getPipelineLink() {
        return pipelineLink;
    }


    public String getSource() {
        return source;
    }

    public String getMergeRequestLink() {
        return mergeRequestLink;
    }

    public String getBranchNameDisplay() {
        return branchNameDisplay != null ? branchNameDisplay : branchName;
    }

    public void setBranchNameDisplay(String branchNameDisplay) {
        this.branchNameDisplay = branchNameDisplay;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(branchName, creationTime, result, pipelineLink);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PipelineJobStatus.class.getSimpleName() + "[", "]")
                .add("branchName='" + branchName + "'")
                .add("time=" + creationTime)
                .add("result='" + result + "'")
                .add("pipelineLink='" + pipelineLink + "'")
                .add("mergeRequestLink='" + mergeRequestLink + "'")
                .add("source='" + source + "'")
                .toString();
    }
}
