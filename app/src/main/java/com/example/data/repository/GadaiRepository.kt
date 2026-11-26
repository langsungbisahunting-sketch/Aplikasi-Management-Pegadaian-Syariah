package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.AmanahDao
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
import com.example.utils.FormatUtils
import com.example.utils.PdfStorageHelper
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class CustomerSummary(
    val namaNasabah: String,
    val nikNasabah: String,
    val noHp: String,
    val alamatNasabah: String,
    val totalAkad: Int,
    val akadAktif: Int,
    val pokokAktif: Double
)

data class OperationResult<T>(
    val ok: Boolean,
    val message: String,
    val data: T? = null
)

class GadaiRepository(
    private val context: Context,
    private val dao: AmanahDao
) {
    val users: Flow<List<UserEntity>> = dao.observeUsers()
    val pengaturan: Flow<PengaturanSistemEntity?> = dao.observePengaturan()
    val rumusLtv: Flow<List<RumusLtvEntity>> = dao.observeRumusLtv()
    val paketUjrah: Flow<List<PaketUjrahEntity>> = dao.observePaketUjrah()
    val transaksiGadai: Flow<List<TransaksiGadaiEntity>> = dao.observeTransaksiGadai()
    val riwayatPerpanjangan: Flow<List<RiwayatPerpanjanganEntity>> = dao.observeRiwayatPerpanjangan()
    val jurnalAkuntansi: Flow<List<JurnalAkuntansiEntity>> = dao.observeJurnal()
    val sesiTutupToko: Flow<List<SesiTutupTokoEntity>> = dao.observeSesiTutupToko()
    val dokumenPelanggan: Flow<List<DokumenPelangganEntity>> = dao.observeDokumenPelanggan()

    suspend fun ensureSeeded() {
        AmanahDatabase.ensureSeeded(dao)
    }

    suspend fun login(username: String, password: String): OperationResult<UserEntity> {
        ensureSeeded()
        val u = dao.getUserByUsername(username.trim())
            ?: return OperationResult(false, "Username atau password salah.")
        if (!FormatUtils.verifyPassword(password, u.passwordHash)) {
            return OperationResult(false, "Username atau password salah.")
        }
        if (u.aktif != 1) {
            return OperationResult(false, "Akun Anda berstatus NONAKTIF.")
        }
        return OperationResult(true, "Login berhasil.", u)
    }

    suspend fun generateNoAkad(): String {
        val prefix = "AG-${FormatUtils.todayCompact()}-"
        val latest = dao.getLatestNoAkadByPrefix(prefix)
        val nextNum = if (latest != null) {
            val suffix = latest.noAkad.removePrefix(prefix).toIntOrNull() ?: 0
            suffix + 1
        } else 1
        return "$prefix${nextNum.toString().padStart(3, '0')}"
    }

    suspend fun isDateClosed(tanggal: String): Boolean {
        val sesi = dao.getSesiByTanggal(tanggal)
        return sesi?.status == "CLOSED"
    }

    suspend fun simpanGadaiBaru(
        noAkadInput: String,
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
        user: UserEntity
    ): OperationResult<TransaksiGadaiEntity> {
        val tgl = if (FormatUtils.validIsoDate(tanggalAkad)) tanggalAkad else FormatUtils.todayIso()
        if (isDateClosed(tgl)) {
            return OperationResult(false, "Sesi operasional tanggal $tgl sudah DITUTUP & TERKUNCI (Tutup Toko).")
        }
        val noAkad = noAkadInput.trim().ifEmpty { generateNoAkad() }
        if (dao.getTransaksiByNoAkad(noAkad) != null) {
            return OperationResult(false, "Nomor akad $noAkad sudah terdaftar. Gunakan nomor akad lain.")
        }
        val kat = if (kategoriBarang in listOf("HP", "Motor", "Emas")) kategoriBarang else "HP"
        if (namaNasabah.isBlank() || nikNasabah.isBlank() || noHp.isBlank() || alamatNasabah.isBlank() ||
            kondisiBarang.isBlank() || nilaiTaksiran <= 0 || pokokPinjaman <= 0
        ) {
            return OperationResult(false, "Semua identitas Rahin, kondisi barang, taksiran, dan pokok pinjaman wajib diisi.")
        }

        val ltv = dao.getActiveLtv(kat)
        val ltvPct = ltv?.persentase ?: 75.0
        val plafon = (nilaiTaksiran * (ltvPct / 100.0)).roundToLong().toDouble()
        if (pokokPinjaman > plafon) {
            return OperationResult(false, "Pokok pinjaman melebihi plafon LTV $ltvPct% (${FormatUtils.rupiah(plafon)}).")
        }

        val pkg = dao.getPaketById(paketId)?.takeIf { it.kategori == kat && it.aktif == 1 }
            ?: dao.getActivePackagesByKategori(kat).firstOrNull()
            ?: return OperationResult(false, "Paket tarif Ujrah aktif untuk kategori $kat belum tersedia.")

        val hari = max(1, pkg.hariPerPeriode)
        val jatuhTempo = FormatUtils.addDaysIso(tgl, hari)
        val adm = pkg.biayaAdmin
        val ujrah = pkg.ujrahPerPeriode
        val bersih = max(0.0, pokokPinjaman - adm)
        val now = FormatUtils.nowTimestamp()

        var fileSimpan: String? = null
        var fileAsli: String? = null
        var dokInfo = ""
        if (dokumenUri != null) {
            val dokRes = simpanDokumenPdf(nikNasabah.trim(), namaNasabah.trim(), dokumenUri, keteranganDokumen, user)
            if (!dokRes.ok) {
                return OperationResult(false, dokRes.message)
            }
            fileSimpan = dokRes.data?.namaFileSimpan
            fileAsli = dokRes.data?.namaFileAsli
            dokInfo = " Dokumen PDF pelanggan (${fileAsli ?: ""}) ikut tersimpan."
        } else {
            val existing = dao.getDokumenByNik(nikNasabah.trim())
            if (existing != null) {
                fileSimpan = existing.namaFileSimpan
                fileAsli = existing.namaFileAsli
            }
        }

        val entity = TransaksiGadaiEntity(
            noAkad = noAkad,
            tanggalAkad = tgl,
            jatuhTempo = jatuhTempo,
            namaNasabah = namaNasabah.trim(),
            nikNasabah = nikNasabah.trim(),
            noHp = noHp.trim(),
            alamatNasabah = alamatNasabah.trim(),
            kategoriBarang = kat,
            namaBarang = namaBarang.trim(),
            spesifikasiRingkas = spesifikasiRingkas.trim(),
            kelengkapanBarang = kelengkapanBarang.trim(),
            kondisiBarang = kondisiBarang.trim(),
            nilaiTaksiran = nilaiTaksiran,
            ltvPersen = ltvPct,
            plafonMaksimal = plafon,
            pokokPinjaman = pokokPinjaman,
            biayaAdmin = adm,
            danaBersihDiterima = bersih,
            ujrahPackageId = pkg.id,
            ujrahPackageNama = pkg.namaPaket,
            biayaUjrahPeriode = ujrah,
            hariPerPeriode = hari,
            statusGadai = "AKTIF",
            userId = user.id,
            namaKasir = user.namaLengkap,
            fileDokumen = fileSimpan,
            namaFileAsli = fileAsli,
            createdAt = now
        )
        val id = dao.insertTransaksi(entity).toInt()
        val saved = entity.copy(id = id)

        dao.insertJurnal(
            JurnalAkuntansiEntity(
                tanggal = tgl,
                noAkad = noAkad,
                jenisMenu = "GADAI_BARANG",
                arusKas = "KELUAR",
                akunDebit = "Piutang Qardh",
                akunKredit = "Kas Operasional",
                pokokNominal = pokokPinjaman,
                totalKas = pokokPinjaman,
                keterangan = "Pencairan pokok Qardh $noAkad - ${namaNasabah.trim()}",
                userId = user.id,
                namaKasir = user.namaLengkap,
                createdAt = now
            )
        )
        if (adm > 0) {
            dao.insertJurnal(
                JurnalAkuntansiEntity(
                    tanggal = tgl,
                    noAkad = noAkad,
                    jenisMenu = "GADAI_BARANG",
                    arusKas = "MASUK",
                    akunDebit = "Kas Operasional",
                    akunKredit = "Pendapatan Admin Riil",
                    pendapatanAdmin = adm,
                    totalKas = adm,
                    keterangan = "Biaya administrasi awal $noAkad",
                    userId = user.id,
                    namaKasir = user.namaLengkap,
                    createdAt = now
                )
            )
        }
        return OperationResult(true, "Akad Rahn $noAkad berhasil disimpan.$dokInfo", saved)
    }

    suspend fun updateGadai(
        id: Int,
        newNoAkad: String,
        tglAkad: String,
        jatuhTempo: String,
        nama: String,
        nik: String,
        hp: String,
        alamat: String,
        kat: String,
        namaBarang: String,
        spek: String,
        kelengkapan: String,
        kondisi: String,
        taksiran: Double,
        ltvPersen: Double,
        pokok: Double,
        admin: Double,
        ujrah: Double,
        hari: Int,
        statusGadai: String,
        user: UserEntity
    ): OperationResult<Unit> {
        val old = dao.getTransaksiById(id) ?: return OperationResult(false, "Transaksi tidak ditemukan.")
        val noFinal = newNoAkad.trim().ifEmpty { old.noAkad }
        if (noFinal != old.noAkad && dao.getTransaksiByNoAkad(noFinal) != null) {
            return OperationResult(false, "Nomor akad $noFinal sudah dipakai transaksi lain.")
        }
        if (nama.isBlank() || namaBarang.isBlank() || pokok <= 0) {
            return OperationResult(false, "Nama nasabah, nama barang, dan pokok pinjaman wajib valid.")
        }
        val tglFinal = if (FormatUtils.validIsoDate(tglAkad)) tglAkad else old.tanggalAkad
        val jtFinal = if (FormatUtils.validIsoDate(jatuhTempo)) jatuhTempo else old.jatuhTempo
        val plafon = (taksiran * (max(1.0, ltvPersen) / 100.0)).roundToLong().toDouble()
        val bersih = max(0.0, pokok - max(0.0, admin))

        dao.updateTransaksi(
            old.copy(
                noAkad = noFinal,
                tanggalAkad = tglFinal,
                jatuhTempo = jtFinal,
                namaNasabah = nama.trim(),
                nikNasabah = nik.trim(),
                noHp = hp.trim(),
                alamatNasabah = alamat.trim(),
                kategoriBarang = kat,
                namaBarang = namaBarang.trim(),
                spesifikasiRingkas = spek.trim(),
                kelengkapanBarang = kelengkapan.trim(),
                kondisiBarang = kondisi.trim(),
                nilaiTaksiran = max(0.0, taksiran),
                ltvPersen = max(1.0, ltvPersen),
                plafonMaksimal = plafon,
                pokokPinjaman = pokok,
                biayaAdmin = max(0.0, admin),
                danaBersihDiterima = bersih,
                biayaUjrahPeriode = max(0.0, ujrah),
                hariPerPeriode = max(1, hari),
                statusGadai = statusGadai
            )
        )
        if (noFinal != old.noAkad) {
            dao.renameNoAkadOnRiwayat(old.noAkad, noFinal)
            dao.renameNoAkadOnJurnal(old.noAkad, noFinal)
        }
        dao.findJurnalByAkadMenuArus(noFinal, "GADAI_BARANG", "KELUAR")?.let { j ->
            dao.updateJurnal(
                j.copy(
                    tanggal = tglFinal,
                    pokokNominal = pokok,
                    totalKas = pokok,
                    keterangan = "Pencairan pokok Qardh $noFinal - ${nama.trim()}"
                )
            )
        }
        if (nama.trim() != old.namaNasabah) {
            dao.findJurnalByAkadAndMenu(noFinal, "TEBUS_BARANG")?.let { jt ->
                dao.updateJurnal(jt.copy(keterangan = "Pelunasan tebus $noFinal - ${nama.trim()}"))
            }
        }
        val admRow = dao.findJurnalByAkadMenuArus(noFinal, "GADAI_BARANG", "MASUK")
        val admVal = max(0.0, admin)
        if (admVal > 0) {
            if (admRow != null) {
                dao.updateJurnal(
                    admRow.copy(
                        tanggal = tglFinal,
                        pendapatanAdmin = admVal,
                        totalKas = admVal,
                        keterangan = "Biaya administrasi awal $noFinal"
                    )
                )
            } else {
                dao.insertJurnal(
                    JurnalAkuntansiEntity(
                        tanggal = tglFinal,
                        noAkad = noFinal,
                        jenisMenu = "GADAI_BARANG",
                        arusKas = "MASUK",
                        akunDebit = "Kas Operasional",
                        akunKredit = "Pendapatan Admin Riil",
                        pendapatanAdmin = admVal,
                        totalKas = admVal,
                        keterangan = "Biaya administrasi awal $noFinal",
                        userId = user.id,
                        namaKasir = user.namaLengkap,
                        createdAt = FormatUtils.nowTimestamp()
                    )
                )
            }
        } else if (admRow != null) {
            dao.deleteJurnalById(admRow.id)
        }
        return OperationResult(true, "Perubahan akad $noFinal disimpan & disinkronkan ke jurnal.")
    }

    suspend fun deleteGadai(id: Int): OperationResult<Unit> {
        val row = dao.getTransaksiById(id) ?: return OperationResult(false, "Transaksi tidak ditemukan.")
        dao.deleteRiwayatByNoAkad(row.noAkad)
        dao.deleteJurnalByNoAkad(row.noAkad)
        dao.deleteTransaksiById(id)
        return OperationResult(true, "Barang & akad ${row.noAkad} beserta jurnal terkait dihapus.")
    }

    suspend fun simpanPerpanjang(
        noAkad: String,
        tanggalPerpanjang: String,
        jumlahPeriode: Int,
        user: UserEntity
    ): OperationResult<Unit> {
        val tgl = if (FormatUtils.validIsoDate(tanggalPerpanjang)) tanggalPerpanjang else FormatUtils.todayIso()
        if (isDateClosed(tgl)) {
            return OperationResult(false, "Sesi operasional tanggal $tgl sudah DITUTUP & TERKUNCI.")
        }
        val tx = dao.getTransaksiByNoAkad(noAkad)
            ?.takeIf { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
            ?: return OperationResult(false, "Akad aktif tidak ditemukan.")
        val per = max(1, jumlahPeriode)
        val ujrah = tx.biayaUjrahPeriode * per
        val hari = max(1, tx.hariPerPeriode) * per
        val jtLama = tx.jatuhTempo
        val jtBaru = FormatUtils.addDaysIso(jtLama, hari)
        val now = FormatUtils.nowTimestamp()

        dao.updateTransaksi(
            tx.copy(
                jatuhTempo = jtBaru,
                totalUjrahDibayar = tx.totalUjrahDibayar + ujrah,
                jumlahPerpanjangan = tx.jumlahPerpanjangan + 1,
                tanggalPerpanjangTerakhir = tgl,
                statusGadai = "DIPERPANJANG"
            )
        )
        dao.insertRiwayat(
            RiwayatPerpanjanganEntity(
                noAkad = noAkad,
                tanggalPerpanjang = tgl,
                jatuhTempoLama = jtLama,
                jatuhTempoBaru = jtBaru,
                jumlahPeriode = per,
                biayaUjrahDibayar = ujrah,
                userId = user.id,
                namaKasir = user.namaLengkap,
                createdAt = now
            )
        )
        dao.insertJurnal(
            JurnalAkuntansiEntity(
                tanggal = tgl,
                noAkad = noAkad,
                jenisMenu = "PERPANJANG_GADAI",
                arusKas = "MASUK",
                akunDebit = "Kas Operasional",
                akunKredit = "Pendapatan Jasa Ujrah",
                pendapatanUjrah = ujrah,
                totalKas = ujrah,
                keterangan = "Perpanjangan Ujrah $noAkad ($jtLama -> $jtBaru)",
                userId = user.id,
                namaKasir = user.namaLengkap,
                createdAt = now
            )
        )
        return OperationResult(true, "Perpanjangan akad $noAkad berhasil. Tempo baru: $jtBaru.")
    }

    suspend fun updatePerpanjang(
        id: Int,
        tglBaru: String,
        jtLama: String,
        jtBaru: String,
        periode: Int,
        ujrahBaru: Double
    ): OperationResult<Unit> {
        val rp = dao.getRiwayatById(id) ?: return OperationResult(false, "Data perpanjangan tidak ditemukan.")
        val tglN = if (FormatUtils.validIsoDate(tglBaru)) tglBaru else rp.tanggalPerpanjang
        val jtLN = if (FormatUtils.validIsoDate(jtLama)) jtLama else rp.jatuhTempoLama
        val jtBN = if (FormatUtils.validIsoDate(jtBaru)) jtBaru else rp.jatuhTempoBaru
        val perN = max(1, periode)
        val ujrN = max(0.0, ujrahBaru)
        dao.updateRiwayat(
            rp.copy(
                tanggalPerpanjang = tglN,
                jatuhTempoLama = jtLN,
                jatuhTempoBaru = jtBN,
                jumlahPeriode = perN,
                biayaUjrahDibayar = ujrN
            )
        )
        dao.findJurnalByAkadMenuTanggal(rp.noAkad, "PERPANJANG_GADAI", rp.tanggalPerpanjang)?.let { j ->
            dao.updateJurnal(
                j.copy(
                    tanggal = tglN,
                    pendapatanUjrah = ujrN,
                    totalKas = ujrN,
                    keterangan = "Perpanjangan Ujrah ${rp.noAkad} ($jtLN -> $jtBN)"
                )
            )
        }
        resyncTransaksiPerpanjangan(rp.noAkad)
        return OperationResult(true, "Perubahan perpanjangan akad ${rp.noAkad} berhasil disimpan & disinkronkan.")
    }

    suspend fun deletePerpanjang(id: Int): OperationResult<Unit> {
        val rp = dao.getRiwayatById(id) ?: return OperationResult(false, "Data tidak ditemukan.")
        dao.deleteRiwayatById(id)
        dao.findJurnalByAkadMenuTanggal(rp.noAkad, "PERPANJANG_GADAI", rp.tanggalPerpanjang)?.let {
            dao.deleteJurnalById(it.id)
        }
        resyncTransaksiPerpanjangan(rp.noAkad)
        return OperationResult(true, "Riwayat perpanjangan berhasil dihapus & tempo akad disinkronkan ulang.")
    }

    private suspend fun resyncTransaksiPerpanjangan(noAkad: String) {
        val tx = dao.getTransaksiByNoAkad(noAkad) ?: return
        val list = dao.getRiwayatByNoAkad(noAkad)
        val cnt = list.size
        val sumUjrah = list.sumOf { it.biayaUjrahDibayar } + (tx.ujrahTebusDibayar ?: 0.0)
        val maxJt = list.maxOfOrNull { it.jatuhTempoBaru }
            ?: FormatUtils.addDaysIso(tx.tanggalAkad, max(1, tx.hariPerPeriode))
        val maxTgl = list.maxOfOrNull { it.tanggalPerpanjang }
        val newStatus = if (tx.statusGadai == "DIPERPANJANG" && cnt == 0) "AKTIF" else tx.statusGadai
        dao.updateTransaksi(
            tx.copy(
                jatuhTempo = maxJt,
                totalUjrahDibayar = sumUjrah,
                jumlahPerpanjangan = cnt,
                tanggalPerpanjangTerakhir = maxTgl,
                statusGadai = newStatus
            )
        )
    }

    suspend fun simpanTebus(
        noAkad: String,
        tanggalTebus: String,
        periodeTerutang: Int,
        user: UserEntity
    ): OperationResult<Unit> {
        val tgl = if (FormatUtils.validIsoDate(tanggalTebus)) tanggalTebus else FormatUtils.todayIso()
        if (isDateClosed(tgl)) {
            return OperationResult(false, "Sesi operasional tanggal $tgl sudah DITUTUP & TERKUNCI.")
        }
        val tx = dao.getTransaksiByNoAkad(noAkad)
            ?.takeIf { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
            ?: return OperationResult(false, "Akad aktif tidak ditemukan.")
        val per = max(0, periodeTerutang)
        val pokok = tx.pokokPinjaman
        val ujrah = tx.biayaUjrahPeriode * per
        val total = pokok + ujrah
        val now = FormatUtils.nowTimestamp()

        dao.updateTransaksi(
            tx.copy(
                statusGadai = "LUNAS",
                tanggalTebus = tgl,
                ujrahTebusDibayar = ujrah,
                totalUjrahDibayar = tx.totalUjrahDibayar + ujrah,
                totalTebusan = total
            )
        )
        dao.insertJurnal(
            JurnalAkuntansiEntity(
                tanggal = tgl,
                noAkad = noAkad,
                jenisMenu = "TEBUS_BARANG",
                arusKas = "MASUK",
                akunDebit = "Kas Operasional",
                akunKredit = "Piutang Qardh & Jasa Ujrah",
                pokokNominal = pokok,
                pendapatanUjrah = ujrah,
                totalKas = total,
                keterangan = "Pelunasan tebus $noAkad - ${tx.namaNasabah}",
                userId = user.id,
                namaKasir = user.namaLengkap,
                createdAt = now
            )
        )
        return OperationResult(true, "Penebusan akad $noAkad (LUNAS) sebesar ${FormatUtils.rupiah(total)} berhasil.")
    }

    suspend fun updateTebus(id: Int, tanggalTebus: String, ujrahTebus: Double): OperationResult<Unit> {
        val tx = dao.getTransaksiById(id)?.takeIf { it.statusGadai == "LUNAS" }
            ?: return OperationResult(false, "Akad lunas tidak ditemukan.")
        val tglN = if (FormatUtils.validIsoDate(tanggalTebus)) tanggalTebus else (tx.tanggalTebus ?: FormatUtils.todayIso())
        val ujrN = max(0.0, ujrahTebus)
        val totTeb = tx.pokokPinjaman + ujrN
        val perpSum = dao.getRiwayatByNoAkad(tx.noAkad).sumOf { it.biayaUjrahDibayar }
        dao.updateTransaksi(
            tx.copy(
                tanggalTebus = tglN,
                ujrahTebusDibayar = ujrN,
                totalUjrahDibayar = perpSum + ujrN,
                totalTebusan = totTeb
            )
        )
        dao.findJurnalByAkadAndMenu(tx.noAkad, "TEBUS_BARANG")?.let { j ->
            dao.updateJurnal(j.copy(tanggal = tglN, pendapatanUjrah = ujrN, totalKas = totTeb))
        }
        return OperationResult(true, "Data pelunasan akad ${tx.noAkad} diperbarui.")
    }

    suspend fun cancelTebus(id: Int): OperationResult<Unit> {
        val tx = dao.getTransaksiById(id)?.takeIf { it.statusGadai == "LUNAS" }
            ?: return OperationResult(false, "Akad lunas tidak ditemukan.")
        val riw = dao.getRiwayatByNoAkad(tx.noAkad)
        val newStatus = if (riw.isNotEmpty()) "DIPERPANJANG" else "AKTIF"
        dao.updateTransaksi(
            tx.copy(
                statusGadai = newStatus,
                tanggalTebus = null,
                ujrahTebusDibayar = null,
                totalTebusan = null,
                totalUjrahDibayar = riw.sumOf { it.biayaUjrahDibayar }
            )
        )
        dao.deleteJurnalByAkadAndMenu(tx.noAkad, "TEBUS_BARANG")
        return OperationResult(true, "Pelunasan akad ${tx.noAkad} dibatalkan & status dikembalikan ke $newStatus.")
    }

    suspend fun simpanLelang(
        noAkad: String,
        tanggalLelang: String,
        hargaLelang: Double,
        user: UserEntity
    ): OperationResult<Unit> {
        val tgl = if (FormatUtils.validIsoDate(tanggalLelang)) tanggalLelang else FormatUtils.todayIso()
        if (isDateClosed(tgl)) {
            return OperationResult(false, "Sesi operasional tanggal $tgl sudah CLOSED.")
        }
        val tx = dao.getTransaksiByNoAkad(noAkad)
            ?.takeIf { it.statusGadai == "AKTIF" || it.statusGadai == "DIPERPANJANG" }
            ?: return OperationResult(false, "Akad aktif tidak ditemukan.")
        if (hargaLelang <= 0) return OperationResult(false, "Harga lelang wajib lebih dari 0.")

        val kewajiban = tx.pokokPinjaman + tx.biayaUjrahPeriode
        val sisaRahin = max(0.0, hargaLelang - kewajiban)
        val kasMasuk = min(hargaLelang, kewajiban)
        val pokokTutup = min(tx.pokokPinjaman, hargaLelang)
        val ujrahTutup = max(0.0, min(tx.biayaUjrahPeriode, hargaLelang - pokokTutup))

        dao.updateTransaksi(
            tx.copy(
                statusGadai = "LELANG",
                tanggalLelang = tgl,
                hargaLelang = hargaLelang,
                sisaDikembalikanLelang = sisaRahin
            )
        )
        dao.insertJurnal(
            JurnalAkuntansiEntity(
                tanggal = tgl,
                noAkad = noAkad,
                jenisMenu = "LELANG_BARANG",
                arusKas = "MASUK",
                akunDebit = "Kas Operasional",
                akunKredit = "Piutang Qardh & Ujrah",
                pokokNominal = pokokTutup,
                pendapatanUjrah = ujrahTutup,
                totalKas = kasMasuk,
                keterangan = "Lelang $noAkad terjual ${FormatUtils.rupiah(hargaLelang)} (Sisa ke Rahin: ${FormatUtils.rupiah(sisaRahin)})",
                userId = user.id,
                namaKasir = user.namaLengkap,
                createdAt = FormatUtils.nowTimestamp()
            )
        )
        return OperationResult(true, "Eksekusi lelang $noAkad berhasil. Kelebihan dikembalikan ke Rahin: ${FormatUtils.rupiah(sisaRahin)}.")
    }

    suspend fun updateLelang(id: Int, tanggalLelang: String, hargaLelang: Double): OperationResult<Unit> {
        val tx = dao.getTransaksiById(id)?.takeIf { it.statusGadai == "LELANG" }
            ?: return OperationResult(false, "Data lelang tidak ditemukan.")
        val tglN = if (FormatUtils.validIsoDate(tanggalLelang)) tanggalLelang else (tx.tanggalLelang ?: FormatUtils.todayIso())
        val hargaN = max(1.0, hargaLelang)
        val kewajiban = tx.pokokPinjaman + tx.biayaUjrahPeriode
        val sisaRahin = max(0.0, hargaN - kewajiban)
        val kasMasuk = min(hargaN, kewajiban)
        val pokokTutup = min(tx.pokokPinjaman, hargaN)
        val ujrahTutup = max(0.0, min(tx.biayaUjrahPeriode, hargaN - pokokTutup))

        dao.updateTransaksi(tx.copy(tanggalLelang = tglN, hargaLelang = hargaN, sisaDikembalikanLelang = sisaRahin))
        dao.findJurnalByAkadAndMenu(tx.noAkad, "LELANG_BARANG")?.let { j ->
            dao.updateJurnal(
                j.copy(
                    tanggal = tglN,
                    pokokNominal = pokokTutup,
                    pendapatanUjrah = ujrahTutup,
                    totalKas = kasMasuk,
                    keterangan = "Lelang ${tx.noAkad} terjual ${FormatUtils.rupiah(hargaN)} (Sisa ke Rahin: ${FormatUtils.rupiah(sisaRahin)})"
                )
            )
        }
        return OperationResult(true, "Data lelang akad ${tx.noAkad} diperbarui.")
    }

    suspend fun cancelLelang(id: Int): OperationResult<Unit> {
        val tx = dao.getTransaksiById(id)?.takeIf { it.statusGadai == "LELANG" }
            ?: return OperationResult(false, "Data lelang tidak ditemukan.")
        val newStatus = if (tx.jumlahPerpanjangan > 0) "DIPERPANJANG" else "AKTIF"
        dao.updateTransaksi(
            tx.copy(
                statusGadai = newStatus,
                tanggalLelang = null,
                hargaLelang = null,
                sisaDikembalikanLelang = null
            )
        )
        dao.deleteJurnalByAkadAndMenu(tx.noAkad, "LELANG_BARANG")
        return OperationResult(true, "Lelang akad ${tx.noAkad} dibatalkan & status dikembalikan ke $newStatus.")
    }

    suspend fun kunciTutupToko(
        tanggal: String,
        gadaiBaru: Int,
        perpanjangan: Int,
        tebusan: Int,
        saldoAwal: Double,
        masuk: Double,
        keluar: Double,
        saldoSistem: Double,
        uangFisik: Double,
        catatanSelisih: String,
        user: UserEntity
    ): OperationResult<Unit> {
        if (isDateClosed(tanggal)) {
            return OperationResult(false, "Sesi tanggal $tanggal sudah CLOSED / TERKUNCI.")
        }
        val selisih = uangFisik - saldoSistem
        if (selisih != 0.0 && catatanSelisih.isBlank()) {
            return OperationResult(false, "Terdapat selisih kas sebesar ${FormatUtils.rupiah(kotlin.math.abs(selisih))}. Wajib isi keterangan selisih.")
        }
        dao.insertSesi(
            SesiTutupTokoEntity(
                tanggal = tanggal,
                status = "CLOSED",
                saldoAwalKas = saldoAwal,
                jumlahGadaiBaru = gadaiBaru,
                jumlahPerpanjangan = perpanjangan,
                jumlahTebusan = tebusan,
                totalUangMasuk = masuk,
                totalUangKeluar = keluar,
                saldoKasSistem = saldoSistem,
                uangFisikAktual = uangFisik,
                selisihKas = selisih,
                catatanSelisih = catatanSelisih.trim(),
                kasirId = user.id,
                namaKasir = user.namaLengkap,
                closedAt = FormatUtils.nowTimestamp()
            )
        )
        return OperationResult(true, "Tutup Toko tanggal $tanggal berhasil dikunci (CLOSED).")
    }

    suspend fun updateSesiTutupToko(id: Int, uangFisik: Double, catatan: String): OperationResult<Unit> {
        val sesi = dao.getSesiById(id) ?: return OperationResult(false, "Sesi tidak ditemukan.")
        val selisih = uangFisik - sesi.saldoKasSistem
        dao.updateSesi(sesi.copy(uangFisikAktual = uangFisik, selisihKas = selisih, catatanSelisih = catatan.trim()))
        return OperationResult(true, "Uang fisik & catatan selisih tutup toko tanggal ${sesi.tanggal} diperbarui.")
    }

    suspend fun bukaKunciSesi(id: Int): OperationResult<Unit> {
        dao.deleteSesiById(id)
        return OperationResult(true, "Kunci sesi tutup toko dibuka kembali (OPEN).")
    }

    suspend fun simpanDokumenPdf(
        nik: String,
        namaNasabah: String,
        uri: Uri,
        keterangan: String,
        user: UserEntity
    ): OperationResult<DokumenPelangganEntity> {
        val cleanNik = nik.trim()
        if (cleanNik.isEmpty()) return OperationResult(false, "NIK pelanggan wajib tersedia.")
        val savedRes = PdfStorageHelper.savePdfFromUri(context, cleanNik, uri)
        if (savedRes.isFailure) {
            return OperationResult(false, savedRes.exceptionOrNull()?.message ?: "Gagal menyimpan file PDF.")
        }
        val fileInfo = savedRes.getOrThrow()
        val oldDok = dao.getDokumenByNik(cleanNik)
        if (oldDok != null && oldDok.namaFileSimpan != fileInfo.savedFileName) {
            PdfStorageHelper.deleteFile(context, oldDok.namaFileSimpan)
        }
        val now = FormatUtils.nowTimestamp()
        val ket = keterangan.trim().ifEmpty { "Bukti Data Diri & Dokumen Pendukung" }
        val entity = DokumenPelangganEntity(
            id = oldDok?.id ?: 0,
            nikNasabah = cleanNik,
            namaNasabah = namaNasabah.trim(),
            namaFileSimpan = fileInfo.savedFileName,
            namaFileAsli = fileInfo.originalFileName,
            ukuranByte = fileInfo.sizeBytes,
            keterangan = ket,
            userId = user.id,
            namaKasir = user.namaLengkap,
            uploadedAt = oldDok?.uploadedAt ?: now,
            updatedAt = now
        )
        dao.upsertDokumen(entity)
        dao.syncDokumenOnTransaksi(cleanNik, fileInfo.savedFileName, fileInfo.originalFileName)
        val actionWord = if (oldDok != null) "diganti" else "disimpan"
        return OperationResult(
            true,
            "Dokumen PDF milik ${namaNasabah.ifBlank { cleanNik }} (${fileInfo.originalFileName} · ${FormatUtils.formatUkuranFile(fileInfo.sizeBytes)}) berhasil $actionWord.",
            entity
        )
    }

    suspend fun hapusDokumenPdf(nik: String): OperationResult<Unit> {
        val dok = dao.getDokumenByNik(nik.trim()) ?: return OperationResult(false, "Dokumen PDF tidak ditemukan.")
        PdfStorageHelper.deleteFile(context, dok.namaFileSimpan)
        dao.deleteDokumenByNik(nik.trim())
        dao.syncDokumenOnTransaksi(nik.trim(), null, null)
        return OperationResult(true, "Dokumen PDF pelanggan NIK $nik berhasil dihapus.")
    }

    suspend fun updatePelanggan(
        oldNama: String,
        oldNik: String,
        newNama: String,
        newNik: String,
        newHp: String,
        newAlamat: String,
        pdfUri: Uri?,
        ketDokumen: String,
        user: UserEntity
    ): OperationResult<Unit> {
        if (newNama.isBlank() || newNik.isBlank()) {
            return OperationResult(false, "Nama dan NIK wajib diisi.")
        }
        dao.updatePelangganOnTransaksi(oldNama, oldNik, newNama.trim(), newNik.trim(), newHp.trim(), newAlamat.trim())
        if (oldNama != newNama.trim()) {
            dao.replaceCustomerNameInJurnal(oldNama, newNama.trim())
        }
        dao.updateCustomerOnDokumen(oldNik, newNik.trim(), newNama.trim())
        var dokInfo = ""
        if (pdfUri != null) {
            val res = simpanDokumenPdf(newNik.trim(), newNama.trim(), pdfUri, ketDokumen, user)
            if (!res.ok) return OperationResult(false, res.message)
            dokInfo = " & dokumen PDF (${res.data?.namaFileAsli ?: ""}) diperbarui"
        }
        return OperationResult(true, "Identitas pelanggan ${newNama.trim()} diperbarui di seluruh akad, jurnal$dokInfo.")
    }

    suspend fun deletePelanggan(nama: String, nik: String): OperationResult<Unit> {
        val akads = dao.getTransaksiByPelanggan(nama, nik)
        akads.forEach { tx ->
            dao.deleteRiwayatByNoAkad(tx.noAkad)
            dao.deleteJurnalByNoAkad(tx.noAkad)
            dao.deleteTransaksiById(tx.id)
        }
        if (dao.countTransaksiByNik(nik) == 0) {
            val dok = dao.getDokumenByNik(nik)
            if (dok != null) {
                PdfStorageHelper.deleteFile(context, dok.namaFileSimpan)
                dao.deleteDokumenByNik(nik)
            }
        }
        return OperationResult(true, "Data pelanggan $nama beserta ${akads.size} akad terkait berhasil dihapus.")
    }

    suspend fun addJurnalManual(
        tanggal: String,
        noAkad: String,
        jenisMenu: String,
        arusKas: String,
        akunDebit: String,
        akunKredit: String,
        pokok: Double,
        admin: Double,
        ujrah: Double,
        totalKas: Double,
        keterangan: String,
        targetUserId: Int,
        fallbackUser: UserEntity
    ): OperationResult<Unit> {
        if (keterangan.isBlank() || totalKas <= 0) {
            return OperationResult(false, "Keterangan dan Total Kas wajib diisi.")
        }
        val tgl = if (FormatUtils.validIsoDate(tanggal)) tanggal else FormatUtils.todayIso()
        val u = dao.getUserById(targetUserId) ?: fallbackUser
        val ref = noAkad.trim().ifEmpty { "JURNAL-MANUAL" }
        dao.insertJurnal(
            JurnalAkuntansiEntity(
                tanggal = tgl,
                noAkad = ref,
                jenisMenu = jenisMenu,
                arusKas = arusKas,
                akunDebit = akunDebit.trim().ifEmpty { "Kas Operasional" },
                akunKredit = akunKredit.trim().ifEmpty { "Penyesuaian Kas" },
                pokokNominal = max(0.0, pokok),
                pendapatanAdmin = max(0.0, admin),
                pendapatanUjrah = max(0.0, ujrah),
                totalKas = totalKas,
                keterangan = keterangan.trim(),
                userId = u.id,
                namaKasir = u.namaLengkap,
                createdAt = FormatUtils.nowTimestamp()
            )
        )
        if (jenisMenu == "MODAL_AWAL" && ref == "MODAL-AWAL") {
            syncModalAwal(totalKas, u)
        }
        return OperationResult(true, "Jurnal manual berhasil ditambahkan dan disinkronkan ke saldo kas bersama.")
    }

    suspend fun updateJurnal(item: JurnalAkuntansiEntity): OperationResult<Unit> {
        dao.updateJurnal(item)
        if (item.jenisMenu == "MODAL_AWAL" && item.noAkad == "MODAL-AWAL") {
            val sys = dao.getPengaturan() ?: PengaturanSistemEntity()
            dao.upsertPengaturan(sys.copy(modalAwal = item.totalKas, updatedAt = FormatUtils.nowTimestamp()))
        }
        return OperationResult(true, "Baris jurnal berhasil diperbarui.")
    }

    suspend fun deleteJurnal(id: Int): OperationResult<Unit> {
        dao.deleteJurnalById(id)
        return OperationResult(true, "Baris jurnal berhasil dihapus.")
    }

    suspend fun saveIdentitasDanModal(
        namaAplikasi: String,
        alamat: String,
        kontak: String,
        modalAwal: Double,
        user: UserEntity
    ): OperationResult<Unit> {
        val m = max(0.0, modalAwal)
        dao.upsertPengaturan(
            PengaturanSistemEntity(
                id = 1,
                namaAplikasi = namaAplikasi.trim().ifEmpty { "AMANAH GADAI SYARIAH" },
                alamatKantor = alamat.trim(),
                kontakKantor = kontak.trim(),
                modalAwal = m,
                updatedAt = FormatUtils.nowTimestamp()
            )
        )
        syncModalAwal(m, user)
        return OperationResult(true, "Pengaturan identitas toko & modal awal (${FormatUtils.rupiah(m)}) diperbarui dan disinkronkan ke jurnal & seluruh user.")
    }

    private suspend fun syncModalAwal(modalAwal: Double, user: UserEntity) {
        val existing = dao.findJurnalByAkadAndMenu("MODAL-AWAL", "MODAL_AWAL")
        if (existing != null) {
            dao.updateJurnal(existing.copy(pokokNominal = modalAwal, totalKas = modalAwal))
        } else {
            dao.insertJurnal(
                JurnalAkuntansiEntity(
                    tanggal = FormatUtils.todayIso(),
                    noAkad = "MODAL-AWAL",
                    jenisMenu = "MODAL_AWAL",
                    arusKas = "MASUK",
                    akunDebit = "Kas Operasional",
                    akunKredit = "Modal Awal Pemilik",
                    pokokNominal = modalAwal,
                    totalKas = modalAwal,
                    keterangan = "Setoran Modal Awal Usaha Gadai Syariah",
                    userId = user.id,
                    namaKasir = user.namaLengkap,
                    createdAt = FormatUtils.nowTimestamp()
                )
            )
        }
    }

    suspend fun setDefaultLtv(id: Int, kategori: String): OperationResult<Unit> {
        dao.clearDefaultLtv(kategori)
        dao.setDefaultLtv(id)
        return OperationResult(true, "Default LTV diperbarui.")
    }

    suspend fun addLtv(kategori: String, persentase: Double, label: String, deskripsi: String): OperationResult<Unit> {
        val pct = persentase.coerceIn(1.0, 100.0)
        dao.insertLtv(
            RumusLtvEntity(
                kategori = kategori,
                persentase = pct,
                label = label.trim().ifEmpty { "Plafon ${pct}% ($kategori)" },
                deskripsi = deskripsi.trim(),
                aktif = 1,
                isDefault = 0,
                isCustom = 1
            )
        )
        return OperationResult(true, "Rumus LTV custom ditambahkan.")
    }

    suspend fun updateLtv(item: RumusLtvEntity): OperationResult<Unit> {
        dao.updateLtv(item)
        return OperationResult(true, "Rumus LTV diperbarui.")
    }

    suspend fun deleteLtv(id: Int): OperationResult<Unit> {
        val l = dao.getLtvById(id) ?: return OperationResult(false, "LTV tidak ditemukan.")
        if (l.isDefault == 1) return OperationResult(false, "Tidak dapat menghapus LTV yang sedang aktif sebagai Default.")
        dao.deleteLtvById(id)
        return OperationResult(true, "Rumus LTV dihapus.")
    }

    suspend fun addPaketUjrah(
        kode: String,
        namaPaket: String,
        kategori: String,
        subTipe: String,
        ujrah: Double,
        admin: Double,
        hari: Int,
        deskripsi: String
    ): OperationResult<Unit> {
        dao.insertPaket(
            PaketUjrahEntity(
                kode = kode.trim(),
                namaPaket = namaPaket.trim(),
                kategori = kategori,
                subTipe = subTipe.trim(),
                ujrahPerPeriode = max(0.0, ujrah),
                biayaAdmin = max(0.0, admin),
                hariPerPeriode = max(1, hari),
                deskripsiSyariah = deskripsi.trim(),
                aktif = 1,
                isDefault = 0,
                isCustom = 1
            )
        )
        return OperationResult(true, "Paket tarif Ujrah custom ditambahkan.")
    }

    suspend fun updatePaketUjrah(item: PaketUjrahEntity): OperationResult<Unit> {
        dao.updatePaket(item)
        return OperationResult(true, "Paket tarif Ujrah diperbarui.")
    }

    suspend fun togglePaketUjrah(item: PaketUjrahEntity): OperationResult<Unit> {
        dao.updatePaket(item.copy(aktif = if (item.aktif == 1) 0 else 1))
        return OperationResult(true, "Status checklist paket Ujrah diperbarui.")
    }

    suspend fun deletePaketUjrah(id: Int): OperationResult<Unit> {
        val p = dao.getPaketById(id) ?: return OperationResult(false, "Paket tidak ditemukan.")
        if (p.isDefault == 1) return OperationResult(false, "Tidak dapat menghapus paket Ujrah default.")
        dao.deletePaketById(id)
        return OperationResult(true, "Paket Ujrah dihapus.")
    }

    suspend fun addUser(username: String, namaLengkap: String, password: String, role: String, noHp: String): OperationResult<Unit> {
        val un = username.trim()
        val nm = namaLengkap.trim()
        if (un.isEmpty() || nm.isEmpty() || password.length < 4) {
            return OperationResult(false, "Username, nama lengkap, dan password (minimal 4 karakter) wajib diisi.")
        }
        if (dao.getUserByUsername(un) != null) {
            return OperationResult(false, "Username sudah digunakan.")
        }
        dao.insertUser(
            UserEntity(
                username = un,
                namaLengkap = nm,
                passwordHash = FormatUtils.hashPassword(password),
                role = if (role == "Owner") "Owner" else "Kasir",
                noHp = noHp.trim(),
                aktif = 1,
                createdAt = FormatUtils.nowTimestamp()
            )
        )
        return OperationResult(true, "User $un berhasil ditambahkan.")
    }

    suspend fun updateUser(
        uid: Int,
        username: String,
        namaLengkap: String,
        newPassword: String,
        role: String,
        noHp: String
    ): OperationResult<UserEntity> {
        val old = dao.getUserById(uid) ?: return OperationResult(false, "User tidak ditemukan.")
        val un = username.trim()
        val nm = namaLengkap.trim()
        if (un.isEmpty() || nm.isEmpty()) {
            return OperationResult(false, "Username dan nama lengkap wajib diisi.")
        }
        val existingByName = dao.getUserByUsername(un)
        if (existingByName != null && existingByName.id != uid) {
            return OperationResult(false, "Username sudah digunakan user lain.")
        }
        val updated = old.copy(
            username = un,
            namaLengkap = nm,
            passwordHash = if (newPassword.isNotEmpty()) FormatUtils.hashPassword(newPassword) else old.passwordHash,
            role = if (role == "Owner") "Owner" else "Kasir",
            noHp = noHp.trim()
        )
        dao.updateUser(updated)
        dao.syncNamaKasirOnTransaksi(uid, nm)
        dao.syncNamaKasirOnRiwayat(uid, nm)
        dao.syncNamaKasirOnJurnal(uid, nm)
        dao.syncNamaKasirOnSesi(uid, nm)
        dao.syncNamaKasirOnDokumen(uid, nm)
        return OperationResult(true, "Data user berhasil diperbarui & disinkronkan ke seluruh riwayat.", updated)
    }

    suspend fun toggleUser(uid: Int, currentUserId: Int): OperationResult<Unit> {
        if (uid == currentUserId) return OperationResult(false, "Tidak dapat menonaktifkan akun sendiri.")
        val u = dao.getUserById(uid) ?: return OperationResult(false, "User tidak ditemukan.")
        dao.updateUser(u.copy(aktif = if (u.aktif == 1) 0 else 1))
        return OperationResult(true, "Status user diperbarui.")
    }

    suspend fun deleteUser(uid: Int, currentUserId: Int): OperationResult<Unit> {
        if (uid == currentUserId) return OperationResult(false, "Tidak dapat menghapus akun Anda sendiri yang sedang login.")
        dao.deleteUserById(uid)
        return OperationResult(true, "User berhasil dihapus.")
    }

    suspend fun importBarangRows(rows: List<List<String>>, user: UserEntity): OperationResult<Unit> {
        var imported = 0
        val errors = mutableListOf<String>()
        for (i in 1 until rows.size) {
            val r = rows[i]
            val no = r.getOrNull(0)?.trim().orEmpty()
            val tgl = r.getOrNull(1)?.trim().orEmpty()
            val nama = r.getOrNull(2)?.trim().orEmpty()
            val nik = r.getOrNull(3)?.trim().orEmpty()
            val hp = r.getOrNull(4)?.trim().orEmpty()
            val alm = r.getOrNull(5)?.trim().orEmpty()
            val kat = r.getOrNull(6)?.trim().orEmpty()
            val brg = r.getOrNull(7)?.trim().orEmpty().ifEmpty { "$kat Jaminan" }
            val tak = r.getOrNull(8)?.trim()?.toDoubleOrNull() ?: 0.0
            val pok = r.getOrNull(9)?.trim()?.toDoubleOrNull() ?: 0.0
            val spek = r.getOrNull(10)?.trim().orEmpty()
            val kond = r.getOrNull(11)?.trim().orEmpty().ifEmpty { "Baik" }

            if (no.isEmpty() || !FormatUtils.validIsoDate(tgl) || nama.isEmpty() ||
                kat !in listOf("HP", "Motor", "Emas") || tak <= 0 || pok <= 0 || spek.isEmpty()
            ) {
                errors.add("Baris ${i + 1}: Data wajib tidak lengkap atau format salah.")
                continue
            }
            if (isDateClosed(tgl)) {
                errors.add("Baris ${i + 1}: Tanggal $tgl sudah CLOSED.")
                continue
            }
            if (dao.getTransaksiByNoAkad(no) != null) {
                errors.add("Baris ${i + 1}: No. Akad $no duplikat.")
                continue
            }
            val ltv = dao.getActiveLtv(kat)
            val ltvPct = ltv?.persentase ?: 75.0
            val plafon = (tak * (ltvPct / 100.0)).roundToLong().toDouble()
            if (pok > plafon) {
                errors.add("Baris ${i + 1}: Pokok melebihi LTV $ltvPct%.")
                continue
            }
            val pkg = dao.getActivePackagesByKategori(kat).firstOrNull()
            if (pkg == null) {
                errors.add("Baris ${i + 1}: Paket Ujrah aktif untuk $kat tidak tersedia.")
                continue
            }
            val jt = FormatUtils.addDaysIso(tgl, pkg.hariPerPeriode)
            val adm = pkg.biayaAdmin
            val bersih = max(0.0, pok - adm)
            val now = FormatUtils.nowTimestamp()
            dao.insertTransaksi(
                TransaksiGadaiEntity(
                    noAkad = no, tanggalAkad = tgl, jatuhTempo = jt,
                    namaNasabah = nama, nikNasabah = nik, noHp = hp, alamatNasabah = alm,
                    kategoriBarang = kat, namaBarang = brg, spesifikasiRingkas = spek, kondisiBarang = kond,
                    nilaiTaksiran = tak, ltvPersen = ltvPct, plafonMaksimal = plafon,
                    pokokPinjaman = pok, biayaAdmin = adm, danaBersihDiterima = bersih,
                    ujrahPackageId = pkg.id, ujrahPackageNama = pkg.namaPaket,
                    biayaUjrahPeriode = pkg.ujrahPerPeriode, hariPerPeriode = pkg.hariPerPeriode,
                    statusGadai = "AKTIF", userId = user.id, namaKasir = user.namaLengkap, createdAt = now
                )
            )
            dao.insertJurnal(
                JurnalAkuntansiEntity(
                    tanggal = tgl, noAkad = no, jenisMenu = "GADAI_BARANG", arusKas = "KELUAR",
                    akunDebit = "Piutang Qardh", akunKredit = "Kas Operasional",
                    pokokNominal = pok, totalKas = pok, keterangan = "Import Gadai $no - $nama",
                    userId = user.id, namaKasir = user.namaLengkap, createdAt = now
                )
            )
            if (adm > 0) {
                dao.insertJurnal(
                    JurnalAkuntansiEntity(
                        tanggal = tgl, noAkad = no, jenisMenu = "GADAI_BARANG", arusKas = "MASUK",
                        akunDebit = "Kas Operasional", akunKredit = "Pendapatan Admin Riil",
                        pendapatanAdmin = adm, totalKas = adm, keterangan = "Biaya administrasi awal $no",
                        userId = user.id, namaKasir = user.namaLengkap, createdAt = now
                    )
                )
            }
            imported++
        }
        val errText = if (errors.isNotEmpty()) " Error: ${errors.joinToString(" ; ")}" else ""
        return OperationResult(true, "Import .xlsx selesai: $imported baris berhasil.$errText")
    }
}
