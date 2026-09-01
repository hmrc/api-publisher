#!/usr/bin/env bash
./generateDoc.sh

export SBT_OPTS="-XX:MaxMetaspaceSize=1G"
sbt pre-commit

