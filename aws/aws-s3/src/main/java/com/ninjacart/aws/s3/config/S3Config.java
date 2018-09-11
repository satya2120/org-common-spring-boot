package com.example.aws.s3.config;

import lombok.Data;

/**
 * Model to hold s3 config.
 */
@Data
public class S3Config {

     private String accessKey;
     private String secretKey;
     private String region;
     private String baseUrl;
     private String bucketName;
     private String symmetricKeyPath;
     private int maxConnectionPool;


}
