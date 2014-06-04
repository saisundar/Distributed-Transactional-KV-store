#!/bin/bash
ps -ef| grep 'rmiregistry'|grep -v 'grep'|awk '{print $2}'|xargs kill -9
make clean
rm -f ../test.part1/results/*
make server
make client
rmiregistry -J-classpath -J.. 2100 &
/bin/tcsh ./setenviron.sh
cd ../test.part1
#setenv CLASSPATH .:gnujaxp.jar
/usr/bin/javac RunTests.java
rm -fr Run.log
java -DrmiPort=2100 RunTests MASTER.xml >> Run.log &
while [ ! -f results/RM.log ]
do
    sleep 1
done
tail -f results/RM.log >> Run.log &
tail -f Run.log
