package com.example.pinghelper

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.Uri
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
import androidx.compose.material.icons.filled.MoreVert
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppDataUsage(
    val appName: String,
    val packageName: String,
    val totalBytes: Long
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
    val scope = rememberCoroutineScope()

    fun loadData() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val apps = getAppsDataUsage(context)
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // الشعار
        val imageResId = context.resources.getIdentifier("app_logo", "drawable", context.packageName)
        if (imageResId != 0) {
            Image(
                painter = painterResource(id = imageResId),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "مراقب استهلاك الإنترنت",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

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
                        text = "يرجى منح صلاحية الوصول لإحصائيات الاستخدام لترتيب التطبيقات بدقة.",
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
                        Text("منح الصلاحية الآن", color = Color.Black, fontWeight = FontWeight.Bold)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التطبيقات مرتبة حسب الاستهلاك الفعلي:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    TextButton(onClick = { loadData() }) {
                        Text("تحديث", color = Color(0xFF00E676))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(appList, key = { it.packageName }) { app ->
                        AppItemRow(app = app)
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemRow(app: AppDataUsage) {
    val context = LocalContext.current
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
                Text(
                    text = app.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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
                            Text("إدارة إنترنت التطبيق (النظام)", color = Color(0xFF00E676))
                        },
                        onClick = {
                            menuExpanded = false
                            openAppSettings(context, app.packageName)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("معلومات التطبيق", color = Color.White)
                        },
                        onClick = {
                            menuExpanded = false
                            openAppSettings(context, app.packageName)
                        }
                    )
                }
            }
        }
    }
}

fun openAppSettings(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(Settings.ACTION_SETTINGS)
        context.startActivity(intent)
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

fun getAppsDataUsage(context: Context): List<AppDataUsage> {
    val packageManager = context.packageManager
    val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val result = mutableListOf<AppDataUsage>()

    val startTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
    val endTime = System.currentTimeMillis()

    for (appInfo in installedApps) {
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (!isSystemApp || appInfo.packageName == context.packageName) {
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val uid = appInfo.uid

            var totalBytes = 0L

            totalBytes += getBytesForUid(networkStatsManager, ConnectivityManager.TYPE_WIFI, uid, startTime, endTime)
            totalBytes += getBytesForUid(networkStatsManager, ConnectivityManager.TYPE_MOBILE, uid, startTime, endTime)

            if (totalBytes == 0L) {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx != TrafficStats.UNSUPPORTED.toLong() && tx != TrafficStats.UNSUPPORTED.toLong()) {
                    totalBytes = rx + tx
                }
            }

            result.add(
                AppDataUsage(
                    appName = appName,
                    packageName = appInfo.packageName,
                    totalBytes = if (totalBytes < 0) 0 else totalBytes
                )
            )
        }
    }

    return result.sortedByDescending { it.totalBytes }
}

private fun getBytesForUid(
    networkStatsManager: NetworkStatsManager,
    networkType: Int,
    uid: Int,
    startTime: Long,
    endTime: Long
): Long {
    var bytes = 0L
    try {
        val stats = networkStatsManager.queryDetailsForUid(
            networkType,
            "",
            startTime,
            endTime,
            uid
        )
        val bucket = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
            stats.getNextBucket(bucket)
            bytes += bucket.rxBytes + bucket.txBytes
        }
        stats.close()
    } catch (_: Exception) {}
    return bytes
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
