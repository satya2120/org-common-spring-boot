package com.example.reactor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.reactor.event.listener.AbstractEventListener;

import reactor.bus.EventBus;
import reactor.bus.selector.ObjectSelector;
import reactor.fn.Consumer;


public class Event {

    private static final Logger LOG = LoggerFactory.getLogger(Event.class);
    protected final String name;
    protected final EventBus eventBus;

    public String getName() {
        return name;
    }

    Event(String name, EventBus eventBus) {
        this.name = name;
        this.eventBus = eventBus;
    }

    public <T> void subscribe(final EventHandler<T> eventHandler) {
        eventBus.on(new ObjectSelector<String,String>(name), new Consumer<reactor.bus.Event<T>>() {
            public void accept(reactor.bus.Event<T> event) {
                LOG.debug("Invoking the handler: {} for event: {}", eventHandler.getName(), name);
                eventHandler.handle(event.getData());
            }
        });
    }

}
