/**
 * Meowsic (C) 2026 — Menu Cheat rahasia
 *
 * Hanya muncul di Pengaturan (di bawah Tentang) setelah easter egg 10x ketuk
 * di Tentang kebuka. Isinya cheat level: tambah EXP custom.
 */
package com.metrolist.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.LevelTotalExpKey
import com.metrolist.music.level.MAX_TOTAL_EXP
import com.metrolist.music.level.expIntoLevel
import com.metrolist.music.level.levelForTotalExp
import com.metrolist.music.level.sanitizeTotalExp
import com.metrolist.music.level.tierForLevel
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheatScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var totalExp by rememberPreference(LevelTotalExpKey, 0L)
    val level = levelForTotalExp(totalExp)
    val tier = tierForLevel(level)
    var amountInput by rememberSaveable { mutableStateOf("") }

    // EXP di luar batas (diubah lewat kode / edit DataStore): reset ke 0.
    LaunchedEffect(totalExp) {
        if (sanitizeTotalExp(totalExp) != totalExp) {
            totalExp = 0L
            Toast.makeText(context, "EXP tidak valid, direset ke 0", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier =
            Modifier
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                windowInsets.only(WindowInsetsSides.Top),
            ),
        )
        Spacer(Modifier.height(16.dp))

        Text("Level kamu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Total EXP: $totalExp", style = MaterialTheme.typography.bodyLarge)
        Text("Level: $level (${tier.name})", style = MaterialTheme.typography.bodyLarge)
        Text("EXP ke level berikutnya: ${100 - expIntoLevel(totalExp)}", style = MaterialTheme.typography.bodyLarge)

        Spacer(Modifier.height(24.dp))

        Text("Tambah EXP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = it.filter { ch -> ch.isDigit() } },
            label = { Text("Jumlah EXP") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val amount = amountInput.toLongOrNull() ?: 0L
                if (amount <= 0L) {
                    Toast.makeText(context, "Masukkan jumlah EXP yang valid", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (totalExp > Long.MAX_VALUE - amount) {
                    Toast.makeText(context, "Jumlah terlalu besar", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                // Maksimal level 2000: tambah EXP tidak boleh lewat batas.
                val base = sanitizeTotalExp(totalExp)
                val added = minOf(amount, MAX_TOTAL_EXP - base)
                if (added <= 0L) {
                    Toast.makeText(context, "Sudah di level maksimal (2000)", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                totalExp = base + added
                amountInput = ""
                Toast.makeText(context, "EXP +$added ditambahkan. Total: $totalExp (Level ${levelForTotalExp(totalExp)})", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Tambah EXP")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "EXP dipakai buat level, unlock border, dan badge di Together.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text("Menu Cheat") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
