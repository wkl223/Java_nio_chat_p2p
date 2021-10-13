package Protocol;

import Protocol.Entity.Room;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(value = { "isSuccessed","successed" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message<T> {
    private String type;
    private String content;
    private String identity;
    private String room;
    private String former;
    private String owner;
    private String roomid;
    private List<Room> rooms;
    private List<String> identities;
    @JsonIgnore
    private boolean isSuccessed;

    // message head constant
    public static final String MESSAGE_HEAD = "content";
    public static final String TYPE_HEAD = "type";
    public static final String ROOM_HEAD ="room";
    public static final String ROOM_DESTINATION_HEAD ="roomid";
    public static final String ROOM_LIST_HEAD ="rooms";
    public static final String FORMER_HEAD ="former";
    public static final String IDENTITY_HEAD ="identity";
    public static final String EMPTY = "";
    // type constant
    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_QUIT = "quit";
    public static final String TYPE_NEW_IDENTITY = "newidentity";
    public static final String TYPE_IDENTITY_CHANGE = "identitychange";
    public static final String TYPE_JOIN = "join";
    public static final String TYPE_ROOM_CONTENTS = "roomcontents";
    public static final String TYPE_WHO = "who";
    public static final String TYPE_ROOM_LIST = "roomlist";
    public static final String TYPE_ROOM_CREATION = "createroom";
    public static final String TYPE_DELETE = "delete";
    public static final String TYPE_LIST = "list";
    public static final String TYPE_ROOM_CHANGE ="roomchange";
    // delimiters and marks
    public static final String KV_DELIMITER = ":";
    public static final String TUPLE_DELIMITER =",";
    public static final String ROOM_OWNER_MARK ="*";
    public static final String OK = "OK";
    public String getContent(){
        return this.content;
    }
    public String getType(){return this.type;}
    public String getIdentity(){return this.identity;}
    public String getRoom(){
        return this.room;
    }
    public String getOwner(){return this.owner;}
    public String getFormer(){return this.former;}
    public String getRoomid(){return this.roomid;}
    public List<Room> getRooms(){return this.rooms;}
    public List<String> getParticipants(){return this.identities;}

    public void setType(String type) {this.type = type;}
    public void setContent(String content) {this.content = content;}
    public void setIdentity(String identity) {this.identity = identity;}
    public void setFormer(String former) {this.former = former;}
    public void setOwner(String owner) {this.owner = owner;}
    public void setRoomid(String roomid) {this.roomid = roomid;}
    public void setRooms(List<Room> rooms){this.rooms=rooms;}
    public void setParticipants(List<String> participants) {this.identities = participants;}


    public Message(){}//empty constructor

    public static String addHeadAndTail(String message, String head, String tail){
        String out = message;
        out = head+out+tail;
        return out;
    }
    public static String transformMessagePairs(String head, String content){
        String message = addHeadAndTail(head,"\"","\"")+KV_DELIMITER+addHeadAndTail(content,"\"","\"");
        return message;
    }
    public static String transformListPairs(String head, String content){
        String message = addHeadAndTail(head,"\"","\"")+KV_DELIMITER+content;
        return message;
    }

    public static String jsonCompose(List<String> content){
        String out="";
        for(String s :content){
            out += s+TUPLE_DELIMITER;
        }
        out = out.substring(0,out.length()-1);//ignore the last comma
        return addHeadAndTail(out,"{","}");
    }
    public static String jsonCompose(Message m) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        mapper.writeValue(out,m);
        return new String(out.toByteArray());
    }
    public static String roomListToJson(List<Room> rooms) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(out, rooms);
        final byte[] data =out.toByteArray();
        return new String(data);
    }

    public boolean isSuccessed() {
        return isSuccessed;
    }

    public void setSuccessed(boolean successed) {
        isSuccessed = successed;
    }

}

