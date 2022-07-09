// (C) 2022 PPI AG
package de.sist.gitlab.pipelinemonitor.gitlab.mapping;

public class PipelineNode {
    private String id;
    private DetailedStatus detailedStatus;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DetailedStatus getDetailedStatus() {
        return detailedStatus;
    }

    public void setDetailedStatus(DetailedStatus detailedStatus) {
        this.detailedStatus = detailedStatus;
    }

}
