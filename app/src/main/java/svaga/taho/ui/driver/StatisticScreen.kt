package svaga.taho.ui.driver

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import svaga.taho.data.remote.OrderWeb
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// Модель заказа (заглушка)
data class DriverTrip(
    val id: String,
    val createdAt: LocalDateTime,
    val price: Int,
    val durationMinutes: Int,
    val startAddress: String,
    val endAddress: String
)

// Заглушка данных
private val mockTripsWeek = listOf(
    DriverTrip("1", LocalDateTime.now().minusDays(1), 1200, 25, "ул. Туманяна 5", "ул. Абовяна 12"),
    DriverTrip("2", LocalDateTime.now().minusDays(3), 850, 18, "ул. Московян 8", "ул. Саят-Нова 3"),
    DriverTrip("3", LocalDateTime.now().minusDays(5), 2100, 42, "ул. Налбандяна 10", "аэропорт Звартноц")
)

private val mockTripsMonth = mockTripsWeek + listOf(
    DriverTrip("4", LocalDateTime.now().minusDays(10), 950, 20, "ул. Амиряна 7", "ул. Сарьяна 2"),
    DriverTrip("5", LocalDateTime.now().minusDays(15), 1800, 35, "ул. Комитаса 45", "ул. Аршакуняц 56")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(navController: NavController) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("За неделю") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("За месяц") }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> TripList(trips = mockTripsWeek)
                    1 -> TripList(trips = mockTripsMonth)
                }
            }
        }
    }
}

@Composable
private fun TripList(trips: List<DriverTrip>) {
    if (trips.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нет поездок за этот период",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(trips) { trip ->
                TripItem(trip = trip)
            }
        }
    }
}

@Composable
private fun TripItem(trip: DriverTrip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trip.createdAt.format(DateTimeFormatter.ofPattern("dd MMM HH:mm")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${trip.price} ₽",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Время в пути: ${trip.durationMinutes} мин",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "От: ${trip.startAddress}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = "До: ${trip.endAddress}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}