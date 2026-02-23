package com.github.nagyesta.filebarj.core.persistence.inmemory;

import com.github.nagyesta.filebarj.core.persistence.BaseFileMapRepository;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class InMemoryBaseFileMapRepository<K extends BaseFileSetId<K>, U extends Comparable<U>, V extends Comparable<V>>
        implements BaseFileMapRepository<K, U, V> {

    private final Map<UUID, Map<U, V>> fileMaps = new ConcurrentHashMap<>();
    private DataStore dataStore;

    @Override
    public void registerWith(final DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public K createFileMap() {
        final var id = createFileMapId(this::removeFileMap);
        fileMaps.put(id.id(), new ConcurrentHashMap<>());
        return id;
    }

    @Override
    public void appendTo(
            final @NotNull K id,
            final @NotNull U key,
            final @NotNull V value) {
        appendTo(id, Map.of(key, value));
    }

    @Override
    public void appendTo(
            final @NotNull K id,
            final @NotNull Map<U, V> values) {
        if (fileMaps.containsKey(id.id())) {
            final var fileMap = getFileMapById(id);
            fileMap.putAll(values);
        }
    }

    @Override
    public void removeFileMap(final @NotNull K id) {
        fileMaps.remove(id.id());
    }

    @Override
    public boolean isClosed() {
        return fileMaps.isEmpty();
    }

    @Override
    public void close() {
        fileMaps.clear();
    }

    protected DataStore dataStore() {
        return dataStore;
    }

    protected abstract K createFileMapId(Consumer<K> closeWith);

    protected Map<U, V> getFileMapById(final @NotNull K id) {
        return fileMaps.get(id.id());
    }

}
