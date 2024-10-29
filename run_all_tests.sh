#!/usr/bin/env bash

export SBT_OPTS="-XX:MaxMetaspaceSize=1G"
sbt pre-commit
./generate-api-definition-docs.py app/resources/api-definition-schema.json > docs/api-definition.md
