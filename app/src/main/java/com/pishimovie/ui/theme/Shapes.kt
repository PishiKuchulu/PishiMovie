package com.pishimovie.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * PishiMovie shape system.
 */
val PishiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Direct shape tokens — signature card shape from Part 9
val ShapePill = RoundedCornerShape(50)
val ShapeCardDefault = RoundedCornerShape(20.dp)
val ShapeSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
