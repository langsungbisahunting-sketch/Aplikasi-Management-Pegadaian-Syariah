<?php
require_once "config/koneksi.php";
require_login();
$page_title = "Tebus Barang";
$cu = current_user();
$ok = ""; $err = ""; $editTeb = null;
$preAkad = trim($_GET['no_akad'] ?? '');

if (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['cancel_tebus'])) {
    $tid = (int)($_POST['id'] ?? 0);
    $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? AND status_gadai='LUNAS' LIMIT 1");
    mysqli_stmt_bind_param($st, "i", $tid); mysqli_stmt_execute($st);
    $tx = mysqli_fetch_assoc(mysqli_stmt_get_result($st)); mysqli_stmt_close($st);
    if ($tx) {
        $nak = $tx['no_akad'];
        $agg = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS cnt, COALESCE(SUM(biaya_ujrah_dibayar),0) AS tot FROM riwayat_perpanjangan WHERE no_akad='" . mysqli_real_escape_string($conn, $nak) . "'"));
        $newSt = ((int)$agg['cnt'] > 0) ? 'DIPERPANJANG' : 'AKTIF';
        $totU = (float)$agg['tot'];
        $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET status_gadai=?, tanggal_tebus=NULL, ujrah_tebus_dibayar=NULL, total_tebusan=NULL, total_ujrah_dibayar=? WHERE id=?");
        mysqli_stmt_bind_param($upd, "sdi", $newSt, $totU, $tid);
        mysqli_stmt_execute($upd); mysqli_stmt_close($upd);

        $dj = mysqli_prepare($conn, "DELETE FROM jurnal_akuntansi WHERE no_akad=? AND jenis_menu='TEBUS_BARANG'");
        mysqli_stmt_bind_param($dj, "s", $nak); mysqli_stmt_execute($dj); mysqli_stmt_close($dj);
        $ok = "Pelunasan akad <strong>" . e($nak) . "</strong> dibatalkan &amp; status dikembalikan ke $newSt.";
    }
} elseif (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['update_tebus'])) {
    $tid = (int)($_POST['id'] ?? 0);
    $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? AND status_gadai='LUNAS' LIMIT 1");
    mysqli_stmt_bind_param($st, "i", $tid); mysqli_stmt_execute($st);
    $tx = mysqli_fetch_assoc(mysqli_stmt_get_result($st)); mysqli_stmt_close($st);
    if ($tx) {
        $tglN = valid_iso_date($_POST['tanggal_tebus'] ?? '') ? $_POST['tanggal_tebus'] : ($tx['tanggal_tebus'] ?: date('Y-m-d'));
        $ujrN = max(0, (float)($_POST['ujrah_tebus_dibayar'] ?? 0));
        $pok = (float)$tx['pokok_pinjaman'];
        $totTeb = $pok + $ujrN;
        $agg = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COALESCE(SUM(biaya_ujrah_dibayar),0) AS tot FROM riwayat_perpanjangan WHERE no_akad='" . mysqli_real_escape_string($conn, $tx['no_akad']) . "'"));
        $totU = (float)$agg['tot'] + $ujrN;
        $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET tanggal_tebus=?, ujrah_tebus_dibayar=?, total_ujrah_dibayar=?, total_tebusan=? WHERE id=?");
        mysqli_stmt_bind_param($upd, "sdddi", $tglN, $ujrN, $totU, $totTeb, $tid);
        mysqli_stmt_execute($upd); mysqli_stmt_close($upd);

        $uj = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET tanggal=?, pendapatan_ujrah=?, total_kas=? WHERE no_akad=? AND jenis_menu='TEBUS_BARANG'");
        mysqli_stmt_bind_param($uj, "sdds", $tglN, $ujrN, $totTeb, $tx['no_akad']);
        mysqli_stmt_execute($uj); mysqli_stmt_close($uj);
        $ok = "Data pelunasan akad <strong>" . e($tx['no_akad']) . "</strong> diperbarui.";
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['simpan_tebus'])) {
    $idem = $_POST['idempotency_token'] ?? '';
    $tgl = valid_iso_date($_POST['tanggal_tebus'] ?? '') ? $_POST['tanggal_tebus'] : date('Y-m-d');
    $no_akad = trim($_POST['no_akad'] ?? '');
    $per = max(0, (int)($_POST['periode_terutang'] ?? 1));
    if (!check_and_record_idempotency($conn, $idem)) {
        $err = "Permintaan penebusan sudah diproses.";
    } elseif (is_date_closed($conn, $tgl)) {
        $err = "Sesi operasional tanggal $tgl sudah DITUTUP & TERKUNCI.";
    } else {
        $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE no_akad = ? AND status_gadai IN ('AKTIF','DIPERPANJANG') LIMIT 1");
        mysqli_stmt_bind_param($st, "s", $no_akad); mysqli_stmt_execute($st);
        $r = mysqli_fetch_assoc(mysqli_stmt_get_result($st)); mysqli_stmt_close($st);
        if (!$r) { $err = "Akad aktif tidak ditemukan."; }
        else {
            $pokok = (float)$r['pokok_pinjaman'];
            $ujrah = (float)$r['biaya_ujrah_periode'] * $per;
            $tot = $pokok + $ujrah;
            $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET status_gadai='LUNAS', tanggal_tebus=?, ujrah_tebus_dibayar=?, total_ujrah_dibayar=total_ujrah_dibayar+?, total_tebusan=? WHERE id=?");
            mysqli_stmt_bind_param($upd, "sdddi", $tgl, $ujrah, $ujrah, $tot, $r['id']);
            mysqli_stmt_execute($upd); mysqli_stmt_close($upd);

            $ket = "Pelunasan tebus $no_akad - " . $r['nama_nasabah'];
            $ja = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pokok_nominal, pendapatan_ujrah, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'TEBUS_BARANG', 'MASUK', 'Kas Operasional', 'Piutang Qardh & Jasa Ujrah', ?, ?, ?, ?, ?, ?)");
            mysqli_stmt_bind_param($ja, "ssdddsis", $tgl, $no_akad, $pokok, $ujrah, $tot, $ket, $cu['id'], $cu['nama_lengkap']);
            mysqli_stmt_execute($ja); mysqli_stmt_close($ja);
            $ok = "Penebusan akad <strong>" . e($no_akad) . "</strong> (LUNAS) sebesar <strong>" . rupiah($tot) . "</strong> berhasil.";
        }
    }
}

if (is_owner() && isset($_GET['edit'])) {
    $eid = (int)$_GET['edit'];
    $stE = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? AND status_gadai='LUNAS' LIMIT 1");
    mysqli_stmt_bind_param($stE, "i", $eid); mysqli_stmt_execute($stE);
    $editTeb = mysqli_fetch_assoc(mysqli_stmt_get_result($stE)); mysqli_stmt_close($stE);
}

$aktif = mysqli_query($conn, "SELECT * FROM transaksi_gadai WHERE status_gadai IN ('AKTIF','DIPERPANJANG') ORDER BY jatuh_tempo ASC");
$lunas = mysqli_query($conn, "SELECT * FROM transaksi_gadai WHERE status_gadai='LUNAS' ORDER BY id DESC LIMIT 30");
$idem_val = bin2hex(random_bytes(12));
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok"><?= $ok; ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err"><?= e($err); ?></div><?php endif; ?>

<?php if ($editTeb && is_owner()): ?>
<div class="panel panel-yellow">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 16); ?> Edit Pelunasan: <?= e($editTeb['no_akad']); ?></div><a href="tebus_barang.php" class="btn btn-sm">Batal</a></div>
  <form method="POST" class="form-grid">
    <input type="hidden" name="id" value="<?= (int)$editTeb['id']; ?>">
    <div class="field"><label>Tanggal Tebus</label><input type="date" name="tanggal_tebus" class="mono" value="<?= e($editTeb['tanggal_tebus'] ?: date('Y-m-d')); ?>" required></div>
    <div class="field"><label>Ujrah Tebus Dibayar (Rp)</label><input type="number" name="ujrah_tebus_dibayar" class="mono" value="<?= (float)($editTeb['ujrah_tebus_dibayar'] ?? 0); ?>" required></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="update_tebus" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Perubahan</button></div>
  </form>
</div>
<?php endif; ?>

<div class="panel panel-yellow">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('check-circle', 16); ?> Form Tebus Barang (Pelunasan Pokok Qardh + Ujrah)</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="perpanjang_gadai.php" class="btn btn-sm"><?= svg_icon('refresh', 13); ?> Perpanjang Gadai</a>
      <a href="tutup_toko.php" class="btn btn-sm"><?= svg_icon('lock', 13); ?> Tutup Toko</a>
    </div>
  </div>
  <form method="POST" onsubmit="const b=this.querySelector('button[type=submit]');if(b){setTimeout(()=>{b.disabled=true;},0);}">
    <input type="hidden" name="simpan_tebus" value="1">
    <input type="hidden" name="idempotency_token" value="<?= e($idem_val); ?>">
    <div class="form-grid">
      <div class="field"><label>No. Akad Aktif</label><select name="no_akad" id="tebAkad" onchange="calcTeb()" required>
        <option value="">-- Pilih Akad --</option>
        <?php while($a = mysqli_fetch_assoc($aktif)): ?>
          <option value="<?= e($a['no_akad']); ?>" data-pokok="<?= (float)$a['pokok_pinjaman']; ?>" data-ujrah="<?= (float)$a['biaya_ujrah_periode']; ?>" <?= $preAkad === $a['no_akad'] ? 'selected' : ''; ?>><?= e($a['no_akad']); ?> | <?= e($a['nama_nasabah']); ?> | Pokok: <?= rupiah($a['pokok_pinjaman']); ?> + Ujrah: <?= rupiah($a['biaya_ujrah_periode']); ?></option>
        <?php endwhile; ?>
      </select></div>
      <div class="field"><label>Tanggal Tebus</label><input type="date" name="tanggal_tebus" class="mono" value="<?= date('Y-m-d'); ?>" required></div>
      <div class="field"><label>Periode Ujrah Terutang</label><input type="number" min="0" name="periode_terutang" id="tebPer" class="mono" value="1" oninput="calcTeb()" required></div>
      <div class="field"><label>Total Tebusan (Pokok + Ujrah)</label><input type="text" id="outTebTot" readonly class="mono"></div>
    </div>
    <div style="margin-top:12px;"><button type="submit" name="simpan_tebus" class="btn btn-primary"><?= svg_icon('check-circle', 14); ?> Simpan Tebus Barang</button></div>
  </form>
</div>
<script>
function calcTeb(){
  const sel = document.getElementById('tebAkad');
  const opt = sel && sel.selectedOptions[0];
  const per = Math.max(0, parseInt(document.getElementById('tebPer').value || '0', 10));
  if (!opt || !opt.value) {
    document.getElementById('outTebTot').value = 'Rp 0';
    return;
  }
  const pok = parseFloat(opt.getAttribute('data-pokok') || '0');
  const ujr = parseFloat(opt.getAttribute('data-ujrah') || '0');
  document.getElementById('outTebTot').value = 'Rp ' + Math.round(pok + (ujr * per)).toLocaleString('id-ID');
}
calcTeb();
</script>
<div class="panel"><div class="panel-head"><div class="panel-title"><?= svg_icon('file-text', 16); ?> Daftar Barang Lunas</div></div>
<div class="table-box"><table><thead><tr><th>No. Akad</th><th>Nasabah</th><th>Barang</th><th>Total Tebusan</th><th>Aksi</th></tr></thead><tbody>
<?php while($r=mysqli_fetch_assoc($lunas)): ?>
<tr><td class="mono"><a href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['no_akad']); ?></strong></a><div class="cell-sub"><?= e($r['tanggal_tebus'] ?? ''); ?></div></td><td><a href="data_pelanggan.php?q=<?= urlencode($r['nama_nasabah']); ?>"><?= e($r['nama_nasabah']); ?></a></td><td><?= e($r['nama_barang']); ?></td><td class="mono"><?= rupiah($r['total_tebusan']); ?></td>
<td><div style="display:flex;gap:5px;flex-wrap:wrap;"><a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('printer', 13); ?> Cetak</a>
<?php if(is_owner()): ?><a class="btn btn-sm" href="tebus_barang.php?edit=<?= (int)$r['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><form method="POST" style="display:inline;" onsubmit="return confirm('Batalkan status LUNAS untuk akad <?= e($r['no_akad']); ?>?')"><input type="hidden" name="id" value="<?= (int)$r['id']; ?>"><button type="submit" name="cancel_tebus" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Batal Lunas</button></form><?php endif; ?>
</div></td></tr>
<?php endwhile; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
