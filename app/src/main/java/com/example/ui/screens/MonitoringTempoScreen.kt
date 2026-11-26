package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.ui.AppViewModel
import com.example.ui.components.BtnStyle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelHeader
import com.example.ui.components.PortalButton
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
import com.example.ui.theme.WarningBg
import com.example.ui.theme.WarningBorder
import com.example.ui.theme.WarningText
import com.example.utils.FormatUtils
import com.example.utils.PrintHelper
import com.example.utils.XlsxSheet
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitoringTempoScreen(
    vm: AppViewModel,
    pengaturan: PengaturanSistemEntity,
    transaksiList: List<TransaksiGadaiEntity>
) {
    val context = LocalContext.current
    val list = remember(transaksiList) {
        transaksiList.filter { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
            .sortedBy { it.jatuhTempo }
    }
    var deleteTarget by remember { mutableStateOf<TransaksiGadaiEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val rows = mutableListOf<List<Any?>>(
                listOf("No. Akad", "Jatuh Tempo", "Rahin", "No. HP", "Kategori", "Barang", "Pokok Qardh (Rp)", "Ujrah (Rp)")
            )
            list.forEach { r ->
                rows.add(listOf(r.noAkad, r.jatuhTempo, r.namaNasabah, r.noHp, r.kategoriBarang, r.namaBarang, r.pokokPinjaman, r.biayaUjrahPeriode))
            }
            vm.exportXlsxToUri(uri, listOf(XlsxSheet("Monitoring", rows)), "Monitoring Tempo")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PanelCard(isYellow = true) {
            PanelHeader(title = "Monitoring Jatuh Tempo", icon = Icons.Filled.Schedule) {
                PortalButton("Export Excel (.xlsx)", icon = Icons.Filled.Download, compact = true) {
                    exportLauncher.launch("monitoring_tempo_${FormatUtils.todayIso()}.xlsx")
                }
                PortalButton("Cetak", icon = Icons.Filled.Print, style = BtnStyle.PRIMARY, compact = true) {
                    val headers = listOf("No. Akad", "Jatuh Tempo", "Rahin", "HP", "Barang", "Pokok", "Ujrah")
                    val rows = list.map {
                        listOf(it.noAkad, it.jatuhTempo, it.namaNasabah, it.noHp, "${it.kategoriBarang} - ${it.namaBarang}", FormatUtils.rupiah(it.pokokPinjaman), FormatUtils.rupiah(it.biayaUjrahPeriode))
                    }
                    val html = PrintHelper.buildTableReportHtml(pengaturan, "Monitoring Jatuh Tempo", headers, rows)
                    PrintHelper.printHtml(context, "Monitoring_Tempo", html)
                }
            }
        }

        PanelCard {
            if (list.isEmpty()) {
                Text("Tidak ada akad aktif dalam monitoring jatuh tempo.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { r ->
                        val diff = FormatUtils.diffDaysFromToday(r.jatuhTempo)
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
                                when {
                                    diff < 0 -> StatusTag("Lewat ${abs(diff)} Hari", DangerBg, DangerText, DangerBorder)
                                    diff == 0 -> StatusTag("Jatuh Tempo Hari Ini", WarningBg, WarningText, WarningBorder)
                                    diff <= 3 -> StatusTag("H-$diff", WarningBg, WarningText, WarningBorder)
                                    else -> StatusTag("$diff Hari Lagi", SuccessBg, SuccessText, SuccessBorder)
                                }
                            }
                            Text(
                                text = "Jatuh Tempo: ${r.jatuhTempo}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "${r.namaNasabah} (${r.noHp}) · ${r.kategoriBarang} - ${r.namaBarang}",
                                fontSize = 12.5.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "${FormatUtils.rupiah(r.pokokPinjaman)} + ${FormatUtils.rupiah(r.biayaUjrahPeriode)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                PortalButton("Nota", icon = Icons.Filled.Print, compact = true) { vm.openNotaAkad(r.noAkad) }
                                PortalButton("Perpanjang", icon = Icons.Filled.Refresh, compact = true) { vm.openPerpanjang(r.noAkad) }
                                PortalButton("Tebus", icon = Icons.Filled.CheckCircleOutline, compact = true) { vm.openTebus(r.noAkad) }
                                PortalButton("Lelang", icon = Icons.Filled.Gavel, compact = true) { vm.openLelang(r.noAkad) }
                                PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) { vm.openEditGadai(r.id) }
                                PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) { deleteTarget = r }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmActionDialog(
            title = "Hapus Akad ${target.noAkad}?",
            message = "Akad ini akan dihapus permanen beserta riwayat dan jurnalnya.",
            confirmText = "Hapus",
            onConfirm = { vm.deleteGadai(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}
