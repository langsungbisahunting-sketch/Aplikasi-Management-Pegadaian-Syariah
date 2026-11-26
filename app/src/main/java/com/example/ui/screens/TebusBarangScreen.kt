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
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.ui.theme.BorderCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TebusBarangScreen(
    vm: AppViewModel,
    currentUser: UserEntity,
    transaksiList: List<TransaksiGadaiEntity>
) {
    val isOwner = currentUser.role == "Owner"
    val preAkad by vm.prefillTebusAkad.collectAsState()
    val aktifList = remember(transaksiList) {
        transaksiList.filter { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
            .sortedBy { it.jatuhTempo }
    }
    val lunasList = remember(transaksiList) {
        transaksiList.filter { it.statusGadai == "LUNAS" }.take(30)
    }

    var selectedAkad by remember(preAkad, aktifList) {
        mutableStateOf(aktifList.firstOrNull { it.noAkad == preAkad })
    }
    var tanggalTebus by remember { mutableStateOf(FormatUtils.todayIso()) }
    var periodeStr by remember { mutableStateOf("1") }

    var editingItem by remember { mutableStateOf<TransaksiGadaiEntity?>(null) }
    var cancelTarget by remember { mutableStateOf<TransaksiGadaiEntity?>(null) }

    val periode = (periodeStr.toIntOrNull() ?: 1).coerceAtLeast(0)
    val totalTebusan = selectedAkad?.let { it.pokokPinjaman + (it.biayaUjrahPeriode * periode) } ?: 0.0

    Column(modifier = Modifier.fillMaxWidth()) {
        editingItem?.let { item ->
            if (isOwner) {
                var eTgl by remember(item.id) { mutableStateOf(item.tanggalTebus ?: FormatUtils.todayIso()) }
                var eUjr by remember(item.id) { mutableStateOf((item.ujrahTebusDibayar ?: 0.0).toLong().toString()) }
                PanelCard(isYellow = true) {
                    PanelHeader(title = "Edit Pelunasan: ${item.noAkad}", icon = Icons.Filled.Edit) {
                        PortalButton("Batal", compact = true) { editingItem = null }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalInputField("Tanggal Tebus", eTgl, { eTgl = it }, mono = true)
                        PortalInputField("Ujrah Tebus Dibayar (Rp)", eUjr, { eUjr = it }, mono = true, keyboardType = KeyboardType.Number)
                        PortalButton("Simpan Perubahan", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY) {
                            vm.updateTebus(item.id, eTgl, eUjr.toDoubleOrNull() ?: 0.0)
                            editingItem = null
                        }
                    }
                }
            }
        }

        PanelCard(isYellow = true) {
            PanelHeader(title = "Form Tebus Barang (Pelunasan Pokok Qardh + Ujrah)", icon = Icons.Filled.CheckCircleOutline) {
                PortalButton("Perpanjang Gadai", icon = Icons.Filled.Refresh, compact = true) {
                    vm.navigateTo(AppRoute.PERPANJANG_GADAI)
                }
                PortalButton("Tutup Toko", icon = Icons.Filled.Lock, compact = true) {
                    vm.openTutupToko()
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PortalDropdown(
                    label = "No. Akad Aktif",
                    items = aktifList,
                    selectedItem = selectedAkad,
                    itemLabel = { "${it.noAkad} | ${it.namaNasabah} | Pokok: ${FormatUtils.rupiah(it.pokokPinjaman)} + Ujrah: ${FormatUtils.rupiah(it.biayaUjrahPeriode)}" },
                    onSelect = { selectedAkad = it },
                    placeholder = "-- Pilih Akad --"
                )
                PortalInputField("Tanggal Tebus", tanggalTebus, { tanggalTebus = it }, mono = true)
                PortalInputField("Periode Ujrah Terutang", periodeStr, { periodeStr = it }, mono = true, keyboardType = KeyboardType.Number)
                PortalInputField("Total Tebusan (Pokok + Ujrah)", FormatUtils.rupiah(totalTebusan), {}, readOnly = true, mono = true)
                PortalButton(
                    text = "Simpan Tebus Barang",
                    icon = Icons.Filled.CheckCircleOutline,
                    style = BtnStyle.PRIMARY,
                    enabled = selectedAkad != null
                ) {
                    selectedAkad?.let { akad ->
                        vm.simpanTebus(akad.noAkad, tanggalTebus, periode)
                        selectedAkad = null
                        periodeStr = "1"
                    }
                }
            }
        }

        PanelCard {
            PanelHeader(title = "Daftar Barang Lunas", icon = Icons.Filled.Description)
            if (lunasList.isEmpty()) {
                Text("Belum ada barang lunas.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lunasList.forEach { r ->
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
                                Text(
                                    text = r.noAkad,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.clickable { vm.openNotaAkad(r.noAkad) }
                                )
                                Text(
                                    text = FormatUtils.rupiah(r.totalTebusan ?: 0.0),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "${r.namaNasabah} · ${r.namaBarang}",
                                fontSize = 12.5.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Tgl Tebus: ${r.tanggalTebus ?: "-"}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                PortalButton("Cetak", icon = Icons.Filled.Print, compact = true) { vm.openNotaAkad(r.noAkad) }
                                if (isOwner) {
                                    PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) { editingItem = r }
                                    PortalButton("Batal Lunas", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) { cancelTarget = r }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    cancelTarget?.let { target ->
        ConfirmActionDialog(
            title = "Batalkan Lunas ${target.noAkad}?",
            message = "Status LUNAS akan dibatalkan dan akad dikembalikan menjadi aktif/diperpanjang.",
            confirmText = "Batal Lunas",
            onConfirm = { vm.cancelTebus(target.id) },
            onDismiss = { cancelTarget = null }
        )
    }
}
