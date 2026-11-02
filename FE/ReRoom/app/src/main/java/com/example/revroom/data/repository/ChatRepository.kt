package com.example.revroom.data.repository

import android.content.Context
import android.net.Uri
import com.example.revroom.core.network.ApiClient
import com.example.revroom.core.network.ServerEndpoint
import com.example.revroom.core.utils.SelectedImageException
import com.example.revroom.data.remote.ChatApi
import com.example.revroom.data.remote.ChatMessageResponse
import com.example.revroom.data.remote.ConfirmDesignBriefResponse
import com.example.revroom.data.remote.ConversationResponse
import com.example.revroom.data.remote.CreateConversationRequest
import com.example.revroom.data.remote.DesignBriefResponse
import com.example.revroom.data.remote.SendChatMessageRequest
import com.example.revroom.data.remote.SendChatMessageResponse
import com.example.revroom.data.remote.UpdateDesignBriefRequest
import com.example.revroom.features.design_studio.model.DesignResult
import com.example.revroom.features.design_studio.model.DesignStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

interface ChatGateway {
    suspend fun conversations(): Result<List<ConversationResponse>>
    suspend fun createConversation(clientConversationId: String): Result<ConversationResponse>
    suspend fun messages(conversationId: String): Result<List<ChatMessageResponse>>
    suspend fun sendMessage(
        conversationId: String,
        clientMessageId: String,
        content: String
    ): Result<SendChatMessageResponse>
    suspend fun brief(conversationId: String): Result<DesignBriefResponse>
    suspend fun updateBrief(
        conversationId: String,
        request: UpdateDesignBriefRequest
    ): Result<DesignBriefResponse>
    suspend fun confirmBrief(
        conversationId: String,
        briefVersion: Int,
        clientRequestId: String,
        image: Uri
    ): Result<ConfirmDesignBriefResponse>
    suspend fun designStyles(): Result<List<DesignStyle>>
    suspend fun designStatus(designId: String): Result<DesignResult>
}

/** The request reached a verdict from the server or the device, so sending it again would not change it. */
class ChatRequestRejectedException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ChatRepository(
    context: Context,
    private val chatApi: ChatApi = ApiClient.chatApi,
    private val designRepository: DesignRepository = DesignRepository(context)
) : ChatGateway {
    override suspend fun conversations(): Result<List<ConversationResponse>> = request {
        chatApi.getConversations(designRepository.getRegisteredUserId())
    }

    override suspend fun createConversation(clientConversationId: String): Result<ConversationResponse> = request {
        chatApi.createConversation(
            designRepository.getRegisteredUserId(),
            CreateConversationRequest(clientConversationId)
        )
    }

    override suspend fun messages(conversationId: String): Result<List<ChatMessageResponse>> = request {
        chatApi.getMessages(designRepository.getRegisteredUserId(), conversationId)
    }

    override suspend fun sendMessage(
        conversationId: String,
        clientMessageId: String,
        content: String
    ): Result<SendChatMessageResponse> = request {
        chatApi.sendMessage(
            designRepository.getRegisteredUserId(),
            conversationId,
            SendChatMessageRequest(clientMessageId, content)
        )
    }

    override suspend fun brief(conversationId: String): Result<DesignBriefResponse> = request {
        chatApi.getBrief(designRepository.getRegisteredUserId(), conversationId)
    }

    override suspend fun updateBrief(
        conversationId: String,
        request: UpdateDesignBriefRequest
    ): Result<DesignBriefResponse> = request {
        chatApi.updateBrief(designRepository.getRegisteredUserId(), conversationId, request)
    }

    override suspend fun confirmBrief(
        conversationId: String,
        briefVersion: Int,
        clientRequestId: String,
        image: Uri
    ): Result<ConfirmDesignBriefResponse> = request {
        val textType = "text/plain".toMediaType()
        chatApi.confirmBrief(
            designRepository.getRegisteredUserId(),
            conversationId,
            designRepository.readImagePart(image),
            briefVersion.toString().toRequestBody(textType),
            clientRequestId.toRequestBody(textType)
        )
    }

    override suspend fun designStyles(): Result<List<DesignStyle>> = designRepository.getDesignStyles()

    override suspend fun designStatus(designId: String): Result<DesignResult> =
        designRepository.getDesignStatus(designId)

    private suspend fun <T> request(block: suspend () -> T): Result<T> =
        ServerEndpoint.withStableEndpoint {
            withContext(Dispatchers.IO) {
                try {
                    Result.success(block())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: SelectedImageException) {
                    Result.failure(ChatRequestRejectedException(error.message ?: "Không thể đọc ảnh đã chọn.", error))
                } catch (error: HttpException) {
                    Result.failure(toFailure(error))
                } catch (error: IOException) {
                    Result.failure(IOException("Không thể kết nối máy chủ. Hãy kiểm tra mạng và thử lại.", error))
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
        }

    private fun toFailure(error: HttpException): IOException {
        val body = error.response()?.errorBody()
        if (error.code() >= 500) {
            // Server error bodies may contain provider details. Do not display them.
            body?.close()
            return IOException("Máy chủ tạm thời gặp lỗi (${error.code()}). Vui lòng thử lại.", error)
        }
        val serverMessage = try {
            body?.use { it.string() }
                ?.let { text -> runCatching { JSONObject(text).optString("message") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?.take(300)
        } catch (_: IOException) {
            null
        }
        val message = serverMessage ?: if (error.code() == 404) {
            "Không tìm thấy hội thoại hoặc API chat. Bản thử nghiệm chỉ hỗ trợ emulator hoặc localhost qua USB reverse."
        } else {
            "Máy chủ từ chối yêu cầu chat (${error.code()})."
        }
        return ChatRequestRejectedException(message, error)
    }
}
