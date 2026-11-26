package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.utils.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        PengaturanSistemEntity::class,
        RumusLtvEntity::class,
        PaketUjrahEntity::class,
        TransaksiGadaiEntity::class,
        RiwayatPerpanjanganEntity::class,
        JurnalAkuntansiEntity::class,
        SesiTutupTokoEntity::class,
        DokumenPelangganEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AmanahDatabase : RoomDatabase() {
    abstract fun dao(): AmanahDao

    companion object {
        @Volatile
        private var INSTANCE: AmanahDatabase? = null

        fun getInstance(context: Context): AmanahDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AmanahDatabase::class.java,
                    "amanah_gadai.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                seedInitialData(getInstance(context).dao())
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun ensureSeeded(dao: AmanahDao) {
            if (dao.countUsers() == 0) {
                seedInitialData(dao)
            }
        }

        private suspend fun seedInitialData(dao: AmanahDao) {
            val now = FormatUtils.nowTimestamp()
            val today = FormatUtils.todayIso()

            if (dao.countUsers() == 0) {
                dao.insertUser(
                    UserEntity(
                        id = 1,
                        username = "adiministrator",
                        namaLengkap = "Administrator (Owner)",
                        passwordHash = FormatUtils.hashPassword("123456"),
                        role = "Owner",
                        noHp = "081263131041",
                        aktif = 1,
                        createdAt = now
                    )
                )
                dao.insertUser(
                    UserEntity(
                        id = 2,
                        username = "kasir",
                        namaLengkap = "Kasir Operasional",
                        passwordHash = FormatUtils.hashPassword("123456"),
                        role = "Kasir",
                        noHp = "081200000002",
                        aktif = 1,
                        createdAt = now
                    )
                )
            }

            if (dao.getPengaturan() == null) {
                dao.upsertPengaturan(
                    PengaturanSistemEntity(
                        id = 1,
                        namaAplikasi = "AMANAH GADAI SYARIAH",
                        alamatKantor = "Jl. Cipinang Muara 2 No. 18, Jakarta Timur",
                        kontakKantor = "Telp / WhatsApp: 0812-6313-1041",
                        modalAwal = 100_000_000.0,
                        updatedAt = now
                    )
                )
            }

            if (dao.getActiveLtv("HP") == null) {
                val ltvSeeds = listOf(
                    RumusLtvEntity(kategori = "HP", persentase = 60.0, label = "Plafon 60% (HP)", deskripsi = "Konservatif 60% dari nilai taksiran pasar HP", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "HP", persentase = 70.0, label = "Plafon 70% (HP)", deskripsi = "Moderat 70% dari nilai taksiran pasar HP", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "HP", persentase = 75.0, label = "Plafon 75% (HP)", deskripsi = "Standar default 75% dari nilai taksiran pasar HP", aktif = 1, isDefault = 1, isCustom = 0),
                    RumusLtvEntity(kategori = "HP", persentase = 80.0, label = "Plafon 80% (HP)", deskripsi = "Fleksibel 80% dari nilai taksiran pasar HP", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "HP", persentase = 85.0, label = "Plafon 85% (HP)", deskripsi = "Maksimal 85% dari nilai taksiran pasar HP", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Motor", persentase = 60.0, label = "Plafon 60% (Motor)", deskripsi = "Konservatif 60% dari nilai taksiran pasar Motor", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Motor", persentase = 70.0, label = "Plafon 70% (Motor)", deskripsi = "Moderat 70% dari nilai taksiran pasar Motor", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Motor", persentase = 75.0, label = "Plafon 75% (Motor)", deskripsi = "Standar default 75% dari nilai taksiran pasar Motor", aktif = 1, isDefault = 1, isCustom = 0),
                    RumusLtvEntity(kategori = "Motor", persentase = 80.0, label = "Plafon 80% (Motor)", deskripsi = "Fleksibel 80% dari nilai taksiran pasar Motor", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Motor", persentase = 85.0, label = "Plafon 85% (Motor)", deskripsi = "Maksimal 85% dari nilai taksiran pasar Motor", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Emas", persentase = 60.0, label = "Plafon 60% (Emas)", deskripsi = "Konservatif 60% dari nilai taksiran Emas", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Emas", persentase = 70.0, label = "Plafon 70% (Emas)", deskripsi = "Moderat 70% dari nilai taksiran Emas", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Emas", persentase = 75.0, label = "Plafon 75% (Emas)", deskripsi = "Menengah 75% dari nilai taksiran Emas", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Emas", persentase = 80.0, label = "Plafon 80% (Emas)", deskripsi = "Tinggi 80% dari nilai taksiran Emas", aktif = 0, isDefault = 0, isCustom = 0),
                    RumusLtvEntity(kategori = "Emas", persentase = 85.0, label = "Plafon 85% (Emas)", deskripsi = "Standar default 85% dari nilai taksiran Emas", aktif = 1, isDefault = 1, isCustom = 0)
                )
                ltvSeeds.forEach { dao.insertLtv(it) }
            }

            if (dao.getActivePackagesByKategori("HP").isEmpty()) {
                val pkgSeeds = listOf(
                    PaketUjrahEntity(kode = "KLS-A", namaPaket = "Tarif Kelas A - HP / Elektronik", kategori = "HP", subTipe = "Unit HP / Gadget", ujrahPerPeriode = 50000.0, biayaAdmin = 25000.0, hariPerPeriode = 30, deskripsiSyariah = "Biaya riil brankas anti-lembab, segel amanah & perawatan baterai 30 hari", aktif = 1, isDefault = 1, isCustom = 0),
                    PaketUjrahEntity(kode = "KLS-B", namaPaket = "Tarif Kelas B - Motor Titip Dokumen (BPKB)", kategori = "Motor", subTipe = "Titip Dokumen (BPKB)", ujrahPerPeriode = 75000.0, biayaAdmin = 50000.0, hariPerPeriode = 30, deskripsiSyariah = "Biaya riil arsip tahan api, verifikasi BPKB & pengawasan 30 hari", aktif = 1, isDefault = 0, isCustom = 0),
                    PaketUjrahEntity(kode = "KLS-C", namaPaket = "Tarif Kelas C - Motor Titip Unit & Dokumen", kategori = "Motor", subTipe = "Titip Unit & Dokumen", ujrahPerPeriode = 150000.0, biayaAdmin = 50000.0, hariPerPeriode = 30, deskripsiSyariah = "Biaya riil sewa ruang garasi tertutup, keamanan 24 jam & perawatan unit 30 hari", aktif = 1, isDefault = 1, isCustom = 0),
                    PaketUjrahEntity(kode = "KLS-D", namaPaket = "Tarif Kelas D - Emas < 10 gr", kategori = "Emas", subTipe = "Emas < 10 gr", ujrahPerPeriode = 40000.0, biayaAdmin = 20000.0, hariPerPeriode = 30, deskripsiSyariah = "Biaya riil kotak brankas SDB asuransi emas kecil (< 10 gram) 30 hari", aktif = 1, isDefault = 1, isCustom = 0),
                    PaketUjrahEntity(kode = "KLS-E", namaPaket = "Tarif Kelas E - Emas >= 10 gr", kategori = "Emas", subTipe = "Emas >= 10 gr", ujrahPerPeriode = 80000.0, biayaAdmin = 30000.0, hariPerPeriode = 30, deskripsiSyariah = "Biaya riil kotak brankas SDB prioritas emas >= 10 gram 30 hari", aktif = 1, isDefault = 0, isCustom = 0)
                )
                pkgSeeds.forEach { dao.insertPaket(it) }
            }

            if (dao.findJurnalByAkadAndMenu("MODAL-AWAL", "MODAL_AWAL") == null) {
                dao.insertJurnal(
                    JurnalAkuntansiEntity(
                        tanggal = today,
                        noAkad = "MODAL-AWAL",
                        jenisMenu = "MODAL_AWAL",
                        arusKas = "MASUK",
                        akunDebit = "Kas Operasional",
                        akunKredit = "Modal Awal Pemilik",
                        pokokNominal = 100_000_000.0,
                        pendapatanAdmin = 0.0,
                        pendapatanUjrah = 0.0,
                        totalKas = 100_000_000.0,
                        keterangan = "Setoran Modal Awal Usaha Gadai Syariah",
                        userId = 1,
                        namaKasir = "Administrator (Owner)",
                        createdAt = now
                    )
                )
            }
        }
    }
}
