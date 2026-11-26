package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val namaLengkap: String,
    val passwordHash: String,
    val role: String = "Kasir", // Owner | Kasir
    val noHp: String = "",
    val aktif: Int = 1,
    val createdAt: String = ""
)

@Entity(tableName = "pengaturan_sistem")
data class PengaturanSistemEntity(
    @PrimaryKey val id: Int = 1,
    val namaAplikasi: String = "AMANAH GADAI SYARIAH",
    val alamatKantor: String = "Jl. Cipinang Muara 2 No. 18, Jakarta Timur",
    val kontakKantor: String = "Telp / WhatsApp: 0812-6313-1041",
    val modalAwal: Double = 100000000.0,
    val updatedAt: String = ""
)

@Entity(tableName = "rumus_ltv")
data class RumusLtvEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val kategori: String, // HP | Motor | Emas
    val persentase: Double,
    val label: String,
    val deskripsi: String = "",
    val aktif: Int = 0,
    val isDefault: Int = 0,
    val isCustom: Int = 0
)

@Entity(tableName = "paket_ujrah")
data class PaketUjrahEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val kode: String,
    val namaPaket: String,
    val kategori: String, // HP | Motor | Emas
    val subTipe: String = "",
    val ujrahPerPeriode: Double,
    val biayaAdmin: Double = 0.0,
    val hariPerPeriode: Int = 30,
    val deskripsiSyariah: String = "",
    val aktif: Int = 1,
    val isDefault: Int = 0,
    val isCustom: Int = 0
)

@Entity(
    tableName = "transaksi_gadai",
    indices = [
        Index(value = ["noAkad"], unique = true),
        Index(value = ["statusGadai"]),
        Index(value = ["tanggalAkad"]),
        Index(value = ["jatuhTempo"])
    ]
)
data class TransaksiGadaiEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val noAkad: String,
    val tanggalAkad: String,
    val jatuhTempo: String,
    val namaNasabah: String,
    val nikNasabah: String = "",
    val noHp: String = "",
    val alamatNasabah: String = "",
    val kategoriBarang: String = "HP", // HP | Motor | Emas
    val namaBarang: String,
    val spesifikasiRingkas: String = "",
    val kelengkapanBarang: String = "",
    val kondisiBarang: String = "",
    val detailJson: String? = null,
    val nilaiTaksiran: Double = 0.0,
    val ltvPersen: Double = 75.0,
    val plafonMaksimal: Double = 0.0,
    val pokokPinjaman: Double = 0.0,
    val biayaAdmin: Double = 0.0,
    val danaBersihDiterima: Double = 0.0,
    val ujrahPackageId: Int? = null,
    val ujrahPackageNama: String = "",
    val biayaUjrahPeriode: Double = 0.0,
    val hariPerPeriode: Int = 30,
    val totalUjrahDibayar: Double = 0.0,
    val jumlahPerpanjangan: Int = 0,
    val tanggalPerpanjangTerakhir: String? = null,
    val statusGadai: String = "AKTIF", // AKTIF | DIPERPANJANG | LUNAS | LELANG
    val tanggalTebus: String? = null,
    val ujrahTebusDibayar: Double? = null,
    val totalTebusan: Double? = null,
    val tanggalLelang: String? = null,
    val hargaLelang: Double? = null,
    val sisaDikembalikanLelang: Double? = null,
    val userId: Int? = null,
    val namaKasir: String = "",
    val fileDokumen: String? = null,
    val namaFileAsli: String? = null,
    val createdAt: String = ""
)

@Entity(tableName = "riwayat_perpanjangan")
data class RiwayatPerpanjanganEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val noAkad: String,
    val tanggalPerpanjang: String,
    val jatuhTempoLama: String,
    val jatuhTempoBaru: String,
    val jumlahPeriode: Int = 1,
    val biayaUjrahDibayar: Double = 0.0,
    val userId: Int? = null,
    val namaKasir: String = "",
    val createdAt: String = ""
)

@Entity(
    tableName = "jurnal_akuntansi",
    indices = [Index(value = ["tanggal"])]
)
data class JurnalAkuntansiEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tanggal: String,
    val noAkad: String,
    val jenisMenu: String, // MODAL_AWAL | GADAI_BARANG | PERPANJANG_GADAI | TEBUS_BARANG | LELANG_BARANG
    val arusKas: String, // MASUK | KELUAR
    val akunDebit: String,
    val akunKredit: String,
    val pokokNominal: Double = 0.0,
    val pendapatanAdmin: Double = 0.0,
    val pendapatanUjrah: Double = 0.0,
    val totalKas: Double = 0.0,
    val keterangan: String,
    val userId: Int? = null,
    val namaKasir: String = "",
    val createdAt: String = ""
)

@Entity(
    tableName = "sesi_tutup_toko",
    indices = [Index(value = ["tanggal"], unique = true)]
)
data class SesiTutupTokoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tanggal: String,
    val status: String = "OPEN", // OPEN | CLOSED
    val saldoAwalKas: Double = 0.0,
    val jumlahGadaiBaru: Int = 0,
    val jumlahPerpanjangan: Int = 0,
    val jumlahTebusan: Int = 0,
    val totalUangMasuk: Double = 0.0,
    val totalUangKeluar: Double = 0.0,
    val saldoKasSistem: Double = 0.0,
    val uangFisikAktual: Double? = null,
    val selisihKas: Double? = null,
    val catatanSelisih: String = "",
    val kasirId: Int? = null,
    val namaKasir: String = "",
    val closedAt: String? = null
)

@Entity(
    tableName = "dokumen_pelanggan",
    indices = [Index(value = ["nikNasabah"], unique = true)]
)
data class DokumenPelangganEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nikNasabah: String,
    val namaNasabah: String = "",
    val namaFileSimpan: String,
    val namaFileAsli: String,
    val ukuranByte: Long = 0L,
    val keterangan: String = "Bukti Data Diri & Dokumen Pendukung",
    val userId: Int? = null,
    val namaKasir: String = "",
    val uploadedAt: String = "",
    val updatedAt: String = ""
)
