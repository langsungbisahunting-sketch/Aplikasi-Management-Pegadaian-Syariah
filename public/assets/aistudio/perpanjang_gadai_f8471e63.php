<?php
require_once "config/koneksi.php";
require_login();
$page_title = "Perpanjang Gadai";
$cu = current_user();
$ok = ""; $err = ""; $editPerp = null;
$preAkad = trim($_GET['no_akad'] ?? '');

if (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['delete_perpanjang'])) {
    $pid = (int)($_POST['id'] ?? 0);
    $stP = mysqli_prepare($conn, "SELECT * FROM riwayat_perpanjangan WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stP, "i", $pid); mysqli_stmt_execute($stP);
    $rpRow = mysqli_fetch_assoc(mysqli_stmt_get_result($stP)); mysqli_stmt_close($stP);
    if ($rpRow) {
        $nak = $rpRow['no_akad'];
        $tglP = $rpRow['tanggal_perpanjang'];
        mysqli_query($conn, "DELETE FROM riwayat_perpanjangan WHERE id=$pid");
        $dj = mysqli_prepare($conn, "DELETE FROM jurnal_akuntansi WHERE no_akad=? AND jenis_menu='PERPANJANG_GADAI' AND tanggal=? LIMIT 1");
        mysqli_stmt_bind_param($dj, "ss", $nak, $tglP); mysqli_stmt_execute($dj); mysqli_stmt_close($dj);

        $stT = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE no_akad=? LIMIT 1");
        mysqli_stmt_bind_param($stT, "s", $nak); mysqli_stmt_execute($stT);
        $tx = mysqli_fetch_assoc(mysqli_stmt_get_result($stT)); mysqli_stmt_close($stT);
        if ($tx) {
            $agg = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS cnt, COALESCE(SUM(biaya_ujrah_dibayar),0) AS tot, MAX(jatuh_tempo_baru) AS max_jt, MAX(tanggal_perpanjang) AS max_tgl FROM riwayat_perpanjangan WHERE no_akad='" . mysqli_real_escape_string($conn, $nak) . "'"));
            $cnt = (int)$agg['cnt'];
            $totU = (float)$agg['tot'] + (float)($tx['ujrah_tebus_dibayar'] ?? 0);
            $newJt = $cnt > 0 && $agg['max_jt'] ? $agg['max_jt'] : date('Y-m-d', strtotime($tx['tanggal_akad'] . ' +' . max(1, (int)$tx['hari_per_periode']) . ' days'));
            $newTglTerakhir = $cnt > 0 ? $agg['max_tgl'] : null;
            $newStatus = ($tx['status_gadai'] === 'DIPERPANJANG' && $cnt === 0) ? 'AKTIF' : $tx['status_gadai'];
            $upT = mysqli_prepare($conn, "UPDATE transaksi_gadai SET jatuh_tempo=?, total_ujrah_dibayar=?, jumlah_perpanjangan=?, tanggal_perpanjang_terakhir=?, status_gadai=? WHERE id=?");
            mysqli_stmt_bind_param($upT, "sdissi", $newJt, $totU, $cnt, $newTglTerakhir, $newStatus, $tx['id']);
            mysqli_stmt_execute($upT); mysqli_stmt_close($upT);
        }
        $ok = "Riwayat perpanjangan berhasil dihapus &amp; tempo akad disinkronkan ulang.";
    }
} elseif (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['update_perpanjang'])) {
    $pid = (int)($_POST['id'] ?? 0);
    $stP = mysqli_prepare($conn, "SELECT * FROM riwayat_perpanjangan WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stP, "i", $pid); mysqli_stmt_execute($stP);
    $rpRow = mysqli_fetch_assoc(mysqli_stmt_get_result($stP)); mysqli_stmt_close($stP);
    if ($rpRow) {
        $tglN = valid_iso_date($_POST['tanggal_perpanjang'] ?? '') ? $_POST['tanggal_perpanjang'] : $rpRow['tanggal_perpanjang'];
        $jtLama = valid_iso_date($_POST['jatuh_tempo_lama'] ?? '') ? $_POST['jatuh_tempo_lama'] : $rpRow['jatuh_tempo_lama'];
        $jtBaru = valid_iso_date($_POST['jatuh_tempo_baru'] ?? '') ? $_POST['jatuh_tempo_baru'] : $rpRow['jatuh_tempo_baru'];
        $perN = max(1, (int)($_POST['jumlah_periode'] ?? $rpRow['jumlah_periode']));
        $ujrN = max(0, (float)($_POST['biaya_ujrah_dibayar'] ?? $rpRow['biaya_ujrah_dibayar']));
        $upP = mysqli_prepare($conn, "UPDATE riwayat_perpanjangan SET tanggal_perpanjang=?, jatuh_tempo_lama=?, jatuh_tempo_baru=?, jumlah_periode=?, biaya_ujrah_dibayar=? WHERE id=?");
        mysqli_stmt_bind_param($upP, "sssidi", $tglN, $jtLama, $jtBaru, $perN, $ujrN, $pid);
        mysqli_stmt_execute($upP); mysqli_stmt_close($upP);

        $nak = $rpRow['no_akad'];
        $ket = "Perpanjangan Ujrah $nak ($jtLama -> $jtBaru)";
        $uj = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET tanggal=?, pendapatan_ujrah=?, total_kas=?, keterangan=? WHERE no_akad=? AND jenis_menu='PERPANJANG_GADAI' AND tanggal=? LIMIT 1");
        mysqli_stmt_bind_param($uj, "sddsss", $tglN, $ujrN, $ujrN, $ket, $nak, $rpRow['tanggal_perpanjang']);
        mysqli_stmt_execute($uj); mysqli_stmt_close($uj);

        $stT = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE no_akad=? LIMIT 1");
        mysqli_stmt_bind_param($stT, "s", $nak); mysqli_stmt_execute($stT);
        $tx = mysqli_fetch_assoc(mysqli_stmt_get_result($stT)); mysqli_stmt_close($stT);
        if ($tx) {
            $agg = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS cnt, COALESCE(SUM(biaya_ujrah_dibayar),0) AS tot, MAX(jatuh_tempo_baru) AS max_jt, MAX(tanggal_perpanjang) AS max_tgl FROM riwayat_perpanjangan WHERE no_akad='" . mysqli_real_escape_string($conn, $nak) . "'"));
            $totU = (float)$agg['tot'] + (float)($tx['ujrah_tebus_dibayar'] ?? 0);
            $upT = mysqli_prepare($conn, "UPDATE transaksi_gadai SET jatuh_tempo=?, total_ujrah_dibayar=?, tanggal_perpanjang_terakhir=? WHERE id=?");
            mysqli_stmt_bind_param($upT, "sdsi", $agg['max_jt'], $totU, $agg['max_tgl'], $tx['id']);
            mysqli_stmt_execute($upT); mysqli_stmt_close($upT);
        }
        $ok = "Perubahan perpanjangan akad <strong>" . e($nak) . "</strong> berhasil disimpan &amp; disinkronkan.";
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['simpan_perpanjang'])) {
    $idem = $_POST['idempotency_token'] ?? '';
    $tgl = valid_iso_date($_POST['tanggal_perpanjang'] ?? '') ? $_POST['tanggal_perpanjang'] : date('Y-m-d');
    $no_akad = trim($_POST['no_akad'] ?? '');
    $per = max(1, (int)($_POST['jumlah_periode'] ?? 1));
    if (!check_and_record_idempotency($conn, $idem)) {
        $err = "Permintaan sudah diproses (pencegahan double submit).";
    } elseif (is_date_closed($conn, $tgl)) {
        $err = "Sesi operasional tanggal $tgl sudah DITUTUP & TERKUNCI.";
    } else {
        $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE no_akad = ? AND status_gadai IN ('AKTIF','DIPERPANJANG') LIMIT 1");
        mysqli_stmt_bind_param($st, "s", $no_akad); mysqli_stmt_execute($st);
        $r = mysqli_fetch_assoc(mysqli_stmt_get_result($st)); mysqli_stmt_close($st);
        if (!$r) { $err = "Akad aktif tidak ditemukan."; }
        else {
            $ujrah = (float)$r['biaya_ujrah_periode'] * $per;
            $hari = max(1, (int)$r['hari_per_periode']) * $per;
            $jt_lama = $r['jatuh_tempo'];
            $jt_baru = date('Y-m-d', strtotime("$jt_lama +{$hari} days"));
            $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET jatuh_tempo=?, total_ujrah_dibayar=total_ujrah_dibayar+?, jumlah_perpanjangan=jumlah_perpanjangan+1, tanggal_perpanjang_terakhir=?, status_gadai='DIPERPANJANG' WHERE id=?");
            mysqli_stmt_bind_param($upd, "sdsi", $jt_baru, $ujrah, $tgl, $r['id']);
            mysqli_stmt_execute($upd); mysqli_stmt_close($upd);

            $rp = mysqli_prepare($conn, "INSERT INTO riwayat_perpanjangan (no_akad, tanggal_perpanjang, jatuh_tempo_lama, jatuh_tempo_baru, jumlah_periode, biaya_ujrah_dibayar, user_id, nama_kasir) VALUES (?,?,?,?,?,?,?,?)");
            mysqli_stmt_bind_param($rp, "ssssidis", $no_akad, $tgl, $jt_lama, $jt_baru, $per, $ujrah, $cu['id'], $cu['nama_lengkap']);
            mysqli_stmt_execute($rp); mysqli_stmt_close($rp);

            $ket = "Perpanjangan Ujrah $no_akad ($jt_lama -> $jt_baru)";
            $ja = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pendapatan_ujrah, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'PERPANJANG_GADAI', 'MASUK', 'Kas Operasional', 'Pendapatan Jasa Ujrah', ?, ?, ?, ?, ?)");
            mysqli_stmt_bind_param($ja, "ssddsis", $tgl, $no_akad, $ujrah, $ujrah, $ket, $cu['id'], $cu['nama_lengkap']);
            mysqli_stmt_execute($ja); mysqli_stmt_close($ja);
            $ok = "Perpanjangan akad <strong>" . e($no_akad) . "</strong> berhasil. Tempo baru: <strong>" . e($jt_baru) . "</strong>.";
        }
    }
}

if (is_owner() && isset($_GET['edit'])) {
    $eid = (int)$_GET['edit'];
    $stE = mysqli_prepare($conn, "SELECT * FROM riwayat_perpanjangan WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stE, "i", $eid); mysqli_stmt_execute($stE);
    $editPerp = mysqli_fetch_assoc(mysqli_stmt_get_result($stE)); mysqli_stmt_close($stE);
}

$aktif = mysqli_query($conn, "SELECT * FROM transaksi_gadai WHERE status_gadai IN ('AKTIF','DIPERPANJANG') ORDER BY jatuh_tempo ASC");
$riw = mysqli_query($conn, "SELECT * FROM riwayat_perpanjangan ORDER BY id DESC LIMIT 30");
$idem_val = bin2hex(random_bytes(12));
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok"><?= $ok; ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err"><?= e($err); ?></div><?php endif; ?>

<?php if ($editPerp && is_owner()): ?>
<div class="panel panel-yellow">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 16); ?> Edit Perpanjangan: <?= e($editPerp['no_akad']); ?></div><a href="perpanjang_gadai.php" class="btn btn-sm">Batal</a></div>
  <form method="POST" class="form-grid">
    <input type="hidden" name="id" value="<?= (int)$editPerp['id']; ?>">
    <div class="field"><label>Tanggal Perpanjang</label><input type="date" name="tanggal_perpanjang" class="mono" value="<?= e($editPerp['tanggal_perpanjang']); ?>" required></div>
    <div class="field"><label>Jatuh Tempo Lama</label><input type="date" name="jatuh_tempo_lama" class="mono" value="<?= e($editPerp['jatuh_tempo_lama']); ?>" required></div>
    <div class="field"><label>Jatuh Tempo Baru</label><input type="date" name="jatuh_tempo_baru" class="mono" value="<?= e($editPerp['jatuh_tempo_baru']); ?>" required></div>
    <div class="field"><label>Jumlah Periode</label><input type="number" min="1" name="jumlah_periode" class="mono" value="<?= (int)$editPerp['jumlah_periode']; ?>" required></div>
    <div class="field"><label>Ujrah Dibayar (Rp)</label><input type="number" name="biaya_ujrah_dibayar" class="mono" value="<?= (float)$editPerp['biaya_ujrah_dibayar']; ?>" required></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="update_perpanjang" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Perubahan</button></div>
  </form>
</div>
<?php endif; ?>

<div class="panel panel-yellow">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('refresh', 16); ?> Perpanjangan Akad Rahn (Ujrah Penitipan)</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="gadai_barang.php" class="btn btn-sm"><?= svg_icon('plus-circle', 13); ?> Gadai Baru</a>
      <a href="tebus_barang.php" class="btn btn-sm"><?= svg_icon('check-circle', 13); ?> Tebus Barang</a>
    </div>
  </div>
  <form method="POST" onsubmit="const b=this.querySelector('button[type=submit]');if(b){setTimeout(()=>{b.disabled=true;},0);}">
    <input type="hidden" name="simpan_perpanjang" value="1">
    <input type="hidden" name="idempotency_token" value="<?= e($idem_val); ?>">
    <div class="form-grid">
      <div class="field"><label>No. Akad Aktif</label><select name="no_akad" id="perpAkad" onchange="calcPerp()" required>
        <option value="">-- Pilih Akad --</option>
        <?php while ($a = mysqli_fetch_assoc($aktif)): ?>
          <option value="<?= e($a['no_akad']); ?>" data-ujrah="<?= (float)$a['biaya_ujrah_periode']; ?>" data-hari="<?= (int)$a['hari_per_periode']; ?>" data-jt="<?= e($a['jatuh_tempo']); ?>" <?= $preAkad === $a['no_akad'] ? 'selected' : ''; ?>><?= e($a['no_akad']); ?> | <?= e($a['nama_nasabah']); ?> | Tempo: <?= e($a['jatuh_tempo']); ?> | Ujrah: <?= rupiah($a['biaya_ujrah_periode']); ?></option>
        <?php endwhile; ?>
      </select></div>
      <div class="field"><label>Tanggal Perpanjang</label><input type="date" name="tanggal_perpanjang" class="mono" value="<?= date('Y-m-d'); ?>" required></div>
      <div class="field"><label>Jumlah Periode</label><input type="number" min="1" name="jumlah_periode" id="perpPer" class="mono" value="1" oninput="calcPerp()" required></div>
      <div class="field"><label>Estimasi Tempo Baru</label><input type="text" id="outPerpJt" readonly class="mono"></div>
      <div class="field"><label>Total Ujrah Dibayar</label><input type="text" id="outPerpUjr" readonly class="mono"></div>
    </div>
    <div style="margin-top:12px;"><button type="submit" name="simpan_perpanjang" class="btn btn-primary"><?= svg_icon('refresh', 14); ?> Simpan Perpanjangan</button></div>
  </form>
</div>
<script>
function calcPerp(){
  const sel = document.getElementById('perpAkad');
  const opt = sel && sel.selectedOptions[0];
  const per = Math.max(1, parseInt(document.getElementById('perpPer').value || '1', 10));
  if (!opt || !opt.value) {
    document.getElementById('outPerpJt').value = '-';
    document.getElementById('outPerpUjr').value = 'Rp 0';
    return;
  }
  const ujr = parseFloat(opt.getAttribute('data-ujrah') || '0');
  const hari = Math.max(1, parseInt(opt.getAttribute('data-hari') || '30', 10)) * per;
  const jt = opt.getAttribute('data-jt');
  if (jt) {
    const d = new Date(jt + 'T00:00:00');
    d.setDate(d.getDate() + hari);
    document.getElementById('outPerpJt').value = d.toISOString().slice(0, 10);
  }
  document.getElementById('outPerpUjr').value = 'Rp ' + Math.round(ujr * per).toLocaleString('id-ID');
}
calcPerp();
</script>
<div class="panel"><div class="panel-head"><div class="panel-title"><?= svg_icon('file-text', 16); ?> Riwayat Perpanjangan</div></div>
<div class="table-box"><table><thead><tr><th>No. Akad</th><th>Tanggal</th><th>Tempo Lama &rarr; Baru</th><th>Ujrah</th><th>Kasir</th><th>Aksi</th></tr></thead><tbody>
<?php while($r=mysqli_fetch_assoc($riw)): ?>
<tr><td class="mono"><a href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['no_akad']); ?></strong></a></td><td class="mono"><?= e($r['tanggal_perpanjang']); ?></td><td class="mono"><?= e($r['jatuh_tempo_lama']); ?> &rarr; <?= e($r['jatuh_tempo_baru']); ?></td><td class="mono"><?= rupiah($r['biaya_ujrah_dibayar']); ?></td><td><?php if(is_owner()): ?><a href="laporan_akuntansi.php?user_id=<?= (int)$r['user_id']; ?>"><?= e($r['nama_kasir']); ?></a><?php else: ?><?= e($r['nama_kasir']); ?><?php endif; ?></td>
<td><div style="display:flex;gap:5px;flex-wrap:wrap;"><a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('printer', 13); ?> Nota</a><?php if(is_owner()): ?><a class="btn btn-sm" href="perpanjang_gadai.php?edit=<?= (int)$r['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><form method="POST" style="display:inline;" onsubmit="return confirm('Hapus riwayat perpanjangan <?= e($r['no_akad']); ?>?')"><input type="hidden" name="id" value="<?= (int)$r['id']; ?>"><button type="submit" name="delete_perpanjang" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form><?php endif; ?></div></td></tr>
<?php endwhile; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
