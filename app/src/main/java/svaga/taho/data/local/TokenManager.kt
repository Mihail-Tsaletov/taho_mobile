package svaga.taho.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val ROLE_KEY = stringPreferencesKey("role")
        private val LAST_MODE_DRIVER = booleanPreferencesKey("last_mode_driver")
    }

    val tokenFlow: Flow<String?> = dataStore.data.map { it[TOKEN_KEY] }
    val roleFlow: Flow<String?> = dataStore.data.map { it[ROLE_KEY] }
    val lastModeDriverFlow: Flow<Boolean> = dataStore.data.map { it[LAST_MODE_DRIVER] ?: false }

    suspend fun saveAuth(token: String, role: String) {
        dataStore.edit {
            it[TOKEN_KEY] = token
            it[ROLE_KEY] = role
        }
    }

    suspend fun setLastModeDriver(isDriver: Boolean) {
        dataStore.edit { it[LAST_MODE_DRIVER] = isDriver }
    }

    suspend fun clear() = dataStore.edit { it.clear() }
}