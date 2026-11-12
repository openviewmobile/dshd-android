package com.dshd.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/**
 * Native PDF page rasterizer exposed to the local WebView.
 *
 * <p>Chromium 92's PDF.js worker stalls while loading embedded fonts on this
 * device. Android's PdfRenderer uses the platform PDF engine instead, so the
 * page bitmap (including text) is always available. The bridge only accepts
 * files created below the app cache directory.
 */
final class PdfBridge {
  private static final String TAG = "dshd";
  private static final String DIR = "pdf-preview";
  private static final int MAX_WIDTH = 2400;

  private final Context context;

  PdfBridge(Context context) {
    this.context = context.getApplicationContext();
  }

  @JavascriptInterface
  public String storePdf(String base64) {
    try {
      byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
      if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
          || bytes[3] != 'F' || bytes[4] != '-') {
        return "";
      }
      File dir = new File(context.getCacheDir(), DIR);
      if (!dir.isDirectory() && !dir.mkdirs()) return "";
      File out = new File(dir, UUID.randomUUID() + ".pdf");
      try (FileOutputStream stream = new FileOutputStream(out)) {
        stream.write(bytes);
      }
      return out.getAbsolutePath();
    } catch (Throwable error) {
      Log.w(TAG, "storePdf failed", error);
      return "";
    }
  }

  @JavascriptInterface
  public String renderPdfPage(String path, int pageIndex, int width) {
    if (path == null || pageIndex < 0) return "";
    try {
      File file = new File(path).getCanonicalFile();
      File root = new File(context.getCacheDir(), DIR).getCanonicalFile();
      if (!file.getPath().startsWith(root.getPath() + File.separator)) return "";
      int targetWidth = Math.max(240, Math.min(width, MAX_WIDTH));
      try (ParcelFileDescriptor descriptor =
               ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
           PdfRenderer renderer = new PdfRenderer(descriptor)) {
        if (pageIndex >= renderer.getPageCount()) return "";
        try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
          int targetHeight = Math.max(1,
              Math.round((float) targetWidth * page.getHeight() / page.getWidth()));
          Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
          bitmap.eraseColor(Color.WHITE);
          page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
          ByteArrayOutputStream png = new ByteArrayOutputStream();
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
          bitmap.recycle();
          return "data:image/png;base64,"
              + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
        }
      }
    } catch (Throwable error) {
      Log.w(TAG, "renderPdfPage failed", error);
      return "";
    }
  }
}
