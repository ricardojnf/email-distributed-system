package sd1920.trab1;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd1920.trab1.impl.MessageResource;
import sd1920.trab1.impl.UserResource;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class EmailServerRest {

    private static Logger Log = Logger.getLogger(EmailServerRest.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8080;

    public static void main(String[] args) throws UnknownHostException {
        InetAddress localHost = InetAddress.getLocalHost();
        String ip = localHost.getHostAddress();
        String domain = localHost.getHostName();

        URI serverURI = URI.create(String.format("https://%s:%s/rest", ip, PORT));

        //This will allow client code executed by this process to ignore hostname verification
        HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

        ResourceConfig config = new ResourceConfig();
        config.register(new MessageResource(domain, serverURI, ByteBuffer.wrap(localHost.getAddress()).getInt(), args[0]));
        config.register(new UserResource(domain, serverURI));

        try {
            JdkHttpServerFactory.createHttpServer(serverURI, config, SSLContext.getDefault());
        }
        catch(NoSuchAlgorithmException e){
            Log.info("Invalid SSL/TLS configuration.\n");
            e.printStackTrace();
        }

        Discovery.startAnnounce(domain, serverURI);
        Discovery.startDiscovery();
        Log.info(String.format("%s REST Server ready @ %s\n", domain, serverURI));
    }

}
