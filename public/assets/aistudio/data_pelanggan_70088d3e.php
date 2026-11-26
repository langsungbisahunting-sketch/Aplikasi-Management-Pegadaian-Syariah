<?php
require_once "config/koneksi.php";
require_login();
$page_title = "Data Pelanggan (Rahin)";
$cu = current_user();
$ok = ""; $err = ""; $editCust = null; $editPdfCust = null;

// Akses Upload / Ganti / Hapus Dokumen PDF untuk Kasir & Owner
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['upload_dokumen_pelanggan'])) {
    $nikDoc = trim($_POST['nik_nasabah'] ?? '');
    $namaDoc = trim($_POST['nama_nasabah'] ?? '');
    $ketDoc = trim($_POST['keterangan_dokumen'] ?? '');
    if ($nikDoc === '') {
        $err = "NIK pelanggan wajib tersedia untuk menyimpan dokumen PDF.";
    } else {
        $resDok = simpan_dokumen_pelanggan($conn, $nikDoc, $namaDoc, $_FILES['dokumen_pdf'] ?? [], $ketDoc, $cu);
        if ($resDok['ok']) {
            $ok = "Dokumen PDF pelanggan <strong>" . e($namaDoc ?: $nikDoc) . "</strong> (" . e($resDok['file_asli']) . " &middot; " . format_ukuran_file($resDok['size']) . ") berhasil " . ($resDok['replaced'] ? "diganti" : "disimpan") . ".";
        } else {
            $err = $resDok['error'];
        }
    }
} elseif ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['delete_dokumen_pelanggan'])) {
    $nikDelDoc = trim($_POST['nik_nasabah'] ?? '');
    if ($nikDelDoc !== '' && hapus_dokumen_pelanggan($conn, $nikDelDoc)) {
        $ok = "Dokumen PDF pelanggan NIK <strong class=\"mono\">" . e($nikDelDoc) . "</strong> berhasil dihapus.";
    } else {
        $err = "Dokumen PDF tidak ditemukan.";
    }
} elseif (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['update_pelanggan'])) {
    $oldNik = trim($_POST['old_nik'] ?? '');
    $oldNama = trim($_POST['old_nama'] ?? '');
    $newNama = trim($_POST['nama_nasabah'] ?? '');
    $newNik = trim($_POST['nik_nasabah'] ?? '');
    $newHp = trim($_POST['no_hp'] ?? '');
    $newAlm = trim($_POST['alamat_nasabah'] ?? '');
    $hasPdf = isset($_FILES['dokumen_pdf']) && (int)($_FILES['dokumen_pdf']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE;
    $pdfChk = $hasPdf ? validate_pdf_upload($_FILES['dokumen_pdf']) : ['ok' => true];
    if ($newNama === '' || $newNik === '') {
        $err = "Nama dan NIK wajib diisi.";
    } elseif ($hasPdf && !$pdfChk['ok']) {
        $err = $pdfChk['error'];
    } else {
        $up = mysqli_prepare($conn, "UPDATE transaksi_gadai SET nama_nasabah=?, nik_nasabah=?, no_hp=?, alamat_nasabah=? WHERE nama_nasabah=? AND nik_nasabah=?");
        mysqli_stmt_bind_param($up, "ssssss", $newNama, $newNik, $newHp, $newAlm, $oldNama, $oldNik);
        mysqli_stmt_execute($up); mysqli_stmt_close($up);
        if ($oldNama !== $newNama) {
            $eOld = mysqli_real_escape_string($conn, $oldNama);
            $eNew = mysqli_real_escape_string($conn, $newNama);
            mysqli_query($conn, "UPDATE jurnal_akuntansi SET keterangan = REPLACE(keterangan, '$eOld', '$eNew') WHERE keterangan LIKE '%$eOld%'");
        }
        $upDok = mysqli_prepare($conn, "UPDATE dokumen_pelanggan SET nik_nasabah=?, nama_nasabah=? WHERE nik_nasabah=?");
        if ($upDok) {
            mysqli_stmt_bind_param($upDok, "sss", $newNik, $newNama, $oldNik);
            @mysqli_stmt_execute($upDok);
            mysqli_stmt_close($upDok);
        }
        $dokInfo = "";
        if ($hasPdf) {
            $resDok = simpan_dokumen_pelanggan($conn, $newNik, $newNama, $_FILES['dokumen_pdf'], trim($_POST['keterangan_dokumen'] ?? ''), $cu);
            if ($resDok['ok']) {
                $dokInfo = " &amp; dokumen PDF (" . e($resDok['file_asli']) . ") diperbarui";
            }
        }
        $ok = "Identitas pelanggan <strong>" . e($newNama) . "</strong> diperbarui di seluruh akad, jurnal" . $dokInfo . ".";
    }
} elseif (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['delete_pelanggan'])) {
    $delNik = trim($_POST['nik_nasabah'] ?? '');
    $delNama = trim($_POST['nama_nasabah'] ?? '');
    $stF = mysqli_prepare($conn, "SELECT no_akad FROM transaksi_gadai WHERE nama_nasabah=? AND nik_nasabah=?");
    mysqli_stmt_bind_param($stF, "ss", $delNama, $delNik); mysqli_stmt_execute($stF);
    $rsF = mysqli_stmt_get_result($stF);
    $akads = [];
    while ($rw = mysqli_fetch_assoc($rsF)) $akads[] = $rw['no_akad'];
    mysqli_stmt_close($stF);
    foreach ($akads as $ak) {
        $d1 = mysqli_prepare($conn, "DELETE FROM riwayat_perpanjangan WHERE no_akad=?");
        mysqli_stmt_bind_param($d1, "s", $ak); mysqli_stmt_execute($d1); mysqli_stmt_close($d1);
        $d2 = mysqli_prepare($conn, "DELETE FROM jurnal_akuntansi WHERE no_akad=?");
        mysqli_stmt_bind_param($d2, "s", $ak); mysqli_stmt_execute($d2); mysqli_stmt_close($d2);
    }
    $d3 = mysqli_prepare($conn, "DELETE FROM transaksi_gadai WHERE nama_nasabah=? AND nik_nasabah=?");
    mysqli_stmt_bind_param($d3, "ss", $delNama, $delNik); mysqli_stmt_execute($d3); mysqli_stmt_close($d3);

    $chkSisa = mysqli_prepare($conn, "SELECT COUNT(*) AS c FROM transaksi_gadai WHERE nik_nasabah=?");
    mysqli_stmt_bind_param($chkSisa, "s", $delNik); mysqli_stmt_execute($chkSisa);
    $sisaRow = mysqli_fetch_assoc(mysqli_stmt_get_result($chkSisa)); mysqli_stmt_close($chkSisa);
    if ((int)($sisaRow['c'] ?? 0) === 0) {
        hapus_dokumen_pelanggan($conn, $delNik);
    }
    $ok = "Data pelanggan <strong>" . e($delNama) . "</strong> beserta " . count($akads) . " akad terkait berhasil dihapus.";
}

if (is_owner() && isset($_GET['edit_nik'], $_GET['edit_nama'])) {
    $stE = mysqli_prepare($conn, "SELECT nama_nasabah, nik_nasabah, no_hp, alamat_nasabah FROM transaksi_gadai WHERE nik_nasabah=? AND nama_nasabah=? LIMIT 1");
    mysqli_stmt_bind_param($stE, "ss", $_GET['edit_nik'], $_GET['edit_nama']); mysqli_stmt_execute($stE);
    $editCust = mysqli_fetch_assoc(mysqli_stmt_get_result($stE)); mysqli_stmt_close($stE);
}

if (isset($_GET['ganti_pdf_nik'])) {
    $gNik = trim($_GET['ganti_pdf_nik']);
    $stP = mysqli_prepare($conn, "SELECT nama_nasabah, nik_nasabah, no_hp, alamat_nasabah FROM transaksi_gadai WHERE nik_nasabah=? LIMIT 1");
    mysqli_stmt_bind_param($stP, "s", $gNik); mysqli_stmt_execute($stP);
    $editPdfCust = mysqli_fetch_assoc(mysqli_stmt_get_result($stP)); mysqli_stmt_close($stP);
}

$q = trim($_GET['q'] ?? '');
$res = mysqli_query($conn, "SELECT nama_nasabah, nik_nasabah, no_hp, alamat_nasabah, COUNT(*) AS tot, SUM(CASE WHEN status_gadai IN ('AKTIF','DIPERPANJANG') THEN 1 ELSE 0 END) AS aktif, COALESCE(SUM(CASE WHEN status_gadai IN ('AKTIF','DIPERPANJANG') THEN pokok_pinjaman ELSE 0 END),0) AS pok_aktif FROM transaksi_gadai GROUP BY nama_nasabah, nik_nasabah, no_hp, alamat_nasabah ORDER BY nama_nasabah ASC");
$list = [];
while ($r = mysqli_fetch_assoc($res)) {
    if ($q !== '' && stripos($r['nama_nasabah'].$r['nik_nasabah'].$r['no_hp'], $q) === false) continue;
    $list[] = $r;
}
$dokMap = get_all_dokumen_map($conn);

if (isset($_GET['export']) && $_GET['export'] === 'xlsx') {
    $rows = [['Nama Rahin','NIK','No. HP','Alamat','Akad Aktif','Total Akad','Pokok Aktif (Rp)','Status Dokumen PDF','Nama File PDF']];
    foreach ($list as $r) {
        $d = $dokMap[$r['nik_nasabah']] ?? null;
        $rows[] = [$r['nama_nasabah'],$r['nik_nasabah'],$r['no_hp'],$r['alamat_nasabah'],(int)$r['aktif'],(int)$r['tot'],(float)$r['pok_aktif'], $d ? 'ADA' : 'BELUM', $d ? $d['nama_file_asli'] : '-'];
    }
    SyariahXlsx::download('data_pelanggan_' . date('Y-m-d') . '.xlsx', [['name' => 'Pelanggan', 'rows' => $rows]]);
}
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok no-print"><?= $ok; ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err no-print"><?= e($err); ?></div><?php endif; ?>

<?php if ($editPdfCust):
  $curDok = $dokMap[$editPdfCust['nik_nasabah']] ?? null;
?>
<div class="panel panel-yellow no-print" id="panel-ganti-pdf">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('folder', 16); ?> <?= $curDok ? 'Edit / Ganti' : 'Upload'; ?> Dokumen PDF Pelanggan: <?= e($editPdfCust['nama_nasabah']); ?> (<span class="mono"><?= e($editPdfCust['nik_nasabah']); ?></span>)</div>
    <a href="data_pelanggan.php<?= $q !== '' ? '?q=' . urlencode($q) : ''; ?>" class="btn btn-sm">Tutup</a>
  </div>
  <?php if ($curDok): ?>
  <div class="cell-sub" style="margin-bottom:10px;padding:8px 10px;background:#ecfdf5;border:1px solid #a7f3d0;border-radius:6px;color:#065f46;">
    Dokumen aktif saat ini: <strong class="mono"><?= e($curDok['nama_file_asli']); ?></strong> (<?= format_ukuran_file($curDok['ukuran_byte']); ?>) &middot;
    <a href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($editPdfCust['nik_nasabah']); ?>"><strong>Preview PDF</strong></a> &middot;
    <a href="dokumen_pelanggan.php?lihat=1&nik=<?= urlencode($editPdfCust['nik_nasabah']); ?>" target="_blank">Buka Tab Baru</a> &middot;
    <a href="dokumen_pelanggan.php?download=1&nik=<?= urlencode($editPdfCust['nik_nasabah']); ?>">Unduh</a>
  </div>
  <?php endif; ?>
  <form method="POST" enctype="multipart/form-data" class="form-grid" onsubmit="const b=this.querySelector('button[type=submit]');if(b){setTimeout(()=>{b.disabled=true;},0);}">
    <input type="hidden" name="upload_dokumen_pelanggan" value="1">
    <input type="hidden" name="nik_nasabah" value="<?= e($editPdfCust['nik_nasabah']); ?>">
    <input type="hidden" name="nama_nasabah" value="<?= e($editPdfCust['nama_nasabah']); ?>">
    <div class="field">
      <label>Pilih File PDF Baru (Maks. 1 PDF / Orang, Ukuran Tidak Dibatasi)</label>
      <input type="file" name="dokumen_pdf" accept=".pdf,application/pdf" required>
    </div>
    <div class="field">
      <label>Keterangan Dokumen</label>
      <input type="text" name="keterangan_dokumen" value="<?= e($curDok['keterangan'] ?? 'Bukti Data Diri (KTP/KK) & Dokumen Pendukung'); ?>">
    </div>
    <div class="field" style="justify-content:flex-end;">
      <button type="submit" name="upload_dokumen_pelanggan" class="btn btn-primary"><?= svg_icon('upload', 14); ?> <?= $curDok ? 'Ganti File PDF Baru' : 'Upload File PDF'; ?></button>
    </div>
  </form>
</div>
<?php endif; ?>

<?php if ($editCust && is_owner()):
  $editCurDok = $dokMap[$editCust['nik_nasabah']] ?? null;
?>
<div class="panel panel-yellow no-print">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 16); ?> Edit Identitas Pelanggan (Rahin)</div><a href="data_pelanggan.php" class="btn btn-sm">Batal</a></div>
  <form method="POST" enctype="multipart/form-data" class="form-grid">
    <input type="hidden" name="old_nik" value="<?= e($editCust['nik_nasabah']); ?>">
    <input type="hidden" name="old_nama" value="<?= e($editCust['nama_nasabah']); ?>">
    <div class="field"><label>Nama Rahin</label><input type="text" name="nama_nasabah" value="<?= e($editCust['nama_nasabah']); ?>" required></div>
    <div class="field"><label>NIK</label><input type="text" name="nik_nasabah" class="mono" value="<?= e($editCust['nik_nasabah']); ?>" required></div>
    <div class="field"><label>No. HP</label><input type="text" name="no_hp" class="mono" value="<?= e($editCust['no_hp']); ?>" required></div>
    <div class="field"><label>Alamat</label><input type="text" name="alamat_nasabah" value="<?= e($editCust['alamat_nasabah']); ?>" required></div>
    <div class="field"><label>Ganti Dokumen PDF (Opsional)</label><input type="file" name="dokumen_pdf" accept=".pdf,application/pdf"><?php if($editCurDok): ?><span class="cell-sub">Saat ini: <?= e($editCurDok['nama_file_asli']); ?></span><?php endif; ?></div>
    <div class="field"><label>Keterangan Dokumen</label><input type="text" name="keterangan_dokumen" value="<?= e($editCurDok['keterangan'] ?? 'Bukti Data Diri (KTP/KK) & Dokumen Pendukung'); ?>"></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="update_pelanggan" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Perubahan</button></div>
  </form>
</div>
<?php endif; ?>

<div class="panel panel-yellow no-print">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('users', 16); ?> Daftar Nasabah (Rahin)</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a class="btn btn-sm btn-primary" href="dokumen_pelanggan.php"><?= svg_icon('folder', 14); ?> Menu Dokumen PDF</a>
      <a class="btn btn-sm" href="data_pelanggan.php?export=xlsx&q=<?= urlencode($q); ?>"><?= svg_icon('download', 14); ?> Export Excel (.xlsx)</a>
      <button type="button" class="btn btn-sm" onclick="window.print()"><?= svg_icon('printer', 14); ?> Cetak</button>
    </div>
  </div>
  <form method="GET" style="display:flex;gap:8px;max-width:420px;">
    <input type="text" name="q" value="<?= e($q); ?>" placeholder="Cari nama, NIK, atau No. HP..." style="flex:1;padding:7px 10px;border:1px solid #c6d3cb;border-radius:6px;">
    <button type="submit" class="btn btn-sm btn-primary">Cari</button>
    <?php if($q !== ''): ?><a href="data_pelanggan.php" class="btn btn-sm">Reset</a><?php endif; ?>
  </form>
</div>
<div class="panel"><div class="table-box"><table><thead><tr><th>Nama &amp; NIK</th><th>Kontak &amp; Alamat</th><th>Akad Aktif / Total</th><th>Pokok Aktif</th><th>Dokumen PDF (Max 1)</th><th class="no-print">Aksi</th></tr></thead><tbody>
<?php foreach($list as $r):
  $gadaiUrl = "gadai_barang.php?nasabah=" . urlencode($r['nama_nasabah']) . "&nik=" . urlencode($r['nik_nasabah']) . "&hp=" . urlencode($r['no_hp']) . "&alamat=" . urlencode($r['alamat_nasabah']);
  $dok = $dokMap[$r['nik_nasabah']] ?? null;
?>
<tr><td><strong><?= e($r['nama_nasabah']); ?></strong><div class="cell-sub mono"><?= e($r['nik_nasabah']); ?></div></td><td><span class="mono"><?= e($r['no_hp']); ?></span><div class="cell-sub"><?= e($r['alamat_nasabah']); ?></div></td><td class="mono"><?= (int)$r['aktif']; ?> / <?= (int)$r['tot']; ?></td><td class="mono"><?= rupiah($r['pok_aktif']); ?></td>
<td>
  <?php if ($dok): ?>
    <a href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($r['nik_nasabah']); ?>" style="color:#065f46;font-weight:700;"><?= svg_icon('paperclip', 12); ?> <?= e($dok['nama_file_asli']); ?></a>
    <div class="cell-sub mono"><?= format_ukuran_file($dok['ukuran_byte']); ?> &middot; <?= e($dok['updated_at']); ?></div>
  <?php else: ?>
    <span class="cell-sub">Belum ada PDF</span>
  <?php endif; ?>
</td>
<td class="no-print"><div style="display:flex;gap:5px;flex-wrap:wrap;">
  <a class="btn btn-sm btn-primary" href="<?= e($gadaiUrl); ?>"><?= svg_icon('plus-circle', 13); ?> Gadai Baru</a>
  <?php if ($dok): ?>
    <a class="btn btn-sm" href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($r['nik_nasabah']); ?>"><?= svg_icon('eye', 13); ?> Lihat PDF</a>
    <a class="btn btn-sm btn-yellow" href="data_pelanggan.php?ganti_pdf_nik=<?= urlencode($r['nik_nasabah']); ?>&q=<?= urlencode($q); ?>#panel-ganti-pdf"><?= svg_icon('upload', 13); ?> Ganti PDF</a>
    <form method="POST" style="display:inline;" onsubmit="return confirm('Hapus file PDF milik <?= e($r['nama_nasabah']); ?>?')"><input type="hidden" name="nik_nasabah" value="<?= e($r['nik_nasabah']); ?>"><button type="submit" name="delete_dokumen_pelanggan" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus PDF</button></form>
  <?php else: ?>
    <a class="btn btn-sm btn-yellow" href="data_pelanggan.php?ganti_pdf_nik=<?= urlencode($r['nik_nasabah']); ?>&q=<?= urlencode($q); ?>#panel-ganti-pdf"><?= svg_icon('upload', 13); ?> Upload PDF</a>
  <?php endif; ?>
  <?php if(is_owner()): ?>
    <a class="btn btn-sm" href="data_barang.php?q=<?= urlencode($r['nama_nasabah']); ?>"><?= svg_icon('package', 13); ?> Barang</a>
    <a class="btn btn-sm" href="data_pelanggan.php?edit_nik=<?= urlencode($r['nik_nasabah']); ?>&edit_nama=<?= urlencode($r['nama_nasabah']); ?>"><?= svg_icon('edit', 13); ?> Edit</a>
    <form method="POST" style="display:inline;" onsubmit="return confirm('Hapus nasabah <?= e($r['nama_nasabah']); ?> beserta seluruh akadnya?')"><input type="hidden" name="nik_nasabah" value="<?= e($r['nik_nasabah']); ?>"><input type="hidden" name="nama_nasabah" value="<?= e($r['nama_nasabah']); ?>"><button type="submit" name="delete_pelanggan" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form>
  <?php endif; ?>
</div></td></tr>
<?php endforeach; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
