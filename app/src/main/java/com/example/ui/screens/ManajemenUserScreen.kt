package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JurnalAkuntansiEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.data.db.UserEntity
import com.example.ui.AppRoute
import com.example.ui.AppViewModel
import com.example.ui.components.BtnStyle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelHeader
import com.example.ui.components.PortalButton
import com.example.ui.components.PortalDropdown
import com.example.ui.components.PortalInputField
import com.example.ui.components.StatusTag
import com.example.ui.theme.BorderCard
import com.example.ui.theme.DangerBg
import com.example.ui.theme.DangerBorder
import com.example.ui.theme.DangerText
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessBg
import com.example.ui.theme.SuccessBorder
import com.example.ui.theme.SuccessText
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManajemenUserScreen(
    vm: AppViewModel,
    currentUser: UserEntity,
    pengaturan: PengaturanSistemEntity,
    users: List<UserEntity>,
    transaksiList: List<TransaksiGadaiEntity>,
    jurnalList: List<JurnalAkuntansiEntity>
) {
    var editingUser by remember { mutableStateOf<UserEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<UserEntity?>(null) }

    var newUsername by remember { mutableStateOf("") }
    var newNama by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("Kasir") }
    var newHp by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        editingUser?.let { eu ->
            var eUsername by remember(eu.id) { mutableStateOf(eu.username) }
            var eNama by remember(eu.id) { mutableStateOf(eu.namaLengkap) }
            var ePass by remember(eu.id) { mutableStateOf("") }
            var eRole by remember(eu.id) { mutableStateOf(eu.role) }
            var eHp by remember(eu.id) { mutableStateOf(eu.noHp) }

            PanelCard(isYellow = true) {
                PanelHeader(title = "Edit User: @${eu.username}", icon = Icons.Filled.Edit) {
                    PortalButton("Batal", compact = true) { editingUser = null }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalInputField("Username", eUsername, { eUsername = it }, mono = true)
                    PortalInputField("Nama Lengkap", eNama, { eNama = it })
                    PortalInputField("Password Baru (Opsional)", ePass, { ePass = it }, placeholder = "Kosongkan jika tidak ganti")
                    PortalDropdown("Role", listOf("Kasir", "Owner"), eRole, { it }, { eRole = it })
                    PortalInputField("No. HP", eHp, { eHp = it }, mono = true, keyboardType = KeyboardType.Phone)
                    PortalButton("Simpan Perubahan", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY) {
                        vm.updateUser(eu.id, eUsername, eNama, ePass, eRole, eHp)
                        editingUser = null
                    }
                }
            }
        } ?: run {
            PanelCard(isYellow = true) {
                PanelHeader(title = "Tambah User Baru", icon = Icons.Filled.ManageAccounts) {
                    PortalButton("Laporan Antar User", icon = Icons.Filled.Description, compact = true) {
                        vm.openLaporan()
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalInputField("Username", newUsername, { newUsername = it }, mono = true)
                    PortalInputField("Nama Lengkap", newNama, { newNama = it })
                    PortalInputField("Password", newPass, { newPass = it })
                    PortalDropdown("Role", listOf("Kasir", "Owner"), newRole, { it }, { newRole = it })
                    PortalInputField("No. HP", newHp, { newHp = it }, mono = true, keyboardType = KeyboardType.Phone)
                    PortalButton("Tambah User", icon = Icons.Filled.AddCircleOutline, style = BtnStyle.PRIMARY) {
                        vm.addUser(newUsername, newNama, newPass, newRole, newHp)
                        newUsername = ""; newNama = ""; newPass = ""; newHp = ""
                    }
                }
            }
        }

        PanelCard {
            PanelHeader(
                title = "Daftar User & Aktivitas (Modal Awal: ${FormatUtils.rupiah(pengaturan.modalAwal)})",
                icon = Icons.Filled.ManageAccounts
            ) {
                PortalButton("Atur Modal Awal", icon = Icons.Filled.Tune, compact = true) {
                    vm.navigateTo(AppRoute.PENGATURAN_RUMUS)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                users.forEach { u ->
                    val jmlAkad = transaksiList.count { it.userId == u.id }
                    val jmlJurnal = jurnalList.count { it.userId == u.id }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceSubtle)
                            .border(1.dp, BorderCard, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("@${u.username} · ${u.role}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (u.aktif == 1) {
                                StatusTag("AKTIF", SuccessBg, SuccessText, SuccessBorder)
                            } else {
                                StatusTag("NONAKTIF", DangerBg, DangerText, DangerBorder)
                            }
                        }
                        Text(u.namaLengkap, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = TextPrimary)
                        Text("HP: ${u.noHp.ifEmpty { "-" }}", fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = TextMuted)
                        Text(
                            text = "$jmlAkad Akad · $jmlJurnal Jurnal →",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = EmeraldPrimary,
                            modifier = Modifier.clickable { vm.openLaporan(userId = u.id) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            PortalButton("Jurnal", icon = Icons.Filled.Description, compact = true) {
                                vm.openLaporan(userId = u.id)
                            }
                            PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) {
                                editingUser = u
                            }
                            if (u.id != currentUser.id) {
                                PortalButton(if (u.aktif == 1) "Nonaktifkan" else "Aktifkan", compact = true) {
                                    vm.toggleUser(u.id)
                                }
                                PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) {
                                    deleteTarget = u
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmActionDialog(
            title = "Hapus User @${target.username}?",
            message = "Akun ${target.namaLengkap} (@${target.username}) akan dihapus.",
            confirmText = "Hapus User",
            onConfirm = { vm.deleteUser(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}
