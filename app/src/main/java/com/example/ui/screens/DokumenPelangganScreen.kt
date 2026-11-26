package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.DokumenPelangganEntity
import com.example.data.repository.CustomerSummary
import com.example.ui.AppRoute
import com.example.ui.AppViewModel
import com.example.ui.components.BtnStyle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.PanelCard
import com.example.ui.components.PanelHeader
import com.example.ui.components.PortalButton
import com.example.ui.components.PortalDropdown
import com.example.ui.components.PortalInputField
import com.example.ui.components.StatCard
import com.example.ui.components.StatusTag
import com.example.ui.theme.BorderCard
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
import com.example.utils.PdfStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DokumenPelangganScreen(
    vm: AppViewModel,
    customers: List<CustomerSummary>,
    dokumenMap: Map<String, DokumenPelangganEntity>
) {
    val context = LocalContext.current
    val selectedNikState by vm.dokumenSelectedNik.collectAsState()
    val previewNikState by vm.dokumenPreviewNik.collectAsState()

    val validCustomers = remember(customers) { customers.filter { it.nikNasabah.isNotBlank() } }
    var selectedCustomer by remember(selectedNikState, validCustomers) {
        mutableStateOf(validCustomers.firstOrNull { it.nikNasabah == selectedNikState })
    }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var keterangan by remember { mutableStateOf("Bukti Data Diri (KTP/KK) & Dokumen Pendukung") }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") } // "" | "ADA" | "BELUM"
    var deleteTarget by remember { mutableStateOf<CustomerSummary?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) selectedPdfUri = uri
    }

    var downloadTargetDok by remember { mutableStateOf<DokumenPelangganEntity?>(null) }
    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destUri ->
        val dok = downloadTargetDok
        if (destUri != null && dok != null) {
            val ok = PdfStorageHelper.copyFileToUri(context, dok.namaFileSimpan, destUri)
            vm.setBanner(ok, if (ok) "Dokumen PDF berhasil disimpan." else "Gagal mengunduh file PDF.")
        }
        downloadTargetDok = null
    }

    val totalPelanggan = validCustomers.size
    val totalAdaDok = validCustomers.count { dokumenMap.containsKey(it.nikNasabah) }
    val totalBelumDok = (totalPelanggan - totalAdaDok).coerceAtLeast(0)

    val filtered = remember(validCustomers, dokumenMap, searchQuery, statusFilter) {
        validCustomers.filter { c ->
            val dok = dokumenMap[c.nikNasabah]
            val sOk = when (statusFilter) {
                "ADA" -> dok != null
                "BELUM" -> dok == null
                else -> true
            }
            val qOk = searchQuery.isBlank() || (
                c.namaNasabah + " " + c.nikNasabah + " " + c.noHp + " " +
                    (dok?.namaFileAsli.orEmpty()) + " " + (dok?.keterangan.orEmpty())
                ).contains(searchQuery, ignoreCase = true)
            sOk && qOk
        }
    }

    val previewDok = remember(previewNikState, dokumenMap) {
        if (previewNikState.isNullOrBlank()) null else dokumenMap[previewNikState]
    }
    var previewPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(previewDok?.namaFileSimpan) {
        previewPages = if (previewDok != null) {
            withContext(Dispatchers.IO) {
                PdfStorageHelper.renderPdfPages(context, previewDok.namaFileSimpan)
            }
        } else emptyList()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        StatCard(
            title = "Total Pelanggan (Rahin)",
            value = "$totalPelanggan Orang",
            linkLabel = "Tampilkan Semua →",
            onLinkClick = { statusFilter = "" }
        )
        StatCard(
            title = "Sudah Upload Dokumen PDF",
            value = "$totalAdaDok PDF",
            linkLabel = "Filter Sudah Upload →",
            onLinkClick = { statusFilter = "ADA" }
        )
        StatCard(
            title = "Belum Ada Dokumen PDF",
            value = "$totalBelumDok Orang",
            linkLabel = "Filter Belum Upload →",
            onLinkClick = { statusFilter = "BELUM" },
            isYellow = true
        )

        // In-App PDF Preview Panel
        if (previewDok != null) {
            PanelCard {
                PanelHeader(
                    title = "Preview PDF: ${previewDok.namaNasabah} (NIK: ${previewDok.nikNasabah})",
                    icon = Icons.Filled.Visibility
                ) {
                    PortalButton("Buka Aplikasi Lain", icon = Icons.Filled.Description, style = BtnStyle.PRIMARY, compact = true) {
                        val opened = PdfStorageHelper.openExternal(context, previewDok.namaFileSimpan)
                        if (!opened) vm.setBanner(false, "Tidak ada aplikasi pembuka PDF yang tersedia.")
                    }
                    PortalButton("Download PDF", icon = Icons.Filled.Download, compact = true) {
                        downloadTargetDok = previewDok
                        savePdfLauncher.launch(previewDok.namaFileAsli)
                    }
                    PortalButton("Data Pelanggan", icon = Icons.Filled.People, compact = true) {
                        vm.openDataPelanggan(query = previewDok.nikNasabah)
                    }
                    PortalButton("Tutup Preview", compact = true) {
                        vm.dokumenPreviewNik.value = null
                    }
                }
                Text(
                    text = "File: ${previewDok.namaFileAsli} (${FormatUtils.formatUkuranFile(previewDok.ukuranByte)}) · ${previewDok.keterangan} · Diperbarui: ${previewDok.updatedAt} oleh ${previewDok.namaKasir}",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (previewPages.isEmpty()) {
                    Text("Menyiapkan preview halaman PDF...", fontSize = 12.sp, color = TextMuted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        previewPages.forEachIndexed { idx, bmp ->
                            Text("Halaman ${idx + 1}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Halaman ${idx + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                                    .border(1.dp, BorderCard, RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }
        }

        // Upload / Replace Form
        PanelCard(isYellow = true) {
            PanelHeader(
                title = "Upload / Ganti Dokumen Pelanggan (1 PDF per Orang)",
                icon = Icons.Filled.UploadFile
            ) {
                PortalButton("Gadai Baru (+ Upload PDF)", icon = Icons.Filled.AddCircleOutline, style = BtnStyle.PRIMARY, compact = true) {
                    vm.navigateTo(AppRoute.GADAI_BARANG)
                }
                PortalButton("Data Pelanggan", icon = Icons.Filled.People, compact = true) {
                    vm.navigateTo(AppRoute.DATA_PELANGGAN)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalDropdown(
                    label = "Pilih Pelanggan (Rahin)",
                    items = validCustomers,
                    selectedItem = selectedCustomer,
                    itemLabel = {
                        val has = dokumenMap.containsKey(it.nikNasabah)
                        "${it.namaNasabah} — ${it.nikNasabah} (${if (has) "Sudah Ada PDF - Akan Mengganti" else "Belum Ada PDF"})"
                    },
                    onSelect = { selectedCustomer = it },
                    placeholder = "-- Pilih Pelanggan --"
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortalButton("Pilih File PDF (.pdf)", icon = Icons.Filled.UploadFile, style = BtnStyle.YELLOW, compact = true) {
                        pdfPicker.launch(arrayOf("application/pdf"))
                    }
                    Text(selectedPdfUri?.lastPathSegment ?: "Belum dipilih", fontSize = 11.5.sp, color = TextMuted)
                }
                PortalInputField("Keterangan Isi Dokumen", keterangan, { keterangan = it })
                Text(
                    text = "Maksimal 1 file PDF per pelanggan. Mengunggah file baru otomatis menggantikan PDF lama pelanggan tersebut.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                PortalButton(
                    text = "Simpan / Ganti Dokumen PDF",
                    icon = Icons.Filled.UploadFile,
                    style = BtnStyle.PRIMARY,
                    enabled = selectedCustomer != null && selectedPdfUri != null
                ) {
                    val cust = selectedCustomer
                    val uri = selectedPdfUri
                    if (cust != null && uri != null) {
                        vm.uploadDokumenPdf(cust.nikNasabah, cust.namaNasabah, uri, keterangan, showPreviewAfter = true)
                        selectedPdfUri = null
                    }
                }
            }
        }

        // Archive List
        PanelCard {
            PanelHeader(title = "Arsip Dokumen Pelanggan", icon = Icons.Filled.Folder)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                PortalInputField("Cari nama pelanggan, NIK, HP, atau nama file...", searchQuery, { searchQuery = it })
                PortalDropdown(
                    label = "Status Dokumen",
                    items = listOf("", "ADA", "BELUM"),
                    selectedItem = statusFilter,
                    itemLabel = {
                        when (it) {
                            "" -> "Semua Status Dokumen"
                            "ADA" -> "Sudah Ada PDF"
                            else -> "Belum Upload PDF"
                        }
                    },
                    onSelect = { statusFilter = it }
                )
            }

            if (filtered.isEmpty()) {
                Text("Belum ada data pelanggan yang sesuai filter.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { c ->
                        val dok = dokumenMap[c.nikNasabah]
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
                                    text = c.namaNasabah,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = EmeraldPrimary,
                                    modifier = Modifier.clickable { vm.openDataPelanggan(query = c.nikNasabah) }
                                )
                                if (dok != null) {
                                    StatusTag("PDF TERSEDIA", SuccessBg, SuccessText, SuccessBorder)
                                } else {
                                    StatusTag("BELUM UPLOAD", WarningBg, WarningText, WarningBorder)
                                }
                            }
                            Text("NIK: ${c.nikNasabah} · HP: ${c.noHp}", fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = TextMuted)
                            Text("${c.akadAktif} Aktif / ${c.totalAkad} Akad", fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = TextPrimary)
                            if (dok != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(dok.namaFileAsli, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextPrimary)
                                Text("${FormatUtils.formatUkuranFile(dok.ukuranByte)} · ${dok.updatedAt} · ${dok.keterangan} (Oleh: ${dok.namaKasir})", fontSize = 11.sp, color = TextMuted)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                if (dok != null) {
                                    PortalButton("Preview", icon = Icons.Filled.Visibility, style = BtnStyle.PRIMARY, compact = true) {
                                        vm.dokumenPreviewNik.value = c.nikNasabah
                                    }
                                    PortalButton("Buka", icon = Icons.Filled.Description, compact = true) {
                                        val ok = PdfStorageHelper.openExternal(context, dok.namaFileSimpan)
                                        if (!ok) vm.setBanner(false, "Tidak ada aplikasi pembuka PDF.")
                                    }
                                    PortalButton("Unduh", icon = Icons.Filled.Download, compact = true) {
                                        downloadTargetDok = dok
                                        savePdfLauncher.launch(dok.namaFileAsli)
                                    }
                                    PortalButton("Ganti PDF", icon = Icons.Filled.Edit, style = BtnStyle.YELLOW, compact = true) {
                                        selectedCustomer = c
                                        vm.dokumenSelectedNik.value = c.nikNasabah
                                    }
                                    PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) {
                                        deleteTarget = c
                                    }
                                } else {
                                    PortalButton("Upload PDF", icon = Icons.Filled.UploadFile, style = BtnStyle.PRIMARY, compact = true) {
                                        selectedCustomer = c
                                        vm.dokumenSelectedNik.value = c.nikNasabah
                                    }
                                }
                                PortalButton("Gadai Baru", icon = Icons.Filled.AddCircleOutline, compact = true) {
                                    vm.openGadaiWithCustomer(c.namaNasabah, c.nikNasabah, c.noHp, c.alamatNasabah)
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
            title = "Hapus Dokumen PDF?",
            message = "Hapus dokumen PDF milik ${target.namaNasabah} (NIK: ${target.nikNasabah})?",
            confirmText = "Hapus PDF",
            onConfirm = { vm.deleteDokumenPdf(target.nikNasabah) },
            onDismiss = { deleteTarget = null }
        )
    }
}
