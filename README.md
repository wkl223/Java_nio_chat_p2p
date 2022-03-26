# Java_nio_chat_p2p

A toy p2p project using JAVA nio library, each instance could play the role as a client/server or both at the same time. The main entrance is at `ClientMain.java`, the Server class is implemented in the `ServerMain.java` with a customized thread pool.
Note that there is only 1 server instance per each main process, but 1 or many client instances (i.e., background client threads for network discovery)


## Functions
1. List all rooms under a peer's control
2. Join a room
3. Command line view of all users + IPs in a room
4. Kick a user out of a room (and break connection + Blacklist), it is only possible for the room owner/creator
5. Graceful quit via custom handshake
6. Message to a room
7. BFS search entire local network and find possible peer connections
8. List all connected peers

## Usage
### 1. Launch the application (from .jar or the ClientMain.java)
```java
java -jar chatpeer.jar
java -jar chatpeer.jar -p 5000
java -jar chatpeer.jar -i 13333
java -jar chatpeer.jar -p 5000 -i 13333
java -jar chatpeer.jar -i 13333 -p 5000
```
Or simply run the application without parameters, there will be a manual prompt

### 2. In-application
Simply type "#help" after application launch
```java
>#help
#help - list this information
#connect IP[:port] [local port] - connect to another peer
#quit - disconnect from a peer
...
```
