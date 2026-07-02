package com.stocksim.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stocksim.app.ui.theme.TextSecondary
import com.stocksim.app.ui.theme.UpRed
import com.stocksim.app.util.formatYen

private val presets = listOf(
    1_000_000L to "100万円",
    3_000_000L to "300万円",
    5_000_000L to "500万円",
    10_000_000L to "1,000万円",
)

/** 初回起動時の初期資金設定画面 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(onStart: (Double) -> Unit) {
    var selectedPreset by remember { mutableLongStateOf(5_000_000L) }
    var customText by remember { mutableStateOf("") }

    val customAmount = customText.toLongOrNull()
    val amount = customAmount ?: selectedPreset

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ShowChart,
            contentDescription = null,
            tint = UpRed,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("株シミュ", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "実際の株価 × 架空の資金で腕試し。\n初期資金を決めて投資シミュレーションを始めよう。",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))
        Text(
            text = "初期資金",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { (value, label) ->
                FilterChip(
                    selected = customAmount == null && selectedPreset == value,
                    onClick = {
                        selectedPreset = value
                        customText = ""
                    },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = customText,
            onValueChange = { input -> customText = input.filter { it.isDigit() }.take(12) },
            label = { Text("カスタム金額（円）") },
            supportingText = { Text("10,000円以上") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onStart(amount.toDouble()) },
            enabled = amount >= 10_000,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${formatYen(amount.toDouble())} で始める")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "株価はYahoo!ファイナンス由来の15〜20分遅延データです。\n使うお金はすべて架空のもので、実際の売買は行われません。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
