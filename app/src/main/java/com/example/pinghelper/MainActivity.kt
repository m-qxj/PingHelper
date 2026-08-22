package com.example.pinghelper

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class AppDataUsage(
    val appName: String,
    val packageName: String,
    val totalBytes: Long,
    var isBlocked: Boolean = false
)

data class ServerPingResult(
    val name: String,
    val host: String,
    var pingMs: Long = -1,
    var jitter: Long = 0,
    var packetLoss: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PingHelperTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun PingHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF00E676),
            secondary = Color(0xFF00E5FF),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                    label = { Text("الألعاب والـ Ping") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DataUsage, contentDescription = null) },
                    label = { Text("استهلاك الشبكة") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    label = { Text("تشخيص الـ IP") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> GamingPingScreen()
                1 -> NetworkUsageScreen()
                2 -> NetworkDiagnosticsScreen()
            }
        }
    }
}

// ---------------- 1. شاشة البينج والألعاب ----------------
@Composable
fun GamingPingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var servers by remember {
        mutableStateOf(
            listOf(
                ServerPingResult("Cloudflare Gaming DNS", "1.1.1.1"),
                ServerPingResult("Google DNS", "8.8.8.8"),
                ServerPingResult("PUBG Mobile (EU)", "162.249.172.1"),
                ServerPingResult("Valorant (EU)", "162.249.170.1"),
                ServerPingResult("League of Legends", "104.160.141.3")
            )
        )
    }

    var isTesting by remember { mutableStateOf(false) }
    var isDnsActive by remember { mutableStateOf(false) }
    var isFloatingActive by remember { mutableStateOf(false) }

    fun runPingTests() {
        isTesting = true
        scope.launch(Dispatchers.IO) {
            val updated = servers.map { server ->
                val times = mutableListOf<Long>()
                var lostCount = 0
                for (i in 1..4) {
                    val time = pingHost(server.host)
                    if (time >= 0) times.add(time) else lostCount++
                    delay(150)
                }

                val avgPing = if (times.isNotEmpty()) times.average().toLong() else -1
                val jitter = if (times.size > 1) (times.maxOrNull()!! - times.minOrNull()!!) else 0
                val loss = (lostCount * 25)

                server.copy(pingMs = avgPing, jitter = jitter, packetLoss = loss)
            }
            withContext(Dispatchers.Main) {
                servers = updated
                isTesting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // الشعار والعنوان
        Row(verticalAlignment = Alignment.CenterVertically) {
            val imageResId = context.resources.getIdentifier("app_logo", "drawable", context.packageName)
            if (imageResId != 0) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = "Logo",
                    modifier = Modifier.size(50.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text("PingHelper Pro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("مركز تحسين الألعاب والـ Ping", fontSize = 12.sp, color = Color(0xFF00E676))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // كرت التحكم السريع بـ DNS والنافذة العائمة
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡ تسريع DNS والألعاب (VPN)", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = isDnsActive,
                        onCheckedChange = { active ->
                            isDnsActive = active
                            val intent = Intent(context, PingVpnService::class.java)
                            if (active) {
                                val vpnIntent = VpnService.prepare(context)
                                if (vpnIntent != null) {
                                    context.startActivity(vpnIntent)
                                } else {
                                    context.startService(intent)
                                }
                            } else {
                                intent.action = "STOP"
                                context.startService(intent)
                            }
                        }
                    )
                }

                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎈 النافذة العائمة فوق الألعاب", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = isFloatingActive,
                        onCheckedChange = { active ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            } else {
                                isFloatingActive = active
                                val intent = Intent(context, FloatingPingService::class.java)
                                if (active) context.startService(intent) else context.stopService(intent)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { runPingTests() },
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
        ) {
            Text(if (isTesting) "جاري اختبار السيرفرات..." else "فحص البينج الحالي الآن 🎯", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(servers) { server ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(server.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Text("Jitter: ${server.jitter}ms | Loss: ${server.packetLoss}%", color = Color.Gray, fontSize = 11.sp)
                        }

                        val pingColor = when {
                            server.pingMs < 0 -> Color.Gray
                            server.pingMs < 80 -> Color(0xFF00E676)
                            server.pingMs < 150 -> Color(0xFFFFC107)
                            else -> Color(0xFFFF5252)
                        }

                        Text(
                            text = if (server.pingMs >= 0) "${server.pingMs} ms" else "--",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = pingColor
                        )
                    }
                }
            }
        }
    }
}

private fun pingHost(host: String): Long {
    return try {
        val start = System.currentTimeMillis()
        val address = InetAddress.getByName(host)
        if (address.isReachable(800)) {
            System.currentTimeMillis() - start
        } else -1
    } catch (_: Exception) { -1 }
}

// ---------------- 2. شاشة استهلاك الإنترنت والجدار الناري ----------------
@Composable
fun NetworkUsageScreen() {
    val context = LocalContext.current
    var appList by remember { mutableStateOf<List<AppDataUsage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var daysFilter by remember { mutableStateOf(30) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val apps = getAppsUsageForDays(context, daysFilter)
            withContext(Dispatchers.Main) {
                appList = apps
                isLoading = false
            }
        }
    }

    LaunchedEffect(daysFilter) {
        loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text("مراقب البيانات والجدار الناري", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(selected = daysFilter == 1, onClick = { daysFilter = 1 }, label = { Text("اليوم") })
            FilterChip(selected = daysFilter == 7, onClick = { daysFilter = 7 }, label = { Text("آخر 7 أيام") })
            FilterChip(selected = daysFilter == 30, onClick = { daysFilter = 30 }, label = { Text("آخر 30 يوم") })
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(appList) { app ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(formatBytes(app.totalBytes), color = Color(0xFF00E676), fontSize = 12.sp)
                            }

                            IconButton(onClick = { openAppSettings(context, app.packageName) }) {
                                Icon(Icons.Default.Settings, contentDescription = "إعدادات", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 3. شاشة تشخيص الـ IP والشبكة ----------------
@Composable
fun NetworkDiagnosticsScreen() {
    val context = LocalContext.current
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val wifiInfo = wifiManager.connectionInfo
    val ssid = wifiInfo.ssid.replace("\"", "")
    val rssi = wifiInfo.rssi
    val ipAddress = formatIpAddress(wifiInfo.ipAddress)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text("معلومات وتخخيص الشبكة", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Spacer(modifier = Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("اسم الشبكة (SSID): $ssid", color = Color.White, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("عنوان الـ IP المحلي: $ipAddress", color = Color(0xFF00E5FF), fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("قوة الإشارة: $rssi dBm", color = Color.LightGray, fontSize = 14.sp)
            }
        }
    }
}

fun formatIpAddress(ip: Int): String {
    return String.format(
        "%d.%d.%d.%d",
        ip and 0xff,
        ip shr 8 and 0xff,
        ip shr 16 and 0xff,
        ip shr 24 and 0xff
    )
}

fun openAppSettings(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

fun getAppsUsageForDays(context: Context, days: Int): List<AppDataUsage> {
    val pm = context.packageManager
    val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    val result = mutableListOf<AppDataUsage>()

    val endTime = System.currentTimeMillis()
    val startTime = endTime - (days.toLong() * 24 * 60 * 60 * 1000)

    for (app in apps) {
        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (!isSystem || app.packageName == context.packageName) {
            val name = pm.getApplicationLabel(app).toString()
            var bytes = 0L

            try {
                val stats = nsm.queryDetailsForUid(ConnectivityManager.TYPE_WIFI, "", startTime, endTime, app.uid)
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    bytes += bucket.rxBytes + bucket.txBytes
                }
                stats.close()
            } catch (_: Exception) {}

            result.add(AppDataUsage(appName = name, packageName = app.packageName, totalBytes = bytes))
        }
    }
    return result.sortedByDescending { it.totalBytes }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}
