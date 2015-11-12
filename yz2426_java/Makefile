JCC = javac

JFLAGS = -g

default: Server.class Client.class

Server.class: Server.java
	$(JCC) $(JFLAGS) Server.java

Client.class: Client.java
	$(JCC) $(JFLAGS) Client.java

clean:
	$(RM) *.class
