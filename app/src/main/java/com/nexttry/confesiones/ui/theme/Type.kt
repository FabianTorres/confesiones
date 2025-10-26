package com.nexttry.confesiones.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Definimos un conjunto más completo de estilos de tipografía de Material 3
val Typography = Typography(
    // Estilo para el texto principal de las confesiones
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp, // Ligeramente más grande para mejorar la lectura
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    // Estilo para textos de cuerpo más pequeños, como en diálogos o descripciones
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    // Estilo para los títulos de las pantallas (ej. "Confesiones Anónimas")
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // Estilo para títulos de secciones (ej. "Comentarios")
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium, // Un poco más de peso
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    // Estilo para metadatos (contadores de likes, fecha)
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)