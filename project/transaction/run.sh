#!/bin/tcsh
ps -ef| grep 'rmiregistry'|grep -v 'grep'|awk '{print $2}'|xargs kill -9
make clean
rm -f ../test.part1/results/*
make server
make client
rmiregistry -J-classpath -J.. 2100 &
cd ../test.part1
setenv CLASSPATH .:gnujaxp.jar
/usr/bin/javac RunTests.java
java -DrmiPort=2100 RunTests MASTER.xml
