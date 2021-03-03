package sd1920.trab1.impl;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;

public class Operation {

    public static final String POSTUSER = "postUser";
    public static final String UPDATEUSER = "updateUser";
    public static final String DELETEUSER = "deleteUser";
    public static final String POSTMESSAGE = "postMessage";
    public static final String DELETEMESSAGE = "deleteMessage";
    public static final String REMOVEFROMUSERINBOX = "removeFromUserInbox";
    public static final String FORWARDSENDMESSAGE = "forwardSendMessage";
    public static final String FORWARDDELETESENTMESSAGE = "forwardDeleteSentMessage";

    private String type, name, pwd;
    private Long mid;
    private User user;
    private Message msg;

    public Operation(String type, String name, String pwd, User user, Long mid, Message msg){
        this.type = type;
        this.name = name;
        this.pwd = pwd;
        this.user = user;
        this.mid = mid;
        this.msg = msg;
    }

    public Operation(){
        this.type = null;
        this.name = null;
        this.pwd = null;
        this.user = null;
        this.mid = null;
        this.msg = null;
    }

    public String getType(){
        return type;
    }

    public String getName(){
        return name;
    }

    public String getPwd(){
        return pwd;
    }

    public User getUser(){
        return user;
    }

    public Long getMid(){
        return mid;
    }

    public Message getMsg(){
        return msg;
    }

    public void setType(String type){
        this.type = type;
    }

    public void setName(String name){
        this.name = name;
    }

    public void setPwd(String pwd){
        this.pwd = pwd;
    }

    public void setUser(User user){
        this.user = user;
    }

    public void setMid(long mid){
        this.mid =  mid;
    }

    public void setMsg(Message msg){
        this.msg = msg;
    }
}
