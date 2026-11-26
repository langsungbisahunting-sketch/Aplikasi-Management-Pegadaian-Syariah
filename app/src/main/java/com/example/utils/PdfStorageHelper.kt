package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class SavedPdfResult(
    val savedFileName: String,
    val originalFileName: String,
    val sizeBytes: Long
)

object PdfStorageHelper {

    fun getDokumenDir(context: Context): File {
        val dir = File(context.filesDir, "dokumen_pelanggan")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getFile(context: Context, savedFileName: String): File? {
        val f = File(getDokumenDir(context), File(savedFileName).name)
        return if (f.exists() && f.isFile) f else null
    }

    fun savePdfFromUri(context: Context, nik: String, uri: Uri): Result<SavedPdfResult> {
        return try {
            val resolver = context.contentResolver
            var originalName = "dokumen_${nik}.pdf"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val n = cursor.getString(idx)
                        if (!n.isNullOrBlank()) originalName = n
                    }
                }
            }
            val mime = resolver.getType(uri) ?: ""
            val isPdfExt = originalName.lowercase().endsWith(".pdf")
            val isPdfMime = mime.equals("application/pdf", ignoreCase = true)
            if (!isPdfExt && !isPdfMime) {
                return Result.failure(IllegalArgumentException("File harus berformat PDF (.pdf)."))
            }

            val cleanNik = nik.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val savedName = "doc_${cleanNik}_${System.currentTimeMillis()}.pdf"
            val destFile = File(getDokumenDir(context), savedName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(IllegalArgumentException("Gagal membaca file PDF yang dipilih."))

            if (destFile.length() <= 0L) {
                destFile.delete()
                return Result.failure(IllegalArgumentException("File PDF kosong."))
            }

            // Validate PDF header (%PDF)
            val header = ByteArray(4)
            destFile.inputStream().use { it.read(header) }
            if (String(header, Charsets.US_ASCII) != "%PDF") {
                destFile.delete()
                return Result.failure(IllegalArgumentException("Isi file bukan dokumen PDF yang valid."))
            }

            Result.success(
                SavedPdfResult(
                    savedFileName = savedName,
                    originalFileName = originalName,
                    sizeBytes = destFile.length()
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Gagal menyimpan PDF: ${e.message}"))
        }
    }

    fun deleteFile(context: Context, savedFileName: String?) {
        if (savedFileName.isNullOrBlank()) return
        val f = File(getDokumenDir(context), File(savedFileName).name)
        if (f.exists()) f.delete()
    }

    fun copyFileToUri(context: Context, savedFileName: String, destUri: Uri): Boolean {
        val f = getFile(context, savedFileName) ?: return false
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openExternal(context: Context, savedFileName: String): Boolean {
        val f = getFile(context, savedFileName) ?: return false
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Buka Dokumen PDF"))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun renderPdfPages(context: Context, savedFileName: String, maxPages: Int = 10): List<Bitmap> {
        val f = getFile(context, savedFileName) ?: return emptyList()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = minOf(renderer.pageCount, maxPages)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val targetW = 1080
                            val targetH = ((page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)) * targetW).toInt().coerceAtLeast(100)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return bitmaps
    }
}
