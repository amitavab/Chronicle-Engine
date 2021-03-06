/*
 *
 *  *     Copyright (C) 2016  higherfrequencytrading.com
 *  *
 *  *     This program is free software: you can redistribute it and/or modify
 *  *     it under the terms of the GNU Lesser General Public License as published by
 *  *     the Free Software Foundation, either version 3 of the License.
 *  *
 *  *     This program is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU Lesser General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU Lesser General Public License
 *  *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package net.openhft.chronicle.engine.server.internal;

import net.openhft.chronicle.engine.api.pubsub.InvalidSubscriberException;
import net.openhft.chronicle.engine.api.pubsub.Subscriber;
import net.openhft.chronicle.engine.api.query.IndexQueueView;
import net.openhft.chronicle.engine.api.query.IndexedEntry;
import net.openhft.chronicle.engine.api.query.VanillaIndexQuery;
import net.openhft.chronicle.engine.api.query.VanillaIndexQueueView;
import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import net.openhft.chronicle.network.connection.WireOutPublisher;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import static net.openhft.chronicle.engine.server.internal.IndexQueueViewHandler.EventId.registerSubscriber;
import static net.openhft.chronicle.engine.server.internal.IndexQueueViewHandler.EventId.unregisterSubscriber;
import static net.openhft.chronicle.network.connection.CoreFields.reply;
import static net.openhft.chronicle.network.connection.CoreFields.tid;

/**
 * Created by Rob Austin
 */
public class IndexQueueViewHandler<E> extends AbstractHandler {

    private static final Logger LOG = LoggerFactory.getLogger(IndexQueueViewHandler.class);

    private Asset contextAsset;
    private WireOutPublisher publisher;

    private final StringBuilder eventName = new StringBuilder();
    private final Map<Long, Subscriber<IndexedEntry>> tidToListener = new ConcurrentHashMap<>();


    @NotNull
    private final BiConsumer<WireIn, Long> dataConsumer = (inWire, inputTid) -> {

        eventName.setLength(0);
        final ValueIn valueIn = inWire.readEventName(eventName);

        if (registerSubscriber.contentEquals(eventName)) {
            if (tidToListener.containsKey(tid)) {
                LOG.info("Duplicate topic registration for tid " + tid);
                return;
            }

            final Subscriber<IndexedEntry> listener = new Subscriber<IndexedEntry>() {

                volatile boolean subscriptionEnded;

                @Override
                public void onMessage(IndexedEntry indexedValue) throws InvalidSubscriberException {

                    if (publisher.isClosed())
                        throw new InvalidSubscriberException();

                    publisher.put(indexedValue.key(), publish -> {
                        publish.writeDocument(true, wire -> wire.writeEventName(tid).int64(inputTid));
                        publish.writeNotCompleteDocument(false, wire ->
                                wire.writeEventName(reply).typedMarshallable(indexedValue));
                    });
                }

                public void onEndOfSubscription() {
                    subscriptionEnded = true;
                    if (!publisher.isClosed()) {
                        publisher.put(null, publish -> {
                            publish.writeDocument(true, wire ->
                                    wire.writeEventName(tid).int64(inputTid));
                            publish.writeDocument(false, wire ->
                                    wire.writeEventName(ObjectKVSubscriptionHandler.EventId.onEndOfSubscription).text(""));
                        });

                    }
                }
            };

            final VanillaIndexQuery<E> query = valueIn.typedMarshallable();

            if (query.select().isEmpty() || query.valueClass() == null) {
                LOG.warn("received empty query");
                return;
            }

            try {
                query.filter();
            } catch (Exception e) {
                LOG.error("unable to load the filter predicate for this query=" + query, e);
                return;
            }

            IndexQueueView indexQueueView = contextAsset.acquireView(IndexQueueView.class);
            indexQueueView.registerSubscriber(listener, query);
            return;
        }

        if (unregisterSubscriber.contentEquals(eventName)) {

            VanillaIndexQueueView indexQueueView = contextAsset.acquireView(VanillaIndexQueueView.class);
            Subscriber<IndexedEntry> listener = tidToListener.remove(inputTid);
            if (listener == null) {
                LOG.warn("No subscriber to present to unsubscribe (" + inputTid + ")");
                return;
            }
            indexQueueView.unregisterSubscriber(listener);
            return;
        }

        outWire.writeDocument(true, wire -> outWire.writeEventName(tid).int64(inputTid));

    };

    @Override
    protected void unregisterAll() {
        final VanillaIndexQueueView indexQueueView = contextAsset.acquireView(VanillaIndexQueueView.class);
        tidToListener.forEach((k, listener) -> indexQueueView.unregisterSubscriber(listener));
        tidToListener.clear();
    }

    void process(@NotNull final WireIn inWire,
                 @NotNull final RequestContext requestContext,
                 @NotNull Asset contextAsset,
                 @NotNull final WireOutPublisher publisher,
                 final long tid,
                 @NotNull final Wire outWire) {
        setOutWire(outWire);
        this.outWire = outWire;
        this.publisher = publisher;
        this.contextAsset = contextAsset;
        this.requestContext = requestContext;
        dataConsumer.accept(inWire, tid);
    }

    public enum Params implements WireKey {
        subscribe
    }

    public enum EventId implements ParameterizeWireKey {
        registerSubscriber(Params.subscribe),
        unregisterSubscriber(),
        onEndOfSubscription;

        private final WireKey[] params;

        <P extends WireKey> EventId(P... params) {
            this.params = params;
        }

        @NotNull
        public <P extends WireKey> P[] params() {
            return (P[]) this.params;
        }
    }
}
