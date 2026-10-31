package io.github.aasifashraf.unscroll.engine.store

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * The single on-disk state document. Writes are atomic (temp file + rename), so a crash or
 * power loss leaves either the old or the new state, never a half-written file.
 */
class RulesStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "state.json"))
    private val lock = Any()

    @Volatile
    var state: PersistedState = load()
        private set

    /** Applies [transform] and saves. Returns the new state. */
    fun update(transform: (PersistedState) -> PersistedState): PersistedState = synchronized(lock) {
        val next = transform(state)
        if (next != state) {
            save(next)
            state = next
        }
        next
    }

    private fun load(): PersistedState {
        if (!file.baseFile.exists()) return PersistedState()
        val decoded = try {
            StateCodec.decodeOrNull(String(file.readFully(), Charsets.UTF_8))
        } catch (e: IOException) {
            Log.w(TAG, "State unreadable", e)
            null
        }
        if (decoded != null) return decoded
        // Keep the unreadable file instead of overwriting the user's rules with defaults.
        val kept = File(file.baseFile.parentFile, "state.corrupt-${System.currentTimeMillis()}.json")
        if (file.baseFile.renameTo(kept)) Log.w(TAG, "Kept unreadable state as ${kept.name}")
        return PersistedState()
    }

    private fun save(next: PersistedState) {
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Cannot open state for writing", e)
            return
        }
        try {
            out.write(StateCodec.encode(next).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Saving state failed", e)
        }
    }

    private companion object {
        const val TAG = "UnscrollStore"
    }
}
