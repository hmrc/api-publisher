# API Publisher
API Publisher registers on startup to the Service Locator to get notified when a microservice has APIs to publish.

When the service locator notifies the API Publisher about a new service coming up,
the API Publisher retrieves its API definition and RAML specification, 
then publish its definition to the API Definition service and its scope to the API Scope service.

## What uses this service?
* [Service Locator](https://github.com/HMRC/service-locator)
* [API Gatekeeper Frontend](https://github.com/hmrc/api-gatekeeper-frontend) (SDST allowing API to be published)

## What does this service use?
* [API Definition](https://github.com/HMRC/api-definition)
* [API Scope](https://github.com/HMRC/api-scope)
* [Service Locator](https://github.com/HMRC/service-locator)
* Metrics/Grafana/Kibana
* Additionally this calls every configured API microservice (asks for API definition and RAML files)

### Notification of a new microservice
Service locator uses this endpoint to notify of a new microservice

request: 
```
POST /publish
```
Payload:
```
{
   "serviceName":"hello-world",
   "serviceUrl":"http://hello-world.example.com",
   "metadata":{
      "key1": "value1",
      "key2": "value2"
   }
}
```
response:
```
200 OK
```

# Running the tests
Mongo version 3.2 must be running to execute the tests.
Can start the correct version using docker with the following command:
```
docker run -p 27017:27017 --name mongo -d mongo:3.2
```

Execute tests via sbt:
```
sbt clean test it:test
```

# License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
