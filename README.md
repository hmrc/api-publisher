# API Publisher
When an API is published through the publish-api Jenkins job,
the API Publisher retrieves its API definition and OAS specification, 
then publishes its definition to the API Definition service.

## What uses this service?
* The publish-api Jenkins job
* [API Gatekeeper Frontend](https://github.com/hmrc/api-gatekeeper-frontend) (SDST allowing API to be published)

## What does this service use?
* [API Definition](https://github.com/HMRC/api-definition)
* [API Subscription Fields](https://github.com/hmrc/api-subscription-fields)
* Metrics/Grafana/Kibana
* Additionally this calls every configured API microservice (asks for API definition and OAS files)

## Endpoints

### POST /publish
Jenkins uses this endpoint to notify of a new microservice
### Request Payload Example
```json
{
  "serviceName": "hello-world",
  "serviceUrl": "http://hello-world.example.com",
  "metadata": {
    "key1": "value1",
    "key2": "value2"
  }
}
```

### Responses
#### 200 OK
The request was successful and the API Definition has been published
##### Response Payload Example
```json
{
  "name": "Hello World",
  "serviceName": "hello-world",
  "context": "test/hello",
  "description": "A 'hello world' example API",
  "versions": [
    {
      "version": "1.0",
      "status": "STABLE"
    },
    {
      "version": "2.0",
      "status": "ALPHA"
    }
  ]
}
```
Possible statuses: ALPHA, BETA, STABLE, DEPRECATED, RETIRED

#### 202 Accepted
The request was successful but the API is awaiting approval in Gatekeeper and has not been published.
The response payload is the same as above when the API is published.

#### 400 Bad Request
The response will contain information regarding why the request could not be understood
```json
{
  "statusCode": 400,
  "message": "Invalid Json: No content to map due to end-of-input\n at [Source: (org.apache.pekko.util.ByteIterator$ByteArrayIterator$$anon$1); line: 1, column: 0]"
}
```
#### 401 Unauthorized
```json
{
  "code": "UNAUTHORIZED",
  "message": "Agent must be authorised to perform Publish or Validate actions"
}
```
#### 415 Unsupported Media Type
```json
{
  "statusCode": 415,
  "message": "Expecting text/json or application/json body"
}
```
#### 422 Unprocessable Entity - Invalid Request Payload
```json
{
  "code": "API_PUBLISHER_INVALID_REQUEST_PAYLOAD",
  "message": {
    "obj.serviceName": [
      {
        "msg": [
          "error.path.missing"
        ],
        "args": []
      }
    ]
  }
}
```
#### 500 Internal Server Error
```json
{
  "code": "API_PUBLISHER_UNKNOWN_ERROR",
  "message": "An unexpected error occurred: GET of 'http://localhost/api/definition' failed. Caused by: 'Connection refused: localhost/127.0.0.1:80'"
}
```

### POST /validate
### Request Payload Example
```json
{
  "api": {
    "name":"Exmaple API",
    "description":"An example API",
    "context":"test/example",
    "versions":[
      {
        "version":"1.0",
        "access":
        {
          "type":"PRIVATE"
        },
        "status":"STABLE",
        "fieldDefinitions":[]
      }
    ]
  }
}
```

### Responses
#### 204 No Content
No response as the request was successful
#### 400 Bad Request - Missing Payload
```json
{
  "statusCode": 400,
  "message": "Invalid Json: No content to map due to end-of-input\n at [Source: (org.apache.pekko.util.ByteIterator$ByteArrayIterator$$anon$1); line: 1, column: 0]"
}
```
#### 400 Bad Request - API Publisher Unknown Error
The message in this response could contain any number of errors related to problems in the request payload. An example is shown below.
```json
{
  "code": "API_PUBLISHER_UNKNOWN_ERROR",
  "message": "An unexpected error occurred: POST of 'http://localhost:9604/api-definition/validate' returned 422. Response body: '{\"code\":\"INVALID_REQUEST_PAYLOAD\",\"messages\":[\"Field 'categories' should exist and not be empty for API 'Exmaple API'\"]}'"
}
```
#### 401 Unauthorized
```json
{
  "code": "UNAUTHORIZED",
  "message": "Agent must be authorised to perform Publish or Validate actions"
}
```
#### 415 Unsupported Media Type
```json
{
  "statusCode": 415,
  "message": "Expecting text/json or application/json body"
}
```

### Running the tests
Mongo version 3.2 must be running to execute the tests.
Can start the correct version using docker with the following command:
```
docker run -p 27017:27017 --name mongo -d mongo:3.2
```

Execute tests via sbt:
```
sbt clean test it:test
```

### API Definition schema

This repo contains the [JSON schema for the API definition JSON](app/resources/api-definition-schema.json).

Documentation for this schema is generated:

```bash
./generate-api-definition-docs.py app/resources/api-definition-schema.json > docs/api-definition.md
```

**This needs to be run whenever the schema is updated and the generated file should be committed. Confluence links to the generated documentation.**


### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
