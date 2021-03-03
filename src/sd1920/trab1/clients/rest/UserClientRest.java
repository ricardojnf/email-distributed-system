package sd1920.trab1.clients.rest;

import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.rest.UserService;
import sd1920.trab1.clients.EmailResponse;
import sd1920.trab1.clients.UsersEmailClient;
import sd1920.trab1.impl.Operation;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;

public class UserClientRest extends EmailClientRest implements UsersEmailClient {


    public UserClientRest(URI serverUrl, int maxRetries, int retryPeriod) {
        super(serverUrl, maxRetries, retryPeriod, UserService.PATH);
    }

    //Method to check if a user exists and the password is valid
    public EmailResponse<User> getUser(String name, String pwd) {
        //Calls the tryMultiple method of EmailClientRest to repeat the operation until it is successful,
        //Also translates the response to an EmailResponse in order to unify SOAP and REST responses
        return tryMultiple(() -> {
            Response response = target.path(name).queryParam("pwd", pwd).request()
                    .accept(MediaType.APPLICATION_JSON).get();
            return EmailResponse.create(response.getStatus(), response.readEntity(User.class));
        });
    }

    @Override
    public EmailResponse<User> getUserInException(String name, String pwd) {
        return tryMultiple(() -> {
            Response response = null;
            try {
                response = target.path(name).queryParam("pwd", pwd).request()
                        .header(MessageService.HEADER_VERSION, null)
                        .accept(MediaType.APPLICATION_JSON).get();
            }
            catch(WebApplicationException e){
                if(e.getResponse().getStatus() != 200)
                    throw new WebApplicationException();
                response = e.getResponse();
            }
            return EmailResponse.create(response.getStatus(), response.readEntity(User.class));
        });
    }

    @Override
    public EmailResponse<Void> update(Long version, String targetURI, String secret) {
        return tryMultiple(() -> {
            Response response = target.path("update").queryParam("targetURI", targetURI)
                    .queryParam("secret", secret).request()
                    .header(MessageService.HEADER_VERSION, version)
                    .post(Entity.entity(null, MediaType.APPLICATION_JSON));

            return EmailResponse.create(response.getStatus());
        });
    }

    public void postUser(Long version, String secret, User user){
        try {
            target.path("replica").queryParam("secret", secret).request()
                    .header(MessageService.HEADER_VERSION, version)
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(user, MediaType.APPLICATION_JSON));
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

    public void updateUser(Long version, String name, String pwd, String secret, User user){
        try {
            target.path("replica").path(name).queryParam("pwd", pwd).queryParam("secret", secret)
                    .request()
                    .header(MessageService.HEADER_VERSION, version)
                    .accept(MediaType.APPLICATION_JSON)
                    .put(Entity.entity(user, MediaType.APPLICATION_JSON));
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

    public void deleteUser(Long version, String user, String pwd, String secret){
        try {
            target.path("replica").path(user).queryParam("pwd", pwd).queryParam("secret", secret).
                    request()
                    .header(MessageService.HEADER_VERSION, version).delete();
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

    public List<Operation> getOperations(Long version, String secret) {
        try {
            Response response = target.path("operations").queryParam("secret", secret)
                    .request().header(MessageService.HEADER_VERSION, version)
                    .accept(MediaType.APPLICATION_JSON).get();

            return response.readEntity(new GenericType<List<Operation>>(){});
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

}
