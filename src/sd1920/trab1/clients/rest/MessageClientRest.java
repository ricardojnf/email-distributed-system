package sd1920.trab1.clients.rest;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.clients.EmailResponse;
import sd1920.trab1.clients.MessagesEmailClient;
import sd1920.trab1.impl.Operation;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

public class MessageClientRest extends EmailClientRest implements MessagesEmailClient {

    public MessageClientRest(URI serverUrl, int maxRetries, int retryPeriod) {
        super(serverUrl, maxRetries, retryPeriod, MessageService.PATH);
    }

    //These method work like in UserClientRest, using "tryMultiple" to retry the operation until it is successful,
    // and then translating the result to an EmailResponse
    public EmailResponse<Void> deleteUserInfo(String user, String secret) {
        return tryMultiple(() -> {
            Response delete = target.path("internal").path("user").path(user)
                    .queryParam("secret", secret).request().delete();
            return EmailResponse.create(delete.getStatus());
        });
    }

    public EmailResponse<Void> setupUserInfo(String user, String secret) {
        return tryMultiple(() -> {
            Response post = target.path("internal").path("user").path(user)
                    .queryParam("secret", secret).request().post(Entity.json(""));
            return EmailResponse.create(post.getStatus());

        });
    }

    public EmailResponse<Void> forwardSendMessage(String user, Message msg, String secret) {
        return tryMultiple(() -> {
            Response post = target.path("internal").path("msg").path(user)
                    .queryParam("secret", secret).request().post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            return EmailResponse.create(post.getStatus());

        });

    }

    public EmailResponse<Void> forwardDeleteSentMessage(String user, long mid, String secret) {
        return tryMultiple(() -> {
            Response delete = target.path("internal").path("msg").path(user).path(String.valueOf(mid))
                    .queryParam("secret", secret).request().delete();
            return EmailResponse.create(delete.getStatus());

        });
    }

    public EmailResponse<Void> updateMessage(Operation op, String secret){
        return tryMultiple(() -> {
            Response response = target.path("update").queryParam("secret", secret)
                    .request().post(Entity.entity(op, MediaType.APPLICATION_JSON));
            return EmailResponse.create(response.getStatus());
        });
    }

    public void postMessage(Long version, String user, String pwd, String secret, Message msg){
        try {
            target.path("replica").path(user).queryParam("pwd", pwd).queryParam("secret", secret)
                    .request()
                    .header(MessageService.HEADER_VERSION, version)
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

    public void removeFromUserInbox(Long version, String user, long mid, String pwd, String secret){
        try {
            target.path("replica/mbox").path(user).path(String.valueOf(mid)).queryParam("pwd", pwd)
                    .queryParam("secret", secret).request()
                    .header(MessageService.HEADER_VERSION, version)
                    .accept(MediaType.APPLICATION_JSON).delete();
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

    public void deleteMessage(Long version, String user, long mid, String pwd, String secret){
        try {
            target.path("replica/msg").path(user).path(String.valueOf(mid))
                    .queryParam("pwd", pwd).queryParam("secret", secret)
                    .request()
                    .header(MessageService.HEADER_VERSION, version)
                    .accept(MediaType.APPLICATION_JSON).delete();
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

    public void forwardSendMessage(Long version, String user, String secret, Message msg){
        try {
            target.path("replica/internal/msg").path(user).queryParam("secret", secret).request()
                    .header(MessageService.HEADER_VERSION, version).accept(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

    public void forwardDeleteSendMessage(Long version, String user, long mid, String secret){
        try {
            target.path("replica/internal/msg").path(user).path(String.valueOf(mid))
                    .queryParam("secret", secret).request()
                    .header(MessageService.HEADER_VERSION, version).delete();
        }
        catch(ProcessingException e){
            System.out.println("Timeout on rest client for " + target.toString() + " -> " + e.getMessage());
        }
    }

}
