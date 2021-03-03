package sd1920.trab1.clients;

import sd1920.trab1.api.User;
import sd1920.trab1.impl.Operation;

import java.util.List;

/*
 * Interface used by both User clients
 */
public interface UsersEmailClient {

    EmailResponse<User> getUser(String name, String pwd);
    EmailResponse<User> getUserInException(String name, String pwd);
    EmailResponse<Void> update(Long version, String targetURI, String secret);

    void postUser(Long version, String secret, User user);

    void updateUser(Long version, String name, String pwd, String secret, User user);

    void deleteUser(Long version, String user, String pwd, String secret);

    List<Operation> getOperations(Long version, String secret);
}
