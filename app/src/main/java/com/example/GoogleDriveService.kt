package com.example

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

object GoogleDriveService {
    private val client = OkHttpClient()

    fun uploadPhoto(
        file: File,
        accessToken: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val metadata = JSONObject().apply {
            put("name", file.name)
            put("mimeType", "image/jpeg")
        }.toString()

        val mediaTypeJson = "application/json; charset=UTF-8".toMediaTypeOrNull()
        val mediaTypeJpeg = "image/jpeg".toMediaTypeOrNull()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(
                Headers.Builder()
                    .add("Content-Type", "application/json; charset=UTF-8")
                    .build(),
                metadata.toRequestBody(mediaTypeJson)
            )
            .addPart(
                Headers.Builder()
                    .add("Content-Disposition", "form-data; name=\"file\"; filename=\"${file.name}\"")
                    .add("Content-Type", "image/jpeg")
                    .build(),
                file.asRequestBody(mediaTypeJpeg)
            )
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    onError(IOException("上传失败，状态码: ${response.code}，原因: ${responseBody.take(200)}"))
                } else {
                    try {
                        val json = JSONObject(responseBody)
                        val fileId = json.optString("id", "Unknown ID")
                        onSuccess(fileId)
                    } catch (e: Exception) {
                        onSuccess("已上传 (但无法解析返回的ID)")
                    }
                }
            }
        })
    }
}
