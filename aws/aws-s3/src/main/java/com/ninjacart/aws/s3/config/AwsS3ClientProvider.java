package com.example.aws.s3.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3EncryptionClientBuilder;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import com.amazonaws.services.s3.model.StaticEncryptionMaterialsProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
 */
@Configuration
@Slf4j
@ConfigurationProperties(prefix = "cloud.aws.s3")
@ConditionalOnProperty(name="cloud.aws.s3.localStackEnabled", havingValue="false")
public class AwsS3ClientProvider extends AbstractAwsS3ClientProvider{

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
        AmazonS3 amazonS3= AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfiguration).withRegion(config.getRegion())
                .withCredentials(this.awsCredentialsProvider(config.getAccessKey(), config.getSecretKey())).build();
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
        AmazonS3 amazonS3 = AmazonS3EncryptionClientBuilder.standard().withRegion(config.getRegion()).
                withEncryptionMaterials(new StaticEncryptionMaterialsProvider(encryptionMaterials)).withCredentials(this.awsCredentialsProvider(config.getAccessKey(), config.getSecretKey())).build();
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
                simpleClient.put(e.getBucketName(), amazonS3Client(e));
                if (!StringUtils.isEmpty(e.getSymmetricKeyPath())) {
                    encryptionClient.put(e.getBucketName(), amazonS3EncryptionClient(e));
                }
            }
        }
    }

}