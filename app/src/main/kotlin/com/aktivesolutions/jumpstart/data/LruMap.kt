package com.aktivesolutions.jumpstart.data

/** Small thread-safe LRU map (access order). Wrap values in a holder to cache negative results. */
class LruMap<K, V : Any>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxEntries
    }

    @Synchronized fun get(key: K): V? = map[key]
    @Synchronized fun put(key: K, value: V) { map[key] = value }
    @Synchronized fun clear() = map.clear()
    @Synchronized fun size(): Int = map.size
}

/** Cached lookup outcome; [value] == null is a remembered "no such entry". */
class Cached<T : Any>(val value: T?)
