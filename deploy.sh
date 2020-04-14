#!/bin/bash
./gradlew assembleDebug
scp -P 4000 app/build/outputs/apk/debug/app-debug.apk goodluck@220.191.225.209:/home/goodluck/arcfacev3.apk