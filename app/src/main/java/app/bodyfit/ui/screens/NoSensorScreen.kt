package app.bodyfit.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import app.bodyfit.R

/**
 * Shown in place of the app when the phone has no hardware step counter.
 *
 * Every activity number the app reports comes from that one sensor, so without it the app
 * has nothing to show and a working-looking screen of zeros would be a lie. The dialog
 * cannot be dismissed by tapping outside or by the back button: the only way on is out,
 * because there is no state the user can change that would make the sensor appear.
 */
@Composable
fun NoSensorScreen(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text("⚠️  No motion sensors") },
            text = {
                Text(
                    text = "This phone has neither a step counter nor an accelerometer, and " +
                        "${stringResource(R.string.app_name)} counts every number it shows from " +
                        "one or the other. Steps, distance, calories, move minutes and heart " +
                        "points would all read zero.\n\n" +
                        "The app cannot run here. Nothing on this phone is wrong and there is " +
                        "no setting to change: the hardware is simply not fitted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Close app") } },
        )
    }
}
