package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AmanahDatabase
import com.example.data.db.DokumenPelangganEntity
import com.example.data.db.JurnalAkuntansiEntity
import com.example.data.db.PaketUjrahEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.RiwayatPerpanjanganEntity
import com.example.data.db.RumusLtvEntity
import com.example.data.db.SesiTutupTokoEntity
import com.example.data.db.TransaksiGadaiEntity
import com.example.data.db.UserEntity
import com.example.data.repository.CustomerSummary
import com.example.data.repository.GadaiRepository
import com.example.utils.FormatUtils
import com.example.utils.XlsxHelper
import com.example.utils.XlsxSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppRoute(val title: String, val ownerOnly: Boolean = false) {
    DASHBOARD("Dashboard", ownerOnly = true),
    GADAI_BARANG("Gadai Barang"),
    PERPANJANG_GADAI("Perpanjang"),
    TEBUS_BARANG("Tebus Barang"),
    DATA_BARANG("Data Barang", ownerOnly = true),
    DATA_PELANGGAN("Data Pelanggan"),
    DOKUMEN_PELANGGAN("Dokumen PDF"),
    MONITORING_TEMPO("Monitoring Tempo", ownerOnly = true),
    LELANG_BARANG("Lelang Barang", ownerOnly = true),
    LAPORAN_AKUNTANSI("Laporan Owner", ownerOnly = true),
    TUTUP_TOKO("Tutup Toko"),
    MANAJEMEN_USER("Manajemen User", ownerOnly = true),
    PENGATURAN_RUMUS("Tarif & Rumus", ownerOnly = true)
}

data class GadaiPrefill(
    val nama: String = "",
    val nik: String = "",
    val hp: String = "",
    val alamat: String = ""
)

data class LaporanFilterState(
    val tglAwal: String = "",
    val tglAkhir: String = "",
    val userId: Int = 0,
    val noAkad: String = "",
    val jenisMenu: String = ""
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AmanahDatabase.getInstance(application)
    val repo = GadaiRepository(application, db.dao())

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _currentRoute = MutableStateFlow(AppRoute.DASHBOARD)
    val currentRoute: StateFlow<AppRoute> = _currentRoute.asStateFlow()

    private val _routeBackStack = MutableStateFlow<List<AppRoute>>(emptyList())
    private val _canNavigateBack = MutableStateFlow(false)
    val canNavigateBack: StateFlow<Boolean> = _canNavigateBack.asStateFlow()

    private val _okMessage = MutableStateFlow<String?>(null)
    val okMessage: StateFlow<String?> = _okMessage.asStateFlow()

    private val _errMessage = MutableStateFlow<String?>(null)
    val errMessage: StateFlow<String?> = _errMessage.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Cross-screen navigation states
    val gadaiPrefill = MutableStateFlow(GadaiPrefill())
    val cetakAkadNo = MutableStateFlow<String?>(null)
    val editGadaiId = MutableStateFlow<Int?>(null)
    val nextAkadSuggested = MutableStateFlow("AG-${FormatUtils.todayCompact()}-001")

    val prefillPerpanjangAkad = MutableStateFlow("")
    val prefillTebusAkad = MutableStateFlow("")
    val prefillLelangAkad = MutableStateFlow("")

    val dataBarangQuery = MutableStateFlow("")
    val dataBarangStatus = MutableStateFlow("")
    val dataPelangganQuery = MutableStateFlow("")
    val dataPelangganPdfEditNik = MutableStateFlow<String?>(null)

    val dokumenSelectedNik = MutableStateFlow("")
    val dokumenPreviewNik = MutableStateFlow<String?>(null)

    val laporanFilter = MutableStateFlow(LaporanFilterState())
    val tutupTokoDate = MutableStateFlow(FormatUtils.todayIso())

    // Database StateFlows
    val pengaturan: StateFlow<PengaturanSistemEntity> = repo.pengaturan
        .combine(MutableStateFlow(Unit)) { p, _ -> p ?: PengaturanSistemEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PengaturanSistemEntity())

    val users: StateFlow<List<UserEntity>> = repo.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rumusLtv: StateFlow<List<RumusLtvEntity>> = repo.rumusLtv
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paketUjrah: StateFlow<List<PaketUjrahEntity>> = repo.paketUjrah
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transaksiGadai: StateFlow<List<TransaksiGadaiEntity>> = repo.transaksiGadai
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val riwayatPerpanjangan: StateFlow<List<RiwayatPerpanjanganEntity>> = repo.riwayatPerpanjangan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val jurnalAkuntansi: StateFlow<List<JurnalAkuntansiEntity>> = repo.jurnalAkuntansi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sesiTutupToko: StateFlow<List<SesiTutupTokoEntity>> = repo.sesiTutupToko
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dokumenMap: StateFlow<Map<String, DokumenPelangganEntity>> = repo.dokumenPelanggan
        .combine(MutableStateFlow(Unit)) { list, _ -> list.associateBy { it.nikNasabah } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val customerSummaries: StateFlow<List<CustomerSummary>> = transaksiGadai
        .combine(MutableStateFlow(Unit)) { list, _ ->
            list.groupBy { Triple(it.namaNasabah, it.nikNasabah, it.noHp) }
                .map { (key, txs) ->
                    val aktifList = txs.filter { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
                    CustomerSummary(
                        namaNasabah = key.first,
                        nikNasabah = key.second,
                        noHp = key.third,
                        alamatNasabah = txs.firstOrNull()?.alamatNasabah.orEmpty(),
                        totalAkad = txs.size,
                        akadAktif = aktifList.size,
                        pokokAktif = aktifList.sumOf { it.pokokPinjaman }
                    )
                }
                .sortedBy { it.namaNasabah.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
            refreshNextAkad()
        }
    }

    fun refreshNextAkad() {
        viewModelScope.launch {
            nextAkadSuggested.value = repo.generateNoAkad()
        }
    }

    fun clearBanners() {
        _okMessage.value = null
        _errMessage.value = null
    }

    fun setBanner(ok: Boolean, msg: String) {
        if (ok) {
            _okMessage.value = msg
            _errMessage.value = null
        } else {
            _errMessage.value = msg
            _okMessage.value = null
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginError.value = null
            val res = repo.login(username, password)
            if (res.ok && res.data != null) {
                _currentUser.value = res.data
                val startRoute = if (res.data.role == "Owner") AppRoute.DASHBOARD else AppRoute.GADAI_BARANG
                _currentRoute.value = startRoute
                _routeBackStack.value = emptyList()
                _canNavigateBack.value = false
                clearBanners()
                refreshNextAkad()
            } else {
                _loginError.value = res.message
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _routeBackStack.value = emptyList()
        _canNavigateBack.value = false
        clearBanners()
        _loginError.value = null
    }

    fun navigateTo(route: AppRoute, clearMessages: Boolean = true) {
        val u = _currentUser.value ?: return
        val target = if (route.ownerOnly && u.role != "Owner") AppRoute.GADAI_BARANG else route
        if (clearMessages) clearBanners()
        if (target != _currentRoute.value) {
            val updatedStack = _routeBackStack.value + _currentRoute.value
            _routeBackStack.value = updatedStack
            _canNavigateBack.value = updatedStack.isNotEmpty()
            _currentRoute.value = target
        }
        if (target == AppRoute.GADAI_BARANG) refreshNextAkad()
    }

    fun navigateBack(): Boolean {
        val stack = _routeBackStack.value
        if (stack.isEmpty()) return false
        val prev = stack.last()
        val nextStack = stack.dropLast(1)
        _routeBackStack.value = nextStack
        _canNavigateBack.value = nextStack.isNotEmpty()
        _currentRoute.value = prev
        clearBanners()
        return true
    }

    fun openNotaAkad(noAkad: String) {
        cetakAkadNo.value = noAkad
        editGadaiId.value = null
        navigateTo(AppRoute.GADAI_BARANG)
    }

    fun openEditGadai(id: Int) {
        editGadaiId.value = id
        cetakAkadNo.value = null
        navigateTo(AppRoute.GADAI_BARANG)
    }

    fun openGadaiWithCustomer(nama: String, nik: String, hp: String, alamat: String) {
        gadaiPrefill.value = GadaiPrefill(nama, nik, hp, alamat)
        cetakAkadNo.value = null
        editGadaiId.value = null
        navigateTo(AppRoute.GADAI_BARANG)
    }

    fun openPerpanjang(noAkad: String) {
        prefillPerpanjangAkad.value = noAkad
        navigateTo(AppRoute.PERPANJANG_GADAI)
    }

    fun openTebus(noAkad: String) {
        prefillTebusAkad.value = noAkad
        navigateTo(AppRoute.TEBUS_BARANG)
    }

    fun openLelang(noAkad: String) {
        prefillLelangAkad.value = noAkad
        navigateTo(AppRoute.LELANG_BARANG)
    }

    fun openDataBarang(query: String = "", status: String = "") {
        dataBarangQuery.value = query
        dataBarangStatus.value = status
        navigateTo(AppRoute.DATA_BARANG)
    }

    fun openDataPelanggan(query: String = "", pdfEditNik: String? = null) {
        dataPelangganQuery.value = query
        dataPelangganPdfEditNik.value = pdfEditNik
        navigateTo(AppRoute.DATA_PELANGGAN)
    }

    fun openDokumenPelanggan(nik: String = "", preview: Boolean = false) {
        dokumenSelectedNik.value = nik
        dokumenPreviewNik.value = if (preview && nik.isNotBlank()) nik else null
        navigateTo(AppRoute.DOKUMEN_PELANGGAN)
    }

    fun openLaporan(
        tglAwal: String = "",
        tglAkhir: String = "",
        userId: Int = 0,
        noAkad: String = "",
        jenisMenu: String = ""
    ) {
        laporanFilter.value = LaporanFilterState(tglAwal, tglAkhir, userId, noAkad, jenisMenu)
        navigateTo(AppRoute.LAPORAN_AKUNTANSI)
    }

    fun openTutupToko(tanggal: String = FormatUtils.todayIso()) {
        tutupTokoDate.value = tanggal
        navigateTo(AppRoute.TUTUP_TOKO)
    }

    // Actions
    fun simpanGadai(
        noAkad: String,
        tanggalAkad: String,
        namaNasabah: String,
        nikNasabah: String,
        noHp: String,
        alamatNasabah: String,
        kategoriBarang: String,
        namaBarang: String,
        spesifikasiRingkas: String,
        kelengkapanBarang: String,
        kondisiBarang: String,
        nilaiTaksiran: Double,
        pokokPinjaman: Double,
        paketId: Int,
        dokumenUri: Uri?,
        keteranganDokumen: String,
        onSaved: () -> Unit = {}
    ) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.simpanGadaiBaru(
                noAkad, tanggalAkad, namaNasabah, nikNasabah, noHp, alamatNasabah,
                kategoriBarang, namaBarang, spesifikasiRingkas, kelengkapanBarang,
                kondisiBarang, nilaiTaksiran, pokokPinjaman, paketId, dokumenUri, keteranganDokumen, u
            )
            setBanner(res.ok, res.message)
            if (res.ok && res.data != null) {
                cetakAkadNo.value = res.data.noAkad
                gadaiPrefill.value = GadaiPrefill()
                refreshNextAkad()
                onSaved()
            }
        }
    }

    fun updateGadai(
        id: Int, newNoAkad: String, tglAkad: String, jatuhTempo: String,
        nama: String, nik: String, hp: String, alamat: String,
        kat: String, namaBarang: String, spek: String, kelengkapan: String, kondisi: String,
        taksiran: Double, ltvPersen: Double, pokok: Double, admin: Double, ujrah: Double,
        hari: Int, statusGadai: String
    ) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.updateGadai(
                id, newNoAkad, tglAkad, jatuhTempo, nama, nik, hp, alamat,
                kat, namaBarang, spek, kelengkapan, kondisi, taksiran, ltvPersen,
                pokok, admin, ujrah, hari, statusGadai, u
            )
            setBanner(res.ok, res.message)
            if (res.ok) editGadaiId.value = null
        }
    }

    fun deleteGadai(id: Int) {
        viewModelScope.launch {
            val res = repo.deleteGadai(id)
            setBanner(res.ok, res.message)
        }
    }

    fun simpanPerpanjang(noAkad: String, tanggal: String, periode: Int) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.simpanPerpanjang(noAkad, tanggal, periode, u)
            setBanner(res.ok, res.message)
            if (res.ok) prefillPerpanjangAkad.value = ""
        }
    }

    fun updatePerpanjang(id: Int, tglBaru: String, jtLama: String, jtBaru: String, periode: Int, ujrah: Double) {
        viewModelScope.launch {
            val res = repo.updatePerpanjang(id, tglBaru, jtLama, jtBaru, periode, ujrah)
            setBanner(res.ok, res.message)
        }
    }

    fun deletePerpanjang(id: Int) {
        viewModelScope.launch {
            val res = repo.deletePerpanjang(id)
            setBanner(res.ok, res.message)
        }
    }

    fun simpanTebus(noAkad: String, tanggal: String, periodeTerutang: Int) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.simpanTebus(noAkad, tanggal, periodeTerutang, u)
            setBanner(res.ok, res.message)
            if (res.ok) prefillTebusAkad.value = ""
        }
    }

    fun updateTebus(id: Int, tanggal: String, ujrahTebus: Double) {
        viewModelScope.launch {
            val res = repo.updateTebus(id, tanggal, ujrahTebus)
            setBanner(res.ok, res.message)
        }
    }

    fun cancelTebus(id: Int) {
        viewModelScope.launch {
            val res = repo.cancelTebus(id)
            setBanner(res.ok, res.message)
        }
    }

    fun simpanLelang(noAkad: String, tanggal: String, hargaLelang: Double) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.simpanLelang(noAkad, tanggal, hargaLelang, u)
            setBanner(res.ok, res.message)
            if (res.ok) prefillLelangAkad.value = ""
        }
    }

    fun updateLelang(id: Int, tanggal: String, hargaLelang: Double) {
        viewModelScope.launch {
            val res = repo.updateLelang(id, tanggal, hargaLelang)
            setBanner(res.ok, res.message)
        }
    }

    fun cancelLelang(id: Int) {
        viewModelScope.launch {
            val res = repo.cancelLelang(id)
            setBanner(res.ok, res.message)
        }
    }

    fun kunciTutupToko(
        tanggal: String, gadaiBaru: Int, perpanjangan: Int, tebusan: Int,
        saldoAwal: Double, masuk: Double, keluar: Double, saldoSistem: Double,
        uangFisik: Double, catatan: String
    ) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.kunciTutupToko(
                tanggal, gadaiBaru, perpanjangan, tebusan, saldoAwal,
                masuk, keluar, saldoSistem, uangFisik, catatan, u
            )
            setBanner(res.ok, res.message)
        }
    }

    fun updateSesiTutupToko(id: Int, uangFisik: Double, catatan: String) {
        viewModelScope.launch {
            val res = repo.updateSesiTutupToko(id, uangFisik, catatan)
            setBanner(res.ok, res.message)
        }
    }

    fun bukaKunciSesi(id: Int) {
        viewModelScope.launch {
            val res = repo.bukaKunciSesi(id)
            setBanner(res.ok, res.message)
        }
    }

    fun uploadDokumenPdf(nik: String, namaNasabah: String, uri: Uri, keterangan: String, showPreviewAfter: Boolean = false) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.simpanDokumenPdf(nik, namaNasabah, uri, keterangan, u)
            setBanner(res.ok, res.message)
            if (res.ok && showPreviewAfter) {
                dokumenSelectedNik.value = nik
                dokumenPreviewNik.value = nik
            }
            if (res.ok) dataPelangganPdfEditNik.value = null
        }
    }

    fun deleteDokumenPdf(nik: String) {
        viewModelScope.launch {
            val res = repo.hapusDokumenPdf(nik)
            setBanner(res.ok, res.message)
            if (dokumenPreviewNik.value == nik) dokumenPreviewNik.value = null
        }
    }

    fun updatePelanggan(
        oldNama: String, oldNik: String, newNama: String, newNik: String,
        newHp: String, newAlamat: String, pdfUri: Uri?, ketDokumen: String
    ) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.updatePelanggan(oldNama, oldNik, newNama, newNik, newHp, newAlamat, pdfUri, ketDokumen, u)
            setBanner(res.ok, res.message)
        }
    }

    fun deletePelanggan(nama: String, nik: String) {
        viewModelScope.launch {
            val res = repo.deletePelanggan(nama, nik)
            setBanner(res.ok, res.message)
        }
    }

    fun addJurnalManual(
        tanggal: String, noAkad: String, jenisMenu: String, arusKas: String,
        akunDebit: String, akunKredit: String, pokok: Double, admin: Double,
        ujrah: Double, totalKas: Double, keterangan: String, targetUserId: Int
    ) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.addJurnalManual(
                tanggal, noAkad, jenisMenu, arusKas, akunDebit, akunKredit,
                pokok, admin, ujrah, totalKas, keterangan, targetUserId, u
            )
            setBanner(res.ok, res.message)
        }
    }

    fun updateJurnal(item: JurnalAkuntansiEntity) {
        viewModelScope.launch {
            val res = repo.updateJurnal(item)
            setBanner(res.ok, res.message)
        }
    }

    fun deleteJurnal(id: Int) {
        viewModelScope.launch {
            val res = repo.deleteJurnal(id)
            setBanner(res.ok, res.message)
        }
    }

    fun saveIdentitasDanModal(namaAplikasi: String, alamat: String, kontak: String, modalAwal: Double) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val res = repo.saveIdentitasDanModal(namaAplikasi, alamat, kontak, modalAwal, u)
            setBanner(res.ok, res.message)
        }
    }

    fun setDefaultLtv(id: Int, kategori: String) {
        viewModelScope.launch {
            val res = repo.setDefaultLtv(id, kategori)
            setBanner(res.ok, res.message)
        }
    }

    fun addLtv(kategori: String, persentase: Double, label: String, deskripsi: String) {
        viewModelScope.launch {
            val res = repo.addLtv(kategori, persentase, label, deskripsi)
            setBanner(res.ok, res.message)
        }
    }

    fun updateLtv(item: RumusLtvEntity) {
        viewModelScope.launch {
            val res = repo.updateLtv(item)
            setBanner(res.ok, res.message)
        }
    }

    fun deleteLtv(id: Int) {
        viewModelScope.launch {
            val res = repo.deleteLtv(id)
            setBanner(res.ok, res.message)
        }
    }

    fun addPaketUjrah(kode: String, nama: String, kat: String, sub: String, ujrah: Double, admin: Double, hari: Int, desk: String) {
        viewModelScope.launch {
            val res = repo.addPaketUjrah(kode, nama, kat, sub, ujrah, admin, hari, desk)
            setBanner(res.ok, res.message)
        }
    }

    fun updatePaketUjrah(item: PaketUjrahEntity) {
        viewModelScope.launch {
            val res = repo.updatePaketUjrah(item)
            setBanner(res.ok, res.message)
        }
    }

    fun togglePaketUjrah(item: PaketUjrahEntity) {
        viewModelScope.launch {
            val res = repo.togglePaketUjrah(item)
            setBanner(res.ok, res.message)
        }
    }

    fun deletePaketUjrah(id: Int) {
        viewModelScope.launch {
            val res = repo.deletePaketUjrah(id)
            setBanner(res.ok, res.message)
        }
    }

    fun addUser(username: String, namaLengkap: String, password: String, role: String, noHp: String) {
        viewModelScope.launch {
            val res = repo.addUser(username, namaLengkap, password, role, noHp)
            setBanner(res.ok, res.message)
        }
    }

    fun updateUser(uid: Int, username: String, namaLengkap: String, newPass: String, role: String, noHp: String) {
        viewModelScope.launch {
            val res = repo.updateUser(uid, username, namaLengkap, newPass, role, noHp)
            setBanner(res.ok, res.message)
            if (res.ok && res.data != null && _currentUser.value?.id == uid) {
                _currentUser.value = res.data
            }
        }
    }

    fun toggleUser(uid: Int) {
        val currentId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            val res = repo.toggleUser(uid, currentId)
            setBanner(res.ok, res.message)
        }
    }

    fun deleteUser(uid: Int) {
        val currentId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            val res = repo.deleteUser(uid, currentId)
            setBanner(res.ok, res.message)
        }
    }

    fun exportXlsxToUri(uri: Uri, sheets: List<XlsxSheet>, label: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val bytes = XlsxHelper.buildXlsxBytes(sheets)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (ok) setBanner(true, "File Excel (.xlsx) $label berhasil disimpan.")
            else setBanner(false, "Gagal menyimpan file Excel (.xlsx).")
        }
    }

    fun importXlsxFromUri(uri: Uri) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    XlsxHelper.parseFirstSheetRows(input)
                } ?: Result.failure(IllegalArgumentException("Tidak dapat membuka file .xlsx."))
            }
            if (parsed.isFailure) {
                setBanner(false, parsed.exceptionOrNull()?.message ?: "Gagal membaca file .xlsx.")
            } else {
                val res = repo.importBarangRows(parsed.getOrThrow(), u)
                setBanner(res.ok, res.message)
            }
        }
    }
}
