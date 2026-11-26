<?php
require_once "config/koneksi.php";
require_login();
$page_title = "Tutup Toko Operasional";
$cu = current_user();
$ok = ""; $err = "";
$tgl = valid_iso_date($_POST['tanggal'] ?? $_GET['tanggal'] ?? '') ? ($_POST['tanggal'] ?? $_GET['tanggal']) : date('Y-m-d');
$etgl = mysqli_real_escape_string($conn, $tgl);

$q_in = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c FROM transaksi_gadai WHERE tanggal_akad = '$etgl'"));
$q_perp = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c FROM riwayat_perpanjangan WHERE tanggal_perpanjang = '$etgl'"));
$q_teb = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c FROM transaksi_gadai WHERE status_gadai='LUNAS' AND tanggal_tebus = '$etgl'"));
$q_j = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COALESCE(SUM(CASE WHEN arus_kas='MASUK' THEN total_kas ELSE 0 END),0) AS m, COALESCE(SUM(CASE WHEN arus_kas='KELUAR' THEN total_kas ELSE 0 END),0) AS k FROM jurnal_akuntansi WHERE tanggal = '$etgl'"));
$q_prev = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COALESCE(SUM(CASE WHEN arus_kas='MASUK' THEN total_kas ELSE 0 END),0) - COALESCE(SUM(CASE WHEN arus_kas='KELUAR' THEN total_kas ELSE 0 END),0) AS s FROM jurnal_akuntansi WHERE tanggal < '$etgl'"));

$saldo_awal = (float)($q_prev['s'] ?? 0);
$masuk = (float)($q_j['m'] ?? 0);
$keluar = (float)($q_j['k'] ?? 0);
$saldo_sistem = $saldo_awal + $masuk - $keluar;

if (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['update_sesi'])) {
    $sid = (int)($_POST['id'] ?? 0);
    $stS = mysqli_prepare($conn, "SELECT * FROM sesi_tutup_toko WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stS, "i", $sid); mysqli_stmt_execute($stS);
    $sRow = mysqli_fetch_assoc(mysqli_stmt_get_result($stS)); mysqli_stmt_close($stS);
    if ($sRow) {
        $fisik = (float)($_POST['uang_fisik_aktual'] ?? 0);
        $cat = trim($_POST['catatan_selisih'] ?? '');
        $selisih = $fisik - (float)$sRow['saldo_kas_sistem'];
        $up = mysqli_prepare($conn, "UPDATE sesi_tutup_toko SET uang_fisik_aktual=?, selisih_kas=?, catatan_selisih=? WHERE id=?");
        mysqli_stmt_bind_param($up, "ddsi", $fisik, $selisih, $cat, $sid);
        mysqli_stmt_execute($up); mysqli_stmt_close($up);
        $ok = "Uang fisik &amp; catatan selisih tutup toko tanggal <strong>" . e($sRow['tanggal']) . "</strong> diperbarui.";
    }
} elseif (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['delete_sesi'])) {
    $sid = (int)($_POST['id'] ?? 0);
    mysqli_query($conn, "DELETE FROM sesi_tutup_toko WHERE id=$sid");
    $ok = "Sesi tutup toko dibuka kembali (status OPEN).";
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['kunci_tutup_toko'])) {
    $idem = $_POST['idempotency_token'] ?? '';
    $fisik = (float)($_POST['uang_fisik_aktual'] ?? 0);
    $cat = trim($_POST['catatan_selisih'] ?? '');
    $selisih = $fisik - $saldo_sistem;
    if (!check_and_record_idempotency($conn, $idem)) {
        $err = "Sesi sudah diproses.";
    } elseif (is_date_closed($conn, $tgl)) {
        $err = "Sesi tanggal $tgl sudah CLOSED / TERKUNCI.";
    } elseif ($selisih != 0 && $cat === '') {
        $err = "Terdapat selisih kas sebesar " . rupiah(abs($selisih)) . ". Wajib isi keterangan selisih.";
    } else {
        $ins = mysqli_prepare($conn, "INSERT INTO sesi_tutup_toko (tanggal, status, saldo_awal_kas, jumlah_gadai_baru, jumlah_perpanjangan, jumlah_tebusan, total_uang_masuk, total_uang_keluar, saldo_kas_sistem, uang_fisik_aktual, selisih_kas, catatan_selisih, kasir_id, nama_kasir, closed_at) VALUES (?, 'CLOSED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())");
        $c1 = (int)$q_in['c']; $c2 = (int)$q_perp['c']; $c3 = (int)$q_teb['c'];
        mysqli_stmt_bind_param($ins, "sdiiidddddsis", $tgl, $saldo_awal, $c1, $c2, $c3, $masuk, $keluar, $saldo_sistem, $fisik, $selisih, $cat, $cu['id'], $cu['nama_lengkap']);
        if (mysqli_stmt_execute($ins)) {
            $ok = "Tutup Toko tanggal <strong>$tgl</strong> berhasil dikunci (CLOSED).";
        } else {
            $err = "Gagal menutup toko: " . mysqli_stmt_error($ins);
        }
        mysqli_stmt_close($ins);
    }
}

$cur_sesi = mysqli_fetch_assoc(mysqli_query($conn, "SELECT * FROM sesi_tutup_toko WHERE tanggal = '$etgl' LIMIT 1"));
$riwayat = mysqli_query($conn, "SELECT * FROM sesi_tutup_toko ORDER BY tanggal DESC LIMIT 30");
$idem_val = bin2hex(random_bytes(12));
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok no-print"><?= $ok; ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err no-print"><?= e($err); ?></div><?php endif; ?>

<div class="panel">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('lock', 16); ?> Berita Acara Tutup Toko Harian — <?= e($tgl); ?> (<?= $cur_sesi ? e($cur_sesi['status']) : 'OPEN'; ?>)</div>
    <div class="no-print" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
      <form method="GET" style="display:flex;gap:6px;align-items:center;">
        <input type="date" name="tanggal" value="<?= e($tgl); ?>" class="mono" style="padding:5px 8px;border:1px solid #c6d3cb;border-radius:6px;">
        <button type="submit" class="btn btn-sm">Lihat Tanggal</button>
      </form>
      <?php if (is_owner()): ?>
        <a href="laporan_akuntansi.php?tgl_awal=<?= urlencode($tgl); ?>&tgl_akhir=<?= urlencode($tgl); ?>" class="btn btn-sm"><?= svg_icon('file-text', 13); ?> Jurnal Hari Ini</a>
      <?php endif; ?>
      <button type="button" class="btn btn-sm btn-primary" onclick="window.print()"><?= svg_icon('printer', 14); ?> Cetak Berita Acara</button>
    </div>
  </div>
  <table>
    <tr><td>Modal Awal Toko (Bersama Antar User)</td><td class="mono"><?= rupiah(MODAL_AWAL_SISTEM); ?> <?php if(is_owner()): ?><a href="pengaturan_rumus.php#identitas-modal" class="no-print" style="font-size:11.5px;">(Ubah Modal)</a><?php endif; ?></td></tr>
    <tr><td>Barang Masuk (Gadai Baru)</td><td class="mono"><a href="gadai_barang.php"><?= (int)$q_in['c']; ?> Akad</a></td></tr>
    <tr><td>Perpanjangan Akad</td><td class="mono"><a href="perpanjang_gadai.php"><?= (int)$q_perp['c']; ?> Akad</a></td></tr>
    <tr><td>Barang Keluar (Tebusan)</td><td class="mono"><a href="tebus_barang.php"><?= (int)$q_teb['c']; ?> Akad</a></td></tr>
    <tr><td>Saldo Awal Kas (Hari Sebelumnya)</td><td class="mono"><?= rupiah($saldo_awal); ?></td></tr>
    <tr><td>Total Uang Masuk</td><td class="mono">+<?= rupiah($masuk); ?></td></tr>
    <tr><td>Total Uang Keluar (Qardh)</td><td class="mono">-<?= rupiah($keluar); ?></td></tr>
    <tr><td><strong>Saldo Kas Seharusnya (Sistem)</strong></td><td class="mono"><strong><?= rupiah($saldo_sistem); ?></strong></td></tr>
    <?php if ($cur_sesi): ?>
      <tr><td>Uang Fisik Aktual</td><td class="mono"><strong><?= rupiah($cur_sesi['uang_fisik_aktual']); ?></strong></td></tr>
      <tr><td>Selisih Kas</td><td class="mono"><strong><?= rupiah($cur_sesi['selisih_kas']); ?></strong> (<?= e($cur_sesi['catatan_selisih']); ?>)</td></tr>
    <?php endif; ?>
  </table>

  <?php if (!$cur_sesi): ?>
  <form method="POST" class="no-print" style="margin-top:14px;" onsubmit="const b=this.querySelector('button[type=submit]');if(b){setTimeout(()=>{b.disabled=true;},0);}">
    <input type="hidden" name="kunci_tutup_toko" value="1">
    <input type="hidden" name="idempotency_token" value="<?= e($idem_val); ?>">
    <input type="hidden" name="tanggal" value="<?= e($tgl); ?>">
    <div class="form-grid">
      <div class="field"><label>Uang Fisik Aktual Laci Kasir (Rp)</label><input type="number" name="uang_fisik_aktual" class="mono" value="<?= (int)$saldo_sistem; ?>" required></div>
      <div class="field"><label>Catatan Keterangan Selisih</label><input type="text" name="catatan_selisih" placeholder="Cocok / alasan selisih"></div>
    </div>
    <div style="margin-top:12px;"><button type="submit" name="kunci_tutup_toko" class="btn btn-primary"><?= svg_icon('lock', 14); ?> Selesaikan &amp; Kunci Tutup Toko</button></div>
  </form>
  <?php elseif (is_owner()): ?>
  <div class="panel panel-yellow no-print" style="margin-top:14px;">
    <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 15); ?> Kontrol Owner: Edit Fisik / Buka Kunci Tanggal <?= e($tgl); ?></div></div>
    <form method="POST" class="form-grid">
      <input type="hidden" name="id" value="<?= (int)$cur_sesi['id']; ?>">
      <div class="field"><label>Revisi Uang Fisik Aktual (Rp)</label><input type="number" name="uang_fisik_aktual" class="mono" value="<?= (float)$cur_sesi['uang_fisik_aktual']; ?>" required></div>
      <div class="field"><label>Revisi Catatan Selisih</label><input type="text" name="catatan_selisih" value="<?= e($cur_sesi['catatan_selisih']); ?>"></div>
      <div class="field" style="justify-content:flex-end;display:flex;flex-direction:row;gap:8px;align-items:flex-end;">
        <button type="submit" name="update_sesi" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Koreksi</button>
      </div>
    </form>
    <form method="POST" style="margin-top:8px;" onsubmit="return confirm('Buka kunci sesi tanggal <?= e($tgl); ?>?')">
      <input type="hidden" name="id" value="<?= (int)$cur_sesi['id']; ?>">
      <button type="submit" name="delete_sesi" class="btn btn-sm btn-yellow"><?= svg_icon('unlock', 13); ?> Buka Kunci Sesi (OPEN)</button>
    </form>
  </div>
  <?php endif; ?>

  <div style="margin-top:24px;display:grid;grid-template-columns:1fr 1fr;gap:32px;text-align:center;">
    <div>Kasir Bertugas,<div style="margin-top:46px;border-top:1px solid #333;padding-top:4px;font-weight:700;"><?= e($cur_sesi ? $cur_sesi['nama_kasir'] : $cu['nama_lengkap']); ?></div></div>
    <div>Mengetahui Owner,<div style="margin-top:46px;border-top:1px solid #333;padding-top:4px;font-weight:700;">Owner <?= e(NAMA_APLIKASI); ?></div></div>
  </div>
</div>

<div class="panel no-print"><div class="panel-head"><div class="panel-title">Log Tutup Toko Harian</div></div>
<div class="table-box"><table><thead><tr><th>Tanggal</th><th>Status</th><th>Saldo Sistem</th><th>Fisik Aktual</th><th>Selisih</th><th>Kasir</th><?php if(is_owner()): ?><th>Aksi</th><?php endif; ?></tr></thead><tbody>
<?php while($h=mysqli_fetch_assoc($riwayat)): ?>
<tr><td class="mono"><a href="tutup_toko.php?tanggal=<?= urlencode($h['tanggal']); ?>"><?= e($h['tanggal']); ?></a></td><td><?= e($h['status']); ?></td><td class="mono"><?= rupiah($h['saldo_kas_sistem']); ?></td><td class="mono"><?= rupiah($h['uang_fisik_aktual']); ?></td><td class="mono"><?= rupiah($h['selisih_kas']); ?></td><td><?php if(is_owner()): ?><a href="laporan_akuntansi.php?user_id=<?= (int)$h['kasir_id']; ?>"><?= e($h['nama_kasir']); ?></a><?php else: ?><?= e($h['nama_kasir']); ?><?php endif; ?></td>
<?php if(is_owner()): ?><td><div style="display:flex;gap:5px;"><a class="btn btn-sm" href="tutup_toko.php?tanggal=<?= urlencode($h['tanggal']); ?>"><?= svg_icon('edit', 13); ?> Kelola</a><form method="POST" style="display:inline;" onsubmit="return confirm('Buka kunci tanggal <?= e($h['tanggal']); ?>?')"><input type="hidden" name="id" value="<?= (int)$h['id']; ?>"><button type="submit" name="delete_sesi" class="btn btn-sm btn-yellow"><?= svg_icon('unlock', 13); ?> Buka Kunci</button></form></div></td><?php endif; ?></tr>
<?php endwhile; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
