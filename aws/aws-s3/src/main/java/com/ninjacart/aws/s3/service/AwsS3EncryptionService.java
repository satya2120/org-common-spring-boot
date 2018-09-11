package com.example.aws.s3.service;

import com.example.aws.s3.config.AwsS3ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class AwsS3EncryptionService extends AbstractAwsS3Service {


    public AwsS3ClientBuilder getConfiguredClient(String bucketName) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = awsClientProvider.getEncryptionClient(bucketName);
        if (awsS3ClientBuilder == null) {
            log.error("No S3 bucket configured with name {}",bucketName);
            throw new Exception("No bucket configured "+ bucketName);
        }
        return awsS3ClientBuilder;
    }

}
