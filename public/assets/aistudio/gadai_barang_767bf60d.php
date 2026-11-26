<?php
require_once "config/koneksi.php";
require_login();
$page_title = "Gadai Barang Baru";
$cu = current_user();
$ok_msg = ""; $err_msg = ""; $nota = null; $editTx = null;

$prefill_nama = trim($_GET['nasabah'] ?? '');
$prefill_nik = trim($_GET['nik'] ?? '');
$prefill_hp = trim($_GET['hp'] ?? '');
$prefill_alamat = trim($_GET['alamat'] ?? '');

if (!empty($_GET['cetak'])) {
    $st = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE no_akad = ? LIMIT 1");
    mysqli_stmt_bind_param($st, "s", $_GET['cetak']);
    mysqli_stmt_execute($st);
    $nota = mysqli_fetch_assoc(mysqli_stmt_get_result($st));
    mysqli_stmt_close($st);
}

$packages_all = [];
$qp = mysqli_query($conn, "SELECT * FROM paket_ujrah WHERE aktif = 1 ORDER BY kategori, is_default DESC, id ASC");
while ($p = mysqli_fetch_assoc($qp)) $packages_all[] = $p;

$ltv_all = [];
$ltv_map = [];
$ql = mysqli_query($conn, "SELECT * FROM rumus_ltv WHERE aktif = 1 ORDER BY kategori, is_default DESC, id ASC");
while ($l = mysqli_fetch_assoc($ql)) {
    $ltv_all[] = $l;
    if (!isset($ltv_map[$l['kategori']])) {
        $ltv_map[$l['kategori']] = (float)$l['persentase'];
    }
}
foreach (['HP', 'Motor', 'Emas'] as $kCat) {
    if (!isset($ltv_map[$kCat])) {
        $defLtv = get_active_ltv($conn, $kCat);
        $ltv_map[$kCat] = (float)$defLtv['persentase'];
    }
}

if (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['delete_gadai'])) {
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
        $ok_msg = "Akad <strong>" . e($nak) . "</strong> beserta riwayat perpanjangan &amp; jurnal terkait berhasil dihapus.";
    } else {
        $err_msg = "Transaksi tidak ditemukan.";
    }
} elseif (is_owner() && $_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['update_gadai'])) {
    $eid = (int)($_POST['id'] ?? 0);
    $stOld = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stOld, "i", $eid); mysqli_stmt_execute($stOld);
    $old = mysqli_fetch_assoc(mysqli_stmt_get_result($stOld)); mysqli_stmt_close($stOld);
    if (!$old) {
        $err_msg = "Transaksi tidak ditemukan.";
    } else {
        $new_no = trim($_POST['no_akad'] ?? $old['no_akad']) ?: $old['no_akad'];
        $tgl_akad = valid_iso_date($_POST['tanggal_akad'] ?? '') ? $_POST['tanggal_akad'] : $old['tanggal_akad'];
        $jt = valid_iso_date($_POST['jatuh_tempo'] ?? '') ? $_POST['jatuh_tempo'] : $old['jatuh_tempo'];
        $nama = trim($_POST['nama_nasabah'] ?? $old['nama_nasabah']);
        $nik = trim($_POST['nik_nasabah'] ?? $old['nik_nasabah']);
        $hp = trim($_POST['no_hp'] ?? $old['no_hp']);
        $alamat = trim($_POST['alamat_nasabah'] ?? $old['alamat_nasabah']);
        $kat = in_array($_POST['kategori_barang'] ?? '', ['HP','Motor','Emas'], true) ? $_POST['kategori_barang'] : $old['kategori_barang'];
        $nama_brg = trim($_POST['nama_barang'] ?? $old['nama_barang']);
        $spek = trim($_POST['spesifikasi_ringkas'] ?? '');
        $kel = trim($_POST['kelengkapan_barang'] ?? '');
        $kond = trim($_POST['kondisi_barang'] ?? '');
        $tak = max(0, (float)($_POST['nilai_taksiran'] ?? $old['nilai_taksiran']));
        $ltv_p = max(1, (float)($_POST['ltv_persen'] ?? $old['ltv_persen']));
        $plaf = round($tak * ($ltv_p / 100));
        $pok = max(0, (float)($_POST['pokok_pinjaman'] ?? $old['pokok_pinjaman']));
        $adm = max(0, (float)($_POST['biaya_admin'] ?? $old['biaya_admin']));
        $bersih = max(0, $pok - $adm);
        $ujr = max(0, (float)($_POST['biaya_ujrah_periode'] ?? $old['biaya_ujrah_periode']));
        $hari = max(1, (int)($_POST['hari_per_periode'] ?? $old['hari_per_periode']));
        $st_g = in_array($_POST['status_gadai'] ?? '', ['AKTIF','DIPERPANJANG','LUNAS','LELANG'], true) ? $_POST['status_gadai'] : $old['status_gadai'];

        $dup = null;
        if ($new_no !== $old['no_akad']) {
            $chk = mysqli_prepare($conn, "SELECT id FROM transaksi_gadai WHERE no_akad=? AND id<>? LIMIT 1");
            mysqli_stmt_bind_param($chk, "si", $new_no, $eid); mysqli_stmt_execute($chk);
            $dup = mysqli_fetch_assoc(mysqli_stmt_get_result($chk)); mysqli_stmt_close($chk);
        }
        if ($dup) {
            $err_msg = "Nomor akad $new_no sudah dipakai transaksi lain.";
        } elseif ($nama === '' || $nama_brg === '' || $pok <= 0) {
            $err_msg = "Nama nasabah, nama barang, dan pokok pinjaman wajib valid.";
        } else {
            $upd = mysqli_prepare($conn, "UPDATE transaksi_gadai SET no_akad=?, tanggal_akad=?, jatuh_tempo=?, nama_nasabah=?, nik_nasabah=?, no_hp=?, alamat_nasabah=?, kategori_barang=?, nama_barang=?, spesifikasi_ringkas=?, kelengkapan_barang=?, kondisi_barang=?, nilai_taksiran=?, ltv_persen=?, plafon_maksimal=?, pokok_pinjaman=?, biaya_admin=?, dana_bersih_diterima=?, biaya_ujrah_periode=?, hari_per_periode=?, status_gadai=? WHERE id=?");
            mysqli_stmt_bind_param($upd, "ssssssssssssdddddddisi", $new_no, $tgl_akad, $jt, $nama, $nik, $hp, $alamat, $kat, $nama_brg, $spek, $kel, $kond, $tak, $ltv_p, $plaf, $pok, $adm, $bersih, $ujr, $hari, $st_g, $eid);
            mysqli_stmt_execute($upd); mysqli_stmt_close($upd);

            if ($new_no !== $old['no_akad']) {
                $u1 = mysqli_prepare($conn, "UPDATE riwayat_perpanjangan SET no_akad=? WHERE no_akad=?");
                mysqli_stmt_bind_param($u1, "ss", $new_no, $old['no_akad']); mysqli_stmt_execute($u1); mysqli_stmt_close($u1);
                $u2 = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET no_akad=? WHERE no_akad=?");
                mysqli_stmt_bind_param($u2, "ss", $new_no, $old['no_akad']); mysqli_stmt_execute($u2); mysqli_stmt_close($u2);
            }
            $ket1 = "Pencairan pokok Qardh $new_no - $nama";
            $uj1 = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET tanggal=?, pokok_nominal=?, total_kas=?, keterangan=? WHERE no_akad=? AND jenis_menu='GADAI_BARANG' AND arus_kas='KELUAR'");
            mysqli_stmt_bind_param($uj1, "sddss", $tgl_akad, $pok, $pok, $ket1, $new_no);
            mysqli_stmt_execute($uj1); mysqli_stmt_close($uj1);
            if ($nama !== $old['nama_nasabah']) {
                $ketTeb = "Pelunasan tebus $new_no - $nama";
                $ujT = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET keterangan=? WHERE no_akad=? AND jenis_menu='TEBUS_BARANG'");
                mysqli_stmt_bind_param($ujT, "ss", $ketTeb, $new_no);
                mysqli_stmt_execute($ujT); mysqli_stmt_close($ujT);
            }

            $chkAdm = mysqli_prepare($conn, "SELECT id FROM jurnal_akuntansi WHERE no_akad=? AND jenis_menu='GADAI_BARANG' AND arus_kas='MASUK' LIMIT 1");
            mysqli_stmt_bind_param($chkAdm, "s", $new_no); mysqli_stmt_execute($chkAdm);
            $admRow = mysqli_fetch_assoc(mysqli_stmt_get_result($chkAdm)); mysqli_stmt_close($chkAdm);
            if ($adm > 0) {
                $ket2 = "Biaya administrasi awal $new_no";
                if ($admRow) {
                    $uj2 = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET tanggal=?, pendapatan_admin=?, total_kas=?, keterangan=? WHERE id=?");
                    mysqli_stmt_bind_param($uj2, "sddsi", $tgl_akad, $adm, $adm, $ket2, $admRow['id']);
                    mysqli_stmt_execute($uj2); mysqli_stmt_close($uj2);
                } else {
                    $ij2 = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pendapatan_admin, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'GADAI_BARANG', 'MASUK', 'Kas Operasional', 'Pendapatan Admin Riil', ?, ?, ?, ?, ?)");
                    mysqli_stmt_bind_param($ij2, "ssddsis", $tgl_akad, $new_no, $adm, $adm, $ket2, $cu['id'], $cu['nama_lengkap']);
                    mysqli_stmt_execute($ij2); mysqli_stmt_close($ij2);
                }
            } elseif ($admRow) {
                mysqli_query($conn, "DELETE FROM jurnal_akuntansi WHERE id=" . (int)$admRow['id']);
            }
            $ok_msg = "Perubahan akad <strong>" . e($new_no) . "</strong> disimpan &amp; disinkronkan ke jurnal.";
        }
    }
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['simpan_gadai'])) {
    $idem = $_POST['idempotency_token'] ?? '';
    $tgl = valid_iso_date($_POST['tanggal_akad'] ?? '') ? $_POST['tanggal_akad'] : date('Y-m-d');
    if (!check_and_record_idempotency($conn, $idem)) {
        $err_msg = "Transaksi sudah diproses sebelumnya (pencegahan double submit).";
    } elseif (is_date_closed($conn, $tgl)) {
        $err_msg = "Sesi operasional tanggal $tgl sudah DITUTUP & TERKUNCI (Tutup Toko).";
    } else {
        $no_akad = trim($_POST['no_akad'] ?? '') ?: generate_no_akad($conn);
        $chkDup = mysqli_prepare($conn, "SELECT id FROM transaksi_gadai WHERE no_akad = ? LIMIT 1");
        mysqli_stmt_bind_param($chkDup, "s", $no_akad);
        mysqli_stmt_execute($chkDup);
        $hasDup = mysqli_fetch_assoc(mysqli_stmt_get_result($chkDup));
        mysqli_stmt_close($chkDup);

        $nama = trim($_POST['nama_nasabah'] ?? '');
        $nik = trim($_POST['nik_nasabah'] ?? '');
        $hp = trim($_POST['no_hp'] ?? '');
        $alamat = trim($_POST['alamat_nasabah'] ?? '');
        $kat = in_array($_POST['kategori_barang'] ?? '', ['HP','Motor','Emas'], true) ? $_POST['kategori_barang'] : 'HP';
        $kondisi = trim($_POST['kondisi_barang'] ?? '');
        $taksiran = max(0, (float)($_POST['nilai_taksiran'] ?? 0));
        $pokok = max(0, (float)($_POST['pokok_pinjaman'] ?? 0));

        $ltv = get_active_ltv($conn, $kat);
        $ltv_pct = (float)$ltv['persentase'];
        $plafon = round($taksiran * ($ltv_pct / 100));

        $pkg_id = (int)($_POST['ujrah_package_id'] ?? 0);
        $st_pkg = mysqli_prepare($conn, "SELECT * FROM paket_ujrah WHERE id = ? AND kategori = ? AND aktif = 1 LIMIT 1");
        mysqli_stmt_bind_param($st_pkg, "is", $pkg_id, $kat);
        mysqli_stmt_execute($st_pkg);
        $pkg = mysqli_fetch_assoc(mysqli_stmt_get_result($st_pkg));
        mysqli_stmt_close($st_pkg);
        if (!$pkg) {
            $listP = get_active_ujrah_packages($conn, $kat);
            $pkg = $listP[0] ?? null;
        }

        $hasPdfUpload = isset($_FILES['dokumen_pdf']) && (int)($_FILES['dokumen_pdf']['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_NO_FILE;
        $pdfCheck = $hasPdfUpload ? validate_pdf_upload($_FILES['dokumen_pdf']) : ['ok' => true, 'empty' => true];

        if ($hasDup) {
            $err_msg = "Nomor akad <strong>" . e($no_akad) . "</strong> sudah terdaftar. Gunakan nomor akad lain.";
        } elseif ($nama === '' || $nik === '' || $hp === '' || $alamat === '' || $kondisi === '' || $taksiran <= 0 || $pokok <= 0) {
            $err_msg = "Semua identitas Rahin, kondisi barang, taksiran, dan pokok pinjaman wajib diisi.";
        } elseif ($pokok > $plafon) {
            $err_msg = "Pokok pinjaman melebihi plafon LTV {$ltv_pct}% (" . rupiah($plafon) . ").";
        } elseif (!$pkg) {
            $err_msg = "Paket tarif Ujrah aktif untuk kategori $kat belum tersedia.";
        } elseif ($hasPdfUpload && !$pdfCheck['ok']) {
            $err_msg = $pdfCheck['error'];
        } else {
            $nama_brg = $kat . " Jaminan"; $spek = ""; $kel = "";
            if ($kat === 'HP') {
                $m = trim($_POST['hp_merk'] ?? ''); $r = trim($_POST['hp_ram'] ?? ''); $im = trim($_POST['hp_imei'] ?? '');
                $nama_brg = trim($m . ($r !== '' ? " ($r)" : '')) ?: "HP Jaminan";
                $spek = trim("RAM " . ($r ?: '-') . " · IMEI: " . ($im ?: '-'));
                $kel = implode(', ', array_filter([isset($_POST['hp_unit'])?'Unit':'', isset($_POST['hp_dus'])?'Dus':'', isset($_POST['hp_charger'])?'Charger':''])) ?: 'Unit';
            } elseif ($kat === 'Motor') {
                $m = trim($_POST['motor_merk'] ?? ''); $th = trim($_POST['motor_tahun'] ?? ''); $wr = trim($_POST['motor_warna'] ?? '');
                $np = strtoupper(trim($_POST['motor_nopol'] ?? '')); $rk = strtoupper(trim($_POST['motor_rangka'] ?? '')); $ms = strtoupper(trim($_POST['motor_mesin'] ?? ''));
                $tp = $_POST['motor_titipan'] ?? 'Titip Unit & Dokumen';
                $nama_brg = trim($m . ($th !== '' ? " ($th)" : '')) ?: "Motor Jaminan";
                $spek = trim("$wr · Plat $np · Rangka $rk · Mesin $ms");
                $kel = "$tp (" . (implode(' & ', array_filter([isset($_POST['motor_bpkb'])?'BPKB':'', isset($_POST['motor_stnk'])?'STNK':''])) ?: 'Tanpa Dokumen') . ")";
            } else {
                $jn = trim($_POST['emas_jenis'] ?? 'Perhiasan'); $kr = trim($_POST['emas_karat'] ?? '24K'); $br = (float)($_POST['emas_berat'] ?? 0);
                $nama_brg = "$jn {$br}gr ($kr)"; $spek = "$jn · $kr · {$br} gram";
                $kel = isset($_POST['emas_surat']) ? 'Surat/Sertifikat Lengkap' : 'Tanpa Surat';
            }
            $hari = max(1, (int)$pkg['hari_per_periode']);
            $jt = date('Y-m-d', strtotime("$tgl +{$hari} days"));
            $adm = (float)$pkg['biaya_admin'];
            $ujrah = (float)$pkg['ujrah_per_periode'];
            $bersih = max(0, $pokok - $adm);
            $detail_json = json_encode($_POST);

            $ins = mysqli_prepare($conn, "INSERT INTO transaksi_gadai (no_akad, tanggal_akad, jatuh_tempo, nama_nasabah, nik_nasabah, no_hp, alamat_nasabah, kategori_barang, nama_barang, spesifikasi_ringkas, kelengkapan_barang, kondisi_barang, detail_json, nilai_taksiran, ltv_persen, plafon_maksimal, pokok_pinjaman, biaya_admin, dana_bersih_diterima, ujrah_package_id, ujrah_package_nama, biaya_ujrah_periode, hari_per_periode, status_gadai, user_id, nama_kasir) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'AKTIF',?,?)");
            mysqli_stmt_bind_param($ins, "sssssssssssssddddddisdiis", $no_akad, $tgl, $jt, $nama, $nik, $hp, $alamat, $kat, $nama_brg, $spek, $kel, $kondisi, $detail_json, $taksiran, $ltv_pct, $plafon, $pokok, $adm, $bersih, $pkg['id'], $pkg['nama_paket'], $ujrah, $hari, $cu['id'], $cu['nama_lengkap']);
            if (mysqli_stmt_execute($ins)) {
                $ket1 = "Pencairan pokok Qardh $no_akad - $nama";
                $j1 = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pokok_nominal, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'GADAI_BARANG', 'KELUAR', 'Piutang Qardh', 'Kas Operasional', ?, ?, ?, ?, ?)");
                mysqli_stmt_bind_param($j1, "ssddsis", $tgl, $no_akad, $pokok, $pokok, $ket1, $cu['id'], $cu['nama_lengkap']);
                mysqli_stmt_execute($j1); mysqli_stmt_close($j1);

                if ($adm > 0) {
                    $ket2 = "Biaya administrasi awal $no_akad";
                    $j2 = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pendapatan_admin, total_kas, keterangan, user_id, nama_kasir) VALUES (?, ?, 'GADAI_BARANG', 'MASUK', 'Kas Operasional', 'Pendapatan Admin Riil', ?, ?, ?, ?, ?)");
                    mysqli_stmt_bind_param($j2, "ssddsis", $tgl, $no_akad, $adm, $adm, $ket2, $cu['id'], $cu['nama_lengkap']);
                    mysqli_stmt_execute($j2); mysqli_stmt_close($j2);
                }
                $dokMsg = "";
                if ($hasPdfUpload) {
                    $ketDok = trim($_POST['keterangan_dokumen'] ?? '');
                    $resDok = simpan_dokumen_pelanggan($conn, $nik, $nama, $_FILES['dokumen_pdf'], $ketDok, $cu);
                    if ($resDok['ok']) {
                        $dokMsg = " Dokumen PDF pelanggan (<strong>" . e($resDok['file_asli']) . "</strong>) ikut tersimpan.";
                    } else {
                        $dokMsg = " Namun upload dokumen gagal: " . e($resDok['error']);
                    }
                } else {
                    $existingDok = get_dokumen_pelanggan($conn, $nik);
                    if ($existingDok) {
                        $upTxD = mysqli_prepare($conn, "UPDATE transaksi_gadai SET file_dokumen=?, nama_file_asli=? WHERE no_akad=?");
                        if ($upTxD) {
                            mysqli_stmt_bind_param($upTxD, "sss", $existingDok['nama_file_simpan'], $existingDok['nama_file_asli'], $no_akad);
                            @mysqli_stmt_execute($upTxD);
                            mysqli_stmt_close($upTxD);
                        }
                    }
                }
                $ok_msg = "Akad Rahn <strong>" . e($no_akad) . "</strong> berhasil disimpan." . $dokMsg;
                $stN = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE no_akad = ? LIMIT 1");
                mysqli_stmt_bind_param($stN, "s", $no_akad); mysqli_stmt_execute($stN);
                $nota = mysqli_fetch_assoc(mysqli_stmt_get_result($stN)); mysqli_stmt_close($stN);
            } else {
                $err_msg = "Gagal menyimpan akad: " . mysqli_stmt_error($ins);
            }
            mysqli_stmt_close($ins);
        }
    }
}

if (is_owner() && isset($_GET['edit'])) {
    $eid = (int)$_GET['edit'];
    $stE = mysqli_prepare($conn, "SELECT * FROM transaksi_gadai WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stE, "i", $eid); mysqli_stmt_execute($stE);
    $editTx = mysqli_fetch_assoc(mysqli_stmt_get_result($stE)); mysqli_stmt_close($stE);
}

$next_akad = generate_no_akad($conn);
$idem_val = bin2hex(random_bytes(12));
$daftar = mysqli_query($conn, "SELECT * FROM transaksi_gadai ORDER BY id DESC LIMIT 30");
$dokMap = get_all_dokumen_map($conn);
$prefillDok = $prefill_nik !== '' ? ($dokMap[$prefill_nik] ?? null) : null;
require_once "includes/header.php";
?>
<?php if ($ok_msg): ?><div class="msg-ok no-print"><?= $ok_msg; ?></div><?php endif; ?>
<?php if ($err_msg): ?><div class="msg-err no-print"><?= $err_msg; ?></div><?php endif; ?>

<?php if ($editTx && is_owner()): ?>
<div class="panel panel-yellow no-print">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 16); ?> Edit Data Akad &amp; Jaminan: <?= e($editTx['no_akad']); ?></div><a href="gadai_barang.php" class="btn btn-sm">Batal Edit</a></div>
  <form method="POST">
    <input type="hidden" name="id" value="<?= (int)$editTx['id']; ?>">
    <div class="form-grid" style="margin-bottom:12px;">
      <div class="field"><label>No. Akad</label><input type="text" name="no_akad" class="mono" value="<?= e($editTx['no_akad']); ?>" required></div>
      <div class="field"><label>Tanggal Akad</label><input type="date" name="tanggal_akad" class="mono" value="<?= e($editTx['tanggal_akad']); ?>" required></div>
      <div class="field"><label>Jatuh Tempo</label><input type="date" name="jatuh_tempo" class="mono" value="<?= e($editTx['jatuh_tempo']); ?>" required></div>
      <div class="field"><label>Status Gadai</label><select name="status_gadai"><?php foreach(['AKTIF','DIPERPANJANG','LUNAS','LELANG'] as $sg): ?><option value="<?= $sg; ?>" <?= $editTx['status_gadai']===$sg?'selected':''; ?>><?= $sg; ?></option><?php endforeach; ?></select></div>
      <div class="field"><label>Nama Rahin</label><input type="text" name="nama_nasabah" value="<?= e($editTx['nama_nasabah']); ?>" required></div>
      <div class="field"><label>NIK</label><input type="text" name="nik_nasabah" class="mono" value="<?= e($editTx['nik_nasabah']); ?>" required></div>
      <div class="field"><label>No. HP</label><input type="text" name="no_hp" class="mono" value="<?= e($editTx['no_hp']); ?>" required></div>
      <div class="field"><label>Alamat</label><input type="text" name="alamat_nasabah" value="<?= e($editTx['alamat_nasabah']); ?>" required></div>
      <div class="field"><label>Kategori</label><select name="kategori_barang"><?php foreach(['HP','Motor','Emas'] as $kt): ?><option value="<?= $kt; ?>" <?= $editTx['kategori_barang']===$kt?'selected':''; ?>><?= $kt; ?></option><?php endforeach; ?></select></div>
      <div class="field"><label>Nama Barang</label><input type="text" name="nama_barang" value="<?= e($editTx['nama_barang']); ?>" required></div>
      <div class="field"><label>Spesifikasi</label><input type="text" name="spesifikasi_ringkas" value="<?= e($editTx['spesifikasi_ringkas']); ?>"></div>
      <div class="field"><label>Kelengkapan</label><input type="text" name="kelengkapan_barang" value="<?= e($editTx['kelengkapan_barang']); ?>"></div>
      <div class="field"><label>Kondisi Fisik</label><input type="text" name="kondisi_barang" value="<?= e($editTx['kondisi_barang']); ?>"></div>
      <div class="field"><label>Nilai Taksiran (Rp)</label><input type="number" name="nilai_taksiran" class="mono" value="<?= (float)$editTx['nilai_taksiran']; ?>" required></div>
      <div class="field"><label>LTV (%)</label><input type="number" step="0.01" name="ltv_persen" class="mono" value="<?= (float)$editTx['ltv_persen']; ?>" required></div>
      <div class="field"><label>Pokok Pinjaman (Rp)</label><input type="number" name="pokok_pinjaman" class="mono" value="<?= (float)$editTx['pokok_pinjaman']; ?>" required></div>
      <div class="field"><label>Biaya Admin (Rp)</label><input type="number" name="biaya_admin" class="mono" value="<?= (float)$editTx['biaya_admin']; ?>" required></div>
      <div class="field"><label>Ujrah / Periode (Rp)</label><input type="number" name="biaya_ujrah_periode" class="mono" value="<?= (float)$editTx['biaya_ujrah_periode']; ?>" required></div>
      <div class="field"><label>Hari / Periode</label><input type="number" name="hari_per_periode" class="mono" value="<?= (int)$editTx['hari_per_periode']; ?>" required></div>
    </div>
    <button type="submit" name="update_gadai" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Perubahan Akad</button>
  </form>
</div>
<?php endif; ?>

<?php if ($nota): ?>
<div class="struk-wrap <?= ($_GET['format'] ?? '') === 'termal' ? 'struk-termal' : 'struk-a4'; ?>">
  <div class="struk-header">
    <strong><?= e(NAMA_APLIKASI); ?></strong><br><?= e(ALAMAT_KANTOR); ?><br><?= e(KONTAK_KANTOR); ?><br>
    <strong>SURAT BUKTI AKAD RAHN SYARIAH</strong>
  </div>
  <table>
    <tr><td>No. Akad</td><td class="mono">: <strong><?= e($nota['no_akad']); ?></strong> (<?= e($nota['status_gadai']); ?>)</td></tr>
    <tr><td>Tgl Akad / Tempo</td><td class="mono">: <?= e($nota['tanggal_akad']); ?> &rarr; <strong><?= e($nota['jatuh_tempo']); ?></strong> (<?= (int)$nota['hari_per_periode']; ?> Hr)</td></tr>
    <tr><td>Kasir Bertugas</td><td>: <strong><?= e($nota['nama_kasir']); ?></strong></td></tr>
    <tr><td>Rahin (Penggadai)</td><td>: <strong><?= e($nota['nama_nasabah']); ?></strong> · NIK: <span class="mono"><?= e($nota['nik_nasabah']); ?></span> · HP: <span class="mono"><?= e($nota['no_hp']); ?></span></td></tr>
    <tr><td>Alamat Rahin</td><td>: <?= e($nota['alamat_nasabah']); ?></td></tr>
    <tr><td>Marhun (Jaminan)</td><td>: <strong><?= e($nota['kategori_barang']); ?></strong> — <?= e($nota['nama_barang']); ?> (<?= e($nota['spesifikasi_ringkas']); ?>)</td></tr>
    <tr><td>Kelengkapan &amp; Kondisi</td><td>: <?= e($nota['kelengkapan_barang']); ?> · <?= e($nota['kondisi_barang']); ?></td></tr>
    <tr><td>Nilai Taksiran</td><td class="mono">: <strong><?= rupiah($nota['nilai_taksiran']); ?></strong> (LTV <?= e($nota['ltv_persen']); ?>%)</td></tr>
    <tr><td>Pokok Pinjaman (Qardh)</td><td class="mono">: <strong><?= rupiah($nota['pokok_pinjaman']); ?></strong></td></tr>
    <tr><td>Biaya Administrasi Riil</td><td class="mono">: - <?= rupiah($nota['biaya_admin']); ?></td></tr>
    <tr><td><strong>Dana Bersih Diterima</strong></td><td class="mono">: <strong><?= rupiah($nota['dana_bersih_diterima']); ?></strong></td></tr>
    <tr><td>Biaya Penitipan (Ujrah)</td><td class="mono">: <strong><?= rupiah($nota['biaya_ujrah_periode']); ?></strong> / <?= (int)$nota['hari_per_periode']; ?> hr (<?= e($nota['ujrah_package_nama']); ?>)</td></tr>
    <tr><td><strong>Biaya Penebusan</strong></td><td class="mono">: <strong><?= rupiah((float)$nota['pokok_pinjaman'] + (float)$nota['biaya_ujrah_periode']); ?></strong></td></tr>
    <?php $notaDok = $dokMap[$nota['nik_nasabah']] ?? null; ?>
    <tr class="no-print"><td>Dokumen Rahin (PDF)</td><td>: <?php if ($notaDok): ?><a href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($nota['nik_nasabah']); ?>"><strong><?= e($notaDok['nama_file_asli']); ?></strong> (<?= format_ukuran_file($notaDok['ukuran_byte']); ?>)</a><?php else: ?><a href="dokumen_pelanggan.php?nik=<?= urlencode($nota['nik_nasabah']); ?>">Belum diupload — Upload PDF &rarr;</a><?php endif; ?></td></tr>
  </table>
  <div style="margin-top:10px;padding:8px;border-top:1px dashed #666;border-bottom:1px dashed #666;font-size:11px;">
    Pinjaman murni tanpa bunga/riba (Qardh). Barang jaminan dititipkan secara amanah (yad al-amanah) dan tidak dimanfaatkan oleh penerima gadai.<br>
    <strong>Jika barang dilelang, setelah dikurangi biaya penitipan barang dan kewajiban pokok pinjaman, kelebihan uang akan dikembalikan sepenuhnya kepada pemilik barang.</strong>
  </div>
  <div style="margin-top:18px;display:grid;grid-template-columns:1fr 1fr;gap:24px;text-align:center;font-size:11.5px;">
    <div>Penggadai (Rahin),<div style="margin-top:42px;border-top:1px solid #333;padding-top:4px;font-weight:700;"><?= e($nota['nama_nasabah']); ?></div></div>
    <div>Penerima Gadai (Murtahin),<div style="margin-top:42px;border-top:1px solid #333;padding-top:4px;font-weight:700;"><?= e($nota['nama_kasir']); ?></div></div>
  </div>
  <div class="no-print" style="margin-top:12px;display:flex;gap:6px;flex-wrap:wrap;">
    <button type="button" class="btn btn-sm btn-primary" onclick="window.print()"><?= svg_icon('printer', 14); ?> Cetak Nota</button>
    <a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($nota['no_akad']); ?>&format=termal">Mode 80mm</a>
    <a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($nota['no_akad']); ?>&format=a4">Mode A4</a>
    <?php if (in_array($nota['status_gadai'], ['AKTIF','DIPERPANJANG'], true)): ?>
      <a class="btn btn-sm" href="perpanjang_gadai.php?no_akad=<?= urlencode($nota['no_akad']); ?>"><?= svg_icon('refresh', 13); ?> Perpanjang</a>
      <a class="btn btn-sm" href="tebus_barang.php?no_akad=<?= urlencode($nota['no_akad']); ?>"><?= svg_icon('check-circle', 13); ?> Tebus</a>
    <?php endif; ?>
    <a class="btn btn-sm" href="data_pelanggan.php?q=<?= urlencode($nota['nama_nasabah']); ?>"><?= svg_icon('users', 13); ?> Profil Rahin</a>
    <a class="btn btn-sm" href="dokumen_pelanggan.php?<?= $notaDok ? 'preview=1&' : ''; ?>nik=<?= urlencode($nota['nik_nasabah']); ?>"><?= svg_icon('folder', 13); ?> Dokumen PDF</a>
    <a class="btn btn-sm" href="gadai_barang.php">Tutup</a>
  </div>
</div>
<?php endif; ?>

<div class="panel no-print">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('plus-circle', 16); ?> Input Akad Rahn Syariah (HP / Motor / Emas)</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="data_pelanggan.php" class="btn btn-sm"><?= svg_icon('users', 13); ?> Pilih Pelanggan Lama</a>
      <a href="dokumen_pelanggan.php" class="btn btn-sm"><?= svg_icon('folder', 13); ?> Arsip Dokumen PDF</a>
      <?php if (is_owner()): ?><a href="pengaturan_rumus.php#rumus-ltv" class="btn btn-sm"><?= svg_icon('sliders', 13); ?> Atur LTV &amp; Ujrah</a><?php endif; ?>
      <a href="tutup_toko.php" class="btn btn-sm"><?= svg_icon('lock', 13); ?> Cek Status Kas</a>
    </div>
  </div>
  <form method="POST" enctype="multipart/form-data" onsubmit="const b=this.querySelector('button[type=submit]');if(b){setTimeout(()=>{b.disabled=true;},0);}">
    <input type="hidden" name="simpan_gadai" value="1">
    <input type="hidden" name="idempotency_token" value="<?= e($idem_val); ?>">
    <div class="form-grid" style="margin-bottom:12px;">
      <div class="field"><label>No. Akad</label><input type="text" name="no_akad" class="mono" value="<?= e($next_akad); ?>" required></div>
      <div class="field"><label>Tanggal Akad</label><input type="date" name="tanggal_akad" class="mono" value="<?= date('Y-m-d'); ?>" required></div>
      <div class="field"><label>Kategori</label><select name="kategori_barang" id="katSel" onchange="toggleKat()"><option value="HP">HP</option><option value="Motor">Motor</option><option value="Emas">Emas</option></select></div>
      <div class="field"><label>Nama Rahin</label><input type="text" name="nama_nasabah" value="<?= e($prefill_nama); ?>" required></div>
      <div class="field"><label>NIK</label><input type="text" name="nik_nasabah" class="mono" value="<?= e($prefill_nik); ?>" required></div>
      <div class="field"><label>No. HP</label><input type="text" name="no_hp" class="mono" value="<?= e($prefill_hp); ?>" required></div>
      <div class="field"><label>Alamat</label><input type="text" name="alamat_nasabah" value="<?= e($prefill_alamat); ?>" required></div>
      <div class="field"><label>Kondisi Fisik</label><input type="text" name="kondisi_barang" required></div>
    </div>

    <div id="boxHP" class="form-grid" style="padding:12px;background:#f8fbf9;border:1px solid #d8e2dc;border-radius:7px;margin-bottom:12px;">
      <div class="field"><label>Merk / Tipe HP</label><input type="text" name="hp_merk" id="hpMerk"></div>
      <div class="field"><label>Kapasitas / RAM</label><input type="text" name="hp_ram"></div>
      <div class="field"><label>IMEI / SN</label><input type="text" name="hp_imei" class="mono"></div>
      <div class="field"><label>Kelengkapan</label><div style="padding-top:6px;"><label><input type="checkbox" name="hp_unit" checked> Unit</label> &nbsp;<label><input type="checkbox" name="hp_dus" checked> Dus</label> &nbsp;<label><input type="checkbox" name="hp_charger" checked> Charger</label></div></div>
    </div>

    <div id="boxMotor" class="form-grid" style="display:none;padding:12px;background:#f8fbf9;border:1px solid #d8e2dc;border-radius:7px;margin-bottom:12px;">
      <div class="field"><label>Merk / Tipe Motor</label><input type="text" name="motor_merk"></div>
      <div class="field"><label>Tahun</label><input type="text" name="motor_tahun" class="mono"></div>
      <div class="field"><label>Warna</label><input type="text" name="motor_warna"></div>
      <div class="field"><label>No. Polisi</label><input type="text" name="motor_nopol" class="mono"></div>
      <div class="field"><label>No. Rangka</label><input type="text" name="motor_rangka" class="mono"></div>
      <div class="field"><label>No. Mesin</label><input type="text" name="motor_mesin" class="mono"></div>
      <div class="field"><label>Titipan</label><select name="motor_titipan"><option value="Titip Unit & Dokumen">Titip Unit &amp; Dokumen</option><option value="Titip Dokumen (BPKB)">Titip Dokumen (BPKB)</option></select></div>
      <div class="field"><label>Dokumen</label><div style="padding-top:6px;"><label><input type="checkbox" name="motor_bpkb" checked> BPKB</label> &nbsp;<label><input type="checkbox" name="motor_stnk" checked> STNK</label></div></div>
    </div>

    <div id="boxEmas" class="form-grid" style="display:none;padding:12px;background:#f8fbf9;border:1px solid #d8e2dc;border-radius:7px;margin-bottom:12px;">
      <div class="field"><label>Jenis Emas</label><select name="emas_jenis"><option value="Perhiasan">Perhiasan</option><option value="LM Antam">LM Antam</option></select></div>
      <div class="field"><label>Kadar Karat</label><input type="text" name="emas_karat" value="24K"></div>
      <div class="field"><label>Berat Bersih (gram)</label><input type="number" step="0.01" name="emas_berat" class="mono"></div>
      <div class="field"><label>Surat</label><div style="padding-top:6px;"><label><input type="checkbox" name="emas_surat" checked> Ada Surat/Sertifikat</label></div></div>
    </div>

    <div class="form-grid">
      <div class="field"><label>Nilai Taksiran (Rp)</label><input type="number" name="nilai_taksiran" id="inpTak" class="mono" oninput="onTakInput()" required></div>
      <div class="field"><label id="lblPlafon">Plafon Maksimal</label><input type="text" id="outPlafon" readonly class="mono"></div>
      <div class="field"><label>Pokok Pinjaman Qardh (Rp)</label><input type="number" name="pokok_pinjaman" id="inpPok" class="mono" oninput="calc()" required></div>
      <div class="field"><label>Paket Tarif Ujrah Aktif</label><select name="ujrah_package_id" id="pkgSel" onchange="calc()"></select></div>
      <div class="field"><label>Admin Riil (Terkunci)</label><input type="text" id="outAdm" readonly class="mono"></div>
      <div class="field"><label>Ujrah / Periode (Terkunci)</label><input type="text" id="outUjr" readonly class="mono"></div>
      <div class="field"><label>Dana Bersih Diterima Rahin</label><input type="text" id="outBersih" readonly class="mono"></div>
    </div>

    <div style="margin-top:12px;padding:12px;background:#FEFCE8;border:1px solid #FDE68A;border-radius:7px;">
      <div style="font-weight:700;font-size:12.5px;color:#163628;margin-bottom:8px;display:flex;align-items:center;gap:6px;flex-wrap:wrap;">
        <?= svg_icon('folder', 15); ?> Dokumen Pelanggan (Bukti Data Diri &amp; Lainnya — File PDF)
      </div>
      <?php if ($prefillDok): ?>
      <div class="cell-sub" style="margin-bottom:8px;color:#065f46;background:#ecfdf5;border:1px solid #a7f3d0;padding:6px 10px;border-radius:6px;">
        Pelanggan ini sudah memiliki dokumen PDF tersimpan: <strong class="mono"><?= e($prefillDok['nama_file_asli']); ?></strong> (<?= format_ukuran_file($prefillDok['ukuran_byte']); ?>) —
        <a href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($prefill_nik); ?>" target="_blank"><strong>Lihat PDF</strong></a>. Jika mengunggah file baru di bawah, file lama otomatis diganti (maks. 1 PDF per pelanggan).
      </div>
      <?php endif; ?>
      <div class="form-grid">
        <div class="field">
          <label>Upload File PDF (Maks. 1 PDF / Pelanggan, Ukuran Bebas)</label>
          <input type="file" name="dokumen_pdf" accept=".pdf,application/pdf">
        </div>
        <div class="field">
          <label>Keterangan Dokumen</label>
          <input type="text" name="keterangan_dokumen" value="Bukti Data Diri (KTP/KK) &amp; Dokumen Pendukung" placeholder="Contoh: KTP, KK, &amp; Dokumen Pendukung">
        </div>
      </div>
      <div class="cell-sub" style="margin-top:5px;">File PDF otomatis terhubung ke Data Pelanggan &amp; Menu Dokumen, dan dapat diedit/ganti kapan saja oleh Kasir maupun Owner.</div>
    </div>

    <div style="margin-top:14px;"><button type="submit" name="simpan_gadai" class="btn btn-primary"><?= svg_icon('plus-circle', 15); ?> Simpan Akad Rahn</button></div>
  </form>
</div>
<script>
const ltvMap = <?= json_encode($ltv_map); ?>;
const pkgList = <?= json_encode($packages_all); ?>;
function toggleKat(){
  const k = document.getElementById('katSel').value;
  document.getElementById('boxHP').style.display = k==='HP'?'grid':'none';
  document.getElementById('boxMotor').style.display = k==='Motor'?'grid':'none';
  document.getElementById('boxEmas').style.display = k==='Emas'?'grid':'none';
  const sel = document.getElementById('pkgSel');
  sel.innerHTML = '';
  pkgList.filter(p => p.kategori === k).forEach(p => {
    const opt = document.createElement('option');
    opt.value = p.id;
    opt.setAttribute('data-ujrah', p.ujrah_per_periode);
    opt.setAttribute('data-adm', p.biaya_admin);
    opt.textContent = p.nama_paket + ' (Rp ' + Math.round(p.ujrah_per_periode).toLocaleString('id-ID') + ')';
    sel.appendChild(opt);
  });
  onTakInput();
}
function onTakInput(){
  const k = document.getElementById('katSel').value;
  const pct = parseFloat(ltvMap[k] || 75);
  const tak = parseFloat(document.getElementById('inpTak').value || 0);
  const plafon = Math.round(tak * pct / 100);
  document.getElementById('lblPlafon').textContent = 'Plafon Maksimal (LTV ' + pct + '%)';
  document.getElementById('outPlafon').value = 'Rp ' + plafon.toLocaleString('id-ID');
  if(tak > 0){ document.getElementById('inpPok').value = plafon; }
  calc();
}
function calc(){
  const opt = document.getElementById('pkgSel').selectedOptions[0];
  const adm = opt ? parseFloat(opt.getAttribute('data-adm') || 0) : 0;
  const ujr = opt ? parseFloat(opt.getAttribute('data-ujrah') || 0) : 0;
  const pok = parseFloat(document.getElementById('inpPok').value || 0);
  document.getElementById('outAdm').value = 'Rp ' + Math.round(adm).toLocaleString('id-ID');
  document.getElementById('outUjr').value = 'Rp ' + Math.round(ujr).toLocaleString('id-ID');
  document.getElementById('outBersih').value = 'Rp ' + Math.max(0, Math.round(pok - adm)).toLocaleString('id-ID');
}
toggleKat();
</script>

<div class="panel no-print">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('file-text', 16); ?> Daftar Transaksi Gadai</div></div>
  <div class="table-box"><table><thead><tr><th>No. Akad</th><th>Rahin</th><th>Marhun</th><th>Pokok Qardh</th><th>Ujrah</th><th>Status</th><th>Nota &amp; Aksi</th></tr></thead><tbody>
  <?php while($r = mysqli_fetch_assoc($daftar)):
    $rDok = $dokMap[$r['nik_nasabah']] ?? null;
  ?>
    <tr><td class="mono"><a href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['no_akad']); ?></strong></a><div class="cell-sub"><?= e($r['tanggal_akad']); ?> &middot; Kasir: <?= e($r['nama_kasir']); ?></div></td>
    <td><a href="data_pelanggan.php?q=<?= urlencode($r['nama_nasabah']); ?>"><?= e($r['nama_nasabah']); ?></a><div class="cell-sub"><?php if($rDok): ?><a href="dokumen_pelanggan.php?preview=1&nik=<?= urlencode($r['nik_nasabah']); ?>" style="color:#065f46;font-weight:600;"><?= svg_icon('paperclip', 11); ?> PDF Tersedia</a><?php else: ?><a href="dokumen_pelanggan.php?nik=<?= urlencode($r['nik_nasabah']); ?>">Upload PDF</a><?php endif; ?></div></td><td><?= e($r['kategori_barang']); ?> - <?= e($r['nama_barang']); ?></td>
    <td class="mono"><?= rupiah($r['pokok_pinjaman']); ?></td><td class="mono"><?= rupiah($r['biaya_ujrah_periode']); ?></td>
    <td><?= badge_status($r['status_gadai']); ?></td>
    <td>
      <div style="display:flex;gap:5px;flex-wrap:wrap;">
        <a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('printer', 13); ?> Cetak</a>
        <a class="btn btn-sm" href="dokumen_pelanggan.php?<?= $rDok ? 'preview=1&' : ''; ?>nik=<?= urlencode($r['nik_nasabah']); ?>"><?= svg_icon('folder', 13); ?> PDF</a>
        <?php if (in_array($r['status_gadai'], ['AKTIF','DIPERPANJANG'], true)): ?>
        <a class="btn btn-sm" href="perpanjang_gadai.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('refresh', 13); ?> Perpanjang</a>
        <a class="btn btn-sm" href="tebus_barang.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('check-circle', 13); ?> Tebus</a>
        <?php endif; ?>
        <?php if (is_owner()): ?>
        <a class="btn btn-sm" href="gadai_barang.php?edit=<?= (int)$r['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a>
        <form method="POST" style="display:inline;" onsubmit="return confirm('Hapus akad <?= e($r['no_akad']); ?> beserta jurnal &amp; riwayatnya?')"><input type="hidden" name="id" value="<?= (int)$r['id']; ?>"><button type="submit" name="delete_gadai" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form>
        <?php endif; ?>
      </div>
    </td></tr>
  <?php endwhile; ?>
  </tbody></table></div>
</div>
<?php require_once "includes/footer.php"; ?>
