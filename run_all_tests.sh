#!/usr/bin/env bash

export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1G"
sbt -Dlogger.application=INFO clean compile coverage test it:test coverageOff coverageReport
