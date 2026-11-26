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
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
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
import com.example.data.db.RiwayatPerpanjanganEntity
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
fun PerpanjangGadaiScreen(
    vm: AppViewModel,
    currentUser: UserEntity,
    transaksiList: List<TransaksiGadaiEntity>,
    riwayatList: List<RiwayatPerpanjanganEntity>
) {
    val isOwner = currentUser.role == "Owner"
    val preAkad by vm.prefillPerpanjangAkad.collectAsState()
    val aktifList = remember(transaksiList) {
        transaksiList.filter { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
            .sortedBy { it.jatuhTempo }
    }

    var selectedAkad by remember(preAkad, aktifList) {
        mutableStateOf(aktifList.firstOrNull { it.noAkad == preAkad })
    }
    var tanggalPerpanjang by remember { mutableStateOf(FormatUtils.todayIso()) }
    var periodeStr by remember { mutableStateOf("1") }

    var editingItem by remember { mutableStateOf<RiwayatPerpanjanganEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<RiwayatPerpanjanganEntity?>(null) }

    val periode = (periodeStr.toIntOrNull() ?: 1).coerceAtLeast(1)
    val estimasiJtBaru = selectedAkad?.let {
        FormatUtils.addDaysIso(it.jatuhTempo, (it.hariPerPeriode.coerceAtLeast(1)) * periode)
    } ?: "-"
    val totalUjrah = (selectedAkad?.biayaUjrahPeriode ?: 0.0) * periode

    Column(modifier = Modifier.fillMaxWidth()) {
        editingItem?.let { item ->
            if (isOwner) {
                var eTgl by remember(item.id) { mutableStateOf(item.tanggalPerpanjang) }
                var eJtL by remember(item.id) { mutableStateOf(item.jatuhTempoLama) }
                var eJtB by remember(item.id) { mutableStateOf(item.jatuhTempoBaru) }
                var ePer by remember(item.id) { mutableStateOf(item.jumlahPeriode.toString()) }
                var eUjr by remember(item.id) { mutableStateOf(item.biayaUjrahDibayar.toLong().toString()) }

                PanelCard(isYellow = true) {
                    PanelHeader(title = "Edit Perpanjangan: ${item.noAkad}", icon = Icons.Filled.Edit) {
                        PortalButton("Batal", compact = true) { editingItem = null }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalInputField("Tanggal Perpanjang", eTgl, { eTgl = it }, mono = true)
                        PortalInputField("Jatuh Tempo Lama", eJtL, { eJtL = it }, mono = true)
                        PortalInputField("Jatuh Tempo Baru", eJtB, { eJtB = it }, mono = true)
                        PortalInputField("Jumlah Periode", ePer, { ePer = it }, mono = true, keyboardType = KeyboardType.Number)
                        PortalInputField("Ujrah Dibayar (Rp)", eUjr, { eUjr = it }, mono = true, keyboardType = KeyboardType.Number)
                        PortalButton("Simpan Perubahan", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY) {
                            vm.updatePerpanjang(
                                item.id, eTgl, eJtL, eJtB,
                                ePer.toIntOrNull() ?: 1,
                                eUjr.toDoubleOrNull() ?: 0.0
                            )
                            editingItem = null
                        }
                    }
                }
            }
        }

        PanelCard(isYellow = true) {
            PanelHeader(title = "Perpanjangan Akad Rahn (Ujrah Penitipan)", icon = Icons.Filled.Refresh) {
                PortalButton("Gadai Baru", icon = Icons.Filled.AddCircleOutline, compact = true) {
                    vm.navigateTo(AppRoute.GADAI_BARANG)
                }
                PortalButton("Tebus Barang", icon = Icons.Filled.CheckCircleOutline, compact = true) {
                    vm.navigateTo(AppRoute.TEBUS_BARANG)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PortalDropdown(
                    label = "No. Akad Aktif",
                    items = aktifList,
                    selectedItem = selectedAkad,
                    itemLabel = { "${it.noAkad} | ${it.namaNasabah} | Tempo: ${it.jatuhTempo} | Ujrah: ${FormatUtils.rupiah(it.biayaUjrahPeriode)}" },
                    onSelect = { selectedAkad = it },
                    placeholder = "-- Pilih Akad --"
                )
                PortalInputField("Tanggal Perpanjang", tanggalPerpanjang, { tanggalPerpanjang = it }, mono = true)
                PortalInputField("Jumlah Periode", periodeStr, { periodeStr = it }, mono = true, keyboardType = KeyboardType.Number)
                PortalInputField("Estimasi Tempo Baru", estimasiJtBaru, {}, readOnly = true, mono = true)
                PortalInputField("Total Ujrah Dibayar", FormatUtils.rupiah(totalUjrah), {}, readOnly = true, mono = true)
                PortalButton(
                    text = "Simpan Perpanjangan",
                    icon = Icons.Filled.Refresh,
                    style = BtnStyle.PRIMARY,
                    enabled = selectedAkad != null
                ) {
                    selectedAkad?.let { akad ->
                        vm.simpanPerpanjang(akad.noAkad, tanggalPerpanjang, periode)
                        selectedAkad = null
                        periodeStr = "1"
                    }
                }
            }
        }

        PanelCard {
            PanelHeader(title = "Riwayat Perpanjangan", icon = Icons.Filled.Description)
            val list = riwayatList.take(30)
            if (list.isEmpty()) {
                Text("Belum ada riwayat perpanjangan.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { r ->
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
                                    text = FormatUtils.rupiah(r.biayaUjrahDibayar),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "Tgl: ${r.tanggalPerpanjang} · Tempo: ${r.jatuhTempoLama} → ${r.jatuhTempoBaru}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "Kasir: ${r.namaKasir}",
                                fontSize = 11.5.sp,
                                color = if (isOwner) EmeraldPrimary else TextMuted,
                                modifier = if (isOwner && r.userId != null) Modifier.clickable { vm.openLaporan(userId = r.userId) } else Modifier
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                PortalButton("Nota", icon = Icons.Filled.Print, compact = true) { vm.openNotaAkad(r.noAkad) }
                                if (isOwner) {
                                    PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) { editingItem = r }
                                    PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) { deleteTarget = r }
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
            title = "Hapus Perpanjangan ${target.noAkad}?",
            message = "Riwayat perpanjangan ini akan dihapus dan jatuh tempo akad akan dihitung ulang.",
            confirmText = "Hapus",
            onConfirm = { vm.deletePerpanjang(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}
