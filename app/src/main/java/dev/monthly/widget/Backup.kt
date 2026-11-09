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
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name
                        if (name == "data.json") {
                            data = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        } else {
                            val parts = name.split("/")
                            // 정해진 폴더의 파일만, 경로 조작 방지
                            if (parts.size == 2 && parts[0] in DIRS && parts[1].isNotEmpty() &&
                                !parts[1].contains("..") && !parts[1].startsWith(".")
                            ) {
                                val out = File(File(tmp, parts[0]).also { it.mkdirs() }, parts[1])
                                FileOutputStream(out).use { zip.copyTo(it) }
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
