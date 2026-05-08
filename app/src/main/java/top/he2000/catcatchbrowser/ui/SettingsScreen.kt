package top.he2000.catcatchbrowser.ui

import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.he2000.catcatchbrowser.data.UserPreferencesRepository
import top.he2000.catcatchbrowser.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val desktopSite by viewModel.desktopSite.collectAsState()
    val homepageSetting by viewModel.homepageUrlSetting.collectAsState()
    val searchTemplate by viewModel.searchTemplate.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var homepageDraft by remember(homepageSetting) { mutableStateOf(homepageSetting) }
    var searchDraft by remember(searchTemplate) { mutableStateOf(searchTemplate) }

    LaunchedEffect(homepageSetting) {
        homepageDraft = homepageSetting
    }
    LaunchedEffect(searchTemplate) {
        searchDraft = searchTemplate
    }

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showClearCookiesConfirm by remember { mutableStateOf(false) }

    val versionName = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            "—"
        }
    }

    val webViewPkg = remember {
        WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "—"
    }

    val webViewMajorVersion = remember {
        try {
            WebSettings.getDefaultUserAgent(context)
                .substringAfter("Chrome/")
                .substringBefore(".")
                .ifBlank { "—" }
        } catch (_: Exception) {
            "—"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ThemeOptionRow(
                    label = "跟随系统",
                    selected = themeMode == UserPreferencesRepository.THEME_FOLLOW_SYSTEM,
                    onClick = { viewModel.setThemeMode(UserPreferencesRepository.THEME_FOLLOW_SYSTEM) }
                )
                ThemeOptionRow(
                    label = "浅色",
                    selected = themeMode == UserPreferencesRepository.THEME_LIGHT,
                    onClick = { viewModel.setThemeMode(UserPreferencesRepository.THEME_LIGHT) }
                )
                ThemeOptionRow(
                    label = "深色",
                    selected = themeMode == UserPreferencesRepository.THEME_DARK,
                    onClick = { viewModel.setThemeMode(UserPreferencesRepository.THEME_DARK) }
                )
            }
            Text(
                "切换主题后立即生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("浏览", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("桌面版网站")
                    Text(
                        "使用桌面浏览器标识访问网页（部分站点排版会变化）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = desktopSite,
                    onCheckedChange = { viewModel.setDesktopSite(it) }
                )
            }

            OutlinedTextField(
                value = homepageDraft,
                onValueChange = { homepageDraft = it },
                label = { Text("自定义首页 URL") },
                placeholder = { Text("留空使用内置书签页 (${MainViewModel.HOME_URL})") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    viewModel.setHomepageUrlSetting(homepageDraft)
                    scope.launch {
                        snackbarHostState.showSnackbar("首页已保存")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存首页")
            }

            Text("搜索引擎模板", style = MaterialTheme.typography.labelLarge)
            Text(
                "须包含 %s，程序会将搜索词 URL 编码后替换该占位符。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        searchDraft = UserPreferencesRepository.SEARCH_TEMPLATE_BING
                    },
                    label = { Text("必应") }
                )
                AssistChip(
                    onClick = {
                        searchDraft = UserPreferencesRepository.SEARCH_TEMPLATE_GOOGLE
                    },
                    label = { Text("Google") }
                )
                AssistChip(
                    onClick = {
                        searchDraft = UserPreferencesRepository.SEARCH_TEMPLATE_BAIDU
                    },
                    label = { Text("百度") }
                )
            }

            OutlinedTextField(
                value = searchDraft,
                onValueChange = { searchDraft = it },
                label = { Text("搜索 URL 模板") },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_SEARCH_TEMPLATE) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    if (!searchDraft.contains("%s")) {
                        scope.launch {
                            snackbarHostState.showSnackbar("模板须包含 %s")
                        }
                    } else {
                        viewModel.setSearchTemplateSetting(searchDraft)
                        scope.launch {
                            snackbarHostState.showSnackbar("搜索模板已保存")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存搜索模板")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("隐私与数据", style = MaterialTheme.typography.titleMedium)

            Text(
                "清除站点数据后，各网站登录状态会丢失；清除缓存主要释放本地网页缓存，不影响历史记录数据库中的条目（可在「历史记录」页单独清空）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { showClearCacheConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除 WebView 缓存")
            }

            OutlinedButton(
                onClick = { showClearCookiesConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除 Cookie 与站点存储")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text("应用版本：$versionName", style = MaterialTheme.typography.bodyMedium)
            Text("WebView 组件：$webViewPkg", style = MaterialTheme.typography.bodyMedium)
            Text("UA 内核版本（Chrome）：$webViewMajorVersion", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("清除 WebView 缓存") },
            text = { Text("将删除网页模板缓存与内存缓存，下次访问可能稍慢。是否继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        viewModel.clearWebViewDiskCache()
                        scope.launch {
                            snackbarHostState.showSnackbar("已请求清除缓存")
                        }
                    }
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearCookiesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCookiesConfirm = false },
            title = { Text("清除 Cookie 与站点存储") },
            text = { Text("所有网站的登录状态与本地存储将被清除，确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCookiesConfirm = false
                        viewModel.clearCookiesAndSiteStorage()
                        scope.launch {
                            snackbarHostState.showSnackbar("已清除 Cookie 与站点存储")
                        }
                    }
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCookiesConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
