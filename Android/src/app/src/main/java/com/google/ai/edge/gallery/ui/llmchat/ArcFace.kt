import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import com.google.android.gms.tflite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class ArcFace(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 112
    private val embeddingSize = 512 // change to 128 if MobileFaceNet

    init {
        val options = Interpreter.Options()

        // ✅ GPU Delegate (optional but recommended)
        try {
            val gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
        } catch (e: Exception) {
            // GPU not supported, fallback to CPU
        }

        options.setNumThreads(4)

        interpreter = Interpreter(
            loadModelFile(context, "model.tflite"),
            options
        )
    }

    private fun loadModelFile(
        context: Context,
        modelName: String
    ): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Equivalent to calc_emb_single() in Python
     */
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
//        Log.d("FaceDetection", "get embedding init")
        val resized = Bitmap.createScaledBitmap(
            faceBitmap,
            inputSize,
            inputSize,
            true
        )

        val input = Array(1) {
            Array(inputSize) {
                Array(inputSize) {
                    FloatArray(3)
                }
            }
        }

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resized.getPixel(x, y)

                input[0][y][x][0] = ((pixel shr 16) and 0xFF) / 255f
                input[0][y][x][1] = ((pixel shr 8) and 0xFF) / 255f
                input[0][y][x][2] = (pixel and 0xFF) / 255f
            }
        }

        val output = Array(1) { FloatArray(embeddingSize) }
        interpreter.run(input, output)
//        Log.d("FaceDetection", output.toString())
        return l2Normalize(output[0])
    }

    /**
     * Same as arcface.lib.utils.l2_norm
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (v in embedding) sum += v * v
        val norm = sqrt(sum)

        for (i in embedding.indices) {
            embedding[i] /= norm
        }
        return embedding
    }

    /**
     * Same logic as get_distance_embeddings()
     */
    fun l2Distance(e1: FloatArray, e2: FloatArray): Float {
        var dist = 0f
        for (i in e1.indices) {
            val diff = e1[i] - e2[i]
            dist += diff * diff
        }
        return dist
    }
}
