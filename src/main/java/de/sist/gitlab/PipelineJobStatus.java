package de.sist.gitlab;

import com.google.common.base.Objects;

import java.time.LocalDateTime;
import java.util.StringJoiner;

public class PipelineJobStatus {

    public String branchName;
    public LocalDateTime creationTime;
    public LocalDateTime updateTime;
    public String result;
    public String pipelineLink;

    public PipelineJobStatus(String ref, LocalDateTime creationTime, LocalDateTime updatedAt, String result, String webUrl) {
        this.branchName = ref;
        this.pipelineLink = webUrl;
        this.creationTime = creationTime;
        this.updateTime = updatedAt;
        this.result = result;
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
                Objects.equal(creationTime, that.creationTime) &&
                Objects.equal(result, that.result) &&
                Objects.equal(pipelineLink, that.pipelineLink);
    }

    public String getBranchName() {
        return branchName;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public String getResult() {
        return result;
    }

    public String getPipelineLink() {
        return pipelineLink;
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
                .toString();
    }
}
