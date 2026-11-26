package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.UserEntity
import com.example.ui.AppRoute
import com.example.ui.theme.BorderCard
import com.example.ui.theme.BorderInput
import com.example.ui.theme.DangerBg
import com.example.ui.theme.DangerBorder
import com.example.ui.theme.DangerText
import com.example.ui.theme.EmeraldHover
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ForestDark
import com.example.ui.theme.ForestDarker
import com.example.ui.theme.SuccessBg
import com.example.ui.theme.SuccessBorder
import com.example.ui.theme.SuccessText
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.WarningBg
import com.example.ui.theme.WarningBorder
import com.example.ui.theme.WarningText
import com.example.ui.theme.YellowBtnBg
import com.example.ui.theme.YellowPanelBg
import com.example.ui.theme.YellowPanelBorder
import com.example.ui.theme.YellowText

@Composable
fun PortalTopHeader(
    appName: String,
    user: UserEntity,
    currentRoute: AppRoute,
    onSelectRoute: (AppRoute) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleRoutes = remember(user.role) {
        AppRoute.entries.filter { !it.ownerOnly || user.role == "Owner" }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ForestDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Logo",
                    tint = YellowPanelBorder,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = appName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${user.namaLengkap} (@${user.username} · ${user.role})",
                        color = Color(0xFFD1E7DC),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = ForestDarker,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFF3E6A56)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Keluar",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Keluar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Scrollable horizontal navigation tabs matching portal top nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ForestDarker)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visibleRoutes.forEach { route ->
                val active = route == currentRoute
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) EmeraldPrimary else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (active) EmeraldPrimary else Color(0xFF2B4D3D),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectRoute(route) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("nav_${route.name.lowercase()}")
                ) {
                    Text(
                        text = route.title,
                        color = if (active) Color.White else Color(0xFFD8E8DF),
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun FeedbackBanners(
    okMessage: String?,
    errMessage: String?,
    onDismiss: () -> Unit
) {
    if (!okMessage.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(SuccessBg)
                .border(1.dp, SuccessBorder, RoundedCornerShape(7.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = okMessage,
                color = SuccessText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup pesan", tint = SuccessText, modifier = Modifier.size(16.dp))
            }
        }
    }
    if (!errMessage.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(DangerBg)
                .border(1.dp, DangerBorder, RoundedCornerShape(7.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = errMessage,
                color = DangerText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup error", tint = DangerText, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    isYellow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = if (isYellow) YellowPanelBg else SurfaceWhite
    val border = if (isYellow) YellowPanelBorder else BorderCard
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelHeader(
    title: String,
    icon: ImageVector? = null,
    actions: @Composable () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions()
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    linkLabel: String? = null,
    onLinkClick: (() -> Unit)? = null,
    isYellow: Boolean = false,
    modifier: Modifier = Modifier
) {
    PanelCard(modifier = modifier, isYellow = isYellow) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }
        if (!linkLabel.isNullOrBlank() && onLinkClick != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = linkLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = EmeraldPrimary,
                modifier = Modifier.clickable { onLinkClick() }
            )
        }
    }
}

enum class BtnStyle { DEFAULT, PRIMARY, YELLOW, DANGER }

@Composable
fun PortalButton(
    text: String,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.DEFAULT,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val bgColor = when (style) {
        BtnStyle.PRIMARY -> EmeraldPrimary
        BtnStyle.YELLOW -> YellowBtnBg
        BtnStyle.DANGER -> DangerBg
        BtnStyle.DEFAULT -> SurfaceWhite
    }
    val txtColor = when (style) {
        BtnStyle.PRIMARY -> Color.White
        BtnStyle.YELLOW -> YellowText
        BtnStyle.DANGER -> DangerText
        BtnStyle.DEFAULT -> TextPrimary
    }
    val borderColor = when (style) {
        BtnStyle.PRIMARY -> EmeraldHover
        BtnStyle.YELLOW -> YellowPanelBorder
        BtnStyle.DANGER -> DangerBorder
        BtnStyle.DEFAULT -> BorderInput
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = txtColor,
            disabledContainerColor = BorderCard,
            disabledContentColor = TextMuted
        ),
        border = BorderStroke(1.dp, borderColor),
        contentPadding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 6.dp) else PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontSize = if (compact) 11.5.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun StatusTag(
    label: String,
    bg: Color,
    fg: Color,
    border: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GadaiStatusBadge(status: String) {
    when (status) {
        "AKTIF" -> StatusTag("AKTIF", SuccessBg, SuccessText, SuccessBorder)
        "DIPERPANJANG" -> StatusTag("DIPERPANJANG", WarningBg, WarningText, WarningBorder)
        "LUNAS" -> StatusTag("LUNAS", Color(0xFFEFF6FF), Color(0xFF1E40AF), Color(0xFFBFDBFE))
        "LELANG" -> StatusTag("LELANG", DangerBg, DangerText, DangerBorder)
        else -> StatusTag(status, SurfaceSubtle, TextMuted, BorderCard)
    }
}

@Composable
fun PortalInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    readOnly: Boolean = false,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = singleLine,
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, fontSize = 12.5.sp, color = TextMuted) }
            } else null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
                color = TextPrimary
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (readOnly) SurfaceSubtle else SurfaceWhite,
                unfocusedContainerColor = if (readOnly) SurfaceSubtle else SurfaceWhite,
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = BorderInput
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun <T> PortalDropdown(
    label: String,
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "-- Pilih --"
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceWhite)
                    .border(1.dp, BorderInput, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedItem != null) itemLabel(selectedItem) else placeholder,
                    fontSize = 13.sp,
                    color = if (selectedItem != null) TextPrimary else TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Pilih",
                    tint = TextPrimary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceWhite)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item), fontSize = 13.sp, color = TextPrimary) },
                        onClick = {
                            expanded = false
                            onSelect(item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String = "Ya, Lanjutkan",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = { Text(message, color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            PortalButton(
                text = confirmText,
                style = BtnStyle.DANGER,
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextMuted)
            }
        }
    )
}
