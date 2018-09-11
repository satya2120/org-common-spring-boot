package com.example.reactor.event;

import reactor.bus.Event;


public class ReactorEvent<T> extends Event<T>{
	public ReactorEvent(T data) {
		super(data);
	}
}
