package top.he2000.catcatchbrowser.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    suspend fun setDesktopSite(value: Boolean): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_DESKTOP_SITE] = value }
    }

    suspend fun setHomepageUrl(value: String): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_HOMEPAGE_URL] = value }
    }

    suspend fun setSearchTemplate(value: String): Result<Unit> = runCatching {
        context.userPrefsDataStore.edit { it[PREF_SEARCH_TEMPLATE] = value }
    }

    companion object {
        private val PREF_DESKTOP_SITE = booleanPreferencesKey("desktop_site")
        private val PREF_HOMEPAGE_URL = stringPreferencesKey("homepage_url")
        private val PREF_SEARCH_TEMPLATE = stringPreferencesKey("search_template")

        const val DEFAULT_SEARCH_TEMPLATE = "https://www.bing.com/search?q=%s"

        @Volatile
        private var instance: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: UserPreferencesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
