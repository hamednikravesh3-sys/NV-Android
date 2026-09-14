package ir.nv.navigation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.map.NvCodePickerMap

@Composable
fun RahnamaCodePickerOverlay(
    state: NvUiState,
    viewModel: NvViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate) }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("مکان انتخابی") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعریف کد روی نقشه") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("نقشه را جابه‌جا کنید؛ نشانگر قرمز وسط نقشه محل دقیق کد است.")
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    NvCodePickerMap(context, selected, state.satelliteMode, { selected = it }, Modifier.fillMaxSize())
                }
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("نام مکان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(9); error = null },
                    label = { Text("کد عددی شخصی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                selected?.let { Text("${"%.6f".format(it.latitude)}, ${"%.6f".format(it.longitude)}", style = MaterialTheme.typography.labelSmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val point: Coordinate = selected ?: run { error = "ابتدا یک نقطه روی نقشه انتخاب کنید"; return@Button }
                if (code.isBlank()) { error = "کد عددی را وارد کنید"; return@Button }
                val place = Place(code = -1L, name = name.ifBlank { "مکان انتخابی" }, coordinate = point, category = "map-picked")
                viewModel.savePersonalCode(place, code)
                onDismiss()
            }) { Text("ذخیره کد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
