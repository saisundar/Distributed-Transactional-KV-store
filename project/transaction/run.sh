#!/bin/tcsh

make clean
rm -f ../test.part1/results/*
make server
make client
rmiregistry -J-classpath -J.. 2100 &
cd ../test.part1
setenv CLASSPATH .:gnujaxp.jar
/usr/bin/javac RunTests.java
java -DrmiPort=2100 RunTests MASTER.xml
