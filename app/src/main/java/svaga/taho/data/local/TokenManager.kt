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
        private val ARRIVED_AT_KEY = longPreferencesKey("arrived_at_ms")// время прибытия (мс)
        private val PAID_START_KEY = longPreferencesKey("paid_wait_start_ms") // время начала платного ожидания (мс)
        private val LAST_ROLE_KEY = stringPreferencesKey("last_role") // ← новый, не чистится
    }

    val roleFlow: Flow<String?> = dataStore.data.map { it[ROLE_KEY] }
    val lastModeDriverFlow: Flow<Boolean> = dataStore.data.map { it[LAST_MODE_DRIVER] ?: false }
    val nameFlow: Flow<String?> = dataStore.data.map { it[NAME_KEY] }
    val phoneFlow: Flow<String?> = dataStore.data.map { it[PHONE_KEY] }
    val arrivedAtFlow: Flow<Long> = dataStore.data.map { it[ARRIVED_AT_KEY] ?: 0L }
    val paidWaitStartFlow: Flow<Long> = dataStore.data.map { it[PAID_START_KEY] ?: 0L }
    val lastRoleFlow: Flow<String?> = dataStore.data.map { it[LAST_ROLE_KEY] }

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
    private val DRIVER_ID_KEY = stringPreferencesKey("driver_id")

    val driverIdFlow: Flow<String?> = dataStore.data.map { it[DRIVER_ID_KEY] }

    suspend fun saveDriverId(driverId: String) {
        dataStore.edit {
            it[DRIVER_ID_KEY] = driverId
        }
    }

    suspend fun saveLastRole(role: String) {
        dataStore.edit { it[LAST_ROLE_KEY] = role }
    }


    suspend fun saveToken(token: String) {
        dataStore.edit { it[TOKEN_KEY] = token }
    }

    suspend fun setLastModeDriver(isDriver: Boolean) {
        dataStore.edit { it[LAST_MODE_DRIVER] = isDriver }
    }

    suspend fun clear() = dataStore.edit { it.clear()
    }
    suspend fun saveArrivedAt(timeMs: Long) {
        dataStore.edit { it[ARRIVED_AT_KEY] = timeMs }
    }

    suspend fun savePaidWaitStart(timeMs: Long) { dataStore.edit { it[PAID_START_KEY] = timeMs } }

    suspend fun clearTimers() {
        dataStore.edit {
            it.remove(ARRIVED_AT_KEY)
            it.remove(PAID_START_KEY) }
    }
}