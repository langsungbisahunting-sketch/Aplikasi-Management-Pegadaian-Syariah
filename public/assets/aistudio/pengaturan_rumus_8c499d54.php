<?php
require_once "config/koneksi.php";
require_owner();
$page_title = "Tarif & Rumus Syariah";
$cu = current_user();
$ok = ""; $err = ""; $editLtv = null; $editPkg = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['save_identitas'])) {
        $nm = trim($_POST['nama_aplikasi'] ?? NAMA_APLIKASI);
        $al = trim($_POST['alamat_kantor'] ?? ALAMAT_KANTOR);
        $kt = trim($_POST['kontak_kantor'] ?? KONTAK_KANTOR);
        $md = max(0, (float)($_POST['modal_awal'] ?? MODAL_AWAL_SISTEM));
        $st = mysqli_prepare($conn, "UPDATE pengaturan_sistem SET nama_aplikasi=?, alamat_kantor=?, kontak_kantor=?, modal_awal=? WHERE id=1");
        mysqli_stmt_bind_param($st, "sssd", $nm, $al, $kt, $md);
        mysqli_stmt_execute($st); mysqli_stmt_close($st);
        sync_modal_awal($conn, $md, $cu['id'], $cu['nama_lengkap']);
        $ok = "Pengaturan identitas toko & modal awal (" . rupiah($md) . ") diperbarui dan disinkronkan ke jurnal & seluruh user.";
    } elseif (isset($_POST['set_ltv'])) {
        $id = (int)$_POST['ltv_id']; $kat = $_POST['kategori'];
        mysqli_query($conn, "UPDATE rumus_ltv SET is_default=0 WHERE kategori='" . mysqli_real_escape_string($conn, $kat) . "'");
        mysqli_query($conn, "UPDATE rumus_ltv SET aktif=1, is_default=1 WHERE id=$id");
        $ok = "Default LTV diperbarui.";
    } elseif (isset($_POST['add_ltv'])) {
        $kat = in_array($_POST['kategori'] ?? '', ['HP','Motor','Emas'], true) ? $_POST['kategori'] : 'HP';
        $pct = max(1, min(100, (float)($_POST['persentase'] ?? 75)));
        $lbl = trim($_POST['label'] ?? "Plafon {$pct}% ($kat)");
        $des = trim($_POST['deskripsi'] ?? '');
        $st = mysqli_prepare($conn, "INSERT INTO rumus_ltv (kategori, persentase, label, deskripsi, aktif, is_default, is_custom) VALUES (?,?,?,?,1,0,1)");
        mysqli_stmt_bind_param($st, "sdss", $kat, $pct, $lbl, $des);
        mysqli_stmt_execute($st); mysqli_stmt_close($st);
        $ok = "Rumus LTV custom ditambahkan.";
    } elseif (isset($_POST['update_ltv'])) {
        $id = (int)$_POST['ltv_id'];
        $pct = max(1, min(100, (float)($_POST['persentase'] ?? 75)));
        $lbl = trim($_POST['label'] ?? '');
        $des = trim($_POST['deskripsi'] ?? '');
        $akf = isset($_POST['aktif']) ? 1 : 0;
        $st = mysqli_prepare($conn, "UPDATE rumus_ltv SET persentase=?, label=?, deskripsi=?, aktif=? WHERE id=?");
        mysqli_stmt_bind_param($st, "dssii", $pct, $lbl, $des, $akf, $id);
        mysqli_stmt_execute($st); mysqli_stmt_close($st);
        $ok = "Rumus LTV diperbarui.";
    } elseif (isset($_POST['delete_ltv'])) {
        $id = (int)$_POST['ltv_id'];
        $rL = mysqli_fetch_assoc(mysqli_query($conn, "SELECT * FROM rumus_ltv WHERE id=$id LIMIT 1"));
        if ($rL && (int)$rL['is_default'] === 1) {
            $err = "Tidak dapat menghapus LTV yang sedang aktif sebagai Default.";
        } elseif ($rL) {
            mysqli_query($conn, "DELETE FROM rumus_ltv WHERE id=$id");
            $ok = "Rumus LTV dihapus.";
        }
    } elseif (isset($_POST['add_ujrah'])) {
        $st = mysqli_prepare($conn, "INSERT INTO paket_ujrah (kode, nama_paket, kategori, sub_tipe, ujrah_per_periode, biaya_admin, hari_per_periode, deskripsi_syariah, aktif, is_default, is_custom) VALUES (?,?,?,?,?,?,?,?,1,0,1)");
        $kd = trim($_POST['kode'] ?? ''); $nm = trim($_POST['nama_paket'] ?? '');
        $kt = in_array($_POST['kategori'] ?? '', ['HP','Motor','Emas'], true) ? $_POST['kategori'] : 'HP';
        $sb = trim($_POST['sub_tipe'] ?? ''); $uj = max(0, (float)($_POST['ujrah'] ?? 0)); $ad = max(0, (float)($_POST['admin'] ?? 0)); $hr = max(1, (int)($_POST['hari'] ?? 30)); $ds = trim($_POST['deskripsi'] ?? '');
        mysqli_stmt_bind_param($st, "ssssddis", $kd, $nm, $kt, $sb, $uj, $ad, $hr, $ds);
        mysqli_stmt_execute($st); mysqli_stmt_close($st);
        $ok = "Paket tarif Ujrah custom ditambahkan.";
    } elseif (isset($_POST['update_pkg'])) {
        $id = (int)$_POST['pkg_id'];
        $kd = trim($_POST['kode']); $nm = trim($_POST['nama_paket']); $kt = in_array($_POST['kategori'] ?? '', ['HP','Motor','Emas'], true) ? $_POST['kategori'] : 'HP'; $sb = trim($_POST['sub_tipe']); $uj = max(0, (float)$_POST['ujrah']); $ad = max(0, (float)$_POST['admin']); $hr = max(1, (int)$_POST['hari']); $ds = trim($_POST['deskripsi']); $akf = isset($_POST['aktif']) ? 1 : 0;
        $st = mysqli_prepare($conn, "UPDATE paket_ujrah SET kode=?, nama_paket=?, kategori=?, sub_tipe=?, ujrah_per_periode=?, biaya_admin=?, hari_per_periode=?, deskripsi_syariah=?, aktif=? WHERE id=?");
        mysqli_stmt_bind_param($st, "ssssddisii", $kd, $nm, $kt, $sb, $uj, $ad, $hr, $ds, $akf, $id);
        mysqli_stmt_execute($st); mysqli_stmt_close($st);
        $ok = "Paket tarif Ujrah diperbarui.";
    } elseif (isset($_POST['delete_pkg'])) {
        $id = (int)$_POST['pkg_id'];
        $rP = mysqli_fetch_assoc(mysqli_query($conn, "SELECT * FROM paket_ujrah WHERE id=$id LIMIT 1"));
        if ($rP && (int)$rP['is_default'] === 1) {
            $err = "Tidak dapat menghapus paket Ujrah default.";
        } elseif ($rP) {
            mysqli_query($conn, "DELETE FROM paket_ujrah WHERE id=$id");
            $ok = "Paket Ujrah dihapus.";
        }
    } elseif (isset($_POST['toggle_pkg'])) {
        $id = (int)$_POST['pkg_id'];
        mysqli_query($conn, "UPDATE paket_ujrah SET aktif = IF(aktif=1,0,1) WHERE id=$id");
        $ok = "Status checklist paket Ujrah diperbarui.";
    }
}

if (isset($_GET['edit_ltv'])) {
    $eid = (int)$_GET['edit_ltv'];
    $editLtv = mysqli_fetch_assoc(mysqli_query($conn, "SELECT * FROM rumus_ltv WHERE id=$eid LIMIT 1"));
}
if (isset($_GET['edit_pkg'])) {
    $eid = (int)$_GET['edit_pkg'];
    $editPkg = mysqli_fetch_assoc(mysqli_query($conn, "SELECT * FROM paket_ujrah WHERE id=$eid LIMIT 1"));
}

$sysNow = mysqli_fetch_assoc(mysqli_query($conn, "SELECT * FROM pengaturan_sistem WHERE id=1 LIMIT 1"));
$ltvs = mysqli_query($conn, "SELECT * FROM rumus_ltv ORDER BY kategori, persentase");
$pkgs = mysqli_query($conn, "SELECT * FROM paket_ujrah ORDER BY kategori, id");
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok"><?= e($ok); ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err"><?= e($err); ?></div><?php endif; ?>
<div class="panel" id="identitas-modal">
  <div class="panel-head">
    <div class="panel-title"><?= svg_icon('sliders', 16); ?> Identitas Kantor &amp; Modal Awal</div>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="laporan_akuntansi.php?no_akad=MODAL-AWAL" class="btn btn-sm"><?= svg_icon('file-text', 13); ?> Lihat Jurnal Modal</a>
      <a href="tutup_toko.php" class="btn btn-sm"><?= svg_icon('lock', 13); ?> Cek Kas Tutup Toko</a>
    </div>
  </div>
  <form method="POST" class="form-grid">
    <div class="field"><label>Nama Aplikasi / Toko</label><input type="text" name="nama_aplikasi" value="<?= e($sysNow['nama_aplikasi'] ?? NAMA_APLIKASI); ?>" required></div>
    <div class="field"><label>Alamat Kantor</label><input type="text" name="alamat_kantor" value="<?= e($sysNow['alamat_kantor'] ?? ALAMAT_KANTOR); ?>" required></div>
    <div class="field"><label>Kontak / WhatsApp</label><input type="text" name="kontak_kantor" value="<?= e($sysNow['kontak_kantor'] ?? KONTAK_KANTOR); ?>" required></div>
    <div class="field"><label>Modal Awal Bersama (Rp)</label><input type="number" name="modal_awal" class="mono" value="<?= (float)($sysNow['modal_awal'] ?? MODAL_AWAL_SISTEM); ?>" required></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="save_identitas" class="btn btn-primary">Simpan Identitas &amp; Modal</button></div>
  </form>
</div>

<div class="panel" id="rumus-ltv"><div class="panel-head"><div class="panel-title">Skema 1 — Checklist Rasio Plafon Taksiran (LTV)</div><a href="gadai_barang.php" class="btn btn-sm"><?= svg_icon('plus-circle', 13); ?> Terapkan di Gadai Baru</a></div>
<?php if ($editLtv): ?>
<form method="POST" class="form-grid" style="padding:12px;background:#FEFCE8;border:1px solid #FDE68A;border-radius:7px;margin-bottom:12px;">
  <input type="hidden" name="ltv_id" value="<?= (int)$editLtv['id']; ?>">
  <div class="field"><label>Edit Persentase LTV (%)</label><input type="number" step="0.01" min="1" max="100" name="persentase" class="mono" value="<?= (float)$editLtv['persentase']; ?>" required></div>
  <div class="field"><label>Label</label><input type="text" name="label" value="<?= e($editLtv['label']); ?>" required></div>
  <div class="field"><label>Deskripsi</label><input type="text" name="deskripsi" value="<?= e($editLtv['deskripsi']); ?>"></div>
  <div class="field"><label>Status</label><div style="padding-top:8px;"><label><input type="checkbox" name="aktif" <?= $editLtv['aktif']?'checked':''; ?>> Aktif</label></div></div>
  <div class="field" style="justify-content:flex-end;display:flex;flex-direction:row;gap:6px;align-items:flex-end;"><button type="submit" name="update_ltv" class="btn btn-primary">Simpan LTV</button><a href="pengaturan_rumus.php" class="btn">Batal</a></div>
</form>
<?php endif; ?>
<div class="table-box" style="margin-bottom:12px;"><table><thead><tr><th>Kategori</th><th>Persentase</th><th>Label</th><th>Status</th><th>Aksi</th></tr></thead><tbody>
<?php while($l=mysqli_fetch_assoc($ltvs)): ?>
<tr><td><?= e($l['kategori']); ?></td><td class="mono"><?= e($l['persentase']); ?>%</td><td><?= e($l['label']); ?><div class="cell-sub"><?= e($l['deskripsi']); ?></div></td><td><?= $l['is_default'] ? '<strong>Default Aktif</strong>' : ($l['aktif'] ? 'Aktif' : 'Nonaktif'); ?></td>
<td><div style="display:flex;gap:5px;flex-wrap:wrap;"><form method="POST" style="display:inline;"><input type="hidden" name="ltv_id" value="<?= (int)$l['id']; ?>"><input type="hidden" name="kategori" value="<?= e($l['kategori']); ?>"><button type="submit" name="set_ltv" class="btn btn-sm">Jadikan Default</button></form><a class="btn btn-sm" href="pengaturan_rumus.php?edit_ltv=<?= (int)$l['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><?php if(!(int)$l['is_default']): ?><form method="POST" style="display:inline;" onsubmit="return confirm('Hapus skema LTV ini?')"><input type="hidden" name="ltv_id" value="<?= (int)$l['id']; ?>"><button type="submit" name="delete_ltv" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form><?php endif; ?></div></td></tr>
<?php endwhile; ?>
</tbody></table></div>
<form method="POST" class="form-grid">
  <div class="field"><label>Kategori</label><select name="kategori"><option value="HP">HP</option><option value="Motor">Motor</option><option value="Emas">Emas</option></select></div>
  <div class="field"><label>Persentase LTV (%)</label><input type="number" step="0.01" min="1" max="100" name="persentase" class="mono" required placeholder="78"></div>
  <div class="field"><label>Label Skema</label><input type="text" name="label" required placeholder="Plafon Khusus 78%"></div>
  <div class="field"><label>Deskripsi</label><input type="text" name="deskripsi" placeholder="Keterangan singkat"></div>
  <div class="field" style="justify-content:flex-end;"><button type="submit" name="add_ltv" class="btn btn-primary"><?= svg_icon('plus-circle', 14); ?> Tambah LTV Custom</button></div>
</form>
</div>

<div class="panel panel-yellow" id="paket-ujrah"><div class="panel-head"><div class="panel-title">Skema 2 — Checklist Paket Tarif Ujrah &amp; Admin Riil</div><a href="gadai_barang.php" class="btn btn-sm"><?= svg_icon('plus-circle', 13); ?> Uji di Gadai Baru</a></div>
<?php if ($editPkg): ?>
<form method="POST" class="form-grid" style="padding:12px;background:#fff;border:1px solid #d8e2dc;border-radius:7px;margin-bottom:14px;">
  <input type="hidden" name="pkg_id" value="<?= (int)$editPkg['id']; ?>">
  <div class="field"><label>Kode</label><input type="text" name="kode" value="<?= e($editPkg['kode']); ?>" required></div>
  <div class="field"><label>Nama Paket</label><input type="text" name="nama_paket" value="<?= e($editPkg['nama_paket']); ?>" required></div>
  <div class="field"><label>Kategori</label><select name="kategori"><?php foreach(['HP','Motor','Emas'] as $kt): ?><option value="<?= $kt; ?>" <?= $editPkg['kategori']===$kt?'selected':''; ?>><?= $kt; ?></option><?php endforeach; ?></select></div>
  <div class="field"><label>Sub Tipe</label><input type="text" name="sub_tipe" value="<?= e($editPkg['sub_tipe']); ?>"></div>
  <div class="field"><label>Ujrah Tetap (Rp)</label><input type="number" name="ujrah" class="mono" value="<?= (float)$editPkg['ujrah_per_periode']; ?>" required></div>
  <div class="field"><label>Admin Tetap (Rp)</label><input type="number" name="admin" class="mono" value="<?= (float)$editPkg['biaya_admin']; ?>" required></div>
  <div class="field"><label>Hari / Periode</label><input type="number" name="hari" class="mono" value="<?= (int)$editPkg['hari_per_periode']; ?>" required></div>
  <div class="field"><label>Alasan Syariah</label><input type="text" name="deskripsi" value="<?= e($editPkg['deskripsi_syariah']); ?>" required></div>
  <div class="field"><label>Status</label><div style="padding-top:8px;"><label><input type="checkbox" name="aktif" <?= $editPkg['aktif']?'checked':''; ?>> Aktif</label></div></div>
  <div class="field" style="justify-content:flex-end;display:flex;flex-direction:row;gap:6px;align-items:flex-end;"><button type="submit" name="update_pkg" class="btn btn-primary">Simpan Paket</button><a href="pengaturan_rumus.php" class="btn">Batal</a></div>
</form>
<?php endif; ?>
<div class="table-box" style="margin-bottom:14px;"><table><thead><tr><th>Paket</th><th>Kategori</th><th>Ujrah / Periode</th><th>Admin</th><th>Deskripsi Syariah</th><th>Status</th><th>Aksi</th></tr></thead><tbody>
<?php while($p=mysqli_fetch_assoc($pkgs)): ?>
<tr><td><strong><?= e($p['nama_paket']); ?></strong><div class="cell-sub mono"><?= e($p['kode']); ?></div></td><td><?= e($p['kategori']); ?></td><td class="mono"><?= rupiah($p['ujrah_per_periode']); ?> / <?= (int)$p['hari_per_periode']; ?> hr</td><td class="mono"><?= rupiah($p['biaya_admin']); ?></td><td><?= e($p['deskripsi_syariah']); ?></td><td><?= $p['aktif'] ? 'Aktif' : 'Nonaktif'; ?></td>
<td><div style="display:flex;gap:5px;flex-wrap:wrap;"><form method="POST" style="display:inline;"><input type="hidden" name="pkg_id" value="<?= (int)$p['id']; ?>"><button type="submit" name="toggle_pkg" class="btn btn-sm"><?= $p['aktif'] ? 'Nonaktifkan' : 'Aktifkan'; ?></button></form><a class="btn btn-sm" href="pengaturan_rumus.php?edit_pkg=<?= (int)$p['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><?php if(!(int)$p['is_default']): ?><form method="POST" style="display:inline;" onsubmit="return confirm('Hapus paket Ujrah ini?')"><input type="hidden" name="pkg_id" value="<?= (int)$p['id']; ?>"><button type="submit" name="delete_pkg" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form><?php endif; ?></div></td></tr>
<?php endwhile; ?>
</tbody></table></div>
<form method="POST" class="form-grid">
  <div class="field"><label>Kode</label><input type="text" name="kode" required placeholder="KLS-F"></div>
  <div class="field"><label>Nama Paket Custom</label><input type="text" name="nama_paket" required></div>
  <div class="field"><label>Kategori</label><select name="kategori"><option value="HP">HP</option><option value="Motor">Motor</option><option value="Emas">Emas</option></select></div>
  <div class="field"><label>Sub Tipe</label><input type="text" name="sub_tipe"></div>
  <div class="field"><label>Ujrah Tetap (Rp)</label><input type="number" name="ujrah" class="mono" required></div>
  <div class="field"><label>Admin Tetap (Rp)</label><input type="number" name="admin" class="mono" required></div>
  <div class="field"><label>Hari / Periode</label><input type="number" name="hari" value="30" class="mono" required></div>
  <div class="field"><label>Alasan Syariah</label><input type="text" name="deskripsi" required></div>
  <div class="field" style="justify-content:flex-end;"><button type="submit" name="add_ujrah" class="btn btn-primary">+ Tambah Paket Custom</button></div>
</form></div>
<?php require_once "includes/footer.php"; ?>
