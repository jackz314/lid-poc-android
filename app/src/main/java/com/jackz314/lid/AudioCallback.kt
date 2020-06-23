package com.jackz314.lid

interface AudioCallback {
    fun gotAudio(audioData: FloatArray, recordedLen: Int)
}