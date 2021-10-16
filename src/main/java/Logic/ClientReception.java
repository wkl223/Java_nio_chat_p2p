package Logic;

import Protocol.*;
import Protocol.Entity.Room;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ClientReception {
    private static Room findRoom(List<Room> rooms, String roomid){
        for (Room r : rooms){
            if (r.getRoomid().equals(roomid))
                return r;
        }
        return null;
    }

    public static String roomChange(Protocol p, String clientName, Map<String,String> request) throws IOException {
        String formerRoom = p.getMessage().getFormer();
        String roomid = p.getMessage().getRoomid();
        String identity = p.getMessage().getIdentity();
        if(formerRoom.equals(roomid) && !roomid.equals("")) return "The requested room is invalid or non existent.";
        if (roomid.equals(Message.EMPTY) && clientName.equals(identity) && request.containsKey(Message.TYPE_QUIT)) return Message.OK;
        if (roomid.equals(Message.EMPTY)) return identity+" has left";
        if (formerRoom.equals(Message.EMPTY)) return identity+" moves to "+roomid;
        return identity+" moved from "+formerRoom+" to "+roomid;
    }
    public static String roomContents(Protocol p) {
        String roomId = p.getMessage().getRoomid();
        List<String> identities = p.getMessage().getParticipants();
        String owner = p.getMessage().getOwner();
        String persons = "";
        for(int i=0; i <identities.size();i++){
            if(owner.equals(identities.get(i)))
                identities.set(i,identities.get(i)+Message.ROOM_OWNER_MARK);
            persons = persons+identities.get(i)+" ";
        }
        return roomId+" contains "+persons;
    }
    public static String roomList(Protocol p, Map<String,String> request) throws IOException{
        // partial message
        List<Room> rooms =p.getMessage().getRooms();
        String response="";
        //if it is a follow-up of delete/createroom request.
        if(request.size()!=0){
            String out = "";
            if(findRoom(rooms,request.get(Message.TYPE_DELETE))!=null) {
                out = "Error, the room "+request.get(Message.TYPE_DELETE)+" was not deleted.";
            }
            else if(request.containsKey(Message.TYPE_DELETE)){
                out ="The room "+request.get(Message.TYPE_DELETE)+ " was not found or successfully deleted";
            }
            if(findRoom(rooms,request.get(Message.TYPE_ROOM_CREATION))!=null){
                out = "The room "+request.get(Message.TYPE_ROOM_CREATION)+" is created";
            }
            else if(request.containsKey(Message.TYPE_ROOM_CREATION)){
                out = "Error, the room "+request.get(Message.TYPE_ROOM_CREATION)+" was not created";
            }
            request.clear();
            return out;
        }
        for(Room r: rooms){
            response += Message.addHeadAndTail(r.getRoomid()+", Number of people in the room:"+r.getCount(),"[","]");
        }
        if(rooms == null){
            response = "No room is available.";
        }
        return response;
    }
    public static String message(Protocol p,String client) throws IOException{
        String identity = p.getMessage().getIdentity();
        String content = p.getMessage().getContent();
        if(identity.equals(client)) return content;
        return Message.addHeadAndTail(identity,"[","]")+": "+content;
    }

}
