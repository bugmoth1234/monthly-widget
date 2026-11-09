package dev.monthly.widget

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 일정·프로젝트·꾸미기 설정과 스티커/사진/글꼴 파일을 zip 하나로 백업·복원. */
object Backup {
    private val DIRS = listOf("stickers", "backgrounds", "headers", "fonts")
    private const val MAX_ENTRIES = 2000
    private const val MAX_ENTRY_BYTES = 40L * 1024 * 1024   // 파일 하나 40MB
    private const val MAX_TOTAL_BYTES = 300L * 1024 * 1024  // 전체 300MB

    /** 정해진 크기까지만 복사하고, 넘으면 실패 */
    private fun copyLimited(input: java.io.InputStream, out: java.io.OutputStream, limit: Long): Long {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw IllegalStateException("too large")
            out.write(buf, 0, n)
        }
        return total
    }

    fun export(ctx: Context, uri: Uri): Boolean = try {
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(Store.rawExport(ctx).toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for (dir in DIRS) {
                    val files = File(ctx.filesDir, dir).listFiles() ?: continue
                    for (f in files) {
                        if (!f.isFile) continue
                        zip.putNextEntry(ZipEntry("$dir/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: throw IllegalStateException("no stream")
        true
    } catch (e: Exception) {
        false
    }

    fun import(ctx: Context, uri: Uri): Boolean {
        // 먼저 임시 폴더에 풀어 보고, data.json이 있을 때만 바꿔치기
        val tmp = File(ctx.cacheDir, "restore_" + System.currentTimeMillis())
        tmp.mkdirs()
        try {
            var data: JSONObject? = null
            var count = 0
            var total = 0L
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (++count > MAX_ENTRIES) throw IllegalStateException("too many entries")
                        val name = e.name
                        if (name == "data.json") {
                            val bos = java.io.ByteArrayOutputStream()
                            total += copyLimited(zip, bos, 5L * 1024 * 1024)
                            data = JSONObject(bos.toString("UTF-8"))
                        } else {
                            val parts = name.split("/")
                            // 정해진 폴더의 파일만, 경로 조작 방지
                            if (parts.size == 2 && parts[0] in DIRS && parts[1].isNotEmpty() &&
                                !parts[1].contains("..") && !parts[1].startsWith(".")
                            ) {
                                val out = File(File(tmp, parts[0]).also { it.mkdirs() }, parts[1])
                                FileOutputStream(out).use { total += copyLimited(zip, it, MAX_ENTRY_BYTES) }
                                if (total > MAX_TOTAL_BYTES) throw IllegalStateException("too large")
                            }
                        }
                    }
                }
            } ?: return false
            val d = data ?: return false
            for (dir in DIRS) {
                val target = File(ctx.filesDir, dir)
                target.listFiles()?.forEach { it.delete() }
                target.mkdirs()
                File(tmp, dir).listFiles()?.forEach { it.copyTo(File(target, it.name), overwrite = true) }
            }
            Store.rawImport(ctx, d)
            return true
        } catch (e: Exception) {
            return false
        } finally {
            tmp.deleteRecursively()
        }
    }
}
