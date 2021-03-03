package sd1920.trab1.api.rest;

import javax.print.attribute.standard.Media;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import sd1920.trab1.api.User;
import sd1920.trab1.impl.Operation;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

@Path(UserService.PATH)
public interface UserService {

    String PATH = "/users";

    /**
     * Creates a new user in the local domain.
     *
     * @param user User to be created
     * @return 200: the address of the user (name@domain).
     * 403 if the domain in the user does not match the domain of the server
     * 409 if either name, pwd, or domain and null
     * 409 for other errors
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    String postUser(@HeaderParam(MessageService.HEADER_VERSION) Long version, User user);

    /**
     * Creates a new user in the replicated server
     *
     * @param version of the request
     * @param secret to communicate across the servers
     * @param user User to be created
     * @return 200: the address of the user (name@domain).
     * 403 if the domain in the user does not match the domain of the server
     * 409 if either name, pwd, or domain and null
     * 409 for other errors
     */
    @POST
    @Path("/replica")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    String postUserReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version,
                           @QueryParam("secret") String secret, User user);

    /**
     * Obtains the information on the user identified by name
     * Also used to authenticate a given user an its password from MessageResource
     *
     * @param name the name of the user
     * @param pwd  password of the user
     * @return the user object, if the name exists and pwd matches the existing password
     * 403 otherwise
     */
    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    User getUser(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("name") String name, @QueryParam("pwd") String pwd) throws IOException;

    /**
     * Modifies the information of a user. Values of null in any field of the user will be
     * considered as if the the fields is not to be modified (the name cannot be modified).
     *
     * @param name the name of the user
     * @param pwd  password of the user
     * @param user Updated information
     * @return the updated user object, if the name exists and pwd matches the existing password
     * 403 otherwise
     */
    @PUT
    @Path("/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    User updateUser(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("name") String name, @QueryParam("pwd") String pwd, User user) throws IOException;

    /**
     * Modifies the information of a user in the replicated server. Values of null in any field
     * of the user will be considered as if the the fields is not to be modified
     * (the name cannot be modified).
     *
     * @param version of the request
     * @param name the name of the user
     * @param pwd  password of the user
     * @param secret to communicate across the servers
     * @param user Updated information
     * @return the updated user object, if the name exists and pwd matches the existing password
     * 403 otherwise
     */
    @PUT
    @Path("/replica/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    User updateUserReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("name") String name,
                           @QueryParam("pwd") String pwd,
                           @QueryParam("secret") String secret, User user) throws IOException;


    /**
     * Deletes the user identified by name
     *
     * @param name the name of the user
     * @param pwd  password of the user
     * @return the deleted user object, if the name exists and pwd matches the existing password
     * 403 otherwise
     */
    @DELETE
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    User deleteUser(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("name") String name, @QueryParam("pwd") String pwd) throws IOException;

    /**
     * Deletes the user identified by name in the replicated server
     *
     * @param version of the request
     * @param name the name of the user
     * @param pwd  password of the user
     * @param secret to communicate across the servers
     * @return the deleted user object, if the name exists and pwd matches the existing password
     * 403 otherwise
     */
    @DELETE
    @Path("/replica/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    User deleteUserReplica(@HeaderParam(MessageService.HEADER_VERSION) Long version, @PathParam("name") String name,
                           @QueryParam("pwd") String pwd,
                           @QueryParam("secret") String secret) throws IOException;


    /**
     * Returns the list of operations from the server since the given version
     * @param version of the request
     * @param secret to communicate across the servers
     * @return list of operations since the given version
     */
    @GET
    @Path("/operations")
    @Produces(MediaType.APPLICATION_JSON)
    List<Operation> getOperations(@HeaderParam(MessageService.HEADER_VERSION) Long version, @QueryParam("secret") String secret);

    /**
     * Execute operations to completely update the server state
     * @param version of the request
     * @param targetURI to request the list of operations
     * @param secret to communicate across the servers
     */
    @POST
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    void update(@HeaderParam(MessageService.HEADER_VERSION) Long version, @QueryParam("targetURI") String targetURI, @QueryParam("secret") String secret);
}