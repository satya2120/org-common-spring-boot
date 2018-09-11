package com.example.reactor.event;

import static reactor.bus.selector.Selectors.$;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.reactor.event.listener.AbstractEventListener;

import lombok.extern.slf4j.Slf4j;
import reactor.bus.EventBus;

@Slf4j
@Service("eventer")
public class EventerImpl implements Eventer {

	@Autowired private EventBus reactor;
	
	@Override
	public <T> void raiseEvent(String eventName, ReactorEvent<T> eventData) {
		log.debug("Raising Event: " + eventName + " with EventData: " + eventData);
		reactor.notify(eventName, eventData);
	}

	@Override
	public <T> void registerEventHandler(String eventName, AbstractEventListener<T> eventHandler) {
		log.debug(eventHandler.getClass().getSimpleName() + " listening to event: " + eventName);
		reactor.on($(eventName), eventHandler);
	}
}
