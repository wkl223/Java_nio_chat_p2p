import Logic.ClientReception;
import Logic.ClientResponds;
import Protocol.Message;
import Protocol.Protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ClientMain {
    private static final ExecutorService USER_INPUT_HANDLER = Executors.newSingleThreadExecutor();
    public static final String DEFAULT_ROOM = "MainHall";
    public static int port = 4444;
    public static String host = "127.0.0.1";

    private String userName;
    private String currentRoom;
    private String prefix;
    private SocketChannel socketChannel;
    private Selector selector;
    private Map<String,String> request = new HashMap<>();

    public void handle() {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);// set to unblocking mode
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_CONNECT);//register channel to selector by connection event
            socketChannel.connect(new InetSocketAddress(host, port));
            while (true) {
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
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            quit();
        }
    }

    private synchronized void selectionKeyHandler(SelectionKey selectionKey, Selector selector) {
        if (selectionKey.isConnectable()) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            try {
                if (socketChannel.isConnectionPending()) {
                    socketChannel.finishConnect();
                    System.out.println("Connection successd");
                    socketChannel.configureBlocking(false);
                    socketChannel.register(selector, SelectionKey.OP_READ);
                    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);//1024 bytes
                    USER_INPUT_HANDLER.execute(() -> {
                        while (!Thread.currentThread().isInterrupted()) {
                            byteBuffer.clear();
                            try {
                                String message = new Scanner(System.in).nextLine();
                                String m =ClientResponds.processMessage(message,request);
                                if(m.equals(ClientResponds.INVALID)) {
                                    m = null; //don't do anything
                                    System.out.println("invalid parameter");
                                }
//                                System.out.println("DEBUG - client side: "+m);
                                if(m!=null) {
                                    byteBuffer.put(new Protocol(m).encodeJson().getBytes(StandardCharsets.UTF_8));
                                    byteBuffer.flip();
                                    while (byteBuffer.hasRemaining()) {
                                        socketChannel.write(byteBuffer);
                                    }
                                }
                                System.out.print(prefix);//new prompt.
                            } catch (IOException e) {
                               //System.out.println("Invalid input");
                            } catch(NullPointerException e){
                                //System.out.println("Invalid input");
                            }
                            catch (NoSuchElementException e){
                                System.out.println("Something wrong with the server side (cannot decode message), program terminate");
                                System.exit(-1);
                            }
                            catch(Exception e){ System.out.println("Strange error?");
                            }
                        }
                    });
                }
            } catch (IOException e) {
                System.err.println("ERROR - Cannot connect to server!!");
                System.exit(-1);
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
//                System.out.println("DEBUG server msg: "+message);
                //possible that server send lots of Json at once (i.e. at client startup)
                String[] strings = message.split("(?<=\\})(?=\\{\"type\")");
                for(String s: strings){
                    String serverRespond = processMessageAndRepresent(s,userName);
                    if(serverRespond.equals(Message.OK)&&request.containsKey(Message.TYPE_QUIT)){
                        quit();
                    }
                    System.out.print("\n"+serverRespond);
                }
                System.out.print("\n"+prefix);// follow-up prefix as command prompt
            } catch (IOException e) {
                System.out.println("Error - Something wrong in server message, program terminated!");
                System.exit(-1);
            }
        }
    }


    public static void main(String[] args) {
        try{
            if (args.length==3&&args[1].equals("-p")) {
                port = Integer.parseInt(args[2]);
                host = args[0];
                ClientMain client = new ClientMain();
                client.handle();
            }
            else if(args.length==1){
                host = args[0];
                ClientMain client = new ClientMain();
                client.handle();
            }
            else{
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid argument input, example:java -jar chatclient.jar hostname <-p port>");
        }
    }
    public String processMessageAndRepresent(String message, String name) throws IOException {
        Protocol p = new Protocol(message);
        String client = name;
        switch (p.decodeJson().getType()){
            case Message.TYPE_NEW_IDENTITY: {
                String former = p.getMessage().getFormer();
                String identity =p.getMessage().getIdentity();
                if((!former.equals(identity)) && (former.equals(client)||former.equals(""))){
//                    System.out.println("DEBUG - NEW IDENTITY successed:"+identity);
                    this.userName=identity;
                    client = this.userName;
                    if(former.equals("")) {
                        this.currentRoom = DEFAULT_ROOM;
                    }
                    this.prefix =String.format("[%s]: %s> ", this.currentRoom, this.userName);
                }
                return ClientReception.newIdentity(p, client);
            }
            case Message.TYPE_ROOM_CHANGE: {
                String formerRoom = p.getMessage().getFormer();
                String roomId = p.getMessage().getRoomid();
                String identity = p.getMessage().getIdentity();
                if (!formerRoom.equals(roomId) && identity.equals(userName)&&!roomId.equals("")){
                    this.currentRoom = roomId;
                    this.prefix = String.format("[%s]: %s> ", this.currentRoom, this.userName);
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
//                System.out.println("DEBUG: Server sends a message with incorrect type!");
                return null;
        }
    }
    private void quit(){
        try {
            selector.close();
            USER_INPUT_HANDLER.shutdown();
            socketChannel.close();
        } catch (IOException e) {
            System.out.println("something wrong with the close methods");
        }finally {
            //System.out.println("Server responds ok, good bye!");
            System.out.print("Disconnected from "+this.currentRoom);
            System.exit(0);
        }
    }
}
