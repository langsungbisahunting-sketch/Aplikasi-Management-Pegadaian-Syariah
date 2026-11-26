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
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.ui.AppRoute
import com.example.ui.AppViewModel
import com.example.ui.components.BtnStyle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.GadaiStatusBadge
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
import com.example.utils.PrintHelper
import com.example.utils.XlsxSheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataBarangScreen(
    vm: AppViewModel,
    pengaturan: PengaturanSistemEntity,
    transaksiList: List<TransaksiGadaiEntity>
) {
    val context = LocalContext.current
    val initialQuery by vm.dataBarangQuery.collectAsState()
    val initialStatus by vm.dataBarangStatus.collectAsState()

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var statusFilter by remember(initialStatus) { mutableStateOf(initialStatus) }
    var deleteTarget by remember { mutableStateOf<TransaksiGadaiEntity?>(null) }

    val filtered = remember(transaksiList, query, statusFilter) {
        transaksiList.filter { r ->
            val qMatch = query.isBlank() || (r.noAkad + r.namaNasabah + r.namaBarang + r.spesifikasiRingkas)
                .contains(query, ignoreCase = true)
            val sMatch = when (statusFilter) {
                "" -> true
                "AKTIF" -> r.statusGadai == "AKTIF" || r.statusGadai == "DIPERPANJANG"
                else -> r.statusGadai == statusFilter
            }
            qMatch && sMatch
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val rows = mutableListOf<List<Any?>>(
                listOf("No. Akad", "Tanggal", "Jatuh Tempo", "Rahin", "Kategori", "Barang", "Taksiran (Rp)", "Pokok Qardh (Rp)", "Ujrah (Rp)", "Status", "Kasir")
            )
            filtered.forEach { r ->
                rows.add(listOf(r.noAkad, r.tanggalAkad, r.jatuhTempo, r.namaNasabah, r.kategoriBarang, r.namaBarang, r.nilaiTaksiran, r.pokokPinjaman, r.biayaUjrahPeriode, r.statusGadai, r.namaKasir))
            }
            vm.exportXlsxToUri(uri, listOf(XlsxSheet("Data_Barang", rows)), "Data Barang Marhun")
        }
    }

    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val templateSheets = listOf(
                XlsxSheet(
                    "Template_Gadai",
                    listOf(
                        listOf("No_Akad", "Tanggal_Akad", "Nama_Nasabah", "NIK", "No_HP", "Alamat", "Kategori", "Nama_Barang", "Nilai_Taksiran", "Pokok_Pinjaman", "Spesifikasi", "Kondisi"),
                        listOf("AG-XLS-001", FormatUtils.todayIso(), "Contoh Nasabah", "3175011203940001", "081234567890", "Jl. Contoh No 1", "HP", "Samsung S24", 8000000, 6000000, "RAM 8/256 IMEI 35698211", "Mulus normal")
                    )
                ),
                XlsxSheet(
                    "Panduan",
                    listOf(
                        listOf("Kolom", "Keterangan"),
                        listOf("Tanggal_Akad", "Wajib format YYYY-MM-DD"),
                        listOf("Kategori", "Hanya HP, Motor, atau Emas")
                    )
                )
            )
            vm.exportXlsxToUri(uri, templateSheets, "Template Gadai Syariah")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importXlsxFromUri(uri)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PanelCard(isYellow = true) {
            PanelHeader(title = "Master Data Barang (Marhun) & Excel (.xlsx)", icon = Icons.Filled.Inventory2) {
                PortalButton("Tambah Barang / Akad", icon = Icons.Filled.AddCircleOutline, style = BtnStyle.PRIMARY, compact = true) {
                    vm.navigateTo(AppRoute.GADAI_BARANG)
                }
                PortalButton("Export Excel (.xlsx)", icon = Icons.Filled.Download, compact = true) {
                    exportLauncher.launch("data_barang_marhun_${FormatUtils.todayIso()}.xlsx")
                }
                PortalButton("Download Template (.xlsx)", icon = Icons.Filled.Download, compact = true) {
                    templateLauncher.launch("template_gadai_syariah.xlsx")
                }
                PortalButton("Upload & Import .xlsx", icon = Icons.Filled.UploadFile, style = BtnStyle.YELLOW, compact = true) {
                    importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                }
                PortalButton("Cetak", icon = Icons.Filled.Print, compact = true) {
                    val headers = listOf("No. Akad", "Tanggal", "Barang", "Rahin", "Pokok", "Ujrah", "Status")
                    val rows = filtered.map {
                        listOf(it.noAkad, it.tanggalAkad, "${it.kategoriBarang} - ${it.namaBarang}", it.namaNasabah, FormatUtils.rupiah(it.pokokPinjaman), FormatUtils.rupiah(it.biayaUjrahPeriode), it.statusGadai)
                    }
                    PrintHelper.printHtml(context, "Data_Barang_Marhun", PrintHelper.buildTableReportHtml(pengaturan, "Data Barang Jaminan (Marhun)", headers, rows))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalInputField("Cari akad, rahin, barang...", query, { query = it })
                PortalDropdown(
                    label = "Filter Status",
                    items = listOf("", "AKTIF", "DIPERPANJANG", "LUNAS", "LELANG"),
                    selectedItem = statusFilter,
                    itemLabel = {
                        when (it) {
                            "" -> "Semua Status"
                            "AKTIF" -> "AKTIF & DIPERPANJANG"
                            else -> it
                        }
                    },
                    onSelect = { statusFilter = it }
                )
                if (query.isNotEmpty() || statusFilter.isNotEmpty()) {
                    PortalButton("Reset Filter", compact = true) {
                        query = ""
                        statusFilter = ""
                        vm.dataBarangQuery.value = ""
                        vm.dataBarangStatus.value = ""
                    }
                }
            }
        }

        PanelCard {
            if (filtered.isEmpty()) {
                Text("Belum ada data barang yang sesuai filter.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { r ->
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
                                GadaiStatusBadge(r.statusGadai)
                            }
                            Text("${r.tanggalAkad} · Kasir: ${r.namaKasir}", fontSize = 11.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${r.kategoriBarang} - ${r.namaBarang}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                            if (r.spesifikasiRingkas.isNotBlank()) {
                                Text(r.spesifikasiRingkas, fontSize = 11.5.sp, color = TextMuted)
                            }
                            Text(
                                text = "Rahin: ${r.namaNasabah}",
                                fontSize = 12.sp,
                                color = EmeraldPrimary,
                                modifier = Modifier.clickable { vm.openDataPelanggan(query = r.namaNasabah) }
                            )
                            Text(
                                text = "Pokok: ${FormatUtils.rupiah(r.pokokPinjaman)} · Ujrah: ${FormatUtils.rupiah(r.biayaUjrahPeriode)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                PortalButton("Nota", icon = Icons.Filled.Print, compact = true) { vm.openNotaAkad(r.noAkad) }
                                if (r.statusGadai == "AKTIF" || r.statusGadai == "DIPERPANJANG") {
                                    PortalButton("Perpanjang", icon = Icons.Filled.Refresh, compact = true) { vm.openPerpanjang(r.noAkad) }
                                    PortalButton("Tebus", icon = Icons.Filled.CheckCircleOutline, compact = true) { vm.openTebus(r.noAkad) }
                                }
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
            message = "Barang & akad ${target.noAkad} beserta jurnal terkait akan dihapus.",
            confirmText = "Hapus",
            onConfirm = { vm.deleteGadai(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}
