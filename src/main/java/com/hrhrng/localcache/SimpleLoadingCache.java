package com.hrhrng.localcache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Function;

public class SimpleLoadingCache<K,V> {

    ConcurrentMap<K,Object> map;
    Function<K, V> loader;

    public SimpleLoadingCache(Function<K, V> loader) {
        this.map = new ConcurrentHashMap<>();
        this.loader = loader;
    }


    public V get(K key) {
        Object value = map.get(key);
        // already loaded
        if(value != null && !(value instanceof FutureTask)) {
            return (V) value;
        }
        // task is not initial or task is running
        return compute(key, value);
    }

    private V compute(K key, Object v) {
        FutureTask<V> task;
        boolean creator = false;
        if(v != null) {
            task = (FutureTask<V>) v;
        } else {
            task = new FutureTask<V>(() -> loader.apply(key));
            // make sure
            Object prevTask = map.putIfAbsent(key, task);
            // this thread is the creator
            if (prevTask == null) {
                creator = true;
                task.run();
            }
            // task is already created by another thread
            else if (prevTask instanceof FutureTask) {
                task = (FutureTask<V>) prevTask;
            }
            // result has computed
            else {
                return (V) prevTask;
            }
        }

        V result;
        try {
            result = task.get();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while loading cache item", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            }
            throw new IllegalStateException("Unable to load cache item", cause);
        }
        // when the task is done, the creator will put the result to the map
        // so that those thread who were not involved in the competition can get the result
        if (creator) {
            map.put(key, result);
        }
        return result;
    }
}