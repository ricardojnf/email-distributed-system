package sd1920.trab1;

import org.apache.zookeeper.CreateMode;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd1920.trab1.impl.*;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

public class EmailServerRestReplicated {

    private static Logger Log = Logger.getLogger(EmailServerRestReplicated.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        InetAddress localHost = InetAddress.getLocalHost();
        String ip = localHost.getHostAddress();
        String domain = localHost.getHostName();
        String path = "/" + domain;

        URI serverURI = URI.create(String.format("https://%s:%s/rest", ip, PORT));

        //This will allow client code executed by this process to ignore hostname verification
        HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

        ZookeeperProcessor zk = new ZookeeperProcessor("kafka:2181");
        String newPath = zk.write(path, CreateMode.PERSISTENT);

        if(newPath != null)
            System.out.println("Created znode with: " + newPath);

        newPath = zk.write(path + "/seq_", serverURI.toString(), CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Created znode child with: " + newPath);

        VersionControl versionControl = new VersionControl(domain, serverURI.toString(), zk);

        ResourceConfig config = new ResourceConfig();
        config.register(new MessageReplicated(domain, serverURI,
                ByteBuffer.wrap(localHost.getAddress()).getInt(), args[0], versionControl));
        config.register(new UserReplicated(domain, serverURI, versionControl));

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
