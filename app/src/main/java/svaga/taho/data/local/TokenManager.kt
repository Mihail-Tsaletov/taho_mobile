package svaga.taho.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
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
        private val NAME_KEY = stringPreferencesKey("name")
        private val PHONE_KEY = stringPreferencesKey("phone")
    }

    val roleFlow: Flow<String?> = dataStore.data.map { it[ROLE_KEY] }
    val lastModeDriverFlow: Flow<Boolean> = dataStore.data.map { it[LAST_MODE_DRIVER] ?: false }
    val nameFlow: Flow<String?> = dataStore.data.map { it[NAME_KEY] }
    val phoneFlow: Flow<String?> = dataStore.data.map { it[PHONE_KEY] }

    val tokenFlow: Flow<String> = dataStore.data
        .map { it[TOKEN_KEY] ?: "" }
    val currentTokenValue: String
        get() = runBlocking { tokenFlow.first() }

    suspend fun saveAuth(token: String, role: String, name: String, phone: String) {
        Log.d("TokenManager", "Сохраняем: token=$token, role=$role, name=$name, phone=$phone")
        dataStore.edit {
            it[TOKEN_KEY] = token
            it[ROLE_KEY] = role
            it[NAME_KEY] = name
            it[PHONE_KEY] = phone
        }
    }

    suspend fun saveToken(token: String) {
        dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun setLastModeDriver(isDriver: Boolean) {
        dataStore.edit { it[LAST_MODE_DRIVER] = isDriver }
    }

    suspend fun clear() = dataStore.edit { it.clear() }
}