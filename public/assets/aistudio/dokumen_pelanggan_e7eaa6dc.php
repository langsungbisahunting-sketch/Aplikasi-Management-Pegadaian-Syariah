<?php
require_once "config/koneksi.php";
require_login();
$cu = current_user();

// Endpoint streaming lihat / download PDF (akses Kasir & Owner)
if ((isset($_GET['lihat']) || isset($_GET['download'])) && isset($_GET['nik'])) {
    $nikReq = trim($_GET['nik']);
    $dok = get_dokumen_pelanggan($conn, $nikReq);
    if (!$dok) {
        http_response_code(404);
        die("Dokumen PDF pelanggan tidak ditemukan.");
    }
    $dir = get_dokumen_dir();
    $baseReal = realpath($dir);
    $fullPath = realpath($dir . '/' . basename($dok['nama_file_simpan']));
    if (!$fullPath || !$baseReal || strpos($fullPath, $baseReal . DIRECTORY_SEPARATOR) !== 0 || !is_file($fullPath)) {
        http_response_code(404);
        die("File PDF fisik tidak ditemukan di server.");
    }
    @set_time_limit(0);
    while (ob_get_level() > 0) {
        @ob_end_clean();
    }
    $dlName = preg_replace('/[^A-Za-z0-9._-]/', '_', $dok['nama_file_asli'] ?: ('dokumen_' . $nikReq . '.pdf'));
    if (strtolower(pathinfo($dlName, PATHINFO_EXTENSION)) !== 'pdf') {
        $dlName .= '.pdf';
    }
    $disp = isset($_GET['download']) ? 'attachment' : 'inline';
    header('Content-Type: application/pdf');
    header('X-Content-Type-Options: nosniff');
    header('Content-Disposition: ' . $disp . '; filename="' . $dlName . '"');
    header('Content-Length: ' . filesize($fullPath));
    header('Cache-Control: private, max-age=0, must-revalidate');
    readfile($fullPath);
    exit;
}

$page_title = "Dokumen Pelanggan (PDF)";
$ok = ""; $err = "";
$selectedNik = trim($_GET['nik'] ?? '');
$previewNik = isset($_GET['preview']) ? trim($_GET['nik'] ?? '') : '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['upload_dokumen'])) {
        $nikUp = trim($_POST['nik_nasabah'] ?? '');
        $ketUp = trim($_POST['keterangan'] ?? '');
        if ($nikUp === '') {
            $err = "Pilih pelanggan (NIK) terlebih dahulu.";
        } else {
            $stC = mysqli_prepare($conn, "SELECT nama_nasabah FROM transaksi_gadai WHERE nik_nasabah = ? ORDER BY id DESC LIMIT 1");
            mysqli_stmt_bind_param($stC, "s", $nikUp);
            mysqli_stmt_execute($stC);
            $cRow = mysqli_fetch_assoc(mysqli_stmt_get_result($stC));
            mysqli_stmt_close($stC);
            if (!$cRow) {
                $err = "Data pelanggan dengan NIK tersebut tidak ditemukan.";
            } else {
                $resUp = simpan_dokumen_pelanggan($conn, $nikUp, $cRow['nama_nasabah'], $_FILES['dokumen_pdf'] ?? [], $ketUp, $cu);
                if ($resUp['ok']) {
                    $ok = "Dokumen PDF milik <strong>" . e($cRow['nama_nasabah']) . "</strong> (" . e($resUp['file_asli']) . " &middot; " . format_ukuran_file($resUp['size']) . ") berhasil " . ($resUp['replaced'] ? "diganti" : "disimpan") . ".";
                    $selectedNik = $nikUp;
                    $previewNik = $nikUp;
                } else {
                    $err = $resUp['error'];
                    $selectedNik = $nikUp;
                }
            }
        }
    } elseif (isset($_POST['delete_dokumen'])) {
        $nikDel = trim($_POST['nik_nasabah'] ?? '');
        if ($nikDel !== '' && hapus_dokumen_pelanggan($conn, $nikDel)) {
            $ok = "Dokumen PDF pelanggan NIK <strong class=\"mono\">" . e($nikDel) . "</strong> berhasil dihapus.";
            if ($previewNik === $nikDel) $previewNik = '';
        } else {
            $err = "Dokumen PDF tidak ditemukan.";
        }
    }
}

$q = trim($_GET['q'] ?? '');
$statusFilter = trim($_GET['status'] ?? '');

$custRes = mysqli_query($conn, "SELECT nik_nasabah, MAX(nama_nasabah) AS nama_nasabah, MAX(no_hp) AS no_hp, MAX(alamat_nasabah) AS alamat_nasabah, COUNT(*) AS tot_akad, SUM(CASE WHEN status_gadai IN ('AKTIF','DIPERPANJANG') THEN 1 ELSE 0 END) AS akad_aktif FROM transaksi_gadai WHERE nik_nasabah <> '' GROUP BY nik_nasabah ORDER BY nama_nasabah ASC");
$customers = [];
while ($c = mysqli_fetch_assoc($custRes)) {
    $customers[] = $c;
}
$dokMap = get_all_dokumen_map($conn);

$totalPelanggan = count($customers);
$totalAdaDok = 0;
foreach ($customers as $c) {
    if (isset($dokMap[$c['nik_nasabah']])) $totalAdaDok++;
}
$totalBelumDok = max(0, $totalPelanggan - $totalAdaDok);

$filtered = [];
foreach ($customers as $c) {
    $dok = $dokMap[$c['nik_nasabah']] ?? null;
    if ($statusFilter === 'ADA' && !$dok) continue;
    if ($statusFilter === 'BELUM' && $dok) continue;
    if ($q !== '') {
        $haystack = $c['nama_nasabah'] . ' ' . $c['nik_nasabah'] . ' ' . $c['no_hp'] . ' ' . ($dok['nama_file_asli'] ?? '') . ' ' . ($dok['keterangan'] ?? '');
        if (stripos($haystack, $q) === false) continue;
    }
    $c['dokumen'] = $dok;
    $filtered[] = $c;
}

$previewDok = ($previewNik !== '' && isset($dokMap[$previewNik])) ? $dokMap[$previewNik] : null;

require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok no-print"><?= $ok; ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err no-print"><?= e($err); ?></div><?php endif; ?>

<div class="stats-row no-print">
  <div class="panel">
    <div class="stat-title">Total Pelanggan (Rahin)</div>
    <div class="stat-num"><?= $totalPelanggan; ?> Orang</div>
    <div class="cell-sub"><a href="dokumen_pelanggan.php">Tampilkan Semua &rarr;</a></div>
  </div>
  <div class="panel">
    <div class="stat-title">Sudah Upload Dokumen PDF</div>
    <div class="stat-num"><?= $totalAdaDok; ?> PDF</div>
    <div class="cell-sub"><a href="dokumen_pelanggan.php?status=ADA">Filter Sudah Upload &rarr;</a></div>
  </div>
  <div class="panel panel-yellow">
    <div class="stat-title">Belum Ada Dokumen PDF</div>
    <div class="stat-num"><?= $totalBelumDok; ?> Orang</div>
    <div class="cell-sub"><a href="dokumen_pelanggan.php?status=BELUM">Filter Belum Upload &rarr;</a></div>
  </div>
</div>

<?php if ($previewDok): ?>
<div class="panel no-print">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('eye', 16); ?> Preview Dokumen PDF: <?= e($previewDok['nama_nasabah']); ?> (NIK: <span class="mono"><?= e($previewDok['nik_nasabah']); ?></span>)</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="dokumen_pelanggan.php?lihat=1&nik=<?= urlencode($previewDok['nik_nasabah']); ?>" target="_blank" class="btn btn-sm btn-primary"><?= svg_icon('eye', 13); ?> Buka Tab Baru</a>
      <a href="dokumen_pelanggan.php?download=1&nik=<?= urlencode($previewDok['nik_nasabah']); ?>" class="btn btn-sm"><?= svg_icon('download', 13); ?> Download PDF</a>
      <a href="data_pelanggan.php?q=<?= urlencode($previewDok['nik_nasabah']); ?>" class="btn btn-sm"><?= svg_icon('users', 13); ?> Data Pelanggan</a>
      <a href="dokumen_pelanggan.php" class="btn btn-sm">Tutup Preview</a>
    </div>
  </div>
  <div class="cell-sub" style="margin-bottom:8px;">
    File: <strong class="mono"><?= e($previewDok['nama_file_asli']); ?></strong> (<?= format_ukuran_file($previewDok['ukuran_byte']); ?>) &middot;
    Keterangan: <?= e($previewDok['keterangan']); ?> &middot;
    Diperbarui: <span class="mono"><?= e($previewDok['updated_at']); ?></span> oleh <strong><?= e($previewDok['nama_kasir']); ?></strong>
  </div>
  <iframe src="dokumen_pelanggan.php?lihat=1&nik=<?= urlencode($previewDok['nik_nasabah']); ?>" style="width:100%;height:520px;border:1px solid #d8e2dc;border-radius:7px;background:#f8fbf9;" title="Preview Dokumen PDF"></iframe>
</div>
<?php endif; ?>

<div class="panel panel-yellow no-print" id="form-dokumen">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('upload', 16); ?> Upload / Ganti Dokumen Pelanggan (1 PDF per Orang, Ukuran Tidak Dibatasi)</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="gadai_barang.php" class="btn btn-sm btn-primary"><?= svg_icon('plus-circle', 13); ?> Gadai Baru (+ Upload PDF)</a>
      <a href="data_pelanggan.php" class="btn btn-sm"><?= svg_icon('users', 13); ?> Data Pelanggan</a>
    </div>
  </div>
  <form method="POST" enctype="multipart/form-data" onsubmit="const b=this.querySelector('button[type=submit]');if(b){setTimeout(()=>{b.disabled=true;},0);}">
    <input type="hidden" name="upload_dokumen" value="1">
    <div class="form-grid" style="margin-bottom:10px;">
      <div class="field">
        <label>Pilih Pelanggan (Rahin)</label>
        <select name="nik_nasabah" required>
          <option value="">-- Pilih Pelanggan --</option>
          <?php foreach ($customers as $c):
            $hasDok = isset($dokMap[$c['nik_nasabah']]);
          ?>
            <option value="<?= e($c['nik_nasabah']); ?>" <?= $selectedNik === $c['nik_nasabah'] ? 'selected' : ''; ?>>
              <?= e($c['nama_nasabah']); ?> — <?= e($c['nik_nasabah']); ?> (<?= $hasDok ? 'Sudah Ada PDF - Upload Akan Mengganti' : 'Belum Ada PDF'; ?>)
            </option>
          <?php endforeach; ?>
        </select>
      </div>
      <div class="field">
        <label>File PDF Bukti Data Diri &amp; Lainnya (.pdf)</label>
        <input type="file" name="dokumen_pdf" accept=".pdf,application/pdf" required>
      </div>
      <div class="field">
        <label>Keterangan Isi Dokumen</label>
        <input type="text" name="keterangan" value="Bukti Data Diri (KTP/KK) &amp; Dokumen Pendukung" placeholder="Contoh: KTP, KK, SIM, BPKB">
      </div>
    </div>
    <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;">
      <span class="cell-sub">Maksimal 1 file PDF per pelanggan (ukuran file tidak dibatasi). Mengunggah file baru otomatis menggantikan PDF lama pelanggan tersebut.</span>
      <button type="submit" name="upload_dokumen" class="btn btn-primary"><?= svg_icon('upload', 14); ?> Simpan / Ganti Dokumen PDF</button>
    </div>
  </form>
</div>

<div class="panel no-print">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('folder', 16); ?> Arsip Dokumen Pelanggan (Terhubung ke Data Pelanggan &amp; Gadai)</div>
  </div>
  <form method="GET" style="display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px;">
    <input type="text" name="q" value="<?= e($q); ?>" placeholder="Cari nama pelanggan, NIK, HP, atau nama file..." style="flex:1;min-width:220px;padding:7px 10px;border:1px solid #c6d3cb;border-radius:6px;">
    <select name="status" style="padding:7px 10px;border:1px solid #c6d3cb;border-radius:6px;">
      <option value="">Semua Status Dokumen</option>
      <option value="ADA" <?= $statusFilter === 'ADA' ? 'selected' : ''; ?>>Sudah Ada PDF</option>
      <option value="BELUM" <?= $statusFilter === 'BELUM' ? 'selected' : ''; ?>>Belum Upload PDF</option>
    </select>
    <button type="submit" class="btn btn-sm btn-primary">Cari / Filter</button>
    <?php if ($q !== '' || $statusFilter !== ''): ?><a href="dokumen_pelanggan.php" class="btn btn-sm">Reset</a><?php endif; ?>
  </form>
  <div class="table-box">
    <table>
      <thead>
        <tr>
          <th>Pelanggan (Rahin) &amp; NIK</th>
          <th>Kontak &amp; Alamat</th>
          <th>Akad Terkait</th>
          <th>Dokumen PDF (Maks. 1 / Orang)</th>
          <th>Aksi</th>
        </tr>
      </thead>
      <tbody>
        <?php if (empty($filtered)): ?>
          <tr><td colspan="5" class="cell-sub" style="text-align:center;padding:18px;">Belum ada data pelanggan yang sesuai filter.</td></tr>
        <?php endif; ?>
        <?php foreach ($filtered as $c):
          $dok = $c['dokumen'];
          $gadaiUrl = "gadai_barang.php?nasabah=" . urlencode($c['nama_nasabah']) . "&nik=" . urlencode($c['nik_nasabah']) . "&hp=" . urlencode($c['no_hp']) . "&alamat=" . urlencode($c['alamat_nasabah']);
        ?>
        <tr>
          <td>
            <a href="data_pelanggan.php?q=<?= urlencode($c['nik_nasabah']); ?>"><strong><?= e($c['nama_nasabah']); ?></strong></a>
            <div class="cell-sub mono">NIK: <?= e($c['nik_nasabah']); ?></div>
          </td>
          <td>
            <span class="mono"><?= e($c['no_hp']); ?></span>
            <div class="cell-sub"><?= e($c['alamat_nasabah']); ?></div>
          </td>
          <td class="mono">
            <a href="data_pelanggan.php?q=<?= urlencode($c['nik_nasabah']); ?>"><?= (int)$c['akad_aktif']; ?> Aktif / <?= (int)$c['tot_akad']; ?> Akad</a>
          </td>
          <td>
            <?php if ($dok): ?>
              <span class="status-tag" style="background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0;">PDF TERSEDIA</span>
              <div style="margin-top:4px;font-weight:600;word-break:break-all;"><?= e($dok['nama_file_asli']); ?></div>
              <div class="cell-sub mono"><?= format_ukuran_file($dok['ukuran_byte']); ?> &middot; <?= e($dok['updated_at']); ?></div>
              <div class="cell-sub"><?= e($dok['keterangan']); ?> (Oleh: <?= e($dok['nama_kasir']); ?>)</div>
            <?php else: ?>
              <span class="status-tag" style="background:#fffbeb;color:#92400e;border:1px solid #fde68a;">BELUM UPLOAD</span>
              <div class="cell-sub" style="margin-top:3px;">Belum ada file PDF bukti data diri.</div>
            <?php endif; ?>
          </td>
          <td>
            <div style="display:flex;gap:5px;flex-wrap:wrap;">
              <?php if ($dok): ?>
                <a class="btn btn-sm btn-primary" href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($c['nik_nasabah']); ?>"><?= svg_icon('eye', 13); ?> Preview</a>
                <a class="btn btn-sm" href="dokumen_pelanggan.php?lihat=1&nik=<?= urlencode($c['nik_nasabah']); ?>" target="_blank"><?= svg_icon('file-text', 13); ?> Buka</a>
                <a class="btn btn-sm" href="dokumen_pelanggan.php?download=1&nik=<?= urlencode($c['nik_nasabah']); ?>"><?= svg_icon('download', 13); ?> Unduh</a>
                <a class="btn btn-sm btn-yellow" href="dokumen_pelanggan.php?nik=<?= urlencode($c['nik_nasabah']); ?>#form-dokumen"><?= svg_icon('edit', 13); ?> Ganti PDF</a>
                <form method="POST" style="display:inline;" onsubmit="return confirm('Hapus dokumen PDF milik <?= e($c['nama_nasabah']); ?>?')">
                  <input type="hidden" name="nik_nasabah" value="<?= e($c['nik_nasabah']); ?>">
                  <button type="submit" name="delete_dokumen" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button>
                </form>
              <?php else: ?>
                <a class="btn btn-sm btn-primary" href="dokumen_pelanggan.php?nik=<?= urlencode($c['nik_nasabah']); ?>#form-dokumen"><?= svg_icon('upload', 13); ?> Upload PDF</a>
              <?php endif; ?>
              <a class="btn btn-sm" href="<?= e($gadaiUrl); ?>"><?= svg_icon('plus-circle', 13); ?> Gadai Baru</a>
            </div>
          </td>
        </tr>
        <?php endforeach; ?>
      </tbody>
    </table>
  </div>
</div>
<?php require_once "includes/footer.php"; ?>
