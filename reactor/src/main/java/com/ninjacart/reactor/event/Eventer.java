package com.example.reactor.event;

import com.example.reactor.event.listener.AbstractEventListener;

public interface Eventer {
	public <T> void raiseEvent(String eventName, ReactorEvent<T> eventData);
	public <T> void registerEventHandler(String eventName, AbstractEventListener<T> eventHandler);
}
