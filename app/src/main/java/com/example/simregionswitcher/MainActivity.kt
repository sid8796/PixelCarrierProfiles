package com.example.simregionswitcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.util.Log
import android.view.View
import android.widget.*
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var mapping: TextView
    private lateinit var carrier: TextView
    private lateinit var events: TextView
    private lateinit var liveRead: TextView
    private lateinit var shizukuStatus: TextView
    private lateinit var authorizeButton: Button
    private lateinit var imsButton: Button
    private lateinit var configButton: Button
    private lateinit var transportButton: Button
    private lateinit var bothButton: Button
    private lateinit var summary: TextView
    private var statusDialog: AlertDialog? = null
    private var statusText: TextView? = null
    private var statusTarget: ReadResultKey? = null
    private var simLabels: Map<Int, String> = emptyMap()
    private var lastReadRevision = 0L
    private var lastWaitingId = 0L
    private val readListener: () -> Unit = { renderReadState() }
    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread { if (!isDestroyed) updateShizukuStatus() } }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread { if (!isDestroyed) updateShizukuStatus() } }
    private val shizukuPermission = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == SHIZUKU_REQUEST) runOnUiThread {
            if (!isDestroyed) {
                record(if (grant == PackageManager.PERMISSION_GRANTED) "SHIZUKU_PERMISSION_GRANTED" else "SHIZUKU_PERMISSION_DENIED")
                updateShizukuStatus()
            }
        }
    }
    private var selectedSlot = 0
    private var phoneDenied = false
    private var records: List<SimRecord>? = null
    private var observedAt: String? = null
    private val history = ArrayDeque<String>()

    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppearanceContext.wrap(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReaderSession.initialize(this)
        RegionSession.initialize(this); ApnSession.initialize(this)
        selectedSlot = (savedInstanceState?.getInt("slot") ?: intent.getIntExtra("slot", 0)).takeIf { it in 0..1 } ?: 0
        lastReadRevision = (savedInstanceState?.getLong("lastReadRevision", 0) ?: 0).coerceAtMost(ReaderSession.state.revision)
        lastWaitingId = (savedInstanceState?.getLong("lastWaitingId", 0) ?: 0)
            .takeIf { it == ReaderSession.state.busy?.id } ?: 0
        phoneDenied = savedInstanceState?.getBoolean("phoneDenied", false) ?: false
        savedInstanceState?.getStringArrayList("events")?.takeLast(40)?.forEach { history.addLast(it) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun text(res: Int) = TextView(this).apply { setText(res); root.addView(this) }
        fun button(res: Int, action: () -> Unit): Button = Button(this).apply {
            setText(res); setOnClickListener { action() }; root.addView(this)
        }
        button(R.string.region_back) { finish() }
        text(R.string.app_name).textSize = 22f
        text(R.string.read_only)
        text(R.string.sim_status)
        info = text(R.string.not_checked)
        button(R.string.refresh) { refresh(true) }
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        group.addView(RadioButton(this).apply { id = R.id.sim_one; setText(R.string.sim_one) })
        group.addView(RadioButton(this).apply { id = R.id.sim_two; setText(R.string.sim_two) })
        group.check(if (selectedSlot == 1) R.id.sim_two else R.id.sim_one)
        root.addView(group)
        text(R.string.summary_heading)
        summary = text(R.string.summary_empty)
        bothButton = button(R.string.read_both) {
            if (!ReaderSession.state.running) {
                refresh(false); record("BOTH_SIM_READ_REQUESTED")
                ReaderSession.readBoth(this, records)
            }
        }
        button(R.string.open_ims_status) { showSelectedStatus() }
        text(R.string.mapping_heading)
        mapping = text(R.string.mapping_unavailable)
        text(R.string.read_heading)
        text(R.string.read_help)
        shizukuStatus = text(R.string.shizuku_not_running)
        authorizeButton = button(R.string.authorize_shizuku) { authorizeShizuku() }
        imsButton = button(R.string.open_ims_status) { showSelectedStatus() }
        configButton = button(R.string.read_config) {}.apply { visibility = View.GONE }
        text(R.string.transport_help)
        transportButton = button(R.string.read_transport) { startRead(ReadOperation.TRANSPORT) }
        liveRead = text(R.string.read_not_checked).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextIsSelectable(true)
        }
        carrier = text(R.string.native_backend).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextIsSelectable(true)
        }
        text(R.string.report_privacy)
        button(R.string.copy_report) { copyReport() }
        text(R.string.event_heading)
        events = text(R.string.not_checked).apply { setTextIsSelectable(true) }
        renderEvents()
        group.setOnCheckedChangeListener { _, checked ->
            selectedSlot = if (checked == R.id.sim_two) 1 else 0
            updateMapping()
        }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
        // A restored report is a dated snapshot, never a fresh package lookup.
        carrier.text = savedInstanceState?.getString("carrier") ?: getString(R.string.not_checked)
        liveRead.text = savedInstanceState?.getString("liveRead") ?: getString(R.string.read_not_checked)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(shizukuPermission)
        ReaderSession.listen(readListener)
        updateShizukuStatus()
        if (savedInstanceState == null && intent.getBooleanExtra("openStatus", false))
            window.decorView.post { if (!isDestroyed) showSelectedStatus() }
        val restoredSub = savedInstanceState?.getInt("statusSub", -1) ?: -1
        val restoredSlot = savedInstanceState?.getInt("statusSlot", -1) ?: -1
        if (restoredSub >= 0 && restoredSlot in 0..1)
            showStatusDialog(ReadResultKey(restoredSlot, restoredSub, ReadOperation.CAPABILITIES))
    }

    override fun onResume() {
        super.onResume()
        RegionSession.onVisible(this)
        // Re-read Android mapping after permission/settings changes. No stale SIM mapping is reused.
        refresh(false)
        updateShizukuStatus()
    }

    override fun onPause() {
        RegionSession.onHidden()
        super.onPause()
    }

    private fun record(event: String, error: Throwable? = null) {
        if (error == null) Log.i(TAG, event) else Log.w(TAG, "$event: ${error.javaClass.simpleName}")
        history.addLast(getString(R.string.event_line, DateFormat.getTimeInstance().format(Date()), event))
        while (history.size > 40) history.removeFirst()
        renderEvents()
    }

    private fun renderEvents() {
        if (::events.isInitialized) events.text = history.joinToString("\n").ifEmpty { getString(R.string.not_checked) }
    }

    private fun refresh(requestPermission: Boolean) {
        records = null; observedAt = null; simLabels = emptyMap()
        updateMapping()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            info.setText(if (phoneDenied) R.string.permission_denied else R.string.permission_needed)
            if (requestPermission) requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), PHONE_REQUEST)
            return
        }
        try {
            phoneDenied = false
            val manager = getSystemService(SubscriptionManager::class.java)
            if (manager == null) {
                info.setText(R.string.telephony_unavailable); record("SIM_SERVICE_UNAVAILABLE"); return
            }
            val subscriptions = manager.activeSubscriptionInfoList
            records = subscriptions?.map { SimRecord(it.simSlotIndex, it.subscriptionId) }
            simLabels = subscriptions.orEmpty().associate { it.subscriptionId to it.displayName.toString() }
            observedAt = DateFormat.getDateTimeInstance().format(Date())
            info.text = when {
                subscriptions == null -> getString(R.string.sim_unknown)
                subscriptions.isEmpty() -> getString(R.string.sim_empty)
                else -> subscriptions.sortedBy { it.simSlotIndex }.joinToString("\n\n") { sub ->
                    getString(R.string.sim_details_stage2, sub.simSlotIndex, sub.subscriptionId, sub.displayName,
                        sub.mccString ?: getString(R.string.unknown), sub.mncString ?: getString(R.string.unknown),
                        getString(if (sub.isEmbedded) R.string.esim else R.string.physical_sim))
                }
            }
            if (subscriptions != null) {
                info.text = getString(R.string.sim_snapshot, info.text, observedAt,
                    defaultSub(SubscriptionManager.getDefaultDataSubscriptionId()),
                    defaultSub(SubscriptionManager.getDefaultVoiceSubscriptionId()),
                    defaultSub(SubscriptionManager.getDefaultSmsSubscriptionId()))
            }
            record("SIM_REFRESH_COMPLETE")
        } catch (error: SecurityException) {
            records = null; observedAt = null
            info.setText(R.string.sim_access_denied); record("SIM_ACCESS_DENIED", error)
        } catch (error: RuntimeException) {
            records = null; observedAt = null
            info.text = getString(R.string.sim_error, error.javaClass.simpleName); record("SIM_READ_ERROR", error)
        } finally { updateMapping() }
    }

    private fun defaultSub(id: Int): String = if (SubscriptionManager.isValidSubscriptionId(id)) id.toString() else getString(R.string.unknown)

    private fun updateMapping() {
        val result = SlotResolver.resolve(selectedSlot, records)
        val status = when (result.state) {
            MappingState.UNAVAILABLE -> getString(R.string.mapping_unavailable)
            MappingState.EMPTY_SLOT -> getString(R.string.mapping_empty)
            MappingState.AMBIGUOUS -> getString(R.string.mapping_ambiguous)
            MappingState.OBSERVED -> getString(R.string.mapping_observed, result.subscriptionId, observedAt ?: getString(R.string.unknown))
        }
        renderStatusDialog()
        mapping.text = getString(R.string.mapping_result, selectedSlot, status)
        renderSummary()
    }

    private fun checkCarrier() {
        record("CARRIER_CHECK_START")
        carrier.text = CarrierDiagnostics(this).read { record(it) }
        record("CARRIER_CHECK_FINISHED")
    }

    private fun copyReport() {
        // Read the latest mapping first, including permission revocation and removed SIMs.
        refresh(false)
        val report = getString(R.string.report_body, Build.VERSION.SDK_INT, info.text, mapping.text, carrier.text, getString(R.string.live_report, shizukuStatus.text, getString(R.string.summary_report, summary.text, liveRead.text) + "\n\n" + getString(R.string.identity_report, ImsNumberSession.diagnostic) + "\n\n" + getString(R.string.region_report, RegionSession.code, RegionSession.activePlan?.let { "SIM ${it.slot + 1}, subId ${it.subId}, ${it.mcc}/${it.mnc}" } ?: getString(R.string.unknown))), events.text)
        try {
            val clipboard = getSystemService(ClipboardManager::class.java) ?: throw IllegalStateException()
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), report))
            record("REPORT_COPIED")
            Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show()
        } catch (error: RuntimeException) {
            record("REPORT_COPY_FAILED", error)
            Toast.makeText(this, R.string.report_copy_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PHONE_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) refresh(false)
            else {
                records = null; observedAt = null; updateMapping()
                phoneDenied = true; info.setText(R.string.permission_denied); record("PHONE_PERMISSION_DENIED")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        statusTarget?.let {
            outState.putInt("statusSlot", it.slot); outState.putInt("statusSub", it.subId)
        }
        outState.putInt("slot", selectedSlot)
        outState.putBoolean("phoneDenied", phoneDenied)
        outState.putLong("lastReadRevision", lastReadRevision)
        outState.putLong("lastWaitingId", lastWaitingId)
        outState.putString("carrier", carrier.text.toString())
        outState.putString("liveRead", liveRead.text.toString())
        outState.putStringArrayList("events", ArrayList(history))
    }

    private fun updateShizukuStatus() {
        if (!::shizukuStatus.isInitialized) return
        val error = ReaderSession.accessError()
        shizukuStatus.text = if (error == null) getString(R.string.shizuku_ready) else errorText(error)
    }

    private fun authorizeShizuku() {
        updateShizukuStatus()
        val error = ReaderSession.accessError()
        if (error == null) return
        if (error != "SHIZUKU_NOT_AUTHORIZED") {
            Toast.makeText(this, errorText(error), Toast.LENGTH_LONG).show(); return
        }
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this, R.string.shizuku_denied_help, Toast.LENGTH_LONG).show()
            } else Shizuku.requestPermission(SHIZUKU_REQUEST)
        } catch (_: RuntimeException) {
            shizukuStatus.setText(R.string.shizuku_unavailable)
            record("SHIZUKU_AUTHORIZATION_ERROR")
        }
    }

    private fun startRead(operation: ReadOperation) {
        if (ReaderSession.state.running) return
        refresh(false)
        val selected = SlotResolver.resolve(selectedSlot, records)
        if (selected.state != MappingState.OBSERVED || selected.subscriptionId == null) {
            ReaderSession.reject(operation, selectedSlot, "SELECTION_CHANGED"); return
        }
        record("${readPrefix(operation)}_REQUESTED")
        ReaderSession.read(this, operation, selectedSlot, selected.subscriptionId)
    }

    private fun renderReadState() {
        if (!::liveRead.isInitialized || isDestroyed) return
        val state = ReaderSession.state
        state.latest?.takeIf { state.revision > lastReadRevision }?.let {
            lastReadRevision = state.revision
            val prefix = readPrefix(it.request.operation)
            record(if (it.result.error != null) "${prefix}_ERROR_${it.result.error}"
                else if (it.late) "${prefix}_LATE_COMPLETE" else "${prefix}_COMPLETE")
        }
        val pending = state.busy
        if (pending != null && state.waitingLong && pending.id != lastWaitingId) {
            lastWaitingId = pending.id
            record("READER_REPLY_WAITING")
        }
        val busy = state.running
        bothButton.isEnabled = !busy
        imsButton.isEnabled = !busy; configButton.isEnabled = !busy; transportButton.isEnabled = !busy; authorizeButton.isEnabled = !busy
        renderSummary()
        renderStatusDialog()
        val sections = mutableListOf<String>()
        if (state.privateBusy && !RegionSession.busy && !RegionSession.blocksReads) sections += getString(if (ImsNumberSession.waitingLong) R.string.identity_wait_long else R.string.identity_wait)
        if (state.cacheError) sections += getString(R.string.cache_error)
        if (state.batchTotal > 0) sections += getString(
            if (state.batchActive) R.string.batch_running else if (state.batchError != null) R.string.batch_stopped else R.string.batch_complete,
            state.batchDone, state.batchTotal)
        state.batchError?.let { sections += errorText(it) }
        if (pending != null) {
            sections += getString(R.string.pending_header,
                readTitle(pending.operation),
                pending.slot, pending.subId, DateFormat.getDateTimeInstance().format(Date(pending.startedAt)),
                getString(if (state.waitingLong) R.string.read_waiting_long else R.string.read_running))
            state.pendingDetail?.let { sections += getString(R.string.pending_code, it) }
        }
        state.archive.entries.sortedWith(compareBy({ it.key.slot }, { it.key.subId }, { it.key.operation.ordinal }))
            .forEach { sections += formatOutcome(it.value) }
        liveRead.text = sections.joinToString("\n\n").ifEmpty { getString(R.string.read_not_checked) }
    }

    private fun renderSummary() {
        if (!::summary.isInitialized) return
        val snapshots = ReaderSession.state.archive.entries
        summary.text = (0..1).joinToString("\n\n") { slot ->
            val current = SlotResolver.resolve(slot, records)
            val key = if (current.state == MappingState.OBSERVED && current.subscriptionId != null)
                ReadResultKey(slot, current.subscriptionId, ReadOperation.TRANSPORT) else null
            val outcome = snapshots.filter { it.key.slot == key?.slot && it.key.subId == key?.subId &&
                it.key.operation in listOf(ReadOperation.TRANSPORT, ReadOperation.CAPABILITIES) }
                .maxByOrNull { it.requestId }?.value
            val label = key?.let { simLabels[it.subId] } ?: getString(R.string.summary_sim)
            val status = when {
                key == null -> getString(if (current.state == MappingState.EMPTY_SLOT) R.string.summary_no_sim else R.string.summary_no_mapping)
                outcome == null -> getString(R.string.summary_empty)
                outcome.result.error != null -> getString(R.string.summary_error)
                else -> outcome.result.transport?.let { t ->
                    when (t.state) {
                        0 -> getString(R.string.mmtel_unregistered)
                        1 -> getString(R.string.mmtel_registering)
                        else -> getString(R.string.summary_registered, getString(when (t.access) {
                            ImsAccess.CELLULAR -> R.string.summary_cellular
                            ImsAccess.WLAN -> R.string.summary_wlan
                            else -> R.string.unknown
                        }))
                    }
                } ?: getString(R.string.unknown)
            }
            val dated = if (outcome == null) status else getString(R.string.summary_dated, status,
                DateFormat.getDateTimeInstance().format(Date(outcome.finishedAt)),
                getString(if (outcome.restored) R.string.summary_saved else R.string.summary_current))
            getString(R.string.summary_row, slot + 1, label, dated)
        }
    }
    private fun showSelectedStatus() {
        refresh(false)
        val mapping = SlotResolver.resolve(selectedSlot, records)
        val subId = mapping.subscriptionId
        if (mapping.state != MappingState.OBSERVED || subId == null) {
            Toast.makeText(this, R.string.read_selection_required, Toast.LENGTH_LONG).show()
            return
        }
        showStatusDialog(ReadResultKey(selectedSlot, subId, ReadOperation.CAPABILITIES))
    }

    private fun showStatusDialog(key: ReadResultKey) {
        statusDialog?.dismiss()
        val content = TextView(this).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            textSize = 17f
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.capability_window_title, key.slot + 1))
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.capability_refresh, null)
            .setNegativeButton(R.string.close_window, null)
            .setNeutralButton(R.string.identity_read, null)
            .create()
        ImsNumberSession.show(ImsNumberSession.Target(key.slot, key.subId))
        statusDialog = dialog; statusText = content; statusTarget = key
        dialog.setOnDismissListener {
            if (statusDialog === dialog) {
                ImsNumberSession.close()
                statusDialog = null; statusText = null; statusTarget = null
            }
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!ReaderSession.state.running) {
                refresh(false)
                val mapping = SlotResolver.resolve(key.slot, records)
                if (mapping.state != MappingState.OBSERVED || mapping.subscriptionId != key.subId) {
                    content.setText(R.string.capability_sim_changed)
                } else {
                    record("CAPABILITIES_READ_REQUESTED")
                    ReaderSession.read(this, ReadOperation.CAPABILITIES, key.slot, key.subId)
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (!ReaderSession.state.running) {
                refresh(false)
                val mapping = SlotResolver.resolve(key.slot, records)
                if (mapping.state != MappingState.OBSERVED || mapping.subscriptionId != key.subId)
                    content.setText(R.string.capability_sim_changed)
                else {
                    record("IMS_NUMBER_READ_REQUESTED")
                    ImsNumberSession.read(this, ImsNumberSession.Target(key.slot, key.subId))
                }
            }
        }
        renderStatusDialog()
    }

    private fun renderStatusDialog() {
        val key = statusTarget ?: return
        val state = ReaderSession.state
        statusDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !state.running
        statusDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = !state.running
        val outcome = state.archive.entries.firstOrNull { it.key == key }?.value
        val parts = mutableListOf(getString(R.string.capability_identity, key.slot + 1, key.subId))
        if (records != null) {
            val current = SlotResolver.resolve(key.slot, records)
            if (current.state != MappingState.OBSERVED || current.subscriptionId != key.subId)
                parts += getString(R.string.capability_sim_changed)
        }
        val busy = state.busy
        if (busy != null && busy.slot == key.slot && busy.subId == key.subId && busy.operation == key.operation)
            parts += getString(if (state.waitingLong) R.string.read_waiting_long else R.string.read_running)
        if (outcome == null) parts += getString(R.string.capability_not_checked)
        else {
            parts += getString(R.string.capability_snapshot_time,
                DateFormat.getDateTimeInstance().format(Date(outcome.finishedAt)))
            val body = if (outcome.result.error != null)
                getString(R.string.read_unknown, errorText(outcome.result.error),
                    getString(R.string.read_error_details, outcome.result.error, outcome.result.detail ?: getString(R.string.read_no_detail)))
            else formatCapabilities(outcome.result, false)
            parts += getString(if (outcome.restored) R.string.saved_snapshot else R.string.session_snapshot, body)
        }
        parts += formatImsNumber(key)
        parts += getString(R.string.capability_window_limit)
        statusText?.text = parts.joinToString("\n\n")
    }

    private fun formatImsNumber(key: ReadResultKey): String {
        val target = ImsNumberSession.Target(key.slot, key.subId)
        if (ImsNumberSession.busy == target)
            return getString(if (ImsNumberSession.waitingLong) R.string.identity_wait_long else R.string.identity_wait)
        val result = if (ImsNumberSession.isVisible(target)) ImsNumberSession.result else null
        if (result == null) return getString(R.string.identity_not_checked)
        val text = when (result.status) {
            "FOUND" -> getString(R.string.identity_found, result.numbers.joinToString("\n"))
            "NOT_REGISTERED" -> getString(R.string.identity_no_registration)
            "NO_NUMBER" -> getString(R.string.identity_no_number)
            "NO_URIS" -> getString(R.string.identity_no_uris)
            "TIMEOUT" -> getString(R.string.identity_timeout)
            "STATE_CHANGED", "MAPPING_CHANGED" -> getString(R.string.identity_changed)
            "CLEANUP_FAILED" -> getString(R.string.identity_cleanup_failed)
            else -> getString(R.string.identity_unavailable, result.status)
        }
        return getString(R.string.identity_view, text,
            DateFormat.getDateTimeInstance().format(Date(ImsNumberSession.receivedAt)),
            getString(R.string.identity_limit))
    }
    private fun availability(value: Boolean?): String = getString(when (value) {
        true -> R.string.capability_available
        false -> R.string.capability_unavailable
        null -> R.string.unknown
    })

    private fun formatCapabilities(result: ParsedRead, detailed: Boolean): String {
        val caps = result.capabilities ?: return getString(R.string.read_invalid_reply)
        val registration = getString(when (result.transport?.state) {
            0 -> R.string.mmtel_unregistered
            1 -> R.string.mmtel_registering
            2 -> R.string.mmtel_registered
            else -> R.string.unknown
        })
        val rows = mutableListOf(getString(R.string.capability_registration_row, registration))
        val voiceRows = listOf(R.string.capability_lte to "voice_lte", R.string.capability_nr to "voice_nr",
            R.string.capability_cross to "voice_cross_sim", R.string.capability_wifi to "voice_wifi")
        voiceRows.forEach { (label, key) ->
            rows += getString(R.string.config_flag_row, getString(label), availability(caps.values[key]))
        }
        rows += getString(R.string.config_flag_row, getString(R.string.capability_video), availability(CapabilityRules.aggregate(caps, "video")))
        rows += getString(R.string.config_flag_row, getString(R.string.capability_ut), availability(CapabilityRules.aggregate(caps, "ut")))
        if (detailed) {
            rows += getString(R.string.capability_details)
            for (probe in CapabilityRules.probes) {
                val reason = caps.errors[probe.key]
                rows += getString(R.string.capability_probe_row, probe.key, availability(caps.values[probe.key]), reason ?: getString(R.string.read_no_detail))
            }
        }
        return rows.joinToString("\n")
    }
    private fun openCarrier() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(ReaderProtocol.PACKAGE)
            if (intent == null) {
                Toast.makeText(this, R.string.read_target_missing, Toast.LENGTH_LONG).show()
                record("CARRIER_OPEN_UNAVAILABLE")
                return
            }
            startActivity(intent)
            record("CARRIER_OPENED")
        } catch (_: RuntimeException) {
            record("CARRIER_OPEN_FAILED")
            Toast.makeText(this, R.string.carrier_open_failed, Toast.LENGTH_LONG).show()
        }
    }
    private fun formatOutcome(outcome: ReaderSession.Outcome): String {
        val request = outcome.request
        val title = readTitle(request.operation)
        val header = getString(R.string.read_result_header, title, request.slot, request.subId,
            DateFormat.getDateTimeInstance().format(Date(request.startedAt)),
            DateFormat.getDateTimeInstance().format(Date(outcome.finishedAt)))
        val result = outcome.result
        val body = when {
            result.error != null -> getString(R.string.read_unknown, errorText(result.error), getString(R.string.read_error_details, result.error, result.detail ?: getString(R.string.read_no_detail)))
            request.operation == ReadOperation.CAPABILITIES -> formatCapabilities(result, true)
            request.operation == ReadOperation.TRANSPORT -> {
                val snapshot = result.transport
                if (snapshot == null) getString(R.string.read_invalid_reply)
                else getString(R.string.transport_body,
                    getString(when (snapshot.state) {
                        0 -> R.string.mmtel_unregistered
                        1 -> R.string.mmtel_registering
                        else -> R.string.mmtel_registered
                    }),
                    getString(when (snapshot.access) {
                        ImsAccess.CELLULAR -> R.string.transport_cellular
                        ImsAccess.WLAN -> R.string.transport_wlan
                        ImsAccess.NOT_APPLICABLE -> R.string.transport_na
                        ImsAccess.UNKNOWN -> R.string.unknown
                    }), snapshot.rawTransport?.toString() ?: getString(R.string.unknown))
            }
            request.operation == ReadOperation.IMS -> when (result.registered) {
                true -> getString(R.string.ims_registered)
                false -> getString(R.string.ims_not_registered)
                null -> getString(R.string.read_unknown, getString(R.string.read_invalid_reply), getString(R.string.read_no_detail))
            }
            else -> {
                val labels = listOf(R.string.flag_volte, R.string.flag_vowifi, R.string.flag_vt, R.string.flag_ut, R.string.flag_cross_sim)
                ReaderProtocol.keys.mapIndexed { index, key ->
                    val value = result.flags?.get(key)
                    getString(R.string.config_flag_row, getString(labels[index]), getString(when (value) {
                        true -> R.string.flag_true
                        false -> R.string.flag_false
                        null -> R.string.unknown
                    }))
                }.joinToString("\n")
            }
        }
        val timed = if (outcome.late) getString(R.string.read_late_result, body) else body
        val status = getString(if (outcome.restored) R.string.saved_snapshot else R.string.session_snapshot, timed)
        return getString(R.string.read_section, header, status,
            getString(when (request.operation) {
                ReadOperation.IMS -> R.string.ims_result_limit
                ReadOperation.CONFIG -> R.string.config_result_limit
                ReadOperation.CAPABILITIES -> R.string.capability_limit
                ReadOperation.TRANSPORT -> R.string.transport_result_limit
            }))
    }

    private fun readPrefix(operation: ReadOperation): String = when (operation) {
        ReadOperation.IMS -> "IMS_READ"
        ReadOperation.CONFIG -> "CONFIG_READ"
        ReadOperation.CAPABILITIES -> "CAPABILITIES_READ"
        ReadOperation.TRANSPORT -> "TRANSPORT_READ"
    }
    private fun readTitle(operation: ReadOperation): String = getString(when (operation) {
        ReadOperation.IMS -> R.string.read_ims_title
        ReadOperation.CONFIG -> R.string.read_config_title
        ReadOperation.CAPABILITIES -> R.string.capability_title
        ReadOperation.TRANSPORT -> R.string.read_transport_title
    })

    private fun errorText(code: String): String = getString(when (code) {
        "PLATFORM_ACCESS_DENIED" -> R.string.platform_access_denied
        "PLATFORM_API_UNAVAILABLE", "PLATFORM_SERVICE_UNAVAILABLE" -> R.string.platform_unavailable
        "PLATFORM_REPLY_TIMEOUT" -> R.string.platform_timeout
        "PLATFORM_READ_ERROR", "INVALID_REGISTRATION_STATE" -> R.string.platform_invalid
        "IMS_STATE_CHANGED" -> R.string.platform_state_changed
        "SHIZUKU_NOT_RUNNING" -> R.string.shizuku_not_running
        "SHIZUKU_TOO_OLD" -> R.string.shizuku_too_old
        "SHIZUKU_NOT_AUTHORIZED" -> R.string.shizuku_not_authorized
        "SHIZUKU_UNAVAILABLE" -> R.string.shizuku_unavailable
        "UNREVIEWED_VERSION" -> R.string.read_version_blocked
        "TARGET_NOT_RUNNING", "TARGET_PROCESS_UNKNOWN" -> R.string.read_target_process_required
        "TARGET_DISABLED" -> R.string.read_target_disabled
        "TARGET_NOT_FOUND" -> R.string.read_target_missing
        "READER_NOT_DECLARED" -> R.string.read_component_missing
        "SELECTION_CHANGED", "INVALID_SELECTION" -> R.string.read_selection_required
        "APP_SELECTION_CHANGED" -> R.string.read_app_mapping_changed
        "REMOTE_SELECTION_CHANGED" -> R.string.read_remote_mapping_changed
        "REMOTE_MAPPING_UNAVAILABLE", "REMOTE_MAPPING_INVALID_TYPE", "REMOTE_MAPPING_AMBIGUOUS" -> R.string.read_remote_mapping_unavailable
        "STALE_HELPER" -> R.string.read_helper_outdated
        "READER_ERROR" -> R.string.read_remote_error
        "READER_TIMEOUT_PENDING", "REMOTE_REQUEST_PENDING" -> R.string.read_remote_pending
        "BRIDGE_NOT_CONNECTED", "BRIDGE_DIED" -> R.string.read_bridge_unavailable
        "ACCESS_OR_ARGUMENT_REJECTED", "TARGET_ACCESS_DENIED" -> R.string.read_access_denied
        "CANCELLED" -> R.string.read_cancelled
        "EMPTY_REPLY", "MISSING_RESULT", "INVALID_RESULT_TYPE", "MALFORMED_REPLY" -> R.string.read_invalid_reply
        else -> R.string.read_transport_error
    })

    override fun onDestroy() {
        statusDialog?.dismiss()
        ReaderSession.remove(readListener)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(shizukuPermission)
        super.onDestroy()
    }
    companion object {
        private const val PHONE_REQUEST = 11
        private const val TAG = "SIMRegionReadOnly"
        private const val SHIZUKU_REQUEST = 21
    }
}













