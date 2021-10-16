package Logic;

import Protocol.Message;
import Protocol.Entity.Room;
import Protocol.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/*
* Construct Json message (Protocol) from Message object
* */
public class ServerResponds {
    public static final String NEW_USER = "";
    public static final String DEFAULT_ROOM = "";

    // ROOM CHANGE
    public static Protocol roomChange(List<Room> chatRoom, String identity, String formerRoomId, String requestedRoomId) throws IOException {
        Message m = new Message();
        String former = formerRoomId == null?"":formerRoomId;
        String roomId = requestedRoomId == null? "":requestedRoomId;
        m.setType(Message.TYPE_ROOM_CHANGE);
        // 1. quit
        if(roomId.equals(Message.EMPTY)){
            m.setFormer(former);
            m.setRoomid(roomId);
            m.setIdentity(identity);
            m.setSuccessed(true);
        }
        // 2. room not found
        else if(findRoom(chatRoom,requestedRoomId) == null ||former.equals(roomId)){
            m.setFormer(former);
            m.setRoomid(former);
            m.setIdentity(identity);
            m.setSuccessed(false);
        }
        // 3. general case
        else{
            // prepare ROOM CHANGE message
            m.setFormer(former);
            m.setRoomid(requestedRoomId);
            m.setIdentity(identity);
            m.setSuccessed(true);
        }
        return new Protocol(m);
    }
    //ROOM CONTENTS
    public static Protocol roomContents(String roomId, List<Room> chatRoom){
        Message m = new Message();
        m.setType(Message.TYPE_ROOM_CONTENTS);
        Room target =findRoom(chatRoom,roomId);
        if (target==null){
            // do not send anything
            m.setSuccessed(false);
        }
        else{
            m.setSuccessed(true);
            m.setRoomid(roomId);
            m.setParticipants(target.getUsers());
            m.setOwner(target.getOwner());
        }
        return new Protocol(m);
    }
    //ROOM LIST
    public static Protocol roomList(List<Room> chatRoom){
        Message m = new Message();
        m.setType(Message.TYPE_ROOM_LIST);
        m.setRooms(chatRoom);
        m.setSuccessed(true);
        return new Protocol(m);
    }
    //MESSAGE
    public static Protocol message(String content, String identity){
        Message m = new Message();
        m.setType(Message.TYPE_MESSAGE);
        m.setContent(content);
        m.setIdentity(identity);
        m.setSuccessed(true);
        return new Protocol(m);
    }
    public synchronized static Room findRoom(List<Room> rooms, String roomId){
        for (Room r : rooms){
            if (r.getRoomid().equals(roomId)&&!roomId.equals(DEFAULT_ROOM))
                return r;
        }
        return null;
    }
}
