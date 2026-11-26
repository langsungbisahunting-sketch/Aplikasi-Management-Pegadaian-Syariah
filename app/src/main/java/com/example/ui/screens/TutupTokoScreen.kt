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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JurnalAkuntansiEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.RiwayatPerpanjanganEntity
import com.example.data.db.SesiTutupTokoEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.data.db.UserEntity
import com.example.ui.AppRoute
import com.example.ui.AppViewModel
import com.example.ui.components.BtnStyle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelHeader
import com.example.ui.components.PortalButton
import com.example.ui.components.PortalInputField
import com.example.ui.theme.BorderCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.utils.FormatUtils
import com.example.utils.PrintHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TutupTokoScreen(
    vm: AppViewModel,
    currentUser: UserEntity,
    pengaturan: PengaturanSistemEntity,
    transaksiList: List<TransaksiGadaiEntity>,
    riwayatList: List<RiwayatPerpanjanganEntity>,
    jurnalList: List<JurnalAkuntansiEntity>,
    sesiList: List<SesiTutupTokoEntity>
) {
    val context = LocalContext.current
    val isOwner = currentUser.role == "Owner"
    val selectedDate by vm.tutupTokoDate.collectAsState()
    var tanggalInput by remember(selectedDate) { mutableStateOf(selectedDate) }
    val tgl = if (FormatUtils.validIsoDate(tanggalInput)) tanggalInput else FormatUtils.todayIso()

    val qIn = remember(transaksiList, tgl) { transaksiList.count { it.tanggalAkad == tgl } }
    val qPerp = remember(riwayatList, tgl) { riwayatList.count { it.tanggalPerpanjang == tgl } }
    val qTeb = remember(transaksiList, tgl) { transaksiList.count { it.statusGadai == "LUNAS" && it.tanggalTebus == tgl } }

    val saldoAwal = remember(jurnalList, tgl) {
        jurnalList.filter { it.tanggal < tgl }
            .sumOf { if (it.arusKas == "MASUK") it.totalKas else -it.totalKas }
    }
    val masuk = remember(jurnalList, tgl) {
        jurnalList.filter { it.tanggal == tgl && it.arusKas == "MASUK" }.sumOf { it.totalKas }
    }
    val keluar = remember(jurnalList, tgl) {
        jurnalList.filter { it.tanggal == tgl && it.arusKas == "KELUAR" }.sumOf { it.totalKas }
    }
    val saldoSistem = saldoAwal + masuk - keluar

    val curSesi = remember(sesiList, tgl) { sesiList.firstOrNull { it.tanggal == tgl } }

    var uangFisikStr by remember(tgl, saldoSistem) { mutableStateOf(saldoSistem.toLong().toString()) }
    var catatanSelisih by remember(tgl) { mutableStateOf("") }

    var reopenTarget by remember { mutableStateOf<SesiTutupTokoEntity?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        PanelCard {
            PanelHeader(
                title = "Berita Acara Tutup Toko Harian — $tgl (${curSesi?.status ?: "OPEN"})",
                icon = Icons.Filled.Lock
            ) {
                if (isOwner) {
                    PortalButton("Jurnal Hari Ini", icon = Icons.Filled.Description, compact = true) {
                        vm.openLaporan(tglAwal = tgl, tglAkhir = tgl)
                    }
                }
                PortalButton("Cetak Berita Acara", icon = Icons.Filled.Print, style = BtnStyle.PRIMARY, compact = true) {
                    val html = PrintHelper.buildBeritaAcaraTutupTokoHtml(
                        pengaturan, tgl, curSesi, qIn, qPerp, qTeb,
                        saldoAwal, masuk, keluar, saldoSistem, currentUser.namaLengkap
                    )
                    PrintHelper.printHtml(context, "Tutup_Toko_$tgl", html)
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                PortalInputField(
                    label = "Tanggal Sesi (YYYY-MM-DD)",
                    value = tanggalInput,
                    onValueChange = { tanggalInput = it },
                    mono = true,
                    modifier = Modifier.weight(1f)
                )
                PortalButton("Lihat Tanggal", compact = true) {
                    if (FormatUtils.validIsoDate(tanggalInput)) {
                        vm.tutupTokoDate.value = tanggalInput
                    } else {
                        vm.setBanner(false, "Format tanggal harus YYYY-MM-DD.")
                    }
                }
            }

            TutupTokoRow("Modal Awal Toko (Bersama Antar User)", FormatUtils.rupiah(pengaturan.modalAwal))
            TutupTokoRow("Barang Masuk (Gadai Baru)", "$qIn Akad", onClick = { vm.navigateTo(AppRoute.GADAI_BARANG) })
            TutupTokoRow("Perpanjangan Akad", "$qPerp Akad", onClick = { vm.navigateTo(AppRoute.PERPANJANG_GADAI) })
            TutupTokoRow("Barang Keluar (Tebusan)", "$qTeb Akad", onClick = { vm.navigateTo(AppRoute.TEBUS_BARANG) })
            TutupTokoRow("Saldo Awal Kas (Hari Sebelumnya)", FormatUtils.rupiah(saldoAwal))
            TutupTokoRow("Total Uang Masuk", "+${FormatUtils.rupiah(masuk)}")
            TutupTokoRow("Total Uang Keluar (Qardh)", "-${FormatUtils.rupiah(keluar)}")
            TutupTokoRow("Saldo Kas Seharusnya (Sistem)", FormatUtils.rupiah(saldoSistem), bold = true)
            if (curSesi != null) {
                TutupTokoRow("Uang Fisik Aktual", FormatUtils.rupiah(curSesi.uangFisikAktual ?: 0.0), bold = true)
                TutupTokoRow("Selisih Kas", "${FormatUtils.rupiah(curSesi.selisihKas ?: 0.0)} (${curSesi.catatanSelisih})", bold = true)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderCard)

            if (curSesi == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalInputField("Uang Fisik Aktual Laci Kasir (Rp)", uangFisikStr, { uangFisikStr = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Catatan Keterangan Selisih", catatanSelisih, { catatanSelisih = it }, placeholder = "Cocok / alasan selisih")
                    PortalButton("Selesaikan & Kunci Tutup Toko", icon = Icons.Filled.Lock, style = BtnStyle.PRIMARY) {
                        vm.kunciTutupToko(
                            tgl, qIn, qPerp, qTeb, saldoAwal, masuk, keluar, saldoSistem,
                            uangFisikStr.toDoubleOrNull() ?: 0.0,
                            catatanSelisih
                        )
                    }
                }
            } else if (isOwner) {
                var revFisik by remember(curSesi.id) { mutableStateOf((curSesi.uangFisikAktual ?: 0.0).toLong().toString()) }
                var revCat by remember(curSesi.id) { mutableStateOf(curSesi.catatanSelisih) }

                PanelCard(isYellow = true) {
                    PanelHeader(title = "Kontrol Owner: Edit Fisik / Buka Kunci Tanggal $tgl", icon = Icons.Filled.Edit)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalInputField("Revisi Uang Fisik Aktual (Rp)", revFisik, { revFisik = it }, mono = true, keyboardType = KeyboardType.Number)
                        PortalInputField("Revisi Catatan Selisih", revCat, { revCat = it })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PortalButton("Simpan Koreksi", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY, compact = true) {
                                vm.updateSesiTutupToko(curSesi.id, revFisik.toDoubleOrNull() ?: 0.0, revCat)
                            }
                            PortalButton("Buka Kunci Sesi (OPEN)", icon = Icons.Filled.LockOpen, style = BtnStyle.YELLOW, compact = true) {
                                reopenTarget = curSesi
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Kasir Bertugas,", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(curSesi?.namaKasir ?: currentUser.namaLengkap, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Mengetahui Owner,", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Owner ${pengaturan.namaAplikasi}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        PanelCard {
            PanelHeader(title = "Log Tutup Toko Harian", icon = Icons.Filled.Description)
            if (sesiList.isEmpty()) {
                Text("Belum ada riwayat tutup toko.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sesiList.take(30).forEach { h ->
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
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${h.tanggal} (${h.status})",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.clickable {
                                        tanggalInput = h.tanggal
                                        vm.tutupTokoDate.value = h.tanggal
                                    }
                                )
                                Text(h.namaKasir, fontSize = 11.5.sp, color = TextMuted)
                            }
                            Text(
                                text = "Sistem: ${FormatUtils.rupiah(h.saldoKasSistem)} · Fisik: ${FormatUtils.rupiah(h.uangFisikAktual ?: 0.0)} · Selisih: ${FormatUtils.rupiah(h.selisihKas ?: 0.0)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = TextPrimary
                            )
                            if (isOwner) {
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    PortalButton("Kelola", icon = Icons.Filled.Edit, compact = true) {
                                        tanggalInput = h.tanggal
                                        vm.tutupTokoDate.value = h.tanggal
                                    }
                                    PortalButton("Buka Kunci", icon = Icons.Filled.LockOpen, style = BtnStyle.YELLOW, compact = true) {
                                        reopenTarget = h
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    reopenTarget?.let { target ->
        ConfirmActionDialog(
            title = "Buka Kunci Tanggal ${target.tanggal}?",
            message = "Sesi tutup toko tanggal ${target.tanggal} akan dibuka kembali (status OPEN).",
            confirmText = "Buka Kunci",
            onConfirm = { vm.bukaKunciSesi(target.id) },
            onDismiss = { reopenTarget = null }
        )
    }
}

@Composable
private fun TutupTokoRow(label: String, value: String, bold: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = TextPrimary,
            modifier = Modifier.weight(0.55f)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = if (onClick != null) EmeraldPrimary else TextPrimary,
            modifier = Modifier
                .weight(0.45f)
                .let { m -> if (onClick != null) m.clickable { onClick() } else m }
        )
    }
}
