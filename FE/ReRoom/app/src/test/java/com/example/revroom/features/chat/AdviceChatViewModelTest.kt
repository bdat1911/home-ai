package com.example.revroom.features.chat

import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.example.revroom.data.remote.ChatCitationResponse
import com.example.revroom.data.remote.ChatMessageResponse
import com.example.revroom.data.remote.ConfirmDesignBriefResponse
import com.example.revroom.data.remote.ConversationResponse
import com.example.revroom.data.remote.DesignBriefResponse
import com.example.revroom.data.remote.SendChatMessageResponse
import com.example.revroom.data.remote.UpdateDesignBriefRequest
import com.example.revroom.data.repository.ChatGateway
import com.example.revroom.data.repository.ChatRequestRejectedException
import com.example.revroom.features.chat.viewmodel.AdviceChatViewModel
import com.example.revroom.features.design_studio.model.DesignJobStatus
import com.example.revroom.features.design_studio.model.DesignResult
import com.example.revroom.features.design_studio.model.DesignStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AdviceChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val image: Uri = mock(Uri::class.java)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun viewModel(gateway: FakeChatGateway, server: () -> String = { SERVER_A }, name: String = "vm") =
        AdviceChatViewModel(gateway, serverUrl = server).also { store.put(name, it) }

    @Test fun textAdviceIsSavedAndAvailableAfterViewModelRecreation() = runTest {
        val gateway = FakeChatGateway()
        val first = viewModel(gateway, name = "first")
        runCurrent()

        first.onDraftChanged("Scandinavian")
        first.send()
        runCurrent()

        assertEquals(2, first.uiState.value.messages.size)
        assertEquals("", first.uiState.value.draft)
        assertNotNull(first.uiState.value.conversationId)

        val restored = viewModel(gateway, name = "restored")
        runCurrent()
        assertEquals(first.uiState.value.conversationId, restored.uiState.value.conversationId)
        assertEquals(2, restored.uiState.value.messages.size)
    }

    @Test fun retryAfterLostReplyUsesSameClientMessageId() = runTest {
        val gateway = FakeChatGateway().apply { failFirstSend = true }
        val vm = viewModel(gateway)
        runCurrent()

        vm.onDraftChanged("Tư vấn Scandinavian")
        vm.send()
        runCurrent()
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertNotNull(vm.uiState.value.error)
        assertEquals("Tư vấn Scandinavian", vm.uiState.value.draft)

        vm.send()
        runCurrent()
        assertEquals(2, gateway.sentIds.size)
        assertEquals(gateway.sentIds[0], gateway.sentIds[1])
        assertEquals(2, vm.uiState.value.messages.size)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun changedServerDoesNotSendOldConversationToNewServer() = runTest {
        val gateway = FakeChatGateway()
        var server = SERVER_A
        val vm = viewModel(gateway, { server })
        runCurrent()
        vm.onDraftChanged("Scandinavian")
        server = "http://another-server:5207/"

        vm.send()
        runCurrent()
        assertTrue(gateway.sentIds.isEmpty())
        assertNotNull(vm.uiState.value.error)
        assertEquals("Scandinavian", vm.uiState.value.draft)
    }

    @Test fun citationsAndBriefComeFromTheServerReply() = runTest {
        val gateway = FakeChatGateway().apply {
            nextBrief = DesignBriefResponse("ignored", 1, roomType = "kitchen", styleId = 2, styleName = "Indochine")
            nextCitations = listOf(ChatCitationResponse("doc#a-1", "doc", "Bài", "Tài liệu nội bộ Home AI"))
        }
        val vm = viewModel(gateway)
        runCurrent()

        vm.onDraftChanged("Bếp Indochine")
        vm.send()
        runCurrent()

        val state = vm.uiState.value
        assertEquals("doc#a-1", state.messages.last().citations?.single()?.chunkId)
        assertEquals("Indochine", state.brief?.styleName)
        assertEquals("kitchen", state.brief?.roomType)
    }

    @Test fun styleNamedOnlyInAReplyDoesNotChangeTheBrief() = runTest {
        val gateway = FakeChatGateway().apply {
            nextBrief = DesignBriefResponse("ignored", 0)
            assistantText = "Bạn có thể thử Scandinavian."
        }
        val vm = viewModel(gateway)
        runCurrent()

        vm.onDraftChanged("Gợi ý giúp tôi")
        vm.send()
        runCurrent()

        assertNull(vm.uiState.value.brief?.styleId)
    }

    @Test fun roomSelectionSendsCurrentVersionAndCreatesConversationWhenNeeded() = runTest {
        val gateway = FakeChatGateway()
        val vm = viewModel(gateway)
        runCurrent()

        vm.selectRoom("bathroom")
        runCurrent()

        assertEquals(1, gateway.conversationCount)
        assertEquals(0, gateway.updates.single().expectedVersion)
        assertEquals("bathroom", vm.uiState.value.brief?.roomType)

        vm.selectRoom("kitchen")
        runCurrent()
        assertEquals(1, gateway.updates[1].expectedVersion)
    }

    @Test fun rejectedBriefUpdateReloadsTheServerValues() = runTest {
        val gateway = FakeChatGateway()
        val vm = viewModel(gateway)
        runCurrent()
        vm.selectRoom("bathroom")
        runCurrent()

        gateway.rejectNextUpdate = true
        vm.selectRoom("kitchen")
        runCurrent()

        assertNotNull(vm.uiState.value.designError)
        assertEquals("bathroom", vm.uiState.value.brief?.roomType)
    }

    @Test fun confirmIsBlockedUntilBriefAndImageAreComplete() = runTest {
        val gateway = FakeChatGateway()
        val vm = viewModel(gateway)
        runCurrent()

        vm.selectImage(image)
        vm.confirmDesign("", "", "")
        runCurrent()

        assertTrue(gateway.confirmRequests.isEmpty())
        assertNotNull(vm.uiState.value.designError)
    }

    @Test fun confirmCreatesJobMessageAndTracksItToCompletion() = runTest {
        val gateway = FakeChatGateway()
        val vm = readyToConfirm(gateway)

        vm.confirmDesign("", "", "")
        runCurrent()

        val state = vm.uiState.value
        assertEquals(1, gateway.confirmRequests.size)
        assertEquals("job-1", state.messages.last().designId)
        assertNull(state.selectedImageUri)
        assertEquals(DesignJobStatus.Pending, state.designJobs["job-1"]?.status)

        gateway.finishedJobs += "job-1"
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(DesignJobStatus.Completed, vm.uiState.value.designJobs["job-1"]?.status)
        assertEquals("https://images.test/job-1.png", vm.uiState.value.designJobs["job-1"]?.designedImageUrl)
    }

    @Test fun doubleTapSendsOneConfirmation() = runTest {
        val gateway = FakeChatGateway()
        val vm = readyToConfirm(gateway)

        vm.confirmDesign("", "", "")
        vm.confirmDesign("", "", "")
        runCurrent()

        assertEquals(1, gateway.confirmRequests.size)
    }

    @Test fun lostConfirmResponseRetriesWithSameRequestId() = runTest {
        val gateway = FakeChatGateway().apply { loseNextConfirm = true }
        val vm = readyToConfirm(gateway)

        vm.confirmDesign("", "", "")
        runCurrent()
        assertNotNull(vm.uiState.value.designError)
        assertNotNull(vm.uiState.value.selectedImageUri)

        vm.confirmDesign("", "", "")
        runCurrent()

        assertEquals(2, gateway.confirmRequests.size)
        assertEquals(gateway.confirmRequests[0].second, gateway.confirmRequests[1].second)
        assertEquals("job-1", vm.uiState.value.messages.last().designId)
    }

    @Test fun rejectedConfirmUsesNewRequestIdNextTime() = runTest {
        val gateway = FakeChatGateway().apply { rejectNextConfirm = true }
        val vm = readyToConfirm(gateway)

        vm.confirmDesign("", "", "")
        runCurrent()
        vm.confirmDesign("", "", "")
        runCurrent()

        assertNotEquals(gateway.confirmRequests[0].second, gateway.confirmRequests[1].second)
    }

    @Test fun changingImageAfterLostResponseUsesNewRequestId() = runTest {
        val gateway = FakeChatGateway().apply { loseNextConfirm = true }
        val vm = readyToConfirm(gateway)

        vm.confirmDesign("", "", "")
        runCurrent()
        vm.selectImage(mock(Uri::class.java))
        vm.confirmDesign("", "", "")
        runCurrent()

        assertNotEquals(gateway.confirmRequests[0].second, gateway.confirmRequests[1].second)
    }

    @Test fun editedTextIsSavedBeforeConfirmingWithNewVersion() = runTest {
        val gateway = FakeChatGateway()
        val vm = readyToConfirm(gateway)
        val versionBefore = vm.uiState.value.brief?.version ?: -1

        vm.confirmDesign("xanh navy", "giữ sofa", "")
        runCurrent()

        assertEquals("xanh navy", gateway.updates.last().colorPreference)
        assertEquals(versionBefore + 1, gateway.confirmRequests.single().first)
    }

    @Test fun reopeningConversationResumesTrackingItsJobs() = runTest {
        val gateway = FakeChatGateway()
        val first = readyToConfirm(gateway, name = "first")
        first.confirmDesign("", "", "")
        runCurrent()
        gateway.finishedJobs += "job-1"

        val reopened = viewModel(gateway, name = "reopened")
        runCurrent()

        assertEquals("kitchen", reopened.uiState.value.brief?.roomType)
        assertEquals(DesignJobStatus.Completed, reopened.uiState.value.designJobs["job-1"]?.status)
    }

    @Test fun lostStatusAsksForManualCheckAndCheckResumes() = runTest {
        val gateway = FakeChatGateway()
        val vm = readyToConfirm(gateway)
        gateway.failStatus = true

        vm.confirmDesign("", "", "")
        runCurrent()
        assertTrue(vm.uiState.value.designJobs["job-1"]?.needsManualCheck == true)

        gateway.failStatus = false
        gateway.finishedJobs += "job-1"
        vm.checkDesignStatus("job-1")
        runCurrent()
        assertEquals(DesignJobStatus.Completed, vm.uiState.value.designJobs["job-1"]?.status)
    }

    private fun kotlinx.coroutines.test.TestScope.readyToConfirm(
        gateway: FakeChatGateway,
        name: String = "vm"
    ): AdviceChatViewModel {
        val vm = viewModel(gateway, name = name)
        runCurrent()
        vm.selectRoom("kitchen")
        runCurrent()
        vm.selectStyle(2)
        runCurrent()
        vm.selectImage(image)
        return vm
    }

    private class FakeChatGateway : ChatGateway {
        private val conversations = mutableListOf<ConversationResponse>()
        private val messagesByConversation = mutableMapOf<String, MutableList<ChatMessageResponse>>()
        private val briefs = mutableMapOf<String, DesignBriefResponse>()
        val sentIds = mutableListOf<String>()
        val updates = mutableListOf<UpdateDesignBriefRequest>()
        val confirmRequests = mutableListOf<Pair<Int, String>>()
        val finishedJobs = mutableSetOf<String>()
        var failFirstSend = false
        var rejectNextUpdate = false
        var loseNextConfirm = false
        var rejectNextConfirm = false
        var failStatus = false
        var nextBrief: DesignBriefResponse? = null
        var nextCitations: List<ChatCitationResponse>? = null
        var assistantText = "Gợi ý phong cách"
        val conversationCount get() = conversations.size

        override suspend fun conversations(): Result<List<ConversationResponse>> =
            Result.success(conversations.toList())

        override suspend fun createConversation(clientConversationId: String): Result<ConversationResponse> {
            val existing = conversations.firstOrNull { it.id == clientConversationId }
            if (existing != null) return Result.success(existing)
            val created = ConversationResponse(clientConversationId, "Cuộc trò chuyện mới", "now", "now")
            conversations.add(0, created)
            return Result.success(created)
        }

        override suspend fun messages(conversationId: String): Result<List<ChatMessageResponse>> =
            Result.success(messagesByConversation[conversationId]?.toList() ?: emptyList())

        override suspend fun sendMessage(
            conversationId: String,
            clientMessageId: String,
            content: String
        ): Result<SendChatMessageResponse> {
            sentIds.add(clientMessageId)
            val saved = messagesByConversation.getOrPut(conversationId) { mutableListOf() }
            val user = saved.firstOrNull { it.id == clientMessageId }
                ?: ChatMessageResponse(clientMessageId, "user", content, "now").also { saved.add(it) }
            val assistant = saved.firstOrNull { it.id == "reply-$clientMessageId" }
                ?: ChatMessageResponse("reply-$clientMessageId", "assistant", assistantText, "now",
                    citations = nextCitations).also { saved.add(it) }
            if (failFirstSend) {
                failFirstSend = false
                return Result.failure(IOException("Lost response"))
            }
            nextBrief?.let { briefs[conversationId] = it.copy(conversationId = conversationId) }
            return Result.success(SendChatMessageResponse(user, assistant, briefs[conversationId]))
        }

        override suspend fun brief(conversationId: String): Result<DesignBriefResponse> =
            Result.success(briefs[conversationId] ?: DesignBriefResponse(conversationId, 0))

        override suspend fun updateBrief(
            conversationId: String,
            request: UpdateDesignBriefRequest
        ): Result<DesignBriefResponse> {
            updates.add(request)
            if (rejectNextUpdate) {
                rejectNextUpdate = false
                return Result.failure(ChatRequestRejectedException("Yêu cầu thiết kế đã thay đổi."))
            }
            val current = briefs[conversationId] ?: DesignBriefResponse(conversationId, 0)
            val updated = current.copy(
                version = current.version + 1,
                roomType = request.roomType ?: current.roomType,
                styleId = request.styleId ?: current.styleId,
                styleName = if (request.styleId != null) "Indochine" else current.styleName,
                featureId = request.featureId ?: current.featureId,
                colorPreference = request.colorPreference ?: current.colorPreference,
                mustKeep = request.mustKeep ?: current.mustKeep,
                mustAvoid = request.mustAvoid ?: current.mustAvoid
            )
            briefs[conversationId] = updated
            return Result.success(updated)
        }

        override suspend fun confirmBrief(
            conversationId: String,
            briefVersion: Int,
            clientRequestId: String,
            image: Uri
        ): Result<ConfirmDesignBriefResponse> {
            confirmRequests.add(briefVersion to clientRequestId)
            if (loseNextConfirm) {
                loseNextConfirm = false
                return Result.failure(IOException("Lost response"))
            }
            if (rejectNextConfirm) {
                rejectNextConfirm = false
                return Result.failure(ChatRequestRejectedException("Ảnh không hợp lệ."))
            }
            val message = ChatMessageResponse("message-job-1", "assistant", "Đã nhận ảnh", "now",
                kind = "design_job", designId = "job-1")
            messagesByConversation.getOrPut(conversationId) { mutableListOf() }.add(message)
            val brief = briefs.getValue(conversationId).copy(status = "confirmed")
            briefs[conversationId] = brief
            return Result.success(ConfirmDesignBriefResponse("job-1", "pending", message, brief))
        }

        override suspend fun designStyles(): Result<List<DesignStyle>> = Result.success(emptyList())

        override suspend fun designStatus(designId: String): Result<DesignResult> {
            if (failStatus) return Result.failure(IOException("Offline"))
            val done = designId in finishedJobs
            return Result.success(DesignResult(
                designId,
                if (done) DesignJobStatus.Completed else DesignJobStatus.Pending,
                "https://images.test/original.png",
                if (done) "https://images.test/$designId.png" else null,
                null
            ))
        }
    }

    private companion object { const val SERVER_A = "http://127.0.0.1:5207/" }
}
