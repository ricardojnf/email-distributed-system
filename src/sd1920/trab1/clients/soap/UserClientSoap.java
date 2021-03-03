package sd1920.trab1.clients.soap;

import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.UserServiceSoap;
import sd1920.trab1.clients.EmailResponse;
import sd1920.trab1.clients.UsersEmailClient;
import sd1920.trab1.impl.Operation;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static com.sun.xml.ws.developer.JAXWSProperties.CONNECT_TIMEOUT;
import static com.sun.xml.ws.developer.JAXWSProperties.REQUEST_TIMEOUT;

public class UserClientSoap extends EmailClientSoap implements UsersEmailClient {

    private static final String USERS_WSDL = "/users/?wsdl";

    UserServiceSoap userProxy;

    public UserClientSoap(URI serverUrl, int maxRetries, int retryPeriod) {
        super(serverUrl, maxRetries, retryPeriod);
    }

    //Method to check if a user exists and the password is valid
    public EmailResponse<User> getUser(String name, String pwd) {
        //Calls the tryMultiple method of EmailClientSoap to repeat the operation until it is successful,
        //Also translates the response to an EmailResponse in order to unify SOAP and REST responses
        return tryMultiple(() -> EmailResponse.ok(getClient().getUser(name, pwd)));
    }

    @Override
    public EmailResponse<User> getUserInException(String name, String pwd) {
        return null;
    }

    @Override
    public EmailResponse<Void> update(Long version, String targetURI, String secret) {
        return null;
    }

    @Override
    public void postUser(Long version, String secret, User user) {

    }

    @Override
    public void updateUser(Long version, String name, String pwd, String secret, User user) {

    }

    @Override
    public void deleteUser(Long version, String user, String pwd, String secret) {

    }

    @Override
    public List<Operation> getOperations(Long version, String secret) {
        return null;
    }

    //this method creates a new UserServiceSoap proxy, or returns the existing one if it has already been created
    //this method is called from within "tryMultiple" since it can fail
    private UserServiceSoap getClient() throws MalformedURLException, WebServiceException {
        synchronized (this) {
            if (userProxy == null) {
                QName QNAME = new QName(UserServiceSoap.NAMESPACE, UserServiceSoap.NAME);
                Service service = Service.create(new URL(serverUri + USERS_WSDL), QNAME);
                userProxy = service.getPort(UserServiceSoap.class);
                //Set timeouts
                ((BindingProvider) userProxy).getRequestContext().put(CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
                ((BindingProvider) userProxy).getRequestContext().put(REQUEST_TIMEOUT, REPLY_TIMEOUT);
            }
            return userProxy;
        }
    }
}
