package com.example.aws.s3.config;

import com.amazonaws.services.s3.AmazonS3;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
 public class AwsS3ClientBuilder {
    public static final String SUFFIX = "/";
    private AmazonS3 amazonS3 = null;
    private String endpoint = null;
    private String bucketName = null;

    public AwsS3ClientBuilder(AmazonS3 amazonS3, String endpoint, String bucketName) {
        this.amazonS3 = amazonS3;
        this.endpoint = massageEndpoint(endpoint,bucketName);
        this.bucketName = bucketName;
    }

    private String massageEndpoint(String endpoint, String bucketName) {
        if (endpoint.endsWith(SUFFIX) && endpoint.contains(bucketName)) {
            return endpoint;
        }

        if (endpoint.endsWith(SUFFIX) && !endpoint.contains(bucketName)) {
            return endpoint + bucketName + SUFFIX;
        }

        if (!endpoint.endsWith(SUFFIX) && !endpoint.contains(bucketName)) {
            return endpoint + SUFFIX + bucketName + SUFFIX;
        }

        if (!endpoint.endsWith(SUFFIX) && endpoint.contains(bucketName)) {
            return endpoint + SUFFIX;
        }

        return endpoint;
    }

}
