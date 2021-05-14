package com.hao.server.fabric;

import java.util.Objects;

public class RequestAuthorization {
    private String fileHash;
    private String organization;

    public RequestAuthorization() {
    }

    public RequestAuthorization(String fileHash, String organization) {
        this.fileHash = fileHash;
        this.organization = organization;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestAuthorization that = (RequestAuthorization) o;
        return Objects.equals(fileHash, that.fileHash) &&
                Objects.equals(organization, that.organization);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileHash, organization);
    }
}
