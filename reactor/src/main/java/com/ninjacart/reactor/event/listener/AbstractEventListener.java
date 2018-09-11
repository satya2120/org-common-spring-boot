package com.example.reactor.event.listener;

import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;

import com.example.reactor.event.Eventer;
import com.example.reactor.event.ReactorEvent;

import lombok.extern.slf4j.Slf4j;
import reactor.fn.Consumer;


@Slf4j
public abstract class AbstractEventListener<T> implements Consumer<ReactorEvent<T>>{

	@Autowired private Eventer eventer;

	public abstract Set<String> getHandledEvents();
	public abstract void handleEvent(ReactorEvent<T> event);

	@PostConstruct
	public void registerListener(){
		for(String event : getHandledEvents()){
			eventer.registerEventHandler(event, this);
		}
	}

	public void accept(ReactorEvent<T> event) {
		log.debug("Handling Event: " + event.getKey());
		handleEvent(event);
	}

}
