/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.engine.api.collection;

import net.openhft.chronicle.engine.api.map.MapView;
import net.openhft.chronicle.engine.api.query.Queryable;
import net.openhft.chronicle.engine.api.tree.Assetted;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Marker interface for a collection which represents the values() of a Map.  This may have additional method in future.
 */
public interface ValuesCollection<V> extends Collection<V>, Assetted<MapView<?, V>>,
        Queryable<V> {
    default Stream<V> stream() {
        return Collection.super.stream();
    }
}
