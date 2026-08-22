package com.example.pinghelper

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

enum class AppLanguage { AR, EN }

data class AppDataUsage(
    val appName: String,
    val packageName: String,
    val totalBytes: Long
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
    var currentLang by remember { mutableStateOf(AppLanguage.AR) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                    label = { Text(if (currentLang == AppLanguage.AR) "الألعاب والـ Ping" else "Gaming & Ping") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DataUsage, contentDescription = null) },
                    label = { Text(if (currentLang == AppLanguage.AR) "استهلاك البيانات" else "Data Usage") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    label = { Text(if (currentLang == AppLanguage.AR) "تشخيص الـ IP" else "IP Diagnostics") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> GamingPingScreen(currentLang) { currentLang = it }
                1 -> NetworkUsageScreen(currentLang) { currentLang = it }
                2 -> NetworkDiagnosticsScreen(currentLang) { currentLang = it }
            }
        }
    }
}

@Composable
fun HeaderBar(lang: AppLanguage, onLangToggle: (AppLanguage) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val imageResId = context.resources.getIdentifier("app_logo", "drawable", context.packageName)
            if (imageResId != 0) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = "Logo",
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column {
                Text("PingHelper Pro", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (lang == AppLanguage.AR) "مُسرّع الألعاب ومراقب الشبكة" else "Gaming Booster & Network Monitor",
                    fontSize = 11.sp,
                    color = Color(0xFF00E676)
                )
            }
        }

        OutlinedButton(
            onClick = {
                onLangToggle(if (lang == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR)
            },
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(if (lang == AppLanguage.AR) "🇸🇦 العربية" else "🇬🇧 English", color = Color(0xFF00E5FF), fontSize = 12.sp)
        }
    }
}

@Composable
fun GamingPingScreen(lang: AppLanguage, onLangToggle: (AppLanguage) -> Unit) {
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

    var bestServerName by remember { mutableStateOf("Cloudflare (1.1.1.1)") }
    var bestServerIp by remember { mutableStateOf("1.1.1.1") }
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

            val best = updated.filter { it.pingMs >= 0 }.minByOrNull { it.pingMs }

            withContext(Dispatchers.Main) {
                servers = updated
                if (best != null) {
                    bestServerName = best.name
                    bestServerIp = best.host
                }
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
        HeaderBar(lang, onLangToggle)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2F23)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    if (lang == AppLanguage.AR) "🏆 أفضل سيرفر تم رصده:" else "🏆 Best Active Server:",
                    color = Color(0xFF00E676),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$bestServerName ($bestServerIp)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                    Text(
                        if (lang == AppLanguage.AR) "⚡ تفعيل تسريع أفضل DNS (VPN)" else "⚡ Activate Best DNS Booster (VPN)",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = isDnsActive,
                        onCheckedChange = { active ->
                            isDnsActive = active
                            val intent = Intent(context, PingVpnService::class.java).apply {
                                putExtra("DNS_IP", bestServerIp)
                            }
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
                    Text(
                        if (lang == AppLanguage.AR) "🎈 النافذة العائمة فوق الألعاب" else "🎈 Floating Ping Widget",
                        color = Color.White,
                        fontSize = 13.sp
                    )
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
            Text(
                if (isTesting) (if (lang == AppLanguage.AR) "جاري فحص واختيار الأفضل..." else "Finding Best Server...")
                else (if (lang == AppLanguage.AR) "فحص واختيار أفضل سيرفر 🎯" else "Run Test & Select Best 🎯"),
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
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
                            Text("IP: ${server.host} | Jitter: ${server.jitter}ms", color = Color.Gray, fontSize = 11.sp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkUsageScreen(lang: AppLanguage, onLangToggle: (AppLanguage) -> Unit) {
    val context = LocalContext.current
    var appList by remember { mutableStateOf<List<AppDataUsage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var daysFilter by remember { mutableStateOf(30) }
    var hasPermission by remember { mutableStateOf(checkUsagePermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isGranted = checkUsagePermission(context)
                hasPermission = isGranted
                if (isGranted) {
                    loadData()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(daysFilter) {
        if (hasPermission) {
            loadData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        HeaderBar(lang, onLangToggle)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            if (lang == AppLanguage.AR) "مراقب استهلاك البيانات" else "Data Usage Tracker",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (lang == AppLanguage.AR)
                            "تطلب هذه الميزة صلاحية الوصول لإحصائيات الاستخدام للترتيب بدقة."
                        else
                            "This feature requires Usage Access permission to track usage accurately.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text(
                            if (lang == AppLanguage.AR) "منح الصلاحية الآن" else "Grant Permission Now",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = daysFilter == 1,
                    onClick = { daysFilter = 1 },
                    label = { Text(if (lang == AppLanguage.AR) "اليوم" else "Today") }
                )
                FilterChip(
                    selected = daysFilter == 7,
                    onClick = { daysFilter = 7 },
                    label = { Text(if (lang == AppLanguage.AR) "آخر 7 أيام" else "Last 7 Days") }
                )
                FilterChip(
                    selected = daysFilter == 30,
                    onClick = { daysFilter = 30 },
                    label = { Text(if (lang == AppLanguage.AR) "آخر 30 يوم" else "Last 30 Days") }
                )
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
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkDiagnosticsScreen(lang: AppLanguage, onLangToggle: (AppLanguage) -> Unit) {
    val context = LocalContext.current
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

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
        HeaderBar(lang, onLangToggle)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            if (lang == AppLanguage.AR) "تشخيص وعنوان الشبكة" else "Network Diagnostics",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (lang == AppLanguage.AR) "اسم الشبكة (SSID): $ssid" else "WiFi SSID: $ssid",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (lang == AppLanguage.AR) "عنوان الـ IP المحلي: $ipAddress" else "Local IP Address: $ipAddress",
                    color = Color(0xFF00E5FF),
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (lang == AppLanguage.AR) "قوة الإشارة: $rssi dBm" else "Signal Strength: $rssi dBm",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
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

fun checkUsagePermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
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
