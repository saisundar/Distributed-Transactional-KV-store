RMIREGPORT = 1099
PROJECTROOT = ..

all : server client

server : InvalidTransactionException.class \
         ResourceManager.class \
         ResourceManagerImpl.class \
         ResourceManagerImpl_Stub.class \
         TransactionAbortedException.class

client: Client.class

runserver : server
	rmiregistry -J-classpath -J$(PROJECTROOT) $(RMIREGPORT) &
	java -classpath $(PROJECTROOT) -DrmiRegPort=$(RMIREGPORT) -Djava.security.policy=./security-policy transaction.ResourceManagerImpl

runclient : client
	java -classpath $(PROJECTROOT) -DrmiRegPort=$(RMIREGPORT) transaction.Client

clean :
	$(RM) *.class

%.class : %.java
	javac -classpath $(PROJECTROOT) $<

%_Stub.class : %.java
	rmic -classpath $(PROJECTROOT) -d $(PROJECTROOT) transaction.$*

.PHONY : runserver runclient clean

