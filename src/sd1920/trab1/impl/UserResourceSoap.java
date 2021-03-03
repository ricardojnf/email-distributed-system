package sd1920.trab1.impl;

import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.MessagesException;
import sd1920.trab1.api.soap.UserServiceSoap;

import javax.inject.Singleton;
import javax.jws.WebService;
import javax.ws.rs.WebApplicationException;
import java.net.URI;

/**
 * This class simply wraps the REST implementation, translating REST errors into SOAP errors.
 */
@WebService(serviceName = UserServiceSoap.NAME,
        targetNamespace = UserServiceSoap.NAMESPACE,
        endpointInterface = UserServiceSoap.INTERFACE)
@Singleton
public class UserResourceSoap implements UserServiceSoap {

    UserResource resource;

    public UserResourceSoap(String domain, URI selfURI) {
        System.out.println("Constructed UserResourceSoap in domain " + domain);
        resource = new UserResource(domain, selfURI);
    }

    @Override
    public String postUser(User user) throws MessagesException {
        try {
            return resource.postUser(null, user);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public User getUser(String name, String pwd) throws MessagesException {
        try {
            return resource.getUser(null, name, pwd);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public User updateUser(String name, String pwd, User user) throws MessagesException {
        try {
            return resource.updateUser(null, name, pwd, user);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public User deleteUser(String user, String pwd) throws MessagesException {
        try {
            return resource.deleteUser(null, user, pwd);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }
}
