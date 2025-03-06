package com.github.nagyesta.filebarj.core.common.database;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentSortedMap<K extends Comparable<K>, V> implements SortedMap<K, V> {

    private final SortedMap<K, V> data;
    private final ReentrantReadWriteLock lock;
    private final String keyName;
    private final String valueType;
    private boolean changedSinceLastMark;

    public ConcurrentSortedMap(final String keyName, final String valueType) {
        this.keyName = keyName;
        this.valueType = valueType;
        this.changedSinceLastMark = true;
        this.data = new TreeMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    public void mark() {
        this.changedSinceLastMark = false;
    }

    public void readLock() {
        lock.readLock().lock();
    }

    public void writeLock() {
        lock.readLock().lock();
    }

    public void readUnlock() {
        lock.readLock().unlock();
    }

    public void writeUnlock() {
        lock.writeLock().unlock();
    }

    public boolean hasChangesSinceMark() {
        return this.changedSinceLastMark;
    }

    public V getExistingValue(final K key) {
        return doRead(() -> {
            if (!data.containsKey(key)) {
                throw new IllegalStateException(valueType + " not found by " + keyName + ":" + key);
            }
            return data.get(key);
        });
    }

    @Override
    public Comparator<? super K> comparator() {
        return this.data.comparator();
    }

    @Override
    public @NonNull SortedMap<K, V> subMap(final K fromKey, final K toKey) {
        return doRead(() -> this.data.subMap(fromKey, toKey));
    }

    @Override
    public @NonNull SortedMap<K, V> headMap(final K toKey) {
        return doRead(() -> this.data.headMap(toKey));
    }

    @Override
    public @NonNull SortedMap<K, V> tailMap(final K fromKey) {
        return doRead(() -> this.data.tailMap(fromKey));
    }

    @Override
    public K firstKey() {
        return doRead(this.data::firstKey);
    }

    @Override
    public K lastKey() {
        return doRead(this.data::lastKey);
    }

    @Override
    public int size() {
        return doRead(this.data::size);
    }

    @Override
    public boolean isEmpty() {
        return doRead(this.data::isEmpty);
    }

    @Override
    public boolean containsKey(@NonNull final Object key) {
        return doRead(() -> this.data.containsKey(key));
    }

    @Override
    public boolean containsValue(@NonNull final Object value) {
        return doRead(() -> this.data.containsValue(value));
    }

    @Override
    public V get(@NonNull final Object key) {
        return doRead(() -> this.data.get(key));
    }

    @Override
    public @Nullable V put(@NonNull final K key, @NonNull final V value) {
        return doWWrite(() -> this.data.put(key, value));
    }

    @Override
    public V remove(@NonNull final Object key) {
        return doWWrite(() -> this.data.remove(key));
    }

    @Override
    public void putAll(@NonNull final Map<? extends K, ? extends V> m) {
        doWWrite(() -> this.data.putAll(m));
    }

    @Override
    public void clear() {
        doWWrite(this.data::clear);
    }

    @Override
    public Set<K> keySet() {
        return doRead(this.data::keySet);
    }

    @Override
    public Collection<V> values() {
        return doRead(this.data::values);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return doRead(this.data::entrySet);
    }

    private <T> T doRead(final Callable<T> tryOperation) {
        return doWhileLocked(lock.readLock(), tryOperation, () -> {
        });
    }

    private <T> T doWWrite(final Callable<T> tryOperation) {
        return doWhileLocked(lock.writeLock(), tryOperation, () -> {
            this.changedSinceLastMark = true;
        });
    }

    private void doWWrite(final Runnable tryOperation) {
        doWhileLocked(lock.writeLock(), tryOperation, () -> {
        });
    }

    private void doWhileLocked(final Lock lockUsed, final Runnable tryOperation, final Runnable doFinally) {
        this.doWhileLocked(lockUsed, () -> {
            tryOperation.run();
            return null;
        }, doFinally);
    }

    private <T> T doWhileLocked(final Lock lockUsed, final Callable<T> tryOperation, final Runnable doFinally) {
        lockUsed.lock();
        try {
            return tryOperation.call();
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            doFinally.run();
            lockUsed.unlock();
        }
    }
}
