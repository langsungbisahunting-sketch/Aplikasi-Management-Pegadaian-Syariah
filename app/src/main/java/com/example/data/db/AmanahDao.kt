package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AmanahDao {
    // Users
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun observeUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Int)

    // Pengaturan Sistem
    @Query("SELECT * FROM pengaturan_sistem WHERE id = 1 LIMIT 1")
    fun observePengaturan(): Flow<PengaturanSistemEntity?>

    @Query("SELECT * FROM pengaturan_sistem WHERE id = 1 LIMIT 1")
    suspend fun getPengaturan(): PengaturanSistemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPengaturan(pengaturan: PengaturanSistemEntity)

    // Rumus LTV
    @Query("SELECT * FROM rumus_ltv ORDER BY kategori ASC, persentase ASC")
    fun observeRumusLtv(): Flow<List<RumusLtvEntity>>

    @Query("SELECT * FROM rumus_ltv WHERE kategori = :kategori AND aktif = 1 ORDER BY isDefault DESC, id ASC LIMIT 1")
    suspend fun getActiveLtv(kategori: String): RumusLtvEntity?

    @Query("SELECT * FROM rumus_ltv WHERE id = :id LIMIT 1")
    suspend fun getLtvById(id: Int): RumusLtvEntity?

    @Query("UPDATE rumus_ltv SET isDefault = 0 WHERE kategori = :kategori")
    suspend fun clearDefaultLtv(kategori: String)

    @Query("UPDATE rumus_ltv SET aktif = 1, isDefault = 1 WHERE id = :id")
    suspend fun setDefaultLtv(id: Int)

    @Insert
    suspend fun insertLtv(ltv: RumusLtvEntity): Long

    @Update
    suspend fun updateLtv(ltv: RumusLtvEntity)

    @Query("DELETE FROM rumus_ltv WHERE id = :id")
    suspend fun deleteLtvById(id: Int)

    // Paket Ujrah
    @Query("SELECT * FROM paket_ujrah ORDER BY kategori ASC, id ASC")
    fun observePaketUjrah(): Flow<List<PaketUjrahEntity>>

    @Query("SELECT * FROM paket_ujrah WHERE kategori = :kategori AND aktif = 1 ORDER BY isDefault DESC, id ASC")
    suspend fun getActivePackagesByKategori(kategori: String): List<PaketUjrahEntity>

    @Query("SELECT * FROM paket_ujrah WHERE id = :id LIMIT 1")
    suspend fun getPaketById(id: Int): PaketUjrahEntity?

    @Insert
    suspend fun insertPaket(paket: PaketUjrahEntity): Long

    @Update
    suspend fun updatePaket(paket: PaketUjrahEntity)

    @Query("DELETE FROM paket_ujrah WHERE id = :id")
    suspend fun deletePaketById(id: Int)

    // Transaksi Gadai
    @Query("SELECT * FROM transaksi_gadai ORDER BY id DESC")
    fun observeTransaksiGadai(): Flow<List<TransaksiGadaiEntity>>

    @Query("SELECT * FROM transaksi_gadai WHERE noAkad = :noAkad LIMIT 1")
    suspend fun getTransaksiByNoAkad(noAkad: String): TransaksiGadaiEntity?

    @Query("SELECT * FROM transaksi_gadai WHERE id = :id LIMIT 1")
    suspend fun getTransaksiById(id: Int): TransaksiGadaiEntity?

    @Query("SELECT * FROM transaksi_gadai WHERE noAkad LIKE :prefix || '%' ORDER BY id DESC LIMIT 1")
    suspend fun getLatestNoAkadByPrefix(prefix: String): TransaksiGadaiEntity?

    @Insert
    suspend fun insertTransaksi(tx: TransaksiGadaiEntity): Long

    @Update
    suspend fun updateTransaksi(tx: TransaksiGadaiEntity)

    @Query("DELETE FROM transaksi_gadai WHERE id = :id")
    suspend fun deleteTransaksiById(id: Int)

    @Query("UPDATE transaksi_gadai SET namaKasir = :namaKasir WHERE userId = :userId")
    suspend fun syncNamaKasirOnTransaksi(userId: Int, namaKasir: String)

    @Query("UPDATE transaksi_gadai SET namaNasabah = :newNama, nikNasabah = :newNik, noHp = :newHp, alamatNasabah = :newAlamat WHERE namaNasabah = :oldNama AND nikNasabah = :oldNik")
    suspend fun updatePelangganOnTransaksi(oldNama: String, oldNik: String, newNama: String, newNik: String, newHp: String, newAlamat: String)

    @Query("SELECT * FROM transaksi_gadai WHERE namaNasabah = :nama AND nikNasabah = :nik")
    suspend fun getTransaksiByPelanggan(nama: String, nik: String): List<TransaksiGadaiEntity>

    @Query("SELECT COUNT(*) FROM transaksi_gadai WHERE nikNasabah = :nik")
    suspend fun countTransaksiByNik(nik: String): Int

    @Query("UPDATE transaksi_gadai SET fileDokumen = :fileSimpan, namaFileAsli = :fileAsli WHERE nikNasabah = :nik")
    suspend fun syncDokumenOnTransaksi(nik: String, fileSimpan: String?, fileAsli: String?)

    // Riwayat Perpanjangan
    @Query("SELECT * FROM riwayat_perpanjangan ORDER BY id DESC")
    fun observeRiwayatPerpanjangan(): Flow<List<RiwayatPerpanjanganEntity>>

    @Query("SELECT * FROM riwayat_perpanjangan WHERE noAkad = :noAkad ORDER BY id ASC")
    suspend fun getRiwayatByNoAkad(noAkad: String): List<RiwayatPerpanjanganEntity>

    @Query("SELECT * FROM riwayat_perpanjangan WHERE id = :id LIMIT 1")
    suspend fun getRiwayatById(id: Int): RiwayatPerpanjanganEntity?

    @Insert
    suspend fun insertRiwayat(rp: RiwayatPerpanjanganEntity): Long

    @Update
    suspend fun updateRiwayat(rp: RiwayatPerpanjanganEntity)

    @Query("DELETE FROM riwayat_perpanjangan WHERE id = :id")
    suspend fun deleteRiwayatById(id: Int)

    @Query("DELETE FROM riwayat_perpanjangan WHERE noAkad = :noAkad")
    suspend fun deleteRiwayatByNoAkad(noAkad: String)

    @Query("UPDATE riwayat_perpanjangan SET noAkad = :newNoAkad WHERE noAkad = :oldNoAkad")
    suspend fun renameNoAkadOnRiwayat(oldNoAkad: String, newNoAkad: String)

    @Query("UPDATE riwayat_perpanjangan SET namaKasir = :namaKasir WHERE userId = :userId")
    suspend fun syncNamaKasirOnRiwayat(userId: Int, namaKasir: String)

    // Jurnal Akuntansi
    @Query("SELECT * FROM jurnal_akuntansi ORDER BY tanggal DESC, id DESC")
    fun observeJurnal(): Flow<List<JurnalAkuntansiEntity>>

    @Query("SELECT * FROM jurnal_akuntansi WHERE id = :id LIMIT 1")
    suspend fun getJurnalById(id: Int): JurnalAkuntansiEntity?

    @Query("SELECT * FROM jurnal_akuntansi WHERE noAkad = :noAkad AND jenisMenu = :jenisMenu ORDER BY id DESC LIMIT 1")
    suspend fun findJurnalByAkadAndMenu(noAkad: String, jenisMenu: String): JurnalAkuntansiEntity?

    @Query("SELECT * FROM jurnal_akuntansi WHERE noAkad = :noAkad AND jenisMenu = :jenisMenu AND arusKas = :arusKas LIMIT 1")
    suspend fun findJurnalByAkadMenuArus(noAkad: String, jenisMenu: String, arusKas: String): JurnalAkuntansiEntity?

    @Query("SELECT * FROM jurnal_akuntansi WHERE noAkad = :noAkad AND jenisMenu = :jenisMenu AND tanggal = :tanggal LIMIT 1")
    suspend fun findJurnalByAkadMenuTanggal(noAkad: String, jenisMenu: String, tanggal: String): JurnalAkuntansiEntity?

    @Query("SELECT * FROM jurnal_akuntansi WHERE tanggal < :tanggal")
    suspend fun getJurnalBeforeDate(tanggal: String): List<JurnalAkuntansiEntity>

    @Query("SELECT * FROM jurnal_akuntansi WHERE tanggal = :tanggal")
    suspend fun getJurnalOnDate(tanggal: String): List<JurnalAkuntansiEntity>

    @Insert
    suspend fun insertJurnal(jurnal: JurnalAkuntansiEntity): Long

    @Update
    suspend fun updateJurnal(jurnal: JurnalAkuntansiEntity)

    @Query("DELETE FROM jurnal_akuntansi WHERE id = :id")
    suspend fun deleteJurnalById(id: Int)

    @Query("DELETE FROM jurnal_akuntansi WHERE noAkad = :noAkad")
    suspend fun deleteJurnalByNoAkad(noAkad: String)

    @Query("DELETE FROM jurnal_akuntansi WHERE noAkad = :noAkad AND jenisMenu = :jenisMenu")
    suspend fun deleteJurnalByAkadAndMenu(noAkad: String, jenisMenu: String)

    @Query("UPDATE jurnal_akuntansi SET noAkad = :newNoAkad WHERE noAkad = :oldNoAkad")
    suspend fun renameNoAkadOnJurnal(oldNoAkad: String, newNoAkad: String)

    @Query("UPDATE jurnal_akuntansi SET namaKasir = :namaKasir WHERE userId = :userId")
    suspend fun syncNamaKasirOnJurnal(userId: Int, namaKasir: String)

    @Query("UPDATE jurnal_akuntansi SET keterangan = REPLACE(keterangan, :oldName, :newName) WHERE keterangan LIKE '%' || :oldName || '%'")
    suspend fun replaceCustomerNameInJurnal(oldName: String, newName: String)

    // Sesi Tutup Toko
    @Query("SELECT * FROM sesi_tutup_toko ORDER BY tanggal DESC")
    fun observeSesiTutupToko(): Flow<List<SesiTutupTokoEntity>>

    @Query("SELECT * FROM sesi_tutup_toko WHERE tanggal = :tanggal LIMIT 1")
    suspend fun getSesiByTanggal(tanggal: String): SesiTutupTokoEntity?

    @Query("SELECT * FROM sesi_tutup_toko WHERE id = :id LIMIT 1")
    suspend fun getSesiById(id: Int): SesiTutupTokoEntity?

    @Insert
    suspend fun insertSesi(sesi: SesiTutupTokoEntity): Long

    @Update
    suspend fun updateSesi(sesi: SesiTutupTokoEntity)

    @Query("DELETE FROM sesi_tutup_toko WHERE id = :id")
    suspend fun deleteSesiById(id: Int)

    @Query("UPDATE sesi_tutup_toko SET namaKasir = :namaKasir WHERE kasirId = :userId")
    suspend fun syncNamaKasirOnSesi(userId: Int, namaKasir: String)

    // Dokumen Pelanggan
    @Query("SELECT * FROM dokumen_pelanggan ORDER BY updatedAt DESC")
    fun observeDokumenPelanggan(): Flow<List<DokumenPelangganEntity>>

    @Query("SELECT * FROM dokumen_pelanggan WHERE nikNasabah = :nik LIMIT 1")
    suspend fun getDokumenByNik(nik: String): DokumenPelangganEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDokumen(dok: DokumenPelangganEntity): Long

    @Query("DELETE FROM dokumen_pelanggan WHERE nikNasabah = :nik")
    suspend fun deleteDokumenByNik(nik: String)

    @Query("UPDATE dokumen_pelanggan SET nikNasabah = :newNik, namaNasabah = :newNama WHERE nikNasabah = :oldNik")
    suspend fun updateCustomerOnDokumen(oldNik: String, newNik: String, newNama: String)

    @Query("UPDATE dokumen_pelanggan SET namaKasir = :namaKasir WHERE userId = :userId")
    suspend fun syncNamaKasirOnDokumen(userId: Int, namaKasir: String)
}
