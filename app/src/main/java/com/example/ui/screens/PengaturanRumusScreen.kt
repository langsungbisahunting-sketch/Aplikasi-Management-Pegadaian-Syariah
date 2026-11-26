package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.db.PaketUjrahEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.RumusLtvEntity
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
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.utils.FormatUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PengaturanRumusScreen(
    vm: AppViewModel,
    pengaturan: PengaturanSistemEntity,
    ltvList: List<RumusLtvEntity>,
    paketList: List<PaketUjrahEntity>
) {
    var namaApp by remember(pengaturan.namaAplikasi) { mutableStateOf(pengaturan.namaAplikasi) }
    var alamat by remember(pengaturan.alamatKantor) { mutableStateOf(pengaturan.alamatKantor) }
    var kontak by remember(pengaturan.kontakKantor) { mutableStateOf(pengaturan.kontakKantor) }
    var modal by remember(pengaturan.modalAwal) { mutableStateOf(pengaturan.modalAwal.toLong().toString()) }

    var editingLtv by remember { mutableStateOf<RumusLtvEntity?>(null) }
    var deleteLtvTarget by remember { mutableStateOf<RumusLtvEntity?>(null) }
    var newLtvKat by remember { mutableStateOf("HP") }
    var newLtvPct by remember { mutableStateOf("") }
    var newLtvLabel by remember { mutableStateOf("") }
    var newLtvDesk by remember { mutableStateOf("") }

    var editingPkg by remember { mutableStateOf<PaketUjrahEntity?>(null) }
    var deletePkgTarget by remember { mutableStateOf<PaketUjrahEntity?>(null) }
    var newPkgKode by remember { mutableStateOf("") }
    var newPkgNama by remember { mutableStateOf("") }
    var newPkgKat by remember { mutableStateOf("HP") }
    var newPkgSub by remember { mutableStateOf("") }
    var newPkgUjr by remember { mutableStateOf("") }
    var newPkgAdm by remember { mutableStateOf("") }
    var newPkgHari by remember { mutableStateOf("30") }
    var newPkgDesk by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Identitas Kantor & Modal Awal
        PanelCard {
            PanelHeader(title = "Identitas Kantor & Modal Awal", icon = Icons.Filled.Tune) {
                PortalButton("Lihat Jurnal Modal", icon = Icons.Filled.Description, compact = true) {
                    vm.openLaporan(noAkad = "MODAL-AWAL")
                }
                PortalButton("Cek Kas Tutup Toko", icon = Icons.Filled.Lock, compact = true) {
                    vm.openTutupToko()
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalInputField("Nama Aplikasi / Toko", namaApp, { namaApp = it })
                PortalInputField("Alamat Kantor", alamat, { alamat = it })
                PortalInputField("Kontak / WhatsApp", kontak, { kontak = it })
                PortalInputField("Modal Awal Bersama (Rp)", modal, { modal = it }, mono = true, keyboardType = KeyboardType.Number)
                PortalButton("Simpan Identitas & Modal", style = BtnStyle.PRIMARY) {
                    vm.saveIdentitasDanModal(namaApp, alamat, kontak, modal.toDoubleOrNull() ?: pengaturan.modalAwal)
                }
            }
        }

        // Skema 1 — Checklist Rasio Plafon Taksiran (LTV)
        PanelCard {
            PanelHeader(title = "Skema 1 — Checklist Rasio Plafon Taksiran (LTV)", icon = Icons.Filled.Tune) {
                PortalButton("Terapkan di Gadai Baru", icon = Icons.Filled.AddCircleOutline, compact = true) {
                    vm.navigateTo(AppRoute.GADAI_BARANG)
                }
            }

            editingLtv?.let { l ->
                var ePct by remember(l.id) { mutableStateOf(l.persentase.toString()) }
                var eLbl by remember(l.id) { mutableStateOf(l.label) }
                var eDes by remember(l.id) { mutableStateOf(l.deskripsi) }
                var eAktif by remember(l.id) { mutableStateOf(l.aktif == 1) }

                PanelCard(isYellow = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PortalInputField("Edit Persentase LTV (%)", ePct, { ePct = it }, mono = true, keyboardType = KeyboardType.Decimal)
                        PortalInputField("Label", eLbl, { eLbl = it })
                        PortalInputField("Deskripsi", eDes, { eDes = it })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = eAktif, onCheckedChange = { eAktif = it })
                            Text("Aktif", fontSize = 12.5.sp, color = TextPrimary)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PortalButton("Simpan LTV", style = BtnStyle.PRIMARY, compact = true) {
                                vm.updateLtv(
                                    l.copy(
                                        persentase = ePct.toDoubleOrNull() ?: l.persentase,
                                        label = eLbl,
                                        deskripsi = eDes,
                                        aktif = if (eAktif) 1 else 0
                                    )
                                )
                                editingLtv = null
                            }
                            PortalButton("Batal", compact = true) { editingLtv = null }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                ltvList.forEach { l ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceSubtle)
                            .border(1.dp, BorderCard, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${l.kategori} · ${l.persentase}%", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                text = if (l.isDefault == 1) "Default Aktif" else if (l.aktif == 1) "Aktif" else "Nonaktif",
                                fontWeight = if (l.isDefault == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.5.sp,
                                color = TextPrimary
                            )
                        }
                        Text(l.label, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = TextPrimary)
                        if (l.deskripsi.isNotBlank()) Text(l.deskripsi, fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            PortalButton("Jadikan Default", compact = true) { vm.setDefaultLtv(l.id, l.kategori) }
                            PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) { editingLtv = l }
                            if (l.isDefault != 1) {
                                PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) {
                                    deleteLtvTarget = l
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalDropdown("Kategori", listOf("HP", "Motor", "Emas"), newLtvKat, { it }, { newLtvKat = it })
                PortalInputField("Persentase LTV (%)", newLtvPct, { newLtvPct = it }, mono = true, keyboardType = KeyboardType.Decimal, placeholder = "78")
                PortalInputField("Label Skema", newLtvLabel, { newLtvLabel = it }, placeholder = "Plafon Khusus 78%")
                PortalInputField("Deskripsi", newLtvDesk, { newLtvDesk = it }, placeholder = "Keterangan singkat")
                PortalButton("Tambah LTV Custom", icon = Icons.Filled.AddCircleOutline, style = BtnStyle.PRIMARY) {
                    vm.addLtv(newLtvKat, newLtvPct.toDoubleOrNull() ?: 75.0, newLtvLabel, newLtvDesk)
                    newLtvPct = ""; newLtvLabel = ""; newLtvDesk = ""
                }
            }
        }

        // Skema 2 — Checklist Paket Tarif Ujrah & Admin Riil
        PanelCard(isYellow = true) {
            PanelHeader(title = "Skema 2 — Checklist Paket Tarif Ujrah & Admin Riil", icon = Icons.Filled.Tune) {
                PortalButton("Uji di Gadai Baru", icon = Icons.Filled.AddCircleOutline, compact = true) {
                    vm.navigateTo(AppRoute.GADAI_BARANG)
                }
            }

            editingPkg?.let { p ->
                var eKode by remember(p.id) { mutableStateOf(p.kode) }
                var eNama by remember(p.id) { mutableStateOf(p.namaPaket) }
                var eKat by remember(p.id) { mutableStateOf(p.kategori) }
                var eSub by remember(p.id) { mutableStateOf(p.subTipe) }
                var eUjr by remember(p.id) { mutableStateOf(p.ujrahPerPeriode.toLong().toString()) }
                var eAdm by remember(p.id) { mutableStateOf(p.biayaAdmin.toLong().toString()) }
                var eHari by remember(p.id) { mutableStateOf(p.hariPerPeriode.toString()) }
                var eDesk by remember(p.id) { mutableStateOf(p.deskripsiSyariah) }
                var eAktif by remember(p.id) { mutableStateOf(p.aktif == 1) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(SurfaceWhite)
                        .border(1.dp, BorderCard, RoundedCornerShape(7.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PortalInputField("Kode", eKode, { eKode = it }, mono = true)
                    PortalInputField("Nama Paket", eNama, { eNama = it })
                    PortalDropdown("Kategori", listOf("HP", "Motor", "Emas"), eKat, { it }, { eKat = it })
                    PortalInputField("Sub Tipe", eSub, { eSub = it })
                    PortalInputField("Ujrah Tetap (Rp)", eUjr, { eUjr = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Admin Tetap (Rp)", eAdm, { eAdm = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Hari / Periode", eHari, { eHari = it }, mono = true, keyboardType = KeyboardType.Number)
                    PortalInputField("Alasan Syariah", eDesk, { eDesk = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = eAktif, onCheckedChange = { eAktif = it })
                        Text("Aktif", fontSize = 12.5.sp, color = TextPrimary)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PortalButton("Simpan Paket", style = BtnStyle.PRIMARY, compact = true) {
                            vm.updatePaketUjrah(
                                p.copy(
                                    kode = eKode, namaPaket = eNama, kategori = eKat, subTipe = eSub,
                                    ujrahPerPeriode = eUjr.toDoubleOrNull() ?: 0.0,
                                    biayaAdmin = eAdm.toDoubleOrNull() ?: 0.0,
                                    hariPerPeriode = eHari.toIntOrNull() ?: 30,
                                    deskripsiSyariah = eDesk,
                                    aktif = if (eAktif) 1 else 0
                                )
                            )
                            editingPkg = null
                        }
                        PortalButton("Batal", compact = true) { editingPkg = null }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                paketList.forEach { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceWhite)
                            .border(1.dp, BorderCard, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.namaPaket, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(if (p.aktif == 1) "Aktif" else "Nonaktif", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Text("${p.kode} · ${p.kategori}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = "Ujrah: ${FormatUtils.rupiah(p.ujrahPerPeriode)} / ${p.hariPerPeriode} hr · Admin: ${FormatUtils.rupiah(p.biayaAdmin)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = TextPrimary
                        )
                        Text(p.deskripsiSyariah, fontSize = 11.5.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            PortalButton(if (p.aktif == 1) "Nonaktifkan" else "Aktifkan", compact = true) { vm.togglePaketUjrah(p) }
                            PortalButton("Edit", icon = Icons.Filled.Edit, compact = true) { editingPkg = p }
                            if (p.isDefault != 1) {
                                PortalButton("Hapus", icon = Icons.Filled.Delete, style = BtnStyle.DANGER, compact = true) {
                                    deletePkgTarget = p
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortalInputField("Kode", newPkgKode, { newPkgKode = it }, mono = true, placeholder = "KLS-F")
                PortalInputField("Nama Paket Custom", newPkgNama, { newPkgNama = it })
                PortalDropdown("Kategori", listOf("HP", "Motor", "Emas"), newPkgKat, { it }, { newPkgKat = it })
                PortalInputField("Sub Tipe", newPkgSub, { newPkgSub = it })
                PortalInputField("Ujrah Tetap (Rp)", newPkgUjr, { newPkgUjr = it }, mono = true, keyboardType = KeyboardType.Number)
                PortalInputField("Admin Tetap (Rp)", newPkgAdm, { newPkgAdm = it }, mono = true, keyboardType = KeyboardType.Number)
                PortalInputField("Hari / Periode", newPkgHari, { newPkgHari = it }, mono = true, keyboardType = KeyboardType.Number)
                PortalInputField("Alasan Syariah", newPkgDesk, { newPkgDesk = it })
                PortalButton("+ Tambah Paket Custom", style = BtnStyle.PRIMARY) {
                    vm.addPaketUjrah(
                        newPkgKode, newPkgNama, newPkgKat, newPkgSub,
                        newPkgUjr.toDoubleOrNull() ?: 0.0,
                        newPkgAdm.toDoubleOrNull() ?: 0.0,
                        newPkgHari.toIntOrNull() ?: 30,
                        newPkgDesk
                    )
                    newPkgKode = ""; newPkgNama = ""; newPkgSub = ""; newPkgUjr = ""; newPkgAdm = ""; newPkgDesk = ""
                }
            }
        }
    }

    deleteLtvTarget?.let { target ->
        ConfirmActionDialog(
            title = "Hapus Skema LTV?",
            message = "Hapus skema LTV '${target.label}'?",
            confirmText = "Hapus",
            onConfirm = { vm.deleteLtv(target.id) },
            onDismiss = { deleteLtvTarget = null }
        )
    }

    deletePkgTarget?.let { target ->
        ConfirmActionDialog(
            title = "Hapus Paket Ujrah?",
            message = "Hapus paket tarif '${target.namaPaket}'?",
            confirmText = "Hapus",
            onConfirm = { vm.deletePaketUjrah(target.id) },
            onDismiss = { deletePkgTarget = null }
        )
    }
}
