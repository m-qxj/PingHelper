package com.pinghelper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class AppTheme { SYSTEM, LIGHT, DARK_BLACK, LUXURY_GREY }

private val DarkBlackColorScheme = darkColorScheme(
    background = Color(0xFF000000), surface = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF), onSurface = Color(0xFFEEEEEE)
)
private val LuxuryGreyColorScheme = darkColorScheme(
    background = Color(0xFF121214), surface = Color(0xFF1C1C1E),
    onBackground = Color(0xFFE5E5EA), onSurface = Color(0xFFD1D1D6)
)
private val LightColorScheme = lightColorScheme(
    background = Color(0xFFF2F2F7), surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000), onSurface = Color(0xFF1C1C1E)
)

@Composable
fun PingHelperTheme(selectedTheme: AppTheme, content: @Composable () -> Unit) {
    val colors = when (selectedTheme) {
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK_BLACK -> DarkBlackColorScheme
        AppTheme.LUXURY_GREY -> LuxuryGreyColorScheme
        AppTheme.SYSTEM -> DarkBlackColorScheme
    }
    MaterialTheme(colorScheme = colors, content = content)
}

data class AppInfo(val name: String, val usageMB: Int, var isBlocked: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentTheme by remember { mutableStateOf(AppTheme.DARK_BLACK) }
            var showSplash by remember { mutableStateOf(true) }

            PingHelperTheme(selectedTheme = currentTheme) {
                if (showSplash) {
                    SplashScreen(onTimeout = { showSplash = false })
                } else {
                    MainScreen(currentTheme = currentTheme, onThemeChange = { currentTheme = it })
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2500)
        onTimeout()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ping helper", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("made by Saudi Arabia , mohib", color = Color(0xFF555555), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(currentTheme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    val appsList = remember {
        mutableStateListOf(
            AppInfo("YouTube", 450),
            AppInfo("TikTok", 320),
            AppInfo("Instagram", 180),
            AppInfo("Chrome", 95)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ping Helper") },
                actions = {
                    TextButton(onClick = { showSettings = !showSettings }) {
                        Text(if (showSettings) "القائمة" else "الإعدادات")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showSettings) {
                SettingsView(currentTheme, onThemeChange)
            } else {
                AppListView(appsList)
            }
        }
    }
}

@Composable
fun AppListView(appsList: List<AppInfo>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("التطبيقات المستهلكة للإنترنت (آخر ساعة):", fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        items(appsList) { app ->
            var blocked by remember { mutableStateOf(app.isBlocked) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(app.name, fontSize = 18.sp)
                        Text("استهلاك الساعة: ${app.usageMB} MB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = !blocked,
                        onCheckedChange = {
                            blocked = !it
                            app.isBlocked = blocked
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsView(currentTheme: AppTheme, onThemeChange: (AppTheme) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("مظهر التطبيق (Theme):", fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
        val themes = listOf(
            "حسب النظام" to AppTheme.SYSTEM,
            "اللون الفاتح" to AppTheme.LIGHT,
            "الأسود الداكن" to AppTheme.DARK_BLACK,
            "رمادي غامق فخم" to AppTheme.LUXURY_GREY
        )
        themes.forEach { (label, theme) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = (currentTheme == theme), onClick = { onThemeChange(theme) })
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
