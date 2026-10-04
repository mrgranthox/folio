package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun getCategoryVectorIcon(iconKey: String?, categoryName: String?): ImageVector {
    val key = (iconKey ?: categoryName ?: "").lowercase()
    return when {
        key.contains("food") || key.contains("dining") || key.contains("utensil") || key.contains("restaurant") -> Icons.Default.Restaurant
        key.contains("transport") || key.contains("car") || key.contains("fuel") || key.contains("ride") -> Icons.Default.DirectionsCar
        key.contains("shop") || key.contains("bag") || key.contains("grocer") -> Icons.Default.ShoppingBag
        key.contains("bill") || key.contains("util") || key.contains("zap") || key.contains("power") || key.contains("ecg") -> Icons.Default.FlashOn
        key.contains("business") || key.contains("work") || key.contains("office") || key.contains("briefcase") -> Icons.Default.Work
        key.contains("entertain") || key.contains("film") || key.contains("movie") || key.contains("game") -> Icons.Default.Movie
        key.contains("health") || key.contains("pulse") || key.contains("fit") || key.contains("medic") -> Icons.Default.Favorite
        key.contains("income") || key.contains("coin") || key.contains("salary") || key.contains("money") -> Icons.Default.AttachMoney
        key.contains("momo") || key.contains("mobile") || key.contains("phone") -> Icons.Default.PhoneAndroid
        key.contains("bank") -> Icons.Default.AccountBalance
        key.contains("card") -> Icons.Default.Payment
        key.contains("cash") -> Icons.Default.LocalAtm
        else -> Icons.Default.Category
    }
}

fun parseColorHex(hex: String?, fallback: Color = Color(0xFF3B82F6)): Color {
    if (hex == null || hex.isBlank()) return fallback
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = if (clean.length == 6) {
            "FF$clean".toLong(16).toInt()
        } else {
            clean.toLong(16).toInt()
        }
        Color(colorInt)
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun CategoryIconBadge(
    categoryName: String?,
    iconKey: String? = null,
    colorHex: String? = null,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val baseColor = parseColorHex(colorHex)
    val icon = getCategoryVectorIcon(iconKey, categoryName)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(baseColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = categoryName ?: "Category",
            tint = baseColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
