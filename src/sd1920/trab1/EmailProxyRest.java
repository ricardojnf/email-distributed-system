package sd1920.trab1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd1920.trab1.impl.MessageProxy;
import sd1920.trab1.impl.UserProxy;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class EmailProxyRest {

    private static Logger Log = Logger.getLogger(EmailServerRest.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        InetAddress localHost = InetAddress.getLocalHost();
        String ip = localHost.getHostAddress();
        String domain = localHost.getHostName();

        URI serverURI = URI.create(String.format("https://%s:%s/rest", ip, PORT));

        //This will allow client code executed by this process to ignore hostname verification
        HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

        //Create instace of the DropBoxApi class
        DropBoxApi api = new DropBoxApi(domain, Boolean.parseBoolean(args[0]));

        ResourceConfig config = new ResourceConfig();
        config.register(new MessageProxy(domain, serverURI, ByteBuffer.wrap(localHost.getAddress()).getInt(), args[1], api));
        config.register(new UserProxy(domain, api));

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
