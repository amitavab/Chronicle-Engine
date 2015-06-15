/*
 * Copyright 2014 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.engine.Factor;
import net.openhft.chronicle.engine.ThreadMonitoringTest;
import net.openhft.chronicle.engine.api.map.MapEvent;
import net.openhft.chronicle.engine.api.map.MapEventListener;
import net.openhft.chronicle.engine.server.ServerEndpoint;
import net.openhft.chronicle.engine.tree.VanillaAssetTree;
import net.openhft.chronicle.wire.TextWire;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static net.openhft.chronicle.engine.Utils.yamlLoggger;
import static net.openhft.chronicle.engine.map.MapClientTest.RemoteMapSupplier.toUri;
import static net.openhft.chronicle.engine.server.WireType.wire;
import static org.junit.Assert.assertEquals;

/**
 * test using the listener both remotely or locally via the engine
 *
 * @author Rob Austin.
 */
@RunWith(value = Parameterized.class)
public class SubscriptionTest extends ThreadMonitoringTest {
    private static int port;
    private static ConcurrentMap<String, Factor> map;
    private static final String NAME = "test";
    private static MapEventListener<String, Factor> listener;
    private static Boolean isRemote;

    @Parameterized.Parameters
    public static Collection<Object[]> data() throws IOException {

        return Arrays.asList(new Boolean[][]{
                {false},
                {true}
        });
    }

    public SubscriptionTest(Boolean isRemote){
        this.isRemote = isRemote;
    }

    @Test
    public void testSubscriptionTest() throws IOException, InterruptedException {
        Factor factorXYZ = new Factor();
        factorXYZ.setAccountNumber("xyz");

        Factor factorABC = new Factor();
        factorABC.setAccountNumber("abc");

        Factor factorDDD = new Factor();
        factorDDD.setAccountNumber("ddd");

        listener = EasyMock.createMock(MapEventListener.class);
        listener.insert("testA", factorXYZ);
        listener.insert("testB", factorABC);
        listener.update("testA", factorXYZ, factorDDD);
        listener.remove("testA", factorDDD);

        EasyMock.replay(listener);

        VanillaAssetTree serverAssetTree = new VanillaAssetTree().forTesting();
        VanillaAssetTree clientAssetTree = new VanillaAssetTree().forRemoteAccess();
        ServerEndpoint serverEndpoint = null;
        if (isRemote) {
            wire = TextWire::new;

            serverEndpoint = new ServerEndpoint(serverAssetTree);
            port = serverEndpoint.getPort();

            map = clientAssetTree.acquireMap(toUri(NAME, port, "localhost"), String.class, Factor.class);
            clientAssetTree.registerSubscriber(toUri(NAME, port, "localhost"), MapEvent.class, e -> e.apply(listener));
        } else {
            map = serverAssetTree.acquireMap(NAME, String.class, Factor.class);
            serverAssetTree.registerSubscriber(NAME, MapEvent.class, e -> e.apply(listener));
        }

        yamlLoggger(() -> {
            //test an insert
            map.put("testA", factorXYZ);
            assertEquals(1, map.size());
            assertEquals("xyz", map.get("testA").getAccountNumber());

            //test another insert
            map.put("testB", factorABC);
            assertEquals("abc", map.get("testB").getAccountNumber());

            //Test an update
            map.put("testA", factorDDD);
            assertEquals("ddd", map.get("testA").getAccountNumber());

            //Test a remove
            map.remove("testA");

            if(isRemote) {
                clientAssetTree.unregisterSubscriber(NAME, MapEvent.class, e -> e.apply(listener));
            }else{
                serverAssetTree.unregisterSubscriber(toUri(NAME, port, "localhost"), MapEvent.class, e -> e.apply(listener));
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //Test that after unregister we don't get events
            map.put("testC", factorXYZ);
        });

        clientAssetTree.close();
        if(serverEndpoint != null)serverEndpoint.close();
        serverAssetTree.close();

        EasyMock.verify(listener);
    }

    @Test
    public void testKeySubscriber() throws IOException{
        Factor factorXYZ = new Factor();
        factorXYZ.setAccountNumber("xyz");

        Factor factorABC = new Factor();
        factorABC.setAccountNumber("abc");

        Factor factorDDD = new Factor();
        factorDDD.setAccountNumber("ddd");

        listener = EasyMock.createMock(MapEventListener.class);
        listener.insert("testA", factorXYZ);
        listener.insert("testB", factorABC);
        listener.update("testA", factorXYZ, factorDDD);
        listener.remove("testA", factorDDD);

        EasyMock.replay(listener);

        VanillaAssetTree serverAssetTree = new VanillaAssetTree().forTesting();
        VanillaAssetTree clientAssetTree = new VanillaAssetTree().forRemoteAccess();
        ServerEndpoint serverEndpoint = null;
        if (isRemote) {
            wire = TextWire::new;

            serverEndpoint = new ServerEndpoint(serverAssetTree);
            port = serverEndpoint.getPort();

            map = clientAssetTree.acquireMap(toUri(NAME, port, "localhost"), String.class, Factor.class);
           // clientAssetTree.registerKeySubscriber(toUri(NAME, port, "localhost"), MapEvent.class, e -> e.apply(listener));
        } else {
            map = serverAssetTree.acquireMap(NAME, String.class, Factor.class);
            serverAssetTree.registerSubscriber(NAME, MapEvent.class, e -> e.apply(listener));
        }

        yamlLoggger(() -> {
            //test an insert
            map.put("testA", factorXYZ);
            assertEquals(1, map.size());
            assertEquals("xyz", map.get("testA").getAccountNumber());

            //test another insert
            map.put("testB", factorABC);
            assertEquals("abc", map.get("testB").getAccountNumber());

            //Test an update
            map.put("testA", factorDDD);
            assertEquals("ddd", map.get("testA").getAccountNumber());

            //Test a remove
            map.remove("testA");

            if(isRemote) {
                clientAssetTree.unregisterSubscriber(NAME, MapEvent.class, e -> e.apply(listener));
            }else{
                serverAssetTree.unregisterSubscriber(toUri(NAME, port, "localhost"), MapEvent.class, e -> e.apply(listener));
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //Test that after unregister we don't get events
            map.put("testC", factorXYZ);
        });

        clientAssetTree.close();
        if(serverEndpoint != null)serverEndpoint.close();
        serverAssetTree.close();

        EasyMock.verify(listener);

    }
}
