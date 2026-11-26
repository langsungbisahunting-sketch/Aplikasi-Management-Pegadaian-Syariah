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
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.DokumenPelangganEntity
import com.example.data.db.PaketUjrahEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.RumusLtvEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.data.db.UserEntity
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
import com.example.ui.theme.SuccessBg
import com.example.ui.theme.SuccessBorder
import com.example.ui.theme.SuccessText
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.YellowPanelBg
import com.example.ui.theme.YellowPanelBorder
import com.example.utils.FormatUtils
import com.example.utils.PrintHelper
import kotlin.math.max
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GadaiBarangScreen(
    vm: AppViewModel,
    currentUser: UserEntity,
    pengaturan: PengaturanSistemEntity,
    transaksiList: List<TransaksiGadaiEntity>,
    ltvList: List<RumusLtvEntity>,
    paketList: List<PaketUjrahEntity>,
    dokumenMap: Map<String, DokumenPelangganEntity>
) {
    val context = LocalContext.current
    val isOwner = currentUser.role == "Owner"
    val prefill by vm.gadaiPrefill.collectAsState()
    val cetakNo by vm.cetakAkadNo.collectAsState()
    val editId by vm.editGadaiId.collectAsState()
    val nextAkad by vm.nextAkadSuggested.collectAsState()

    val nota = remember(cetakNo, transaksiList) {
        if (cetakNo.isNullOrBlank()) null else transaksiList.firstOrNull { it.noAkad == cetakNo }
    }
    val editTx = remember(editId, transaksiList) {
        if (editId == null) null else transaksiList.firstOrNull { it.id == editId }
    }

    var deleteTarget by remember { mutableStateOf<TransaksiGadaiEntity?>(null) }

    // Form state
    var noAkad by remember(nextAkad) { mutableStateOf(nextAkad) }
    var tanggalAkad by remember { mutableStateOf(FormatUtils.todayIso()) }
    var kategori by remember { mutableStateOf("HP") }
    var namaNasabah by remember(prefill) { mutableStateOf(prefill.nama) }
    var nikNasabah by remember(prefill) { mutableStateOf(prefill.nik) }
    var noHp by remember(prefill) { mutableStateOf(prefill.hp) }
    var alamatNasabah by remember(prefill) { mutableStateOf(prefill.alamat) }
    var kondisiBarang by remember { mutableStateOf("") }

    // HP fields
    var hpMerk by remember { mutableStateOf("") }
    var hpRam by remember { mutableStateOf("") }
    var hpImei by remember { mutableStateOf("") }
    var hpUnit by remember { mutableStateOf(true) }
    var hpDus by remember { mutableStateOf(true) }
    var hpCharger by remember { mutableStateOf(true) }

    // Motor fields
    var motorMerk by remember { mutableStateOf("") }
    var motorTahun by remember { mutableStateOf("") }
    var motorWarna by remember { mutableStateOf("") }
    var motorNopol by remember { mutableStateOf("") }
    var motorRangka by remember { mutableStateOf("") }
    var motorMesin by remember { mutableStateOf("") }
    var motorTitipan by remember { mutableStateOf("Titip Unit & Dokumen") }
    var motorBpkb by remember { mutableStateOf(true) }
    var motorStnk by remember { mutableStateOf(true) }

    // Emas fields
    var emasJenis by remember { mutableStateOf("Perhiasan") }
    var emasKarat by remember { mutableStateOf("24K") }
    var emasBerat by remember { mutableStateOf("") }
    var emasSurat by remember { mutableStateOf(true) }

    // Valuation & Ujrah
    var nilaiTaksiranStr by remember { mutableStateOf("") }
    var pokokPinjamanStr by remember { mutableStateOf("") }
    val activeLtvPct = remember(kategori, ltvList) {
        val active = ltvList.filter { it.kategori == kategori && it.aktif == 1 }
            .sortedByDescending { it.isDefault }
            .firstOrNull()
        active?.persentase ?: 75.0
    }
    val categoryPackages = remember(kategori, paketList) {
        paketList.filter { it.kategori == kategori && it.aktif == 1 }
            .sortedByDescending { it.isDefault }
    }
    var selectedPkg by remember(kategori, categoryPackages) {
        mutableStateOf(categoryPackages.firstOrNull())
    }
    LaunchedEffect(categoryPackages) {
        if (selectedPkg == null || selectedPkg?.kategori != kategori) {
            selectedPkg = categoryPackages.firstOrNull()
        }
    }

    // PDF Upload
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var keteranganDokumen by remember { mutableStateOf("Bukti Data Diri (KTP/KK) & Dokumen Pendukung") }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) selectedPdfUri = uri
    }

    val taksiranVal = nilaiTaksiranStr.toDoubleOrNull() ?: 0.0
    val plafonVal = (taksiranVal * (activeLtvPct / 100.0)).roundToLong().toDouble()
    val pokokVal = pokokPinjamanStr.toDoubleOrNull() ?: 0.0
    val admVal = selectedPkg?.biayaAdmin ?: 0.0
    val ujrVal = selectedPkg?.ujrahPerPeriode ?: 0.0
    val danaBersihVal = max(0.0, pokokVal - admVal)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Edit Form (Owner only)
        if (editTx != null && isOwner) {
            EditGadaiPanel(
                tx = editTx,
                onSave = { newNo, tgl, jt, nm, nik, hp, alm, kt, brg, spk, kel, knd, tak, ltv, pok, adm, ujr, hr, st ->
                    vm.updateGadai(editTx.id, newNo, tgl, jt, nm, nik, hp, alm, kt, brg, spk, kel, knd, tak, ltv, pok, adm, ujr, hr, st)
                },
                onCancel = { vm.editGadaiId.value = null }
            )
        }

        // Nota / Surat Bukti Akad Rahn Syariah
        if (nota != null) {
            NotaAkadPanel(
                nota = nota,
                pengaturan = pengaturan,
                dokumen = dokumenMap[nota.nikNasabah],
                onPrintA4 = {
                    val html = PrintHelper.buildNotaAkadHtml(nota, pengaturan, thermalMode = false)
                    PrintHelper.printHtml(context, "Nota_${nota.noAkad}_A4", html)
                },
                onPrintThermal = {
                    val html = PrintHelper.buildNotaAkadHtml(nota, pengaturan, thermalMode = true)
                    PrintHelper.printHtml(context, "Nota_${nota.noAkad}_80mm", html)
                },
                onPerpanjang = { vm.openPerpanjang(nota.noAkad) },
                onTebus = { vm.openTebus(nota.noAkad) },
                onProfil = { vm.openDataPelanggan(query = nota.namaNasabah) },
                onDokumen = { d -> vm.openDokumenPelanggan(nota.nikNasabah, preview = d != null) },
                onClose = { vm.cetakAkadNo.value = null }
            )
        }

        // Main Input Akad Form
        PanelCard {
            PanelHeader(
                title = "Input Akad Rahn Syariah (HP / Motor / Emas)",
                icon = Icons.Filled.AddCircleOutline
            ) {
                PortalButton(text = "Pilih Pelanggan Lama", icon = Icons.Filled.People, compact = true) {
                    vm.navigateTo(AppRoute.DATA_PELANGGAN)
                }
                PortalButton(text = "Arsip Dokumen PDF", icon = Icons.Filled.Folder, compact = true) {
                    vm.navigateTo(AppRoute.DOKUMEN_PELANGGAN)
                }
                if (isOwner) {
                    PortalButton(text = "Atur LTV & Ujrah", icon = Icons.Filled.Tune, compact = true) {
                        vm.navigateTo(AppRoute.PENGATURAN_RUMUS)
                    }
                }
                PortalButton(text = "Cek Status Kas", icon = Icons.Filled.Lock, compact = true) {
                    vm.openTutupToko()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PortalInputField("No. Akad", noAkad, { noAkad = it }, mono = true)
                PortalInputField("Tanggal Akad (YYYY-MM-DD)", tanggalAkad, { tanggalAkad = it }, mono = true)
                PortalDropdown(
                    label = "Kategori",
                    items = listOf("HP", "Motor", "Emas"),
                    selectedItem = kategori,
                    itemLabel = { it },
                    onSelect = { kategori = it }
                )
                PortalInputField("Nama Rahin", namaNasabah, { namaNasabah = it }, modifier = Modifier.testTag("gadai_nama_input"))
                PortalInputField("NIK", nikNasabah, { nikNasabah = it }, mono = true, keyboardType = KeyboardType.Number, modifier = Modifier.testTag("gadai_nik_input"))
                PortalInputField("No. HP", noHp, { noHp = it }, mono = true, keyboardType = KeyboardType.Phone)
                PortalInputField("Alamat", alamatNasabah, { alamatNasabah = it })
                PortalInputField("Kondisi Fisik", kondisiBarang, { kondisiBarang = it }, placeholder = "Contoh: Mulus normal, layar utuh")

                // Category-specific box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(SurfaceSubtle)
                        .border(1.dp, BorderCard, RoundedCornerShape(7.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (kategori) {
                        "HP" -> {
                            PortalInputField("Merk / Tipe HP", hpMerk, { hpMerk = it })
                            PortalInputField("Kapasitas / RAM", hpRam, { hpRam = it })
                            PortalInputField("IMEI / SN", hpImei, { hpImei = it }, mono = true)
                            Text("Kelengkapan", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CheckItem("Unit", hpUnit) { hpUnit = it }
                                CheckItem("Dus", hpDus) { hpDus = it }
                                CheckItem("Charger", hpCharger) { hpCharger = it }
                            }
                        }
                        "Motor" -> {
                            PortalInputField("Merk / Tipe Motor", motorMerk, { motorMerk = it })
                            PortalInputField("Tahun", motorTahun, { motorTahun = it }, mono = true, keyboardType = KeyboardType.Number)
                            PortalInputField("Warna", motorWarna, { motorWarna = it })
                            PortalInputField("No. Polisi", motorNopol, { motorNopol = it }, mono = true)
                            PortalInputField("No. Rangka", motorRangka, { motorRangka = it }, mono = true)
                            PortalInputField("No. Mesin", motorMesin, { motorMesin = it }, mono = true)
                            PortalDropdown(
                                label = "Titipan",
                                items = listOf("Titip Unit & Dokumen", "Titip Dokumen (BPKB)"),
                                selectedItem = motorTitipan,
                                itemLabel = { it },
                                onSelect = { motorTitipan = it }
                            )
                            Text("Dokumen", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CheckItem("BPKB", motorBpkb) { motorBpkb = it }
                                CheckItem("STNK", motorStnk) { motorStnk = it }
                            }
                        }
                        else -> {
                            PortalDropdown(
                                label = "Jenis Emas",
                                items = listOf("Perhiasan", "LM Antam"),
                                selectedItem = emasJenis,
                                itemLabel = { it },
                                onSelect = { emasJenis = it }
                            )
                            PortalInputField("Kadar Karat", emasKarat, { emasKarat = it })
                            PortalInputField("Berat Bersih (gram)", emasBerat, { emasBerat = it }, mono = true, keyboardType = KeyboardType.Decimal)
                            CheckItem("Ada Surat/Sertifikat", emasSurat) { emasSurat = it }
                        }
                    }
                }

                PortalInputField(
                    label = "Nilai Taksiran (Rp)",
                    value = nilaiTaksiranStr,
                    onValueChange = {
                        nilaiTaksiranStr = it
                        val t = it.toDoubleOrNull() ?: 0.0
                        if (t > 0) {
                            val autoPlafon = (t * (activeLtvPct / 100.0)).roundToLong()
                            pokokPinjamanStr = autoPlafon.toString()
                        }
                    },
                    mono = true,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.testTag("gadai_taksiran_input")
                )
                PortalInputField(
                    label = "Plafon Maksimal (LTV $activeLtvPct%)",
                    value = FormatUtils.rupiah(plafonVal),
                    onValueChange = {},
                    readOnly = true,
                    mono = true
                )
                PortalInputField(
                    label = "Pokok Pinjaman Qardh (Rp)",
                    value = pokokPinjamanStr,
                    onValueChange = { pokokPinjamanStr = it },
                    mono = true,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.testTag("gadai_pokok_input")
                )
                PortalDropdown(
                    label = "Paket Tarif Ujrah Aktif",
                    items = categoryPackages,
                    selectedItem = selectedPkg,
                    itemLabel = { "${it.namaPaket} (${FormatUtils.rupiah(it.ujrahPerPeriode)})" },
                    onSelect = { selectedPkg = it }
                )
                PortalInputField("Admin Riil (Terkunci)", FormatUtils.rupiah(admVal), {}, readOnly = true, mono = true)
                PortalInputField("Ujrah / Periode (Terkunci)", FormatUtils.rupiah(ujrVal), {}, readOnly = true, mono = true)
                PortalInputField("Dana Bersih Diterima Rahin", FormatUtils.rupiah(danaBersihVal), {}, readOnly = true, mono = true)

                // Yellow PDF section
                val prefillExistingDok = if (nikNasabah.isNotBlank()) dokumenMap[nikNasabah.trim()] else null
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(YellowPanelBg)
                        .border(1.dp, YellowPanelBorder, RoundedCornerShape(7.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Dokumen Pelanggan (Bukti Data Diri & Lainnya — File PDF)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (prefillExistingDok != null) {
                        Text(
                            text = "Pelanggan ini sudah memiliki dokumen PDF tersimpan: ${prefillExistingDok.namaFileAsli} (${FormatUtils.formatUkuranFile(prefillExistingDok.ukuranByte)}). Jika mengunggah file baru di bawah, file lama otomatis diganti.",
                            fontSize = 11.5.sp,
                            color = SuccessText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SuccessBg)
                                .border(1.dp, SuccessBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PortalButton(
                            text = if (selectedPdfUri == null) "Pilih File PDF" else "Ganti Pilihan PDF",
                            icon = Icons.Filled.UploadFile,
                            style = BtnStyle.YELLOW,
                            compact = true
                        ) {
                            pdfPicker.launch(arrayOf("application/pdf"))
                        }
                        Text(
                            text = selectedPdfUri?.lastPathSegment ?: "Belum ada file dipilih (Opsional)",
                            fontSize = 11.5.sp,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PortalInputField(
                        label = "Keterangan Dokumen",
                        value = keteranganDokumen,
                        onValueChange = { keteranganDokumen = it }
                    )
                    Text(
                        text = "File PDF otomatis terhubung ke Data Pelanggan & Menu Dokumen, dan dapat diedit/ganti kapan saja oleh Kasir maupun Owner.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                PortalButton(
                    text = "Simpan Akad Rahn",
                    icon = Icons.Filled.AddCircleOutline,
                    style = BtnStyle.PRIMARY,
                    modifier = Modifier.testTag("simpan_gadai_button")
                ) {
                    val (namaBrg, spekBrg, kelBrg) = when (kategori) {
                        "HP" -> {
                            val nb = listOfNotNull(
                                hpMerk.trim().takeIf { it.isNotEmpty() },
                                hpRam.trim().takeIf { it.isNotEmpty() }?.let { "($it)" }
                            ).joinToString(" ").ifEmpty { "HP Jaminan" }
                            val sp = "RAM ${hpRam.trim().ifEmpty { "-" }} · IMEI: ${hpImei.trim().ifEmpty { "-" }}"
                            val kl = listOfNotNull(
                                if (hpUnit) "Unit" else null,
                                if (hpDus) "Dus" else null,
                                if (hpCharger) "Charger" else null
                            ).joinToString(", ").ifEmpty { "Unit" }
                            Triple(nb, sp, kl)
                        }
                        "Motor" -> {
                            val nb = listOfNotNull(
                                motorMerk.trim().takeIf { it.isNotEmpty() },
                                motorTahun.trim().takeIf { it.isNotEmpty() }?.let { "($it)" }
                            ).joinToString(" ").ifEmpty { "Motor Jaminan" }
                            val sp = "${motorWarna.trim()} · Plat ${motorNopol.trim().uppercase()} · Rangka ${motorRangka.trim().uppercase()} · Mesin ${motorMesin.trim().uppercase()}"
                            val docs = listOfNotNull(
                                if (motorBpkb) "BPKB" else null,
                                if (motorStnk) "STNK" else null
                            ).joinToString(" & ").ifEmpty { "Tanpa Dokumen" }
                            Triple(nb, sp, "$motorTitipan ($docs)")
                        }
                        else -> {
                            val brt = emasBerat.toDoubleOrNull() ?: 0.0
                            val nb = "$emasJenis ${brt}gr ($emasKarat)"
                            val sp = "$emasJenis · $emasKarat · $brt gram"
                            val kl = if (emasSurat) "Surat/Sertifikat Lengkap" else "Tanpa Surat"
                            Triple(nb, sp, kl)
                        }
                    }

                    vm.simpanGadai(
                        noAkad = noAkad,
                        tanggalAkad = tanggalAkad,
                        namaNasabah = namaNasabah,
                        nikNasabah = nikNasabah,
                        noHp = noHp,
                        alamatNasabah = alamatNasabah,
                        kategoriBarang = kategori,
                        namaBarang = namaBrg,
                        spesifikasiRingkas = spekBrg,
                        kelengkapanBarang = kelBrg,
                        kondisiBarang = kondisiBarang,
                        nilaiTaksiran = taksiranVal,
                        pokokPinjaman = pokokVal,
                        paketId = selectedPkg?.id ?: 0,
                        dokumenUri = selectedPdfUri,
                        keteranganDokumen = keteranganDokumen
                    ) {
                        namaNasabah = ""
                        nikNasabah = ""
                        noHp = ""
                        alamatNasabah = ""
                        kondisiBarang = ""
                        nilaiTaksiranStr = ""
                        pokokPinjamanStr = ""
                        selectedPdfUri = null
                    }
                }
            }
        }

        // Daftar Transaksi Gadai
        PanelCard {
            PanelHeader(title = "Daftar Transaksi Gadai", icon = Icons.Filled.Description)
            val recent = transaksiList.take(30)
            if (recent.isEmpty()) {
                Text("Belum ada transaksi gadai.", fontSize = 12.5.sp, color = TextMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { r ->
                        val rDok = dokumenMap[r.nikNasabah]
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
                            Text(
                                text = "${r.namaNasabah} — ${r.kategoriBarang} - ${r.namaBarang}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Pokok: ${FormatUtils.rupiah(r.pokokPinjaman)} · Ujrah: ${FormatUtils.rupiah(r.biayaUjrahPeriode)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                PortalButton(text = "Cetak", icon = Icons.Filled.Print, compact = true) {
                                    vm.openNotaAkad(r.noAkad)
                                }
                                PortalButton(
                                    text = if (rDok != null) "PDF" else "Upload PDF",
                                    icon = Icons.Filled.Folder,
                                    compact = true
                                ) {
                                    vm.openDokumenPelanggan(r.nikNasabah, preview = rDok != null)
                                }
                                if (r.statusGadai == "AKTIF" || r.statusGadai == "DIPERPANJANG") {
                                    PortalButton(text = "Perpanjang", icon = Icons.Filled.Refresh, compact = true) {
                                        vm.openPerpanjang(r.noAkad)
                                    }
                                    PortalButton(text = "Tebus", icon = Icons.Filled.CheckCircleOutline, compact = true) {
                                        vm.openTebus(r.noAkad)
                                    }
                                }
                                if (isOwner) {
                                    PortalButton(text = "Edit", icon = Icons.Filled.Edit, compact = true) {
                                        vm.openEditGadai(r.id)
                                    }
                                    PortalButton(text = "Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) {
                                        deleteTarget = r
                                    }
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
            title = "Hapus Akad ${target.noAkad}?",
            message = "Akad ${target.noAkad} beserta jurnal akuntansi & riwayat perpanjangan terkait akan dihapus permanen.",
            confirmText = "Hapus Akad",
            onConfirm = { vm.deleteGadai(target.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 12.5.sp, color = TextPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotaAkadPanel(
    nota: TransaksiGadaiEntity,
    pengaturan: PengaturanSistemEntity,
    dokumen: DokumenPelangganEntity?,
    onPrintA4: () -> Unit,
    onPrintThermal: () -> Unit,
    onPerpanjang: () -> Unit,
    onTebus: () -> Unit,
    onProfil: () -> Unit,
    onDokumen: (DokumenPelangganEntity?) -> Unit,
    onClose: () -> Unit
) {
    var thermalPreview by remember { mutableStateOf(false) }
    PanelCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TextPrimary, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(pengaturan.namaAplikasi, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, textAlign = TextAlign.Center)
                Text(pengaturan.alamatKantor, fontSize = 11.5.sp, color = TextMuted, textAlign = TextAlign.Center)
                Text(pengaturan.kontakKantor, fontSize = 11.5.sp, color = TextMuted, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (thermalPreview) "SURAT BUKTI AKAD RAHN SYARIAH (80mm)" else "SURAT BUKTI AKAD RAHN SYARIAH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderCard)
            NotaRow("No. Akad", "${nota.noAkad} (${nota.statusGadai})", mono = true)
            NotaRow("Tgl Akad / Tempo", "${nota.tanggalAkad} → ${nota.jatuhTempo} (${nota.hariPerPeriode} Hr)", mono = true)
            NotaRow("Kasir Bertugas", nota.namaKasir)
            NotaRow("Rahin (Penggadai)", "${nota.namaNasabah} · NIK: ${nota.nikNasabah} · HP: ${nota.noHp}")
            NotaRow("Alamat Rahin", nota.alamatNasabah)
            NotaRow("Marhun (Jaminan)", "${nota.kategoriBarang} — ${nota.namaBarang} (${nota.spesifikasiRingkas})")
            NotaRow("Kelengkapan & Kondisi", "${nota.kelengkapanBarang} · ${nota.kondisiBarang}")
            NotaRow("Nilai Taksiran", "${FormatUtils.rupiah(nota.nilaiTaksiran)} (LTV ${nota.ltvPersen}%)", mono = true)
            NotaRow("Pokok Pinjaman (Qardh)", FormatUtils.rupiah(nota.pokokPinjaman), mono = true)
            NotaRow("Biaya Administrasi Riil", "- ${FormatUtils.rupiah(nota.biayaAdmin)}", mono = true)
            NotaRow("Dana Bersih Diterima", FormatUtils.rupiah(nota.danaBersihDiterima), mono = true, bold = true)
            NotaRow("Biaya Penitipan (Ujrah)", "${FormatUtils.rupiah(nota.biayaUjrahPeriode)} / ${nota.hariPerPeriode} hr (${nota.ujrahPackageNama})", mono = true)
            NotaRow("Biaya Penebusan", FormatUtils.rupiah(nota.pokokPinjaman + nota.biayaUjrahPeriode), mono = true, bold = true)
            NotaRow(
                label = "Dokumen Rahin (PDF)",
                value = if (dokumen != null) "${dokumen.namaFileAsli} (${FormatUtils.formatUkuranFile(dokumen.ukuranByte)})" else "Belum diupload — Upload PDF →"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderCard)
            Text(
                text = "Pinjaman murni tanpa bunga/riba (Qardh). Barang jaminan dititipkan secara amanah (yad al-amanah) dan tidak dimanfaatkan oleh penerima gadai. Jika barang dilelang, setelah dikurangi biaya penitipan barang dan kewajiban pokok pinjaman, kelebihan uang akan dikembalikan sepenuhnya kepada pemilik barang.",
                fontSize = 11.sp,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Penggadai (Rahin),", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(26.dp))
                    Text(nota.namaNasabah, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Penerima Gadai (Murtahin),", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(26.dp))
                    Text(nota.namaKasir, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = TextPrimary)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PortalButton(text = "Cetak Nota", icon = Icons.Filled.Print, style = BtnStyle.PRIMARY, compact = true) {
                if (thermalPreview) onPrintThermal() else onPrintA4()
            }
            PortalButton(text = "Mode 80mm", compact = true, style = if (thermalPreview) BtnStyle.YELLOW else BtnStyle.DEFAULT) {
                thermalPreview = true
                onPrintThermal()
            }
            PortalButton(text = "Mode A4", compact = true, style = if (!thermalPreview) BtnStyle.YELLOW else BtnStyle.DEFAULT) {
                thermalPreview = false
                onPrintA4()
            }
            if (nota.statusGadai == "AKTIF" || nota.statusGadai == "DIPERPANJANG") {
                PortalButton(text = "Perpanjang", icon = Icons.Filled.Refresh, compact = true, onClick = onPerpanjang)
                PortalButton(text = "Tebus", icon = Icons.Filled.CheckCircleOutline, compact = true, onClick = onTebus)
            }
            PortalButton(text = "Profil Rahin", icon = Icons.Filled.People, compact = true, onClick = onProfil)
            PortalButton(text = "Dokumen PDF", icon = Icons.Filled.Folder, compact = true) { onDokumen(dokumen) }
            PortalButton(text = "Tutup", compact = true, onClick = onClose)
        }
    }
}

@Composable
private fun NotaRow(label: String, value: String, mono: Boolean = false, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = TextMuted,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = ": $value",
            fontSize = 11.5.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = TextPrimary,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun EditGadaiPanel(
    tx: TransaksiGadaiEntity,
    onSave: (
        String, String, String, String, String, String, String, String,
        String, String, String, String, Double, Double, Double, Double, Double, Int, String
    ) -> Unit,
    onCancel: () -> Unit
) {
    var noAkad by remember(tx.id) { mutableStateOf(tx.noAkad) }
    var tglAkad by remember(tx.id) { mutableStateOf(tx.tanggalAkad) }
    var jt by remember(tx.id) { mutableStateOf(tx.jatuhTempo) }
    var status by remember(tx.id) { mutableStateOf(tx.statusGadai) }
    var nama by remember(tx.id) { mutableStateOf(tx.namaNasabah) }
    var nik by remember(tx.id) { mutableStateOf(tx.nikNasabah) }
    var hp by remember(tx.id) { mutableStateOf(tx.noHp) }
    var alamat by remember(tx.id) { mutableStateOf(tx.alamatNasabah) }
    var kat by remember(tx.id) { mutableStateOf(tx.kategoriBarang) }
    var barang by remember(tx.id) { mutableStateOf(tx.namaBarang) }
    var spek by remember(tx.id) { mutableStateOf(tx.spesifikasiRingkas) }
    var kel by remember(tx.id) { mutableStateOf(tx.kelengkapanBarang) }
    var kond by remember(tx.id) { mutableStateOf(tx.kondisiBarang) }
    var tak by remember(tx.id) { mutableStateOf(tx.nilaiTaksiran.toLong().toString()) }
    var ltv by remember(tx.id) { mutableStateOf(tx.ltvPersen.toString()) }
    var pok by remember(tx.id) { mutableStateOf(tx.pokokPinjaman.toLong().toString()) }
    var adm by remember(tx.id) { mutableStateOf(tx.biayaAdmin.toLong().toString()) }
    var ujr by remember(tx.id) { mutableStateOf(tx.biayaUjrahPeriode.toLong().toString()) }
    var hari by remember(tx.id) { mutableStateOf(tx.hariPerPeriode.toString()) }

    PanelCard(isYellow = true) {
        PanelHeader(title = "Edit Data Akad & Jaminan: ${tx.noAkad}", icon = Icons.Filled.Edit) {
            PortalButton(text = "Batal Edit", compact = true, onClick = onCancel)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PortalInputField("No. Akad", noAkad, { noAkad = it }, mono = true)
            PortalInputField("Tanggal Akad", tglAkad, { tglAkad = it }, mono = true)
            PortalInputField("Jatuh Tempo", jt, { jt = it }, mono = true)
            PortalDropdown("Status Gadai", listOf("AKTIF", "DIPERPANJANG", "LUNAS", "LELANG"), status, { it }, { status = it })
            PortalInputField("Nama Rahin", nama, { nama = it })
            PortalInputField("NIK", nik, { nik = it }, mono = true)
            PortalInputField("No. HP", hp, { hp = it }, mono = true)
            PortalInputField("Alamat", alamat, { alamat = it })
            PortalDropdown("Kategori", listOf("HP", "Motor", "Emas"), kat, { it }, { kat = it })
            PortalInputField("Nama Barang", barang, { barang = it })
            PortalInputField("Spesifikasi", spek, { spek = it })
            PortalInputField("Kelengkapan", kel, { kel = it })
            PortalInputField("Kondisi Fisik", kond, { kond = it })
            PortalInputField("Nilai Taksiran (Rp)", tak, { tak = it }, mono = true, keyboardType = KeyboardType.Number)
            PortalInputField("LTV (%)", ltv, { ltv = it }, mono = true, keyboardType = KeyboardType.Decimal)
            PortalInputField("Pokok Pinjaman (Rp)", pok, { pok = it }, mono = true, keyboardType = KeyboardType.Number)
            PortalInputField("Biaya Admin (Rp)", adm, { adm = it }, mono = true, keyboardType = KeyboardType.Number)
            PortalInputField("Ujrah / Periode (Rp)", ujr, { ujr = it }, mono = true, keyboardType = KeyboardType.Number)
            PortalInputField("Hari / Periode", hari, { hari = it }, mono = true, keyboardType = KeyboardType.Number)
            PortalButton("Simpan Perubahan Akad", icon = Icons.Filled.Edit, style = BtnStyle.PRIMARY) {
                onSave(
                    noAkad, tglAkad, jt, nama, nik, hp, alamat, kat, barang, spek, kel, kond,
                    tak.toDoubleOrNull() ?: 0.0,
                    ltv.toDoubleOrNull() ?: 75.0,
                    pok.toDoubleOrNull() ?: 0.0,
                    adm.toDoubleOrNull() ?: 0.0,
                    ujr.toDoubleOrNull() ?: 0.0,
                    hari.toIntOrNull() ?: 30,
                    status
                )
            }
        }
    }
}
