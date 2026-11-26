<?php
require_once "config/koneksi.php";
require_owner();
$page_title = "Laporan Keuangan Owner";
$cu = current_user();
$ok = ""; $err = ""; $editJurnal = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $allowedMenus = ['MODAL_AWAL','GADAI_BARANG','PERPANJANG_GADAI','TEBUS_BARANG','LELANG_BARANG'];
    if (isset($_POST['add_jurnal'])) {
        $tglJ = valid_iso_date($_POST['tanggal'] ?? '') ? $_POST['tanggal'] : date('Y-m-d');
        $ref = trim($_POST['no_akad'] ?? 'JURNAL-MANUAL') ?: 'JURNAL-MANUAL';
        $jm = in_array($_POST['jenis_menu'] ?? '', $allowedMenus, true) ? $_POST['jenis_menu'] : 'MODAL_AWAL';
        $ak = ($_POST['arus_kas'] ?? '') === 'KELUAR' ? 'KELUAR' : 'MASUK';
        $deb = trim($_POST['akun_debit'] ?? 'Kas Operasional') ?: 'Kas Operasional';
        $kre = trim($_POST['akun_kredit'] ?? 'Penyesuaian Kas') ?: 'Penyesuaian Kas';
        $pn = max(0, (float)($_POST['pokok_nominal'] ?? 0));
        $pa = max(0, (float)($_POST['pendapatan_admin'] ?? 0));
        $pu = max(0, (float)($_POST['pendapatan_ujrah'] ?? 0));
        $tk = max(0, (float)($_POST['total_kas'] ?? ($pn + $pa + $pu)));
        $ket = trim($_POST['keterangan'] ?? '');
        $targetUid = (int)($_POST['user_id'] ?? $cu['id']);
        $targetNama = $cu['nama_lengkap'];
        if ($targetUid > 0) {
            $stU = mysqli_prepare($conn, "SELECT id, nama_lengkap FROM users WHERE id=? LIMIT 1");
            if ($stU) {
                mysqli_stmt_bind_param($stU, "i", $targetUid);
                mysqli_stmt_execute($stU);
                $uRow = mysqli_fetch_assoc(mysqli_stmt_get_result($stU));
                mysqli_stmt_close($stU);
                if ($uRow) $targetNama = $uRow['nama_lengkap'];
                else $targetUid = $cu['id'];
            }
        } else {
            $targetUid = $cu['id'];
        }
        if ($ket === '' || $tk <= 0) {
            $err = "Keterangan dan Total Kas wajib diisi.";
        } else {
            $st = mysqli_prepare($conn, "INSERT INTO jurnal_akuntansi (tanggal, no_akad, jenis_menu, arus_kas, akun_debit, akun_kredit, pokok_nominal, pendapatan_admin, pendapatan_ujrah, total_kas, keterangan, user_id, nama_kasir) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
            mysqli_stmt_bind_param($st, "ssssssddddsis", $tglJ, $ref, $jm, $ak, $deb, $kre, $pn, $pa, $pu, $tk, $ket, $targetUid, $targetNama);
            mysqli_stmt_execute($st); mysqli_stmt_close($st);
            if ($jm === 'MODAL_AWAL' && $ref === 'MODAL-AWAL') {
                sync_modal_awal($conn, $tk, $targetUid, $targetNama);
            }
            $ok = "Jurnal manual berhasil ditambahkan dan disinkronkan ke saldo kas bersama.";
        }
    } elseif (isset($_POST['update_jurnal'])) {
        $jid = (int)($_POST['id'] ?? 0);
        $tglJ = valid_iso_date($_POST['tanggal'] ?? '') ? $_POST['tanggal'] : date('Y-m-d');
        $ref = trim($_POST['no_akad'] ?? '');
        $jm = in_array($_POST['jenis_menu'] ?? '', $allowedMenus, true) ? $_POST['jenis_menu'] : 'MODAL_AWAL';
        $ak = ($_POST['arus_kas'] ?? '') === 'KELUAR' ? 'KELUAR' : 'MASUK';
        $deb = trim($_POST['akun_debit'] ?? '');
        $kre = trim($_POST['akun_kredit'] ?? '');
        $pn = max(0, (float)($_POST['pokok_nominal'] ?? 0));
        $pa = max(0, (float)($_POST['pendapatan_admin'] ?? 0));
        $pu = max(0, (float)($_POST['pendapatan_ujrah'] ?? 0));
        $tk = max(0, (float)($_POST['total_kas'] ?? 0));
        $ket = trim($_POST['keterangan'] ?? '');
        $st = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET tanggal=?, no_akad=?, jenis_menu=?, arus_kas=?, akun_debit=?, akun_kredit=?, pokok_nominal=?, pendapatan_admin=?, pendapatan_ujrah=?, total_kas=?, keterangan=? WHERE id=?");
        mysqli_stmt_bind_param($st, "ssssssddddsi", $tglJ, $ref, $jm, $ak, $deb, $kre, $pn, $pa, $pu, $tk, $ket, $jid);
        mysqli_stmt_execute($st); mysqli_stmt_close($st);
        if ($jm === 'MODAL_AWAL' && $ref === 'MODAL-AWAL') {
            $upSys = mysqli_prepare($conn, "UPDATE pengaturan_sistem SET modal_awal=? WHERE id=1");
            if ($upSys) {
                mysqli_stmt_bind_param($upSys, "d", $tk);
                mysqli_stmt_execute($upSys); mysqli_stmt_close($upSys);
            }
        }
        $ok = "Baris jurnal berhasil diperbarui.";
    } elseif (isset($_POST['delete_jurnal'])) {
        $jid = (int)($_POST['id'] ?? 0);
        mysqli_query($conn, "DELETE FROM jurnal_akuntansi WHERE id=$jid");
        $ok = "Baris jurnal berhasil dihapus.";
    } elseif (isset($_POST['delete_sesi'])) {
        $sid = (int)($_POST['id'] ?? 0);
        mysqli_query($conn, "DELETE FROM sesi_tutup_toko WHERE id=$sid");
        $ok = "Kunci sesi tutup toko dibuka kembali (OPEN).";
    }
}

if (isset($_GET['edit_jurnal'])) {
    $eid = (int)$_GET['edit_jurnal'];
    $stE = mysqli_prepare($conn, "SELECT * FROM jurnal_akuntansi WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stE, "i", $eid); mysqli_stmt_execute($stE);
    $editJurnal = mysqli_fetch_assoc(mysqli_stmt_get_result($stE)); mysqli_stmt_close($stE);
}

$preset = $_GET['preset'] ?? '';
$tgl_awal = valid_iso_date($_GET['tgl_awal'] ?? '') ? $_GET['tgl_awal'] : '';
$tgl_akhir = valid_iso_date($_GET['tgl_akhir'] ?? '') ? $_GET['tgl_akhir'] : '';
$filter_uid = (int)($_GET['user_id'] ?? 0);
$filter_akad = trim($_GET['no_akad'] ?? '');
$filter_menu = trim($_GET['jenis_menu'] ?? '');
if ($preset === 'hari_ini') { $tgl_awal = $tgl_akhir = date('Y-m-d'); }
elseif ($preset === '7_hari') { $tgl_awal = date('Y-m-d', strtotime('-6 days')); $tgl_akhir = date('Y-m-d'); }
elseif ($preset === 'bulan_ini') { $tgl_awal = date('Y-m-01'); $tgl_akhir = date('Y-m-d'); }

$w = ["1=1"]; $wt = ["1=1"]; $ws_sesi = ["1=1"];
if ($tgl_awal !== '') { $ea = mysqli_real_escape_string($conn, $tgl_awal); $w[] = "tanggal >= '$ea'"; $wt[] = "tanggal_akad >= '$ea'"; $ws_sesi[] = "tanggal >= '$ea'"; }
if ($tgl_akhir !== '') { $eb = mysqli_real_escape_string($conn, $tgl_akhir); $w[] = "tanggal <= '$eb'"; $wt[] = "tanggal_akad <= '$eb'"; $ws_sesi[] = "tanggal <= '$eb'"; }
if ($filter_uid > 0) {
    $w[] = "user_id = $filter_uid";
    $wt[] = "user_id = $filter_uid";
    $ws_sesi[] = "kasir_id = $filter_uid";
}
if ($filter_akad !== '') {
    $eak = mysqli_real_escape_string($conn, $filter_akad);
    $w[] = "no_akad LIKE '%$eak%'";
    $wt[] = "no_akad LIKE '%$eak%'";
}
if ($filter_menu !== '' && in_array($filter_menu, ['MODAL_AWAL','GADAI_BARANG','PERPANJANG_GADAI','TEBUS_BARANG','LELANG_BARANG'], true)) {
    $emn = mysqli_real_escape_string($conn, $filter_menu);
    $w[] = "jenis_menu = '$emn'";
}
$ws = implode(" AND ", $w); $wts = implode(" AND ", $wt); $wss = implode(" AND ", $ws_sesi);

$met = mysqli_fetch_assoc(mysqli_query($conn, "SELECT
  COALESCE(SUM(CASE WHEN jenis_menu='GADAI_BARANG' AND arus_kas='KELUAR' THEN pokok_nominal ELSE 0 END),0) AS modal_keluar,
  COALESCE(SUM(CASE WHEN jenis_menu IN ('TEBUS_BARANG','LELANG_BARANG') AND arus_kas='MASUK' THEN pokok_nominal ELSE 0 END),0) AS pokok_kembali,
  COALESCE(SUM(pendapatan_ujrah),0) AS pend_ujrah,
  COALESCE(SUM(pendapatan_admin),0) AS pend_admin,
  COALESCE(SUM(CASE WHEN arus_kas='MASUK' THEN total_kas ELSE -total_kas END),0) AS net_kas
  FROM jurnal_akuntansi WHERE $ws"));
$st_cnt = mysqli_fetch_assoc(mysqli_query($conn, "SELECT
  SUM(status_gadai='AKTIF') AS s_aktif, SUM(status_gadai='DIPERPANJANG') AS s_perp, SUM(status_gadai='LUNAS') AS s_lunas, SUM(status_gadai='LELANG') AS s_lelang,
  SUM(status_gadai IN ('AKTIF','DIPERPANJANG') AND jatuh_tempo < CURDATE()) AS s_lewat
  FROM transaksi_gadai WHERE $wts"));
$jurnal = mysqli_query($conn, "SELECT * FROM jurnal_akuntansi WHERE $ws ORDER BY tanggal DESC, id DESC");
$logs = mysqli_query($conn, "SELECT * FROM sesi_tutup_toko WHERE $wss ORDER BY tanggal DESC");
$allUsers = [];
$qu = mysqli_query($conn, "SELECT id, username, nama_lengkap, role FROM users ORDER BY id ASC");
while ($ur = mysqli_fetch_assoc($qu)) $allUsers[] = $ur;

if (isset($_GET['export']) && $_GET['export'] === 'xlsx') {
    $jRows = [['Tanggal','No. Akad','Menu','Arus','Pokok (Rp)','Admin (Rp)','Ujrah (Rp)','Total Kas (Rp)','Kasir','Keterangan']];
    while ($j = mysqli_fetch_assoc($jurnal)) $jRows[] = [$j['tanggal'],$j['no_akad'],$j['jenis_menu'],$j['arus_kas'],(float)$j['pokok_nominal'],(float)$j['pendapatan_admin'],(float)$j['pendapatan_ujrah'],(float)$j['total_kas'],$j['nama_kasir'],$j['keterangan']];
    SyariahXlsx::download('laporan_owner_' . date('Y-m-d') . '.xlsx', [['name' => 'Jurnal_Owner', 'rows' => $jRows]]);
}
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok no-print"><?= e($ok); ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err no-print"><?= e($err); ?></div><?php endif; ?>

<div class="panel panel-yellow no-print">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('file-text', 16); ?> Filter Tanggal, Kasir &amp; Ref Akad Laporan Owner</div>
  <div style="display:flex;gap:6px;flex-wrap:wrap;"><a class="btn btn-sm" href="laporan_akuntansi.php?export=xlsx&tgl_awal=<?= urlencode($tgl_awal); ?>&tgl_akhir=<?= urlencode($tgl_akhir); ?>&user_id=<?= (int)$filter_uid; ?>&no_akad=<?= urlencode($filter_akad); ?>&jenis_menu=<?= urlencode($filter_menu); ?>"><?= svg_icon('download', 14); ?> Export Excel (.xlsx)</a> <button type="button" class="btn btn-sm btn-primary" onclick="window.print()"><?= svg_icon('printer', 14); ?> Cetak A4</button></div></div>
  <div style="display:flex;gap:6px;margin-bottom:10px;flex-wrap:wrap;">
    <a class="btn btn-sm" href="?preset=hari_ini">Hari Ini</a>
    <a class="btn btn-sm" href="?preset=7_hari">7 Hari Terakhir</a>
    <a class="btn btn-sm" href="?preset=bulan_ini">Bulan Ini</a>
    <a class="btn btn-sm" href="?jenis_menu=MODAL_AWAL">Modal Awal &amp; Tambahan</a>
    <a class="btn btn-sm" href="laporan_akuntansi.php">Semua</a>
  </div>
  <form method="GET" class="form-grid">
    <div class="field"><label>Dari Tanggal</label><input type="date" name="tgl_awal" class="mono" value="<?= e($tgl_awal); ?>"></div>
    <div class="field"><label>Sampai Tanggal</label><input type="date" name="tgl_akhir" class="mono" value="<?= e($tgl_akhir); ?>"></div>
    <div class="field"><label>Petugas / User</label><select name="user_id"><option value="0">-- Semua User (Gabungan) --</option><?php foreach($allUsers as $au): ?><option value="<?= (int)$au['id']; ?>" <?= $filter_uid===(int)$au['id']?'selected':''; ?>><?= e($au['nama_lengkap']); ?> (<?= e($au['role']); ?>)</option><?php endforeach; ?></select></div>
    <div class="field"><label>Jenis Menu</label><select name="jenis_menu"><option value="">-- Semua --</option><?php foreach(['MODAL_AWAL','GADAI_BARANG','PERPANJANG_GADAI','TEBUS_BARANG','LELANG_BARANG'] as $jmOpt): ?><option value="<?= $jmOpt; ?>" <?= $filter_menu===$jmOpt?'selected':''; ?>><?= $jmOpt; ?></option><?php endforeach; ?></select></div>
    <div class="field"><label>No. Akad / Ref</label><input type="text" name="no_akad" class="mono" value="<?= e($filter_akad); ?>" placeholder="AG-... / MODAL-AWAL"></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" class="btn btn-primary">Terapkan</button></div>
  </form>
</div>

<?php if ($editJurnal): ?>
<div class="panel panel-yellow no-print">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 16); ?> Edit Baris Jurnal #<?= (int)$editJurnal['id']; ?></div><a href="laporan_akuntansi.php" class="btn btn-sm">Batal</a></div>
  <form method="POST" class="form-grid">
    <input type="hidden" name="id" value="<?= (int)$editJurnal['id']; ?>">
    <div class="field"><label>Tanggal</label><input type="date" name="tanggal" class="mono" value="<?= e($editJurnal['tanggal']); ?>" required></div>
    <div class="field"><label>No. Ref / Akad</label><input type="text" name="no_akad" class="mono" value="<?= e($editJurnal['no_akad']); ?>" required></div>
    <div class="field"><label>Jenis Menu</label><select name="jenis_menu"><?php foreach(['MODAL_AWAL','GADAI_BARANG','PERPANJANG_GADAI','TEBUS_BARANG','LELANG_BARANG'] as $jm): ?><option value="<?= $jm; ?>" <?= $editJurnal['jenis_menu']===$jm?'selected':''; ?>><?= $jm; ?></option><?php endforeach; ?></select></div>
    <div class="field"><label>Arus Kas</label><select name="arus_kas"><option value="MASUK" <?= $editJurnal['arus_kas']==='MASUK'?'selected':''; ?>>MASUK</option><option value="KELUAR" <?= $editJurnal['arus_kas']==='KELUAR'?'selected':''; ?>>KELUAR</option></select></div>
    <div class="field"><label>Akun Debit</label><input type="text" name="akun_debit" value="<?= e($editJurnal['akun_debit']); ?>" required></div>
    <div class="field"><label>Akun Kredit</label><input type="text" name="akun_kredit" value="<?= e($editJurnal['akun_kredit']); ?>" required></div>
    <div class="field"><label>Pokok (Rp)</label><input type="number" name="pokok_nominal" class="mono" value="<?= (float)$editJurnal['pokok_nominal']; ?>" required></div>
    <div class="field"><label>Admin (Rp)</label><input type="number" name="pendapatan_admin" class="mono" value="<?= (float)$editJurnal['pendapatan_admin']; ?>" required></div>
    <div class="field"><label>Ujrah (Rp)</label><input type="number" name="pendapatan_ujrah" class="mono" value="<?= (float)$editJurnal['pendapatan_ujrah']; ?>" required></div>
    <div class="field"><label>Total Kas (Rp)</label><input type="number" name="total_kas" class="mono" value="<?= (float)$editJurnal['total_kas']; ?>" required></div>
    <div class="field"><label>Keterangan</label><input type="text" name="keterangan" value="<?= e($editJurnal['keterangan']); ?>" required></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="update_jurnal" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Jurnal</button></div>
  </form>
</div>
<?php else: ?>
<div class="panel no-print" id="panel-jurnal-manual">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('plus-circle', 16); ?> Tambah Jurnal Manual (Penyesuaian Kas / Modal Antar User)</div></div>
  <form method="POST" class="form-grid">
    <div class="field"><label>Tanggal</label><input type="date" name="tanggal" class="mono" value="<?= date('Y-m-d'); ?>" required></div>
    <div class="field"><label>No. Ref / Akad</label><input type="text" name="no_akad" class="mono" value="JURNAL-MANUAL" required></div>
    <div class="field"><label>Jenis</label><select name="jenis_menu"><option value="MODAL_AWAL">MODAL_AWAL</option><option value="GADAI_BARANG">GADAI_BARANG</option><option value="PERPANJANG_GADAI">PERPANJANG_GADAI</option><option value="TEBUS_BARANG">TEBUS_BARANG</option><option value="LELANG_BARANG">LELANG_BARANG</option></select></div>
    <div class="field"><label>Arus Kas</label><select name="arus_kas"><option value="MASUK">MASUK</option><option value="KELUAR">KELUAR</option></select></div>
    <div class="field"><label>Petugas Terkait</label><select name="user_id"><?php foreach($allUsers as $au): ?><option value="<?= (int)$au['id']; ?>" <?= (int)$au['id']===$cu['id']?'selected':''; ?>><?= e($au['nama_lengkap']); ?> (<?= e($au['role']); ?>)</option><?php endforeach; ?></select></div>
    <div class="field"><label>Akun Debit</label><input type="text" name="akun_debit" value="Kas Operasional" required></div>
    <div class="field"><label>Akun Kredit</label><input type="text" name="akun_kredit" value="Modal Tambahan" required></div>
    <div class="field"><label>Pokok (Rp)</label><input type="number" name="pokok_nominal" class="mono" value="0"></div>
    <div class="field"><label>Admin (Rp)</label><input type="number" name="pendapatan_admin" class="mono" value="0"></div>
    <div class="field"><label>Ujrah (Rp)</label><input type="number" name="pendapatan_ujrah" class="mono" value="0"></div>
    <div class="field"><label>Total Kas (Rp)</label><input type="number" name="total_kas" class="mono" required></div>
    <div class="field"><label>Keterangan</label><input type="text" name="keterangan" required placeholder="Catatan jurnal manual / serah terima kas"></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="add_jurnal" class="btn btn-primary"><?= svg_icon('plus-circle', 14); ?> Simpan Jurnal Manual</button></div>
  </form>
</div>
<?php endif; ?>

<div class="stats-row">
  <div class="panel"><div class="stat-title">Modal Awal &amp; Saldo Kas Filter</div><div class="stat-num"><?= rupiah($met['net_kas']); ?></div><div class="cell-sub mono">Modal Sistem: <a href="pengaturan_rumus.php#identitas-modal"><?= rupiah(MODAL_AWAL_SISTEM); ?></a></div></div>
  <div class="panel"><div class="stat-title">Total Modal Keluar (Qardh)</div><div class="stat-num"><?= rupiah($met['modal_keluar']); ?></div><div class="cell-sub"><a href="data_barang.php?status=AKTIF">Lihat Barang Aktif &rarr;</a></div></div>
  <div class="panel"><div class="stat-title">Total Pokok Kembali</div><div class="stat-num"><?= rupiah($met['pokok_kembali']); ?></div></div>
  <div class="panel panel-yellow"><div class="stat-title">Pendapatan Jasa (Ujrah + Admin)</div><div class="stat-num"><?= rupiah((float)$met['pend_ujrah'] + (float)$met['pend_admin']); ?></div></div>
  <div class="panel"><div class="stat-title">Status Jaminan</div><div class="cell-sub mono">Aktif: <a href="data_barang.php?status=AKTIF"><?= (int)$st_cnt['s_aktif']; ?></a> &middot; Perpanjang: <a href="data_barang.php?status=DIPERPANJANG"><?= (int)$st_cnt['s_perp']; ?></a> &middot; Lunas: <a href="data_barang.php?status=LUNAS"><?= (int)$st_cnt['s_lunas']; ?></a> &middot; Lewat Tempo: <a href="monitoring_tempo.php"><?= (int)$st_cnt['s_lewat']; ?></a></div></div>
</div>
<div class="panel"><div class="panel-head"><div class="panel-title">Jurnal Akuntansi &amp; Kasir Bertugas</div><a href="manajemen_user.php" class="btn btn-sm"><?= svg_icon('user-cog', 13); ?> Daftar User</a></div>
<div class="table-box"><table><thead><tr><th>Tanggal</th><th>Ref</th><th>Menu</th><th>Pokok/Jasa</th><th>Total Kas</th><th>Kasir</th><th class="no-print">Aksi</th></tr></thead><tbody>
<?php while($j=mysqli_fetch_assoc($jurnal)):
  $refLink = ($j['no_akad'] === 'MODAL-AWAL') ? 'pengaturan_rumus.php#identitas-modal' : (strpos($j['no_akad'], 'AG-') === 0 ? 'gadai_barang.php?cetak=' . urlencode($j['no_akad']) : '');
?>
<tr><td class="mono"><?= e($j['tanggal']); ?></td><td class="mono"><?php if($refLink): ?><a href="<?= e($refLink); ?>"><strong><?= e($j['no_akad']); ?></strong></a><?php else: ?><?= e($j['no_akad']); ?><?php endif; ?><div class="cell-sub"><?= e($j['keterangan']); ?></div></td><td><?= e($j['jenis_menu']); ?> (<?= e($j['arus_kas']); ?>)</td><td class="mono">P:<?= rupiah($j['pokok_nominal']); ?> · U:<?= rupiah($j['pendapatan_ujrah']); ?> · A:<?= rupiah($j['pendapatan_admin']); ?></td><td class="mono"><?= rupiah($j['total_kas']); ?></td><td><a href="laporan_akuntansi.php?user_id=<?= (int)$j['user_id']; ?>"><?= e($j['nama_kasir']); ?></a></td>
<td class="no-print"><div style="display:flex;gap:5px;"><a class="btn btn-sm" href="laporan_akuntansi.php?edit_jurnal=<?= (int)$j['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><form method="POST" style="display:inline;" onsubmit="return confirm('Hapus baris jurnal ini?')"><input type="hidden" name="id" value="<?= (int)$j['id']; ?>"><button type="submit" name="delete_jurnal" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form></div></td></tr>
<?php endwhile; ?>
</tbody></table></div></div>
<div class="panel"><div class="panel-head"><div class="panel-title">Log Tutup Toko Harian</div><a href="tutup_toko.php" class="btn btn-sm"><?= svg_icon('lock', 13); ?> Buka Tutup Toko</a></div>
<div class="table-box"><table><thead><tr><th>Tanggal</th><th>Status</th><th>Saldo Sistem</th><th>Fisik</th><th>Selisih</th><th>Kasir</th><th class="no-print">Aksi</th></tr></thead><tbody>
<?php while($l=mysqli_fetch_assoc($logs)): ?>
<tr><td class="mono"><a href="tutup_toko.php?tanggal=<?= urlencode($l['tanggal']); ?>"><?= e($l['tanggal']); ?></a></td><td><?= e($l['status']); ?></td><td class="mono"><?= rupiah($l['saldo_kas_sistem']); ?></td><td class="mono"><?= rupiah($l['uang_fisik_aktual']); ?></td><td class="mono"><?= rupiah($l['selisih_kas']); ?></td><td><a href="laporan_akuntansi.php?user_id=<?= (int)$l['kasir_id']; ?>"><?= e($l['nama_kasir']); ?></a></td>
<td class="no-print"><form method="POST" style="display:inline;" onsubmit="return confirm('Buka kunci tutup toko tanggal <?= e($l['tanggal']); ?>?')"><input type="hidden" name="id" value="<?= (int)$l['id']; ?>"><button type="submit" name="delete_sesi" class="btn btn-sm btn-yellow"><?= svg_icon('unlock', 13); ?> Buka Kunci</button></form></td></tr>
<?php endwhile; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
