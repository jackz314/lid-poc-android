package com.jackz314.lid

import android.app.Activity
import android.os.Build
import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.IOException
import java.nio.MappedByteBuffer

private const val TAG = "Classifier"

class Classifier @Throws(IOException::class) internal constructor(activity: Activity, numThreads: Int) {

    private val delegate: Delegate?
    private val tfliteOptions: Interpreter.Options
    private var modelFile: MappedByteBuffer?
    private val model: Interpreter

    /** The runtime device type used for executing classification.  */
    enum class Device {
        CPU, NNAPI, GPU
    }

    init {
        modelFile = FileUtil.loadMappedFile(activity, getModelPath())
        tfliteOptions = Interpreter.Options()
        val device = getDeviceType()
        Log.i(TAG, "Device Type: $device")
        when (device) {
            Device.NNAPI -> {
                delegate = NnApiDelegate()
                tfliteOptions.addDelegate(delegate)
            }
            Device.GPU -> {
                delegate = GpuDelegate()
                tfliteOptions.addDelegate(delegate)
            }
            else -> {
                delegate = null
            }
        }
        tfliteOptions.setNumThreads(numThreads)
        model = Interpreter(modelFile!!, tfliteOptions)
    }

    fun classify(data: FloatArray): FloatArray{
        val tensorIdx = 0
//        var outputLabels = FloatArray(model.getOutputTensor(tensorIdx).shape()[0])
//        Log.i(TAG, "DATA MINMAX: ${data.min()} ${data.max()}")
        var outputLabels = arrayOf(FloatArray(3))
        model.run(data, outputLabels)
        return outputLabels[0]
    }

    fun close(){
        model.close()
//        (delegate as NnApiDelegate).close()
        modelFile = null
    }

    private fun getModelPath(): String{
//        return "crnn_model.tflite"
        return "model-input-shape.tflite"
    }

    private fun getDeviceType(): Device {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // available above 8.1
            return Device.CPU
        }else{
            return Device.CPU
        }
    }
}