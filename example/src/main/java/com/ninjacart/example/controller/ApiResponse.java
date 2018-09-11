package com.example.example.controller;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiResponse<T> {
    private boolean success=true;
    private T data;
    private String errorCode;
    private String errorResponse;
}
