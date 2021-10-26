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
import java.util.stream.Collectors;

public class ServerMain {
  protected ThreadPool pool = new ThreadPool(10);
  protected volatile List<Room> chatRoom;
  protected volatile List<String> blackList;
  protected static ConcurrentHashMap clients; //<key=clientName, value=current room>
  protected static ConcurrentHashMap client_addresses; //<key=clientName, value=client_listening_address>
  protected static final String DEFAULT_ROOM = "";
  protected Room defaultRoom = new Room(DEFAULT_ROOM,"0",""); //for record purpose only, will be completely transparent.
  SelectionKey selectionKey;
  Selector selector;

  public ServerMain(){
    chatRoom = new CopyOnWriteArrayList<>();
    chatRoom.add(defaultRoom);
    blackList = new ArrayList<>();
    clients = new ConcurrentHashMap<String,String>();
    client_addresses = new ConcurrentHashMap<String,String>();
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
  synchronized void singleResponse(Protocol message, String name, Selector selector) throws IOException {
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
  synchronized void broadCast(Protocol message, ArrayList<String> multicastList, Selector selector, SelectionKey selectionKey) throws IOException {
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


  public void handle(InetSocketAddress servingAddr) {
    ServerSocketChannel serverSocketChannel;

    try {
      serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.configureBlocking(false);
      serverSocketChannel.bind(servingAddr);
      Selector selector = Selector.open();
      this.selector = selector;
      serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
      System.out.println("server is up at port: "+ servingAddr.getPort());

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
          String clientIp = ((ServerSocketChannel) selectionKey.channel()).socket().getInetAddress().getHostAddress();

          if (socketChannel != null) {
            //set unblocking mode
            socketChannel.configureBlocking(false);
            // register the client to the selector for event monitoring.
            String clientName = socketChannel.getRemoteAddress().toString().replace("/","");
            SelectionKey clientKey = socketChannel.register(selector, SelectionKey.OP_READ, clientName);
            System.out.println("DEBUG - Received connect from client: "+clientIp);
            if(blackList.contains(clientIp)) {
              System.out.println("DEBUG - In blacklist, connection abort");
              clientKey.cancel();
              clientKey.channel().close();
              selector.wakeup();
              return;
            }
            System.out.println("DEBUG - client connection established: " + clientName);
            List<Room> tempRoom = new ArrayList<>(chatRoom);
            tempRoom.remove(defaultRoom);
            Protocol roomList = ServerResponds.roomList(tempRoom);
//            Protocol roomContent = ServerResponds.roomContents(DEFAULT_ROOM, chatRoom);
//            Protocol roomChange = ServerResponds.roomChange(chatRoom, clientName, ServerResponds.NEW_USER, DEFAULT_ROOM);

            // add to default room, and add client to the clients name collection.
            clients.put(clientName, DEFAULT_ROOM);
            chatRoom.get(0).users.add(clientName);
            // new identity assignment
            singleResponse(roomList, clientName, selector);
//            singleResponse(roomContent, clientName, selector);
//            singleResponse(roomChange, clientName, selector);
          }
        } catch (IOException e) {
          e.printStackTrace();
        } catch (Exception e) {
        }
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
      List<Room> tempRoom = new ArrayList<>(chatRoom);
      tempRoom.remove(defaultRoom);
      switch (p.decodeJson().getType()){
        case Message.TYPE_JOIN: {
          Room formerRoom = ServerResponds.findRoom(tempRoom, (String) clients.get(client));
          answer = ServerReception.joinRoom(p, client, clients, tempRoom);
          if(answer.getMessage().isSuccessed()){
            Room targetRoom = ServerResponds.findRoom(tempRoom,p.getMessage().getRoomid());
            List<String> affectedUser = null;
            if(formerRoom != null){
            if((targetRoom.users !=null && formerRoom.users!=null)||(targetRoom.users.size()!=0&&formerRoom.users.size()!=0)){
              affectedUser = new ArrayList<>(formerRoom.users);
              affectedUser.addAll(targetRoom.users);
            }
            else{
              affectedUser = (targetRoom.users == null)||(targetRoom.users.size()==0)? formerRoom.users:targetRoom.users;
            }
              //finally, delete room if the room has no owner and no users.
              if((!formerRoom.getRoomid().equals(DEFAULT_ROOM))&&formerRoom.getCount().equals("0") && formerRoom.owner.equals(Message.EMPTY)){
                chatRoom.remove(formerRoom);
              }
            }
            else{
              // only notify the target room
              affectedUser = targetRoom.users;
            }
            broadCast(answer, (ArrayList<String>) affectedUser,selector,selectionKey);

            if (answer.getMessage().getRoomid().equals(DEFAULT_ROOM) && !answer.getMessage().getFormer().equals(DEFAULT_ROOM)) {
              singleResponse(ServerResponds.roomContents(DEFAULT_ROOM, tempRoom), client, selector);
              singleResponse(ServerResponds.roomList(tempRoom), client, selector);
            }
          }
          singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_WHO: {
          answer = ServerReception.who(p, client, tempRoom);
          if(answer.getMessage().isSuccessed()) singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_QUIT: {
          //1. remove from current room
          Room affected = ServerResponds.findRoom(tempRoom, (String) clients.get(client));
          // special case: since "" is the default room, and it is null when we try to find it.
          if(affected == null){
            // so that we don't need to notify anyone, and do anything extra messages.
            // simply remove, and respond individually.
            clients.remove(client);
            answer = ServerReception.quit(p, client, tempRoom, clients);
            singleResponse(answer,client,selector);
            int port = ((SocketChannel) selectionKey.channel()).socket().getPort();
            System.out.println("DEBUG - port: "+port+" has disconnected");
            selectionKey.cancel();
            selector.wakeup();
            break;
          }
          //1.a special case where client respond empty message because of close()
          answer = ServerReception.quit(p, client, tempRoom, clients);
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

        }
        case Message.TYPE_LIST: {
          answer = ServerReception.list(p, client, tempRoom);
          singleResponse(answer,client,selector);
          break;
        }
        case Message.TYPE_MESSAGE: {
          Room affected = ServerResponds.findRoom(tempRoom, (String) clients.get(client));
          answer = ServerReception.message(p, client);
          if (affected != null) {
            broadCast(answer, (ArrayList<String>) affected.users, selector, selectionKey);
            //Send a copy to the sender according to the slide page 5
            singleResponse(answer, client, selector);
          }
          break;
        }
        // since host change does not require any s2c packet. Simply check the incoming data and add it to the collection.
        case Message.TYPE_HOST_CHANGE:{
          if(p.getMessage().getHost()!=null) {
            client_addresses.put(client, p.getMessage().getHost());
            System.out.println("DEBUG - Host added: " + client_addresses.toString());
          }
          break;
        }
        default:
          System.out.println("DEBUG - Something is wrong in reception service");
          break;
      }
    }

  }

}