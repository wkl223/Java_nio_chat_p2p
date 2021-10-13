package Protocol.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class Room{
    private String roomid;
    private String count;
    @JsonIgnore
    public String owner;
    @JsonIgnore
    public List<String> users;
    @JsonIgnore
    public static final int SUCCESS =1;
    @JsonIgnore
    public static final int FAILURE =-1;

    public Room(){
    }
    public Room(String roomid, String count, String owner) {
        this.roomid = roomid;
        this.count = count;
        this.owner=owner;
        users = new ArrayList<String>();
    }
    public Room(String roomid, String count) {
        this.roomid = roomid;
        this.count = count;
        this.owner="";
        users = new ArrayList<String>();
    }
    public synchronized int removeUser(String username){
        if(users.contains(username)){
            users.remove(username);
            return SUCCESS;
        }
        else return FAILURE;
    }
    public synchronized int addUser(String username){
        // avoid duplicate add
        if(users.contains(username)) return FAILURE;
        else{
            users.add(username);
            return SUCCESS;
        }
    }
    public String getRoomid(){
        return roomid;
    }
    public String getCount(){
        // if there is users in the object, then it means it is the server-side object.
        if(users != null)
            count = String.valueOf(users.size());
        return count;
    }
    public List<String> getUsers(){
        return users;
    }
    public String getOwner(){
        return owner;
    }
    public String toString(){
        return "roomId: "+roomid+", count: "+count;
    }
}