package com.scanner.offline.ui.screen.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanner.offline.data.storage.StorageManager
import com.scanner.offline.engine.image.EdgeDetector
import com.scanner.offline.engine.image.PerspectiveCorrector
import com.scanner.offline.util.BitmapUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CropViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val edgeDetector: EdgeDetector,
    private val corrector: PerspectiveCorrector,
    private val storage: StorageManager
) : ViewModel() {

    private val _state = MutableStateFlow<CropState>(CropState.Loading)
    val state: StateFlow<CropState> = _state.asStateFlow()

    fun load(imageUri: String) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                if (java.io.File(imageUri).exists()) {
                    BitmapUtils.decode(java.io.File(imageUri))
                } else {
                    val uri = Uri.parse(imageUri)
                    if (uri.scheme == "file" || uri.scheme == null) {
                        BitmapUtils.decode(java.io.File(uri.path ?: imageUri))
                    } else {
                        BitmapUtils.decode(context.contentResolver, uri)
                    }
                }
            }
            if (bmp == null) {
                _state.value = CropState.Error("无法读取图片")
                return@launch
            }
            val corners = edgeDetector.detect(bmp)
            _state.value = CropState.Ready(bmp, corners)
        }
    }

    fun updateCorner(index: Int, point: PointF) {
        val cur = _state.value as? CropState.Ready ?: return
        val newPts = cur.corners.toMutableList()
        newPts[index] = PointF(
            point.x.coerceIn(0f, 1f),
            point.y.coerceIn(0f, 1f)
        )
        _state.value = cur.copy(corners = newPts)
    }

    fun moveEdge(edgeIndex: Int, dx: Float, dy: Float) {
        val cur = _state.value as? CropState.Ready ?: return
        if (cur.corners.size != 4) return
        val i1 = edgeIndex
        val i2 = (edgeIndex + 1) % 4
        val p1 = cur.corners[i1]
        val p2 = cur.corners[i2]
        val newPts = cur.corners.toMutableList()
        newPts[i1] = PointF((p1.x + dx).coerceIn(0f, 1f), (p1.y + dy).coerceIn(0f, 1f))
        newPts[i2] = PointF((p2.x + dx).coerceIn(0f, 1f), (p2.y + dy).coerceIn(0f, 1f))
        _state.value = cur.copy(corners = newPts)
    }

    fun translateCorners(dx: Float, dy: Float) {
        val cur = _state.value as? CropState.Ready ?: return
        val minX = cur.corners.minOf { it.x }
        val maxX = cur.corners.maxOf { it.x }
        val minY = cur.corners.minOf { it.y }
        val maxY = cur.corners.maxOf { it.y }
        val clampedDx = dx.coerceIn(-minX, 1f - maxX)
        val clampedDy = dy.coerceIn(-minY, 1f - maxY)
        val newPts = cur.corners.map {
            PointF(it.x + clampedDx, it.y + clampedDy)
        }
        _state.value = cur.copy(corners = newPts)
    }

    /** 完成裁剪，输出处理后的图片路径 */
    fun confirm(onDone: (String) -> Unit, onError: (String) -> Unit) {
        val cur = _state.value as? CropState.Ready ?: return
        viewModelScope.launch {
            val outFile = withContext(Dispatchers.Default) {
                runCatching {
                    val corrected = corrector.correct(cur.bitmap, cur.corners)
                    val clamped = corrector.clamp(corrected, 2400)
                    val tmp = storage.newCacheImage("crop")
                    storage.saveBitmap(clamped, tmp)
                    tmp
                }
            }
            outFile.fold(
                onSuccess = { onDone(it.absolutePath) },
                onFailure = { onError(it.message ?: "裁剪失败") }
            )
        }
    }

    sealed class CropState {
        data object Loading : CropState()
        data class Ready(val bitmap: Bitmap, val corners: List<PointF>) : CropState()
        data class Error(val message: String) : CropState()
    }
}
