package Logic;

import Protocol.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/*
 * Construct Json message (Protocol) from Message object
 * */
public class ClientResponds {
    private static final String ALPHABETIC_PATTERN ="[A-Za-z]+";
    private static final String NUMERIC_PATTERN="[0-9]+";
    private static final String ALPHANUMERIC_PATTERN="[A-Za-z0-9]+";
    public static final String INVALID ="INVALID";

    //since it is human typing command, so extra command checking function is required.
    public static String getCommand(String message){
        StringTokenizer tokenizer = new StringTokenizer(message," ");
        if((tokenizer.countTokens()<=2)&&tokenizer.countTokens()>0){
            String firstArg =tokenizer.nextToken().substring(1);
            if(Protocol.isValidCommandType(firstArg))
                return firstArg;
        }
        return null;
    }

    public static String processMessage(String message, Map<String,String> requests, boolean isConnected) throws IOException {
        String command =getCommand(message);
        if(command!=null){
            StringTokenizer tokenizer = new StringTokenizer(message," ");
            tokenizer.nextToken();
            Protocol answer;
            String request = null;
            try {
                 request = tokenizer.nextToken();
            }catch(Exception e){
                //don't do anything
                }
            // local commands, will regulate the message into Message.TYPE
            // the actual logic is executed in client main.
            if(!isConnected){
                switch(command){
                    case Message.TYPE_ROOM_CREATION: {
                        answer = createRoom(request);
                        if (answer == null) return "Invalid room name";
                        else if(request!=null){
                            requests.put(Message.TYPE_ROOM_CREATION,request);
                        }
                        return createRoom(request).encodeJson();
                    }
                    case Message.TYPE_DELETE: {
                        if(request!=null)
                            requests.put(Message.TYPE_DELETE,request);
                        return deleteRoom(request).encodeJson();
                    }
                    case Message.TYPE_QUIT: {
                        return quit().encodeJson();
                    }
                    case Message.TYPE_CONNECT:{
                        return connect(request).encodeJson();
                    }
                    case Message.TYPE_HOST_CHANGE:{
                        return hostchange(request).encodeJson();
                    }
                    case Message.TYPE_KICK:{
                        return kick(request).encodeJson();
                    }
                    case Message.TYPE_SEARCH_NETWORK:{
                        return searchNetwork().encodeJson();
                    }
                    default: {
                        System.out.println("DEBUG - SOMETHING WRONG WITH THE PROCESS MESSAGE FUNCTION?");
                        return INVALID;
                    }
                }
            }
            else{
                switch(command){
                    case Message.TYPE_JOIN: {
                        if(request !=null)
                            return joinRoom(request).encodeJson();
                        return INVALID;
                    }
                    case Message.TYPE_WHO: {
                        if(request !=null)
                            return who(request).encodeJson();
                        return INVALID;
                    }
                    case Message.TYPE_LIST:
                        return list().encodeJson();
                    case Message.TYPE_QUIT: {
                        requests.put(Message.TYPE_QUIT, Message.EMPTY);
                        return quit().encodeJson();
                    }
                    case Message.TYPE_HOST_CHANGE:{
                        hostchange(request).encodeJson();
                    }
                    case Message.TYPE_LIST_NEIGHBORS:{
                        return listNeighbors().encodeJson();
                    }
                    case Message.TYPE_SEARCH_NETWORK:{
                        return searchNetwork().encodeJson();
                    }
                    default: {
                    System.out.println("DEBUG - SOMETHING WRONG WITH THE PROCESS MESSAGE FUNCTION?");
                        return INVALID;
                    }
                }
            }

        }
        return message(message).encodeJson();
    }

    public static Protocol searchNetwork() {
        Message m = new Message();
        m.setType(Message.TYPE_SEARCH_NETWORK);
        return new Protocol(m);
    }

    public static Protocol listNeighbors() {
        Message m = new Message();
        m.setType(Message.TYPE_LIST_NEIGHBORS);
        return new Protocol(m);
    }

    public static Protocol kick(String request) {
        // respond to the local client only.
        Message m = new Message();
        m.setType(Message.TYPE_KICK);
        m.setContent(request);
        return new Protocol(m);
    }

    public static Protocol joinRoom(String request){
        Message m = new Message();
        m.setType(Message.TYPE_JOIN);
        m.setRoomid(request);
        return new Protocol(m);
    }
    public static Protocol who(String request){
        Message m = new Message();
        m.setType(Message.TYPE_WHO);
        m.setRoomid(request);
        return new Protocol(m);
    }
    public static Protocol list(){
        Message m = new Message();
        m.setType(Message.TYPE_LIST);
        return new Protocol(m);
    }
    public static Protocol createRoom(Object re){
        String request = null;
        if(re instanceof String) request = (String) re;
        else return null;
        if (request.length()<3 || request.length()>32) return null;
        if (!request.matches(ALPHABETIC_PATTERN)) return null;
        Message m = new Message();
        m.setType(Message.TYPE_ROOM_CREATION);
        m.setRoomid(request);
        return new Protocol(m);
    }
    public static Protocol deleteRoom(String request){
        Message m = new Message();
        m.setType(Message.TYPE_DELETE);
        m.setRoomid(request);
        return new Protocol(m);
    }
    public static Protocol message(String request){
        Message m = new Message();
        m.setType(Message.TYPE_MESSAGE);
        m.setContent(request);
        return new Protocol(m);
    }
    public static Protocol quit(){
        Message m = new Message();
        m.setType(Message.TYPE_QUIT);
        return new Protocol(m);
    }

    // customized protocol message, only used in local for consistency.
    public static Protocol connect(String request){
        Message m = new Message();
        m.setType(Message.TYPE_CONNECT);
        m.setContent(request);
        return new Protocol(m);
    }

    // host change
    public static Protocol hostchange(String address){
        Message m = new Message();
        m.setType(Message.TYPE_HOST_CHANGE);
        m.setHost(address);
        return new Protocol(m);
    }

}
