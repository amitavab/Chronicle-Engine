package net.openhft.chronicle.engine.map;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.bytes.BytesUtil;
import net.openhft.chronicle.engine.api.map.*;
import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.SubscriptionConsumer;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetNotFoundException;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;

import static net.openhft.chronicle.engine.map.Buffers.BUFFERS;

/**
 * Created by peter on 25/05/15.
 */
public class VanillaStringStringKeyValueStore implements StringStringKeyValueStore {
    private final ObjectKVSSubscription<String, StringBuilder, String> subscriptions;

    private SubscriptionKeyValueStore<String, Bytes, BytesStore> kvStore;
    private Asset asset;

    public VanillaStringStringKeyValueStore(RequestContext context, @NotNull Asset asset,
                                            @NotNull SubscriptionKeyValueStore<String, Bytes, BytesStore> kvStore) throws AssetNotFoundException {
        this(asset.acquireView(ObjectKVSSubscription.class, context), asset, kvStore);
    }

    VanillaStringStringKeyValueStore(ObjectKVSSubscription<String, StringBuilder, String> subscriptions,
                                     @NotNull Asset asset,
                                     @NotNull SubscriptionKeyValueStore<String, Bytes, BytesStore> kvStore) throws AssetNotFoundException {
        this.asset = asset;
        this.kvStore = kvStore;
        asset.registerView(ValueReader.class, StringValueReader.BYTES_STORE_TO_STRING);
        RawKVSSubscription<String, Bytes, BytesStore> rawSubscription =
                (RawKVSSubscription<String, Bytes, BytesStore>) kvStore.subscription(true);
        this.subscriptions = subscriptions;
        rawSubscription.registerDownstream(mpe ->
                subscriptions.notifyEvent(mpe.translate(s -> s, BytesStoreToString.BYTES_STORE_TO_STRING)));
    }

    enum BytesStoreToString implements Function<BytesStore, String> {
        BYTES_STORE_TO_STRING;

        @Override
        public String apply(BytesStore bs) {
            return bs == null ? null : BytesUtil.to8bitString(bs);
        }
    }

    enum StringValueReader implements ValueReader<BytesStore, String> {
        BYTES_STORE_TO_STRING;

        @NotNull
        @Override
        public String readFrom(BytesStore bs, String usingValue) {
            return bs == null ? null : BytesUtil.to8bitString(bs);
        }
    }

    @NotNull
    @Override
    public ObjectKVSSubscription<String, StringBuilder, String> subscription(boolean createIfAbsent) {
        return subscriptions;
    }

    @Override
    public boolean put(String key, String value) {
        Buffers b = BUFFERS.get();
        Bytes<ByteBuffer> bytes = b.valueBuffer;
        bytes.clear();
        bytes.append8bit(value);
        bytes.flip();
        return kvStore.put(key, bytes);
    }

    @Nullable
    @Override
    public String getAndPut(String key, String value) {
        Buffers b = BUFFERS.get();
        Bytes<ByteBuffer> bytes = b.valueBuffer;
        bytes.clear();
        bytes.append(value);
        bytes.flip();
        BytesStore retBytes = kvStore.getAndPut(key, bytes);
        return retBytes == null ? null : retBytes.toString();
    }

    @Override
    public boolean remove(String key) {
        return kvStore.remove(key);
    }

    @Nullable
    @Override
    public String getAndRemove(String key) {
        BytesStore retBytes = kvStore.getAndRemove(key);
        return retBytes == null ? null : retBytes.toString();
    }

    @Nullable
    @Override
    public String getUsing(String key, StringBuilder value) {
        Buffers b = BUFFERS.get();
        BytesStore retBytes = kvStore.getUsing(key, b.valueBuffer);
        return retBytes == null ? null : retBytes.toString();
    }

    @Override
    public long longSize() {
        return kvStore.longSize();
    }

    @Override
    public void keysFor(int segment, SubscriptionConsumer<String> kConsumer) throws InvalidSubscriberException {
        kvStore.keysFor(segment, kConsumer);
    }

    @Override
    public void entriesFor(int segment, @NotNull SubscriptionConsumer<MapEvent<String, String>> kvConsumer) throws InvalidSubscriberException {
        kvStore.entriesFor(segment, e -> kvConsumer.accept(e.translate(k -> k, BytesStoreToString.BYTES_STORE_TO_STRING)));
    }

    @NotNull
    @Override
    public Iterator<Map.Entry<String, String>> entrySetIterator() {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        try {
            for (int i = 0, seg = segments(); i < seg; i++)
                entriesFor(i, e -> entries.add(new AbstractMap.SimpleEntry<>(e.key(), e.value())));
        } catch (InvalidSubscriberException e) {
            throw new AssertionError(e);
        }
        return entries.iterator();
    }

    @Override
    public void clear() {
        kvStore.clear();
    }

    @Override
    public boolean containsValue(final StringBuilder value) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void replicatedPut(final Bytes key, final Bytes value, final byte remoteIdentifer, final long timestamp) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public void replicatedRemove(final Bytes key, final byte identifier, final long timestamp) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Asset asset() {
        return asset;
    }

    @Override
    public KeyValueStore underlying() {
        return kvStore;
    }

    @Override
    public void close() {
        kvStore.close();
    }
}