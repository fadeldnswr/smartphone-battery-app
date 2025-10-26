package com.example.smartphonebatteryprediction.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartphonebatteryprediction.presentation.viewmodel.UIMetrics

@Composable
fun DashboardScreen(ui: UIMetrics, onStart: () -> Unit, onStop: () -> Unit){
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text(
                "Smartphone Condition Application",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Battery and Network Manager", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.padding(12.dp))

                    // Define two columns each for battery metrics and running application
                    TwoCols("Current (mA)", ui.currentMa?.let { "%.1f mA".format(it) } ?: "-",
                        "Voltage (V)", ui.voltageMv?.let { "%.2f V".format(it/1000.0) } ?: "-")
                    TwoCols("Temperature (C)", ui.temperatureC?.let { "%.0f C".format(it) } ?: "-",
                    "Running Application", ui.fgApp ?: "-")
                    
                    // Define two columns for network metrics
                    val dlMbps = ui.dlBps?.let { it / 1_000_000.0 }
                    val ulMbps = ui.ulBps?.let { it / 1_000_000.0 }
                    TwoCols("Throughput (DL, Mbps)", dlMbps?.let { "%.2f".format(it) } ?: "-",
                        "Throughput (UL, Mbps)", ulMbps?.let { "%.2f".format(it) } ?: "-")
                    TwoCols("Channel Quality (dBm)", ui.wifiRssiDbm?.let { "$it dBm" } ?: "-", "—", "—")
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(onClick = onStart) { Text("Start") }
                OutlinedButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

// Define private function to create two columns layout
@Composable
private fun TwoCols(l1:String, v1: String, l2:String, v2:String){
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricBox(label = l1, value = v1, modifier = Modifier.weight(1f))
        MetricBox(label = l2, value = v2, modifier = Modifier.weight(1f))
    }
}

// Define function to create metrix box layout
@Composable
private fun MetricBox(label:String, value:String, modifier: Modifier = Modifier){
    Card(modifier, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}