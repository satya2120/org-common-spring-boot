package com.example.reactor;


public interface EventHandler<T> {

    String getName();

    void handle(T data);

}
