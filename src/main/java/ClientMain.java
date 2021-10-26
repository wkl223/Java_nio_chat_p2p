import Logic.*;
import Protocol.Entity.Room;
import Protocol.Message;
import Protocol.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static Logic.ServerReception.DEFAULT_ROOM;


public class ClientMain {
    private static ExecutorService USER_INPUT_HANDLER;
    private static Future<?> future;

    private static int outgoingPort = 0; // 0 = let system pick one
    private static int listeningPort = 4444; // default as assignment spec

    private static String userName = "";
    private static String currentRoom = ""; //will be treated as no room, assign empty string for avoid null.
    private static String currentPeer = "";
    private static String prefix = "";
    private static SocketChannel socketChannel;
    private static Selector selector;
    private static boolean completed;
    private static final Map<String,String> request = new HashMap<>();
    protected static final ServerMain SERVER = new ServerMain();

    public static void handle(String hostAddr, int destinationPort) {
        try {
            future.cancel(true);
            System.out.println("keyboard interrupt sent in handle()");
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);// set to unblocking mode
            selector = Selector.open();
            socketChannel.socket().bind(new InetSocketAddress(InetAddress.getLocalHost(),outgoingPort));
//            System.out.println("DEBUG - bind with: "+InetAddress.getLocalHost()+":"+outgoingPort);
            socketChannel.register(selector, SelectionKey.OP_CONNECT);//register channel to selector by connection event
            socketChannel.connect(new InetSocketAddress(hostAddr, destinationPort));
            System.out.println("DEBUG - connect to: "+hostAddr+":"+destinationPort);
            userName = socketChannel.getLocalAddress().toString().replace("/","").trim();
            System.out.println("DEBUG - issued username:"+userName);
            prefix = String.format("[%s]: %s> ", currentRoom, userName);
            while (!completed) {
                int eventCountTriggered = selector.select();
                if (eventCountTriggered <= 0) {
                    continue;
                }
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : selectionKeys) {
                    selectionKeyHandler(selectionKey, selector);
                }
                selectionKeys.clear();
            }
        } catch (ClosedChannelException e) {
            quit();
            System.out.println("Forced quit by ctrl+D, quit");
        }catch (ClosedSelectorException e) {
            quit();
            System.out.println("Forced quit by ctrl+D, quit");
        }
        catch (IOException e) {
            e.printStackTrace();
        }finally {
            localConsole();
            completed = true;
        }
    }
    static void localConsole(){
        init();
        System.out.println("DEBUG - local console start");
        System.out.print(prefix);
        future = USER_INPUT_HANDLER.submit(() -> {
            System.out.println("DEBUG - local user input handler is up");
            completed = false;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(System.in));
                    String message;
                    while (!Thread.currentThread().isInterrupted()&&!br.ready()) {
                    }
                    message = br.readLine();
                    String m = ClientResponds.processMessage(message,request,false);
                    System.out.println("DEBUG - client local command:" +m);
                    if(!m.equals(ClientResponds.INVALID)){
                        Protocol msg = new Protocol(m);
                        switch(msg.getType()){
                            case Message.TYPE_CONNECT:{
                                String[] req=localConnect(msg.getMessage().getContent());
                                if(req == null) System.out.println("invalid ip and/or port");
                                else{
                                    handle(req[0], Integer.parseInt(req[1]));
                                }
                                return;
                            }
                            case Message.TYPE_DELETE: {
                                List<String> affectedClients = null;

                                Room affectedRoom =ServerResponds.findRoom(SERVER.chatRoom,msg.getMessage().getRoomid());
                                Protocol respond = ServerReception.deleteRoom(msg,SERVER.clients,SERVER.chatRoom);
                                System.out.println("DEBUG - local server logic respond:" +respond.encodeJson());
                                if(affectedRoom != null) {
                                    affectedClients = affectedRoom.getUsers();
                                }
                                if(respond.getMessage().isSuccessed()){
                                    for(String c: affectedClients){
                                        Protocol temp = ServerResponds.roomChange(SERVER.chatRoom,c,affectedRoom.getRoomid(),DEFAULT_ROOM);

                                        // broadcast message without client sender (thus an empty selection key)
                                        SERVER.broadCast(temp, (ArrayList<String>) SERVER.defaultRoom.users, SERVER.selector, new SelectionKey() {
                                            @Override
                                            public SelectableChannel channel() {
                                                return null;
                                            }

                                            @Override
                                            public Selector selector() {
                                                return null;
                                            }

                                            @Override
                                            public boolean isValid() {
                                                return false;
                                            }

                                            @Override
                                            public void cancel() {

                                            }

                                            @Override
                                            public int interestOps() {
                                                return 0;
                                            }

                                            @Override
                                            public SelectionKey interestOps(int ops) {
                                                return null;
                                            }

                                            @Override
                                            public int readyOps() {
                                                return 0;
                                            }
                                        });
                                        SERVER.defaultRoom.users.add(c);
                                        SERVER.clients.remove(c);
                                        SERVER.clients.put(c,DEFAULT_ROOM);
                                    }
                                    System.out.println("Room "+msg.getMessage().getRoomid()+" has been deleted");
                                }
                                else System.out.println("Room "+msg.getMessage().getRoomid()+" deletion failed");
                                break;
                            }
                            case Message.TYPE_ROOM_CREATION: {
                                Protocol respond = ServerReception.createRoom(msg,userName,SERVER.chatRoom);
                                System.out.println("DEBUG - local server logic respond:" +respond.encodeJson());
                                if(respond.getMessage().isSuccessed()) System.out.println("Room "+msg.getMessage().getRoomid()+" has been created");
                                else System.out.println("Room "+msg.getMessage().getRoomid()+" creation failed");
                                break;
                            }
                            case Message.TYPE_KICK:{
                                System.out.println(m);
                                Protocol respond = ServerReception.kick(msg.getMessage().getContent(),SERVER.chatRoom,SERVER.clients,SERVER.client_addresses,SERVER.blackList, SERVER.selector);
                                System.out.println("DEBUG - local server logic respond:" +respond.encodeJson());
                                if(respond.getMessage().isSuccessed()) System.out.println("DEBUG - user kicked");
                                else System.out.println("User not been kicked");
                                break;
                            }
                            case Message.TYPE_QUIT:
                                localQuit();
                                break;
                        }
                    }
                    System.out.print(prefix);//new prompt.
                } catch (IOException e) {
                    System.out.println("IOexception: Invalid input");
                } catch(NullPointerException e){
                    System.out.println("NullPointer: Invalid input");
                    e.printStackTrace();
                } catch(Exception e){
                    // handle any errors here, plus ctrl+D.
                    System.out.println("Exception caught, quit");
                    e.printStackTrace();
                    localQuit();
                }
            }
        });
    }
    private static synchronized void selectionKeyHandler(SelectionKey selectionKey, Selector selector) {
        if (selectionKey.isConnectable()) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            try {
                if (socketChannel.isConnectionPending()) {
                    socketChannel.finishConnect();
                    System.out.println("Connection successd");
                    currentPeer = socketChannel.getRemoteAddress().toString();
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);//1024 bytes
                    USER_INPUT_HANDLER = Executors.newSingleThreadExecutor();
                    // send hostchange packet
                    byteBuffer.put(ClientResponds.hostchange(InetAddress.getLocalHost().getHostAddress()+":"+ listeningPort).encodeJson().getBytes(StandardCharsets.UTF_8));
                    byteBuffer.flip();
                    while (byteBuffer.hasRemaining()) {
                        socketChannel.write(byteBuffer);
                    }
                    future = USER_INPUT_HANDLER.submit(() -> {
                        System.out.println("handler start in selection key statement");
                        while (!Thread.currentThread().isInterrupted()) {
                            byteBuffer.clear();
                            try {
                                    BufferedReader br = new BufferedReader(
                                            new InputStreamReader(System.in));
                                    String message;
                                    while (!Thread.currentThread().isInterrupted()&&!br.ready()) {
                                    }
                                    if(!completed) {
                                    message = br.readLine();
                                    String m = ClientResponds.processMessage(message, request, true);
                                    if (m.equals(ClientResponds.INVALID)) {
                                        m = null; //don't do anything
                                        System.out.println("invalid parameter");
                                    }
                                    System.out.println("DEBUG - client side: " + m);
                                    if(new Protocol(m).getType().equals(Message.TYPE_QUIT)){
                                        completed = true;
                                    }
                                    if (m != null) {
                                        byteBuffer.put(new Protocol(m).encodeJson().getBytes(StandardCharsets.UTF_8));
                                        byteBuffer.flip();
                                        while (byteBuffer.hasRemaining()) {
                                            socketChannel.write(byteBuffer);
                                        }
                                    }
                                    System.out.print(prefix);//new prompt.
                                }
                            } catch (IOException e) {
                               System.out.println("IO: Invalid input");
                            } catch(NullPointerException e){
                                System.out.println("Null: Invalid input");
                            }
                            catch (NoSuchElementException e){
                                System.out.println("Something wrong with the server side (cannot decode message) OR ctrl+D entered? connection abort");
                                quit();
                            }
                            catch(Exception e){
                                System.out.println("Strange error?");
                            }
                        }
                    });

                }
            } catch (IOException e) {
                System.err.println("ERROR - Cannot connect to server!!");
                quit();
                localConsole();
                completed = true;
            }
        } else if (selectionKey.isReadable()) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            ByteBuffer b = ByteBuffer.allocate(1024);
            try {
                while (socketChannel.read(b) > 0) {
                }
                b.flip();
                String message = String.valueOf(StandardCharsets.UTF_8.decode(b));
                b.clear();
                System.out.println("DEBUG server msg: "+message);
                //possible that server sends lots of Json at once (i.e. at client startup)
                String[] strings = message.split("(?<=\\})(?=\\{\"type\")");
                for(String s: strings){
                    String serverRespond = processMessageAndRepresent(s,userName);
//                    System.out.print("\n"+serverRespond);
                    if(serverRespond.equals(Message.OK)&&request.containsKey(Message.TYPE_QUIT)){
                        quit();
                        completed = true;
                        continue;
                    }
//                    System.out.print("\n"+serverRespond);
                }
                System.out.print("\n"+prefix);// follow-up prefix as command prompt
            } catch (Exception e) {
                System.out.println("Error - Something wrong in server message, connection abort!");
                completed = true;
                quit();
                localConsole();
            }
        }
    }
    public static void init(){
        userName = "";
        currentRoom = "";
        prefix = ">";
    }
    public static void main(String[] args) throws IOException {
        ParameterHandler p = new ParameterHandler();
        p.doMain(args);
        USER_INPUT_HANDLER = Executors.newSingleThreadExecutor();
        if( p.getP()>0 ) {
            ClientMain.listeningPort = p.getP();
        }
        if( p.getI()>0 ) {
            ClientMain.outgoingPort = p.getI();
        }
        System.out.println("DEBUG - set listening address at "+ InetAddress.getLocalHost().getHostAddress()+":"+ ClientMain.listeningPort+", set outgoing port:"+ClientMain.outgoingPort);
        int finalListeningPort = listeningPort;
        new Thread(() -> {
            try {
                SERVER.handle(new InetSocketAddress(InetAddress.getLocalHost(), finalListeningPort));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }).start();
        init();
        localConsole();
    }

    public static String processMessageAndRepresent(String message, String name)  {
        Protocol p = new Protocol(message);
        String client = name;
        try{
        switch (p.decodeJson().getType()){
            case Message.TYPE_ROOM_CHANGE: {
                String formerRoom = p.getMessage().getFormer();
                String roomId = p.getMessage().getRoomid();
                String identity = p.getMessage().getIdentity();
                if (!formerRoom.equals(roomId) && identity.equals(userName)){
                    currentRoom = roomId;
                    prefix = String.format("[%s]: %s> ", currentRoom, userName);
//                    System.out.println("DEBUG- new prefix: "+prefix);
                }
                return ClientReception.roomChange(p, client,request);
            }
            case Message.TYPE_ROOM_CONTENTS:
                return ClientReception.roomContents(p);
            case Message.TYPE_ROOM_LIST: {
                return ClientReception.roomList(p,request);
            }
            case Message.TYPE_MESSAGE:
                return ClientReception.message(p,client);
            default:
                System.out.println("DEBUG: Server sends a message with incorrect type! Type:"+p.decodeJson().getType());
                return null;
        }
        }
        catch(Exception e){
            System.out.println("DEBUG - received something wrong. Disconnected");
            quit();
        }
        return null;
    }
    private static void quit(){
        try {
            System.out.println("DEBUG - quit() called");
            selector.close();
            socketChannel.close();
            future.cancel(true);
            completed = true;
        } catch (IOException e) {
            System.out.println("something wrong with the close methods");
        }finally {
            //System.out.println("Server responds ok, good bye!");
            System.out.print("Disconnected from "+ currentPeer);
        }
    }
    private static void localQuit(){
        try {
            USER_INPUT_HANDLER.shutdownNow();
        } catch (Exception e) {
            System.out.println("something wrong with the close methods");
        }finally {
            //System.out.println("Server responds ok, good bye!");
            System.out.print("Bye!");
            System.exit(0);
        }
    }
    private static String[] localConnect(String destination){
        if(destination != null) {
            String[] req = destination.split(":");
            // if the first arg matches with ip
            String ipRegex = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";
            if (req[0].matches(ipRegex) || req[0].equals("localhost")) {
                try {
                    Integer.parseInt(req[1]);
                    return req;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
