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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.DokumenPelangganEntity
import com.example.data.db.JurnalAkuntansiEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.ui.AppRoute
import com.example.ui.AppViewModel
import com.example.ui.components.BtnStyle
import com.example.ui.components.GadaiStatusBadge
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelHeader
import com.example.ui.components.PortalButton
import com.example.ui.components.StatCard
import com.example.ui.theme.BorderCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SuccessText
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    vm: AppViewModel,
    pengaturan: PengaturanSistemEntity,
    transaksi: List<TransaksiGadaiEntity>,
    jurnal: List<JurnalAkuntansiEntity>,
    dokumenMap: Map<String, DokumenPelangganEntity>
) {
    val today = FormatUtils.todayIso()
    val aktifList = transaksi.filter { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
    val pokokBeredar = aktifList.sumOf { it.pokokPinjaman }
    val lewatTempoCount = aktifList.count { it.jatuhTempo < today }

    val kasMasuk = jurnal.filter { it.arusKas == "MASUK" }.sumOf { it.totalKas }
    val kasKeluar = jurnal.filter { it.arusKas == "KELUAR" }.sumOf { it.totalKas }
    val saldoKasRiil = kasMasuk - kasKeluar
    val pendapatanJasa = jurnal.sumOf { it.pendapatanAdmin + it.pendapatanUjrah }

    val recent = transaksi.take(10)

    Column(modifier = Modifier.fillMaxWidth()) {
        StatCard(
            title = "Akad Aktif & Diperpanjang",
            value = "${aktifList.size} Akad",
            subtitle = "Pokok Beredar: ${FormatUtils.rupiah(pokokBeredar)}",
            linkLabel = "Lihat Barang Aktif →",
            onLinkClick = { vm.openDataBarang(status = "AKTIF") }
        )

        StatCard(
            title = "Lewat Jatuh Tempo",
            value = "$lewatTempoCount Akad",
            linkLabel = "Buka Monitoring Tempo →",
            onLinkClick = { vm.navigateTo(AppRoute.MONITORING_TEMPO) },
            isYellow = true
        )

        StatCard(
            title = "Saldo Kas Riil (Modal: ${FormatUtils.rupiah(pengaturan.modalAwal)})",
            value = FormatUtils.rupiah(saldoKasRiil),
            subtitle = "Pendapatan Jasa: ${FormatUtils.rupiah(pendapatanJasa)}",
            linkLabel = "Laporan Jasa & Atur Modal →",
            onLinkClick = { vm.navigateTo(AppRoute.LAPORAN_AKUNTANSI) }
        )

        PanelCard {
            PanelHeader(
                title = "Transaksi Gadai Terbaru",
                icon = Icons.Filled.Description
            ) {
                PortalButton(
                    text = "Gadai Baru",
                    icon = Icons.Filled.AddCircleOutline,
                    style = BtnStyle.PRIMARY,
                    compact = true,
                    onClick = { vm.navigateTo(AppRoute.GADAI_BARANG) }
                )
                PortalButton(
                    text = "Semua Jurnal",
                    icon = Icons.Filled.Description,
                    compact = true,
                    onClick = { vm.openLaporan() }
                )
                PortalButton(
                    text = "Atur Modal",
                    compact = true,
                    onClick = { vm.navigateTo(AppRoute.PENGATURAN_RUMUS) }
                )
            }

            if (recent.isEmpty()) {
                Text(
                    text = "Belum ada transaksi gadai. Klik 'Gadai Baru' untuk membuat akad pertama.",
                    fontSize = 12.5.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { r ->
                        val dok = dokumenMap[r.nikNasabah]
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
                                    fontSize = 13.sp,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.clickable { vm.openNotaAkad(r.noAkad) }
                                )
                                GadaiStatusBadge(r.statusGadai)
                            }
                            Text(
                                text = "${r.tanggalAkad} · Kasir: ${r.namaKasir}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = r.namaNasabah,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = EmeraldPrimary,
                                        modifier = Modifier.clickable { vm.openDataPelanggan(query = r.namaNasabah) }
                                    )
                                    Text(
                                        text = "${r.kategoriBarang} - ${r.namaBarang}",
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        modifier = Modifier.clickable { vm.openDataBarang(query = r.noAkad) }
                                    )
                                    Text(
                                        text = if (dok != null) "PDF Ada (${dok.namaFileAsli})" else "Belum ada PDF · Upload",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (dok != null) SuccessText else EmeraldPrimary,
                                        modifier = Modifier.clickable {
                                            vm.openDokumenPelanggan(r.nikNasabah, preview = dok != null)
                                        }
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FormatUtils.rupiah(r.pokokPinjaman),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Ujrah: ${FormatUtils.rupiah(r.biayaUjrahPeriode)}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                PortalButton(
                                    text = "Nota",
                                    icon = Icons.Filled.Print,
                                    compact = true,
                                    onClick = { vm.openNotaAkad(r.noAkad) }
                                )
                                if (r.statusGadai == "AKTIF" || r.statusGadai == "DIPERPANJANG") {
                                    PortalButton(
                                        text = "Perpanjang",
                                        icon = Icons.Filled.Refresh,
                                        compact = true,
                                        onClick = { vm.openPerpanjang(r.noAkad) }
                                    )
                                    PortalButton(
                                        text = "Tebus",
                                        icon = Icons.Filled.CheckCircleOutline,
                                        compact = true,
                                        onClick = { vm.openTebus(r.noAkad) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
