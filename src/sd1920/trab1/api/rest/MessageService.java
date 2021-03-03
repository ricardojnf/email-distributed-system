package sd1920.trab1.api.rest;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import sd1920.trab1.api.Message;
import sd1920.trab1.impl.Operation;

@Path(MessageService.PATH)
public interface MessageService {
    String PATH = "/messages";
    String HEADER_VERSION = "Msgserver-version";

    /**
     * Posts a new message to the server, associating it to the inbox of every individual destination.
     * An outgoing message should be modified before delivering it, by assigning an ID, and by changing the
     * sender to be in the format "display name <name@domain>", with display name the display name
     * associated with a user.
     * NOTE: there might be some destinations that are not from the local domain (see grading for
     * how addressing this feature is valued).
     *
     * @param msg the message object to be posted to the server
     * @param pwd password of the user sending the message
     * @return 200 the unique numerical identifier for the posted message;
     * 403 if the sender does not exist or if the pwd is not correct (NOTE: sender can be in the form
     * "name" or "name@domain");
     * 409 otherwise
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    long postMessage(@HeaderParam(MessageService.HEADER_VERSION) Long version, @QueryParam("pwd") String pwd, Message msg) throws InterruptedException;

    /**
     * Posts a new message to the replicated server, associating it to the inbox of every individual destination.
     * An outgoing message should be modified before delivering it, by assigning an ID, and by changing the
     * sender to be in the format "display name <name@domain>", with display name the display name
     * associated with a user.
     * NOTE: there might be some destinations that are not from the local domain (see grading for
     * how addressing this feature is valued).
     *
     * @param version of the request
     * @param user sender of the message
     * @param pwd password of the user sending the message
     * @param secret to communicate across the servers
     * @param msg the message object to be posted to the server
     * @return 200 the unique numerical identifier for the posted message;
     * 403 if the sender does not exist or if the pwd is not correct (NOTE: sender can be in the form
     * "name" or "name@domain");
     * 409 otherwise
     */
    @POST
    @Path("/replica/{user}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    long postMessageReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version,
                            @PathParam("user") String user, @QueryParam("pwd") String pwd,
                            @QueryParam("secret") String secret, Message msg);

    /**
     * Obtains the message identified by mid of user user
     *
     * @param version of the request
     * @param user user name for the operation
     * @param mid  the identifier of the message
     * @param pwd  password of the user
     * @return 200 the message if it exists;
     * 403 if the user does not exist or if the pwd is not correct;
     * 404 if the message does not exists
     */
    @GET
    @Path("/mbox/{user}/{mid}")
    @Produces(MediaType.APPLICATION_JSON)
    Message getMessage(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("user") String user, @PathParam("mid") long mid,
                       @QueryParam("pwd") String pwd) throws IOException;

    /**
     * Returns a list of all ids of messages stored in the server for a given user
     *
     * @param version of the request
     * @param user the username of the user whose message ids should be returned
     * @param pwd  password of the user
     * @return 200 a list of ids potentially empty;
     * 403 if the user does not exist or if the pwd is not correct.
     */
    @GET
    @Path("/mbox/{user}")
    @Produces(MediaType.APPLICATION_JSON)
    List<Long> getMessages(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("user") String user, @QueryParam("pwd") String pwd);

    /**
     * Removes a message identified by mid from the inbox of user identified by user.
     *
     * @param version of the request
     * @param user the username of the inbox that is manipulated by this method
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the user
     * @return 204 if ok
     * 403 if the user does not exist or if the pwd is not correct;
     * 404 is generated if the message does not exist in the server.
     */
    @DELETE
    @Path("/mbox/{user}/{mid}")
    void removeFromUserInbox(@HeaderParam(MessageService.HEADER_VERSION) Long version,
                             @PathParam("user") String user, @PathParam("mid") long mid,
                             @QueryParam("pwd") String pwd) throws IOException;

    /**
     * Removes a message identified by mid from the inbox of user identified by user
     * from the replicated server.
     *
     * @param version of the request
     * @param user the username of the inbox that is manipulated by this method
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the user
     * @param secret to communicate across the servers
     * @return 204 if ok
     * 403 if the user does not exist or if the pwd is not correct;
     * 404 is generated if the message does not exist in the server.
     */
    @DELETE
    @Path("/replica/mbox/{user}/{mid}")
    void removeFromUserInboxReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version,
                                    @PathParam("user") String user, @PathParam("mid") long mid,
                                    @QueryParam("pwd") String pwd,
                                    @QueryParam("secret") String secret) throws IOException;


    /**
     * Removes the message identified by mid from the inboxes of any server that holds the message.
     * The deletion can be executed asynchronously and does not generate any error message if the
     * message does not exist.
     *
     * @param version of the request
     * @param user the username of the sender of the message to be deleted
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the user that sent the message
     * @return 204 if ok
     * 403 is generated if the user does not exist or if the pwd is not correct
     */
    @DELETE
    @Path("/msg/{user}/{mid}")
    void deleteMessage(@HeaderParam(MessageService.HEADER_VERSION) Long version,
                       @PathParam("user") String user, @PathParam("mid") long mid,
                       @QueryParam("pwd") String pwd) throws IOException;

    /**
     * Removes the message identified by mid from the inboxes of any server that holds the message
     * from the replicated server
     * The deletion can be executed asynchronously and does not generate any error message if the
     * message does not exist.
     *
     * @param version of the request
     * @param user the username of the sender of the message to be deleted
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the user that sent the message
     * @param secret to communicate across the servers
     * @return 204 if ok
     * 403 is generated if the user does not exist or if the pwd is not correct
     */
    @DELETE
    @Path("/replica/msg/{user}/{mid}")
    void deleteMessageReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version,
                       @PathParam("user") String user, @PathParam("mid") long mid,
                       @QueryParam("pwd") String pwd, @QueryParam("secret") String secret) throws IOException;

    /**
     * NOT REQUIRED
     *
     * Removes the inbox and outbox of a given user.
     * This is used by {@link sd1920.trab1.impl.UserResource UserResource} on user deletion.
     *
     * @param user the username of the user which inbox and outbox will be deleted
     * @param secret internal secret
     */
    @DELETE
    @Path("internal/user/{name}")
    void deleteUserInfo(@PathParam("name") String user, @QueryParam("secret") String secret);

    /**
     * NOT REQUIRED
     *
     * Creates an inbox and outbox for a given user.
     * This is used by {@link sd1920.trab1.impl.UserResource UserResource} on user creation.
     *
     * @param user the username of the user which inbox and outbox will be deleted
     * @param secret internal secret
     */
    @POST
    @Path("internal/user/{name}")
    void setupUserInfo(@PathParam("name") String user, @QueryParam("secret") String secret);

    /**
     * NOT REQUIRED
     *
     * Receives requests to add message to a user's inbox from other servers.
     *
     * @param version of the request
     * @param user the user name
     * @param secret internal secret
     * @param message the message object to be inserted in the users inbox
     */
    @POST
    @Path("internal/msg/{user}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void forwardSendMessage(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("user") String user,
                            @QueryParam("secret") String secret, Message message);

    /**
     * NOT REQUIRED
     *
     * Receives requests to add message to a user's inbox from other servers to the replicated server
     *
     * @param version of the request
     * @param user the user name
     * @param secret internal secret
     * @param message the message object to be inserted in the users inbox
     */
    @POST
    @Path("replica/internal/msg/{user}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    void forwardSendMessageReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("user") String user,
                                   @QueryParam("secret") String secret, Message message);


    /**
     * NOT REQUIRED
     *
     * Receives requests to delete a message from a user's inbox from other servers.
     *
     * @param version of the request
     * @param user the user name
     * @param mid the identifier of the message to be deleted
     * @param secret internal secret
     */
    @DELETE
    @Path("internal/msg/{user}/{mid}")
    @Consumes(MediaType.APPLICATION_JSON)
    void forwardDeleteSentMessage(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("user") String user,
                                  @PathParam("mid") long mid, @QueryParam("secret") String secret) throws IOException;

    /**
     * NOT REQUIRED
     *
     * Receives requests to delete a message from a user's inbox from other servers
     * to the replicated server
     *
     * @param version of the request
     * @param user the user name
     * @param mid the identifier of the message to be deleted
     * @param secret internal secret
     */
    @DELETE
    @Path("replica/internal/msg/{user}/{mid}")
    @Consumes(MediaType.APPLICATION_JSON)
    void forwardDeleteSentMessageReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version,
                                         @PathParam("user") String user, @PathParam("mid") long mid,
                                         @QueryParam("secret") String secret) throws IOException;

    /**
     * Execute a message operation to get the server status updated
     * @param secret internal secret
     * @param op operation to be made
     */
    @POST
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    void updateMessages(@QueryParam("secret") String secret, Operation op);
}
