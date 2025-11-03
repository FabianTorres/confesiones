package com.nexttry.confesiones.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttry.confesiones.R
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val appBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    // --- Listas de Opciones ---
    // (Clave, Valor a mostrar)
    val genderOptions = mapOf(
        "male" to "Hombre",
        "female" to "Mujer",
        "other" to "Otro"
    )
    val ageOptions = (14..99).toList()

    // Lista de países: (CL, Chile), (AR, Argentina), etc.
    val countryOptions = remember {
        Locale.getISOCountries().map { code ->
            val locale = Locale("es", code) // Usamos español
            code to locale.getDisplayCountry(locale)
        }.sortedBy { it.second } // Orden alfabético por nombre
    }

    // --- Estados Locales para la UI ---
    var selectedGenderKey by remember { mutableStateOf<String?>(null) }
    var selectedAge by remember { mutableStateOf<Int?>(null) }
    var selectedCountryCode by remember { mutableStateOf<String?>(null) }
    var selectedBadgePref by remember { mutableStateOf("none") } // "none", "gender", "age", "country"

    // Cuando el perfil carga de la BD, actualizamos los estados de la UI
    LaunchedEffect(uiState.profile) {
        uiState.profile?.let {
            selectedGenderKey = it.gender
            selectedAge = it.age
            selectedCountryCode = it.countryCode
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil Opcional") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.accessibility_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarContainerColor),
                actions = {
                    // Botón de Guardar en la barra
                    TextButton(onClick = {
                        vm.onSaveProfile(
                            gender = selectedGenderKey,
                            age = selectedAge,
                            countryCode = selectedCountryCode

                        )
                        onNavigateBack() // Cerramos
                    }) {
                        Text("Guardar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp) // Más espacio
        ) {

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(
                    "Esta información es opcional y se usará de forma anónima.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // --- 1. Selector de Género ---
                DropdownSelector(
                    label = "Género",
                    options = genderOptions, // Pasamos el Map
                    selectedKey = selectedGenderKey,
                    onKeySelected = { selectedGenderKey = it }
                )

                // --- 2. Selector de Edad ---
                DropdownSelector(
                    label = "Edad",
                    options = ageOptions.associateWith { it.toString() }, // (25, "25")
                    selectedKey = selectedAge,
                    onKeySelected = { selectedAge = it }
                )

                // --- 3. Selector de País ---
                DropdownSelector(
                    label = "País",
                    options = countryOptions.toMap(), // (CL, Chile)
                    selectedKey = selectedCountryCode,
                    onKeySelected = { selectedCountryCode = it }
                )
            }
        }
    }
}

/**
 * Un Composable reutilizable para nuestros menús desplegables (Dropdown).
 * Usa la clave (K) para la lógica interna y muestra el valor (String) al usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <K> DropdownSelector(
    label: String,
    options: Map<K, String>,
    selectedKey: K?,
    onKeySelected: (K?) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    // Texto que se muestra en el campo: el valor (ej. "Hombre") o vacío
    val selectedText = options[selectedKey] ?: ""

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {}, // No se escribe, solo se selecciona
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor() // Ancla el menú al campo de texto
        )

        // Contenido del menú desplegable
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            // Opción para "No especificar" o limpiar
            DropdownMenuItem(
                text = {
                    Text(
                        "No especificar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                onClick = {
                    onKeySelected(null) // Pasa null
                    isExpanded = false
                }
            )

            // Lista de opciones
            options.forEach { (key, value) ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onKeySelected(key) // Pasa la clave (ej. "male")
                        isExpanded = false
                    }
                )
            }
        }
    }
}