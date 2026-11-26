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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Print
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
import com.example.data.db.SesiTutupTokoEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.data.db.UserEntity
import com.example.ui.AppRoute
import com.example.ui.AppViewModel
import com.example.ui.LaporanFilterState
import com.example.ui.components.BtnStyle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelHeader
import com.example.ui.components.PortalButton
import com.example.ui.components.PortalDropdown
import com.example.ui.components.PortalInputField
import com.example.ui.components.StatCard
import com.example.ui.theme.BorderCard
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.utils.FormatUtils
import com.example.utils.PrintHelper
import com.example.utils.XlsxSheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaporanAkuntansiScreen(
    vm: AppViewModel,
    currentUser: UserEntity,
    pengaturan: PengaturanSistemEntity,
    users: List<UserEntity>,
    transaksiList: List<TransaksiGadaiEntity>,
    jurnalList: List<JurnalAkuntansiEntity>,
    sesiList: List<SesiTutupTokoEntity>
) {
    val context = LocalContext.current
    val filterState by vm.laporanFilter.collectAsState()

    var tglAwal by remember(filterState) { mutableStateOf(filterState.tglAwal) }
    var tglAkhir by remember(filterState) { mutableStateOf(filterState.tglAkhir) }
    var filterUid by remember(filterState) { mutableStateOf(filterState.userId) }
    var filterMenu by remember(filterState) { mutableStateOf(filterState.jenisMenu) }
    var filterAkad by remember(filterState) { mutableStateOf(filterState.noAkad) }

    var editingJurnal by remember { mutableStateOf<JurnalAkuntansiEntity?>(null) }
    var deleteJurnalTarget by remember { mutableStateOf<JurnalAkuntansiEntity?>(null) }
    var reopenSesiTarget by remember { mutableStateOf<SesiTutupTokoEntity?>(null) }

    val filteredJurnal = remember(jurnalList, tglAwal, tglAkhir, filterUid, filterMenu, filterAkad) {
        jurnalList.filter { j ->
            val aOk = tglAwal.isBlank() || j.tanggal >= tglAwal
            val bOk = tglAkhir.isBlank() || j.tanggal <= tglAkhir
            val uOk = filterUid == 0 || j.userId == filterUid
            val mOk = filterMenu.isBlank() || j.jenisMenu == filterMenu
            val kOk = filterAkad.isBlank() || j.noAkad.contains(filterAkad, ignoreCase = true)
            aOk && bOk && uOk && mOk && kOk
        }
    }

    val filteredTransaksi = remember(transaksiList, tglAwal, tglAkhir, filterUid, filterAkad) {
        transaksiList.filter { t ->
            val aOk = tglAwal.isBlank() || t.tanggalAkad >= tglAwal
            val bOk = tglAkhir.isBlank() || t.tanggalAkad <= tglAkhir
            val uOk = filterUid == 0 || t.userId == filterUid
            val kOk = filterAkad.isBlank() || t.noAkad.contains(filterAkad, ignoreCase = true)
            aOk && bOk && uOk && kOk
        }
    }

    val filteredSesi = remember(sesiList, tglAwal, tglAkhir, filterUid) {
        sesiList.filter { s ->
            val aOk = tglAwal.isBlank() || s.tanggal >= tglAwal
            val bOk = tglAkhir.isBlank() || s.tanggal <= tglAkhir
            val uOk = filterUid == 0 || s.kasirId == filterUid
            aOk && bOk && uOk
        }
    }

    val modalKeluar = filteredJurnal.filter { it.jenisMenu == "GADAI_BARANG" && it.arusKas == "KELUAR" }.sumOf { it.pokokNominal }
    val pokokKembali = filteredJurnal.filter { (it.jenisMenu == "TEBUS_BARANG" || it.jenisMenu == "LELANG_BARANG") && it.arusKas == "MASUK" }.sumOf { it.pokokNominal }
    val pendUjrah = filteredJurnal.sumOf { it.pendapatanUjrah }
    val pendAdmin = filteredJurnal.sumOf { it.pendapatanAdmin }
    val netKas = filteredJurnal.sumOf { if (it.arusKas == "MASUK") it.totalKas else -it.totalKas }

    val today = FormatUtils.todayIso()
    val cntAktif = filteredTransaksi.count { it.statusGadai == "AKTIF" }
    val cntPerp = filteredTransaksi.count { it.statusGadai == "DIPERPANJANG" }
    val cntLunas = filteredTransaksi.count { it.statusGadai == "LUNAS" }
    val cntLewat = filteredTransaksi.count { (it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG") && it.jatuhTempo < today }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val rows = mutableListOf<List<Any?>>(
                listOf("Tanggal", "No. Akad", "Menu", "Arus", "Pokok (Rp)", "Admin (Rp)", "Ujrah (Rp)", "Total Kas (Rp)", "Kasir", "Keterangan")
            )
            filteredJurnal.forEach { j ->
                rows.add(listOf(j.tanggal, j.noAkad, j.jenisMenu, j.arusKas, j.pokokNominal, j.pendapatanAdmin, j.pendapatanUjrah, j.totalKas, j.namaKasir, j.keterangan))
            }
            vm.exportXlsxToUri(uri, listOf(XlsxSheet("Jurnal_Owner", rows)), "Laporan Owner")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Filters Panel
        PanelCard(isYellow = true) {
            PanelHeader(title = "Filter Tanggal, Kasir & Ref Akad Laporan Owner", icon = Icons.Filled.Description) {
                PortalButton("Export Excel (.xlsx)", icon = Icons.Filled.Download, compact = true) {
                    exportLauncher.launch("laporan_owner_${FormatUtils.todayIso()}.xlsx")
                }
                PortalButton("Cetak A4", icon = Icons.Filled.Print, style = BtnStyle.PRIMARY, compact = true) {
                    val html = PrintHelper.buildLaporanOwnerHtml(
                        pengaturan, netKas, modalKeluar, pokokKembali, pendUjrah + pendAdmin, filteredJurnal
                    )
                    PrintHelper.printHtml(context, "Laporan_Owner", html)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                PortalButton("Hari Ini", compact = true) {
                    tglAwal = today; tglAkhir = today
                }
                PortalButton("7 Hari Terakhir", compact = true) {
                    tglAwal = FormatUtils.daysAgoIso(6); tglAkhir = today
                }
                PortalButton("Bulan Ini", compact = true) {
                    tglAwal = FormatUtils.firstDayOfMonthIso(); tglAkhir = today
                }
                PortalButton("Modal Awal & Tambahan", compact = true) {
                    filterMenu = "MODAL_AWAL"
                }
                PortalButton("Semua", compact = true) {
                    tglAwal = ""; tglAkhir = ""; filterUid = 0; filterMenu = ""; filterAkad = ""
                    vm.laporanFilter.value = LaporanFilterState()
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalInputField("Dari Tanggal (YYYY-MM-DD)", tglAwal, { tglAwal = it }, mono = true)
                PortalInputField("Sampai Tanggal (YYYY-MM-DD)", tglAkhir, { tglAkhir = it }, mono = true)
                val userOptions = listOf(null) + users
                PortalDropdown(
                    label = "Petugas / User",
                    items = userOptions,
                    selectedItem = users.firstOrNull { it.id == filterUid },
                    itemLabel = { u -> if (u == null) "-- Semua User (Gabungan) --" else "${u.namaLengkap} (${u.role})" },
                    onSelect = { filterUid = it?.id ?: 0 }
                )
                PortalDropdown(
                    label = "Jenis Menu",
                    items = listOf("", "MODAL_AWAL", "GADAI_BARANG", "PERPANJANG_GADAI", "TEBUS_BARANG", "LELANG_BARANG"),
                    selectedItem = filterMenu,
                    itemLabel = { it.ifEmpty { "-- Semua --" } },
                    onSelect = { filterMenu = it }
                )
                PortalInputField("No. Akad / Ref", filterAkad, { filterAkad = it }, mono = true, placeholder = "AG-... / MODAL-AWAL")
            }
        }

        // Edit or Add Manual Jurnal
        if (editingJurnal != null) {
            val ej = editingJurnal!!
            var eTgl by remember(ej.id) { mutableStateOf(ej.tanggal) }
            var eRef by remember(ej.id) { mutableStateOf(ej.noAkad) }
            var eMenu by remember(ej.id) { mutableStateOf(ej.jenisMenu) }
            var eArus by remember(ej.id) { mutableStateOf(ej.arusKas) }
            var eDeb by remember(ej.id) { mutableStateOf(ej.akunDebit) }
            var eKre by remember(ej.id) { mutableStateOf(ej.akunKredit) }
            var ePok by remember(ej.id) { mutableStateOf(ej.pokokNominal.toLong().toString()) }
            var eAdm by remember(ej.id) { mutableStateOf(ej.pendapatanAdmin.toLong().toString()) }
            var eUjr by remember(ej.id) { mutableStateOf(ej.pendapatanUjrah.toLong().toString()) }
            var eTot by remember(ej.id) { mutableStateOf(ej.totalKas.toLong().toString()) }
            var eKet by remember(ej.id) { mutableStateOf(ej.keterangan) }

            PanelCard(isYellow = true) {
                PanelHeader(title = "Edit Baris Jurnal #${ej.id}", icon = Icons.Filled.Edit) {
                    PortalButton("Batal", compact = true) { editingJurnal = null }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalInputField("Tanggal", eTgl, { eTgl = it }, mono = true)
                    PortalInputField("No. Ref / Akad", eRef, { eRef = it }, mono = true)
                    PortalDropdown("Jenis Menu", listOf("MODAL_AWAL", "GADAI_BARANG", "PERPANJANG_GADAI", "TEBUS_BARANG", "LELANG_BARANG"), eMenu, { it }, { eMenu = it })
                    PortalDropdown("Arus Kas", listOf("MASUK", "KELUAR"), eArus, { it }, { eArus = it })
                    PortalInputField("Akun Debit", eDeb, { eDeb = it })
                    PortalInputField("Akun Kredit", eKre, { eKre = it })
                    PortalInputField("Pokok (Rp)", ePok, { ePok = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Admin (Rp)", eAdm, { eAdm = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Ujrah (Rp)", eUjr, { eUjr = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Total Kas (Rp)", eTot, { eTot = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Keterangan", eKet, { eKet = it })
                    PortalButton("Simpan Jurnal", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY) {
                        vm.updateJurnal(
                            ej.copy(
                                tanggal = eTgl, noAkad = eRef, jenisMenu = eMenu, arusKas = eArus,
                                akunDebit = eDeb, akunKredit = eKre,
                                pokokNominal = ePok.toDoubleOrNull() ?: 0.0,
                                pendapatanAdmin = eAdm.toDoubleOrNull() ?: 0.0,
                                pendapatanUjrah = eUjr.toDoubleOrNull() ?: 0.0,
                                totalKas = eTot.toDoubleOrNull() ?: 0.0,
                                keterangan = eKet
                            )
                        )
                        editingJurnal = null
                    }
                }
            }
        } else {
            var mTgl by remember { mutableStateOf(FormatUtils.todayIso()) }
            var mRef by remember { mutableStateOf("JURNAL-MANUAL") }
            var mMenu by remember { mutableStateOf("MODAL_AWAL") }
            var mArus by remember { mutableStateOf("MASUK") }
            var mUid by remember(currentUser.id) { mutableStateOf(currentUser.id) }
            var mDeb by remember { mutableStateOf("Kas Operasional") }
            var mKre by remember { mutableStateOf("Modal Tambahan") }
            var mPok by remember { mutableStateOf("0") }
            var mAdm by remember { mutableStateOf("0") }
            var mUjr by remember { mutableStateOf("0") }
            var mTot by remember { mutableStateOf("") }
            var mKet by remember { mutableStateOf("") }

            PanelCard {
                PanelHeader(title = "Tambah Jurnal Manual (Penyesuaian Kas / Modal Antar User)", icon = Icons.Filled.AddCircleOutline)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalInputField("Tanggal", mTgl, { mTgl = it }, mono = true)
                    PortalInputField("No. Ref / Akad", mRef, { mRef = it }, mono = true)
                    PortalDropdown("Jenis", listOf("MODAL_AWAL", "GADAI_BARANG", "PERPANJANG_GADAI", "TEBUS_BARANG", "LELANG_BARANG"), mMenu, { it }, { mMenu = it })
                    PortalDropdown("Arus Kas", listOf("MASUK", "KELUAR"), mArus, { it }, { mArus = it })
                    PortalDropdown(
                        label = "Petugas Terkait",
                        items = users,
                        selectedItem = users.firstOrNull { it.id == mUid } ?: currentUser,
                        itemLabel = { "${it.namaLengkap} (${it.role})" },
                        onSelect = { mUid = it.id }
                    )
                    PortalInputField("Akun Debit", mDeb, { mDeb = it })
                    PortalInputField("Akun Kredit", mKre, { mKre = it })
                    PortalInputField("Pokok (Rp)", mPok, { mPok = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Admin (Rp)", mAdm, { mAdm = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Ujrah (Rp)", mUjr, { mUjr = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Total Kas (Rp)", mTot, { mTot = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Keterangan", mKet, { mKet = it }, placeholder = "Catatan jurnal manual / serah terima kas")
                    PortalButton("Simpan Jurnal Manual", icon = Icons.Filled.AddCircleOutline, style = BtnStyle.PRIMARY) {
                        vm.addJurnalManual(
                            mTgl, mRef, mMenu, mArus, mDeb, mKre,
                            mPok.toDoubleOrNull() ?: 0.0,
                            mAdm.toDoubleOrNull() ?: 0.0,
                            mUjr.toDoubleOrNull() ?: 0.0,
                            mTot.toDoubleOrNull() ?: 0.0,
                            mKet, mUid
                        )
                        mTot = ""; mKet = ""
                    }
                }
            }
        }

        // KPI Stats
        StatCard(
            title = "Modal Awal & Saldo Kas Filter",
            value = FormatUtils.rupiah(netKas),
            subtitle = "Modal Sistem: ${FormatUtils.rupiah(pengaturan.modalAwal)}",
            linkLabel = "Ubah Modal Awal →",
            onLinkClick = { vm.navigateTo(AppRoute.PENGATURAN_RUMUS) }
        )
        StatCard(
            title = "Total Modal Keluar (Qardh)",
            value = FormatUtils.rupiah(modalKeluar),
            linkLabel = "Lihat Barang Aktif →",
            onLinkClick = { vm.openDataBarang(status = "AKTIF") }
        )
        StatCard(
            title = "Total Pokok Kembali",
            value = FormatUtils.rupiah(pokokKembali)
        )
        StatCard(
            title = "Pendapatan Jasa (Ujrah + Admin)",
            value = FormatUtils.rupiah(pendUjrah + pendAdmin),
            isYellow = true
        )
        StatCard(
            title = "Status Jaminan",
            value = "$cntAktif Aktif · $cntPerp Perpanjang",
            subtitle = "Lunas: $cntLunas · Lewat Tempo: $cntLewat",
            linkLabel = "Buka Monitoring Tempo →",
            onLinkClick = { vm.navigateTo(AppRoute.MONITORING_TEMPO) }
        )

        // Jurnal Akuntansi List
        PanelCard {
            PanelHeader(title = "Jurnal Akuntansi & Kasir Bertugas", icon = Icons.Filled.Description) {
                PortalButton("Daftar User", icon = Icons.Filled.ManageAccounts, compact = true) {
                    vm.navigateTo(AppRoute.MANAJEMEN_USER)
                }
            }
            if (filteredJurnal.isEmpty()) {
                Text("Tidak ada jurnal sesuai filter.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredJurnal.forEach { j ->
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
                                    text = "${j.tanggal} · ${j.noAkad}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.clickable {
                                        if (j.noAkad == "MODAL-AWAL") vm.navigateTo(AppRoute.PENGATURAN_RUMUS)
                                        else if (j.noAkad.startsWith("AG-")) vm.openNotaAkad(j.noAkad)
                                    }
                                )
                                Text(
                                    text = "${j.arusKas} ${FormatUtils.rupiah(j.totalKas)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                            Text(j.keterangan, fontSize = 12.sp, color = TextPrimary)
                            Text(
                                text = "${j.jenisMenu} | P:${FormatUtils.rupiah(j.pokokNominal)} · U:${FormatUtils.rupiah(j.pendapatanUjrah)} · A:${FormatUtils.rupiah(j.pendapatanAdmin)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "Kasir: ${j.namaKasir}",
                                fontSize = 11.sp,
                                color = EmeraldPrimary,
                                modifier = Modifier.clickable { j.userId?.let { filterUid = it } }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) { editingJurnal = j }
                                PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) { deleteJurnalTarget = j }
                            }
                        }
                    }
                }
            }
        }

        // Log Tutup Toko Harian
        PanelCard {
            PanelHeader(title = "Log Tutup Toko Harian", icon = Icons.Filled.Lock) {
                PortalButton("Buka Tutup Toko", icon = Icons.Filled.Lock, compact = true) {
                    vm.openTutupToko()
                }
            }
            if (filteredSesi.isEmpty()) {
                Text("Belum ada log tutup toko.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredSesi.forEach { s ->
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
                                    text = "${s.tanggal} (${s.status})",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.clickable { vm.openTutupToko(s.tanggal) }
                                )
                                Text(s.namaKasir, fontSize = 11.5.sp, color = TextMuted)
                            }
                            Text(
                                text = "Sistem: ${FormatUtils.rupiah(s.saldoKasSistem)} · Fisik: ${FormatUtils.rupiah(s.uangFisikAktual ?: 0.0)} · Selisih: ${FormatUtils.rupiah(s.selisihKas ?: 0.0)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            PortalButton("Buka Kunci", icon = Icons.Filled.LockOpen, style = BtnStyle.YELLOW, compact = true) {
                                reopenSesiTarget = s
                            }
                        }
                    }
                }
            }
        }
    }

    deleteJurnalTarget?.let { target ->
        ConfirmActionDialog(
            title = "Hapus Baris Jurnal?",
            message = "Hapus baris jurnal #${target.id} (${target.noAkad})?",
            confirmText = "Hapus",
            onConfirm = { vm.deleteJurnal(target.id) },
            onDismiss = { deleteJurnalTarget = null }
        )
    }

    reopenSesiTarget?.let { target ->
        ConfirmActionDialog(
            title = "Buka Kunci Tutup Toko?",
            message = "Buka kunci sesi tutup toko tanggal ${target.tanggal} agar status menjadi OPEN kembali?",
            confirmText = "Buka Kunci",
            onConfirm = { vm.bukaKunciSesi(target.id) },
            onDismiss = { reopenSesiTarget = null }
        )
    }
}
