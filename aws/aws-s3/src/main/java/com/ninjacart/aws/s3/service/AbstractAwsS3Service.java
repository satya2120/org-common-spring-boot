package com.example.aws.s3.service;

import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import com.example.aws.s3.config.AbstractAwsS3ClientProvider;
import com.example.aws.s3.config.AwsS3ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Instant;
import java.util.Date;

@Slf4j
abstract class AbstractAwsS3Service {

    @Autowired protected AbstractAwsS3ClientProvider awsClientProvider;

    /**
     * Upload file with mimeType
     * @param bucketName
     * @param filePath
     * @param fileData
     * @param mimeType
     * @return
     * @throws Exception
     */
    public String upload(String bucketName, final String filePath, byte[] fileData, final String mimeType) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = getConfiguredClient(bucketName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(fileData.length);
        metadata.setContentType(mimeType);
        String path = awsS3ClientBuilder.getEndpoint() + massageFilePath(filePath);
        awsS3ClientBuilder.getAmazonS3().putObject(new PutObjectRequest(bucketName, filePath, new ByteArrayInputStream(fileData), metadata).withCannedAcl(CannedAccessControlList.Private));
        return path;
    }

    /**
     * Upload file with ObjectMetadata
     * @param bucketName
     * @param filePath
     * @param fileData
     * @param objectMetadata
     * @return
     * @throws Exception
     */
    public String upload(String bucketName, final String filePath, byte[] fileData, ObjectMetadata objectMetadata) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = getConfiguredClient(bucketName);
        String path = awsS3ClientBuilder.getEndpoint() + massageFilePath(filePath);
        awsS3ClientBuilder.getAmazonS3().putObject(new PutObjectRequest(bucketName, filePath, new ByteArrayInputStream(fileData), objectMetadata).withCannedAcl(CannedAccessControlList.Private));
        return path;
    }

    /**
     * Get presigned url for given path than can expires in expiredIn
     * @param bucketName
     * @param filePath
     * @param expiredIn
     * @return
     * @throws Exception
     */
    public String generatePreSignedUrl(String bucketName, final String filePath, int expiredIn) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = getConfiguredClient(bucketName);
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, filePath);
        if (expiredIn > 0) {
            Instant instant = Instant.now();
            long expiresInMilli = expiredIn * 60 * 1000;
            long timeStampSeconds = instant.toEpochMilli();
            generatePresignedUrlRequest = generatePresignedUrlRequest.withExpiration(new Date(timeStampSeconds + expiresInMilli));
        }
        return awsS3ClientBuilder.getAmazonS3().generatePresignedUrl(generatePresignedUrlRequest).toString();
    }

    /**
     * Upload objects to s3. Use for JSON or base 64 strings
     * @param bucketName
     * @param key
     * @param content
     * @return
     * @throws Exception
     */
    public String upload(String bucketName, String key, String content) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = getConfiguredClient(bucketName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length());
        String path = awsS3ClientBuilder.getEndpoint() + massageFilePath(key);
        awsS3ClientBuilder.getAmazonS3().putObject(bucketName, key, content);
        return path;
    }

    /**
     * Download file as a byte
     * @param bucketName
     * @param filePath
     * @return
     * @throws Exception
     */
    public byte[] downloadAsArray(String bucketName, final String filePath) throws Exception {
        S3Object s3Object = download(bucketName, filePath);
        byte[] data = IOUtils.toByteArray(s3Object.getObjectContent());
        closeS3Object(s3Object);
        return data;
    }

    /**
     * Download file as s3Object. Close the s3Object once content are read
     * @param bucketName
     * @param filePath
     * @return
     * @throws Exception
     */
    public S3Object download(String bucketName, final String filePath) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = getConfiguredClient(bucketName);
        try {
            return awsS3ClientBuilder.getAmazonS3().getObject(new GetObjectRequest(bucketName, filePath));
        } catch (Exception e) {
            log.error("Error during file download for bucket:{} path:{}", bucketName, filePath);
            throw new Exception(e);
        }
    }

    public ObjectMetadata downloadAsFile(String bucketName, String s3FilePath, File localFilePath) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = getConfiguredClient(bucketName);
        return awsS3ClientBuilder.getAmazonS3().getObject(new GetObjectRequest(bucketName, s3FilePath), localFilePath);
    }

    public void delete(String bucketName, final String filePath) throws Exception {
        AwsS3ClientBuilder awsS3ClientBuilder = getConfiguredClient(bucketName);
        awsS3ClientBuilder.getAmazonS3().deleteObject(bucketName, filePath);
    }

    protected String massageFilePath(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    private void closeS3Object(S3Object s3object) {
        if (null != s3object) {
            try {
                if (s3object.getObjectContent() != null) s3object.getObjectContent().close();
            } catch (Exception e) {
                log.error("Error while closing s3Object", e);
            }
        }
    }

    abstract AwsS3ClientBuilder getConfiguredClient(String bucketName) throws Exception;
}
