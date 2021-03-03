package sd1920.trab1.impl;

import com.google.gson.Gson;
import sd1920.trab1.DropBoxApi;
import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.UserService;

import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class UserProxy implements UserService {

    //Domain of this server
    private final String domain;
    private final String domainPath;

    //Class for DropBox Requests
    private final DropBoxApi api;

    public UserProxy(String domain, DropBoxApi api) {
        System.out.println("Constructed UserResource in domain " + domain);
        this.domain = domain;
        this.domainPath = "/" + domain;
        this.api = api;
    }

    // ************ Operations ************

    @Override
    public String postUser(Long version, User user) {
        System.out.println("PostUser: " + user);
        //Check errors in the request
        if (user.getName() == null || user.getName().isEmpty() || user.getName().contains("@") ||
                user.getPwd() == null || user.getPwd().isEmpty() || user.getDomain() == null ||
                user.getDomain().isEmpty())
            throw new WebApplicationException("Invalid or empty user or password", Response.Status.CONFLICT);
        if (!user.getDomain().equals(this.domain))
            throw new WebApplicationException("Invalid domain", Response.Status.FORBIDDEN);

        //Creates a new directory in DropBox for the new user, if he does not exist
        String userFolderPath = domainPath + "/" + user.getName();
        if (!api.createDirectory(userFolderPath))
            throw new WebApplicationException("User already exists", Response.Status.CONFLICT);

        //Create a file with the user's info
        api.uploadFile(userFolderPath + "/" + user.getName() + ".txt", "add", user.toString().getBytes());

        //Create the inbox and outbox of the new user
        List<String> inboxAndOutbox = new ArrayList<>(2);
        inboxAndOutbox.add(userFolderPath + "/Inbox");
        inboxAndOutbox.add(userFolderPath + "/Outbox");
        api.createMultipleDirectories(inboxAndOutbox);

        return user.getName() + "@" + user.getDomain();
    }

    //It is not used in this server
    @Override
    public String postUserReplica(Long version, String secret, User user) {
        return null;
    }


    @Override
    public User getUser(Long version, String name, String pwd) throws IOException {
        System.out.println("GetUser: " + name);
        //This method is also used from other methods in this class and in
        // MessageResource to validate user/password combinations.

        //Checks parameters, and if the user exists, returns it.
        if (name == null || name.isEmpty() || pwd == null || pwd.isEmpty())
            throw new WebApplicationException("Invalid or empty user or password", Response.Status.FORBIDDEN);

        String response = api.downloadFile(domainPath + "/" + name + "/" + name + ".txt");
        System.out.println(response);

        if (response == null)
            throw new WebApplicationException("Invalid user", Response.Status.FORBIDDEN);

        User user = new Gson().fromJson(response, User.class);

        if (!user.getPwd().equals(pwd))
            throw new WebApplicationException("Invalid password", Response.Status.FORBIDDEN);
        return user;
    }

    @Override
    public User updateUser(Long version, String name, String pwd, User user) throws IOException {
        //fetches the user
        System.out.println("UpdateUser: " + name);
        User oldUser = getUser(version, name, pwd);

        User newUser = new User();
        newUser.setName(oldUser.getName());
        newUser.setDomain(oldUser.getDomain());
        newUser.setPwd((user.getPwd() != null && !user.getPwd().isEmpty()) ?
                user.getPwd() : oldUser.getPwd());
        newUser.setDisplayName((user.getDisplayName() != null && !user.getDisplayName().isEmpty()) ?
                user.getDisplayName() : oldUser.getDisplayName());
        //replaces with the new one, returns false if the user was deleted between the fetch and now
        String userInfoPath = domainPath + "/" + name + "/" + name + ".txt";
        if (!api.uploadFile(userInfoPath, "overwrite", newUser.toString().getBytes()))
            throw new WebApplicationException("User does not exist", Response.Status.FORBIDDEN);
        return newUser;
    }

    @Override
    public User updateUserReplica(Long version, String name, String pwd, String secret, User user) throws IOException {
        return null;
    }


    @Override
    public User deleteUser(Long version, String name, String pwd) throws IOException {
        //Deletes the user
        System.out.println("DeleteUser: " + name);
        User u = getUser(version, name, pwd);

        //Errors in local requests should never happen, but we are handling them just in case
        if (!api.deleteFileOrFolder(domainPath + "/" + name)){
            System.err.println("Unexpected error in deleteUserInfo");
            throw new WebApplicationException("Unexpected error", Response.Status.FORBIDDEN);
        }
        return u;
    }

    @Override
    public User deleteUserReplica(Long version, String name, String pwd, String secret) throws IOException {
        return null;
    }

    @Override
    public List<Operation> getOperations(Long version, String secret) {
        return null;
    }

    @Override
    public void update(Long version, String targetURI, String secret) {
        return;
    }
}
