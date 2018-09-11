package com.example.example.controller;

import com.example.example.s3.S3Upload;
import com.example.example.sqs.Publisher;
import com.example.example.sqs.SqsSampleMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
@RequestMapping (value = "event")
public class ExampleController {
    @Autowired
    private Publisher publisher;
    @Autowired
    private S3Upload s3Upload;


    @RequestMapping (value = "/publish", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ApiResponse<String> messagePublish(@RequestBody SqsSampleMessage payload) {
        publisher.publish(payload);
        return ApiResponse.<String>builder().data("Message Published").build();
    }


    @RequestMapping (value = "/publish-delay", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ApiResponse<String> messagePublishWith(@RequestBody SqsSampleMessage payload, @RequestParam ("delay") long delay) {
        publisher.publishWithDelay(payload, delay);
        return ApiResponse.<String>builder().data("Message Published").build();
    }


    @PostMapping ("/upload")
    @ResponseBody
    public ApiResponse<String> singleFileUpload(@RequestParam ("file") MultipartFile file) throws Exception {
        try {
            byte[] bytes = file.getBytes();
            return ApiResponse.<String>builder().data(s3Upload.upload(bytes, file.getName())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.<String>builder().errorCode("TEST").errorResponse("Unable to upload").success(false).build();
        }
    }
}