package com.example.reactor;

import com.example.reactor.config.ReactorConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.bus.EventBus;


public class RegisteredEvent<T> extends Event {

    private static final Logger LOG = LoggerFactory.getLogger(RegisteredEvent.class);
    private final ReactorConfiguration reactorConfiguration;

    RegisteredEvent(String name, EventBus eventBus, ReactorConfiguration reactorConfiguration) {
        super(name, eventBus);
        this.reactorConfiguration = reactorConfiguration;
    }

    public void raise(T data) {
        if (eventIsNotMuted()) {
            LOG.debug("Raising event : {}", getName());
            eventBus.notify(name, reactor.bus.Event.wrap(data));
        } else
            LOG.debug("Event : {} is muted.", getName());
    }

    private boolean eventIsNotMuted() {
        return !reactorConfiguration.isEventMuted(getName());
    }
}
