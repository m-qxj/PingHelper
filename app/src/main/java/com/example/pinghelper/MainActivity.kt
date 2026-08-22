package com.example.pinghelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    PingScreen()
                }
            }
        }
    }
}

@Composable
fun PingScreen() {
    var hostInput by remember { mutableStateOf("8.8.8.8") }
    var pingResult by remember { mutableStateOf("اضغط على الفحص للبدء") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PingHelper Tool",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            label = { Text("IP / Hostname", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E676),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00E676),
                cursorColor = Color(0xFF00E676)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (!isLoading) {
                    isLoading = true
                    pingResult = "جاري قياس الـ Ping..."
                    scope.launch(Dispatchers.IO) {
                        val result = executePing(hostInput)
                        withContext(Dispatchers.Main) {
                            pingResult = result
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            } else {
                Text("بدء الفحص (Start Ping)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = pingResult,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

fun executePing(host: String): String {
    return try {
        val process = Runtime.getRuntime().exec("ping -c 4 $host")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }

        reader.close()
        process.waitFor()

        if (output.isNotEmpty()) {
            output.toString()
        } else {
            "فشل الاتصال بالخادم."
        }
    } catch (e: Exception) {
        "حدث خطأ أثناء الفحص: ${e.message}"
    }
}
