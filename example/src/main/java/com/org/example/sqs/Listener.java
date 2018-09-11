package com.example.example.sqs;


import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Listener {

    /**
     * SqsListener will automatically listens on the registered queue on annotation. Any available messages will
     * be received at this method
     * @param sqsSampleMessage
     */
    @SqsListener(QueueNames.TEST_QUEUE)
    public void receiveMessages(SqsSampleMessage sqsSampleMessage){
        log.info("Received messages:{}",sqsSampleMessage.toString());
    }


}
