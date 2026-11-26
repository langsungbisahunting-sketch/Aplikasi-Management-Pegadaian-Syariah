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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
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
fun LelangBarangScreen(
    vm: AppViewModel,
    transaksiList: List<TransaksiGadaiEntity>
) {
    val preAkad by vm.prefillLelangAkad.collectAsState()
    val aktifList = remember(transaksiList) {
        transaksiList.filter { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
            .sortedBy { it.jatuhTempo }
    }
    val lelangList = remember(transaksiList) {
        transaksiList.filter { it.statusGadai == "LELANG" }
    }

    var selectedAkad by remember(preAkad, aktifList) {
        mutableStateOf(aktifList.firstOrNull { it.noAkad == preAkad })
    }
    var tanggalLelang by remember { mutableStateOf(FormatUtils.todayIso()) }
    var hargaLelangStr by remember { mutableStateOf("") }

    var editingItem by remember { mutableStateOf<TransaksiGadaiEntity?>(null) }
    var cancelTarget by remember { mutableStateOf<TransaksiGadaiEntity?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        editingItem?.let { item ->
            var eTgl by remember(item.id) { mutableStateOf(item.tanggalLelang ?: FormatUtils.todayIso()) }
            var eHarga by remember(item.id) { mutableStateOf((item.hargaLelang ?: 0.0).toLong().toString()) }
            PanelCard(isYellow = true) {
                PanelHeader(title = "Edit Data Lelang: ${item.noAkad}", icon = Icons.Filled.Edit) {
                    PortalButton("Batal", compact = true) { editingItem = null }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalInputField("Tanggal Lelang", eTgl, { eTgl = it }, mono = true)
                    PortalInputField("Harga Terjual Lelang (Rp)", eHarga, { eHarga = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalButton("Simpan Perubahan", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY) {
                        vm.updateLelang(item.id, eTgl, eHarga.toDoubleOrNull() ?: 0.0)
                        editingItem = null
                    }
                }
            }
        }

        PanelCard(isYellow = true) {
            PanelHeader(title = "Eksekusi Lelang Jaminan", icon = Icons.Filled.Gavel) {
                PortalButton("Monitoring Tempo", icon = Icons.Filled.Schedule, compact = true) {
                    vm.navigateTo(AppRoute.MONITORING_TEMPO)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PortalDropdown(
                    label = "No. Akad",
                    items = aktifList,
                    selectedItem = selectedAkad,
                    itemLabel = { "${it.noAkad} - ${it.namaNasabah} (Kewajiban: ${FormatUtils.rupiah(it.pokokPinjaman + it.biayaUjrahPeriode)})" },
                    onSelect = { selectedAkad = it },
                    placeholder = "-- Pilih Akad --"
                )
                PortalInputField("Tanggal Lelang", tanggalLelang, { tanggalLelang = it }, mono = true)
                PortalInputField("Harga Terjual Lelang (Rp)", hargaLelangStr, { hargaLelangStr = it }, mono = true, keyboardType = KeyboardType.Number)
                PortalButton(
                    text = "Simpan Lelang",
                    icon = Icons.Filled.Gavel,
                    style = BtnStyle.PRIMARY,
                    enabled = selectedAkad != null
                ) {
                    selectedAkad?.let { akad ->
                        vm.simpanLelang(akad.noAkad, tanggalLelang, hargaLelangStr.toDoubleOrNull() ?: 0.0)
                        selectedAkad = null
                        hargaLelangStr = ""
                    }
                }
            }
        }

        PanelCard {
            PanelHeader(title = "Daftar Barang Dilelang", icon = Icons.Filled.Gavel)
            if (lelangList.isEmpty()) {
                Text("Belum ada barang yang dilelang.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lelangList.forEach { r ->
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
                                    text = r.tanggalLelang ?: "-",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = TextMuted
                                )
                            }
                            Text(text = "${r.namaNasabah} · ${r.namaBarang}", fontSize = 12.5.sp, color = TextPrimary)
                            Text(
                                text = "Harga Lelang: ${FormatUtils.rupiah(r.hargaLelang ?: 0.0)} · Kelebihan ke Rahin: ${FormatUtils.rupiah(r.sisaDikembalikanLelang ?: 0.0)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                PortalButton("Nota", icon = Icons.Filled.Print, compact = true) { vm.openNotaAkad(r.noAkad) }
                                PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) { editingItem = r }
                                PortalButton("Batalkan", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) { cancelTarget = r }
                            }
                        }
                    }
                }
            }
        }
    }

    cancelTarget?.let { target ->
        ConfirmActionDialog(
            title = "Batalkan Lelang ${target.noAkad}?",
            message = "Status lelang dan jurnal lelang akan dibatalkan.",
            confirmText = "Batalkan Lelang",
            onConfirm = { vm.cancelLelang(target.id) },
            onDismiss = { cancelTarget = null }
        )
    }
}
