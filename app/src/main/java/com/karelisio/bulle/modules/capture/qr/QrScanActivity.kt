package com.karelisio.bulle.modules.capture.qr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.capture.CapturePrefs
import com.karelisio.bulle.modules.capture.CaptureState
import com.karelisio.bulle.ui.components.isReduceMotion
import com.karelisio.bulle.ui.theme.BulleTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scanner QR plein écran, léger : aperçu CameraX + analyse ML Kit (modèle intégré, hors ligne).
 * Au premier code lu : vibration, cadre qui se resserre, puis l'activité se ferme et le résultat
 * s'affiche dans l'île (ou est renvoyé au demandeur s'il a fourni un tag, cf. [QrScan]).
 */
class QrScanActivity : ComponentActivity() {

    private val granted = mutableStateOf(false)
    private val torchOn = mutableStateOf(false)
    private val detected = mutableStateOf(false)
    private val handled = AtomicBoolean(false)
    private var camera: Camera? = null
    private var scanner: BarcodeScanner? = null
    private lateinit var analysisExecutor: ExecutorService

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted.value = ok
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        analysisExecutor = Executors.newSingleThreadExecutor()
        granted.value = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!granted.value) permission.launch(Manifest.permission.CAMERA)
        val reduceMotion = isReduceMotion(this)

        setContent {
            BulleTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (granted.value) {
                        CameraPreview()
                        ScanFrame(detected = detected.value, reduceMotion = reduceMotion)
                    } else {
                        PermissionPrompt()
                    }
                    TopBar()
                }
            }
        }
    }

    @Composable
    private fun CameraPreview() {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    bindCamera(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun bindCamera(view: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val client = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_PDF417)
                    .build(),
            )
            scanner = client
            analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(client, proxy) }
            runCatching {
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                camera?.cameraControl?.enableTorch(torchOn.value)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(client: BarcodeScanner, proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || handled.get()) {
            proxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        client.process(input)
            .addOnSuccessListener { codes ->
                val raw = codes.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
                if (raw != null) onDetected(raw)
            }
            .addOnCompleteListener { proxy.close() }
    }

    private fun onDetected(raw: String) {
        if (!handled.compareAndSet(false, true)) return
        if (CapturePrefs.current(this).qrHaptics) {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        detected.value = true
        val tag = intent.getStringExtra(QrScan.EXTRA_TAG)
        // Laisse le cadre se resserrer avant de fermer (sensation de « capture »).
        window.decorView.postDelayed({
            if (tag != null) QrScan.deliver(tag, raw) else CaptureState.showQr(QrParser.parse(raw))
            finish()
        }, CLOSE_DELAY_MS)
    }

    @Composable
    private fun TopBar() {
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White)
            FilledTonalIconButton(onClick = ::finish, colors = colors) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.capture_close))
            }
            if (granted.value) {
                FilledTonalIconButton(
                    onClick = {
                        torchOn.value = !torchOn.value
                        camera?.cameraControl?.enableTorch(torchOn.value)
                    },
                    colors = colors,
                ) {
                    Icon(
                        if (torchOn.value) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = stringResource(R.string.capture_qr_torch),
                    )
                }
            }
        }
    }

    @Composable
    private fun PermissionPrompt() {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.capture_qr_permission),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.capture_grant))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner?.close()
        analysisExecutor.shutdown()
    }

    companion object {
        private const val CLOSE_DELAY_MS = 320L
    }
}

/**
 * Cadre de visée : fond assombri autour d'un carré arrondi, coins lumineux et ligne de balayage.
 * Les valeurs animées ne sont lues qu'au dessin ; la ligne est coupée si les animations sont réduites.
 */
@Composable
internal fun ScanFrame(detected: Boolean, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val sweep = if (!reduceMotion) {
        rememberInfiniteTransition(label = "scan").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "sweep",
        )
    } else {
        null
    }
    val squeeze = remember { Animatable(0f) }
    LaunchedEffect(detected) {
        if (detected) {
            if (reduceMotion) squeeze.snapTo(1f) else squeeze.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
    }
    val hint = stringResource(R.string.capture_qr_hint)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val side = size.minDimension * 0.68f * (1f - 0.08f * squeeze.value)
            val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2 - size.height * 0.04f)
            val radius = 28.dp.toPx()
            val frame = RoundRect(Rect(topLeft, Size(side, side)), CornerRadius(radius))
            // Voile autour du cadre.
            val veil = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(frame)
            }
            drawPath(veil, Color.Black.copy(alpha = 0.55f))
            val ok = squeeze.value
            val color = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFF7FD7C4), ok)
            // Coins (quatre arcs + segments).
            val len = side * 0.18f
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val l = topLeft.x
            val t = topLeft.y
            val r = l + side
            val b = t + side
            fun corner(p: Path) = drawPath(p, color, style = stroke)
            corner(Path().apply { moveTo(l, t + len); lineTo(l, t + radius); quadraticTo(l, t, l + radius, t); lineTo(l + len, t) })
            corner(Path().apply { moveTo(r - len, t); lineTo(r - radius, t); quadraticTo(r, t, r, t + radius); lineTo(r, t + len) })
            corner(Path().apply { moveTo(r, b - len); lineTo(r, b - radius); quadraticTo(r, b, r - radius, b); lineTo(r - len, b) })
            corner(Path().apply { moveTo(l + len, b); lineTo(l + radius, b); quadraticTo(l, b, l, b - radius); lineTo(l, b - len) })
            // Ligne de balayage.
            sweep?.let { s ->
                if (ok < 0.5f) {
                    val y = t + radius + (side - 2 * radius) * s.value
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(l + radius, y), Offset(r - radius, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Text(
            hint,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
        )
    }
}
