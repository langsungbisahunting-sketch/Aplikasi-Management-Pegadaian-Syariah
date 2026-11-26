package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AmanahDatabase
import com.example.data.repository.GadaiRepository
import com.example.utils.XlsxHelper
import com.example.utils.XlsxSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AmanahDatabase
    private lateinit var repo: GadaiRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AmanahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = GadaiRepository(context, db.dao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches Amanah Gadai`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Amanah Gadai", appName)
    }

    @Test
    fun `default users seed and login works`() = runBlocking {
        val ownerRes = repo.login("adiministrator", "123456")
        assertTrue(ownerRes.ok)
        assertEquals("Owner", ownerRes.data?.role)

        val kasirRes = repo.login("kasir", "123456")
        assertTrue(kasirRes.ok)
        assertEquals("Kasir", kasirRes.data?.role)

        val badRes = repo.login("kasir", "salah")
        assertFalse(badRes.ok)
    }

    @Test
    fun `gadai ltv plafon enforcement and full lifecycle`() = runBlocking {
        val kasir = repo.login("kasir", "123456").data!!
        val hpPackage = db.dao().getActivePackagesByKategori("HP").first()

        // Taksiran 2,000,000 with default 75% LTV => plafon 1,500,000
        val overPlafon = repo.simpanGadaiBaru(
            noAkadInput = "AG-TEST-001",
            tanggalAkad = "2026-01-10",
            namaNasabah = "Budi Santoso",
            nikNasabah = "3175010101900001",
            noHp = "081234567890",
            alamatNasabah = "Jl. Melati No. 5",
            kategoriBarang = "HP",
            namaBarang = "Samsung Galaxy A55",
            spesifikasiRingkas = "8/256GB",
            kelengkapanBarang = "Unit + Dus",
            kondisiBarang = "Mulus Normal",
            nilaiTaksiran = 2_000_000.0,
            pokokPinjaman = 1_600_000.0,
            paketId = hpPackage.id,
            dokumenUri = null,
            keteranganDokumen = "",
            user = kasir
        )
        assertFalse(overPlafon.ok)

        val validGadai = repo.simpanGadaiBaru(
            noAkadInput = "AG-TEST-001",
            tanggalAkad = "2026-01-10",
            namaNasabah = "Budi Santoso",
            nikNasabah = "3175010101900001",
            noHp = "081234567890",
            alamatNasabah = "Jl. Melati No. 5",
            kategoriBarang = "HP",
            namaBarang = "Samsung Galaxy A55",
            spesifikasiRingkas = "8/256GB",
            kelengkapanBarang = "Unit + Dus",
            kondisiBarang = "Mulus Normal",
            nilaiTaksiran = 2_000_000.0,
            pokokPinjaman = 1_500_000.0,
            paketId = hpPackage.id,
            dokumenUri = null,
            keteranganDokumen = "",
            user = kasir
        )
        assertTrue(validGadai.ok)
        assertNotNull(validGadai.data)
        assertEquals("2026-02-09", validGadai.data!!.jatuhTempo)

        // Perpanjang 1 periode (+30 hari)
        val perpRes = repo.simpanPerpanjang("AG-TEST-001", "2026-02-08", 1, kasir)
        assertTrue(perpRes.ok)
        val afterPerp = db.dao().getTransaksiByNoAkad("AG-TEST-001")!!
        assertEquals("DIPERPANJANG", afterPerp.statusGadai)
        assertEquals("2026-03-11", afterPerp.jatuhTempo)

        // Tebus barang
        val tebusRes = repo.simpanTebus("AG-TEST-001", "2026-03-01", 1, kasir)
        assertTrue(tebusRes.ok)
        val afterTebus = db.dao().getTransaksiByNoAkad("AG-TEST-001")!!
        assertEquals("LUNAS", afterTebus.statusGadai)

        val jurnal = repo.jurnalAkuntansi.first()
        assertTrue(jurnal.any { it.noAkad == "AG-TEST-001" && it.jenisMenu == "GADAI_BARANG" })
        assertTrue(jurnal.any { it.noAkad == "AG-TEST-001" && it.jenisMenu == "PERPANJANG_GADAI" })
        assertTrue(jurnal.any { it.noAkad == "AG-TEST-001" && it.jenisMenu == "TEBUS_BARANG" })
    }

    @Test
    fun `xlsx build and parse roundtrip preserves rows`() {
        val bytes = XlsxHelper.buildXlsxBytes(
            listOf(
                XlsxSheet(
                    name = "Data",
                    rows = listOf(
                        listOf("No Akad", "Nasabah", "Pokok"),
                        listOf("AG-20260110-001", "Siti Aminah", 1500000)
                    )
                )
            )
        )
        val parsed = XlsxHelper.parseFirstSheetRows(ByteArrayInputStream(bytes))
        assertTrue(parsed.isSuccess)
        val rows = parsed.getOrThrow()
        assertEquals(2, rows.size)
        assertEquals("AG-20260110-001", rows[1][0])
        assertEquals("Siti Aminah", rows[1][1])
        assertEquals("1500000", rows[1][2])
    }
}

