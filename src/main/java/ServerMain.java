import Logic.ServerReception;
import Protocol.Entity.Room;
import Logic.ServerResponds;
import Protocol.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerMain {
  private ThreadPool pool = new ThreadPool(10);
  private static final String host = "127.0.0.1";
  private volatile static List<Room> chatRoom;
  private static ConcurrentHashMap clients; //<key=clientName, value=current room>
  private volatile static List<String> freeName = new CopyOnWriteArrayList<>();//
  public static final String DEFAULT_ROOM = "MainHall";
  private Room defaultRoom = new Room(DEFAULT_ROOM,"0","");

  public static void main(String[] args) {
    int port =4444;//default to port 4444
    try{
      if(args.length==0){
        new ServerMain().handle(port);
      }
      else if (args.length==2&&args[0].equals("-p")) {
        port = Integer.parseInt(args[1]);
        new ServerMain().handle(port);
      }
      else
        throw new NumberFormatException();
    } catch (NumberFormatException e) {
      System.err.println("Invalid argument input, example:java -jar chatserver.jar <-p port>");
    }
  }

  private SelectionKey getKey(Selector selector,String clientName){
    Set<SelectionKey> keys = selector.keys();
    for(SelectionKey sk:keys) {
      if (sk.attachment() != null) {
        if (sk.attachment().equals(clientName))
          return sk;
      }
    }
    return null;
  }
  private synchronized void singleResponse(Protocol message, String name, Selector selector) throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    byteBuffer.put(message.encodeJson().getBytes(StandardCharsets.UTF_8));
    System.out.println("DEBUG - Single response: "+message.encodeJson());
    byteBuffer.flip();
    SocketChannel socketChannel = (SocketChannel) getKey(selector,name).channel();
    while (byteBuffer.hasRemaining()) {
      try {
            socketChannel.write(byteBuffer);
      } catch (IOException e) {
        break;
      }
    }
    byteBuffer.clear();
  }
  private synchronized void broadCast(Protocol message, ArrayList<String> multicastList, Selector selector, SelectionKey selectionKey) throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    byteBuffer.put(message.encodeJson().getBytes(StandardCharsets.UTF_8));
    byteBuffer.flip();
    byteBuffer.mark();// because we need to send this multiple times.
    Set<SelectionKey> keys = selector.keys();
    System.out.println("DEBUG - broadcast to users:"+multicastList.toString());
    System.out.println("broadcast message:"+message.encodeJson());
    for(SelectionKey k : keys){
      //ignore the sender + server itself
      if(selectionKey.equals(k) || !(k.channel() instanceof SocketChannel)){
        continue;
      }
      else if (multicastList.contains(k.attachment())) {
        SocketChannel socketChannel = (SocketChannel) k.channel();
        while (byteBuffer.hasRemaining()) {
          try {
            socketChannel.write(byteBuffer);
          } catch (IOException e) {
            break;
          }
        }
        byteBuffer.reset();
      }
    }
    byteBuffer.clear();
  }


  public void handle(int port) {
    ServerSocketChannel serverSocketChannel;

    try {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.bind(new InetSocketAddress(host, port));
      Selector selector = Selector.open();

      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
      System.out.println("server is up at port: "+ port);
      chatRoom = new CopyOnWriteArrayList<>();
      //create default chatRoom
      chatRoom.add(defaultRoom);
      clients = new ConcurrentHashMap<String,SelectionKey>();
      while(true){
        int eventCountTriggered = selector.select();
        if (eventCountTriggered <=0){
          continue;
        }
        // if there is an event.
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        for(SelectionKey s: selectionKeys){
          //System.out.println("DEBUG: new event: "+s.toString());
          selectionKeyHandler(s,selector);
        }
        selectionKeys.clear();
      }
    } catch (IOException e) {
      // handle the exception
      System.out.println("connection reset by peer");
    }
  }
  private void selectionKeyHandler(SelectionKey selectionKey, Selector selector) throws IOException {
    // if a connection event
    try {
      if (selectionKey.isAcceptable()) {
        try {
          SocketChannel socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
          if (socketChannel != null) {
            System.out.println("DEBUG: client connection established: " + socketChannel.socket().getPort());
            //set unblocking mode
            socketChannel.configureBlocking(false);
            // register the client to the selector for event monitoring. attach Username.
            Protocol respond = ServerResponds.newIdentity(clients, freeName, ServerResponds.NEW_USER, ServerResponds.NEW_USER);
            Protocol roomList = ServerResponds.roomList(chatRoom);
            Protocol roomContent = ServerResponds.roomContents(DEFAULT_ROOM, chatRoom);
            String clientName = respond.getMessage().getIdentity();
            Protocol roomChange = ServerResponds.roomChange(chatRoom, clientName, ServerResponds.NEW_USER, DEFAULT_ROOM);
            SelectionKey clientKey = socketChannel.register(selector, SelectionKey.OP_READ, clientName);
            // add to default room, and add client to the clients name collection.
            clients.put(clientName, DEFAULT_ROOM);
            chatRoom.get(0).users.add(clientName);
            // new identity assignment
            singleResponse(respond, clientName, selector);
            singleResponse(roomList, clientName, selector);
            singleResponse(roomContent, clientName, selector);
            singleResponse(roomChange, clientName, selector);
          }
        } catch (IOException e) {
          e.printStackTrace();
        } catch (Exception e) {
        }
        ;//just in case
      }
      // otherwise, it is something the server can read.
      else if (selectionKey.isReadable()) {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        if (socketChannel != null) {
          ByteBuffer b = ByteBuffer.allocate(1024); //1024 byte per read
          try {
            // while not reach to EOF
            while (socketChannel.read(b) > 0) {
            }
            b.flip(); //reset the cursor at 0.
            String message = String.valueOf(StandardCharsets.UTF_8.decode(b));
            String client = (String) selectionKey.attachment();
            System.out.println("DEBUG: received client message: " + message + " from client:" + client);
            if (message.equals("") || message.equals(" ")) {
              System.out.println("Received empty message, connection abort.");
              throw new IOException();
            }
            WorkerThread worker;
            do {
              worker = pool.getWorker();
            } while (worker == null);
            worker.serviceChannel(message, client, selector, selectionKey);
            b.clear();
          } catch (IOException e) {
            //something wrong with the client. treat it as quit
            Message m = new Message();
            m.setType(Message.TYPE_QUIT);
            m.setSuccessed(true);
            Protocol p = new Protocol(m);
            try {
              WorkerThread worker;
              do {
                worker = pool.getWorker();
              } while (worker == null);
              worker.processMessageAndRespond(p.encodeJson(), (String) selectionKey.attachment(), selector, selectionKey);
            } catch (IOException ex) {
            }
          } catch (Exception e) {
          }//just in case
        }
      }
    }catch (Exception e){}//just in case
  }
  class ThreadPool {
    List idle = new LinkedList();
    ThreadPool(int poolSize) {
      for (int i = 0; i < poolSize; i++) {
        WorkerThread thread = new WorkerThread(this);
        thread.setName("Worker" + (i + 1));
        thread.start();
        idle.add(thread);
      }
    }
    WorkerThread getWorker() {
      WorkerThread worker = null;
      synchronized (idle) {
        if (idle.size() > 0) {
          worker = (WorkerThread) idle.remove(0);
        }
      }
      return (worker);
    }
    void returnWorker(WorkerThread worker) {
      synchronized (idle) {
        idle.add(worker);
      }
    }
  }

  class WorkerThread extends Thread {
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private ThreadPool pool;
    private SelectionKey selectionKey;
    private Selector selector;
    private String message;
    private String client;

    WorkerThread(ThreadPool pool) {
      this.pool = pool;
    }
    public synchronized void run() {
      System.out.println(this.getName() + " is ready");

      while (true) {
        try {
          this.wait();
        } catch (InterruptedException e) {
          this.interrupted();
        }
        System.out.println(this.getName() + " has been awakened");
        try {
          processMessageAndRespond(message, client, selector,selectionKey);
        } catch (IOException e) {
        }
        this.pool.returnWorker(this);
      }
    }
    synchronized void serviceChannel(String message, String client, Selector selector,SelectionKey selectionKey) {
      this.selectionKey=selectionKey;
      this.selector=selector;
      this.message=message;
      this.client=client;
      this.notify(); // Awaken the thread
    }
    public synchronized void processMessageAndRespond(String message, String client, Selector selector,SelectionKey selectionKey) throws IOException {
      this.selectionKey=selectionKey;
      this.selector=selector;
      Protocol p = new Protocol(message);
      Protocol answer = null;
      List<String> affectedClients = null;
      if(p.decodeJson().getType()==null) return;
      switch (p.decodeJson().getType()){
        case Message.TYPE_DELETE: {
          Room defaultRoom = ServerResponds.findRoom(chatRoom, DEFAULT_ROOM);
          Room affectedRoom =ServerResponds.findRoom(chatRoom,p.getMessage().getRoomid());
          answer = ServerReception.deleteRoom(p, client, clients, chatRoom);
          if(affectedRoom != null) {
            affectedClients = affectedRoom.getUsers();
          }
          if (answer.getMessage().isSuccessed()){
            for(String c: affectedClients){
              Protocol temp = ServerResponds.roomChange(chatRoom,c,affectedRoom.getRoomid(),DEFAULT_ROOM);
              //as they are changing rooms, note that it is not a formal room change message
              //so that no follow-up message in this case. e.g., ROOM CONTENTS and ROOM LIST.
              singleResponse(temp,c,selector);
              broadCast(temp, (ArrayList<String>) defaultRoom.users,selector,selectionKey);
              defaultRoom.users.add(c);
              clients.remove(c);
              clients.put(c,DEFAULT_ROOM);
            }
          }
          singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_JOIN: {
          Room formerRoom = ServerResponds.findRoom(chatRoom, (String) clients.get(client));
          answer = ServerReception.joinRoom(p, client, clients, chatRoom);
          if(answer.getMessage().isSuccessed()){
            Room targetRoom = ServerResponds.findRoom(chatRoom,p.getMessage().getRoomid());
            List<String> affectedUser;
            if((targetRoom.users !=null && formerRoom.users!=null)||(targetRoom.users.size()!=0&&formerRoom.users.size()!=0)){
              affectedUser = new ArrayList<>(formerRoom.users);
              affectedUser.addAll(targetRoom.users);
            }
            else{
              affectedUser = (targetRoom.users == null)||(targetRoom.users.size()==0)? formerRoom.users:targetRoom.users;
            }
            broadCast(answer, (ArrayList<String>) affectedUser,selector,selectionKey);
            //finally, delete room if the room has no owner and no users.
            if((!formerRoom.getRoomid().equals(DEFAULT_ROOM))&&formerRoom.getCount().equals("0") && formerRoom.owner.equals(Message.EMPTY)){
              chatRoom.remove(formerRoom);
            }
            if (answer.getMessage().getRoomid().equals(DEFAULT_ROOM) && !answer.getMessage().getFormer().equals(DEFAULT_ROOM)) {
              singleResponse(ServerResponds.roomContents(DEFAULT_ROOM, chatRoom), client, selector);
              singleResponse(ServerResponds.roomList(chatRoom), client, selector);
            }
          }
          singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_IDENTITY_CHANGE: {
//          Room affected = ServerResponds.findRoom(chatRoom, (String) clients.get(client));
          answer = ServerReception.identityChange(p, client, clients, freeName, chatRoom);
          String name = client;
          if(answer.getMessage().isSuccessed()){
            for(Room r: chatRoom) {
              broadCast(answer, (ArrayList<String>) r.users, selector, selectionKey);
            }
            getKey(selector,answer.getMessage().getFormer()).attach(answer.getMessage().getIdentity());
            name = answer.getMessage().getIdentity();
            if(client.matches("Guest[0-9]+")) freeName.add(client);
          }
          singleResponse(answer,name,selector);
          break;
        }
        case Message.TYPE_WHO: {
          answer = ServerReception.who(p, client, chatRoom);
          if(answer.getMessage().isSuccessed()) singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_QUIT: {
          //1. remove from current room
          Room affected = ServerResponds.findRoom(chatRoom, (String) clients.get(client));
          //1.a special case where client respond empty message because of close()
          if(affected == null) return;
          answer = ServerReception.quit(p, client, chatRoom, clients);
          affected.users.remove(client);
          //2. remove affected room owner name, remove the room if necessary.
          for(Room r: chatRoom){
            if(r.owner.equals(client)){
              System.out.println("DEBUG - affected room: "+r.getRoomid());
              r.owner=Message.EMPTY;
            }
            if((!r.getRoomid().equals(DEFAULT_ROOM))&&r.getCount().equals("0") && r.owner.equals(Message.EMPTY)){
              System.out.println("DEBUG - deleted room: "+r.getRoomid());
              chatRoom.remove(r);
            }
          }
          clients.remove(client);
          broadCast(answer, (ArrayList<String>) affected.users,selector,selectionKey);
          singleResponse(answer,client,selector);
          int port = ((SocketChannel) selectionKey.channel()).socket().getPort();
          System.out.println("DEBUG - port: "+port+" has disconnected");
          selectionKey.cancel();
          selector.wakeup();
          if(client.matches("Guest[0-9]+")) freeName.add(client);
          break;
        }
        case Message.TYPE_LIST: {
          answer = ServerReception.list(p, client, chatRoom);
          singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_ROOM_CREATION: {
          answer = ServerReception.createRoom(p, client, chatRoom);
          if(answer.getMessage().isSuccessed()) {
            System.out.println("room created: "+p.getMessage().getRoomid());
          }
          singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_MESSAGE: {
          Room affected = ServerResponds.findRoom(chatRoom, (String) clients.get(client));
          answer = ServerReception.message(p, client);
          broadCast(answer, (ArrayList<String>) affected.users,selector,selectionKey);
          //Send a copy to the sender according to the slide page 5
          singleResponse(answer,client,selector);
          break;
        }
        default:
          System.out.println("DEBUG - Something is wrong in reception service");
          break;
      }
    }

  }

}