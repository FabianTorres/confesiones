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
import androidx.compose.ui.Alignment
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val appBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    // --- Listas de Opciones (sin cambios) ---
    val genderOptions = mapOf(
        "male" to "Hombre",
        "female" to "Mujer",
        "other" to "Otro"
    )
    val ageOptions = (14..99).toList()
    val countryOptions = remember {
        Locale.getISOCountries().map { code ->
            val locale = Locale("es", code)
            code to locale.getDisplayCountry(locale)
        }.sortedBy { it.second }
    }

    // --- Estados Locales para la UI ---
    var selectedGenderKey by remember { mutableStateOf<String?>(null) }
    var selectedAge by remember { mutableStateOf<Int?>(null) }
    var selectedCountryCode by remember { mutableStateOf<String?>(null) }
    // --- NUEVO ESTADO PARA EL SWITCH ---
    var allowsMessaging by remember { mutableStateOf(true) }

    // Cuando el perfil carga de la BD, actualizamos todos los estados
    LaunchedEffect(uiState.profile) {
        uiState.profile?.let {
            selectedGenderKey = it.gender
            selectedAge = it.age
            selectedCountryCode = it.countryCode
            allowsMessaging = it.allowsMessaging // <-- AÑADIDO
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Perfil") }, // Título acortado
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.accessibility_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Botón de Guardar (llamada actualizada)
                    TextButton(onClick = {
                        vm.onSaveProfile(
                            gender = selectedGenderKey,
                            age = selectedAge,
                            countryCode = selectedCountryCode,
                            allowsMessaging = allowsMessaging // <-- AÑADIDO
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {

                // --- CAMPO DE NOMBRE ANÓNIMO (AÑADIDO) ---
                OutlinedTextField(
                    value = uiState.profile?.anonymousName ?: "Cargando...",
                    onValueChange = {},
                    label = { Text("Tu nombre anónimo") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true, // No se puede editar
                    supportingText = { Text("Este nombre es permanente y te identifica anónimamente.") }
                )

                // --- 1. Selector de Género (sin cambios) ---
                DropdownSelector(
                    label = "Género",
                    options = genderOptions,
                    selectedKey = selectedGenderKey,
                    onKeySelected = { selectedGenderKey = it }
                )

                // --- 2. Selector de Edad (sin cambios) ---
                DropdownSelector(
                    label = "Edad",
                    options = ageOptions.associateWith { it.toString() },
                    selectedKey = selectedAge,
                    onKeySelected = { selectedAge = it }
                )

                // --- 3. Selector de País (sin cambios) ---
                DropdownSelector(
                    label = "País",
                    options = countryOptions.toMap(),
                    selectedKey = selectedCountryCode,
                    onKeySelected = { selectedCountryCode = it }
                )

                // --- 4. PREFERENCIA DE MENSAJERÍA (AÑADIDO) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Aceptar mensajes privados",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f) // Ocupa el espacio restante
                    )
                    Switch(
                        checked = allowsMessaging,
                        onCheckedChange = { allowsMessaging = it }
                    )
                }
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