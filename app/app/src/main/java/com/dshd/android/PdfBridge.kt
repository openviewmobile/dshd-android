package com.dshd.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Native PDF page rasterizer exposed to the local WebView.
 *
 * Chromium 92's PDF.js worker stalls while loading embedded fonts on this
 * device. Android's PdfRenderer uses the platform PDF engine instead, so the
 * page bitmap (including text) is always available. The bridge only accepts
 * files created below the app cache directory.
 */
class PdfBridge(context: Context) {
  private val context: Context = context.applicationContext

  @JavascriptInterface
  fun storePdf(base64: String?): String {
    try {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      if (bytes.size < 5 || bytes[0].toInt() != '%'.code || bytes[1].toInt() != 'P'.code ||
          bytes[2].toInt() != 'D'.code || bytes[3].toInt() != 'F'.code ||
          bytes[4].toInt() != '-'.code) {
        return ""
      }
      val dir = File(context.cacheDir, DIR)
      if (!dir.isDirectory && !dir.mkdirs()) return ""
      val out = File(dir, UUID.randomUUID().toString() + ".pdf")
      FileOutputStream(out).use { stream ->
        stream.write(bytes)
      }
      return out.absolutePath
    } catch (failure: Throwable) {
      Log.w(TAG, "storePdf failed", failure)
      return ""
    }
  }

  @JavascriptInterface
  fun renderPdfPage(path: String?, pageIndex: Int, width: Int): String {
    if (path == null || pageIndex < 0) return ""
    try {
      val file = File(path).canonicalFile
      val root = File(context.cacheDir, DIR).canonicalFile
      if (!file.path.startsWith(root.path + File.separator)) return ""
      val targetWidth = Math.max(240, Math.min(width, MAX_WIDTH))
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
          if (pageIndex >= renderer.pageCount) return ""
          renderer.openPage(pageIndex).use { page ->
            val targetHeight = Math.max(
                1, Math.round(targetWidth.toFloat() * page.height / page.width))
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val png = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
            bitmap.recycle()
            return "data:image/png;base64," +
                Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP)
          }
        }
      }
    } catch (failure: Throwable) {
      Log.w(TAG, "renderPdfPage failed", failure)
      return ""
    }
  }

  private companion object {
    private const val TAG = "dshd"
    private const val DIR = "pdf-preview"
    private const val MAX_WIDTH = 2400
  }
}
