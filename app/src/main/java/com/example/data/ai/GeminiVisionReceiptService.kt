package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.engine.ReceiptDraft
import com.example.data.engine.ReceiptValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class GeminiVisionReceiptService {

    private val modelName = "gemini-3.5-flash"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeReceipt(
        context: Context,
        imageUri: Uri,
        apiKeyOverride: String? = null
    ): ReceiptDraft? = withContext(Dispatchers.IO) {
        val apiKey = when {
            !apiKeyOverride.isNullOrBlank() -> apiKeyOverride.trim()
            else -> try {
                BuildConfig.GEMINI_API_KEY
            } catch (_: Exception) {
                ""
            }
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext null
        }

        val base64Image = prepareCompressedBase64(context, imageUri) ?: return@withContext null

        try {
            val systemPrompt = """
                You are an enterprise document intelligence and financial receipt parsing system.
                Analyze the provided image of a financial document, sales receipt, Mobile Money payment confirmation screenshot (MTN MoMo, Telecel Cash, Zeepay), bank debit alert, POS transaction slip, or utility recharge printout.
                
                Accurately extract:
                1. merchant: The clean business, store, vendor, or recipient name.
                   - If it is a mobile money screenshot (e.g., MTN MoMo, Telecel Cash), extract the counterparty person or merchant name.
                   - If it is a utility recharge (e.g. ECG electricity recharge), extract the customer name (e.g. "STEPHEN ETSE") and append the meter number if available.
                   - NEVER return generic noise such as "RECEIPT", "INVOICE", "TAX INVOICE", "CUSTOMER COPY", or "WELCOME".
                2. amount: Grand total amount paid or transferred as a positive decimal number.
                3. currency: 3-letter currency code (default "GHS" unless USD, EUR, GBP, etc. is clearly specified).
                4. transactionType: One of "EXPENSE", "INCOME", "TRANSFER", or "BILL_PAYMENT".
                5. referenceNumber: Any explicit transaction ID, reference number, invoice number, receipt number, approval code, or meter recharge token (e.g. "EAD00605119", "TXN12345678", "Token: 69579079454110730781").
                6. date: Date in "YYYY-MM-DD" format if visible, or null.
                7. tax: VAT, NHIL, GETFund, or tax amount as a number, or null.
                8. categorySuggestion: Best matching expense category among: "Food & Dining", "Bills & Utilities", "Transportation", "Shopping & Groceries", "Health & Medical", "Entertainment", "Personal Care", "Business".
                9. paymentRail: Payment channel if visible: "MTN MoMo", "Telecel Cash", "Bank Transfer", "Visa / Mastercard", "Cash", etc.
                10. notes: Any helpful context (e.g. meter token, items list, cashier name).
                11. confidenceScore: Confidence score between 0.70 and 0.99.

                Respond ONLY in valid JSON with this exact format:
                {
                  "merchant": "KFC Accra Mall",
                  "amount": 65.00,
                  "currency": "GHS",
                  "transactionType": "EXPENSE",
                  "referenceNumber": "POS-984321",
                  "date": "2026-10-04",
                  "tax": 8.50,
                  "categorySuggestion": "Food & Dining",
                  "paymentRail": "MTN MoMo",
                  "notes": "Chicken Bucket & Drinks",
                  "confidenceScore": 0.98
                }
            """.trimIndent()

            val root = JSONObject()

            // Generation config requesting JSON output
            val genConfig = JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json")
            root.put("generationConfig", genConfig)

            // Content with Image & Text prompt
            val partsArray = JSONArray()

            // 1. Text Prompt
            val textPart = JSONObject().put("text", systemPrompt)
            partsArray.put(textPart)

            // 2. Inline Image Data
            val inlineData = JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", base64Image)
            val imagePart = JSONObject().put("inlineData", inlineData)
            partsArray.put(imagePart)

            val contentObj = JSONObject()
                .put("role", "user")
                .put("parts", partsArray)

            root.put("contents", JSONArray().put(contentObj))

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = root.toString().toRequestBody(mediaType)

            val url = "$baseUrl?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val replyText = parts?.optJSONObject(0)?.optString("text") ?: return@withContext null

            // Parse returned JSON from model
            val parsedJson = JSONObject(replyText.trim())

            val merchant = parsedJson.optString("merchant").ifBlank { "Scanned Merchant" }
            val amount = parsedJson.optDouble("amount", 0.0).let { if (it > 0) it else null }
            val currency = parsedJson.optString("currency", "GHS").ifBlank { "GHS" }
            val transactionType = parsedJson.optString("transactionType", "EXPENSE").ifBlank { "EXPENSE" }
            val referenceNumber = parsedJson.optString("referenceNumber").ifBlank { null }
            val category = parsedJson.optString("categorySuggestion").ifBlank { null }
            val paymentRail = parsedJson.optString("paymentRail").ifBlank { null }
            val notes = parsedJson.optString("notes").ifBlank { null }
            val confidence = parsedJson.optDouble("confidenceScore", 0.95)
            val tax = if (parsedJson.has("tax") && !parsedJson.isNull("tax")) parsedJson.optDouble("tax") else null

            // Date parsing
            var timestamp = System.currentTimeMillis()
            val dateStr = parsedJson.optString("date")
            if (dateStr.isNotBlank()) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    sdf.parse(dateStr)?.let { timestamp = it.time }
                } catch (_: Exception) {}
            }

            val untrustedDraft = ReceiptDraft(
                amount = amount,
                merchant = merchant,
                date = timestamp,
                tax = tax,
                confidenceScore = confidence.coerceIn(0.0, 1.0),
                currency = currency,
                referenceNumber = referenceNumber,
                transactionType = transactionType,
                suggestedCategory = category,
                paymentRail = paymentRail,
                rawText = notes,
                isAiEnhanced = true,
                itemsSummary = notes
            )
            untrustedDraft.copy(
                confidenceScore = minOf(
                    untrustedDraft.confidenceScore,
                    ReceiptValidation.confidenceCeiling(untrustedDraft)
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun prepareCompressedBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Check orientation from EXIF
            var rotation = 0
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    rotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (_: Exception) {}

            var bitmap = originalBitmap
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                bitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            }

            // Downscale to max 1280px to keep payload size lightweight and fast
            val maxDimension = 1280
            val maxSide = max(bitmap.width, bitmap.height)
            val scaledBitmap = if (maxSide > maxDimension) {
                val scale = maxDimension.toFloat() / maxSide
                val targetW = (bitmap.width * scale).toInt()
                val targetH = (bitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
            val byteArray = outputStream.toByteArray()

            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
