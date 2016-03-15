package net.openhft.chronicle.engine.server.internal;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.core.annotation.UsedViaReflection;
import net.openhft.chronicle.core.threads.EventHandler;
import net.openhft.chronicle.core.threads.EventLoop;
import net.openhft.chronicle.core.threads.HandlerPriority;
import net.openhft.chronicle.core.threads.InvalidEventHandlerException;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.tree.ChronicleQueueView;
import net.openhft.chronicle.engine.tree.QueueView;
import net.openhft.chronicle.network.connection.CoreFields;
import net.openhft.chronicle.network.connection.WireOutPublisher;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.openhft.chronicle.network.WireTcpHandler.logYaml;

/**
 * Created by Rob Austin
 */
public class QueueReplicationHandler extends AbstractSubHandler<EngineWireNetworkContext>
        implements Demarshallable, WriteMarshallable {

    private static final Logger LOG = LoggerFactory.getLogger(QueueReplicationHandler.class);
    private final boolean isSource;

    private long lastIndexReceived;
    private EventLoop eventLoop;
    private Asset rootAsset;
    private ExcerptAppender appender;
    private boolean closed;

    @UsedViaReflection
    private QueueReplicationHandler(WireIn wire) {
        lastIndexReceived = wire.read(() -> "lastIndexReceived").int64();
        isSource = wire.read(() -> "isSource").bool();
    }

    public QueueReplicationHandler(long lastIndexReceived, boolean isSource) {
        this.lastIndexReceived = lastIndexReceived;
        this.isSource = isSource;
    }

    @Override
    public void writeMarshallable(@NotNull WireOut wire) {
        wire.write(() -> "lastIndexReceived").int64(lastIndexReceived);
        wire.write(() -> "isSource").bool(isSource);
    }

    @Override
    public void processData(@NotNull WireIn inWire, @NotNull WireOut outWire) {

        if (closed)
            return;

        final StringBuilder eventName = Wires.acquireStringBuilder();
        final ValueIn valueIn = inWire.readEventName(eventName);

        // receives replication events
        if ("replicationEvent".contentEquals(eventName)) {
            final ReplicationEvent replicationEvent = valueIn.typedMarshallable();

            if (replicationEvent == null)
                return;

            long index;

            try {
                index = appender.lastIndexAppended();
            } catch (Exception ignore) {
                index = -1;
            }

            if (index == -1)
                appender.writeBytes(replicationEvent.payload().bytesForRead());
            else if (replicationEvent.index() - 1 == index) {
                appender.writeBytes(replicationEvent.payload().bytesForRead());
            } else {
                LOG.error("replication failed due to out of order indexes");
            }
        }
    }

    public static class ReplicationEvent implements Demarshallable, WriteMarshallable {

        private final long index;
        private final BytesStore payload;

        @UsedViaReflection
        private ReplicationEvent(@NotNull WireIn wireIn) {
            index = wireIn.read(() -> "index").int64();
            payload = wireIn.read(() -> "payload").bytesStore();
        }

        public ReplicationEvent(long index, @NotNull Bytes payload) {
            this.index = index;
            this.payload = payload;
        }

        public long index() {
            return index;
        }

        @NotNull
        public BytesStore payload() {
            return payload;
        }

        @Override
        public void writeMarshallable(@NotNull WireOut wire) {
            wire.write(() -> "index").int64(index);
            wire.write(() -> "payload").bytes(payload);
        }

        @Override
        public String toString() {
            return "ReplicationEvent{" +
                    "index=" + index +
                    ", payload=" + Wires.fromSizePrefixedBlobs(payload.bytesForRead()) +
                    '}';
        }
    }

    class EventListener implements EventHandler {


        @NotNull
        final Bytes bytes = Bytes.elasticByteBuffer();

        @NotNull
        final ExcerptTailer tailer;

        @NotNull
        final WireOutPublisher publisher;

        public EventListener(@NotNull final ExcerptTailer tailer,
                             @NotNull final WireOutPublisher publisher) {
            this.tailer = tailer;
            this.publisher = publisher;
        }

        @Override
        public boolean action() throws InvalidEventHandlerException, InterruptedException {

            if (closed)
                throw new InvalidEventHandlerException("closed");

            if (!publisher.canTakeMoreData())
                return false;

            long index = tailer.index();
            if (index <= lastIndexReceived)
                return false;

            tailer.readBytes(bytes);

            if (bytes.readRemaining() == 0)
                return false;

            lastIndexReceived = tailer.index();

            final ReplicationEvent event = new ReplicationEvent(index, bytes);
            publisher.put("", d -> d.writeDocument(false,
                    w -> w.writeEventName(() -> "replicationEvent").typedMarshallable
                            (event)));
            bytes.clear();
            return false;
        }

        @Override
        public HandlerPriority priority() {
            return HandlerPriority.REPLICATION;
        }
    }


    @Override
    public void onBootstrap(@NotNull WireOut outWire) {

        rootAsset = nc().rootAsset();
        final Asset asset = rootAsset.acquireAsset(csp());
        final ChronicleQueueView chronicleQueueView = (ChronicleQueueView) asset.acquireView(QueueView
                .class);
        final ChronicleQueue chronicleQueue = chronicleQueueView.chronicleQueue();
        appender = chronicleQueue.createAppender();

        assert appender != null;

        if (!isSource)
            return;

        eventLoop = rootAsset.findOrCreateView(EventLoop.class);
        eventLoop.start();

        outWire.writeDocument(true, d -> {

            // this handler will just receive events
            final QueueReplicationHandler handler = new QueueReplicationHandler(0, false);

            d.writeEventName(CoreFields.csp).text(csp())
                    .writeEventName(CoreFields.cid).int64(cid())
                    .writeEventName(CoreFields.handler).typedMarshallable(handler);
        });


        eventLoop.addHandler(new EventListener(chronicleQueue.createTailer(), nc().wireOutPublisher()));
        logYaml(outWire);
    }

    @Override
    public void close() {
        this.closed = true;
    }

}