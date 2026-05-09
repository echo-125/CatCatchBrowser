package top.he2000.catcatchbrowser.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {

    val desktopSite: Flow<Boolean> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading desktopSite", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_DESKTOP_SITE] ?: false }

    val homepageUrl: Flow<String> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading homepageUrl", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_HOMEPAGE_URL] ?: "" }

    val searchTemplate: Flow<String> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading searchTemplate", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_SEARCH_TEMPLATE] ?: DEFAULT_SEARCH_TEMPLATE }

    /** 0 = 跟随系统, 1 = 浅色, 2 = 深色 */
    val themeMode: Flow<Int> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading themeMode", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_THEME_MODE] ?: THEME_FOLLOW_SYSTEM }

    val downloadPath: Flow<String> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading downloadPath", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_DOWNLOAD_PATH] ?: "" }

    val concurrentDownloads: Flow<Int> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading concurrentDownloads", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_CONCURRENT_DOWNLOADS] ?: 3 }

    val autoRetry: Flow<Boolean> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading autoRetry", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_AUTO_RETRY] ?: true }

    val maxRetries: Flow<Int> = context.userPrefsDataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("UserPrefs", "Error reading maxRetries", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it[PREF_MAX_RETRIES] ?: 3 }

    suspend fun setDesktopSite(value: Boolean): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_DESKTOP_SITE] = value }
    }

    suspend fun setHomepageUrl(value: String): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_HOMEPAGE_URL] = value }
    }

    suspend fun setSearchTemplate(value: String): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_SEARCH_TEMPLATE] = value }
    }

    suspend fun setThemeMode(mode: Int): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_THEME_MODE] = mode }
    }

    suspend fun setDownloadPath(value: String): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_DOWNLOAD_PATH] = value }
    }

    suspend fun setConcurrentDownloads(value: Int): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_CONCURRENT_DOWNLOADS] = value }
    }

    suspend fun setAutoRetry(value: Boolean): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_AUTO_RETRY] = value }
    }

    suspend fun setMaxRetries(value: Int): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_MAX_RETRIES] = value }
    }

    companion object {
        private val PREF_DESKTOP_SITE = booleanPreferencesKey("desktop_site")
        private val PREF_HOMEPAGE_URL = stringPreferencesKey("homepage_url")
        private val PREF_SEARCH_TEMPLATE = stringPreferencesKey("search_template")
        private val PREF_THEME_MODE = intPreferencesKey("theme_mode")
        private val PREF_DOWNLOAD_PATH = stringPreferencesKey("download_path")
        private val PREF_CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")
        private val PREF_AUTO_RETRY = booleanPreferencesKey("auto_retry")
        private val PREF_MAX_RETRIES = intPreferencesKey("max_retries")

        const val THEME_FOLLOW_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        const val DEFAULT_SEARCH_TEMPLATE = "https://www.bing.com/search?q=%s"

        val SEARCH_TEMPLATE_BING = DEFAULT_SEARCH_TEMPLATE
        const val SEARCH_TEMPLATE_GOOGLE = "https://www.google.com/search?q=%s"
        const val SEARCH_TEMPLATE_BAIDU = "https://www.baidu.com/s?wd=%s"

        @Volatile
        private var instance: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: UserPreferencesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
