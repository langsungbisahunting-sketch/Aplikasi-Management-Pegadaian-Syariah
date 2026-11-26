<?php
require_once "config/koneksi.php";
require_owner();
$page_title = "Dashboard Owner";
$aktif = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(SUM(pokok_pinjaman),0) AS p FROM transaksi_gadai WHERE status_gadai IN ('AKTIF','DIPERPANJANG')"));
$kas = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COALESCE(SUM(CASE WHEN arus_kas='MASUK' THEN total_kas ELSE 0 END),0) AS m, COALESCE(SUM(CASE WHEN arus_kas='KELUAR' THEN total_kas ELSE 0 END),0) AS k, COALESCE(SUM(pendapatan_admin+pendapatan_ujrah),0) AS j FROM jurnal_akuntansi"));
$lewat = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c FROM transaksi_gadai WHERE status_gadai IN ('AKTIF','DIPERPANJANG') AND jatuh_tempo < CURDATE()"));
$rows = mysqli_query($conn, "SELECT * FROM transaksi_gadai ORDER BY id DESC LIMIT 10");
$dokMap = get_all_dokumen_map($conn);
require_once "includes/header.php";
?>
<div class="stats-row">
  <div class="panel">
    <div class="stat-title">Akad Aktif &amp; Diperpanjang</div>
    <div class="stat-num"><?= (int)$aktif['c']; ?> Akad</div>
    <div class="cell-sub mono">Pokok Beredar: <?= rupiah($aktif['p']); ?></div>
    <div class="cell-sub" style="margin-top:4px;"><a href="data_barang.php?status=AKTIF">Lihat Barang Aktif &rarr;</a></div>
  </div>
  <div class="panel panel-yellow">
    <div class="stat-title">Lewat Jatuh Tempo</div>
    <div class="stat-num"><?= (int)$lewat['c']; ?> Akad</div>
    <div class="cell-sub"><a href="monitoring_tempo.php">Buka Monitoring Tempo &rarr;</a></div>
  </div>
  <div class="panel">
    <div class="stat-title">Saldo Kas Riil (Modal: <?= rupiah(MODAL_AWAL_SISTEM); ?>)</div>
    <div class="stat-num"><?= rupiah((float)$kas['m'] - (float)$kas['k']); ?></div>
    <div class="cell-sub mono">Pendapatan Jasa: <a href="laporan_akuntansi.php"><?= rupiah($kas['j']); ?></a> &middot; <a href="pengaturan_rumus.php#identitas-modal">Atur Modal &rarr;</a></div>
  </div>
</div>
<div class="panel" id="transaksi-terbaru">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('file-text', 16); ?> Transaksi Gadai Terbaru</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="gadai_barang.php" class="btn btn-sm btn-primary"><?= svg_icon('plus-circle', 14); ?> Gadai Baru</a>
      <a href="laporan_akuntansi.php" class="btn btn-sm"><?= svg_icon('file-text', 14); ?> Semua Jurnal</a>
    </div>
  </div>
  <div class="table-box">
    <table>
      <thead><tr><th>No. Akad</th><th>Nasabah</th><th>Barang (Marhun)</th><th>Pokok Qardh</th><th>Ujrah / Periode</th><th>Kasir</th><th>Status</th><th>Aksi Cepat</th></tr></thead>
      <tbody>
      <?php while($r = mysqli_fetch_assoc($rows)):
        $dRow = $dokMap[$r['nik_nasabah']] ?? null;
      ?>
        <tr>
          <td class="mono"><a href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['no_akad']); ?></strong></a><div class="cell-sub"><?= e($r['tanggal_akad']); ?></div></td>
          <td><a href="data_pelanggan.php?q=<?= urlencode($r['nama_nasabah']); ?>"><?= e($r['nama_nasabah']); ?></a><div class="cell-sub mono"><?= e($r['no_hp']); ?> &middot; <?php if($dRow): ?><a href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($r['nik_nasabah']); ?>" style="color:#065f46;font-weight:600;">PDF Ada</a><?php else: ?><a href="dokumen_pelanggan.php?nik=<?= urlencode($r['nik_nasabah']); ?>">Upload PDF</a><?php endif; ?></div></td>
          <td><a href="data_barang.php?q=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['kategori_barang']); ?></strong> - <?= e($r['nama_barang']); ?></a></td>
          <td class="mono"><?= rupiah($r['pokok_pinjaman']); ?></td>
          <td class="mono"><?= rupiah($r['biaya_ujrah_periode']); ?></td>
          <td><a href="laporan_akuntansi.php?user_id=<?= (int)$r['user_id']; ?>"><?= e($r['nama_kasir']); ?></a></td>
          <td><?= badge_status($r['status_gadai']); ?></td>
          <td>
            <div style="display:flex;gap:4px;flex-wrap:wrap;">
              <a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('printer', 12); ?> Nota</a>
              <?php if (in_array($r['status_gadai'], ['AKTIF','DIPERPANJANG'], true)): ?>
                <a class="btn btn-sm" href="perpanjang_gadai.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('refresh', 12); ?> Perpanjang</a>
                <a class="btn btn-sm" href="tebus_barang.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('check-circle', 12); ?> Tebus</a>
              <?php endif; ?>
            </div>
          </td>
        </tr>
      <?php endwhile; ?>
      </tbody>
    </table>
  </div>
</div>
<?php require_once "includes/footer.php"; ?>
