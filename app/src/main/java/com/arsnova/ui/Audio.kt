package com.arsnova.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arsnova.data.Notebook
import com.arsnova.data.Rec
import kotlinx.coroutines.delay
import java.io.File

/** Grabación ligada a trazos (como Samsung Notes / Notability). */
class AudioCtl(private val ctx: Context, private val dir: File, private val nb: Notebook) {
    var recording by mutableStateOf(false); private set
    var open by mutableStateOf(false)
    var current by mutableStateOf<Rec?>(null)
    var playing by mutableStateOf(false)
    var pos by mutableLongStateOf(0L)
    private var rec: MediaRecorder? = null
    private var recItem: Rec? = null
    private var player: MediaPlayer? = null

    fun startRec() {
        val r = Rec("${nb.id}-a${System.currentTimeMillis()}.m4a", System.currentTimeMillis())
        rec = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioEncodingBitRate(96_000); setAudioSamplingRate(44_100)
            setOutputFile(File(dir, r.file).path); prepare(); start()
        }
        recItem = r; recording = true
    }

    fun stopRec() {
        runCatching { rec?.stop() }; rec?.release(); rec = null
        recItem?.let { it.dur = System.currentTimeMillis() - it.start; nb.recs.add(it) }
        recItem = null; recording = false
    }

    fun select(r: Rec, seekMs: Long = 0, autoplay: Boolean = false) {
        if (current != r) { player?.release(); player = MediaPlayer().apply { setDataSource(File(dir, r.file).path); prepare(); setOnCompletionListener { this@AudioCtl.playing = false } }; current = r }
        player?.seekTo(seekMs.coerceIn(0, r.dur).toInt()); pos = seekMs
        if (autoplay) play()
    }
    fun play() { player?.start(); playing = true }
    fun pause() { player?.pause(); playing = false }
    fun seek(ms: Long) { player?.seekTo(ms.toInt()); pos = ms }
    fun tick() { player?.let { if (playing) pos = it.currentPosition.toLong() } }
    /** Salta al momento en que se escribió un trazo. */
    fun jumpTo(t: Long) { nb.recs.firstOrNull { t >= it.start && t <= it.start + it.dur }?.let { select(it, t - it.start - 1500, true) } }
    fun replayTime(): Long = current?.let { if (open) it.start + pos else -1 } ?: -1
    fun release() { if (recording) stopRec(); player?.release(); player = null }
}

@Composable
fun AudioBar(a: AudioCtl, recs: List<Rec>) {
    LaunchedEffect(a.playing) { while (a.playing) { a.tick(); delay(200) } }
    Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp, modifier = Modifier.padding(bottom = 10.dp).widthIn(max = 520.dp)) {
        Column(Modifier.padding(12.dp, 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (recs.isEmpty()) "Sin grabaciones" else "Toca un trazo para oír ese momento", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton({ a.pause(); a.open = false }) { Icon(Icons.Outlined.Close, "Cerrar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recs.forEachIndexed { i, r -> FilterChip(a.current == r, { a.select(r) }, { Text("${i + 1} · ${r.dur / 60000}:${"%02d".format(r.dur / 1000 % 60)}") }) }
            }
            a.current?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ if (a.playing) a.pause() else a.play() }) { Icon(if (a.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null) }
                    Slider(a.pos.toFloat(), { a.seek(it.toLong()) }, valueRange = 0f..r.dur.toFloat().coerceAtLeast(1f), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
