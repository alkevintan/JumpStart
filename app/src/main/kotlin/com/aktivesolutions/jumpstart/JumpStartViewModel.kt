package com.aktivesolutions.jumpstart

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aktivesolutions.jumpstart.data.ActionLauncher
import com.aktivesolutions.jumpstart.data.AppRepository
import com.aktivesolutions.jumpstart.data.ContactRepository
import com.aktivesolutions.jumpstart.data.Dictionary
import com.aktivesolutions.jumpstart.data.Prefs
import com.aktivesolutions.jumpstart.data.SettingsCatalog
import com.aktivesolutions.jumpstart.edge.EdgeBarController
import com.aktivesolutions.jumpstart.edge.ShortcutAccessibilityService
import com.aktivesolutions.jumpstart.edge.SplitMode
import com.aktivesolutions.jumpstart.search.Converter
import com.aktivesolutions.jumpstart.search.ContactIndex
import com.aktivesolutions.jumpstart.search.DictEntry
import com.aktivesolutions.jumpstart.search.Item
import com.aktivesolutions.jumpstart.search.Kind
import com.aktivesolutions.jumpstart.search.SearchEngine
import com.aktivesolutions.jumpstart.search.SearchResult
import com.aktivesolutions.jumpstart.search.Tags
import com.aktivesolutions.jumpstart.search.dictFor
import com.aktivesolutions.jumpstart.search.isDictQuery
import com.aktivesolutions.jumpstart.update.CheckResult
import com.aktivesolutions.jumpstart.update.UpdateState
import com.aktivesolutions.jumpstart.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SEARCH, SETTINGS }

@Immutable
data class UiState(
    val screen: Screen = Screen.HOME,
    val query: String = "",
    val apps: List<Item> = emptyList(),
    val suggested: List<Item> = emptyList(),
    val recents: List<String> = emptyList(),
    val result: SearchResult = SearchResult(),
    val dict: DictEntry? = null,
    val copiedValue: String? = null,
    val toast: String = "",
    val scheme: String = "Purple",
    val themedIcons: Boolean = true,
    val contactsGranted: Boolean = false,
    val edgeBar: Boolean = false,
    val edgeSide: String = "Right",
    /** "Display over other apps" granted: required for the edge bar. */
    val overlayGranted: Boolean = false,
    val splitLaunch: Boolean = true,
    /** Alt+Space accessibility service switched on. */
    val shortcutEnabled: Boolean = false,
    val showFullDict: Boolean = false,
    /** App whose tags are being edited (long-press dialog), or null. */
    val editingTags: Item? = null,
    /** Latest GitHub Release / download progress, shown in Settings and the launch-time prompt. */
    val update: UpdateState = UpdateState(),
)

/** A search to run: [q] is the trimmed query; [epoch] bumps to force a re-run when the data (apps, contacts) changes. */
private data class SearchReq(val q: String, val epoch: Int = 0)

class JumpStartViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app
    private val prefs = Core.prefs
    val appRepo = Core.appRepo
    private val contacts = Core.contacts
    private val dictionary = Core.dictionary
    // Application context by default; the overlay activity swaps in its own so launches have a source activity (needed
    // for split-screen placement), and resets it in onDestroy so the VM never outlives it holding the Activity.
    var launcher = ActionLauncher(ctx, appRepo)
        private set

    fun useActivityContext(activity: Context?) {
        launcher = ActionLauncher(activity ?: ctx, appRepo)
    }

    private val _state = MutableStateFlow(
        UiState(recents = prefs.recents, scheme = prefs.scheme, themedIcons = prefs.themedIcons, contactsGranted = contacts.hasPermission(),
            edgeBar = prefs.edgeBar, edgeSide = prefs.edgeSide, splitLaunch = prefs.splitLaunch, overlayGranted = Settings.canDrawOverlays(app),
            shortcutEnabled = ShortcutAccessibilityService.isEnabled(app),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var toastJob: Job? = null
    private var preloadJob: Job? = null

    // Search requests; collectLatest cancels a stale search when a newer query arrives, and the StateFlow
    // drops a request equal to the current one, so an unchanged query is never recomputed.
    private val searchReq = MutableStateFlow(SearchReq(""))
    // Trimmed query that _state.value.result was computed for (main thread only). Results are async, so
    // onEnter uses this to avoid acting on the previous keystroke's top hit.
    private var resultQ = ""

    // Contact index lives in Core (one owner, one observer); this is just the latest copy for ranking.
    @Volatile private var contactIndex = ContactIndex.EMPTY

    // One-shot "close the overlay" signal, emitted after a successful launch from the overlay.
    private val _dismiss = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dismiss = _dismiss.asSharedFlow()

    init {
        viewModelScope.launch { searchReq.collectLatest { process(it) } }
        // The app list and contact index are loaded and kept fresh by Core; each VM only merges tags and searches.
        viewModelScope.launch {
            Core.apps.collect { snap ->
                val (list, sug) = tagged(snap.list)
                _state.update { it.copy(apps = list, suggested = sug) }
                bumpSearch()
                preloadIcons()
            }
        }
        viewModelScope.launch {
            Core.contactIndex.collect { idx ->
                contactIndex = idx
                bumpSearch()
            }
        }
    }

    private fun bumpSearch() {
        // The shown result predates the data change: make onEnter re-rank synchronously until the new pass lands.
        resultQ = ""
        if (_state.value.screen == Screen.SEARCH) searchReq.update { it.copy(epoch = it.epoch + 1) }
    }

    /** Merges the user's tags (keyed by component) into freshly loaded app items. */
    private fun withTags(list: List<Item>): List<Item> {
        val tags = prefs.appTags()
        if (tags.isEmpty()) return list
        return list.map { i -> tags[i.key]?.let { i.withTags(it) } ?: i }
    }

    // Bumped (main thread) by every tag edit; lets an in-flight reload notice that its merged tags went stale.
    private var tagsVersion = 0

    /**
     * Main thread. [raw] with the current tags merged in, plus its suggestions (ranked on Default). Redone if a tag
     * edit landed during that suspension, otherwise the reload would publish the pre-edit tags over reindexTags().
     */
    private suspend fun tagged(raw: List<Item>): Pair<List<Item>, List<Item>> {
        while (true) {
            val v = tagsVersion
            val list = withTags(raw)
            val sug = withContext(Dispatchers.Default) { suggestedFrom(list) }
            if (v == tagsVersion) return list to sug
        }
    }

    private fun suggestedFrom(list: List<Item>): List<Item> {
        val counts = prefs.launchCounts()
        // sortedBy is stable, so never-launched apps keep their alphabetical order.
        return list.sortedByDescending { counts[it.key] ?: 0 }.take(8)
    }

    /** Real icons only show when themed icons are off; render the suggested apps' ones ahead of time. */
    private fun preloadIcons() {
        val s = _state.value
        if (s.themedIcons) return
        val keys = s.suggested.map { it.key }
        val d = ctx.resources.displayMetrics.density
        // Same sizes the UI asks for: Home tiles 56dp, Search suggestions 52dp.
        val sizes = listOf(56, 52).map { (it * d + 0.5f).toInt() }
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch(Dispatchers.IO) { appRepo.preload(keys, sizes) }
    }

    // ---- navigation ----
    fun openSearch(initial: String = "") {
        // Goes through refreshPermission so a grant noticed here also builds the contact index (setting
        // contactsGranted directly would make the later refreshPermission see "no change" and never load it).
        // Already on SEARCH (a second hardware key arrived before recomposition swapped screens): append, don't overwrite.
        val q = _state.value.let { if (it.screen == Screen.SEARCH) it.query + initial else initial }
        _state.update { it.copy(screen = Screen.SEARCH, query = q) }
        refreshPermission()
        searchReq.update { it.copy(q = q.trim()) }
    }

    fun goHome() {
        searchReq.update { it.copy(q = "") }
        _state.update { it.copy(screen = Screen.HOME, query = "", result = SearchResult(), dict = null, showFullDict = false, editingTags = null) }
        resultQ = ""
    }

    fun openSettings() = _state.update { it.copy(screen = Screen.SETTINGS) }

    fun refreshPermission() {
        // Returning from Settings > Install unknown apps may complete an OTA install started earlier.
        Updater.resumePending(ctx)
        val sc = ShortcutAccessibilityService.isEnabled(ctx)
        if (sc != _state.value.shortcutEnabled) _state.update { it.copy(shortcutEnabled = sc) }
        val o = Settings.canDrawOverlays(ctx)
        if (o != _state.value.overlayGranted) _state.update { it.copy(overlayGranted = o) }
        val g = contacts.hasPermission()
        if (g != _state.value.contactsGranted) {
            _state.update { it.copy(contactsGranted = g) }
            Core.contactsPermissionChanged(g)
        }
    }

    // ---- search ----
    fun setQuery(q: String) {
        val t = q.trim()
        if (t.isEmpty()) {
            _state.update { it.copy(query = q, result = SearchResult(), dict = null) }
            resultQ = ""
        } else {
            _state.update { it.copy(query = q) }
        }
        searchReq.update { it.copy(q = t) }
    }

    /** Runs on the collector's (cancellable) coroutine: ranking on Default, dictionary lookup on IO. */
    private suspend fun process(req: SearchReq) {
        val q = req.q
        if (q.isEmpty()) {
            _state.update { if (it.result == SearchResult() && it.dict == null) it else it.copy(result = SearchResult(), dict = null) }
            resultQ = ""
            return
        }
        val lower = q.lowercase()
        val apps = _state.value.apps
        val result = withContext(Dispatchers.Default) { rank(q, apps) }
        applyResult(q, result)
        if (result.answer != null || !isDictQuery(lower)) {
            _state.update { if (it.dict == null) it else it.copy(dict = null) }
            return
        }
        val dict = withContext(Dispatchers.IO) { dictFor(lower, dictionary) }
        _state.update { it.copy(dict = dict) }
    }

    /** Calculator / converter answer first (digit check keeps it free for plain words); an answer suppresses contacts. */
    private fun rank(q: String, apps: List<Item>): SearchResult {
        val answer = Converter.answerFor(q)
        val people = if (answer != null) emptyList() else contactIndex.search(q.lowercase())
        return SearchEngine.search(q, apps, people, SettingsCatalog.items, answer)
    }

    /** Main thread. A previous dictionary card is kept only while it can still be the answer, so it doesn't linger. */
    private fun applyResult(q: String, result: SearchResult) {
        val lower = q.lowercase()
        _state.update { s ->
            s.copy(result = result, dict = s.dict?.takeIf { result.answer == null && it.word.startsWith(lower) })
        }
        resultQ = q
    }

    fun clearQuery() = setQuery("")

    private fun remember() = _state.value.query.takeIf { it.isNotBlank() }?.let {
        prefs.addRecent(it)
        _state.update { s -> s.copy(recents = prefs.recents) }
    }

    // ---- actions ----
    fun toast(msg: String) {
        toastJob?.cancel()
        _state.update { it.copy(toast = msg) }
        toastJob = viewModelScope.launch {
            delay(1800)
            _state.update { it.copy(toast = "") }
        }
    }

    /** Set by SearchOverlayActivity on its own VM: launches from the overlay go beside the app underneath. */
    var fromOverlay = false

    /** Set by the overlay activity: how to launch right now (needs its isInMultiWindowMode). Cleared in onDestroy. */
    var splitDecider: (() -> SplitMode)? = null

    /** After a successful launch from the overlay: the search has done its job, so ask the activity to close. */
    private fun dismissOverlay() { if (fromOverlay) _dismiss.tryEmit(Unit) }

    /**
     * Home resumed: the overlay (a separate VM sharing Core.prefs) may have changed launch counts, tags or recents.
     * Cheap: re-merge tags + suggestions from the shared prefs and re-read recents.
     */
    fun syncFromPrefs() {
        _state.update { it.copy(recents = prefs.recents) }
        reindexTags()
    }

    fun launchApp(item: Item) {
        remember()
        // Bookkeeping below runs whichever way the launch is handed off.
        val mode = if (fromOverlay) splitDecider?.invoke() ?: SplitMode.PLAIN else SplitMode.PLAIN
        val ok = when (mode) {
            // The service docks the previous app, then launches into the other pane after a delay; the overlay finishes now.
            SplitMode.TOGGLE_THEN_ADJACENT -> appRepo.launchIntent(item.key)?.let { ShortcutAccessibilityService.launchSplit(it) } ?: false
            else -> launcher.openApp(item.key, mode == SplitMode.ADJACENT)
        }
        if (ok) {
            prefs.bumpLaunch(item.key)
            _state.update { it.copy(suggested = suggestedFrom(it.apps)) }
            dismissOverlay()
        } else toast("Couldn't open ${item.title}")
    }

    fun primary(item: Item) {
        when (item.kind) {
            Kind.APP -> launchApp(item)
            Kind.CONTACT -> { remember(); if (launcher.call(item.key)) dismissOverlay() else toast("No dialer available") }
            Kind.SETTING -> { remember(); if (launcher.openSetting(item.key)) dismissOverlay() else toast("Couldn't open settings") }
        }
    }

    fun message(item: Item) {
        remember()
        if (launcher.message(item.key)) dismissOverlay() else toast("No messaging app available")
    }

    fun copyAnswer() {
        val a = _state.value.result.answer ?: return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("JumpStart", a.value))
        remember()
        _state.update { it.copy(copiedValue = a.value) }
        toast("Copied to clipboard")
    }

    fun openCalculator() {
        remember()
        if (launcher.calculator(_state.value.apps)) dismissOverlay() else toast("No calculator app found")
    }

    fun web(kind: String) {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        remember()
        val ok = when (kind) {
            "web" -> launcher.webSearch(q)
            "store" -> launcher.playStore(q)
            "maps" -> launcher.maps(q)
            else -> launcher.wikipedia(q)
        }
        if (ok) dismissOverlay() else toast("No app found to handle that")
    }

    /** IME Search / Enter: top hit, else copy the answer, else web search. */
    fun onEnter() {
        syncResult()
        val r = _state.value.result
        when {
            r.top != null -> primary(r.top)
            r.answer != null -> copyAnswer()
            _state.value.query.isNotBlank() -> web("web")
        }
    }

    // The async pass may not have caught up with the last keystroke yet: rank synchronously (in-memory,
    // the same work the pre-refactor instant pass did on every keystroke) so Enter never fires a stale top hit.
    private fun syncResult() {
        val q = _state.value.query.trim()
        if (q.isNotEmpty() && q != resultQ) applyResult(q, rank(q, _state.value.apps))
    }

    /** Ctrl+Enter: message the top hit if it's a contact (resynced like [onEnter]). */
    fun messageTop() {
        syncResult()
        _state.value.result.top?.takeIf { it.kind == Kind.CONTACT }?.let(::message)
    }

    /** Ctrl+Shift+C: copy the answer if there is one (resynced like [onEnter]). */
    fun copyTop() {
        syncResult()
        copyAnswer()
    }

    fun showFullDict(show: Boolean) = _state.update { it.copy(showFullDict = show) }

    // ---- app tags ----
    // Prefilled from prefs (the source of truth), not the tapped Item, which may be a snapshot from before an edit.
    fun editTags(item: Item?) = _state.update { it.copy(editingTags = item?.let { i -> i.withTags(prefs.appTags()[i.key].orEmpty()) }) }

    /** [raw] is the comma-separated text from the dialog. Re-indexes the item and refreshes the current search. */
    fun saveTags(item: Item, raw: String) {
        val tags = Tags.joinTags(Tags.parseTags(raw))
        prefs.setAppTags(item.key, tags)
        reindexTags()
        _state.update { it.copy(editingTags = null) }
        toast(if (tags.isEmpty()) "Tags removed" else "Tags saved")
    }

    fun clearAppTags() {
        prefs.clearAppTags()
        reindexTags()
        toast("App tags cleared")
    }

    private fun reindexTags() {
        tagsVersion++
        val tags = prefs.appTags()
        _state.update { s ->
            val apps = s.apps.map { i -> i.withTags(tags[i.key].orEmpty()) }
            s.copy(apps = apps, suggested = suggestedFrom(apps))
        }
        bumpSearch() // epoch change forces the current query to be re-ranked
    }

    // ---- updates ----
    private var autoChecked = false

    /** Once per process (cold launch): a newer release raises the in-app prompt. */
    fun autoCheckUpdate() {
        if (autoChecked) return
        autoChecked = true
        checkUpdate(auto = true)
    }

    fun checkUpdate(auto: Boolean = false) {
        if (_state.value.update.checking) return
        _state.update { it.copy(update = UpdateState(checking = true)) }
        viewModelScope.launch {
            val result = Updater.check(Updater.installedVersion(ctx))
            _state.update { s ->
                s.copy(update = when (result) {
                    is CheckResult.New -> UpdateState(available = result.release, prompt = auto)
                    is CheckResult.UpToDate -> UpdateState(upToDate = true)
                    is CheckResult.Error -> UpdateState(error = result.message)
                })
            }
        }
    }

    fun installUpdate() {
        val release = _state.value.update.available ?: return
        _state.update { it.copy(update = it.update.copy(downloading = true, progress = -1, error = null, prompt = false)) }
        viewModelScope.launch {
            val file = Updater.download(ctx, release.apkUrl) { p ->
                _state.update { s -> s.copy(update = s.update.copy(progress = p)) }
            }
            if (file == null) {
                _state.update { s -> s.copy(update = s.update.copy(downloading = false, error = "download failed")) }
                return@launch
            }
            val launched = Updater.install(ctx, file)
            _state.update { s ->
                s.copy(update = s.update.copy(
                    downloading = false, progress = 100,
                    error = if (launched) null else "allow installs from this source to continue",
                ))
            }
        }
    }

    fun dismissUpdate() = _state.update { it.copy(update = it.update.copy(prompt = false)) }

    // ---- settings ----
    fun setScheme(s: String) {
        prefs.scheme = s
        _state.update { it.copy(scheme = s) }
    }

    fun setThemedIcons(on: Boolean) {
        prefs.themedIcons = on
        _state.update { it.copy(themedIcons = on) }
    }

    fun setEdgeBar(on: Boolean) {
        prefs.edgeBar = on
        _state.update { it.copy(edgeBar = on) }
        EdgeBarController.refresh()
    }

    fun setSplitLaunch(on: Boolean) {
        prefs.splitLaunch = on
        _state.update { it.copy(splitLaunch = on) }
    }

    fun setEdgeSide(side: String) {
        prefs.edgeSide = side
        _state.update { it.copy(edgeSide = side) }
        EdgeBarController.refresh()
    }

    fun clearRecents() {
        prefs.recents = emptyList()
        _state.update { it.copy(recents = emptyList()) }
        toast("Recent searches cleared")
    }
}
