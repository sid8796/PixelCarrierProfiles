package com.example.simregionswitcher

import android.content.Context
import android.util.AtomicFile
import java.io.File

class SnapshotStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "read-snapshots-v1.bin"))
    fun load(): List<StoredRead> {
        if (!file.baseFile.exists()) return emptyList()
        return file.openRead().use { stream ->
            val bytes = stream.readNBytes(SnapshotCodec.MAX_BYTES + 1)
            SnapshotCodec.decode(bytes)
        }
    }
    fun save(values: List<StoredRead>) {
        val bytes = SnapshotCodec.encode(values)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
