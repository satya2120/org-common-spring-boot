package com.example.aws.s3.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractAwsS3ClientProvider {
    private static final String AES_ENCRYPTION = "AES";

    protected List<S3Config> clients;

    protected Map<String, AwsS3ClientBuilder> simpleClient = new HashMap<>();

    protected Map<String, AwsS3ClientBuilder> encryptionClient = new HashMap<>();

    protected static SecretKey loadSymmetricAESKey(String path)
            throws IOException {
        File keyFile = new File(path);
        FileInputStream keyfis = new FileInputStream(keyFile);
        byte[] encodedPrivateKey = new byte[(int) keyFile.length()];
        keyfis.read(encodedPrivateKey);
        keyfis.close();
        return new SecretKeySpec(encodedPrivateKey, AES_ENCRYPTION);
    }


    protected AWSCredentialsProvider awsCredentialsProvider(String accessKey, String secretKey) {
        return new AWSCredentialsProvider() {
            @Override
            public AWSCredentials getCredentials() {
                return new BasicAWSCredentials(accessKey, secretKey);
            }

            @Override
            public void refresh() {

            }
        };

    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }


    public AwsS3ClientBuilder getSimpleClient(String bucketName) {
        return simpleClient.get(bucketName);
    }

    public AwsS3ClientBuilder getEncryptionClient(String bucketName) {
        return encryptionClient.get(bucketName);
    }

    public List<S3Config> getClients() {
        return clients;
    }

    public void setClients(List<S3Config> clients) {
        this.clients = clients;
    }

    abstract  public AwsS3ClientBuilder amazonS3Client(S3Config config);
    abstract  public AwsS3ClientBuilder amazonS3EncryptionClient(S3Config config) throws IOException;

}
