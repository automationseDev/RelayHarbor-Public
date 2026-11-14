package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The six-tab companion page. Fold/tablets show calendar + weather side by side. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarWeatherScreen(twoPane: Boolean) {
    Scaffold(topBar = { TopAppBar(title = { Text("カレンダー・天気") }) }) { padding ->
        if (twoPane) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .verticalScroll(rememberScrollState()).padding(12.dp)
                ) { DeviceCalendarSection() }
                VerticalDivider()
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .verticalScroll(rememberScrollState()).padding(12.dp)
                ) { WeatherForecastSection() }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DeviceCalendarSection()
                WeatherForecastSection()
            }
        }
    }
}
