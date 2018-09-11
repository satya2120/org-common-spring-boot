package com.example.example.s3;

import com.example.aws.s3.service.AwsS3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class S3Upload {

    @Autowired private AwsS3Service awsS3Service;

    public String upload(byte[] fileBytes,String fileName) throws Exception {
       return awsS3Service.upload("test",fileName,fileBytes,"application/css");
    }


}
