package com.example.utils

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.db.JurnalAkuntansiEntity
import com.example.data.db.PengaturanSistemEntity
import com.example.data.db.SesiTutupTokoEntity
import com.example.data.db.TransaksiGadaiEntity

object PrintHelper {
    @Volatile
    private var activeWebView: WebView? = null

    fun printHtml(context: Context, jobName: String, html: String) {
        val webView = WebView(context)
        activeWebView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
                }
                if (activeWebView === view) {
                    activeWebView = null
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun buildNotaAkadHtml(
        nota: TransaksiGadaiEntity,
        sys: PengaturanSistemEntity,
        thermalMode: Boolean
    ): String {
        val widthCss = if (thermalMode) "max-width:80mm;font-size:11px;" else "max-width:740px;font-size:12.5px;"
        val penebusan = nota.pokokPinjaman + nota.biayaUjrahPeriode
        return """
        <!doctype html>
        <html><head><meta charset="utf-8">
        <style>
          body{font-family:system-ui,sans-serif;margin:16px;color:#111;$widthCss}
          .hdr{text-align:center;border-bottom:1.5px solid #163628;padding-bottom:8px;margin-bottom:10px}
          table{width:100%;border-collapse:collapse}
          td{padding:3px 4px;vertical-align:top}
          .mono{font-family:monospace}
          .note{margin-top:10px;padding:8px;border-top:1px dashed #666;border-bottom:1px dashed #666;font-size:11px}
          .sig{margin-top:18px;display:flex;justify-content:space-between;text-align:center;font-size:11.5px}
          .sig > div{width:44%}
          .line{margin-top:42px;border-top:1px solid #333;padding-top:4px;font-weight:700}
        </style></head><body>
          <div class="hdr">
            <strong>${esc(sys.namaAplikasi)}</strong><br>${esc(sys.alamatKantor)}<br>${esc(sys.kontakKantor)}<br>
            <strong>SURAT BUKTI AKAD RAHN SYARIAH</strong>
          </div>
          <table>
            <tr><td>No. Akad</td><td class="mono">: <strong>${esc(nota.noAkad)}</strong> (${esc(nota.statusGadai)})</td></tr>
            <tr><td>Tgl Akad / Tempo</td><td class="mono">: ${esc(nota.tanggalAkad)} &rarr; <strong>${esc(nota.jatuhTempo)}</strong> (${nota.hariPerPeriode} Hr)</td></tr>
            <tr><td>Kasir Bertugas</td><td>: <strong>${esc(nota.namaKasir)}</strong></td></tr>
            <tr><td>Rahin (Penggadai)</td><td>: <strong>${esc(nota.namaNasabah)}</strong> · NIK: <span class="mono">${esc(nota.nikNasabah)}</span> · HP: <span class="mono">${esc(nota.noHp)}</span></td></tr>
            <tr><td>Alamat Rahin</td><td>: ${esc(nota.alamatNasabah)}</td></tr>
            <tr><td>Marhun (Jaminan)</td><td>: <strong>${esc(nota.kategoriBarang)}</strong> — ${esc(nota.namaBarang)} (${esc(nota.spesifikasiRingkas)})</td></tr>
            <tr><td>Kelengkapan &amp; Kondisi</td><td>: ${esc(nota.kelengkapanBarang)} · ${esc(nota.kondisiBarang)}</td></tr>
            <tr><td>Nilai Taksiran</td><td class="mono">: <strong>${FormatUtils.rupiah(nota.nilaiTaksiran)}</strong> (LTV ${nota.ltvPersen}%)</td></tr>
            <tr><td>Pokok Pinjaman (Qardh)</td><td class="mono">: <strong>${FormatUtils.rupiah(nota.pokokPinjaman)}</strong></td></tr>
            <tr><td>Biaya Administrasi Riil</td><td class="mono">: - ${FormatUtils.rupiah(nota.biayaAdmin)}</td></tr>
            <tr><td><strong>Dana Bersih Diterima</strong></td><td class="mono">: <strong>${FormatUtils.rupiah(nota.danaBersihDiterima)}</strong></td></tr>
            <tr><td>Biaya Penitipan (Ujrah)</td><td class="mono">: <strong>${FormatUtils.rupiah(nota.biayaUjrahPeriode)}</strong> / ${nota.hariPerPeriode} hr (${esc(nota.ujrahPackageNama)})</td></tr>
            <tr><td><strong>Biaya Penebusan</strong></td><td class="mono">: <strong>${FormatUtils.rupiah(penebusan)}</strong></td></tr>
          </table>
          <div class="note">
            Pinjaman murni tanpa bunga/riba (Qardh). Barang jaminan dititipkan secara amanah (yad al-amanah) dan tidak dimanfaatkan oleh penerima gadai.<br>
            <strong>Jika barang dilelang, setelah dikurangi biaya penitipan barang dan kewajiban pokok pinjaman, kelebihan uang akan dikembalikan sepenuhnya kepada pemilik barang.</strong>
          </div>
          <div class="sig">
            <div>Penggadai (Rahin),<div class="line">${esc(nota.namaNasabah)}</div></div>
            <div>Penerima Gadai (Murtahin),<div class="line">${esc(nota.namaKasir)}</div></div>
          </div>
        </body></html>
        """.trimIndent()
    }

    fun buildBeritaAcaraTutupTokoHtml(
        sys: PengaturanSistemEntity,
        tanggal: String,
        sesi: SesiTutupTokoEntity?,
        gadaiBaru: Int,
        perpanjangan: Int,
        tebusan: Int,
        saldoAwal: Double,
        masuk: Double,
        keluar: Double,
        saldoSistem: Double,
        namaKasir: String
    ): String {
        val status = sesi?.status ?: "OPEN"
        val fisikRow = if (sesi != null) {
            """
            <tr><td>Uang Fisik Aktual</td><td class="mono"><strong>${FormatUtils.rupiah(sesi.uangFisikAktual ?: 0.0)}</strong></td></tr>
            <tr><td>Selisih Kas</td><td class="mono"><strong>${FormatUtils.rupiah(sesi.selisihKas ?: 0.0)}</strong> (${esc(sesi.catatanSelisih)})</td></tr>
            """
        } else ""
        return """
        <!doctype html><html><head><meta charset="utf-8">
        <style>
          body{font-family:system-ui,sans-serif;margin:18px;color:#111;font-size:12.5px}
          h3{margin:0 0 4px;color:#163628}
          table{width:100%;border-collapse:collapse;margin-top:10px}
          td{padding:6px 8px;border-bottom:1px solid #d8e2dc}
          .mono{font-family:monospace}
          .sig{margin-top:26px;display:flex;justify-content:space-between;text-align:center}
          .sig > div{width:42%}
          .line{margin-top:46px;border-top:1px solid #333;padding-top:4px;font-weight:700}
        </style></head><body>
          <h3>${esc(sys.namaAplikasi)}</h3>
          <div>${esc(sys.alamatKantor)} · ${esc(sys.kontakKantor)}</div>
          <hr>
          <h4>Berita Acara Tutup Toko Harian — $tanggal ($status)</h4>
          <table>
            <tr><td>Modal Awal Toko (Bersama Antar User)</td><td class="mono">${FormatUtils.rupiah(sys.modalAwal)}</td></tr>
            <tr><td>Barang Masuk (Gadai Baru)</td><td class="mono">$gadaiBaru Akad</td></tr>
            <tr><td>Perpanjangan Akad</td><td class="mono">$perpanjangan Akad</td></tr>
            <tr><td>Barang Keluar (Tebusan)</td><td class="mono">$tebusan Akad</td></tr>
            <tr><td>Saldo Awal Kas (Hari Sebelumnya)</td><td class="mono">${FormatUtils.rupiah(saldoAwal)}</td></tr>
            <tr><td>Total Uang Masuk</td><td class="mono">+${FormatUtils.rupiah(masuk)}</td></tr>
            <tr><td>Total Uang Keluar (Qardh)</td><td class="mono">-${FormatUtils.rupiah(keluar)}</td></tr>
            <tr><td><strong>Saldo Kas Seharusnya (Sistem)</strong></td><td class="mono"><strong>${FormatUtils.rupiah(saldoSistem)}</strong></td></tr>
            $fisikRow
          </table>
          <div class="sig">
            <div>Kasir Bertugas,<div class="line">${esc(sesi?.namaKasir ?: namaKasir)}</div></div>
            <div>Mengetahui Owner,<div class="line">Owner ${esc(sys.namaAplikasi)}</div></div>
          </div>
        </body></html>
        """.trimIndent()
    }

    fun buildTableReportHtml(
        sys: PengaturanSistemEntity,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<String> = emptyList()
    ): String {
        val ths = headers.joinToString("") { "<th>${esc(it)}</th>" }
        val trs = rows.joinToString("") { row ->
            "<tr>" + row.joinToString("") { "<td>${esc(it)}</td>" } + "</tr>"
        }
        val sums = if (summaryLines.isNotEmpty()) {
            "<div style='margin-bottom:10px;padding:8px;background:#f8fbf9;border:1px solid #d8e2dc;'>" +
                summaryLines.joinToString("<br>") { esc(it) } + "</div>"
        } else ""
        return """
        <!doctype html><html><head><meta charset="utf-8">
        <style>
          body{font-family:system-ui,sans-serif;margin:16px;color:#111;font-size:11.5px}
          h3{margin:0 0 2px;color:#163628}
          table{width:100%;border-collapse:collapse;margin-top:8px}
          th{background:#163628;color:#fff;text-align:left;padding:6px;font-size:11px}
          td{padding:5px 6px;border-bottom:1px solid #d8e2dc;vertical-align:top}
        </style></head><body>
          <h3>${esc(sys.namaAplikasi)} — ${esc(title)}</h3>
          <div style="margin-bottom:8px;color:#53665b;">${esc(sys.alamatKantor)} · Dicetak: ${FormatUtils.nowTimestamp()}</div>
          $sums
          <table><thead><tr>$ths</tr></thead><tbody>$trs</tbody></table>
        </body></html>
        """.trimIndent()
    }

    fun buildLaporanOwnerHtml(
        sys: PengaturanSistemEntity,
        netKas: Double,
        modalKeluar: Double,
        pokokKembali: Double,
        pendapatanJasa: Double,
        jurnals: List<JurnalAkuntansiEntity>
    ): String {
        val headers = listOf("Tanggal", "Ref / Akad", "Menu", "Arus", "Pokok", "Admin", "Ujrah", "Total Kas", "Kasir")
        val rows = jurnals.map { j ->
            listOf(
                j.tanggal,
                "${j.noAkad}\n${j.keterangan}",
                j.jenisMenu,
                j.arusKas,
                FormatUtils.rupiah(j.pokokNominal),
                FormatUtils.rupiah(j.pendapatanAdmin),
                FormatUtils.rupiah(j.pendapatanUjrah),
                FormatUtils.rupiah(j.totalKas),
                j.namaKasir
            )
        }
        return buildTableReportHtml(
            sys = sys,
            title = "Laporan Keuangan Owner",
            headers = headers,
            rows = rows,
            summaryLines = listOf(
                "Saldo Kas Filter: ${FormatUtils.rupiah(netKas)} | Modal Awal Sistem: ${FormatUtils.rupiah(sys.modalAwal)}",
                "Modal Keluar (Qardh): ${FormatUtils.rupiah(modalKeluar)} | Pokok Kembali: ${FormatUtils.rupiah(pokokKembali)} | Pendapatan Jasa: ${FormatUtils.rupiah(pendapatanJasa)}"
            )
        )
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
