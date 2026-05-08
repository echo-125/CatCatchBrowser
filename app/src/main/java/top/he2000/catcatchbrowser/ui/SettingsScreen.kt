package top.he2000.catcatchbrowser.ui

import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.he2000.catcatchbrowser.data.UserPreferencesRepository
import top.he2000.catcatchbrowser.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    var homepageDraft by remember(homepageSetting) { mutableStateOf(homepageSetting) }
    var searchDraft by remember(searchTemplate) { mutableStateOf(searchTemplate) }

    LaunchedEffect(homepageSetting) {
        homepageDraft = homepageSetting
    }
    LaunchedEffect(searchTemplate) {
        searchDraft = searchTemplate
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("浏览", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("桌面版网站")
                Switch(
                    checked = desktopSite,
                    onCheckedChange = { viewModel.setDesktopSite(it) }
                )
            }

            OutlinedTextField(
                value = homepageDraft,
                onValueChange = { homepageDraft = it },
                label = { Text("自定义首页 URL") },
                placeholder = { Text("留空使用内置书签首页 (${MainViewModel.HOME_URL})") },
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

            OutlinedTextField(
                value = searchDraft,
                onValueChange = { searchDraft = it },
                label = { Text("搜索 URL 模板") },
                placeholder = { Text(UserPreferencesRepository.DEFAULT_SEARCH_TEMPLATE) },
                supportingText = {
                    Text("使用 %s 作为查询关键词占位符（会自动 URL 编码）")
                },
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

            HorizontalDivider()

            Text("隐私与数据", style = MaterialTheme.typography.titleMedium)

            Button(
                onClick = {
                    viewModel.clearAllHistory()
                    scope.launch {
                        snackbarHostState.showSnackbar("已清空历史记录")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清空历史记录")
            }

            Button(
                onClick = {
                    viewModel.clearCookiesAndSiteStorage()
                    scope.launch {
                        snackbarHostState.showSnackbar("已清除 Cookie 与站点存储")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除 Cookie 与站点存储")
            }

            HorizontalDivider()

            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text("应用版本：$versionName", style = MaterialTheme.typography.bodyMedium)
            Text("WebView：$webViewPkg", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
