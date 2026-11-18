package com.example.simregionswitcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import java.util.concurrent.Executors

/** Private memory-only result. No URI/number is passed to the shared archive/report/persistence. */
object ImsNumberSession {
    data class Target(val slot: Int, val subId: Int)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var visible: Target? = null
    var result: ImsNumberSnapshot? = null
        private set
    var receivedAt = 0L
        private set
    var busy: Target? = null
        private set
    var waitingLong = false
        private set
    var diagnostic = "NOT_REQUESTED"
        private set
    fun show(target: Target) { visible = target; result = null; receivedAt = 0 }
    fun close() { visible = null; result = null; receivedAt = 0 }
    fun isVisible(target: Target) = visible == target

    fun read(context: Context, target: Target) {
        if (!ReaderSession.beginPrivateRead()) return
        result = null; receivedAt = 0; busy = target; waitingLong = false; diagnostic = "RUNNING"
        ReaderSession.notifyPrivateChange()
        main.postDelayed({
            if (busy === target) { waitingLong = true; ReaderSession.notifyPrivateChange() }
        }, 15_000)
        val app = context.applicationContext
        worker.execute {
            val snapshot = try {
                if (ReaderSession.accessError() != null) ImsNumberSnapshot("ACCESS_DENIED")
                else {
                    val sm = app.getSystemService(SubscriptionManager::class.java)
                    val records = if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                        sm?.activeSubscriptionInfoList?.map { SimRecord(it.simSlotIndex, it.subscriptionId) } else null
                    if (!ReaderProtocol.validSelection(target.slot, target.subId, records)) ImsNumberSnapshot("MAPPING_CHANGED")
                    else {
                        val packet = ReaderSession.readPrivateNumber(app, target.slot, target.subId)
                        @Suppress("DEPRECATION")
                        if (packet == null || !ReaderProtocol.validBridgeProtocol(packet.get("bridge_protocol")))
                            ImsNumberSnapshot("INVALID_REPLY")
                        else ImsNumberRules.decode(packet.get("number_status"), packet.get("numbers"))
                    }
                }
            } catch (_: SecurityException) { ImsNumberSnapshot("ACCESS_DENIED") }
            catch (_: Exception) { ImsNumberSnapshot("READ_ERROR") }
            main.post {
                diagnostic = snapshot.status
                if (visible == target) { result = snapshot; receivedAt = System.currentTimeMillis() }
                busy = null; waitingLong = false
                ReaderSession.finishPrivateRead()
            }
        }
    }
}
