package sd1920.trab1.impl;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class MessageReplicated implements MessageService {

    //Used for inter-domain communications
    public static String secret;

    String domain;
    String selfURI;
    //Counter for message ids (to avoid duplicates)
    AtomicInteger midCounter;
    //Prefix for message ids (numerical representation of the server IP, unique per server)
    int midPrefix;

    VersionControl versionControl;

    //Client for local communications with the UserResource
    UsersEmailClient localUserClient;

    //Per user
    final Map<String, Map<Long, Message>> inBoxes; //inbox of each user
    final Map<String, Map<Long, Message>> outBoxes; //outbox (sent messages) of each user

    //Per domain
    final Map<String, Dispatcher> dispatchers; //Map of all dispatchers -> each dispatcher contains a queue and a thread

    public MessageReplicated(String domain, URI selfURI, int midPrefix, String secret, VersionControl versionControl) {
        System.out.println("Constructed MessageResource in domain " + domain);
        System.out.println("Prefix: " + midPrefix);

        this.secret = secret;
        this.domain = domain;
        this.selfURI = selfURI.toString();
        this.midCounter = new AtomicInteger(0);
        this.midPrefix = midPrefix;
        this.versionControl = versionControl;

        localUserClient = ClientFactory.getUsersClient(selfURI, 5, 1000);

        inBoxes = new HashMap<>();
        outBoxes = new HashMap<>();
        dispatchers = new ConcurrentHashMap<>();
    }

    public long nextMessageId() {
        //Message id is constructed using the (server-unique) prefix and a local counter
        int counter = midCounter.incrementAndGet();
        return ((long) counter << 32) | (midPrefix & 0xFFFFFFFFL);
    }

    //***************** INBOX OPERATIONS **************************
    @Override
    public Message getMessage(Long version, String user, long mid, String pwd) {
        System.out.println("GetMessage: " + user + " " + mid + " " + pwd);

        if(version != null && versionControl.getVersion() < version && !selfURI.equals(versionControl.getPrimaryURI())) {
            localUserClient.update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);
        }

        //Request to UserResource to check if user is valid
        getUserOperation(user, pwd);
        Message result;
        //synchronized since we are handling data structures
        synchronized (this) {
            Map<Long, Message> inbox = inBoxes.get(user);
            if (inbox == null) //if the user exists, then so should the inbox.
                throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            result = inbox.get(mid);
            if (result == null) //throw 404 if the message does not exist
                throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        throw new WebApplicationException(Response.status(200).
                header(MessageService.HEADER_VERSION, versionControl.getVersion())
                .entity(result).build()); //return the message
    }

    @Override
    public List<Long> getMessages(Long version, String user, String pwd) {
        //Same as the above method, but returns all message ids, instead of a single message
        System.out.println("GetInbox: " + user + " " + pwd);

        if(version != null && versionControl.getVersion() < version && !selfURI.equals(versionControl.getPrimaryURI())) {
            localUserClient.update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);
        }

        getUserOperation(user, pwd);
        List<Long> results;
        synchronized (this) {
            Map<Long, Message> inbox = inBoxes.get(user);
            if (inbox == null)
                throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            results = new LinkedList<>(inbox.keySet()); //adds all the keys of the map to a list
        }

        throw new WebApplicationException(Response.status(200).
                header(MessageService.HEADER_VERSION, versionControl.getVersion())
                .entity(results).build());
    }

    @Override
    public void removeFromUserInbox(Long version, String user, long mid, String pwd) {
        //Similar logic to "getMessage"
        String primaryURI = versionControl.getPrimaryURI();
        if(!primaryURI.equals(this.selfURI))
            throw new WebApplicationException(Response.temporaryRedirect(URI.create(
                    primaryURI + MessageService.PATH + "/mbox/" + user + "/" + mid +
                    "?pwd=" + pwd)).build());

        removeFromUserInboxOperation(user, mid, pwd);

        Operation op = new Operation(Operation.REMOVEFROMUSERINBOX, user, pwd, null, mid, null);
        long v = versionControl.addVersion(op);

        for(String secondaryURI : versionControl.getSecondariesURI()) {
            try {
                ClientFactory.getMessagesClient(new URI(secondaryURI), 1, 1000).removeFromUserInbox(version, user,
                        mid, pwd, MessageReplicated.secret);
            } catch (URISyntaxException ignored) {}
        }

        throw new WebApplicationException(Response.status(204).
                header(MessageService.HEADER_VERSION, v).build());
    }

    @Override
    public void removeFromUserInboxReplica(Long version, String user, long mid, String pwd, String secret) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        if(version != null && versionControl.getVersion() < version)
            localUserClient.update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        removeFromUserInboxOperation(user, mid, pwd);

        Operation op = new Operation(Operation.REMOVEFROMUSERINBOX, user, pwd, null, mid, null);
        versionControl.addVersion(op);
    }

    //Method called by other servers to forward sent messages
    @Override
    public void forwardSendMessage(Long version, String user, String secret, Message message) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        String primaryURI = versionControl.getPrimaryURI();
        if (!primaryURI.equals(this.selfURI)) {
            String redirectPath = primaryURI + MessageService.PATH + "/internal/msg/" + user
                    + "?secret=" + MessageReplicated.secret;
            throw new WebApplicationException(Response.temporaryRedirect(
                    URI.create(redirectPath)).build());
        }

        messageToInbox(user, message);

        Operation op = new Operation(Operation.FORWARDSENDMESSAGE, user, null, null, null, message);
        versionControl.addVersion(op);

        for (String secondaryURI : versionControl.getSecondariesURI()) {
            try {
                ClientFactory.getMessagesClient(new URI(secondaryURI), 1, 1000).
                        forwardSendMessage(version, user, MessageReplicated.secret, message);
            } catch (URISyntaxException ignored) {}
        }
    }

    @Override
    public void forwardSendMessageReplica(Long version, String user, String secret, Message message){
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        if(version != null && versionControl.getVersion() < version)
            localUserClient.update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        messageToInbox(user, message);

        Operation op = new Operation(Operation.FORWARDSENDMESSAGE, user, null, null, null, message);
        versionControl.addVersion(op);
    }

    //Method called by other servers to forward message deletion
    @Override
    public void forwardDeleteSentMessage(Long version, String user, long mid, String secret) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        String primaryURI = versionControl.getPrimaryURI();
        if (!primaryURI.equals(this.selfURI)) {
            String redirectPath = primaryURI + MessageService.PATH + "/internal/msg/"
                    + user + "/" + mid + "?secret=" + secret;
            throw new WebApplicationException(Response.temporaryRedirect(
                    URI.create(redirectPath)).build());
        }

        deleteMessageInbox(user, mid);

        Operation op = new Operation(Operation.FORWARDDELETESENTMESSAGE, user, null, null, mid, null);
        versionControl.addVersion(op);

        for (String secondaryURI : versionControl.getSecondariesURI()) {
            try {
                ClientFactory.getMessagesClient(new URI(secondaryURI), 1, 1000).forwardDeleteSendMessage(version, user, mid, secret);
            } catch (URISyntaxException ignored) {
            }
        }
    }

    @Override
    public void forwardDeleteSentMessageReplica(Long version, String user, long mid, String secret) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        if(version != null && versionControl.getVersion() < version)
            localUserClient.update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        deleteMessageInbox(user, mid);

        Operation op = new Operation(Operation.FORWARDDELETESENTMESSAGE, user, null, null, mid, null);
        versionControl.addVersion(op);
    }

    //Called by "postMessage" or by the dispatcher to add an error message to the user inbox
    public void createErrorMessage(String formattedSender, String senderName, long msgId, String destination) {
        try {
            //To avoid duplicate code, calls "forwardSendMessage"
            System.out.println("Create error message!");
            messageToInbox(senderName, new Message(nextMessageId(), formattedSender,
                    senderName + "@" + domain,
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
        String primaryURI = versionControl.getPrimaryURI();
        if(!primaryURI.equals(this.selfURI))
            throw new WebApplicationException(Response.temporaryRedirect(
                    URI.create(primaryURI + MessageService.PATH + "?pwd=" + pwd)).build());

        //Parses sender into a class
        Address sender = Address.fromString(msg.getSender(), domain);
        //Checks for errors
        if (sender == null || !sender.getDomain().equals(domain))
            throw new WebApplicationException(Response.Status.CONFLICT);
        if (msg.getDestination() == null || msg.getDestination().size() == 0)
            throw new WebApplicationException(Response.Status.CONFLICT);

        User u = getUserOperation(sender.getName(), pwd);

        //Formats the sender of the message to the correct format
        msg.setSender(u.getDisplayName() + " <" + u.getName() + "@" + u.getDomain() + ">");
        //Sets the id to a new unique id
        msg.setId(nextMessageId());

        System.out.println("SendMessage -> " + msg.getId());

        postMessageOperation(u, msg, true);

        Operation op = new Operation(Operation.POSTMESSAGE, null, null, u, null, msg);
        long v = versionControl.addVersion(op);

        //replicate postMessage request
        for(String secondaryURI : versionControl.getSecondariesURI()){
            try {
                ClientFactory.getMessagesClient(new URI(secondaryURI), 5, 1000).
                        postMessage(version, sender.getName(), pwd, MessageReplicated.secret, msg);
            } catch (URISyntaxException ignored) {}
        }

        throw new WebApplicationException(Response.status(200).
                header(MessageService.HEADER_VERSION, v)
                .entity(msg.getId()).build());
    }

    @Override
    public long postMessageReplica(Long version, String user, String pwd, String secret, Message msg) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        if(version != null && versionControl.getVersion() < version)
            localUserClient.update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        User u = getUserOperation(user, pwd);
        postMessageOperation(u, msg, false);

        Operation op = new Operation(Operation.POSTMESSAGE, null, null, u, null, msg);
        versionControl.addVersion(op);

        return msg.getId();
    }

    @Override
    public void deleteMessage(Long version, String user, long mid, String pwd) throws WebApplicationException{
        String primaryURI = versionControl.getPrimaryURI();
        if(!primaryURI.equals(this.selfURI)) {
            throw new WebApplicationException(Response.temporaryRedirect(
                    URI.create(primaryURI + MessageService.PATH + "/msg/" + user + "/" + mid + "?pwd=" + pwd)).build());
        }

        if(!deleteMessageOperation(user, mid, pwd, true))
            throw new WebApplicationException(Response.status(204).
                    header(MessageService.HEADER_VERSION, versionControl.getVersion()).build());

        Operation op = new Operation(Operation.DELETEMESSAGE, user, pwd, null, mid, null);
        long v = versionControl.addVersion(op);

        for(String secondaryURI : versionControl.getSecondariesURI()) {
            try {
                ClientFactory.getMessagesClient(new URI(secondaryURI), 1, 1000).
                        deleteMessage(version, user, mid, pwd, MessageReplicated.secret);
            } catch(URISyntaxException ignored) {}
        }

        throw new WebApplicationException(Response.status(204).
                header(MessageService.HEADER_VERSION, v).build());
    }

    @Override
    public void deleteMessageReplica(Long version, String user, long mid, String pwd, String secret) {
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        if(version != null && versionControl.getVersion() < version)
            localUserClient.update(versionControl.getVersion() + 1, versionControl.getPrimaryURI(), MessageReplicated.secret);

        if(!deleteMessageOperation(user, mid, pwd, false))
            return;

        Operation op = new Operation(Operation.DELETEMESSAGE, user, pwd, null, mid, null);
        versionControl.addVersion(op);
    }

    @Override
    public void deleteUserInfo(String user, String secret) {
        //Used by UserResource to delete the inbox and outbox when the user is deleted
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        synchronized (this) {
            Map<Long, Message> remove = inBoxes.remove(user);
            Map<Long, Message> remove1 = outBoxes.remove(user);
            if (remove == null || remove1 == null) {
                System.err.println("deleteUserInfo was called, but inbox or outbox did not exist...");
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }
        }
    }

    @Override
    public void setupUserInfo(String user, String secret) {
        //Used by UserResource to create the inbox and outbox when the user is created
        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        synchronized (this) {
            if (inBoxes.containsKey(user) || outBoxes.containsKey(user)) {
                System.err.println("setupUserInfo was called, but inbox or outbox already exists...");
                throw new WebApplicationException(Response.Status.CONFLICT);
            } else
                inBoxes.put(user, new HashMap<>());
            outBoxes.put(user, new HashMap<>());
        }
    }

    @Override
    public void updateMessages(String secret, Operation op) {

        if (secret == null || !secret.equals(MessageReplicated.secret))
            throw new WebApplicationException(Response.Status.FORBIDDEN);

        switch (op.getType()) {
            case Operation.POSTMESSAGE:
                postMessageOperation(op.getUser(), op.getMsg(), false);
                break;
            case Operation.DELETEMESSAGE:
                deleteMessageOperation(op.getName(), op.getMid(), op.getPwd(), false);
                break;
            case Operation.REMOVEFROMUSERINBOX:
                removeFromUserInboxOperation(op.getName(), op.getMid(), op.getPwd());
                break;
            case Operation.FORWARDSENDMESSAGE:
                messageToInbox(op.getName(), op.getMsg());
                break;
            case Operation.FORWARDDELETESENTMESSAGE:
                deleteMessageInbox(op.getName(), op.getMid());
                break;
        }
    }

    private User getUserOperation(String user, String pwd) throws WebApplicationException {
        //Checks if user is valid
        EmailResponse<User> validate = localUserClient.getUserInException(user, pwd);

        if (validate.getStatusCode() != Response.Status.OK.getStatusCode())
            throw new WebApplicationException(validate.getStatusCode());

        return validate.getEntity();
    }

    private void removeFromUserInboxOperation(String user, long mid, String pwd) throws WebApplicationException{
        System.out.println("RmvInbox: " + user + " " + mid + " " + pwd);

        getUserOperation(user,pwd);
        synchronized (this) {
            Map<Long, Message> inbox = inBoxes.get(user);
            if (inbox == null)
                throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            if (inbox.remove(mid) == null)
                throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
    }

    private void messageToInbox(String user, Message message){
        //Synchronized since we are handling data structures (inboxes)
        //Adds to the user inbox, or throws a 404 if the inbox (and consequently the user) does not exist
        synchronized (this) {
            Map<Long, Message> inbox = inBoxes.get(user);
            if (inbox == null)
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            inbox.put(message.getId(), message);
            System.out.println("Added " + message.getId() + " to inbox of " + user);
        }
    }

    private void deleteMessageInbox(String user, long mid){
        //Similar logic as "forwardSendMessage"
        synchronized (this) {
            Map<Long, Message> inbox = inBoxes.get(user);
            if (inbox == null || inbox.remove(mid) == null) {
                System.out.println("Not found in forwardDeleteSentMessage, probably deleted by user");
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }
        }
    }

    private void postMessageOperation(User u, Message msg, boolean isPrimary) {

        //Adds to the outbox (useful for the deleteMessage operation)
        //synchronized since we are handling a data structure)
        synchronized (this) {
            outBoxes.get(u.getName()).put(msg.getId(), msg);
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
                    messageToInbox(destination.getName(), msg);
                } catch (WebApplicationException e) {
                    System.out.println("Failed to deliver locally " + e.getMessage());
                    createErrorMessage(msg.getSender(), u.getName(), msg.getId(), d);
                }
            } else if(isPrimary){
                //if it is in another domain, creates a dispatcher if there is not one already, and submits
                //a new deliverJob to it.
                //The method "computeIfAbsent" creates a new dispatcher, of fetches the existing one if it exists already
                dispatchers.computeIfAbsent(destination.getDomain(), k ->
                        new Dispatcher(k, this)).addDeliverJob(msg, destination.getName(), u.getName());
            }
        }
    }

    private boolean deleteMessageOperation(String user, long mid, String pwd, boolean isPrimary) throws WebApplicationException {
        //Similar logic to "postMessage"
        System.out.println("DeleteMessage: " + user + " " + mid + " " + pwd);

        //Checks if user is valid
        getUserOperation(user, pwd);

        Message msg;
        //Checks if the message is in the user outbox, which means it was sent by that user
        //and has not yet been deleted
        synchronized (this) {
            msg = outBoxes.get(user).remove(mid);
            if (msg == null) {
                System.out.println("Not found");
                return false;
            }
        }

        //For each destination in the original message, either deletes from the inbox if it is a local user
        //or creates a deleteJob in the corresponding dispatcher
        for (String d : msg.getDestination()) {
            Address addr = Address.fromString(d);
            if (addr != null) {
                if (addr.getDomain().equals(domain)) {
                    try {
                        deleteMessageInbox(addr.getName(), msg.getId());
                    } catch (WebApplicationException ignored) {
                    }
                } else if(isPrimary){
                    dispatchers.computeIfAbsent(addr.getDomain(), k -> new Dispatcher(k, this))
                            .addDeleteJob(msg.getId(), addr.getName());
                }
            }
        }
        return true;
    }

}

