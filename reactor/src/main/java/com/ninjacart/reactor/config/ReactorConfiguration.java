package com.example.reactor.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.netflix.config.DynamicPropertyFactory;

import lombok.extern.slf4j.Slf4j;
import reactor.Environment;
import reactor.bus.EventBus;
import reactor.core.dispatch.SynchronousDispatcher;

@Slf4j
@EnableAsync
@Configuration
public class ReactorConfiguration implements AsyncConfigurer{

	@Autowired private org.springframework.core.env.Environment environment;

	@Bean
	public Environment env() {
		return Environment.initializeIfEmpty().assignErrorJournal();
	}

	@Bean 
	public EventBus reactor(Environment env) {
		if(environment.acceptsProfiles("test"))
			return EventBus.create(env, SynchronousDispatcher.INSTANCE);
		return EventBus.create(env, Environment.THREAD_POOL);
	}

	@Override
	public Executor getAsyncExecutor() {
		if(environment.acceptsProfiles("test"))
			return new SynchronousDispatcher();
		return  delegateAsyncTaskExecutor();
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return new AsyncUncaughtExceptionHandler() {
			@Override
			public void handleUncaughtException(Throwable ex, Method method, Object... params) {
				log.error("Async Uncaught Exception: " + ex, ex);
			}
		};
	}


	@Bean
	public AsyncTaskExecutor delegateAsyncTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(getReactorPoolSize());
		executor.setMaxPoolSize(getReactorBacklogSize());
		executor.setQueueCapacity(getReactorBacklogSize());
		executor.setThreadNamePrefix(getReactorThreadsNamePrefix());
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}

	private int getReactorBacklogSize() {
		return DynamicPropertyFactory.getInstance().getIntProperty("reactor.backlog.size", 2048).getValue();
	}

	private int getReactorPoolSize() {
		return DynamicPropertyFactory.getInstance().getIntProperty("reactor.pool.size", 256).getValue();
	}

	private String getReactorThreadsNamePrefix() {
		return DynamicPropertyFactory.getInstance().getStringProperty("reactor.threads.name.prefix","reactorThread").getValue();
	}

	public Boolean isEventMuted(String eventName){
		return DynamicPropertyFactory.getInstance().getBooleanProperty("reactor.event." + eventName + ".mute", Boolean.FALSE).getValue();
	}

}
