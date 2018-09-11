package com.example.reactor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;


@ComponentScan("com.example")
public class TestMainClass {

    private static final String TEST_EVENT = "TEST-EVENT";

    public static void main(String[] args) throws InterruptedException {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(TestMainClass.class);
        final EventManager eventManager = applicationContext.getBean(EventManager.class);

        final Event event = eventManager.build(TEST_EVENT);
        final RegisteredEvent registeredEvent = eventManager.register(event);
        event.subscribe(new EventHandler<String>() {
            public void handle(String data) {
                System.out.println(data + " from " + Thread.currentThread().getName());
            }

            @Override
            public String getName() {
                return "TEST-HANDLER-2";
            }
        });

        event.subscribe(new EventHandler<String>() {
            public void handle(String data) {
                System.out.println(data + " from " + Thread.currentThread().getName());
            }

            @Override
            public String getName() {
                return "TEST-HANDLER-1";
            }
        });
        for (int i = 0; i < 10; i++) {
            registeredEvent.raise("World " + i);
        }

        Thread.currentThread().sleep(10000);
    }

}
