/**
 * File: AudioNrTap.kt
 *
 * Description: Real-time AI noise suppression for IVS Player audio.
 *
 * IVS Player decodes and renders audio entirely in Java:
 *   AudioDecoder -> AudioRendererFilter -> AudioTrackRenderer.render(ByteBuffer,int,long) -> AudioTrack.write()
 *
 * There is no public/reachable seam to splice a filter into that chain (the chain
 * objects are native-owned). So we use in-process ART method hooking (Pine) to
 * intercept AudioTrackRenderer.render(), where the decoded PCM (48 kHz, stereo,
 * 16-bit) is present right before it is written to the AudioTrack. We run RNNoise
 * on it in place (native, per channel). Because this is IVS's own decoded output
 * going to IVS's own AudioTrack, there is ZERO A/V sync cost.
 *
 * NOTE: relies on undocumented IVS internals (class/method names) and ART inline
 * hooking. Both can break on IVS SDK updates or specific Android versions.
 *
 * Author: Xacnio
 */
package dev.xacnio.kciktv.mobile.ui.player

import android.util.Log
import dev.xacnio.kciktv.R
import top.canyie.pine.Pine
import top.canyie.pine.PineConfig
import top.canyie.pine.callback.MethodHook
import java.lang.reflect.Field
import java.nio.ByteBuffer

object AudioNrTap {
    private const val TAG = "AudioNrTap"

    @Volatile private var installed = false
    @Volatile private var dbgCount = 0L

    /** A selectable noise-reduction preset. [baseWet] is the denoise mix; [vadAdapt]
     *  reduces it during speech (Voice Focus) using RNNoise's voice-activity output. */
    data class NrPreset(val nameRes: Int, val baseWet: Float, val vadAdapt: Float)

    /** Index 0 must be "Off". Order defines the dropdown and the stored pref index. */
    val PRESETS = listOf(
        NrPreset(R.string.nr_off, 0.0f, 0.0f),
        NrPreset(R.string.nr_light, 0.55f, 0.0f),
        NrPreset(R.string.nr_balanced, 0.72f, 0.0f),
        NrPreset(R.string.nr_strong, 0.90f, 0.0f),
        NrPreset(R.string.nr_voice, 0.90f, 0.45f),
        NrPreset(R.string.nr_max, 0.96f, 0.0f)
    )

    @Volatile var level = 0
    @Volatile private var baseWet = 0.0f
    @Volatile private var vadAdapt = 0.0f

    /** Applies a preset by index (0 = off). */
    fun setPreset(index: Int) {
        val i = index.coerceIn(0, PRESETS.size - 1)
        level = i
        baseWet = PRESETS[i].baseWet
        vadAdapt = PRESETS[i].vadAdapt
    }

    // ---- Native RNNoise bridge ----
    private class RendererNrState(
        var handle: Long,
        var channels: Int,
        var sampleRate: Int,
        var unsupported: Boolean
    )

    private var nativeLoaded = false
    private val ctxLock = Any()
    private val rendererStates = java.util.WeakHashMap<Any, RendererNrState>()
    @Volatile private var nonDirectWarned = false
    @Volatile private var non48kWarned = false

    private external fun nrCreate(channels: Int): Long
    private external fun nrDestroy(handle: Long)
    private external fun nrProcess(handle: Long, buf: ByteBuffer, startByte: Int, sizeBytes: Int, baseWet: Float, vadAdapt: Float)

    init {
        nativeLoaded = try {
            System.loadLibrary("rnnoise_nr"); true
        } catch (t: Throwable) {
            Log.e(TAG, "loadLibrary(rnnoise_nr) failed", t); false
        }
    }

    // ---- Reflection helpers ----
    private fun findField(obj: Any, predicate: (Field) -> Boolean): Field? {
        var c: Class<*>? = obj.javaClass
        while (c != null && c != Any::class.java) {
            c.declaredFields.firstOrNull(predicate)?.let { it.isAccessible = true; return it }
            c = c.superclass
        }
        return null
    }

    private fun fieldByType(obj: Any, typeSuffix: String): Any? =
        findField(obj) { it.type.name.endsWith(typeSuffix) }?.get(obj)

    private fun fieldByName(obj: Any, name: String): Any? =
        findField(obj) { it.name == name }?.get(obj)

    private fun resolveStateForRenderer(renderer: Any): RendererNrState? {
        if (!nativeLoaded) return null
        val track = fieldByName(renderer, "track") as? android.media.AudioTrack ?: return null
        val sr = track.sampleRate
        val ch = track.channelCount.coerceIn(1, 2)
        synchronized(ctxLock) {
            val existing = rendererStates[renderer]
            if (existing != null && existing.sampleRate == sr && existing.channels == ch) {
                return existing
            }
            if (existing != null) {
                synchronized(existing) {
                    if (existing.handle != 0L) {
                        nrDestroy(existing.handle)
                        existing.handle = 0L
                    }
                }
            }
            if (sr != 48000) {
                if (!non48kWarned) {
                    Log.w(TAG, "sampleRate=$sr (RNNoise expects 48000) — bypassing NR")
                    non48kWarned = true
                }
                return RendererNrState(0L, ch, sr, unsupported = true).also {
                    rendererStates[renderer] = it
                }
            }
            val h = nrCreate(ch)
            Log.i(TAG, "RNNoise context created handle=$h channels=$ch sampleRate=$sr")
            return RendererNrState(h, ch, sr, unsupported = (h == 0L)).also {
                rendererStates[renderer] = it
            }
        }
    }

    /**
     * Reads the runtime PCM format from the live AudioTrack and prepares the per-renderer
     * RNNoise context for that sample rate and channel count.
     */
    fun logFormat(player: Any) {
        if (!nativeLoaded) return
        try {
            val platform = fieldByType(player, ".Platform") ?: run { Log.w(TAG, "logFormat: no Platform"); return }
            val audioRenderer = fieldByName(platform, "audioRenderer") ?: run { Log.w(TAG, "logFormat: audioRenderer=null"); return }
            val state = resolveStateForRenderer(audioRenderer) ?: run { Log.w(TAG, "logFormat: AudioTrack not created yet"); return }
            Log.i(TAG, "🎧 PCM FORMAT: sampleRate=${state.sampleRate}Hz channels=${state.channels} unsupported=${state.unsupported}")
        } catch (t: Throwable) {
            Log.e(TAG, "logFormat failed", t)
        }
    }

    fun release() {
        if (!nativeLoaded) return
        synchronized(ctxLock) {
            for (state in rendererStates.values) {
                synchronized(state) {
                    if (state.handle != 0L) {
                        nrDestroy(state.handle)
                        state.handle = 0L
                    }
                }
            }
            rendererStates.clear()
        }
    }

    /** Installs the render() hook once. Safe to call repeatedly. */
    fun install() {
        if (installed || !nativeLoaded) return
        synchronized(this) {
            if (installed) return
            try {
                PineConfig.debug = false
                PineConfig.debuggable = dev.xacnio.kciktv.BuildConfig.DEBUG

                val cls = Class.forName("com.amazonaws.ivs.player.AudioTrackRenderer")
                val render = cls.getDeclaredMethod(
                    "render",
                    ByteBuffer::class.java,
                    Int::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType
                )

                Pine.hook(render, object : MethodHook() {
                    override fun beforeCall(frame: Pine.CallFrame) {
                        try {
                            if (level <= 0) return
                            val buf = frame.args[0] as? ByteBuffer ?: return
                            val size = frame.args[1] as? Int ?: return
                            if (size <= 0) return
                            val renderer = frame.thisObject ?: return
                            val state = resolveStateForRenderer(renderer) ?: return
                            if (state.unsupported) return
                            if (dbgCount++ == 0L) {
                                Log.i(TAG, "beforeCall: handle=${state.handle} level=$level baseWet=$baseWet size=$size direct=${buf.isDirect} ro=${buf.isReadOnly} pos=${buf.position()} cap=${buf.capacity()}")
                            }
                            // Write via the direct native address (bypasses the Java read-only flag,
                            // which MediaCodec output buffers set even though the memory is writable).
                            if (!buf.isDirect) {
                                if (!nonDirectWarned) { Log.w(TAG, "render buffer not direct — passthrough"); nonDirectWarned = true }
                                return
                            }
                            synchronized(state) {
                                val h = state.handle
                                if (h != 0L) {
                                    nrProcess(h, buf, buf.position(), size, baseWet, vadAdapt)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "beforeCall error", t)
                        }
                    }
                })

                installed = true
                Log.i(TAG, "✅ RNNoise hook installed on AudioTrackRenderer.render()")
            } catch (t: Throwable) {
                Log.e(TAG, "❌ Hook install FAILED: ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }
    }
}
