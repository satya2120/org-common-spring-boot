#org-common

##SQS
###Configure

Sample

```
cloud:
  aws:
    sqs:
      credentials:
        accessKey: test
        secretKey: test
      region: ap-northeast-1
      elasticmq:
        enabled: true
```
Provide accessKey and secretKey for a perticular region of AWS.
Note: Do not enable elasticmq enabled true in production

###How To Use?

Publish
```
    @Autowired
    private QueuePublisher queuePublisher;

    /**
     * Messages will be visible in the queue instantly
     * @param sqsSampleMessage
     */
    public void publish(SqsSampleMessage sqsSampleMessage){
        queuePublisher.sendMessageToQueue(QueueNames.TEST_QUEUE,sqsSampleMessage);
    }
```

Listen

```
   /**
     * SqsListener will automatically listens on the registered queue on annotation. Any available messages will
     * be received at this method
     * @param sqsSampleMessage
     */
    @SqsListener(QueueNames.TEST_QUEUE)
    public void receiveMessages(SqsSampleMessage sqsSampleMessage){
        log.info("Received messages:{}",sqsSampleMessage.toString());
    }
```

##s3
###Configure

```
cloud:
  aws:
    s3:
      localStackEnabled: true
      localS3StackUrl: http://localhost:4569
      clients:
       -
        accessKey: foo
        secretKey: foo
        region: us-east-1
        baseUrl: http://localhost:4569
        bucketName: test
        maxConnectionPool: 10
        symmetricKeyPath:
```
localStackEnabled and localS3StackUrl should be used only if we want to test locally


| Param  | Use  | 
| :------------ |:---------------:| 
| accessKey      | From AWS and in case local keep foo | 
| secretKey     | From AWS and in case local keep foo        |   
| region | From AWS and in case local keep us-east-1       |    
|baseUrl| This URL will get embedded to the s3 path
|bucketName| Bucket Name from AWS |
|maxConnectionPool|Number of connections needed in thread pool for each bucket|
|symmetricKeyPath| If data needs to be encrypted during upload
|localStackEnabled| Make true in case localStack
|localS3StackUrl| Local stack URL as per docker-compose


###How To Use?

```
 @Autowired private AwsS3Service awsS3Service;

    public String upload(byte[] fileBytes,String fileName) throws Exception {
       return awsS3Service.upload("test",fileName,fileBytes,"application/css");
    }
```

##Reactor

Do not use -Needs testing