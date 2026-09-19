package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.BorderGrey

@Composable
fun AppLogoBadge(
  modifier: Modifier = Modifier,
  size: Dp = 72.dp,
  shapeRadius: Dp = (size.value * 0.25f).dp
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(shapeRadius))
      .background(Color(0xFF1B4D3E))
      .border(1.5.dp, BorderGrey, RoundedCornerShape(shapeRadius)),
    contentAlignment = Alignment.Center
  ) {
    Image(
      painter = painterResource(id = R.drawable.ic_launcher_background),
      contentDescription = null,
      modifier = Modifier.matchParentSize()
    )
    Image(
      painter = painterResource(id = R.drawable.ic_launcher_foreground),
      contentDescription = "SusuLedger Logo",
      modifier = Modifier.matchParentSize()
    )
  }
}
