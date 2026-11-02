package com.example.revroom.features.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.revroom.core.network.ConnectionCheck
import com.example.revroom.core.network.ServerEndpoint
import com.example.revroom.core.ui.StudioMuted
import com.example.revroom.core.ui.StudioPurple
import com.example.revroom.core.ui.StudioText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val EMULATOR_DEBUG_API_URL = "http://10.0.2.2:5207/"
private const val USB_DEBUG_API_URL = "http://127.0.0.1:5207/"

@Composable
fun ServerConnectionCard(onSaved: () -> Unit, enabled: Boolean = true, canSave: () -> Boolean = { true }) {
    val latestCanSave by rememberUpdatedState(canSave)
    val currentUrl by ServerEndpoint.baseUrl.collectAsState()
    var draft by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var isError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun checkAndOptionallySave(save: Boolean, candidate: String = draft) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val result = ConnectionCheck.test(candidate)
                if (save) {
                    check(latestCanSave()) { "A profile request is running. Wait for it to finish, then try again." }
                    ServerEndpoint.save(candidate)
                    onSaved()
                }
                isError = false
                message = if (save) "Saved. $result" else result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                isError = true
                message = ConnectionCheck.errorMessage(error)
            } finally {
                busy = false
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Server connection · Debug", color = StudioText, fontWeight = FontWeight.Bold)
            Text("Current: $currentUrl", color = StudioMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; message = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server address") },
                placeholder = { Text("http://192.168.1.10:5207/") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                enabled = enabled && !busy,
            )
            Text(
                "For LAN, connect the phone and computer to a network that allows them to communicate. " +
                    "When the computer IP changes, enter the new address here. Changing this does not connect separate networks.",
                color = StudioMuted, fontSize = 12.sp,
            )
            Text("Emulator: use the emulator button below. USB phone: run adb reverse tcp:5207 tcp:5207, then use the USB button. The chat demo requires one of these two, not LAN.",
                color = StudioMuted, fontSize = 12.sp)
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = StudioPurple)
            message?.let {
                Text(it, color = if (isError) Color(0xFFB3261E) else Color(0xFF1B7F4B), fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { checkAndOptionallySave(false) }, enabled = enabled && !busy) {
                    Text("Test connection")
                }
                Button(onClick = { checkAndOptionallySave(true) }, enabled = enabled && !busy) {
                    Text("Test & save")
                }
            }
            OutlinedButton(
                onClick = {
                    draft = EMULATOR_DEBUG_API_URL
                    checkAndOptionallySave(save = true, candidate = EMULATOR_DEBUG_API_URL)
                },
                enabled = enabled && !busy,
            ) {
                Text("Use emulator (test & save)")
            }
            OutlinedButton(
                onClick = {
                    draft = USB_DEBUG_API_URL
                    checkAndOptionallySave(save = true, candidate = USB_DEBUG_API_URL)
                },
                enabled = enabled && !busy,
            ) {
                Text("Use USB localhost (test & save)")
            }
        }
    }
}
