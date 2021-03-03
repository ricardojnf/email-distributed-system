package sd1920.trab1.impl;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.MessagesException;

import javax.inject.Singleton;
import javax.jws.WebService;
import javax.ws.rs.WebApplicationException;
import java.net.URI;
import java.util.*;

/**
 * This class simply wraps the REST implementation, translating REST errors into SOAP errors.
 */
@Singleton
@WebService(serviceName = MessageServiceSoap.NAME,
        targetNamespace = MessageServiceSoap.NAMESPACE,
        endpointInterface = MessageServiceSoap.INTERFACE)

public class MessageResourceSoap implements MessageServiceSoap {

    MessageResource resource;

    public MessageResourceSoap(String domain, URI selfURI, int midPrefix, String secret) {
        System.out.println("Constructed MessageResourceSoap in domain " + domain);
        resource = new MessageResource(domain, selfURI, midPrefix, secret);
    }

    @Override
    public long postMessage(String pwd, Message msg) throws MessagesException {
        try {
            return resource.postMessage(null, pwd, msg);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public Message getMessage(String user, String pwd, long mid) throws MessagesException {
        try {
            return resource.getMessage(null, user, mid, pwd);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public List<Long> getMessages(String user, String pwd) throws MessagesException {
        try {
            return resource.getMessages(null, user, pwd);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public void removeFromUserInbox(String user, String pwd, long mid) throws MessagesException {
        try {
            resource.removeFromUserInbox(null, user, mid, pwd);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public void deleteMessage(String user, String pwd, long mid) throws MessagesException {
        try {
            resource.deleteMessage(null, user, mid, pwd);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public void deleteUserInfo(String user, String secret) throws MessagesException {
        try {
            resource.deleteUserInfo(user, secret);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public void setupUserInfo(String user, String secret) throws MessagesException {
        try {
            resource.setupUserInfo(user, secret);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public void forwardSendMessage(String user, String secret, Message message) throws MessagesException {
        try {
            resource.forwardSendMessage(null, user, secret, message);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }

    @Override
    public void forwardDeleteSentMessage(String user, long mid, String secret) throws MessagesException {
        try {
            resource.forwardDeleteSentMessage(null, user, mid, secret);
        } catch (WebApplicationException e) {
            throw new MessagesException(e.getResponse().getStatus());
        }
    }
}
