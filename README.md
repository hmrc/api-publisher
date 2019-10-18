# API Publisher
When an API is published through the publish-api Jenkins job,
the API Publisher retrieves its API definition and RAML specification, 
then publish its definition to the API Definition service and its scope to the API Scope service.

## What uses this service?
* The publish-api Jenkins job
* [API Gatekeeper Frontend](https://github.com/hmrc/api-gatekeeper-frontend) (SDST allowing API to be published)

## What does this service use?
* [API Definition](https://github.com/HMRC/api-definition)
* [API Scope](https://github.com/HMRC/api-scope)
* Metrics/Grafana/Kibana
* Additionally this calls every configured API microservice (asks for API definition and RAML files)

### Notification of a new microservice
Jenkins uses this endpoint to notify of a new microservice

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

## Running the tests
Mongo version 3.2 must be running to execute the tests.
Can start the correct version using docker with the following command:
```
docker run -p 27017:27017 --name mongo -d mongo:3.2
```

Execute tests via sbt:
```
sbt clean test it:test
```

## API Definition schema

This repo contains the [JSON schema for the API definition JSON](app/resources/api-definition-schema.json).

Documentation for this schema is generated:

```bash
./generate-api-definition-docs.py app/resources/api-definition-schema.json > docs/api-definition.md
```

**This needs to be run whenever the schema is updated and the generated file should be committed. Confluence links to the generated documentation.**


## License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
