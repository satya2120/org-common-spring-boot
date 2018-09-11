package com.example.aws.sqs.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.aws.messaging.core.QueueMessagingTemplate;
import org.springframework.cloud.aws.messaging.core.SqsMessageHeaders;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class QueuePublisher {

	@Autowired private QueueMessagingTemplate queueMessagingTemplate;

	public <T> void sendMessageToQueue(String queueName, T payload) {
		sendMessageToQueue(queueName, payload, 0l);
	}

	public <T> void sendMessageToQueue(String queueName, T payload, Long delayInMillis) {
		Map<String, Object> headers = new HashMap<>();
		int delay = delayInMillis.intValue();
		if(delay > 0 && delay < 1000) {
			log.debug("Too small a delay: {}. Assuming 1000ms", delay);
			delay = 1000;
		}
		if(delay > 900000) {
			log.debug("Too big a delay: {}. Assuming 900s", delay);
			delay = 900000;
		}
		headers.put(SqsMessageHeaders.SQS_DELAY_HEADER, delay/1000);
		log.debug(String.format("Posting To Queue: %s Payload: %s", queueName, payload));
		queueMessagingTemplate.convertAndSend(queueName, payload, headers);
	}

}