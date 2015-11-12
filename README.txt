Pushed here for tracking original version of class files.

Computer Networks Project 1
Yin Zhao

Socket Programming

Development environment:
using Eclipse, 1.6 library as described in the assignment.

Design:
Multithreading in java: a single socket for server listening, and one socket for each incoming client, which starts a thread.
Each client has two threads, one for receiving the server message and display to the console, the other for getting userinput and sending to the server as commands.
All implements Runnable to support multithreading.


Run code:
type "make"
java Server <portnum>
in another terminal,
java Client localhost <portnum>
then enter the username and password as prompted.

commands:

whoelse

wholast

broadcast message hello world
broadcast user facebook wikipedia message hello guys
message facebook hello you're a waste of time.

logout

authentication implemented as instructed, as well as time_out

