package sd1920.trab1;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class InsecureHostnameVerifier implements HostnameVerifier {

    @Override
    public boolean verify(String hostname, SSLSession sslSession) {
        //Ignore the verification of hostname in the certificate
        //This should not be used in production systems
        return true;
    }
}
