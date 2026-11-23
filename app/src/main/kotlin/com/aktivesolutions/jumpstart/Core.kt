package com.aktivesolutions.jumpstart

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.aktivesolutions.jumpstart.data.AppRepository
import com.aktivesolutions.jumpstart.data.ContactRepository
import com.aktivesolutions.jumpstart.data.Dictionary
import com.aktivesolutions.jumpstart.data.Prefs
import com.aktivesolutions.jumpstart.search.ContactIndex
import com.aktivesolutions.jumpstart.search.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The (untagged) app list; [gen] bumps on a forced reload so collectors re-preload icons even if the list is equal. */
class AppsSnapshot(val list: List<Item>, val gen: Int)

/**
 * Process-wide singletons shared by the home VM, the search-overlay VM and the edge bar. Two VMs used to each own a
 * Prefs (with in-memory launch-count / tag caches that overwrote each other), an AppRepository and Dictionary (racing on
 * apps.cache.tmp and the dictionary copy), and each redid the whole cold start. Now exactly one owner holds the data
 * and the receivers / observers; the VMs only collect [apps] / [contactIndex] and search.
 */
object Core {
    private lateinit var app: Application
    val prefs by lazy { Prefs(app) }
    val appRepo by lazy { AppRepository(app) }
    val contacts by lazy { ContactRepository(app) }
    val dictionary by lazy { Dictionary(app, prefs) }

    // Main-thread scope for the process lifetime (no VM owns this work any more).
    private val scope = MainScope()

    private val _apps = MutableStateFlow(AppsSnapshot(emptyList(), 0))
    val apps: StateFlow<AppsSnapshot> = _apps.asStateFlow()
    private val _contactIndex = MutableStateFlow(ContactIndex.EMPTY)
    val contactIndex: StateFlow<ContactIndex> = _contactIndex.asStateFlow()

    private var reloadJob: Job? = null
    private var contactsJob: Job? = null
    private var contactsObserverRegistered = false
    // Last list written to apps.cache. Compared separately from the published list because a reload cancelled after
    // publishing but before saving would otherwise leave the disk cache stale (the next scan sees "unchanged").
    @Volatile private var persisted: List<Item>? = null

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = loadContacts(delayMs = 1000)
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Debounced: an app update / Play Store batch sends a burst of broadcasts. force = icons may have changed.
            reloadApps(delayMs = 500, force = true)
        }
    }

    fun init(application: Application) {
        app = application
        // Registered once for the process lifetime (the Application is never torn down separately).
        app.registerReceiver(
            pkgReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
        )
        // Apps on adoptable/SD storage appear after boot without a PACKAGE_* broadcast; labels change with locale.
        app.registerReceiver(
            pkgReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
                addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
                addAction(Intent.ACTION_LOCALE_CHANGED)
            },
        )
        // Everything heavy is off the main thread: cached app list -> real scan, dictionary open, contacts load.
        reloadApps()
        scope.launch(Dispatchers.IO) { dictionary.warmUp() }
        if (contacts.hasPermission()) loadContacts()
    }

    /** Shows the cached list right away (cold start), then scans PackageManager and publishes only if it differs. */
    fun reloadApps(delayMs: Long = 0, force: Boolean = false) {
        // Cancel the previous reload so an older, slower PackageManager query can't land last with a stale list.
        reloadJob?.cancel()
        reloadJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (_apps.value.list.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { appRepo.readCachedList() }
                if (cached != null && _apps.value.list.isEmpty()) {
                    persisted = cached
                    _apps.value = AppsSnapshot(cached, _apps.value.gen + 1)
                }
            }
            val list = withContext(Dispatchers.IO) { appRepo.load() }
            val changed = list != _apps.value.list
            if (changed || force) {
                appRepo.invalidateIcons()
                _apps.value = AppsSnapshot(list, _apps.value.gen + 1)
            }
            if (list != persisted) {
                withContext(Dispatchers.IO) {
                    appRepo.saveList(list)
                    persisted = list
                    appRepo.pruneIconDisk(list.map { it.key })
                }
            }
        }
    }

    /** Called by a VM when it notices the contacts permission changed. */
    fun contactsPermissionChanged(granted: Boolean) {
        if (granted) loadContacts() else _contactIndex.value = ContactIndex.EMPTY
    }

    private fun loadContacts(delayMs: Long = 0) {
        contactsJob?.cancel()
        contactsJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (!contacts.hasPermission()) return@launch
            _contactIndex.value = withContext(Dispatchers.IO) { ContactIndex.build(contacts.loadRows()) }
            if (!contactsObserverRegistered) {
                contactsObserverRegistered = runCatching {
                    app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
                }.isSuccess
            }
        }
    }
}
