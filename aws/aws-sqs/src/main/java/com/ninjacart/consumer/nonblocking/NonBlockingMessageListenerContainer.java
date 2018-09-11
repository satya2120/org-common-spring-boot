package com.example.consumer.nonblocking;

import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import org.springframework.cloud.aws.messaging.listener.QueueMessageAcknowledgment;
import org.springframework.cloud.aws.messaging.listener.SqsMessageDeletionPolicy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.cloud.aws.messaging.core.QueueMessageUtils.createMessage;

 class NonBlockingMessageListenerContainer extends AbstractMessageListenerContainer {
	static final String LOGICAL_RESOURCE_ID = "LogicalResourceId";
	static final String ACKNOWLEDGMENT = "Acknowledgment";
	private static final int DEFAULT_MIN_AVAILABLE_SLOTS_TO_POLL_THRESHOLD = 1;
	private static final int DEFAULT_MAX_FREE_WORKER_SLOT_WAIT_TIME_MS = 1000;
	private static final int DEFAULT_WORKER_THREADS = 2;
	private static final String DEFAULT_THREAD_NAME_PREFIX =
			ClassUtils.getShortName(NonBlockingMessageListenerContainer.class) + "-";

	private boolean defaultTaskExecutor;
	private long backOffTime = 10000;
	private long queueStopTimeout = 10000;

	private AsyncTaskExecutor taskExecutor;
	private ConcurrentHashMap<String, Future<?>> scheduledFutureByQueue;
	private ConcurrentHashMap<String, Boolean> runningStateByQueue;

	protected AsyncTaskExecutor getTaskExecutor() {
		return this.taskExecutor;
	}

	public void setTaskExecutor(final AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}


	public long getBackOffTime() {
		return this.backOffTime;
	}


	public void setBackOffTime(final long backOffTime) {
		this.backOffTime = backOffTime;
	}


	public long getQueueStopTimeout() {
		return this.queueStopTimeout;
	}

	/**
	 * The number of milliseconds the {@link #stop(String)} method waits for a queue
	 * to stop before interrupting the current thread. Default value is 10000 milliseconds (10 seconds).
	 *
	 * @param queueStopTimeout
	 * 		in milliseconds
	 */
	public void setQueueStopTimeout(final long queueStopTimeout) {
		this.queueStopTimeout = queueStopTimeout;
	}

	@Override
	protected void initialize() {
		if (this.taskExecutor == null) {
			this.defaultTaskExecutor = true;
			this.taskExecutor = createDefaultTaskExecutor();
		}
		super.initialize();
		initializeRunningStateByQueue();
		this.scheduledFutureByQueue = new ConcurrentHashMap<>(getRegisteredQueues().size());
	}

	private void initializeRunningStateByQueue() {
		this.runningStateByQueue = new ConcurrentHashMap<>(getRegisteredQueues().size());
		for (String queueName : getRegisteredQueues().keySet()) {
			this.runningStateByQueue.put(queueName, false);
		}
	}

	@Override
	protected void doStart() {
		synchronized (this.getLifecycleMonitor()) {
			scheduleMessageListeners();
		}
	}

	@Override
	protected void doStop() {
		for (Map.Entry<String, Boolean> runningStateByQueue : this.runningStateByQueue.entrySet()) {
			if (runningStateByQueue.getValue()) {
				stop(runningStateByQueue.getKey());
			}
		}
	}

	@Override
	protected void doDestroy() {
		if (this.defaultTaskExecutor) {
			((ThreadPoolTaskExecutor) this.taskExecutor).destroy();
		}
	}

	/**
	 * Create a default TaskExecutor. Called if no explicit TaskExecutor has been specified.
	 * <p>The default implementation builds a {@link org.springframework.core.task.SimpleAsyncTaskExecutor}
	 * with the specified bean name (or the class name, if no bean name specified) as thread name prefix.
	 *
	 * @return a {@link org.springframework.core.task.SimpleAsyncTaskExecutor} configured with the thread name prefix
	 * @see org.springframework.core.task.SimpleAsyncTaskExecutor#SimpleAsyncTaskExecutor(String)
	 */
	protected AsyncTaskExecutor createDefaultTaskExecutor() {
		String beanName = getBeanName();
		ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
		threadPoolTaskExecutor.setThreadNamePrefix(beanName != null ? beanName + "-" : DEFAULT_THREAD_NAME_PREFIX);
		int spinningThreads = this.getRegisteredQueues().size();

		if (spinningThreads > 0) {
			threadPoolTaskExecutor.setCorePoolSize(spinningThreads * DEFAULT_WORKER_THREADS);

			int maxNumberOfMessagePerBatch = getMaxNumberOfMessages() != null ? getMaxNumberOfMessages() : DEFAULT_WORKER_THREADS;
			threadPoolTaskExecutor.setMaxPoolSize(spinningThreads * maxNumberOfMessagePerBatch);
		}

		// No use of a thread pool executor queue to avoid retaining message to long in memory
		threadPoolTaskExecutor.setQueueCapacity(0);
		threadPoolTaskExecutor.afterPropertiesSet();

		return threadPoolTaskExecutor;

	}

	private void scheduleMessageListeners() {
		for (Map.Entry<String, QueueAttributes> registeredQueue : getRegisteredQueues().entrySet()) {
			startQueue(registeredQueue.getKey(), registeredQueue.getValue());
		}
	}

	protected void executeMessage(org.springframework.messaging.Message<String> stringMessage) {
		getMessageHandler().handleMessage(stringMessage);
	}

	/**
	 * Stops and waits until the specified queue has stopped. If the wait timeout specified by {@link }
	 * is reached, the current thread is interrupted.
	 *
	 * @param logicalQueueName
	 * 		the name as defined on the listener method
	 */
	public void stop(String logicalQueueName) {
		stopQueue(logicalQueueName);

		try {
			if (isRunning(logicalQueueName)) {
				Future<?> future = this.scheduledFutureByQueue.remove(logicalQueueName);
				future.get(this.queueStopTimeout, TimeUnit.MILLISECONDS);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException | TimeoutException e) {
			getLogger().warn("Error stopping queue with name: '" + logicalQueueName + "'", e);
		}
	}

	protected void stopQueue(final String logicalQueueName) {
		Assert.isTrue(this.runningStateByQueue.containsKey(logicalQueueName), "Queue with name '" + logicalQueueName + "' does not exist");
		this.runningStateByQueue.put(logicalQueueName, false);
	}

	public void start(final String logicalQueueName) {
		Assert.isTrue(this.runningStateByQueue.containsKey(logicalQueueName), "Queue with name '" + logicalQueueName + "' does not exist");

		QueueAttributes queueAttributes = this.getRegisteredQueues().get(logicalQueueName);
		startQueue(logicalQueueName, queueAttributes);
	}

	/**
	 * Checks if the spinning thread for the specified queue {@code logicalQueueName} is still running (polling for new
	 * messages) or not.
	 *
	 * @param logicalQueueName
	 * 		the name as defined on the listener method
	 * @return {@code true} if the spinning thread for the specified queue is running otherwise {@code false}.
	 */
	public boolean isRunning(final String logicalQueueName) {
		Future<?> future = this.scheduledFutureByQueue.get(logicalQueueName);
		return future != null && !future.isCancelled() && !future.isDone();
	}

	protected void startQueue(final String queueName, QueueAttributes queueAttributes) {
		if (this.runningStateByQueue.containsKey(queueName) && this.runningStateByQueue.get(queueName)) {
			return;
		}

		this.runningStateByQueue.put(queueName, true);
		Future<?> future = getTaskExecutor().submit(new AsynchronousMessageListener(queueName, queueAttributes));
		this.scheduledFutureByQueue.put(queueName, future);
	}

	class AsynchronousMessageListener implements Runnable {

		private final QueueAttributes queueAttributes;
		private final String logicalQueueName;
		private final AtomicInteger availableSlots;

		AsynchronousMessageListener(final String logicalQueueName, final QueueAttributes queueAttributes) {
			this.logicalQueueName = logicalQueueName;
			this.queueAttributes = queueAttributes;
			this.availableSlots = new AtomicInteger(this.queueAttributes.getReceiveMessageRequest().getMaxNumberOfMessages());
		}

		private int calculateMinimumAvailableSlotsToPollThreshold() {
			if (this.queueAttributes.getReceiveMessageRequest().getMaxNumberOfMessages() == null) {
				return DEFAULT_MIN_AVAILABLE_SLOTS_TO_POLL_THRESHOLD;
			} else {
				return Math.max(
						(DEFAULT_MIN_AVAILABLE_SLOTS_TO_POLL_THRESHOLD),
						(this.queueAttributes.getReceiveMessageRequest().getMaxNumberOfMessages() / 2)
				);
			}
		}

		private int calculateMaximumFreeWorkerSlotWaitTime() {
			if (this.queueAttributes.getReceiveMessageRequest().getWaitTimeSeconds() == null) {
				return DEFAULT_MAX_FREE_WORKER_SLOT_WAIT_TIME_MS;
			} else {
				return Math.max(
						(DEFAULT_MAX_FREE_WORKER_SLOT_WAIT_TIME_MS),
						(this.queueAttributes.getReceiveMessageRequest().getWaitTimeSeconds() * 1000)
				);
			}
		}

		@Override
		public void run() {

			final int maximumFreeWorkerSlotWaitTime = calculateMaximumFreeWorkerSlotWaitTime();
			final int minimumAvailableSlotsToPollThreshold = calculateMinimumAvailableSlotsToPollThreshold();

			while (isQueueRunning()) {
				try {
					// if we don't have any slots, wait for some to become available
					if (availableSlots.get() == 0) {
						synchronized (availableSlots) {
							availableSlots.wait(maximumFreeWorkerSlotWaitTime);
						}
						continue;
					}

					// receive as many messages as we have slots available
					ReceiveMessageResult receiveMessageResult = getAmazonSqs().receiveMessage(this
							.queueAttributes
							.getReceiveMessageRequest()
							.withMaxNumberOfMessages(availableSlots.get())
					);

					for (Message message : receiveMessageResult.getMessages()) {
						availableSlots.decrementAndGet();
						if (isQueueRunning()) {
							MessageExecutor messageExecutor = new MessageExecutor(
									this.logicalQueueName,
									message,
									this.queueAttributes
							);
							getTaskExecutor().execute(new SignalExecutingRunnable(
									messageExecutor,
									availableSlots,
									minimumAvailableSlotsToPollThreshold
							));
						} else {
							availableSlots.incrementAndGet();
						}
					}

				} catch (Exception e) {
					getLogger().warn("An Exception occurred while polling queue '{}'. The failing operation will be " +
							"retried in {} milliseconds", this.logicalQueueName, getBackOffTime(), e);
					try {
						//noinspection BusyWait
						Thread.sleep(getBackOffTime());
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}

		private boolean isQueueRunning() {
			if (NonBlockingMessageListenerContainer.this.runningStateByQueue.containsKey(this.logicalQueueName)) {
				return NonBlockingMessageListenerContainer.this.runningStateByQueue.get(this.logicalQueueName);
			} else {
				getLogger().warn("Stopped queue '" + this.logicalQueueName + "' because it was not listed as running queue.");
				return false;
			}
		}
	}

	private class MessageExecutor implements Runnable {

		private final Message message;
		private final String logicalQueueName;
		private final String queueUrl;
		private final boolean hasRedrivePolicy;
		private final SqsMessageDeletionPolicy deletionPolicy;

		private MessageExecutor(String logicalQueueName, Message message, QueueAttributes queueAttributes) {
			this.logicalQueueName = logicalQueueName;
			this.message = message;
			this.queueUrl = queueAttributes.getReceiveMessageRequest().getQueueUrl();
			this.hasRedrivePolicy = queueAttributes.hasRedrivePolicy();
			this.deletionPolicy = queueAttributes.getDeletionPolicy();
		}

		@Override
		public void run() {
			String receiptHandle = this.message.getReceiptHandle();
			org.springframework.messaging.Message<String> queueMessage = getMessageForExecution();
			try {
				executeMessage(queueMessage);
				applyDeletionPolicyOnSuccess(receiptHandle);
			} catch (MessagingException messagingException) {
				applyDeletionPolicyOnError(receiptHandle, messagingException);
			}
		}

		private void applyDeletionPolicyOnSuccess(final String receiptHandle) {
			if (this.deletionPolicy == SqsMessageDeletionPolicy.ON_SUCCESS ||
					this.deletionPolicy == SqsMessageDeletionPolicy.ALWAYS ||
					this.deletionPolicy == SqsMessageDeletionPolicy.NO_REDRIVE) {
				deleteMessage(receiptHandle);
			}
		}

		private void applyDeletionPolicyOnError(final String receiptHandle, final MessagingException messagingException) {
			if (this.deletionPolicy == SqsMessageDeletionPolicy.ALWAYS ||
					(this.deletionPolicy == SqsMessageDeletionPolicy.NO_REDRIVE && !this.hasRedrivePolicy)) {
				deleteMessage(receiptHandle);
			} else if (this.deletionPolicy == SqsMessageDeletionPolicy.ON_SUCCESS) {
				getLogger().error("Exception encountered while processing message.", messagingException);
			}
		}

		private void deleteMessage(final String receiptHandle) {
			getAmazonSqs().deleteMessageAsync(new DeleteMessageRequest(this.queueUrl, receiptHandle));
		}

		private org.springframework.messaging.Message<String> getMessageForExecution() {
			HashMap<String, Object> additionalHeaders = new HashMap<>();
			additionalHeaders.put(LOGICAL_RESOURCE_ID, this.logicalQueueName);
			if (this.deletionPolicy == SqsMessageDeletionPolicy.NEVER) {
				String receiptHandle = this.message.getReceiptHandle();
				QueueMessageAcknowledgment acknowledgment = new QueueMessageAcknowledgment(NonBlockingMessageListenerContainer.this.getAmazonSqs(), this.queueUrl, receiptHandle);
				additionalHeaders.put(ACKNOWLEDGMENT, acknowledgment);
			}

			return createMessage(this.message, additionalHeaders);
		}
	}

	private static class SignalExecutingRunnable implements Runnable {
		private final Runnable runnable;
		private final AtomicInteger availableSlots;
		private final int minimumAvailableSlotsToPollThreshold;

		private SignalExecutingRunnable(
				final Runnable runnable,
				final AtomicInteger availableSlots,
				final int minimumAvailableSlotsToPollThreshold
		) {
			this.minimumAvailableSlotsToPollThreshold = minimumAvailableSlotsToPollThreshold;
			this.availableSlots = availableSlots;
			this.runnable = runnable;
		}

		@Override
		public void run() {
			try {
				this.runnable.run();
			} finally {
				// after finishing this work, are we ready to fetch more messages off the queue?
				if (this.availableSlots.incrementAndGet() >= this.minimumAvailableSlotsToPollThreshold) {
					// if so, wake up the message receiving worker
					synchronized (this.availableSlots) {
						this.availableSlots.notify();
					}
				}
			}
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