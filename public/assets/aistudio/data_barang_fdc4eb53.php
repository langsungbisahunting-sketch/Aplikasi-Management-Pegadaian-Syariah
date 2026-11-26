<?php
require_once "config/koneksi.php";
require_owner();
$page_title = "Data Barang Jaminan (Marhun)";
$cu = current_user();
$ok = ""; $err = "";

if (isset($_GET['template']) && $_GET['template'] === 'xlsx') {
    SyariahXlsx::download('template_gadai_syariah.xlsx', [
        ['name' => 'Template_Gadai', 'rows' => [
            ['No_Akad','Tanggal_Akad','Nama_Nasabah','NIK','No_HP','Alamat','Kategori','Nama_Barang','Nilai_Taksiran','Pokok_Pinjaman','Spesifikasi','Kondisi'],
            ['AG-XLS-001', date('Y-m-d'), 'Contoh Nasabah', '3175011203940001', '081234567890', 'Jl. Contoh No 1', 'HP', 'Samsung S24', 8000000, 6000000, 'RAM 8/256 IMEI 35698211', 'Mulus normal']
        ]],
        ['name' => 'Panduan', 'rows' => [
            ['Kolom', 'Keterangan'],
            ['Tanggal_Akad', 'Wajib format YYYY-MM-DD'],
            ['Kategori', 'Hanya HP, Motor, atau Emas']
        ]]
    ]);
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['delete_gadai'])) {
    $did = (int)($_POST['id'] ?? 0);
    $stD = mysqli_prepare($conn, "SELECT no_akad FROM transaksi_gadai WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stD, "i", $did); mysqli_stmt_execute($stD);
    $rowD = mysqli_fetch_assoc(mysqli_stmt_get_result($stD)); mysqli_stmt_close($stD);
    if ($rowD) {
        $nak = $rowD['no_akad'];
        $d1 = mysqli_prepare($conn, "DELETE FROM riwayat_perpanjangan WHERE no_akad=?");
        mysqli_stmt_bind_param($d1, "s", $nak); mysqli_stmt_execute($d1); mysqli_stmt_close($d1);
        $d2 = mysqli_prepare($conn, "DELETE FROM jurnal_akuntansi WHERE no_akad=?");
        mysqli_stmt_bind_param($d2, "s", $nak); mysqli_stmt_execute($d2); mysqli_stmt_close($d2);
        $d3 = mysqli_prepare($conn, "DELETE FROM transaksi_gadai WHERE id=?");
        mysqli_stmt_bind_param($d3, "i", $did); mysqli_stmt_execute($d3); mysqli_stmt_close($d3);
        $ok = "Barang &amp; akad <strong>" . e($nak) . "</strong> beserta jurnal terkait dihapus.";
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['import_xlsx'])) {
    if (empty($_FILES['xlsx_file']['tmp_name'])) {
        $err = "Pilih file .xlsx terlebih dahulu.";
    } elseif (strtolower(pathinfo($_FILES['xlsx_file']['name'], PATHINFO_EXTENSION)) !== 'xlsx') {
        $err = "Hanya menerima file Microsoft Excel asli (.xlsx).";
    } else {
        $parsed = SyariahXlsx::parseRows($_FILES['xlsx_file']['tmp_name']);
        if (!$parsed['ok']) {
            $err = $parsed['error'];
        } else {
            $rows = $parsed['rows'];
            $imported = 0; $errs = [];
            for ($i = 1; $i < count($rows); $i++) {
                $r = $rows[$i];
                $no = trim($r[0] ?? ''); $tgl = trim($r[1] ?? ''); $nama = trim($r[2] ?? ''); $nik = trim($r[3] ?? ''); $hp = trim($r[4] ?? ''); $alm = trim($r[5] ?? ''); $kat = trim($r[6] ?? ''); $brg = trim($r[7] ?? ''); $tak = (float)($r[8] ?? 0); $pok = (float)($r[9] ?? 0); $spek = trim($r[10] ?? ''); $kond = trim($r[11] ?? 'Baik');
                if ($no === '' || !valid_iso_date($tgl) || $nama === '' || !in_array($kat, ['HP','Motor','Emas'], true) || $tak <= 0 || $pok <= 0 || $spek === '') {
                    $errs[] = "Baris " . ($i + 1) . ": Data wajib tidak lengkap atau format salah.";
                    continue;
                }
                if ($brg === '') $brg = $kat . ' Jaminan';
                if (is_date_closed($conn, $tgl)) { $errs[] = "Baris " . ($i + 1) . ": Tanggal $tgl sudah CLOSED."; continue; }
                $chk = mysqli_prepare($conn, "SELECT id FROM transaksi_gadai WHERE no_akad=? LIMIT 1");
                mysqli_stmt_bind_param($chk, "s", $no); mysqli_stmt_execute($chk);
                $dup = mysqli_fetch_assoc(mysqli_stmt_get_result($chk)); mysqli_stmt_close($chk);
                if ($dup) { $errs[] = "Baris " . ($i + 1) . ": No. Akad $no duplikat."; continue; }

                $ltv = get_active_ltv($conn, $kat);
                $plafon = round($tak * ((float)$ltv['persentase'] / 100));
                if ($pok > $plafon) { $errs[] = "Baris " . ($i + 1) . ": Pokok melebihi LTV {$ltv['persentase']}%."; continue; }
                $pkgs = get_active_ujrah_packages($conn, $kat);
                if (empty($pkgs)) { $errs[] = "Baris " . ($i + 1) . ": Paket Ujrah aktif untuk $kat tidak tersedia."; continue; }
                $pkg = $pkgs[0];
                $jt = date('Y-m-d', strtotime("$tgl +{$pkg['hari_per_periode']} days"));
                $adm = (float)$pkg['biaya_admin'];
                $bersih = max(0, $pok - $adm);
                $ins = mysqli_prepare($conn, "INSERT INTO transaksi_gadai (no_akad, tanggal_akad, jatuh_tempo, nama_nasabah, nik_nasabah, no_hp, alamat_nasabah, kategori_barang, nama_barang, spesifikasi_ringkas, kondisi_barang, nilai_taksiran, ltv_persen, plafon_maksimal, pokok_pinjaman, biaya_admin, dana_bersih_diterima, ujrah_package_id, ujrah_package_nama, biaya_ujrah_periode, hari_per_periode, status_gadai, user_id, nama_kasir) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'AKTIF',?,?)");
                mysqli_stmt_bind_param($ins, "sssssssssssddddddisdiis", $no, $tgl, $jt, $nama, $nik, $hp, $alm, $kat, $brg, $spek, $kond, $tak, $ltv['persentase'], $plafon, $pok, $adm, $bersih, $pkg['id'], $pkg['nama_paket'], $pkg['ujrah_per_periode'], $pkg['hari_per_periode'], $cu['id'], $cu['nama_lengkap']);
                if (mysqli_stmt_execute($ins)) {
                    $imported++;
                    $ket1 = "Import Gadai $no - $nama";
                    $j1 = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pokok_nominal, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'GADAI_BARANG', 'KELUAR', 'Piutang Qardh', 'Kas Operasional', ?, ?, ?, ?, ?)");
                    mysqli_stmt_bind_param($j1, "ssddsis", $tgl, $no, $pok, $pok, $ket1, $cu['id'], $cu['nama_lengkap']);
                    mysqli_stmt_execute($j1); mysqli_stmt_close($j1);
                    if ($adm > 0) {
                        $ket2 = "Biaya administrasi awal $no";
                        $j2 = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pendapatan_admin, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'GADAI_BARANG', 'MASUK', 'Kas Operasional', 'Pendapatan Admin Riil', ?, ?, ?, ?, ?)");
                        mysqli_stmt_bind_param($j2, "ssddsis", $tgl, $no, $adm, $adm, $ket2, $cu['id'], $cu['nama_lengkap']);
                        mysqli_stmt_execute($j2); mysqli_stmt_close($j2);
                    }
                }
                mysqli_stmt_close($ins);
            }
            $ok = "Import .xlsx selesai: <strong>$imported baris</strong> berhasil." . (count($errs) ? " Error: " . e(implode(' ; ', $errs)) : '');
        }
    }
}

$q = trim($_GET['q'] ?? '');
$stFilter = trim($_GET['status'] ?? '');
$res = mysqli_query($conn, "SELECT * FROM transaksi_gadai ORDER BY id DESC");
$list = [];
while ($row = mysqli_fetch_assoc($res)) {
    if ($q !== '' && stripos($row['no_akad'].$row['nama_nasabah'].$row['nama_barang'].$row['spesifikasi_ringkas'], $q) === false) continue;
    if ($stFilter !== '') {
        if ($stFilter === 'AKTIF' && !in_array($row['status_gadai'], ['AKTIF','DIPERPANJANG'], true)) continue;
        elseif ($stFilter !== 'AKTIF' && $row['status_gadai'] !== $stFilter) continue;
    }
    $list[] = $row;
}

if (isset($_GET['export']) && $_GET['export'] === 'xlsx') {
    $xlsRows = [['No. Akad','Tanggal','Jatuh Tempo','Rahin','Kategori','Barang','Taksiran (Rp)','Pokok Qardh (Rp)','Ujrah (Rp)','Status','Kasir']];
    foreach ($list as $r) {
        $xlsRows[] = [$r['no_akad'],$r['tanggal_akad'],$r['jatuh_tempo'],$r['nama_nasabah'],$r['kategori_barang'],$r['nama_barang'],(float)$r['nilai_taksiran'],(float)$r['pokok_pinjaman'],(float)$r['biaya_ujrah_periode'],$r['status_gadai'],$r['nama_kasir']];
    }
    SyariahXlsx::download('data_barang_marhun_' . date('Y-m-d') . '.xlsx', [['name' => 'Data_Barang', 'rows' => $xlsRows]]);
}
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok no-print"><?= $ok; ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err no-print"><?= e($err); ?></div><?php endif; ?>
<div class="panel panel-yellow no-print">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('package', 16); ?> Master Data Barang (Marhun) &amp; Excel (.xlsx)</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a class="btn btn-sm btn-primary" href="gadai_barang.php"><?= svg_icon('plus-circle', 14); ?> Tambah Barang / Akad</a>
      <a class="btn btn-sm" href="data_barang.php?export=xlsx&q=<?= urlencode($q); ?>&status=<?= urlencode($stFilter); ?>"><?= svg_icon('download', 14); ?> Export Excel (.xlsx)</a>
      <a class="btn btn-sm" href="data_barang.php?template=xlsx"><?= svg_icon('download', 14); ?> Download Template (.xlsx)</a>
      <button type="button" class="btn btn-sm" onclick="window.print()"><?= svg_icon('printer', 14); ?> Cetak</button>
    </div>
  </div>
  <div style="display:flex;gap:12px;flex-wrap:wrap;justify-content:space-between;align-items:center;">
    <form method="GET" style="display:flex;gap:8px;flex-wrap:wrap;align-items:center;">
      <input type="text" name="q" value="<?= e($q); ?>" placeholder="Cari akad, rahin, barang..." style="padding:7px 10px;border:1px solid #c6d3cb;border-radius:6px;">
      <select name="status" style="padding:7px 10px;border:1px solid #c6d3cb;border-radius:6px;">
        <option value="">Semua Status</option>
        <option value="AKTIF" <?= $stFilter==='AKTIF'?'selected':''; ?>>AKTIF &amp; DIPERPANJANG</option>
        <option value="DIPERPANJANG" <?= $stFilter==='DIPERPANJANG'?'selected':''; ?>>DIPERPANJANG</option>
        <option value="LUNAS" <?= $stFilter==='LUNAS'?'selected':''; ?>>LUNAS</option>
        <option value="LELANG" <?= $stFilter==='LELANG'?'selected':''; ?>>LELANG</option>
      </select>
      <button type="submit" class="btn btn-sm btn-primary">Filter</button>
      <?php if($q !== '' || $stFilter !== ''): ?><a href="data_barang.php" class="btn btn-sm">Reset</a><?php endif; ?>
    </form>
    <form method="POST" enctype="multipart/form-data" style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;">
      <input type="file" name="xlsx_file" accept=".xlsx" required>
      <button type="submit" name="import_xlsx" class="btn btn-sm btn-yellow">Upload &amp; Import .xlsx</button>
    </form>
  </div>
</div>
<div class="panel"><div class="table-box"><table><thead><tr><th>No. Akad</th><th>Kategori &amp; Barang</th><th>Rahin</th><th>Pokok Qardh</th><th>Ujrah</th><th>Status</th><th class="no-print">Aksi</th></tr></thead><tbody>
<?php foreach($list as $r): ?>
<tr><td class="mono"><a href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['no_akad']); ?></strong></a><div class="cell-sub"><?= e($r['tanggal_akad']); ?> &middot; <a href="laporan_akuntansi.php?user_id=<?= (int)$r['user_id']; ?>"><?= e($r['nama_kasir']); ?></a></div></td>
<td><strong><?= e($r['kategori_barang']); ?></strong> - <?= e($r['nama_barang']); ?><div class="cell-sub"><?= e($r['spesifikasi_ringkas']); ?></div></td>
<td><a href="data_pelanggan.php?q=<?= urlencode($r['nama_nasabah']); ?>"><?= e($r['nama_nasabah']); ?></a></td><td class="mono"><?= rupiah($r['pokok_pinjaman']); ?></td><td class="mono"><?= rupiah($r['biaya_ujrah_periode']); ?></td><td><?= badge_status($r['status_gadai']); ?></td>
<td class="no-print"><div style="display:flex;gap:5px;flex-wrap:wrap;"><a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('printer', 13); ?> Nota</a><?php if(in_array($r['status_gadai'], ['AKTIF','DIPERPANJANG'], true)): ?><a class="btn btn-sm" href="perpanjang_gadai.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('refresh', 13); ?> Perpanjang</a><a class="btn btn-sm" href="tebus_barang.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('check-circle', 13); ?> Tebus</a><?php endif; ?><a class="btn btn-sm" href="gadai_barang.php?edit=<?= (int)$r['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><form method="POST" style="display:inline;" onsubmit="return confirm('Hapus akad <?= e($r['no_akad']); ?>?')"><input type="hidden" name="id" value="<?= (int)$r['id']; ?>"><button type="submit" name="delete_gadai" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form></div></td></tr>
<?php endforeach; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
