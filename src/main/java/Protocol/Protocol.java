package Protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.StringJoiner;

public class Protocol {
    static ObjectMapper mapper = new ObjectMapper();
    public static final String INVALID_JSON = "INVALID";
    public static final String SUCCESS = "Done";
    private Message m;
    public Protocol(String message) {
        try {
            m = mapper.readValue(message, Message.class);
        }
        catch (JsonProcessingException e){
            System.out.println("PROTOCOL ERROR: INVALID INPUT OBJECT");
//            e.printStackTrace();
        }
        catch (Exception e){
            //just in case
            System.out.println("PROTOCOL ERROR: INVALID INPUT OBJECT");
        }
    }
    public Protocol(Message m){
        this.m = m;
    }
    public Message getMessage(){return m;}
    public String getType(){return m.getType();}
    // same as getMessage, but with extra check for the receiver side.
    public Message decodeJson() throws IOException{
        if (isValidType()){
            return m;
        }
        return new Message();
    }
    // Message object to String that's ready to send.
    public String encodeJson() throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (isValidType())
        {
                mapper.writeValue(out,m);
                return new String(out.toByteArray());
        }
        return INVALID_JSON;
    }
    public static boolean isValidCommandType(String input){
        switch (input){
            case Message.TYPE_WHO:
            case Message.TYPE_JOIN:
            case Message.TYPE_ROOM_CREATION:
            case Message.TYPE_LIST:
            case Message.TYPE_DELETE:
            case Message.TYPE_QUIT:
            case Message.TYPE_CONNECT:
            case Message.TYPE_KICK:
                return true;
            default:
                return false;
        }
    }
    public static boolean isValidType(String input){
        switch (input){
            case Message.TYPE_DELETE:
            case Message.TYPE_JOIN:
            case Message.TYPE_NEW_IDENTITY:
            case Message.TYPE_IDENTITY_CHANGE:
            case Message.TYPE_LIST:
            case Message.TYPE_QUIT:
            case Message.TYPE_ROOM_CONTENTS:
            case Message.TYPE_ROOM_CREATION:
            case Message.TYPE_ROOM_CHANGE:
            case Message.TYPE_ROOM_LIST:
            case Message.TYPE_WHO:
            case Message.TYPE_MESSAGE:
            case Message.TYPE_CONNECT:
            case Message.TYPE_HOST_CHANGE:
            case Message.TYPE_KICK:
                return true;
            default:
                return false;
        }
    }
    public boolean isValidType(){
        String type ="";
        try{
            type = m.getType();
        }catch (Exception e){
            //reject anything strange.
            System.out.println("DEBUG - isValidType() error");
        }
        return isValidType(type);
    }


}
