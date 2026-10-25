package app.lumen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size as CameraSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.lumen.AppViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.Executors

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
    )

    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) { proxy.close(); return }
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { list -> list.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
            .addOnCompleteListener { proxy.close() }
    }
}

@Composable
fun ScanScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalLumen.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    var barcodeMode by remember { mutableStateOf(false) }
    var showTyped by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val barcodeModeNow by rememberUpdatedState(barcodeMode)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) vm.analyzePhoto(uri) }

    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(granted, lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                // Small barcodes on curved bottles need more pixels than the 640x480 default.
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(CameraSize(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also { a -> a.setAnalyzer(analysisExecutor, BarcodeAnalyzer { code -> if (barcodeModeNow) vm.lookupBarcode(code) }) }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose { provider?.unbindAll() }
    }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    fun shoot() {
        val file = File(ctx.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { vm.analyzePhoto(Uri.fromFile(file)) }
                override fun onError(exception: ImageCaptureException) { /* user can just tap again */ }
            }
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            if (barcodeMode) Canvas(Modifier.fillMaxSize()) {
                val w = size.width * 0.78f
                val h = w * 0.55f
                drawRoundRect(
                    Color.White.copy(alpha = 0.9f), Offset((size.width - w) / 2f, (size.height - h) / 2f - 40.dp.toPx()),
                    Size(w, h), CornerRadius(24.dp.toPx()), Stroke(width = 2.5.dp.toPx())
                )
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T("Camera access is needed to scan food and barcodes.", 15.sp, color = Color.White)
                Spacer(Modifier.height(16.dp))
                GlassButton("Allow camera", { permLauncher.launch(Manifest.permission.CAMERA) }, primary = true)
                Spacer(Modifier.height(10.dp))
                GlassButton("Pick a photo instead", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconCircle(Icons.Rounded.ChevronLeft, onBack, 42.dp, tint = Color.White, desc = "Back")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Meal photo", !barcodeMode, { barcodeMode = false })
                Chip("Barcode", barcodeMode, { barcodeMode = true })
            }
            Spacer(Modifier.size(42.dp))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            T(
                if (barcodeMode) "Point at a barcode. It scans automatically." else "Frame your meal, then tap the shutter.",
                13.sp, color = Color.White, modifier = Modifier.padding(bottom = 18.dp)
            )
            if (barcodeMode) Chip("Type barcode instead", false, { showTyped = true }, Modifier.padding(bottom = 14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconCircle(Icons.Rounded.PhotoLibrary, { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, 52.dp, tint = Color.White, desc = "Gallery")
                Box(
                    Modifier.size(78.dp).clip(CircleShape).background(if (barcodeMode) Color.White.copy(alpha = 0.25f) else Color.White)
                        .clickable(enabled = !barcodeMode) { shoot() }
                )
                Spacer(Modifier.size(52.dp))
            }
        }
    }

    if (showTyped) AlertDialog(
        onDismissRequest = { showTyped = false }, containerColor = c.bgBottom,
        title = { T("Type the barcode", 18.sp, FontWeight.SemiBold) },
        text = { GlassField(typed, { typed = it }, "Digits under the barcode", keyboardType = KeyboardType.Number) },
        confirmButton = { TextButton({ showTyped = false; vm.lookupBarcode(typed, force = true); typed = "" }) { Text("Look up") } },
        dismissButton = { TextButton({ showTyped = false }) { Text("Cancel") } }
    )
}
