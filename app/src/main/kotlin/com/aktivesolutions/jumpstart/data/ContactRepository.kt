package com.aktivesolutions.jumpstart.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import com.aktivesolutions.jumpstart.search.ContactRow

/** Reads the address book once into rows for ContactIndex (no per-keystroke ContentResolver queries). */
class ContactRepository(private val context: Context) {
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Blocking; call off the main thread. */
    fun loadRows(): List<ContactRow> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<ContactRow>()
        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.IS_PRIMARY),
                null, null,
                "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val num = c.getString(1) ?: continue
                    out += ContactRow(name, num, c.getInt(2) != 0)
                }
            }
        } catch (_: SecurityException) {
        } catch (_: RuntimeException) {
            // Provider-side failures (SQLiteException, IllegalArgumentException, ...) must not crash the launcher.
        }
        return out
    }
}
