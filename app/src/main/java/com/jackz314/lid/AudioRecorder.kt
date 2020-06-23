package com.jackz314.lid

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

private const val TAG = "AudioRecorder"

class AudioRecorder(private val audioCallback: AudioCallback, sampleRate: Int, windowLen: Float) {
    private val audioRecord: AudioRecord
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    private val bufferSize = maxOf((sampleRate * windowLen).toInt(),minBufferSize) // if somehow min buffer size is bigger, return that much everytime
    private val audioData = FloatArray(bufferSize) { _ ->0F}// used to be returned into callback
    private var recordingThread: Thread
    private var isRecording = false

    init {
        Log.i(TAG, "AudioRecorder: min buffer size: $minBufferSize")
        audioRecord = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(bufferSize).build()
        recordingThread = Thread(Runnable { record() })
    }

    private fun record(){
        while (isRecording){
            val recordedLen = audioRecord.read(audioData, 0, bufferSize, AudioRecord.READ_BLOCKING)
            if (recordedLen < 0){ // error
                Log.e(TAG, "record: Record failed, return value: $recordedLen")
                continue
            }
            if(recordedLen != bufferSize){
                Log.w(TAG,
                    "record: recorded length is not the same as buffer size: $recordedLen vs $bufferSize"
                )
                if(recordedLen < bufferSize){ // pad the rest
                    val tempZeroArr = FloatArray(bufferSize - recordedLen){ _ ->0F}
                    tempZeroArr.copyInto(audioData, recordedLen)
                }
            }
            audioCallback.gotAudio(audioData.copyOf(), recordedLen)
        }
    }

    fun isRecording(): Boolean{
        return isRecording
    }

    fun start(){
        if (isRecording) return
        Log.i(TAG, "start: recording started")
        isRecording = true
        audioRecord.startRecording()
        if (recordingThread.state == Thread.State.NEW){
            recordingThread.start()
        }else if(recordingThread.state == Thread.State.TERMINATED){
            recordingThread = Thread(Runnable { record() })
            recordingThread.start()
        }
    }

    fun stop(){
        if (!isRecording) return
        Log.i(TAG, "stop: recording stopped")
        isRecording = false
        audioRecord.stop()
    }

    fun close(){
        audioRecord.stop()
        audioRecord.release()
    }
}