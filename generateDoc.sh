#!/usr/bin/env bash

sbt "generateDoc app/resources/api-definition-schema.json docs/api-definition.md"
