package com.racegow.camtimer.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.hypot
import kotlin.math.roundToInt

data class CameraOption(
    val id: String,
    val label: String,
    val isFront: Boolean,
    val isBack: Boolean,
    /** Distinct max frame rates the camera supports, ascending. */
    val fpsOptions: List<Int>,
    val fpsRanges: List<Range<Int>>,
)

data class BoundCamera(val option: CameraOption)

class CameraRepository(private val context: Context) {
    private var cachedProvider: ProcessCameraProvider? = null

    suspend fun provider(): ProcessCameraProvider {
        cachedProvider?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { cachedProvider = it; cont.resume(it) }
                    .onFailure { cont.resumeWithException(it) }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    suspend fun cameras(): List<CameraOption> = provider().availableCameraInfos.map { describe(it) }

    /** Binds preview and analysis to [cameraId], falling back to the default back camera. */
    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        rotation: Int,
        cameraId: String?,
        fps: Int,
        analyzer: ImageAnalysis.Analyzer,
        executor: Executor,
    ): BoundCamera {
        val provider = provider()
        val options = provider.availableCameraInfos.map { describe(it) }
        val option = options.firstOrNull { it.id == cameraId }
            ?: options.firstOrNull { it.isBack }
            ?: options.firstOrNull()
            ?: error("No camera available")

        // Same 4:3 aspect for preview and analysis so the zone drawn on the preview matches the
        // analysed pixels. Analysis stays small to keep up with high frame rates.
        val previewSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val analysisSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(previewSelector)
            .setTargetRotation(rotation)
        val analysisBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(analysisSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageRotationEnabled(true)
            .setTargetRotation(rotation)

        // Prefer a fixed range (e.g. 60-60) over a variable one ending at the same rate.
        val range = option.fpsRanges.filter { it.upper == fps }.maxByOrNull { it.lower }
        if (range != null) {
            Camera2Interop.Extender(previewBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
            Camera2Interop.Extender(analysisBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
        }

        val preview = previewBuilder.build().also { it.setSurfaceProvider(surfaceProvider) }
        val analysis = analysisBuilder.build().also { it.setAnalyzer(executor, analyzer) }
        val selector = CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == option.id } }
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        return BoundCamera(option)
    }

    fun unbindAll() {
        cachedProvider?.unbindAll()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun describe(info: CameraInfo): CameraOption {
        val c2 = Camera2CameraInfo.from(info)
        val facing = c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
        val focal = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
        val sensor = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        // 35 mm equivalent focal length: f * (full-frame diagonal / sensor diagonal).
        val equivalentMm = if (focal != null && sensor != null && sensor.width > 0f) {
            (focal * FULL_FRAME_DIAGONAL_MM / hypot(sensor.width, sensor.height)).roundToInt()
        } else {
            null
        }
        val ranges = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.toList().orEmpty()

        val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT
        val isBack = facing == CameraCharacteristics.LENS_FACING_BACK
        val label = buildString {
            append(
                when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Camera"
                },
            )
            if (isBack && equivalentMm != null) {
                append(
                    when {
                        equivalentMm < 20 -> " – ultra-wide"
                        equivalentMm < 40 -> " – main"
                        else -> " – tele"
                    },
                )
            }
            if (equivalentMm != null) append(" ($equivalentMm mm)")
        }
        return CameraOption(
            id = c2.cameraId,
            label = label,
            isFront = isFront,
            isBack = isBack,
            fpsOptions = ranges.map { it.upper }.distinct().sorted(),
            fpsRanges = ranges,
        )
    }

    private companion object {
        const val FULL_FRAME_DIAGONAL_MM = 43.27f
    }
}
