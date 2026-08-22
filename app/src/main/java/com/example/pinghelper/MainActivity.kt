package com.example.pinghelper

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.util.Collections

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
    val context = LocalContext.current
    var targetHost by remember { mutableStateOf("8.8.8.8") }
    var localIp by remember { mutableStateOf("جاري التعرف...") }
    var pingResult by remember { mutableStateOf("اضغط على زر الفحص للبدء") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        localIp = getDeviceIpAddress(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PingHelper Tool",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "IP جهازك الحالي: $localIp",
            fontSize = 14.sp,
            color = Color(0xFF00E676),
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = targetHost,
            onValueChange = { targetHost = it },
            label = { Text("السيرفر المستهدف (Default: Google)", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E676),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00E676),
                cursorColor = Color(0xFF00E676),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (!isLoading) {
                    isLoading = true
                    pingResult = "جاري الفحص..."
                    scope.launch(Dispatchers.IO) {
                        val hostToPing = if (targetHost.isBlank()) "8.8.8.8" else targetHost.trim()
                        val result = executePing(hostToPing)
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
                .heightIn(min = 120.dp)
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = pingResult,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

fun getDeviceIpAddress(context: Context): String {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        if (ipAddress != 0) {
            return Formatter.formatIpAddress(ipAddress)
        }
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val sAddr = addr.hostAddress
                    if (sAddr != null && sAddr.indexOf(':') < 0) {
                        return sAddr
                    }
                }
            }
        }
    } catch (e: Exception) {
        return "غير معروف"
    }
    return "غير متصل"
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
            "فشل الاتصال بالخادم ($host). تأكد من الاتصال بالإنترنت."
        }
    } catch (e: Exception) {
        "حدث خطأ أثناء الفحص: ${e.message}"
    }
}
