package com.networkmarketing.planner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networkmarketing.planner.domain.compensation.PayoutBreakdown
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
private val number: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    maximumFractionDigits = 0
}

fun money(value: Double): String = currency.format(value)
fun qty(value: Double): String = number.format(value.roundToInt())
fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MetricRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun PayoutSummary(payout: PayoutBreakdown, incomeGoal: Double) {
    val progress = if (incomeGoal <= 0) 1f else (payout.estimatedMonthly / incomeGoal).toFloat().coerceIn(0f, 1f)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estimated monthly", style = MaterialTheme.typography.labelLarge)
            Text(money(payout.estimatedMonthly), style = MaterialTheme.typography.headlineMedium)
            Text(
                "${payout.currentRank.title} · ${percent(payout.performancePercent)} of ${qty(payout.group.bv)} group BV",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Progress toward ${money(incomeGoal)} income goal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DisclaimerBanner(compact: Boolean = false) {
    Text(
        if (compact) {
            "Unofficial planning estimates. Not affiliated with any company. Income is not guaranteed."
        } else {
            "This is an unofficial planning tool. It is not affiliated with, endorsed by, or sponsored by any network marketing company. Formulas are simplified estimates. Actual payouts, ranks, and rules vary by market. Income is not guaranteed."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
