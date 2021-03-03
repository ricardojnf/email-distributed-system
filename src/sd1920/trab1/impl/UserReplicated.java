package sd1920.trab1.impl;

import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.clients.ClientFactory;
import sd1920.trab1.clients.EmailResponse;
import sd1920.trab1.clients.MessagesEmailClient;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class UserReplicated implements UserService {

    //Since there is a single data structure, using a concurrent structure (ConcurrentHashMap)
    //is simpler than using a synchronized block
    private ConcurrentMap<String, User> users;
    String domain;
    String selfURI;

    private VersionControl versionControl;

    private MessagesEmailClient localMessageClient;


    public UserReplicated(String domain, URI selfURI, VersionControl versionControl) {
        System.out.println("Constructed UserResource in domain " + domain);
        this.users = new ConcurrentHashMap<>();
        this.domain = domain;
        this.selfURI = selfURI.toString();
        this.versionControl = versionControl;
        this.localMessageClient = ClientFactory.getMessagesClient(selfURI, 2, 20000);
    }

    //if version == null is because the client (tester or remote domain/server)
    // did not pass any version
    @Override
    public String postUser(Long version, User user) {
        String primaryURI = versionControl.getPrimaryURI();

        if(!primaryURI.equals(this.selfURI))
            throw new WebApplicationException(Response.temporaryRedirect(URI.create(
                    primaryURI + UserService.PATH)).build());

        System.out.println("PostUser: " + user);

        postUserOperation(user);

        Operation op = new Operation(Operation.POSTUSER, null, null, user, null, null);
        long v = versionControl.addVersion(op);

        //Replicate
        for(String secondaryURI : versionControl.getSecondariesURI()) {
            try {
                ClientFactory.getUsersClient(new URI(secondaryURI), 1, 1000).postUser(version, MessageReplicated.secret, user);
            } catch (URISyntaxException ignored) {}
        }

        throw new WebApplicationException(Response.status(200).
                header(MessageService.HEADER_VERSION, v)
                .entity(user.getName() + "@" + user.getDomain()).build());
    }

    @Override
    public String postUserReplica(Long version, String secret, User user){
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        if(version != null && versionControl.getVersion() < version)
            update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        System.out.println("Post user replica: " + user);

        postUserOperation(user);

        Operation op = new Operation(Operation.POSTUSER, null, null, user, null, null);
        versionControl.addVersion(op);

        return user.getName() + "@" + user.getDomain();
    }

    @Override
    public User getUser(Long version, String name, String pwd) {
        //This method is also used from other methods in this class and in
        // MessageReplicated to validate user/password combinations.
        System.out.println("Recebi pedido para getUser: " + name);

        if(version != null && versionControl.getVersion() < version) {
            update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);
        }

        //Checks parameters, and if the user exists, returns it.
        if (name == null || name.isEmpty() || pwd == null || pwd.isEmpty())
            throw new WebApplicationException("Invalid or empty user or password", Response.Status.FORBIDDEN);
        User user = users.get(name);
        if (user == null)
            throw new WebApplicationException("Invalid user", Response.Status.FORBIDDEN);
        if (!user.getPwd().equals(pwd))
            throw new WebApplicationException("Invalid password", Response.Status.FORBIDDEN);

        throw new WebApplicationException(Response.status(200).
                header(MessageService.HEADER_VERSION, versionControl.getVersion())
                .entity(user).build());
    }

    @Override
    public User updateUser(Long version, String name, String pwd, User user) {
        String primaryURI = versionControl.getPrimaryURI();
        if(!primaryURI.equals(this.selfURI))
            throw new WebApplicationException(Response.temporaryRedirect(URI.create(
                    primaryURI + UserService.PATH + "/" + name + "?pwd=" + pwd)).build());

        System.out.println("UpdateUser: " + name);

        User newUser = updateUserOperation(name, pwd, user);

        Operation op = new Operation(Operation.UPDATEUSER, name, pwd, user, null, null);
        long v = versionControl.addVersion(op);

        for(String secondaryURI : versionControl.getSecondariesURI()) {
            try {
                ClientFactory.getUsersClient(new URI(secondaryURI), 1, 1000).
                        updateUser(version, name, pwd, MessageReplicated.secret, user);
            } catch (URISyntaxException ignored) {}
        }
        throw new WebApplicationException(Response.status(200).
                header(MessageService.HEADER_VERSION, v)
                .entity(newUser).build());
    }

    @Override
    public User updateUserReplica(Long version, String name, String pwd, String secret, User user) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        System.out.println("UpdateUserReplica: " + name);

        if(version != null && versionControl.getVersion() < version)
            update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        User newUser = updateUserOperation(name, pwd, user);

        Operation op = new Operation(Operation.UPDATEUSER, name, pwd, user, null, null);
        versionControl.addVersion(op);

        return newUser;
    }

    @Override
    public User deleteUser(Long version, String user, String pwd) {
        String primaryURI = versionControl.getPrimaryURI();
        if(!primaryURI.equals(this.selfURI))
            throw new WebApplicationException(Response.temporaryRedirect(URI.create(
                    primaryURI + UserService.PATH + "/" + user + "?pwd=" + pwd)).build());

        System.out.println("DeleteUser: " + user);

        User u = deleteUserOperation(user, pwd);

        Operation op = new Operation(Operation.DELETEMESSAGE, user, pwd, null, null, null);
        long v = versionControl.addVersion(op);

        for(String secondaryURI : versionControl.getSecondariesURI()) {
            try {
                ClientFactory.getUsersClient(new URI(secondaryURI), 1, 1000).
                        deleteUser(version, user, pwd, MessageReplicated.secret);
            } catch(URISyntaxException ignored) {}
        }
        throw new WebApplicationException(Response.status(200)
                .header(MessageService.HEADER_VERSION, v)
                .entity(u).build());
    }

    @Override
    public User deleteUserReplica(Long version, String user, String pwd, String secret) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        System.out.println("DeleteUserReplica: " + user);

        if(version != null && versionControl.getVersion() < version)
            update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        User u = deleteUserOperation(user, pwd);

        Operation op = new Operation(Operation.DELETEMESSAGE, user, pwd, null, null, null);
        versionControl.addVersion(op);

        return u;
    }

    @Override
    public List<Operation> getOperations(Long version, String secret){
        //Checks the secret, to make sure clients cannot call this method
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        //return the operations
        return versionControl.getOperations(version);
    }

    @Override
    public void update(Long version, String targetURI, String secret) {

        synchronized (this) {

            if (secret == null || !secret.equals(MessageReplicated.secret))
                throw new WebApplicationException(Response.Status.FORBIDDEN);

            List<Operation> ops = null;
            try {
                ops = ClientFactory.getUsersClient(new URI(targetURI), 5, 1000).
                        getOperations(version, MessageReplicated.secret);
            } catch(URISyntaxException ignored) {}

            for (Operation op : ops) {
                versionControl.addVersion(op);
                switch (op.getType()) {
                    case Operation.POSTUSER:
                        postUserOperation(op.getUser());
                        break;
                    case Operation.UPDATEUSER:
                        updateUserOperation(op.getName(), op.getPwd(), op.getUser());
                        break;
                    case Operation.DELETEUSER:
                        deleteUserOperation(op.getName(), op.getPwd());
                        break;
                    default:
                        localMessageClient.updateMessage(op, secret);
                }
            }
        }
    }

    private void postUserOperation(User user){
        //Check errors in the request
        if (user.getName() == null || user.getName().isEmpty() || user.getName().contains("@") ||
                user.getPwd() == null || user.getPwd().isEmpty() || user.getDomain() == null ||
                user.getDomain().isEmpty())
            throw new WebApplicationException("Invalid or empty user or password", Response.Status.CONFLICT);
        if (!user.getDomain().equals(this.domain))
            throw new WebApplicationException("Invalid domain", Response.Status.FORBIDDEN);


        //putIfAbsent atomically checks if the user already exists, and if it does not, inserts it into the map
        User old = users.putIfAbsent(user.getName(), user);
        if (old != null)
            throw new WebApplicationException("User already exists", Response.Status.CONFLICT);

        //Create the inbox in the MessageResource
        EmailResponse<Void> post = localMessageClient.setupUserInfo(user.getName(), MessageReplicated.secret);
        //Errors in local requests should never happen, but we are handling them just in case
        if (post.getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
            System.err.println("Unexpected error in setupUserInfo: " + post);
            throw new WebApplicationException(500);
        }
    }

    private User updateUserOperation(String name, String pwd, User user) throws WebApplicationException{
        //fetches the user
        User oldUser = null;
        try {
            getUser(null, name, pwd);
        }
        catch(WebApplicationException e){
            if(e.getResponse().getStatus() != 200)
                throw e;
            oldUser = (User) e.getResponse().getEntity();
        }

        User newUser = new User();
        newUser.setName(oldUser.getName());
        newUser.setDomain(oldUser.getDomain());
        newUser.setPwd((user.getPwd() != null && !user.getPwd().isEmpty()) ?
                user.getPwd() : oldUser.getPwd());
        newUser.setDisplayName((user.getDisplayName() != null && !user.getDisplayName().isEmpty()) ?
                user.getDisplayName() : oldUser.getDisplayName());
        //replaces with the new one, returns null if the user was deleted between the fetch and now
        if (users.replace(name, newUser) == null)
            throw new WebApplicationException("User does not exist", Response.Status.CONFLICT);

        return newUser;
    }

    private User deleteUserOperation(String user, String pwd){
        //synchronized block since the "getUser" method also handles the "users" structure
        //and we do not want concurrency errors in two simultaneous deletes
        User u = null;

        synchronized (this) {
            try {
                getUser(null, user, pwd);
            } catch (WebApplicationException e) {
                if (e.getResponse().getStatus() != 200)
                    throw e;
                u = (User) e.getResponse().getEntity();
                users.remove(user);
            }
        }

        //Deletes the users' inbox and outbox from MessageResource
        EmailResponse<Void> delete = localMessageClient.deleteUserInfo(user, MessageReplicated.secret);
        //Errors in local requests should never happen, but we are handling them just in case
        if (delete.getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()){
            System.err.println("Unexpected error in deleteUserInfo: " + delete);
            throw new WebApplicationException(500);
        }
        return u;
    }

}