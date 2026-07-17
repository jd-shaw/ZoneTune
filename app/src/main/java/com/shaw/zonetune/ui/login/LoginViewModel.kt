package com.shaw.zonetune.ui.login

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.shaw.zonetune.ZoneTuneApp
import com.shaw.zonetune.data.api.BiliRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class QrStatus {
    Loading,
    Waiting,
    Scanned,
    Success,
    Expired,
    Error,
}

data class LoginUiState(
    val status: QrStatus = QrStatus.Loading,
    val qrBitmap: Bitmap? = null,
    val message: String = "请使用手机扫码登录",
)

class LoginViewModel(
    private val repository: BiliRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    private var pollJob: Job? = null

    fun start() {
        pollJob?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(status = QrStatus.Loading, message = "正在生成二维码…") }
            try {
                val data = repository.generateQrCode()
                val bitmap = encodeQr(data.url)
                _ui.update {
                    it.copy(
                        status = QrStatus.Waiting,
                        qrBitmap = bitmap,
                        message = "请使用手机扫码登录",
                    )
                }
                poll(data.qrcodeKey)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(status = QrStatus.Error, message = e.message ?: "生成二维码失败")
                }
            }
        }
    }

    private fun poll(qrcodeKey: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(1800)
                try {
                    val result = repository.pollQrCode(qrcodeKey)
                    when (result.code) {
                        0 -> {
                            _ui.update {
                                it.copy(status = QrStatus.Success, message = "登录成功")
                            }
                            break
                        }
                        86090 -> {
                            _ui.update {
                                it.copy(status = QrStatus.Scanned, message = "扫码成功，请在手机上确认")
                            }
                        }
                        86101 -> {
                            // waiting
                        }
                        86038 -> {
                            _ui.update {
                                it.copy(status = QrStatus.Expired, message = "二维码已过期，请刷新")
                            }
                            break
                        }
                        else -> {
                            // keep waiting for transient codes
                        }
                    }
                } catch (_: Exception) {
                    // network blip — continue polling
                }
            }
        }
    }

    private fun encodeQr(content: String, size: Int = 512): Bitmap {
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bits[x, y]) 0xFF111827.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = ZoneTuneApp.instance
                val repo = BiliRepository(app.biliClient, app.cookieStore)
                return LoginViewModel(repo) as T
            }
        }
    }
}
