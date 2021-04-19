package de.sist.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.ZonedDateTime;
import java.util.StringJoiner;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineTo {

    @JsonProperty("id")
    private Integer id;
    @JsonProperty("sha")
    private String sha;
    @JsonProperty("ref")
    private String ref;
    @JsonProperty("status")
    private String status;
    @JsonProperty("created_at")
    private ZonedDateTime createdAt;
    @JsonProperty("updated_at")
    private ZonedDateTime updatedAt;
    @JsonProperty("web_url")
    private String webUrl;

    @JsonProperty("id")
    public Integer getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(Integer id) {
        this.id = id;
    }

    @JsonProperty("sha")
    public String getSha() {
        return sha;
    }

    @JsonProperty("sha")
    public void setSha(String sha) {
        this.sha = sha;
    }

    @JsonProperty("ref")
    public String getRef() {
        return ref;
    }

    @JsonProperty("ref")
    public void setRef(String ref) {
        this.ref = ref;
    }

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @JsonProperty("created_at")
    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("created_at")
    @JsonDeserialize(using = Jackson.DateTimeJsonDeserializer.class)
    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @JsonProperty("updated_at")
    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    @JsonProperty("updated_at")
    @JsonDeserialize(using = Jackson.DateTimeJsonDeserializer.class)
    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @JsonProperty("web_url")
    public String getWebUrl() {
        return webUrl;
    }

    @JsonProperty("web_url")
    public void setWebUrl(String webUrl) {
        this.webUrl = webUrl;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", PipelineTo.class.getSimpleName() + "[", "]")
                .add("ref='" + ref + "'")
                .add("status='" + status + "'")
                .add("webUrl='" + webUrl + "'")
                .toString();
    }
}
