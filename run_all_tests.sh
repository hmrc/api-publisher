#!/usr/bin/env bash
sbt -Dlogger.application=INFO clean compile coverage test it:test coverageOff coverageReport
