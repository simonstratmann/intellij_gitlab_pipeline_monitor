// (C) 2022 PPI AG
package de.sist.gitlab.pipelinemonitor.gitlab.mapping;

public class Pipeline {
    private String iid;
    private DetailedStatus detailedStatus;

    public String getIid() {
        return iid;
    }

    public void setIid(String iid) {
        this.iid = iid;
    }

    public DetailedStatus getDetailedStatus() {
        return detailedStatus;
    }

    public void setDetailedStatus(DetailedStatus detailedStatus) {
        this.detailedStatus = detailedStatus;
    }
}
