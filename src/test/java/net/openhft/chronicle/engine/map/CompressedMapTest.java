package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.map.remote.CompressedKeyValueStore;
import net.openhft.chronicle.engine.map.remote.RemoteKeyValueStore;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.wire.WireType;
import net.openhft.chronicle.wire.YamlLogging;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by daniel on 30/07/2015.
 */
public class CompressedMapTest {
    @Ignore //In Progress
    @Test
    public void testCompression() throws IOException {

        //Enable Yaml logging when running in debug.
        YamlLogging.showServerWrites = true;
        YamlLogging.showServerReads = true;
        YamlLogging.clientWrites = true;
        YamlLogging.clientReads = true;

        //For this test we can use a VanillaMapKeyValueStore
        //To test with a ChronicleMapKeyValueStore uncomment lines below
        AssetTree serverAssetTree = new VanillaAssetTree().forTesting();
//        serverAssetTree.root().addWrappingRule(MapView.class, "map directly to KeyValueStore",
//                VanillaMapView::new, KeyValueStore.class);
//        serverAssetTree.root().addLeafRule(KeyValueStore.class, "use Chronicle Map", (context, asset) ->
//                new ChronicleMapKeyValueStore(context.basePath(OS.TARGET), asset));
        TCPRegistry.createServerSocketChannelFor("RemoteSubscriptionModelPerformanceTest.port");

        ServerEndpoint serverEndpoint = new ServerEndpoint("RemoteSubscriptionModelPerformanceTest.port",
                serverAssetTree, WireType.TEXT);
        AssetTree clientAssetTree = new VanillaAssetTree()
                .forRemoteAccess("RemoteSubscriptionModelPerformanceTest.port", WireType.TEXT);
        clientAssetTree.root().addLeafRule(RemoteKeyValueStore.class, " Remote AKVS",
                (requestContext, asset) -> new RemoteKeyValueStore<>
                        (requestContext.clone().valueType(BytesStore.class), asset));
        clientAssetTree.root().addWrappingRule(ObjectKeyValueStore.class, " Compressed KVS",
                CompressedKeyValueStore::new, RemoteKeyValueStore.class);


        MapView<Integer, String> testMap = clientAssetTree.acquireMap("/tmp/testBatch", Integer.class, String.class);

        for (int i = 0; i < 2; i++) {
            testMap.put(i, "" + i);
        }

        assertEquals("1", testMap.get(1));
        testMap.remove(1);


        assertEquals(null, testMap.get(1));
        assertEquals(1, testMap.longSize());

        clientAssetTree.registerSubscriber("/tmp/testBatch", MapEvent.class, new Subscriber<MapEvent>() {
            @Override
            public void onMessage(MapEvent mapEvent) throws InvalidSubscriberException {
                System.out.println("******" + mapEvent);
            }
        });


        testMap.put(5, "5");

        Jvm.pause(1000);
    }
}