package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiModelConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PdfExtractionResult(
    val fileName: String,
    val pageCount: Int,
    val extractedText: String,
    val isScanned: Boolean = false,
    val localFilePath: String = "",
    val tempCachePath: String = ""
)

object PdfExtractor {

    private var isPdfBoxInitialized = false

    private fun ensurePdfBoxInitialized(context: Context) {
        if (!isPdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isPdfBoxInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun extractTextFromUri(
        context: Context,
        uri: Uri,
        modelName: String = GeminiModelConfig.DEFAULT_MODEL
    ): PdfExtractionResult = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri)
        val tempFile = copyUriToTempFile(context, uri, fileName) ?: return@withContext PdfExtractionResult(
            fileName = fileName,
            pageCount = 0,
            extractedText = "Error: Could not access PDF file from storage."
        )

        extractTextFromFileInternal(context, tempFile, fileName, modelName, tempFile.absolutePath)
    }

    suspend fun extractTextFromFile(
        context: Context,
        file: File,
        modelName: String = GeminiModelConfig.DEFAULT_MODEL
    ): PdfExtractionResult = withContext(Dispatchers.IO) {
        extractTextFromFileInternal(context, file, file.name, modelName, file.absolutePath)
    }

    private suspend fun extractTextFromFileInternal(
        context: Context,
        file: File,
        fileName: String,
        modelName: String,
        tempPath: String
    ): PdfExtractionResult = withContext(Dispatchers.IO) {
        ensurePdfBoxInitialized(context)

        var totalPageCount = 0
        val extractedPagesMap = mutableMapOf<Int, String>()

        // 1. Check page count reliably using Android PdfRenderer
        var pfd: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            if (pfd != null) {
                pdfRenderer = PdfRenderer(pfd)
                totalPageCount = pdfRenderer.pageCount
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Extract text from all pages using PDFBox
        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)
            if (totalPageCount <= 0) {
                totalPageCount = document.numberOfPages
            }

            val docPages = document.numberOfPages
            if (docPages > 0) {
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.suppressDuplicateOverlappingText = true

                for (pageIndex in 0 until docPages) {
                    try {
                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        val rawText = stripper.getText(document)
                        val cleanedText = cleanExtractedText(rawText)
                        val strippedText = stripWatermarksAndMetadata(cleanedText)
                        if (isMeaningfulContent(strippedText)) {
                            extractedPagesMap[pageIndex] = strippedText
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
        }

        // 3. Process any pages with missing, scanned, watermarked-only, or garbled text using Gemini Vision OCR
        val missingPages = (0 until totalPageCount).filter { 
            !extractedPagesMap.containsKey(it) || extractedPagesMap[it].isNullOrBlank() 
        }

        if (missingPages.isNotEmpty() && pdfRenderer != null) {
            try {
                // Process missing/scanned pages in batches of 4 concurrent workers
                val batchSize = 4
                for (chunk in missingPages.chunked(batchSize)) {
                    val batchBitmaps = mutableListOf<Pair<Int, String>>()

                    for (pageIdx in chunk) {
                        try {
                            if (pageIdx < pdfRenderer.pageCount) {
                                val page = pdfRenderer.openPage(pageIdx)
                                val width = (page.width * 1.4f).toInt().coerceIn(720, 1400)
                                val height = (page.height * 1.4f).toInt().coerceIn(960, 1920)

                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)

                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()

                                val base64 = bitmapToBase64(bitmap)
                                bitmap.recycle()
                                batchBitmaps.add(Pair(pageIdx, base64))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (batchBitmaps.isNotEmpty()) {
                        val ocrResults = coroutineScope {
                            batchBitmaps.map { (pageIndex, base64Img) ->
                                async(Dispatchers.IO) {
                                    try {
                                        val ocrResponse = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                                            GeminiClient.generateMultimodal(
                                                prompt = "Extract all readable text, questions, answers, paragraphs, headings, tables, bullet points, math equations, and numbers from this document page accurately. Preserve the exact content, structure, and text. Do not summarize or omit anything. Do not include watermark stamps or download metadata.",
                                                imageBase64 = base64Img,
                                                mimeType = "image/jpeg",
                                                modelName = modelName
                                            )
                                        }
                                        if (!ocrResponse.isNullOrBlank() && !ocrResponse.startsWith("API Error") && !ocrResponse.startsWith("API Key Error")) {
                                            val cleaned = stripWatermarksAndMetadata(cleanExtractedText(ocrResponse))
                                            Pair(pageIndex, cleaned)
                                        } else {
                                            Pair(pageIndex, "[Page content: diagram / image]")
                                        }
                                    } catch (_: Exception) {
                                        Pair(pageIndex, "[Page content: diagram / image]")
                                    }
                                }
                            }.awaitAll()
                        }

                        for ((pageIdx, text) in ocrResults) {
                            extractedPagesMap[pageIdx] = text
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Close renderer resources
        try {
            pdfRenderer?.close()
            pfd?.close()
        } catch (_: Exception) {}

        if (totalPageCount == 0 && extractedPagesMap.isEmpty()) {
            return@withContext PdfExtractionResult(
                fileName = fileName,
                pageCount = 0,
                extractedText = "Unable to read text from this document. Please ensure the file is a valid PDF.",
                tempCachePath = tempPath
            )
        }

        // 4. Assemble ALL pages in exact sequential order (1 to totalPageCount)
        val fullTextBuilder = StringBuilder()
        for (pageIdx in 0 until totalPageCount) {
            val pageNum = pageIdx + 1
            val pageText = extractedPagesMap[pageIdx]?.trim()
            if (pageIdx > 0) fullTextBuilder.append("\n\n")
            fullTextBuilder.append("--- Page $pageNum ---\n")
            if (!pageText.isNullOrBlank()) {
                fullTextBuilder.append(pageText)
            } else {
                fullTextBuilder.append("[Page $pageNum: Visual / diagram content]")
            }
        }

        PdfExtractionResult(
            fileName = fileName,
            pageCount = totalPageCount,
            extractedText = fullTextBuilder.toString().trim(),
            isScanned = missingPages.isNotEmpty(),
            localFilePath = "",
            tempCachePath = tempPath
        )
    }

    private fun stripWatermarksAndMetadata(raw: String): String {
        var cleaned = raw
        // Remove StuDocu identifiers, e.g. "lOMoARcPSD|63016857", "lOMoARcPSD 63016857", "IOMOARCPSD 63016857"
        cleaned = cleaned.replace(Regex("(?i)[lI1|]OMoARcPSD[|\\s]*\\d+"), "")
        // Remove download markers like "Downloaded by Mahalakshmi.N (mahalakshmin285@gmail.com)"
        cleaned = cleaned.replace(Regex("(?i)Downloaded by [^\n]+"), "")
        // Remove StuDocu / CourseHero disclaimers
        cleaned = cleaned.replace(Regex("(?i)Studocu is not sponsored or endorsed by[^\n]+"), "")
        cleaned = cleaned.replace(Regex("(?i)https?://(www\\.)?(studocu|coursehero|scribd|academia)\\.com[^\n]*"), "")
        cleaned = cleaned.replace(Regex("(?i)Course Hero[^\n]*"), "")
        cleaned = cleaned.replace(Regex("(?i)This document is (available on|authorized for use only by)[^\n]+"), "")
        cleaned = cleaned.replace(Regex("(?i)Scanned (with|by) [^\n]+"), "")
        // Remove standalone page header numbers or "Page X of Y" lines if isolated
        cleaned = cleaned.replace(Regex("(?m)^\\s*Page\\s+\\d+(\\s+of\\s+\\d+)?\\s*$"), "")
        return cleanExtractedText(cleaned)
    }

    private fun isMeaningfulContent(text: String): Boolean {
        val stripped = stripWatermarksAndMetadata(text).trim()
        if (stripped.length < 25) return false
        if (isTextGarbledOrUnreadable(stripped)) return false

        // Count meaningful words (words with 2 or more letters)
        val words = stripped.split(Regex("\\s+")).filter { word ->
            word.length >= 2 && word.any { it.isLetter() }
        }
        return words.size >= 5
    }

    private fun cleanExtractedText(raw: String): String {
        return raw
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            .replace("\uFFFD", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun isTextGarbledOrUnreadable(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 5) return true

        var letterCount = 0
        var digitCount = 0
        var symbolOrControlCount = 0
        var replacementCount = 0
        var privateUseCount = 0

        for (ch in trimmed) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' -> letterCount++
                ch in '0'..'9' -> digitCount++
                ch == '\uFFFD' || ch == '\u0000' || (ch.code in 1..31 && ch != '\n' && ch != '\r' && ch != '\t') -> replacementCount++
                ch.code in 0xE000..0xF8FF -> privateUseCount++
                ch.isWhitespace() -> {}
                else -> symbolOrControlCount++
            }
        }

        val totalSignificantChars = letterCount + digitCount + symbolOrControlCount + replacementCount + privateUseCount
        if (totalSignificantChars == 0) return true

        // If more than 15% replacement chars or private use chars, it's garbled
        if ((replacementCount + privateUseCount).toFloat() / totalSignificantChars > 0.15f) {
            return true
        }

        // Check if words are mostly single digits/isolated symbols e.g. "1 4 2 9 0 3 8 2 1" or "23 45 12 89"
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 6) {
            val singleCharWords = words.count { it.length == 1 }
            val numericWords = words.count { it.all { c -> c.isDigit() } }
            if (singleCharWords.toFloat() / words.size > 0.60f && letterCount < 10) {
                return true
            }
            if (numericWords.toFloat() / words.size > 0.80f && letterCount < 10) {
                return true
            }
        }

        // If text has significant length (>40 chars) but very few recognizable letters (<15% letters and high symbols)
        if (trimmed.length > 40 && (letterCount.toFloat() / totalSignificantChars) < 0.15f) {
            return true
        }

        return false
    }

    fun savePermanentPdfFile(context: Context, sourceFilePath: String, fileName: String): File? {
        return try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return null
            val pdfDir = File(context.filesDir, "saved_pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            val sanitized = fileName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val targetFile = File(pdfDir, "${System.currentTimeMillis()}_$sanitized")
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "Document.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val foundName = cursor.getString(nameIndex)
                if (!foundName.isNullOrBlank()) {
                    name = foundName
                }
            }
        }
        return name
    }

    private fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val sanitized = fileName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}_$sanitized")
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
