package com.example.example.sqs;

import com.example.aws.sqs.service.QueuePublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Publisher {
    @Autowired
    private QueuePublisher queuePublisher;

    /**
     * Messages will be visible in the queue instantly
     * @param sqsSampleMessage
     */
    public void publish(SqsSampleMessage sqsSampleMessage){
        queuePublisher.sendMessageToQueue(QueueNames.TEST_QUEUE,sqsSampleMessage);
    }

    /**
     * Messages will be available in queue after delay. Max delay supported by aws sqs is 15 minutes
     * @param sqsSampleMessage
     * @param delay
     */
    public void publishWithDelay(SqsSampleMessage sqsSampleMessage,long delay){
        queuePublisher.sendMessageToQueue(QueueNames.TEST_QUEUE,sqsSampleMessage,delay);
    }



}
