package com.example.reactor;

import com.example.reactor.config.ReactorConfiguration;
import com.example.reactor.exceptions.EventAlreadyRegisteredException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.bus.EventBus;

import java.util.HashMap;
import java.util.Map;


@Component
public class EventManager {

    private Map<String, Event> events = new HashMap<>();
    private Map<String, RegisteredEvent> registeredEvents = new HashMap<>();
    @Autowired private EventBus reactor;
    @Autowired private ReactorConfiguration reactorConfiguration;

    public Event build(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }
        if (!events.containsKey(name)) {
            final Event event = new Event(name, reactor);
            events.put(name, event);
            return event;
        } else {
            return events.get(name);
        }
    }

    public RegisteredEvent register(Event event) {
        if (!registeredEvents.containsKey(event.getName())) {
            final RegisteredEvent registeredEvent = new RegisteredEvent(event.getName(), reactor, reactorConfiguration);
            registeredEvents.put(registeredEvent.getName(), registeredEvent);
            return registeredEvent;
        } else {
            throw new EventAlreadyRegisteredException("This event is already registered. Are you sure the name that you are using is unique in JVM? name: " + event.getName());
        }
    }
}
