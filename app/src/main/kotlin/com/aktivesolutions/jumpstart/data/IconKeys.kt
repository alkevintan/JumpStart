package com.aktivesolutions.jumpstart.data

import java.security.MessageDigest

/** Pure icon cache key functions. [componentKey] is the full flattened ComponentName (package AND activity). */
object IconKeys {
    /** Directory name; bumped from "icons" when the key format changed so old files are discarded. */
    const val DIR = "icons2"
    const val LEGACY_DIR = "icons"

    fun memKey(componentKey: String, px: Int) = "$componentKey@$px"

    /** Full SHA-1 hex (40 chars) of the whole component string. */
    fun hash(componentKey: String): String =
        MessageDigest.getInstance("SHA-1").digest(componentKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun diskName(componentKey: String, px: Int, lastUpdate: Long) = "${hash(componentKey)}_${px}_$lastUpdate.png"

    /** Same component + size, any lastUpdateTime. */
    fun diskPrefix(componentKey: String, px: Int) = "${hash(componentKey)}_${px}_"
}
