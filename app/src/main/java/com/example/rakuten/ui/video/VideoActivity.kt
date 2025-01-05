package com.example.rakuten.ui.video

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.example.rakuten.R
import com.example.rakuten.databinding.ActivityVideoBinding
import com.example.rakuten.utils.NetworkSpeedMonitor

@OptIn(UnstableApi::class)
class VideoActivity : AppCompatActivity(), AnalyticsListener {

    private val binding by lazy {
        ActivityVideoBinding.inflate(layoutInflater)
    }

    private val networkSpeedMonitor by lazy {
        NetworkSpeedMonitor(this)
    }

    private var dataConsumedInMb = 0.0
    private var player: ExoPlayer? = null
    var qualityList = java.util.ArrayList<Pair<String, TrackSelectionParameters.Builder?>>()

    private val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
    private lateinit var trackSelector: DefaultTrackSelector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initPlayer()
        setListener()
        observeData()
    }

    private fun observeData() {
        networkSpeedMonitor.networkMetrics.observe(this) { metrics ->
            updateUI(metrics)
        }
    }

    private fun updateUI(metrics: NetworkSpeedMonitor.NetworkMetrics?) {
        val netWorkDetail =
            "Download Speed: ${metrics?.downloadSpeed} \n" +
                    "Upload Speed: ${metrics?.uploadSpeed} \n" +
                    "Network Speed: ${metrics?.networkType} \n" +
                    "Latency: ${metrics?.latency}ms"

        binding?.networkDetail?.text = netWorkDetail
    }

    override fun onPause() {
        super.onPause()
        pause()
        networkSpeedMonitor.stopMonitoring()
    }

    override fun onResume() {
        super.onResume()
        play()
        networkSpeedMonitor.startMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        trackSelector =
            DefaultTrackSelector(this, AdaptiveTrackSelection.Factory()).apply {
                setParameters(buildUponParameters().setMaxVideoSize(480, 270))
            }
        bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                /*val source = if (MEDIA_URL_HLS.contains("m3u8"))
                    getHlsMediaSource()
                else
                    getProgressiveMediaSource()

                    setMediaSource(source) */

                addAnalyticsListener(this@VideoActivity)

                val mediaSourceList = listOf(
                    "https://hls.fantv.world/d3097a48-e642-41a7-a8dd-9851941054d4/hls/d3097a48-e642-41a7-a8dd-9851941054d4.m3u8",
                    "https://hls.fantv.world/86203a31-27ed-4d4e-a885-dcf0de450122/hls/86203a31-27ed-4d4e-a885-dcf0de450122.m3u8",
                    "https://hls.fantv.world/f2cd54a4-a076-4792-a7f1-92d8e1c5b307/hls/f2cd54a4-a076-4792-a7f1-92d8e1c5b307.m3u8",
                    "https://hls.fantv.world/7151b594-ff78-48d9-a13c-1da10d8cc4d2/hls/7151b594-ff78-48d9-a13c-1da10d8cc4d2.m3u8",
                    "https://hls.fantv.world/73df9e5b-4287-43c4-a826-0a6fa401e6f1/hls/73df9e5b-4287-43c4-a826-0a6fa401e6f1.m3u8"

                )
                val concatenatingMediaSource = createConcatenatingMediaSource(mediaSourceList)
                setMediaSource(concatenatingMediaSource)
                prepare()
                addListener(playerListener)
            }
    }

    private fun getHlsMediaSource(url: String): MediaSource {
        val mediaItem = MediaItem.fromUri(url)
        return HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
            .createMediaSource(mediaItem)
    }

    private fun createConcatenatingMediaSource(urls: List<String>): MediaSource {
        val concatenatingMediaSource = ConcatenatingMediaSource()
        for (url in urls) {
            val mediaSource = getHlsMediaSource(url)
            concatenatingMediaSource.addMediaSource(mediaSource)
        }
        return concatenatingMediaSource
    }

    private fun releasePlayer() {
        player?.apply {
            playWhenReady = false
            release()
        }
        player = null
    }

    private fun pause() {
        player?.playWhenReady = false
    }

    private fun play() {
        player?.playWhenReady = true
    }

    private fun restartPlayer() {
        player?.seekTo(0)
        player?.playWhenReady = true
    }

    private lateinit var bandwidthMeter: DefaultBandwidthMeter

    fun bytesToMB(bytes: Long): Double {
        val megabytes = bytes / (1024.0 * 1024.0)
        return megabytes
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_ENDED -> restartPlayer()
                Player.STATE_READY -> {
                    binding.progressbar.visibility = View.GONE
                    binding.playerView.player = player
                    trackSelector?.generateQualityList()?.let {
                        qualityList = it
                    }
                    qualityList.add(Pair("Auto", null))
                    play()
                }

                Player.STATE_BUFFERING -> {
                    binding.progressbar.visibility = View.VISIBLE
                    // Do nothing
                }

                Player.STATE_IDLE -> {
                    // Do nothing
                }
            }
        }
    }

    private fun setListener() {
        binding?.setting?.setOnClickListener {
            showPopupUpList()
        }
    }

    private var videoQuality: Pair<String, String> = Pair("480 x 270", "270p")
    private fun saveVideoQuality(quality: String, displayQuality: String) {
        videoQuality = Pair(quality, displayQuality)
    }

    private fun showPopupUpList() {
        val popupWindow = ListPopupWindow(this)
        val selectedText = qualityList.map { it.first }.let {
            if (it.contains(videoQuality.first)
                    .not()
            ) "Auto" else videoQuality.second
        }
        val adapter = object : ArrayAdapter<String>(this,
            R.layout.popup_list_item_layout,
            R.id.list_item_text,
            qualityList.map { it.first.getWidthAndHeight().second + "p" }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.popup_list_item_layout, parent, false)
                val textView = view.findViewById<TextView>(R.id.list_item_text)
                textView.text = qualityList[position].first.let {
                    if (it != "Auto") it.getWidthAndHeight().second + "p" else "Auto"
                }

                if (textView.text == selectedText) {
                    textView.setTextColor(
                        ContextCompat.getColor(
                            context, androidx.appcompat.R.color.material_blue_grey_800
                        )
                    )
                } else {
                    textView.setTextColor(Color.BLACK)
                }

                return view
            }
        }
        popupWindow.setAdapter(adapter)
        popupWindow.anchorView = binding?.setting
        popupWindow.width = 200
        popupWindow.setOnItemClickListener { parent, view, position, id ->
            val selectedItem = qualityList[position]
            saveVideoQuality(selectedItem.first, selectedItem.first.let {
                if (it != "Auto") it.getWidthAndHeight().second + "p" else "Auto"
            })
            setVideoQuality()
            popupWindow.dismiss()
        }
        popupWindow.show()
    }

    private fun setVideoQuality() {
        if (videoQuality.first != "Auto") {
            val savedResolution = videoQuality?.first?.getWidthAndHeight()
            savedResolution?.let {
                if (it.first.isNotEmpty()) {
                    player?.trackSelector?.parameters = trackSelector.buildUponParameters()
                        .setMaxVideoSize(it.first.toInt(), it.second.toInt()).build()
                }
            }
        } else {
            setPlayerAutoQuality()
        }
    }

    private fun setPlayerAutoQuality() {
        player?.trackSelector?.parameters =
            trackSelector.buildUponParameters().setMaxVideoSizeSd().build()
    }

    private fun DefaultTrackSelector.generateQualityList(): ArrayList<Pair<String, TrackSelectionParameters.Builder?>> {
        //Render Track -> TRACK GROUPS (Track Array)(Video,Audio,Text)->Track
        val trackOverrideList = ArrayList<Pair<String, TrackSelectionParameters.Builder?>>()

        val renderTrack = this.currentMappedTrackInfo
        val renderCount = renderTrack?.rendererCount ?: 0
        for (rendererIndex in 0 until renderCount) {
            if (isSupportedFormat(renderTrack, rendererIndex)) {
                val trackGroupType = renderTrack?.getRendererType(rendererIndex)
                val trackGroups = renderTrack?.getTrackGroups(rendererIndex)
                val trackGroupsCount = trackGroups?.length!!
                if (trackGroupType == C.TRACK_TYPE_VIDEO) {
                    for (groupIndex in 0 until trackGroupsCount) {
                        val videoQualityTrackCount = trackGroups[groupIndex].length
                        for (trackIndex in 0 until videoQualityTrackCount) {
                            val isTrackSupported = renderTrack.getTrackSupport(
                                rendererIndex,
                                groupIndex,
                                trackIndex
                            ) == C.FORMAT_HANDLED
                            if (isTrackSupported) {
                                val track = trackGroups[groupIndex]
                                val trackName =
                                    "${track.getFormat(trackIndex).width} x ${
                                        track.getFormat(
                                            trackIndex
                                        ).height
                                    }"
                                if (track.getFormat(trackIndex).selectionFlags == C.SELECTION_FLAG_AUTOSELECT) {
                                    trackName.plus(" (Default)")
                                }
                                val trackBuilder =
                                    TrackSelectionParameters.Builder()
                                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        .addOverride(
                                            TrackSelectionOverride(
                                                track,
                                                listOf(trackIndex)
                                            )
                                        )
                                trackOverrideList.add(Pair(trackName, trackBuilder))
                            }
                        }
                    }
                }
            }
        }
        return trackOverrideList
    }

    private fun isSupportedFormat(
        mappedTrackInfo: MappingTrackSelector.MappedTrackInfo?,
        rendererIndex: Int
    ): Boolean {
        val trackGroupArray = mappedTrackInfo?.getTrackGroups(rendererIndex)
        return if (trackGroupArray?.length == 0) {
            false
        } else mappedTrackInfo?.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO || mappedTrackInfo?.getRendererType(
            rendererIndex
        ) == C.TRACK_TYPE_AUDIO || mappedTrackInfo?.getRendererType(rendererIndex) == C.TRACK_TYPE_TEXT
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long
    ) {
        dataConsumedInMb += bytesToMB(totalBytesLoaded)
        binding?.dataConsumed?.text = "Data Consumed in MB = $dataConsumedInMb MB \n" +
                "Total Load Time Ms = $totalLoadTimeMs MS \n" +
                "Bitrate Estimate = $bitrateEstimate"

    }

    override fun onVolumeChanged(eventTime: AnalyticsListener.EventTime, volume: Float) {
        //super.onVolumeChanged(eventTime, volume)
        binding?.otherDetails?.text = "${binding?.otherDetails?.text} \n" +
                "Volume Changed value = $volume"

    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        binding?.otherDetails?.text = "${binding?.otherDetails?.text} \n" +
                "Dropped Frames value = $droppedFrames \n" +
                "ElapsedMs = $elapsedMs"
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        binding?.otherDetails?.text = "${binding?.otherDetails?.text} \n" +
                "First Frame Render Time Ms = $renderTimeMs MS"
    }

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
        if (videoSize.width != 0) {
            binding?.otherDetails?.text = "VideoSizeChanged Width In Pixel = ${videoSize.width} \n" +
                    "VideoSizeChanged Height In Pixel = ${videoSize.height}"
        }
    }

    fun String.getWidthAndHeight(): Pair<String, String> {
        return Pair(this.substringBefore("x").trim(), this.substringAfter("x").trim())
    }
}