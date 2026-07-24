package com.example.data.remote

import android.content.Context
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveKitCallManager {

    private val TAG = "AuraLiveKit"

    private var room: Room? = null
    private var eventJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _participantCount = MutableStateFlow(0)
    val participantCount: StateFlow<Int> = _participantCount.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    val livekitUrl: String = "wss://demo.livekit.cloud"

    suspend fun connect(context: Context, serverUrl: String, token: String, isVideo: Boolean) {
        try {
            Log.d(TAG, "Connecting to LiveKit room at $serverUrl with token length ${token.length}")
            val activeRoom = LiveKit.create(context)
            room = activeRoom

            // Collect LiveKit room events
            eventJob?.cancel()
            eventJob = scope.launch {
                activeRoom.events.collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            Log.d(TAG, "LiveKit Room Connected!")
                            _isConnected.value = true
                            _connectionError.value = null

                            // Enable mic and optional camera
                            try {
                                activeRoom.localParticipant.setMicrophoneEnabled(true)
                                if (isVideo) {
                                    activeRoom.localParticipant.setCameraEnabled(true)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error enabling local tracks: ${e.localizedMessage}")
                            }
                        }

                        is RoomEvent.ParticipantConnected -> {
                            Log.d(TAG, "Participant Connected: ${event.participant.identity}")
                            updateParticipantState(activeRoom)
                        }

                        is RoomEvent.ParticipantDisconnected -> {
                            Log.d(TAG, "Participant Disconnected: ${event.participant.identity}")
                            updateParticipantState(activeRoom)
                        }

                        is RoomEvent.TrackSubscribed -> {
                            Log.d(TAG, "Track Subscribed: ${event.track.kind}")
                            if (event.track is VideoTrack) {
                                _remoteVideoTrack.value = event.track as VideoTrack
                            }
                        }

                        is RoomEvent.TrackUnsubscribed -> {
                            Log.d(TAG, "Track Unsubscribed: ${event.track.kind}")
                            if (event.track == _remoteVideoTrack.value) {
                                _remoteVideoTrack.value = null
                            }
                        }

                        is RoomEvent.Disconnected -> {
                            Log.d(TAG, "LiveKit Room Disconnected")
                            _isConnected.value = false
                            _remoteVideoTrack.value = null
                            _localVideoTrack.value = null
                        }

                        else -> {}
                    }
                }
            }

            // Connect to server
            val targetUrl = serverUrl.ifBlank { livekitUrl }
            activeRoom.connect(targetUrl, token)
            updateParticipantState(activeRoom)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect LiveKit room: ${e.localizedMessage}", e)
            _connectionError.value = "LiveKit connection failed: ${e.localizedMessage}"
            _isConnected.value = false
        }
    }

    private fun updateParticipantState(activeRoom: Room) {
        val remoteMap = activeRoom.remoteParticipants
        _participantCount.value = remoteMap.size + 1
    }

    fun toggleMute(isMuted: Boolean) {
        scope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(!isMuted)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling mute: ${e.localizedMessage}")
            }
        }
    }

    fun toggleCamera(isCameraOn: Boolean) {
        scope.launch {
            try {
                room?.localParticipant?.setCameraEnabled(!isCameraOn)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling camera: ${e.localizedMessage}")
            }
        }
    }

    fun disconnect() {
        try {
            eventJob?.cancel()
            room?.disconnect()
            room = null
            _isConnected.value = false
            _remoteVideoTrack.value = null
            _localVideoTrack.value = null
            _participantCount.value = 0
            Log.d(TAG, "LiveKit room disconnected successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting LiveKit room: ${e.localizedMessage}")
        }
    }
}
