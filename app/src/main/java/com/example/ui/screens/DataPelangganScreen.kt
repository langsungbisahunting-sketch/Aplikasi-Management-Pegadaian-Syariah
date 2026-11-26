package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
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
import com.example.data.db.DokumenPelangganEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.UserEntity
import com.example.data.repository.CustomerSummary
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
import com.example.ui.theme.SuccessBg
import com.example.ui.theme.SuccessBorder
import com.example.ui.theme.SuccessText
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.utils.FormatUtils
import com.example.utils.PrintHelper
import com.example.utils.XlsxSheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DataPelangganScreen(
    vm: AppViewModel,
    currentUser: UserEntity,
    pengaturan: PengaturanSistemEntity,
    customers: List<CustomerSummary>,
    dokumenMap: Map<String, DokumenPelangganEntity>
) {
    val context = LocalContext.current
    val isOwner = currentUser.role == "Owner"
    val initialQuery by vm.dataPelangganQuery.collectAsState()
    val pdfEditNik by vm.dataPelangganPdfEditNik.collectAsState()

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var editingCustomer by remember { mutableStateOf<CustomerSummary?>(null) }
    var deleteCustomerTarget by remember { mutableStateOf<CustomerSummary?>(null) }
    var deletePdfNikTarget by remember { mutableStateOf<CustomerSummary?>(null) }

    val filtered = remember(customers, query) {
        if (query.isBlank()) customers
        else customers.filter {
            (it.namaNasabah + it.nikNasabah + it.noHp).contains(query, ignoreCase = true)
        }
    }

    val pdfEditCustomer = remember(pdfEditNik, customers) {
        if (pdfEditNik.isNullOrBlank()) null else customers.firstOrNull { it.nikNasabah == pdfEditNik }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            val rows = mutableListOf<List<Any?>>(
                listOf("Nama Rahin", "NIK", "No. HP", "Alamat", "Akad Aktif", "Total Akad", "Pokok Aktif (Rp)", "Status Dokumen PDF", "Nama File PDF")
            )
            filtered.forEach { r ->
                val d = dokumenMap[r.nikNasabah]
                rows.add(listOf(r.namaNasabah, r.nikNasabah, r.noHp, r.alamatNasabah, r.akadAktif, r.totalAkad, r.pokokAktif, if (d != null) "ADA" else "BELUM", d?.namaFileAsli ?: "-"))
            }
            vm.exportXlsxToUri(uri, listOf(XlsxSheet("Pelanggan", rows)), "Data Pelanggan")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Upload / Replace PDF panel for selected customer
        if (pdfEditCustomer != null) {
            val curDok = dokumenMap[pdfEditCustomer.nikNasabah]
            var pdfUri by remember(pdfEditCustomer.nikNasabah) { mutableStateOf<Uri?>(null) }
            var ketDok by remember(pdfEditCustomer.nikNasabah) {
                mutableStateOf(curDok?.keterangan ?: "Bukti Data Diri (KTP/KK) & Dokumen Pendukung")
            }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) pdfUri = uri
            }
            PanelCard(isYellow = true) {
                PanelHeader(
                    title = "${if (curDok != null) "Edit / Ganti" else "Upload"} Dokumen PDF: ${pdfEditCustomer.namaNasabah}",
                    icon = Icons.Filled.Folder
                ) {
                    PortalButton("Tutup", compact = true) { vm.dataPelangganPdfEditNik.value = null }
                }
                if (curDok != null) {
                    Text(
                        text = "Dokumen aktif saat ini: ${curDok.namaFileAsli} (${FormatUtils.formatUkuranFile(curDok.ukuranByte)})",
                        fontSize = 11.5.sp,
                        color = SuccessText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SuccessBg)
                            .border(1.dp, SuccessBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalButton("Pilih File PDF Baru", icon = Icons.Filled.UploadFile, style = BtnStyle.YELLOW, compact = true) {
                            picker.launch(arrayOf("application/pdf"))
                        }
                        Text(pdfUri?.lastPathSegment ?: "Belum dipilih", fontSize = 11.5.sp, color = TextMuted)
                    }
                    PortalInputField("Keterangan Dokumen", ketDok, { ketDok = it })
                    PortalButton(
                        text = if (curDok != null) "Ganti File PDF Baru" else "Upload File PDF",
                        icon = Icons.Filled.UploadFile,
                        style = BtnStyle.PRIMARY,
                        enabled = pdfUri != null
                    ) {
                        pdfUri?.let { uri ->
                            vm.uploadDokumenPdf(pdfEditCustomer.nikNasabah, pdfEditCustomer.namaNasabah, uri, ketDok)
                        }
                    }
                }
            }
        }

        // Edit Customer Panel (Owner only)
        editingCustomer?.let { cust ->
            if (isOwner) {
                val curDok = dokumenMap[cust.nikNasabah]
                var newNama by remember(cust.nikNasabah) { mutableStateOf(cust.namaNasabah) }
                var newNik by remember(cust.nikNasabah) { mutableStateOf(cust.nikNasabah) }
                var newHp by remember(cust.nikNasabah) { mutableStateOf(cust.noHp) }
                var newAlm by remember(cust.nikNasabah) { mutableStateOf(cust.alamatNasabah) }
                var pdfUri by remember(cust.nikNasabah) { mutableStateOf<Uri?>(null) }
                var ketDok by remember(cust.nikNasabah) { mutableStateOf(curDok?.keterangan ?: "Bukti Data Diri (KTP/KK) & Dokumen Pendukung") }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) pdfUri = uri
                }

                PanelCard(isYellow = true) {
                    PanelHeader(title = "Edit Identitas Pelanggan (Rahin)", icon = Icons.Filled.Edit) {
                        PortalButton("Batal", compact = true) { editingCustomer = null }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalInputField("Nama Rahin", newNama, { newNama = it })
                        PortalInputField("NIK", newNik, { newNik = it }, mono = true)
                        PortalInputField("No. HP", newHp, { newHp = it }, mono = true)
                        PortalInputField("Alamat", newAlm, { newAlm = it })
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PortalButton("Ganti Dokumen PDF (Opsional)", icon = Icons.Filled.UploadFile, compact = true) {
                                picker.launch(arrayOf("application/pdf"))
                            }
                            Text(pdfUri?.lastPathSegment ?: (curDok?.namaFileAsli ?: "Tidak diubah"), fontSize = 11.5.sp, color = TextMuted)
                        }
                        PortalInputField("Keterangan Dokumen", ketDok, { ketDok = it })
                        PortalButton("Simpan Perubahan", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY) {
                            vm.updatePelanggan(cust.namaNasabah, cust.nikNasabah, newNama, newNik, newHp, newAlm, pdfUri, ketDok)
                            editingCustomer = null
                        }
                    }
                }
            }
        }

        PanelCard(isYellow = true) {
            PanelHeader(title = "Daftar Nasabah (Rahin)", icon = Icons.Filled.People) {
                PortalButton("Menu Dokumen PDF", icon = Icons.Filled.Folder, style = BtnStyle.PRIMARY, compact = true) {
                    vm.navigateTo(AppRoute.DOKUMEN_PELANGGAN)
                }
                PortalButton("Export Excel (.xlsx)", icon = Icons.Filled.Download, compact = true) {
                    exportLauncher.launch("data_pelanggan_${FormatUtils.todayIso()}.xlsx")
                }
                PortalButton("Cetak", icon = Icons.Filled.Print, compact = true) {
                    val headers = listOf("Nama Rahin", "NIK", "No. HP", "Alamat", "Aktif/Total", "Pokok Aktif", "Dokumen PDF")
                    val rows = filtered.map {
                        val d = dokumenMap[it.nikNasabah]
                        listOf(it.namaNasabah, it.nikNasabah, it.noHp, it.alamatNasabah, "${it.akadAktif}/${it.totalAkad}", FormatUtils.rupiah(it.pokokAktif), d?.namaFileAsli ?: "-")
                    }
                    PrintHelper.printHtml(context, "Data_Pelanggan", PrintHelper.buildTableReportHtml(pengaturan, "Data Pelanggan (Rahin)", headers, rows))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalInputField("Cari nama, NIK, atau No. HP...", query, { query = it })
                if (query.isNotEmpty()) {
                    PortalButton("Reset Pencarian", compact = true) {
                        query = ""
                        vm.dataPelangganQuery.value = ""
                    }
                }
            }
        }

        PanelCard {
            if (filtered.isEmpty()) {
                Text("Belum ada data pelanggan.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { r ->
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
                                Text(r.namaNasabah, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = TextPrimary)
                                Text(
                                    text = "${r.akadAktif} / ${r.totalAkad} Akad",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                            Text("NIK: ${r.nikNasabah} · HP: ${r.noHp}", fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = TextMuted)
                            Text(r.alamatNasabah, fontSize = 11.5.sp, color = TextMuted)
                            Text("Pokok Aktif: ${FormatUtils.rupiah(r.pokokAktif)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)

                            if (dok != null) {
                                Text(
                                    text = "PDF: ${dok.namaFileAsli} (${FormatUtils.formatUkuranFile(dok.ukuranByte)})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessText,
                                    modifier = Modifier.clickable { vm.openDokumenPelanggan(r.nikNasabah, preview = true) }
                                )
                            } else {
                                Text("Belum ada PDF", fontSize = 11.5.sp, color = TextMuted)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                PortalButton("Gadai Baru", icon = Icons.Filled.AddCircleOutline, style = BtnStyle.PRIMARY, compact = true) {
                                    vm.openGadaiWithCustomer(r.namaNasabah, r.nikNasabah, r.noHp, r.alamatNasabah)
                                }
                                if (dok != null) {
                                    PortalButton("Lihat PDF", icon = Icons.Filled.Visibility, compact = true) {
                                        vm.openDokumenPelanggan(r.nikNasabah, preview = true)
                                    }
                                    PortalButton("Ganti PDF", icon = Icons.Filled.UploadFile, style = BtnStyle.YELLOW, compact = true) {
                                        vm.dataPelangganPdfEditNik.value = r.nikNasabah
                                    }
                                    PortalButton("Hapus PDF", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) {
                                        deletePdfNikTarget = r
                                    }
                                } else {
                                    PortalButton("Upload PDF", icon = Icons.Filled.UploadFile, style = BtnStyle.YELLOW, compact = true) {
                                        vm.dataPelangganPdfEditNik.value = r.nikNasabah
                                    }
                                }
                                if (isOwner) {
                                    PortalButton("Barang", icon = Icons.Filled.Inventory2, compact = true) {
                                        vm.openDataBarang(query = r.namaNasabah)
                                    }
                                    PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) {
                                        editingCustomer = r
                                    }
                                    PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) {
                                        deleteCustomerTarget = r
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletePdfNikTarget?.let { target ->
        ConfirmActionDialog(
            title = "Hapus File PDF?",
            message = "Hapus file PDF milik ${target.namaNasabah} (NIK: ${target.nikNasabah})?",
            confirmText = "Hapus PDF",
            onConfirm = { vm.deleteDokumenPdf(target.nikNasabah) },
            onDismiss = { deletePdfNikTarget = null }
        )
    }

    deleteCustomerTarget?.let { target ->
        ConfirmActionDialog(
            title = "Hapus Pelanggan ${target.namaNasabah}?",
            message = "Data pelanggan beserta seluruh akad dan jurnal terkait akan dihapus permanen.",
            confirmText = "Hapus Pelanggan",
            onConfirm = { vm.deletePelanggan(target.namaNasabah, target.nikNasabah) },
            onDismiss = { deleteCustomerTarget = null }
        )
    }
}
