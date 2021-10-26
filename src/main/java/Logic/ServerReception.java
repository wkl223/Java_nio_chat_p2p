package Logic;

import Protocol.Entity.Room;
import Protocol.*;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/*
 * When server received a client's message, it will then process it and respond accordingly.
 * Since the server does not have any command that's send to the client by itself.
 * The ServerRespond class may be used alone in the future when there is necessary a server required to send a command to client.
 *  Construct Json message (Protocol) from Message object
 * */
public class ServerReception {
    public static final String DEFAULT_ROOM = "";

    public static Protocol quit(Protocol p, String client, List<Room> chatRoom, Map<String,String> clients) throws IOException {
        Protocol respond = ServerResponds.roomChange(chatRoom,client, clients.get(client), Message.EMPTY);
        return respond;
    }
    public static Protocol message(Protocol p, String client) throws IOException {
        String content = p.getMessage().getContent();
        Protocol respond = ServerResponds.message(content,client);
        return respond;
    }
    public static Protocol deleteRoom(Protocol p,Map<String,String> clients ,List<Room> chatRoom) throws IOException{
        String requestedRoomId = p.getMessage().getRoomid();
        Protocol respond = null;
        Room targetRoom =ServerResponds.findRoom(chatRoom,requestedRoomId);
        if(targetRoom!= null && !targetRoom.getRoomid().equals(DEFAULT_ROOM)) {
            // 1. put everyone into main hall.
            for (String user : targetRoom.users) {
                clients.remove(user);
                clients.put(user, DEFAULT_ROOM);
            }
            // remove room object.
            chatRoom.remove(targetRoom);
            respond = ServerResponds.roomList(chatRoom);
            respond.getMessage().setSuccessed(true);
            return respond;
        }
        respond = ServerResponds.roomList(chatRoom);
        respond.getMessage().setSuccessed(false);
        return respond;
    }
    public static Protocol createRoom(Protocol p, String client,List<Room> chatRoom) throws IOException{
        String requestedRoomId = p.getMessage().getRoomid();
        Protocol respond;
        if(ServerResponds.findRoom(chatRoom,requestedRoomId)== null)
        {
            chatRoom.add(new Room(requestedRoomId, "0", client));
            respond = ServerResponds.roomList(chatRoom);
            respond.getMessage().setSuccessed(true);
        }
        else {
            List<Room> temp = new ArrayList<>(chatRoom);
            temp.remove(ServerResponds.findRoom(temp,requestedRoomId));
            respond = ServerResponds.roomList(temp);
            respond.getMessage().setSuccessed(false);
        }
        return respond;
    }
    public static Protocol list(Protocol p, String client, List<Room> chatRoom) throws IOException {
        Protocol respond = ServerResponds.roomList(chatRoom);
        return respond;
    }

    public static Protocol who(Protocol p, String client, List<Room> chatRoom) throws IOException{
        String requestRoomid = p.getMessage().getRoomid();
        Protocol respond = ServerResponds.roomContents(requestRoomid,chatRoom);
        if(respond.getMessage().isSuccessed())
            return respond;
        else System.out.println("DEBUG - client: " + client + " sent invalid room id for WHO command: " + requestRoomid);
        return respond;
    }
    public static Protocol joinRoom(Protocol p, String client, Map<String,String>clients, List<Room>chatRoom) throws IOException{
        String identity = client;
        String formerRoomId = clients.get(client);
        String requestRoomid = p.getMessage().getRoomid();
        Protocol respond = ServerResponds.roomChange(chatRoom,identity,formerRoomId,requestRoomid);
        if (respond.getMessage().isSuccessed()){
            for(Room r: chatRoom){
                if (r.getRoomid().equals(requestRoomid)) r.addUser(client); //add to new list
                if (r.getRoomid().equals(formerRoomId)) r.removeUser(client); // remove from the former room list.
            }
            clients.remove(identity);
            clients.put(identity,requestRoomid);
        }
        return respond;
    }

    public static Protocol kick(String userName, List<Room> chatRoom, Map<String,String>clients, Map<String,String> clients_addr, List<String> blackList, Selector selector) throws IOException {
        // simply remove the user in all, and add to blacklist
        String targetIp = userName.split(":")[0];
        System.out.println("Kicking from room");
        for (Room r:chatRoom){
            r.removeUser(targetIp);
        }
        System.out.println("Kicking from client list");
        for (var entry: clients.entrySet()){
            if(entry.getKey().contains(targetIp)){
                clients.remove(entry.getKey());
            }
        }
        System.out.println("Kicking from client addresses list");
        for (var entry: clients_addr.entrySet()){
            if(entry.getKey().contains(targetIp)){
                clients_addr.remove(entry.getKey());
            }
        }
        System.out.println("Deregister the keys");
        for(SelectionKey k: selector.keys()){
            String clientIp = (String)k.attachment();
            // The selection key of server itself has no attachment.
            if(clientIp!= null) {
                if (clientIp.contains(targetIp)) {
                    k.cancel();
                    k.channel().close();
                    selector.wakeup();
                    System.out.println("DEBUG - kicked client: " + clientIp);
                }
            }
        }
        blackList.add(targetIp);
        Message m = new Message();
        m.setType(Message.TYPE_KICK);
        m.setSuccessed(true);
        System.out.println("DEBUG - delete user and block ip:"+targetIp);
        return new Protocol(m);
    }
}
