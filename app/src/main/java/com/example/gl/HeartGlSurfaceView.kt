package com.example.gl

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.HeartUiState
import com.example.model.TelemetrySnapshot

@SuppressLint("ClickableViewAccessibility")
@Composable
fun HeartGlSurfaceView(
    uiState: HeartUiState,
    onTelemetryUpdate: (TelemetrySnapshot) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember {
        HeartGlRenderer(onTelemetryUpdate = onTelemetryUpdate)
    }

    // Sync Compose UI state into the GL renderer
    LaunchedEffect(
        uiState.bpm,
        uiState.myocardiumOpacity,
        uiState.transparencyMode,
        uiState.isPlaying,
        uiState.selectedStructure
    ) {
        renderer.bpm = uiState.bpm
        renderer.myocardiumOpacity = uiState.myocardiumOpacity
        renderer.transparencyMode = uiState.transparencyMode
        renderer.isPlaying = uiState.isPlaying
        renderer.selectedRegionCode = uiState.selectedStructure?.regionCode?.toFloat() ?: 0f
    }

    LaunchedEffect(uiState.cameraResetToken) {
        if (uiState.cameraResetToken > 0) {
            renderer.resetCamera()
        }
    }

    val glSurfaceView = remember {
        var lastX = 0f
        var lastY = 0f
        var activePointerId = MotionEvent.INVALID_POINTER_ID

        val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    renderer.addZoomFactor(detector.scaleFactor)
                    return true
                }
            }
        )

        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        activePointerId = event.getPointerId(0)
                        lastX = event.x
                        lastY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                            val idx = event.findPointerIndex(activePointerId)
                            if (idx != -1) {
                                val x = event.getX(idx)
                                val y = event.getY(idx)
                                val dx = x - lastX
                                val dy = y - lastY
                                renderer.addRotationDelta(dx * 0.35f, dy * 0.35f)
                                lastX = x
                                lastY = y
                            }
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        val pointerIdx = event.actionIndex
                        val pointerId = event.getPointerId(pointerIdx)
                        if (pointerId == activePointerId) {
                            val newIndex = if (pointerIdx == 0) 1 else 0
                            lastX = event.getX(newIndex)
                            lastY = event.getY(newIndex)
                            activePointerId = event.getPointerId(newIndex)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
                true
            }
        }
    }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier
    )
}
