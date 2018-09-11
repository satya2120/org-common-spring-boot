#Sample

Install docker-compose to test this locally

If you already have queues/bucket created at aws you can specify credentials at application.yml

##Test Installation

###Mac

Follow instruction [https://docs.docker.com/docker-for-mac/install/]

Go to example directory and run 
```
TMPDIR=/private$TMPDIR docker-compose up
```
###Linux

Install docker engine and docker compose.

Go to example directory and run 
 
```
docker-compose up
```
For more explanation look at docker-compose.yml

##Sqs Sample Request:

###Without Delay:
```
curl -X POST \
  http://0.0.0.0:8449/example/event/publish \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -d '{
		"message":"OrgTech"
}'
```
###With delay:
```
curl -X POST \
  'http://0.0.0.0:8449/example/event/publish-delay?delay=60000' \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -d '{
	"message":"OrgTech" }'
```

##S3 Sample Request:
```
curl -X POST \
  http://0.0.0.0:8449/example/event/upload \
  -H 'Cache-Control: no-cache' \
  -H 'Content-Type: application/json' \
  -H 'content-type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW' \
  -F file=@/file_location
  ```