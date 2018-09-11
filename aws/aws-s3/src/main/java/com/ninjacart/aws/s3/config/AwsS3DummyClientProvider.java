package com.example.aws.s3.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3EncryptionClientBuilder;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * Only one bucket can be configured  against each accessKey.
 * If any other bucket is using same key create new entry in config.
 * This implementation provides a basic implementation of s3 without having aws s3 access.
 */
@Configuration
@Slf4j
@ConfigurationProperties(prefix = "cloud.aws.s3")
@ConditionalOnProperty(name="cloud.aws.s3.localStackEnabled", havingValue="true")
public class AwsS3DummyClientProvider extends AbstractAwsS3ClientProvider{

    @Value ("${cloud.aws.s3.localS3StackUrl:http://localhost:4569}") private String localS3StackUrl;

    /**
     * Builds an simple client with basic utilities.
     *
     * @param config
     * @return
     */
    public AwsS3ClientBuilder amazonS3Client(S3Config config) {
      ClientConfiguration clientConfiguration=new ClientConfiguration();
        if(config.getMaxConnectionPool()>0){
            clientConfiguration.setMaxConnections(config.getMaxConnectionPool());
        }
        AmazonS3ClientBuilder builder= AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfiguration)
                .withPathStyleAccessEnabled(true).withCredentials(this.awsCredentialsProvider(config.getAccessKey(), config.getSecretKey()));
        builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localS3StackUrl, config.getRegion()));
        AmazonS3 amazonS3=builder.build();
        return new AwsS3ClientBuilder(amazonS3, config.getBaseUrl(), config.getBucketName());
    }

    /**
     * Builds an symmetric AES encryption client with basic utilities .
     * Generate symmetric key as per s3 standards.
     *
     * @param config
     * @return
     * @throws InvalidKeySpecException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws IOException
     */
    public AwsS3ClientBuilder amazonS3EncryptionClient(S3Config config) throws IOException {
        SecretKey symmetricKey = loadSymmetricAESKey(config.getSymmetricKeyPath());
        EncryptionMaterials encryptionMaterials = new EncryptionMaterials(symmetricKey);
        AmazonS3 amazonS3 = AmazonS3EncryptionClientBuilder.standard().
                withEncryptionMaterials(new StaticEncryptionMaterialsProvider(encryptionMaterials)).
                withPathStyleAccessEnabled(true).withCredentials(this.awsCredentialsProvider(config.getAccessKey(), config.getSecretKey()))
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localS3StackUrl, config.getRegion())).build();
        return new AwsS3ClientBuilder(amazonS3, config.getBaseUrl(), config.getBucketName());
    }

    /**
     * Builds Aws clients based upon the configuration
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws InvalidKeyException
     * @throws IOException
     */
    @PostConstruct
    void init() throws IOException {
        if (clients != null && !clients.isEmpty()) {
            for (S3Config e : clients) {
                AwsS3ClientBuilder clientBuilder=amazonS3Client(e);
                createBucket(clientBuilder);
                simpleClient.put(e.getBucketName(), amazonS3Client(e));
                if (!StringUtils.isEmpty(e.getSymmetricKeyPath())) {
                    encryptionClient.put(e.getBucketName(), amazonS3EncryptionClient(e));
                }
            }
        }
    }

    private void createBucket(AwsS3ClientBuilder clientBuilder){
        if(!clientBuilder.getAmazonS3().doesBucketExistV2(clientBuilder.getBucketName()))
            clientBuilder.getAmazonS3().createBucket(clientBuilder.getBucketName());
    }

}