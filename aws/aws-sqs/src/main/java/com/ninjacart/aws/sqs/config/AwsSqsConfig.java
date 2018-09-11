package com.example.aws.sqs.config;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.aws.core.env.StackResourceRegistryDetectingResourceIdResolver;
import org.springframework.cloud.aws.messaging.config.QueueMessageHandlerFactory;
import org.springframework.cloud.aws.messaging.config.SimpleMessageListenerContainerFactory;
import org.springframework.cloud.aws.messaging.config.annotation.EnableSqs;
import org.springframework.cloud.aws.messaging.core.QueueMessagingTemplate;
import org.springframework.cloud.aws.messaging.listener.QueueMessageHandler;
import org.springframework.cloud.aws.messaging.listener.SimpleMessageListenerContainer;
import org.springframework.cloud.aws.messaging.support.destination.DynamicQueueUrlDestinationResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.core.CachingDestinationResolverProxy;
import org.springframework.messaging.handler.annotation.support.PayloadArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.util.StringUtils;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.config.DynamicPropertyFactory;

@Configuration
@EnableSqs
public class AwsSqsConfig {

	@Value("${cloud.aws.sqs.credentials.accessKey:#{null}}") private String accessKey;
	@Value("${cloud.aws.sqs.credentials.secretKey:#{null}}") private String secretKey;
	@Value("${cloud.aws.sqs.region:#{null}}") private String sqsRegion;
	@Value("${cloud.aws.sqs.elasticmq.enabled:false}") private boolean elasticMqEnabled;
	@Value("${queue.endpoint:http://localhost:4570}") private String endpoint;

	@Bean
	public AmazonSQSAsync amazonSqsAsync() {
		AmazonSQSAsyncClientBuilder builder = AmazonSQSAsyncClientBuilder.standard()
				.withCredentials(awsCredentialsProvider());
		if(isElasticMQEnabled())
			builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, ""));
		else
			builder.withRegion(getSqsRegion());
		AmazonSQSAsync amazonSQSAsync = builder.build();
		return new AmazonSQSBufferedAsyncClient(amazonSQSAsync);
	}

	private String getSqsRegion() {
		if(!StringUtils.hasText(sqsRegion))
			sqsRegion = DynamicPropertyFactory.getInstance().getStringProperty("cloud.aws.sqs.region", "").getValue();
		return sqsRegion;
	}

	@Bean
	public AWSCredentialsProvider awsCredentialsProvider() {
		return new AWSCredentialsProvider() {
			@Override
			public AWSCredentials getCredentials() {
				if(!StringUtils.hasText(accessKey)) {
					String propAccessKey = DynamicPropertyFactory.getInstance().getStringProperty("cloud.aws.sqs.credentials.accessKey", "x").getValue();
					if(StringUtils.hasText(propAccessKey))
						accessKey = propAccessKey;
				}
				if(!StringUtils.hasText(secretKey)) {
					String propSecretKey = DynamicPropertyFactory.getInstance().getStringProperty("cloud.aws.sqs.credentials.secretKey", "x").getValue();
					if(StringUtils.hasText(propSecretKey))
						secretKey = propSecretKey;
				}
				return new BasicAWSCredentials(accessKey, secretKey);
			}

			@Override
			public void refresh() {

			}
		};

	}

	@Bean
	public QueueMessagingTemplate queueMessagingTemplate() {
		return new QueueMessagingTemplate(amazonSqsAsync(), cachingDestinationResolverProxy(), null);
	}

	@Bean
	public CachingDestinationResolverProxy<String> cachingDestinationResolverProxy() {
		DynamicQueueUrlDestinationResolver dynamicQueueUrlDestinationResolver = new DynamicQueueUrlDestinationResolver(amazonSqsAsync(), new StackResourceRegistryDetectingResourceIdResolver());
		dynamicQueueUrlDestinationResolver.setAutoCreate(true);
		return new CachingDestinationResolverProxy<>(dynamicQueueUrlDestinationResolver);
	}

	@Bean
	public SimpleMessageListenerContainer simpleMessageListenerContainer() {
		SimpleMessageListenerContainer msgListenerContainer = simpleMessageListenerContainerFactory().createSimpleMessageListenerContainer();
		msgListenerContainer.setMessageHandler(queueMessageHandler());
		return msgListenerContainer;
	}

	@Bean
	public SimpleMessageListenerContainerFactory simpleMessageListenerContainerFactory() {
		SimpleMessageListenerContainerFactory msgListenerContainerFactory = new SimpleMessageListenerContainerFactory();
		msgListenerContainerFactory.setAmazonSqs(amazonSqsAsync());
		msgListenerContainerFactory.setMaxNumberOfMessages(10);
		msgListenerContainerFactory.setVisibilityTimeout(300);
		msgListenerContainerFactory.setWaitTimeOut(20);
		msgListenerContainerFactory.setDestinationResolver(cachingDestinationResolverProxy());
		return msgListenerContainerFactory;
	}

	@Bean
	public QueueMessageHandler queueMessageHandler() {
		QueueMessageHandlerFactory factory = new QueueMessageHandlerFactory();
		factory.setAmazonSqs(amazonSqsAsync());
		MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
		messageConverter.setObjectMapper(new ObjectMapper()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.registerModule(new JavaTimeModule())
				.enable(SerializationFeature.INDENT_OUTPUT));
		messageConverter.setStrictContentTypeMatch(false);
		factory.setArgumentResolvers(Collections.<HandlerMethodArgumentResolver>singletonList(new PayloadArgumentResolver(messageConverter)));
		QueueMessageHandler queueMessageHandler = factory.createQueueMessageHandler();
		return queueMessageHandler;
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	private boolean isElasticMQEnabled() {
		return this.elasticMqEnabled || DynamicPropertyFactory.getInstance().getBooleanProperty("cloud.aws.sqs.elasticmq.enabled", false).getValue();
	}

}

