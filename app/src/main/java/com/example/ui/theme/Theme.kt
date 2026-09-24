package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
  primary = ForestGreenPrimary,
  onPrimary = Color.White,
  primaryContainer = ForestGreenContainer,
  onPrimaryContainer = OnForestGreenContainer,
  secondary = ForestGreenPrimary,
  onSecondary = Color.White,
  background = PureWhite,
  surface = PureWhite,
  onBackground = TextPrimary,
  onSurface = TextPrimary,
  outline = InputBorderUnfocused,
  outlineVariant = BorderGreyDark,
  inverseSurface = InverseSurfaceDark,
  inverseOnSurface = InverseOnSurfaceLight,
  error = ErrorRed,
  onError = Color.White,
  errorContainer = ErrorBgLight,
  onErrorContainer = DangerRed
)

@Composable
fun SusuLedgerTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit,
) {
  // SusuLedger Figma Design Spec (v2): Background is pure white #FFFFFF on every screen
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  SusuLedgerTheme(content = content)
}
