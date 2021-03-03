package sd1920.trab1.impl;

import com.google.gson.Gson;
import sd1920.trab1.DropBoxApi;
import sd1920.trab1.api.Message;
import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.clients.ClientFactory;
import sd1920.trab1.clients.EmailResponse;
import sd1920.trab1.clients.UsersEmailClient;
import sd1920.trab1.util.Address;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class MessageProxy implements MessageService {

    //Class for DropBox Requests
    private final DropBoxApi api;

    //Used for inter-domain communications
    public static String secret;

    //Domain of this server
    private final String domain;
    private final String domainPath;

    //Counter for message ids (to avoid duplicates)
    AtomicInteger midCounter;
    //Prefix for message ids (numerical representation of the server IP, unique per server)
    int midPrefix;

    //Client for local communications with the UserResource
    UsersEmailClient localUserClient;

    //Per domain
    final Map<String, Dispatcher> dispatchers; //Map of all dispatchers -> each dispatcher contains a queue and a thread

    public MessageProxy(String domain, URI selfURI, int midPrefix, String secret, DropBoxApi api) {
        System.out.println("Constructed MessageResource in domain " + domain);
        System.out.println("Prefix: " + midPrefix);

        this.domain = domain;
        this.domainPath = "/" + domain;
        this.midCounter = new AtomicInteger(0);
        this.midPrefix = midPrefix;
        this.secret = secret;
        this.api = api;

        localUserClient = ClientFactory.getUsersClient(selfURI, 5, 1000);

        dispatchers = new ConcurrentHashMap<>();
    }

    public long nextMessageId() {
        //Message id is constructed using the (server-unique) prefix and a local counter
        int counter = midCounter.incrementAndGet();
        return ((long) counter << 32) | (midPrefix & 0xFFFFFFFFL);
    }

    //***************** INBOX OPERATIONS **************************

    @Override
    public Message getMessage(Long version, String user, long mid, String pwd) throws IOException {
        System.out.println("GetMessage: " + user + " " + mid + " " + pwd);
        //Request to UserResource to check if user is valid
        EmailResponse<User> validate = localUserClient.getUser(user, pwd);
        if (validate.getStatusCode() != Response.Status.OK.getStatusCode())
            throw new WebApplicationException(validate.getStatusCode());

        String response = api.downloadFile(domainPath + "/" + user + "/Inbox" + "/" + mid + ".txt");

        //If the message does not exist in users inbox throws 404 NOT FOUND
        if(response == null)
            throw new WebApplicationException(Response.Status.NOT_FOUND);

        return new Gson().fromJson(response, Message.class); //return the message
    }

    @Override
    public List<Long> getMessages(Long version, String user, String pwd) {
        //Same as the above method, but returns all message ids, instead of a single message
        System.out.println("GetInbox: " + user + " " + pwd);
        EmailResponse<User> validate = localUserClient.getUser(user, pwd);
        if (validate.getStatusCode() != Response.Status.OK.getStatusCode())
            throw new WebApplicationException(validate.getStatusCode());

        List<String> folderInfo = api.listFolder(domainPath + "/" + user + "/Inbox");
        List<Long> results = new LinkedList<>();

        for(String filePath : folderInfo){
            String[] filePathDivided = filePath.split("/");
            String fileName = filePathDivided[filePathDivided.length - 1];
            Long msgId = Long.parseLong(fileName.split("\\.")[0]);
            results.add(msgId);
        }
        return results;
    }

    @Override
    public void removeFromUserInbox(Long version, String user, long mid, String pwd) {
        //Similar logic to "getMessage"
        System.out.println("RmvInbox: " + user + " " + mid + " " + pwd);
        EmailResponse<User> validate = localUserClient.getUser(user, pwd);
        if (validate.getStatusCode() != Response.Status.OK.getStatusCode())
            throw new WebApplicationException(validate.getStatusCode());

        if(!api.deleteFileOrFolder(domainPath + "/" + user + "/Inbox" + "/" + mid + ".txt"))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    @Override
    public void removeFromUserInboxReplica(Long version, String user, long mid, String pwd, String secret) {

    }

    //Method called by other servers to forward sent messages
    @Override
    public void forwardSendMessage(Long version, String user, String secret, Message message) {
        //Checks the secret, to make sure clients cannot call this method
        if (secret == null || !secret.equals(MessageProxy.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        //Adds to the user inbox, or throws a 404 if the inbox (and consequently the user) does not exist
        if (!api.uploadFile(domainPath + "/" + user + "/Inbox" + "/" + message.getId() + ".txt",
                "add", message.toString().getBytes())) {
            System.out.println("------------> Mandei um 404");
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        System.out.println("Added " + message.getId() + " to inbox of " + user);
    }

    @Override
    public void forwardSendMessageReplica(Long version, String user, String secret, Message message) {

    }

    //Method called by other servers to forward message deletion
    @Override
    public void forwardDeleteSentMessage(Long version, String user, long mid, String secret) {
        if (secret == null || !secret.equals(MessageProxy.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        if(!api.deleteFileOrFolder(domainPath + "/" + user + "/Inbox" + "/" + mid + ".txt")){
            System.out.println("Not found in forwardDeleteSentMessage, probably deleted by user");
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
    }

    @Override
    public void forwardDeleteSentMessageReplica(Long version, String user, long mid, String secret) {

    }

    //Called by "postMessage" or by the dispatcher to add an error message to the user inbox
    public void createErrorMessage(String formattedSender, String senderName, long msgId, String destination) {
        try {
            //To avoid duplicate code, calls "forwardSendMessage"
            forwardSendMessage(null, senderName, secret,
                    new Message(nextMessageId(), formattedSender, senderName + "@" + domain,
                            "FALHA NO ENVIO DE " + msgId + " PARA " + destination, new byte[0]));
        } catch (WebApplicationException e) {
            //Could happen for instance, if the user sent a message to an invalid destination
            //and was deleted immediately after
            System.out.println("Unexpected WebAppExc in createErrorMessage... " + e.getMessage());
        }
    }

    //***************** OUTBOX OPERATIONS **************************

    @Override
    public long postMessage(Long version, String pwd, Message msg) {
        //Validate msg params
        System.out.println("SendMessage: " + msg);

        //Parses sender into a class
        Address sender = Address.fromString(msg.getSender(), domain);
        //Checks for errors
        if (sender == null || !sender.getDomain().equals(domain))
            throw new WebApplicationException(Response.Status.CONFLICT);
        if (msg.getDestination() == null || msg.getDestination().size() == 0)
            throw new WebApplicationException(Response.Status.CONFLICT);

        //Checks if user is valid
        EmailResponse<User> validate = localUserClient.getUser(sender.getName(), pwd);
        if (validate.getStatusCode() != Response.Status.OK.getStatusCode())
            throw new WebApplicationException(validate.getStatusCode());
        User u = validate.getEntity();

        //Formats the sender of the message to the correct format
        msg.setSender(u.getDisplayName() + " <" + u.getName() + "@" + u.getDomain() + ">");
        //Sets the id to a new unique id
        msg.setId(nextMessageId());

        //Adds to the outbox (useful for the deleteMessage operation)
        String path = domainPath + "/" + u.getName() + "/Outbox" + "/" + msg.getId() + ".txt";

        boolean success = api.uploadFile(path, "add", msg.toString().getBytes());
        while(!success) {
            msg.setId(nextMessageId());
            success = api.uploadFile(path, "add", msg.toString().getBytes());
        }

        //For each destination...
        for (String d : msg.getDestination()) {
            Address destination = Address.fromString(d);
            //If it is invalid, creates an error message in the sender inbox
            if (destination == null) {
                System.out.println("Dest: " + d + " is invalid");
                createErrorMessage(msg.getSender(), u.getName(), msg.getId(), d);
            } else if (destination.getDomain().equals(domain)) {
                //if it is a local domain, simply call the forwardSendMessage function
                //and creates an error message if the destination is invalid
                try {
                    forwardSendMessage(null, destination.getName(), MessageProxy.secret, msg);
                } catch (WebApplicationException e) {
                    System.out.println("Failed to deliver locally " + e.getMessage());
                    createErrorMessage(msg.getSender(), u.getName(), msg.getId(), d);
                }
            } else {
                //if it is in another domain, creates a dispatcher if there is not one already, and submits
                //a new deliverJob to it.
                //The method "computeIfAbsent" creates a new dispatcher, of fetches the existing one if it exists already
                dispatchers.computeIfAbsent(destination.getDomain(), k ->
                        new Dispatcher(k, this)).addDeliverJob(msg, destination.getName(), u.getName());
            }
        }
        return msg.getId();
    }

    @Override
    public long postMessageReplica(Long version, String user, String pwd, String secret, Message msg) {
        return 0;
    }

    @Override
    public void deleteMessage(Long version, String user, long mid, String pwd) throws IOException {
        //Similar logic to "postMessage"
        System.out.println("DeleteMessage: " + user + " " + mid + " " + pwd);
        //Checks if user is valid
        EmailResponse<User> validate = localUserClient.getUser(user, pwd);
        if (validate.getStatusCode() != Response.Status.OK.getStatusCode())
            throw new WebApplicationException(validate.getStatusCode());

        //Checks if the message is in the user outbox, which means it was sent by that user
        //and has not yet been deleted
        String msgPath = domainPath + "/" + user + "/Outbox" + "/" + mid + ".txt";
        String responseDownloadFile = api.downloadFile(msgPath);

        if(responseDownloadFile == null){
            System.out.println("Not found");
            return;
        }

        Message msg = new Gson().fromJson(responseDownloadFile, Message.class);

        api.deleteFileOrFolder(msgPath);

        //For each destination in the original message, either deletes from the inbox if it is a local user
        //or creates a deleteJob in the corresponding dispatcher
        for (String d : msg.getDestination()) {
            Address addr = Address.fromString(d);
            if (addr != null) {
                if (addr.getDomain().equals(domain)) {
                    try {
                        forwardDeleteSentMessage(null, addr.getName(), msg.getId(), MessageProxy.secret);
                    } catch (WebApplicationException ignored) {
                    }
                } else {
                    dispatchers.computeIfAbsent(addr.getDomain(), k -> new Dispatcher(k, this))
                            .addDeleteJob(msg.getId(), addr.getName());
                }
            }
        }
    }

    @Override
    public void deleteMessageReplica(Long version, String user, long mid, String pwd, String secret) {

    }

    //Deprecated method in proxy servers (it's being done in users proxy)
    @Override
    public void deleteUserInfo(String user, String secret) {
    }

    //Deprecated method in proxy servers (it's being done in users proxy)
    @Override
    public void setupUserInfo(String user, String secret) {
    }

    @Override
    public void updateMessages(String secret, Operation op){

    }

}