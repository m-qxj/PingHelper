package com.example.pinghelper

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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

data class AppDataUsage(
    val appName: String,
    val packageName: String,
    val bytesReceived: Long,
    val bytesTransferred: Long,
    val totalBytes: Long,
    var isBlocked: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    AppUsageScreen()
                }
            }
        }
    }
}

@Composable
fun AppUsageScreen() {
    val context = LocalContext.current
    var appList by remember { mutableStateOf<List<AppDataUsage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(checkUsagePermission(context)) }
    val blockedPackages = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    fun loadData() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val apps = getAppsDataUsage(context, blockedPackages)
            withContext(Dispatchers.Main) {
                appList = apps
                isLoading = false
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loadData()
        } else {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "مراقب استهلاك البيانات",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "يتطلب التطبيق صلاحية الوصول لإحصائيات استخدام البيانات لترتيب التطبيقات.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("منح الصلاحية من الإعدادات", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00E676))
                }
            } else {
                Text(
                    text = "التطبيقات مرتبة من الأكثر استهلاكاً إلى الأقل:",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(appList, key = { it.packageName }) { app ->
                        AppItemRow(
                            app = app,
                            onBlockToggle = { shouldBlock ->
                                if (shouldBlock) {
                                    if (!blockedPackages.contains(app.packageName)) {
                                        blockedPackages.add(app.packageName)
                                    }
                                } else {
                                    blockedPackages.remove(app.packageName)
                                }
                                loadData()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemRow(
    app: AppDataUsage,
    onBlockToggle: (Boolean) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (app.isBlocked) Color(0xFFFF5252) else Color(0xFF00E676),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (app.isBlocked) "محظور" else "نشط",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "الاستهلاك: ${formatBytes(app.totalBytes)}",
                    fontSize = 13.sp,
                    color = Color(0xFF00E676)
                )
                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "خيارات",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color(0xFF2C2C2C))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "إيقاف إنترنت التطبيق",
                                color = if (app.isBlocked) Color.Gray else Color(0xFFFF5252)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onBlockToggle(true)
                        },
                        enabled = !app.isBlocked
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "إرجاع إنترنت التطبيق",
                                color = if (!app.isBlocked) Color.Gray else Color(0xFF00E676)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onBlockToggle(false)
                        },
                        enabled = app.isBlocked
                    )
                }
            }
        }
    }
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

fun getAppsDataUsage(context: Context, blockedPackages: List<String>): List<AppDataUsage> {
    val packageManager = context.packageManager
    val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val result = mutableListOf<AppDataUsage>()

    val startTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // آخر 30 يوم
    val endTime = System.currentTimeMillis()

    for (appInfo in installedApps) {
        // تصفية تطبيقات النظام وعرض تطبيقات المستخدم فقط
        if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || appInfo.packageName == context.packageName) {
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val uid = appInfo.uid

            var rxBytes = 0L
            var txBytes = 0L

            try {
                val bucket = networkStatsManager.querySummaryForDevice(
                    ConnectivityManager.TYPE_WIFI,
                    "",
                    startTime,
                    endTime
                )
                rxBytes += bucket.rxBytes
                txBytes += bucket.txBytes
            } catch (_: Exception) {}

            try {
                val stats: NetworkStats = networkStatsManager.queryDetailsForUid(
                    ConnectivityManager.TYPE_MOBILE,
                    "",
                    startTime,
                    endTime,
                    uid
                )
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    rxBytes += bucket.rxBytes
                    txBytes += bucket.txBytes
                }
                stats.close()
            } catch (_: Exception) {}

            val totalBytes = rxBytes + txBytes
            val isBlocked = blockedPackages.contains(appInfo.packageName)

            result.add(
                AppDataUsage(
                    appName = appName,
                    packageName = appInfo.packageName,
                    bytesReceived = rxBytes,
                    bytesTransferred = txBytes,
                    totalBytes = totalBytes,
                    isBlocked = isBlocked
                )
            )
        }
    }

    // ترتيب التطبيقات من الأكثر استهلاكاً إلى الأقل
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
