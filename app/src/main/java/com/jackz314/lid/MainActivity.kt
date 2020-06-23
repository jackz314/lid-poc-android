package com.jackz314.lid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

private const val TAG = "MainActivity"
private const val SAMPLE_RATE = 8000
private const val SEGMENT_TIME = 0.5F // time of each shorter segments, in seconds
private const val MODEL_TIME_LENGTH = 10
private const val RECORD_PERMISSION_REQUEST_CODE = 69
private val CLASSES = arrayOf("Chinese","English","Spanish")

class MainActivity() : AppCompatActivity(), AudioCallback {
    private lateinit var text: TextView
    private lateinit var detectBtn: Button
    private lateinit var playBtn: Button

    private val audioDataList = LinkedList<FloatArray>()
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioTrack: AudioTrack

    private lateinit var classifier: Classifier

    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        text = findViewById(R.id.textView)
        detectBtn = findViewById(R.id.detectBtn)
        detectBtn.setOnClickListener {
            Toast.makeText(applicationContext, "We need recording permission to record audio! Please grant it!", Toast.LENGTH_SHORT).show()
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_PERMISSION_REQUEST_CODE)
        }
        when {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {//has permission
                init()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // been denied before, try again
                Toast.makeText(applicationContext, "Please grant permission to record audio!", Toast.LENGTH_SHORT).show()
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_PERMISSION_REQUEST_CODE)
            }
            else -> {
                // You can directly ask for the permission.
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when(requestCode){
            RECORD_PERMISSION_REQUEST_CODE -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(applicationContext, "Thank you!", Toast.LENGTH_SHORT).show()
                    init()
                }else{
                    Toast.makeText(applicationContext, "We need recording permission to record audio!", Toast.LENGTH_SHORT).show()
                }
            } else -> {
                Log.i(TAG, "onRequestPermissionsResult: other permission request")
            }
        }
    }

    private fun init(){
        audioRecorder = AudioRecorder(this, SAMPLE_RATE, SEGMENT_TIME)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes((SAMPLE_RATE*SEGMENT_TIME).toInt())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val numThreads = Runtime.getRuntime().availableProcessors()
        classifier = Classifier(this, numThreads)

        playBtn = findViewById(R.id.playBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)
        val resetBtn = findViewById<Button>(R.id.resetBtn)
        detectBtn.setOnClickListener {
            detectBtn.isEnabled = false
            isRecording = true
            audioRecorder.start()
            stopBtn.isEnabled = true
            playBtn.isEnabled = true
            resetBtn.isEnabled = true
            //TODO: start inference
        }
        stopBtn.setOnClickListener {
            stopBtn.isEnabled = false
            isRecording = false
            audioRecorder.stop()
            detectBtn.isEnabled = true
        }
        playBtn.setOnClickListener {
            playAudio()
        }
        resetBtn.setOnClickListener {
            isRecording = false
            audioDataList.clear()
            isRecording = true
        }
    }

    override fun gotAudio(audioData: FloatArray, recordedLen: Int) {
        Log.i(TAG, "gotAudio: len: $recordedLen")
        if(!isRecording){
            Log.i(TAG, "gotAudio: not recording anymore, discarding..")
            return
        }
        if(audioDataList.size < MODEL_TIME_LENGTH/SEGMENT_TIME){//not filled up yet
            audioDataList.add(audioData)
        }else{ // filled up, operate like FIFO queue
            audioDataList.removeFirst()
            audioDataList.add(audioData)
        }
        runInference()
    }

    private fun playAudio(){
        Thread(Runnable {
            runOnUiThread { playBtn.isEnabled = false }
            if(audioRecorder.isRecording()){
                runOnUiThread { stopBtn.isEnabled = false }
                isRecording = false
                audioRecorder.stop()
                playAudioInternal() // blocking long call to play things
                isRecording = true
                audioRecorder.start()
                runOnUiThread { stopBtn.isEnabled = true }
            }else playAudioInternal()
            runOnUiThread { playBtn.isEnabled = true }
        }).start()//play on separate thread
    }

    private fun playAudioInternal(){
        Log.i(TAG, "playAudio: PLAYING!")
        audioTrack.play()
        for (audioData in audioDataList){
            audioTrack.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
            Log.i(TAG, "playAudioInternal: played " + audioData.size + " samples")
        }
        audioTrack.stop()
        Log.i(TAG, "playAudio: DONE!")
    }

    private fun prepareData(): FloatArray{
        val targetLen = SAMPLE_RATE * MODEL_TIME_LENGTH
        val finalArray = FloatArray(targetLen)
        var offset = 0
        for (element in audioDataList){
            element.copyInto(finalArray, offset)
            offset += element.size
        }
        if(offset < targetLen){//pad the incomplete data
            val tempZeroArr = FloatArray(targetLen - offset){ _ ->0F}
            tempZeroArr.copyInto(finalArray, offset)
        }
        return finalArray
    }

    private fun runInference(){
        Log.i(TAG, "runInference: STARTING")
        Thread(Runnable { //run in another thread different from the audio thread
            val preparedData = prepareData()
            val resultArr = classifier.classify(preparedData)
            var resultPairArr = CLASSES zip resultArr.toTypedArray()
            resultPairArr = resultPairArr.sortedByDescending { pair -> pair.second } // sort based on the values
            var textStr = ""
            for (result in resultPairArr){
                textStr += "${result.first}: %.3f%%\n".format(result.second*100)
            }
            runOnUiThread {
//                val textStr = "LENGTH: ${audioDataList.size}"
//                val textStr = "RESULT: ${CLASSES[result]}"
                text.text = textStr
            }
        }).start()
    }

    override fun onDestroy() {
        classifier.close()
        audioTrack.release()
        audioRecorder.close()
        super.onDestroy()
    }
}