package app.bodyfit.notification

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import app.bodyfit.data.HealthRepository
import app.bodyfit.data.UserSettings
import app.bodyfit.data.Volume
import app.bodyfit.ui.theme.BodyFitTheme
import kotlinx.coroutines.launch

/**
 * The water reminder, full screen, like an alarm.
 *
 * A reminder that only rings leaves the user hunting for what is making the noise, so the
 * ringing reminder opens this over the lock screen and turns the screen on. The sound comes
 * from the notification and keeps going until the user chooses: a cup logs the drink, Not now
 * (or Back) snoozes for [WaterReminder.SNOOZE_MINUTES]. Either way the notification is
 * cancelled, which stops the sound.
 */
class WaterReminderAlarmActivity : ComponentActivity() {

    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        // Back is a "not now", not a silent exit: leaving the reminder unanswered would
        // otherwise stop the sound with no follow-up at all.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = snoozeAndClose()
        })

        val repository = HealthRepository(applicationContext)
        setContent {
            BodyFitTheme {
                var settings by remember { mutableStateOf<UserSettings?>(null) }
                var drank by remember { mutableStateOf(0) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    settings = repository.currentSettings()
                    drank = repository.drinkState().drankTodayMl
                }
                settings?.let {
                    ReminderScreen(
                        settings = it,
                        drankMl = drank,
                        onCup = ::logAndClose,
                        onNotNow = ::snoozeAndClose,
                    )
                }
            }
        }
    }

    private fun logAndClose(amountMl: Int) {
        if (answered) return
        answered = true
        stopRinging()
        lifecycleScope.launch {
            HealthRepository(applicationContext).logWater(amountMl)
            finish()
        }
    }

    private fun snoozeAndClose() {
        if (answered) return
        answered = true
        stopRinging()
        WaterReminder.snooze(applicationContext, dismissedAt = System.currentTimeMillis())
        finish()
    }

    /** Cancelling the notification stops its sound; cancel() does not fire its delete intent. */
    private fun stopRinging() {
        NotificationManagerCompat.from(this).cancel(WaterReminder.NOTIFICATION_ID)
    }
}

@Composable
private fun ReminderScreen(
    settings: UserSettings,
    drankMl: Int,
    onCup: (Int) -> Unit,
    onNotNow: () -> Unit,
) {
    val goal = settings.waterGoalMl.coerceAtLeast(1)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "💧", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Time to drink water",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${Volume.format(drankMl)} of ${Volume.format(settings.waterGoalMl)} today",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (drankMl.toFloat() / goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Tap what you drank",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                settings.cupSizesMl.forEach { amount ->
                    Button(onClick = { onCup(amount) }, modifier = Modifier.weight(1f)) {
                        Text(text = "+$amount ml", maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) {
                Text("Not now, remind me in ${WaterReminder.SNOOZE_MINUTES} min")
            }
        }
    }
}
