<?php
require_once "config/koneksi.php";
require_owner();
$page_title = "Lelang Barang";
$cu = current_user();
$ok = ""; $err = ""; $editLelang = null;
$preAkad = trim($_GET['no_akad'] ?? '');

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['cancel_lelang'])) {
    $lid = (int)($_POST['id'] ?? 0);
    $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? AND status_gadai='LELANG' LIMIT 1");
    mysqli_stmt_bind_param($st, "i", $lid); mysqli_stmt_execute($st);
    $tx = mysqli_fetch_assoc(mysqli_stmt_get_result($st)); mysqli_stmt_close($st);
    if ($tx) {
        $newSt = ((int)$tx['jumlah_perpanjangan'] > 0) ? 'DIPERPANJANG' : 'AKTIF';
        $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET status_gadai=?, tanggal_lelang=NULL, harga_lelang=NULL, sisa_dikembalikan_lelang=NULL WHERE id=?");
        mysqli_stmt_bind_param($upd, "si", $newSt, $lid); mysqli_stmt_execute($upd); mysqli_stmt_close($upd);
        $dj = mysqli_prepare($conn, "DELETE FROM jurnal_akuntansi WHERE no_akad=? AND jenis_menu='LELANG_BARANG'");
        mysqli_stmt_bind_param($dj, "s", $tx['no_akad']); mysqli_stmt_execute($dj); mysqli_stmt_close($dj);
        $ok = "Lelang akad <strong>" . e($tx['no_akad']) . "</strong> dibatalkan &amp; status dikembalikan ke $newSt.";
    }
} elseif ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['update_lelang'])) {
    $lid = (int)($_POST['id'] ?? 0);
    $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? AND status_gadai='LELANG' LIMIT 1");
    mysqli_stmt_bind_param($st, "i", $lid); mysqli_stmt_execute($st);
    $tx = mysqli_fetch_assoc(mysqli_stmt_get_result($st)); mysqli_stmt_close($st);
    if ($tx) {
        $tglN = valid_iso_date($_POST['tanggal_lelang'] ?? '') ? $_POST['tanggal_lelang'] : ($tx['tanggal_lelang'] ?: date('Y-m-d'));
        $hargaN = max(1, (float)($_POST['harga_lelang'] ?? $tx['harga_lelang']));
        $kewajiban = (float)$tx['pokok_pinjaman'] + (float)$tx['biaya_ujrah_periode'];
        $sisa_rahin = max(0, $hargaN - $kewajiban);
        $kas_masuk = min($hargaN, $kewajiban);
        $pokok_tutup = min((float)$tx['pokok_pinjaman'], $hargaN);
        $ujrah_tutup = max(0, min((float)$tx['biaya_ujrah_periode'], $hargaN - $pokok_tutup));
        $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET tanggal_lelang=?, harga_lelang=?, sisa_dikembalikan_lelang=? WHERE id=?");
        mysqli_stmt_bind_param($upd, "sddi", $tglN, $hargaN, $sisa_rahin, $lid);
        mysqli_stmt_execute($upd); mysqli_stmt_close($upd);

        $ket = "Lelang {$tx['no_akad']} terjual " . rupiah($hargaN) . " (Sisa ke Rahin: " . rupiah($sisa_rahin) . ")";
        $uj = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET tanggal=?, pokok_nominal=?, pendapatan_ujrah=?, total_kas=?, keterangan=? WHERE no_akad=? AND jenis_menu='LELANG_BARANG'");
        mysqli_stmt_bind_param($uj, "sdddss", $tglN, $pokok_tutup, $ujrah_tutup, $kas_masuk, $ket, $tx['no_akad']);
        mysqli_stmt_execute($uj); mysqli_stmt_close($uj);
        $ok = "Data lelang akad <strong>" . e($tx['no_akad']) . "</strong> diperbarui.";
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['simpan_lelang'])) {
    $idem = $_POST['idempotency_token'] ?? '';
    $tgl = valid_iso_date($_POST['tanggal_lelang'] ?? '') ? $_POST['tanggal_lelang'] : date('Y-m-d');
    $no_akad = trim($_POST['no_akad'] ?? '');
    $harga = max(0, (float)($_POST['harga_lelang'] ?? 0));
    if (!check_and_record_idempotency($conn, $idem)) {
        $err = "Permintaan sudah diproses.";
    } elseif (is_date_closed($conn, $tgl)) {
        $err = "Sesi operasional tanggal $tgl sudah CLOSED.";
    } else {
        $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE no_akad = ? AND status_gadai IN ('AKTIF','DIPERPANJANG') LIMIT 1");
        mysqli_stmt_bind_param($st, "s", $no_akad); mysqli_stmt_execute($st);
        $r = mysqli_fetch_assoc(mysqli_stmt_get_result($st)); mysqli_stmt_close($st);
        if (!$r || $harga <= 0) { $err = "Akad atau harga lelang tidak valid."; }
        else {
            $kewajiban = (float)$r['pokok_pinjaman'] + (float)$r['biaya_ujrah_periode'];
            $sisa_rahin = max(0, $harga - $kewajiban);
            $kas_masuk = min($harga, $kewajiban);
            $pokok_tutup = min((float)$r['pokok_pinjaman'], $harga);
            $ujrah_tutup = max(0, min((float)$r['biaya_ujrah_periode'], $harga - $pokok_tutup));

            $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET status_gadai='LELANG', tanggal_lelang=?, harga_lelang=?, sisa_dikembalikan_lelang=? WHERE id=?");
            mysqli_stmt_bind_param($upd, "sddi", $tgl, $harga, $sisa_rahin, $r['id']);
            mysqli_stmt_execute($upd); mysqli_stmt_close($upd);

            $ket = "Lelang $no_akad terjual " . rupiah($harga) . " (Sisa ke Rahin: " . rupiah($sisa_rahin) . ")";
            $ja = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pokok_nominal, pendapatan_ujrah, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'LELANG_BARANG', 'MASUK', 'Kas Operasional', 'Piutang Qardh & Ujrah', ?, ?, ?, ?, ?, ?)");
            mysqli_stmt_bind_param($ja, "ssdddsis", $tgl, $no_akad, $pokok_tutup, $ujrah_tutup, $kas_masuk, $ket, $cu['id'], $cu['nama_lengkap']);
            mysqli_stmt_execute($ja); mysqli_stmt_close($ja);
            $ok = "Eksekusi lelang $no_akad berhasil. Kelebihan dikembalikan ke Rahin: <strong>" . rupiah($sisa_rahin) . "</strong>.";
        }
    }
}

if (isset($_GET['edit'])) {
    $eid = (int)$_GET['edit'];
    $stE = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? AND status_gadai='LELANG' LIMIT 1");
    mysqli_stmt_bind_param($stE, "i", $eid); mysqli_stmt_execute($stE);
    $editLelang = mysqli_fetch_assoc(mysqli_stmt_get_result($stE)); mysqli_stmt_close($stE);
}

$aktif = mysqli_query($conn, "SELECT * FROM transaksi_gadai WHERE status_gadai IN ('AKTIF','DIPERPANJANG') ORDER BY jatuh_tempo ASC");
$lelang = mysqli_query($conn, "SELECT * FROM transaksi_gadai WHERE status_gadai='LELANG' ORDER BY id DESC");
$idem_val = bin2hex(random_bytes(12));
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok"><?= $ok; ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err"><?= e($err); ?></div><?php endif; ?>

<?php if ($editLelang): ?>
<div class="panel panel-yellow">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 16); ?> Edit Data Lelang: <?= e($editLelang['no_akad']); ?></div><a href="lelang_barang.php" class="btn btn-sm">Batal</a></div>
  <form method="POST" class="form-grid">
    <input type="hidden" name="id" value="<?= (int)$editLelang['id']; ?>">
    <div class="field"><label>Tanggal Lelang</label><input type="date" name="tanggal_lelang" class="mono" value="<?= e($editLelang['tanggal_lelang'] ?: date('Y-m-d')); ?>" required></div>
    <div class="field"><label>Harga Terjual Lelang (Rp)</label><input type="number" name="harga_lelang" class="mono" value="<?= (float)($editLelang['harga_lelang'] ?? 0); ?>" required></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="update_lelang" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Perubahan</button></div>
  </form>
</div>
<?php endif; ?>

<div class="panel panel-yellow">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('gavel', 16); ?> Eksekusi Lelang Jaminan</div><a href="monitoring_tempo.php" class="btn btn-sm"><?= svg_icon('clock', 13); ?> Monitoring Tempo</a></div>
  <form method="POST"><input type="hidden" name="idempotency_token" value="<?= e($idem_val); ?>">
    <div class="form-grid">
      <div class="field"><label>No. Akad</label><select name="no_akad" required><option value="">-- Pilih Akad --</option><?php while($a=mysqli_fetch_assoc($aktif)): ?><option value="<?= e($a['no_akad']); ?>" <?= $preAkad === $a['no_akad'] ? 'selected' : ''; ?>><?= e($a['no_akad']); ?> - <?= e($a['nama_nasabah']); ?> (Kewajiban: <?= rupiah((float)$a['pokok_pinjaman'] + (float)$a['biaya_ujrah_periode']); ?>)</option><?php endwhile; ?></select></div>
      <div class="field"><label>Tanggal Lelang</label><input type="date" name="tanggal_lelang" class="mono" value="<?= date('Y-m-d'); ?>" required></div>
      <div class="field"><label>Harga Terjual Lelang (Rp)</label><input type="number" name="harga_lelang" class="mono" required></div>
    </div>
    <div style="margin-top:12px;"><button type="submit" name="simpan_lelang" class="btn btn-primary"><?= svg_icon('gavel', 14); ?> Simpan Lelang</button></div>
  </form>
</div>
<div class="panel"><div class="table-box"><table><thead><tr><th>No. Akad</th><th>Nasabah</th><th>Barang</th><th>Harga Lelang</th><th>Kelebihan ke Rahin</th><th>Aksi</th></tr></thead><tbody>
<?php while($r=mysqli_fetch_assoc($lelang)): ?>
<tr><td class="mono"><a href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['no_akad']); ?></strong></a><div class="cell-sub"><?= e($r['tanggal_lelang'] ?? ''); ?></div></td><td><a href="data_pelanggan.php?q=<?= urlencode($r['nama_nasabah']); ?>"><?= e($r['nama_nasabah']); ?></a></td><td><?= e($r['nama_barang']); ?></td><td class="mono"><?= rupiah($r['harga_lelang']); ?></td><td class="mono"><?= rupiah($r['sisa_dikembalikan_lelang']); ?></td>
<td><div style="display:flex;gap:5px;"><a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('printer', 13); ?> Nota</a><a class="btn btn-sm" href="lelang_barang.php?edit=<?= (int)$r['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><form method="POST" style="display:inline;" onsubmit="return confirm('Batalkan lelang akad <?= e($r['no_akad']); ?>?')"><input type="hidden" name="id" value="<?= (int)$r['id']; ?>"><button type="submit" name="cancel_lelang" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Batalkan</button></form></div></td></tr>
<?php endwhile; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
