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
        try {
            val metadata = JSONObject().apply {
                put("name", file.name)
                put("mimeType", "image/jpeg")
            }.toString()

            val mediaTypeJson = "application/json; charset=UTF-8".toMediaTypeOrNull()
            val mediaTypeJpeg = "image/jpeg".toMediaTypeOrNull()

            val multipartType = "multipart/related".toMediaTypeOrNull() ?: MultipartBody.FORM
            val requestBody = MultipartBody.Builder()
                .setType(multipartType)
                .addPart(metadata.toRequestBody(mediaTypeJson))
                .addPart(file.asRequestBody(mediaTypeJpeg))
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
                    val responseBody = try {
                        response.body?.string() ?: ""
                    } catch (e: Exception) {
                        onError(IOException("读取服务器响应数据失败: ${e.message}"))
                        return
                    }

                    if (!response.isSuccessful) {
                        val parsedError = try {
                            val json = org.json.JSONObject(responseBody)
                            val errorObj = json.optJSONObject("error")
                            errorObj?.optString("message") ?: responseBody.take(200)
                        } catch (e: Exception) {
                            responseBody.take(200)
                        }
                        android.util.Log.e("GoogleDriveService", "Upload failed with status ${response.code}: $responseBody")
                        onError(IOException("上传失败 (状态码: ${response.code}): $parsedError"))
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
        } catch (e: Exception) {
            onError(e)
        }
    }
}
