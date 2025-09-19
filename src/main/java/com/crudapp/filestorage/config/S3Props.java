package com.crudapp.filestorage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "s3")
public class S3Props {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String region = "us-east-1";
    private String bucket;
    private String publicBaseUrl;

    public String getEndpoint() {
        return endpoint;
    }
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }
    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }
    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }
}
