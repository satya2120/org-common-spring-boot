
package com.example.consumer.nonblocking;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.aws.core.env.ResourceIdResolver;
import org.springframework.cloud.aws.messaging.listener.QueueMessageHandler;
import org.springframework.cloud.aws.messaging.listener.SqsMessageDeletionPolicy;
import org.springframework.cloud.aws.messaging.support.destination.DynamicQueueUrlDestinationResolver;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.core.CachingDestinationResolverProxy;
import org.springframework.messaging.core.DestinationResolutionException;
import org.springframework.messaging.core.DestinationResolver;
import org.springframework.util.Assert;

abstract class AbstractMessageListenerContainer implements InitializingBean, DisposableBean, SmartLifecycle, BeanNameAware {
    private static final String RECEIVING_ATTRIBUTES = "All";
    private static final String RECEIVING_MESSAGE_ATTRIBUTES = "All";
    private static final int DEFAULT_MAX_NUMBER_OF_MESSAGES = 10;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Object lifecycleMonitor = new Object();
    private final Map<String, AbstractMessageListenerContainer.QueueAttributes> registeredQueues = new HashMap();
    private AmazonSQSAsync amazonSqs;
    private DestinationResolver<String> destinationResolver;
    private String beanName;
    private QueueMessageHandler messageHandler;
    private Integer maxNumberOfMessages;
    private Integer visibilityTimeout;
    private ResourceIdResolver resourceIdResolver;
    private Integer waitTimeOut;
    private boolean autoStartup = true;
    private int phase = 2147483647;
    private boolean active;
    private boolean running;

    AbstractMessageListenerContainer() {
    }

    protected Map<String, AbstractMessageListenerContainer.QueueAttributes> getRegisteredQueues() {
        return Collections.unmodifiableMap(this.registeredQueues);
    }

    protected QueueMessageHandler getMessageHandler() {
        return this.messageHandler;
    }

    public void setMessageHandler(QueueMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    protected Object getLifecycleMonitor() {
        return this.lifecycleMonitor;
    }

    protected Logger getLogger() {
        return this.logger;
    }

    protected AmazonSQSAsync getAmazonSqs() {
        return this.amazonSqs;
    }

    public void setAmazonSqs(AmazonSQSAsync amazonSqs) {
        this.amazonSqs = amazonSqs;
    }

    protected DestinationResolver<String> getDestinationResolver() {
        return this.destinationResolver;
    }

    public void setDestinationResolver(DestinationResolver<String> destinationResolver) {
        this.destinationResolver = destinationResolver;
    }

    protected String getBeanName() {
        return this.beanName;
    }

    public void setBeanName(String name) {
        this.beanName = name;
    }

    protected Integer getMaxNumberOfMessages() {
        return this.maxNumberOfMessages;
    }

    public void setMaxNumberOfMessages(Integer maxNumberOfMessages) {
        this.maxNumberOfMessages = maxNumberOfMessages;
    }

    protected Integer getVisibilityTimeout() {
        return this.visibilityTimeout;
    }

    public void setVisibilityTimeout(Integer visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    public void setResourceIdResolver(ResourceIdResolver resourceIdResolver) {
        this.resourceIdResolver = resourceIdResolver;
    }

    protected Integer getWaitTimeOut() {
        return this.waitTimeOut;
    }

    public void setWaitTimeOut(Integer waitTimeOut) {
        this.waitTimeOut = waitTimeOut;
    }

    public boolean isAutoStartup() {
        return this.autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public void stop(Runnable callback) {
        this.stop();
        callback.run();
    }

    public int getPhase() {
        return this.phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public boolean isActive() {
        synchronized(this.getLifecycleMonitor()) {
            return this.active;
        }
    }

    public void afterPropertiesSet() throws Exception {
        this.validateConfiguration();
        this.initialize();
    }

    private void validateConfiguration() {
        Assert.state(this.amazonSqs != null, "amazonSqs must not be null");
        Assert.state(this.messageHandler != null, "messageHandler must not be null");
    }

    protected void initialize() {
        synchronized(this.getLifecycleMonitor()) {
            if(this.destinationResolver == null) {
                if(this.resourceIdResolver == null) {
                    this.destinationResolver = new CachingDestinationResolverProxy(new DynamicQueueUrlDestinationResolver(this.amazonSqs));
                } else {
                    this.destinationResolver = new CachingDestinationResolverProxy(new DynamicQueueUrlDestinationResolver(this.amazonSqs, this.resourceIdResolver));
                }
            }

            Iterator var2 = this.messageHandler.getHandlerMethods().keySet().iterator();

            while(var2.hasNext()) {
                MappingInformation mappingInformation = (MappingInformation)var2.next();
                Iterator var4 = mappingInformation.getLogicalResourceIds().iterator();

                while(var4.hasNext()) {
                    String queue = (String)var4.next();
                    AbstractMessageListenerContainer.QueueAttributes queueAttributes = this.queueAttributes(queue, mappingInformation.getDeletionPolicy());
                    if(queueAttributes != null) {
                        this.registeredQueues.put(queue, queueAttributes);
                    }
                }
            }

            this.active = true;
            this.getLifecycleMonitor().notifyAll();
        }
    }

    public void start() {
        this.getLogger().debug("Starting container with name {}", this.getBeanName());
        synchronized(this.getLifecycleMonitor()) {
            this.running = true;
            this.getLifecycleMonitor().notifyAll();
        }

        this.doStart();
    }

    private AbstractMessageListenerContainer.QueueAttributes queueAttributes(String queue, SqsMessageDeletionPolicy deletionPolicy) {
        String destinationUrl;
        try {
            destinationUrl = (String)this.getDestinationResolver().resolveDestination(queue);
        } catch (DestinationResolutionException var6) {
            if(this.getLogger().isDebugEnabled()) {
                this.getLogger().debug("Ignoring queue with name \'" + queue + "\' as it does not exist.", var6);
            } else {
                this.getLogger().warn("Ignoring queue with name \'" + queue + "\' as it does not exist.");
            }

            return null;
        }

        GetQueueAttributesResult queueAttributes = this.getAmazonSqs().getQueueAttributes((new GetQueueAttributesRequest(destinationUrl)).withAttributeNames(new QueueAttributeName[]{QueueAttributeName.RedrivePolicy}));
        boolean hasRedrivePolicy = queueAttributes.getAttributes().containsKey(QueueAttributeName.RedrivePolicy.toString());
        return new AbstractMessageListenerContainer.QueueAttributes(hasRedrivePolicy, deletionPolicy, destinationUrl, this.getMaxNumberOfMessages(), this.getVisibilityTimeout(), this.getWaitTimeOut());
    }

    public void stop() {
        this.getLogger().debug("Stopping container with name {}", this.getBeanName());
        synchronized(this.getLifecycleMonitor()) {
            this.running = false;
            this.getLifecycleMonitor().notifyAll();
        }

        this.doStop();
    }

    public boolean isRunning() {
        synchronized(this.getLifecycleMonitor()) {
            return this.running;
        }
    }

    public void destroy() {
        Object var1 = this.lifecycleMonitor;
        synchronized(this.lifecycleMonitor) {
            this.stop();
            this.active = false;
            this.doDestroy();
        }
    }

    protected abstract void doStart();

    protected abstract void doStop();

    protected void doDestroy() {
    }

    protected static class QueueAttributes {
        private final boolean hasRedrivePolicy;
        private final SqsMessageDeletionPolicy deletionPolicy;
        private final String destinationUrl;
        private final Integer maxNumberOfMessages;
        private final Integer visibilityTimeout;
        private final Integer waitTimeOut;

        public QueueAttributes(boolean hasRedrivePolicy, SqsMessageDeletionPolicy deletionPolicy, String destinationUrl, Integer maxNumberOfMessages, Integer visibilityTimeout, Integer waitTimeOut) {
            this.hasRedrivePolicy = hasRedrivePolicy;
            this.deletionPolicy = deletionPolicy;
            this.destinationUrl = destinationUrl;
            this.maxNumberOfMessages = maxNumberOfMessages;
            this.visibilityTimeout = visibilityTimeout;
            this.waitTimeOut = waitTimeOut;
        }

        public boolean hasRedrivePolicy() {
            return this.hasRedrivePolicy;
        }

        public ReceiveMessageRequest getReceiveMessageRequest() {
            ReceiveMessageRequest receiveMessageRequest = (new ReceiveMessageRequest(this.destinationUrl)).withAttributeNames(new String[]{"All"}).withMessageAttributeNames(new String[]{"All"});
            if(this.maxNumberOfMessages != null) {
                receiveMessageRequest.withMaxNumberOfMessages(this.maxNumberOfMessages);
            } else {
                receiveMessageRequest.withMaxNumberOfMessages(Integer.valueOf(10));
            }

            if(this.visibilityTimeout != null) {
                receiveMessageRequest.withVisibilityTimeout(this.visibilityTimeout);
            }

            if(this.waitTimeOut != null) {
                receiveMessageRequest.setWaitTimeSeconds(this.waitTimeOut);
            }

            return receiveMessageRequest;
        }

        public SqsMessageDeletionPolicy getDeletionPolicy() {
            return this.deletionPolicy;
        }
    }

    protected static class MappingInformation implements Comparable<MappingInformation> {
        private final Set<String> logicalResourceIds;
        private final SqsMessageDeletionPolicy deletionPolicy;

        public MappingInformation(Set<String> logicalResourceIds, SqsMessageDeletionPolicy deletionPolicy) {
            this.logicalResourceIds = Collections.unmodifiableSet(logicalResourceIds);
            this.deletionPolicy = deletionPolicy;
        }

        public Set<String> getLogicalResourceIds() {
            return this.logicalResourceIds;
        }

        public SqsMessageDeletionPolicy getDeletionPolicy() {
            return this.deletionPolicy;
        }

        public int compareTo(MappingInformation o) {
            return 0;
        }
    }
}
