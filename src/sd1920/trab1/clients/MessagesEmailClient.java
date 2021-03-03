package sd1920.trab1.clients;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.impl.Operation;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

/*
 * Interface used by both Message clients
 */
public interface MessagesEmailClient {

    EmailResponse<Void> deleteUserInfo(String user, String secret);

    EmailResponse<Void> setupUserInfo(String user, String secret);

    EmailResponse<Void> forwardSendMessage(String user, Message msg, String secret);

    EmailResponse<Void> forwardDeleteSentMessage(String user, long mid, String secret);

    EmailResponse<Void> updateMessage(Operation op, String secret);

    void postMessage(Long version, String user, String pwd, String secret, Message msg);

    void removeFromUserInbox(Long version, String user, long mid, String pwd, String secret);

    void deleteMessage(Long version, String user, long mid, String pwd, String secret);

    void forwardSendMessage(Long version, String user, String secret, Message msg);

    void forwardDeleteSendMessage(Long version, String user, long mid, String secret);

}
