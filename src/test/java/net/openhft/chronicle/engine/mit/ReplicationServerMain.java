package net.openhft.chronicle.engine.mit;

import net.openhft.chronicle.engine.api.EngineReplication;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.pubsub.Publisher;
import net.openhft.chronicle.engine.api.pubsub.Replication;
import net.openhft.chronicle.engine.api.pubsub.TopicPublisher;
import net.openhft.chronicle.engine.api.session.SessionProvider;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.fs.Cluster;
import net.openhft.chronicle.engine.fs.Clusters;
import net.openhft.chronicle.engine.fs.HostDetails;
import net.openhft.chronicle.engine.map.*;
import net.openhft.chronicle.engine.pubsub.VanillaReference;
import net.openhft.chronicle.engine.pubsub.VanillaTopicPublisher;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.session.VanillaSessionProvider;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.engine.tree.VanillaReplication;
import net.openhft.chronicle.threads.EventGroup;
import net.openhft.chronicle.threads.api.EventLoop;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.YamlLogging;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;

import static net.openhft.chronicle.engine.api.tree.RequestContext.requestContext;

/**
 * Created by Rob Austin
 */

public class ReplicationServerMain {


    public static final String REMOTE_HOSTNAME = System.getProperty("remote.host");
    public static final Integer HOST_ID = Integer.getInteger("hostId", 1);

    public static void main(String[] args) throws IOException, InterruptedException {
        final Integer hostId = HOST_ID;
        String remoteHostname = REMOTE_HOSTNAME;
        ReplicationServerMain replicationServerMain = new ReplicationServerMain();
        replicationServerMain.create(hostId, remoteHostname);

        for(;;) {
            Thread.sleep(10000);
        }

    }


    /**
     * @param identifier     the local host identifier
     * @param remoteHostname the hostname of the remote host
     * @throws IOException
     */

    @NotNull
    public ServerEndpoint create(int identifier, String remoteHostname) throws IOException {
        if (identifier < 0 || identifier > Byte.MAX_VALUE)
            throw new IllegalStateException();
        return create((byte) identifier, remoteHostname);
    }

    @NotNull
    private ServerEndpoint create(byte identifier, String remoteHostname) throws IOException {

        YamlLogging.clientReads = true;
        YamlLogging.clientWrites = true;
        YamlLogging.showServerWrites = true;
        YamlLogging.showServerReads = true;
        WireType wireType = WireType.TEXT;

        System.out.println("using local hostid=" + identifier);
        System.out.println("using remote hostname=" + remoteHostname);

        final VanillaAssetTree tree = new VanillaAssetTree(identifier);
        newCluster(identifier, tree, remoteHostname);
        tree.root().addLeafRule(EngineReplication.class, "Engine replication holder",
                CMap2EngineReplicator::new);

        tree.root().addView(SessionProvider.class, new VanillaSessionProvider());
        tree.root().addWrappingRule(Replication.class, "replication", VanillaReplication::new, MapView.class);
        tree.root().addWrappingRule(MapView.class, "mapv view", VanillaMapView::new, AuthenticatedKeyValueStore.class);
        tree.root().addWrappingRule(TopicPublisher.class, " topic publisher", VanillaTopicPublisher::new, MapView.class);
        tree.root().addWrappingRule(Publisher.class, "publisher", VanillaReference::new, MapView.class);
        tree.root().addLeafRule(ObjectKVSSubscription.class, " vanilla", VanillaKVSSubscription::new);

        ThreadGroup threadGroup = new ThreadGroup("my-named-thread-group");
        threadGroup.setDaemon(true);
        tree.root().addView(ThreadGroup.class, threadGroup);

        tree.root().addView(EventLoop.class, new EventGroup(true));
        Asset asset = tree.root().acquireAsset("map");
        asset.addView(AuthenticatedKeyValueStore.class, new ChronicleMapKeyValueStore<>(requestContext("map"), asset));

        tree.root().addLeafRule(ObjectKVSSubscription.class, " ObjectKVSSubscription",
                VanillaKVSSubscription::new);

        ReplicationClientTest.closeables.add(tree);
        ServerEndpoint serverEndpoint = new ServerEndpoint("*:" + (5700 + identifier), tree, wireType);
        ReplicationClientTest.closeables.add(serverEndpoint);

        return serverEndpoint;
    }


    private static void newCluster(byte host, @NotNull VanillaAssetTree tree, String remoteHostname) {
        Clusters clusters = new Clusters();
        HashMap<String, HostDetails> hostDetailsMap = new HashMap<String, HostDetails>();

        {
            final HostDetails value = new HostDetails();
            value.hostId = 1;
            value.connectUri = (host == 1 ? "*" : remoteHostname) + ":" + 5701;
            value.timeoutMs = 1000;
            hostDetailsMap.put("host1", value);
        }
        {
            final HostDetails value = new HostDetails();
            value.hostId = 2;
            value.connectUri = (host == 2 ? "*" : remoteHostname) + ":" + 5702;
            value.timeoutMs = 1000;
            hostDetailsMap.put("host2", value);
        }


        clusters.put("cluster", new Cluster("hosts", hostDetailsMap));
        tree.root().addView(Clusters.class, clusters);
    }
}
