package com.example.simregionswitcher

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Retained in the app process: rotation never starts or cancels a privileged read. */
object ReaderSession {
    private val ids = java.util.concurrent.atomic.AtomicLong(0)
    data class Request(val operation: ReadOperation, val slot: Int, val subId: Int, val startedAt: Long = System.currentTimeMillis(), val id: Long = ids.incrementAndGet())
    data class Outcome(val request: Request, val result: ParsedRead, val finishedAt: Long = System.currentTimeMillis(), val late: Boolean = false, val restored: Boolean = false)
    data class State(val busy: Request? = null, val archive: ReadArchive<Outcome> = ReadArchive(), val latest: Outcome? = null, val revision: Long = 0, val waitingLong: Boolean = false, val pendingDetail: String? = null, val batchActive: Boolean = false, val batchDone: Int = 0, val batchTotal: Int = 0, val batchError: String? = null, val cacheError: Boolean = false, val privateBusy: Boolean = false) {
        val running: Boolean get() = busy != null || batchActive || privateBusy
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listeners = mutableSetOf<() -> Unit>()
    @Volatile var state = State()
        private set
    @Volatile private var bridge: IReadOnlyBridge? = null
    @Volatile private var connected: CountDownLatch? = null
    private var connection: ServiceConnection? = null

    private val saveWorker = Executors.newSingleThreadExecutor()
    private var store: SnapshotStore? = null
    private var initialized = false
    private val queue = ArrayDeque<SimRecord>()
    private var batchApplication: android.app.Application? = null

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        store = SnapshotStore(context.applicationContext)
        try {
            var archive = ReadArchive<Outcome>()
            for (saved in store!!.load()) {
                val request = Request(saved.key.operation, saved.key.slot, saved.key.subId, saved.startedAt)
                archive = archive.put(saved.key, request.id,
                    Outcome(request, saved.result, saved.finishedAt, saved.late, restored = true))
            }
            state = state.copy(archive = archive)
        } catch (_: Exception) { state = state.copy(cacheError = true) }
    }

    fun beginPrivateRead(): Boolean {
        if (state.running) return false
        state = state.copy(privateBusy = true); notifyListeners()
        return true
    }
    fun notifyPrivateChange() { notifyListeners() }
    fun finishPrivateRead() {
        state = state.copy(privateBusy = false); notifyListeners()
    }
    fun readPrivateNumber(context: Context, slot: Int, subId: Int): Bundle? =
        service(context.applicationContext)?.readImsNumber(slot, subId)
    fun readBoth(context: Context, records: List<SimRecord>?) {
        if (state.running) return
        val plan = runCatching { BothSimPlan.create(records) }.getOrElse {
            reject(ReadOperation.TRANSPORT, 0, "SELECTION_CHANGED"); return
        }
        queue.clear(); queue.addAll(plan)
        batchApplication = context.applicationContext as android.app.Application
        state = state.copy(batchActive = true, batchDone = 0, batchTotal = plan.size, batchError = null)
        nextInBatch()
    }

    private fun nextInBatch() {
        if (!state.batchActive || state.busy != null) return
        val next = queue.removeFirst()
        beginRead(requireNotNull(batchApplication), ReadOperation.TRANSPORT, next.slot, next.subscriptionId)
    }
    fun listen(listener: () -> Unit) { listeners.add(listener); listener() }
    fun remove(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it() } }

    fun accessError(): String? = try {
        when {
            !Shizuku.pingBinder() -> "SHIZUKU_NOT_RUNNING"
            Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> "SHIZUKU_TOO_OLD"
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "SHIZUKU_NOT_AUTHORIZED"
            else -> null
        }
    } catch (_: RuntimeException) { "SHIZUKU_UNAVAILABLE" }

    fun read(context: Context, operation: ReadOperation, slot: Int, subId: Int) {
        if (state.running) return
        beginRead(context, operation, slot, subId)
    }
    private fun beginRead(context: Context, operation: ReadOperation, slot: Int, subId: Int) {
        if (state.busy != null) return
        val request = Request(operation, slot, subId)
        state = state.copy(busy = request, waitingLong = false, pendingDetail = null)
        accessError()?.let { complete(request, ParsedRead(error = it)); return }
        notifyListeners()
        main.postDelayed({
            if (state.busy === request) {
                // No cancellation or automatic retry: the Binder request could still be running.
                state = state.copy(waitingLong = true); notifyListeners()
            }
        }, 15_000)
        val app = context.applicationContext
        val callback = object : IReaderCallback.Stub() {
            override fun onResult(requestId: Long, result: Bundle?) {
                main.post {
                    if (ReadLifecycle.accepts(state.busy?.id, request.id, requestId)) {
                        val parsed = runCatching { decode(operation, result) }.getOrElse { ParsedRead(error = "MALFORMED_REPLY") }
                        complete(request, parsed, completedAt(result), state.waitingLong)
                    }
                }
            }
        }
        worker.execute {
            var envelope: Bundle? = null
            val result = try {
                // Final app-side mapping check; the helper checks the platform slot mapping again.
                val sm = app.getSystemService(android.telephony.SubscriptionManager::class.java)
                val records = if (app.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                    sm?.activeSubscriptionInfoList?.map { SimRecord(it.simSlotIndex, it.subscriptionId) } else null
                when {
                    !ReaderProtocol.validSelection(slot, subId, records) -> ParsedRead(error = "APP_SELECTION_CHANGED", detail = "phase=app; requested slot=$slot; subId=$subId; active mapping=$records")
                    else -> {
                        val gate = if (operation == ReadOperation.TRANSPORT || operation == ReadOperation.CAPABILITIES) null else ReaderTargetGate.check(app, operation)
                        if (gate != null) ParsedRead(error = gate, detail = "phase=app; step=target_package")
                        else {
                            val service = service(app)
                            if (service == null) ParsedRead(error = "BRIDGE_NOT_CONNECTED")
                            else {
                                envelope = when (operation) {
                                    ReadOperation.CAPABILITIES -> service.readCapabilities(slot, subId)
                                    ReadOperation.TRANSPORT -> service.readTransport(slot, subId)
                                    ReadOperation.IMS -> service.readIms(slot, subId, request.id, callback)
                                    ReadOperation.CONFIG -> service.readConfig(slot, subId, request.id, callback)
                                }
                                decode(operation, envelope)
                            }
                        }
                    }
                }
            } catch (_: SecurityException) { ParsedRead(error = "ACCESS_OR_ARGUMENT_REJECTED") }
            catch (_: android.os.DeadObjectException) { bridge = null; connected = null; ParsedRead(error = "BRIDGE_DIED") }
            catch (error: Exception) { ParsedRead(error = "TRANSPORT_ERROR", detail = error.javaClass.simpleName) }
            main.post {
                // A quick callback may already have completed this exact request.
                if (state.busy === request) {
                    if (envelope?.getBoolean("pending", false) == true &&
                        ReaderProtocol.validBridgeProtocol(envelope?.get("bridge_protocol"))) {
                        state = state.copy(waitingLong = true, pendingDetail = result.error)
                        notifyListeners()
                    } else complete(request, result, completedAt(envelope), state.waitingLong)
                }
            }
        }
    }

    fun reject(operation: ReadOperation, slot: Int, code: String) {
        if (state.busy == null) complete(Request(operation, slot, -1), ParsedRead(error = code))
    }

    private fun completedAt(response: Bundle?): Long =
        response?.getLong("completed_at", 0L)?.takeIf { it > 0 } ?: System.currentTimeMillis()

    private fun complete(request: Request, result: ParsedRead, at: Long = System.currentTimeMillis(), late: Boolean = false) {
        val outcome = Outcome(request, result, at, late)
        val key = ReadResultKey(request.slot, request.subId, request.operation)
        state = state.copy(busy = null, archive = state.archive.put(key, request.id, outcome),
            latest = outcome, revision = state.revision + 1, waitingLong = false, pendingDetail = null)
        val values = state.archive.entries.map { it.value }.filter { it.request.subId >= 0 }.map {
            StoredRead(ReadResultKey(it.request.slot, it.request.subId, it.request.operation),
                it.request.startedAt, it.finishedAt, it.result, it.late)
        }
        saveWorker.execute {
            try {
                store?.save(values)
                main.post { if (state.cacheError) { state = state.copy(cacheError = false); notifyListeners() } }
            }
            catch (_: Exception) { main.post { state = state.copy(cacheError = true); notifyListeners() } }
        }
        if (state.batchActive) {
            state = state.copy(batchDone = state.batchDone + 1)
            if (result.error != null || queue.isEmpty()) {
                queue.clear(); batchApplication = null
                state = state.copy(batchActive = false, batchError = result.error)
            } else main.post { nextInBatch() }
        }
        notifyListeners()
    }
    @Synchronized
    private fun service(context: Context): IReadOnlyBridge? {
        bridge?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        if (connected?.count == 0L && bridge?.asBinder()?.pingBinder() != true) connected = null
        if (connected == null) {
            val signal = CountDownLatch(1)
            connected = signal
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    bridge = IReadOnlyBridge.Stub.asInterface(binder)
                    signal.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    if (connection !== this) return
                    val lostRequest = state.busy
                    bridge = null; connected = null; signal.countDown()
                    main.post {
                        if (lostRequest != null && state.busy === lostRequest) {
                            complete(lostRequest, ParsedRead(error = "BRIDGE_DIED"))
                        }
                    }
                }
            }
            connection = conn // Keep the connection alive; a late bind never starts a read by itself.
            val args = Shizuku.UserServiceArgs(ComponentName(context, ReaderUserService::class.java))
                .tag("sim-region-read-only").version(10).daemon(false).debuggable(false)
                .processNameSuffix("readonly")
            try { Shizuku.bindUserService(args, conn) }
            catch (error: RuntimeException) { connected = null; connection = null; throw error }
        }
        if (connected?.await(10, TimeUnit.SECONDS) != true) return null
        return bridge?.takeIf { it.asBinder().pingBinder() }
    }

    @Suppress("DEPRECATION")
    private fun decode(operation: ReadOperation, response: Bundle?): ParsedRead {
        if (response == null) return ParsedRead(error = "EMPTY_REPLY")
        if (!ReaderProtocol.validBridgeProtocol(response.get("bridge_protocol"))) {
            return ParsedRead(error = "STALE_HELPER", detail = "phase=app; expected bridge protocol 6; helper needs reconnection")
        }
        val error = response.get("transport_error")
        if (error != null) return ParsedRead(error = error as? String ?: "MALFORMED_REPLY", detail = response.getString("detail"))
        val code = response.get("result_code") as? Int ?: return ParsedRead(error = "MALFORMED_REPLY")
        val payload = response.get("payload") as? Bundle ?: return ParsedRead(error = "EMPTY_REPLY")
        val values = mutableMapOf<String, Any?>()
        if (payload.containsKey("result_msg")) values["result_msg"] = payload.get("result_msg")
        if (payload.containsKey("result")) {
            val raw = payload.get("result")
            values["result"] = if (raw is Bundle) {
                ReaderProtocol.projectResult(operation, { raw.containsKey(it) }, { raw.get(it) })
            } else raw
        }
        return ReaderProtocol.decode(operation, code, values)
    }
}


