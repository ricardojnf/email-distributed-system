package sd1920.trab1.impl;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import sd1920.trab1.ZookeeperProcessor;
import sd1920.trab1.clients.ClientFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class VersionControl {

    private long version;
    Watcher watcher;
    private String selfURI, primaryURI, currentPrimaryURI;
    private Set<String> childrenURI;
    private Map<Long,Operation> operations;

    public VersionControl(String domain, String selfURI, ZookeeperProcessor zk){
        this.version = 0L;
        this.selfURI = selfURI;
        this.primaryURI = null;
        this.currentPrimaryURI = null;
        this.operations = new HashMap<>();
        this.childrenURI = new HashSet<>();
        this.watcher = new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                childrenURI = new HashSet<>();
                List<String> lst = zk.getChildren("/" + domain, this);
                AtomicInteger id = new AtomicInteger(Integer.MAX_VALUE);
                lst.stream().forEach(seq -> {
                    int aux = Integer.parseInt(seq.split("_")[1]);
                    String childURI = zk.getValue("/" + domain + "/seq_" + seq.split("_")[1]);
                    childrenURI.add(childURI);
                    if(aux < id.get()) {
                        id.set(aux);
                        currentPrimaryURI = childURI;
                    }
                });
                if(primaryURI != null && !primaryURI.equals(currentPrimaryURI)
                        && currentPrimaryURI.equals(selfURI)) {
                    for (String childURI : getSecondariesURI()) {
                        try {
                            ClientFactory.getUsersClient(new URI(selfURI), 5, 1000).
                                    update(version + 1, childURI, MessageReplicated.secret);
                        } catch (URISyntaxException ignored){}
                    }
                }
                primaryURI = currentPrimaryURI;
            }
        };
        watcher.process(null);
        Thread t = new Thread(() -> { zk.getChildren("/" + domain, watcher); });
        t.start();
    }

    public long addVersion(Operation op){
        synchronized (this) {
            this.operations.put(++version, op);
        }
        return version;
    }

    public List<Operation> getOperations(Long version){
        List<Operation> list = new LinkedList<>();
        synchronized (this) {
            for(long i = version; i < this.version; i++)
                list.add(this.operations.get(i));
        }
        return list;
    }

    public synchronized long getVersion(){
        return version;
    }

    public synchronized String getPrimaryURI(){
        return primaryURI;
    }

    public Set<String> getSecondariesURI() {
        //Remove the primary one from the set to return only the secondaries
        Set<String> aux = new HashSet<>(childrenURI.size() - 1);
        synchronized (this) {
            for (String childURI : childrenURI)
                if (!childURI.equals(currentPrimaryURI))
                    aux.add(childURI);
        }
        return aux;
    }

}
