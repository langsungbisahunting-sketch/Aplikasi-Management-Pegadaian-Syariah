<?php
require_once "config/koneksi.php";
require_owner();
$page_title = "Manajemen User";
$ok = ""; $err = "";
$editUser = null;

if (isset($_GET['edit'])) {
    $eid = (int)$_GET['edit'];
    $stE = mysqli_prepare($conn, "SELECT id, username, nama_lengkap, role, no_hp, aktif FROM users WHERE id=? LIMIT 1");
    mysqli_stmt_bind_param($stE, "i", $eid);
    mysqli_stmt_execute($stE);
    $editUser = mysqli_fetch_assoc(mysqli_stmt_get_result($stE));
    mysqli_stmt_close($stE);
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['add_user'])) {
        $un = trim($_POST['username'] ?? '');
        $nm = trim($_POST['nama_lengkap'] ?? '');
        $pw = $_POST['password'] ?? '';
        $rl = ($_POST['role'] ?? '') === 'Owner' ? 'Owner' : 'Kasir';
        $hp = trim($_POST['no_hp'] ?? '');
        if ($un === '' || $nm === '' || strlen($pw) < 4) {
            $err = "Username, nama lengkap, dan password (minimal 4 karakter) wajib diisi.";
        } else {
            $hash = password_hash($pw, PASSWORD_DEFAULT);
            $st = mysqli_prepare($conn, "INSERT INTO users (username, nama_lengkap, password_hash, role, no_hp, aktif) VALUES (?,?,?,?,?,1)");
            mysqli_stmt_bind_param($st, "sssss", $un, $nm, $hash, $rl, $hp);
            if (mysqli_stmt_execute($st)) $ok = "User $un berhasil ditambahkan.";
            else $err = "Username sudah digunakan.";
            mysqli_stmt_close($st);
        }
    } elseif (isset($_POST['edit_user'])) {
        $uid = (int)($_POST['uid'] ?? 0);
        $un = trim($_POST['username'] ?? '');
        $nm = trim($_POST['nama_lengkap'] ?? '');
        $pw = $_POST['password'] ?? '';
        $rl = ($_POST['role'] ?? '') === 'Owner' ? 'Owner' : 'Kasir';
        $hp = trim($_POST['no_hp'] ?? '');
        if ($un === '' || $nm === '') {
            $err = "Username dan nama lengkap wajib diisi.";
        } else {
            if ($pw !== '') {
                $hash = password_hash($pw, PASSWORD_DEFAULT);
                $st = mysqli_prepare($conn, "UPDATE users SET username=?, nama_lengkap=?, password_hash=?, role=?, no_hp=? WHERE id=?");
                mysqli_stmt_bind_param($st, "sssssi", $un, $nm, $hash, $rl, $hp, $uid);
            } else {
                $st = mysqli_prepare($conn, "UPDATE users SET username=?, nama_lengkap=?, role=?, no_hp=? WHERE id=?");
                mysqli_stmt_bind_param($st, "ssssi", $un, $nm, $rl, $hp, $uid);
            }
            if (mysqli_stmt_execute($st)) {
                $uG = mysqli_prepare($conn, "UPDATE transaksi_gadai SET nama_kasir=? WHERE user_id=?");
                mysqli_stmt_bind_param($uG, "si", $nm, $uid); mysqli_stmt_execute($uG); mysqli_stmt_close($uG);
                $uP = mysqli_prepare($conn, "UPDATE riwayat_perpanjangan SET nama_kasir=? WHERE user_id=?");
                mysqli_stmt_bind_param($uP, "si", $nm, $uid); mysqli_stmt_execute($uP); mysqli_stmt_close($uP);
                $uJ = mysqli_prepare($conn, "UPDATE jurnal_akuntansi SET nama_kasir=? WHERE user_id=?");
                mysqli_stmt_bind_param($uJ, "si", $nm, $uid); mysqli_stmt_execute($uJ); mysqli_stmt_close($uJ);
                $uS = mysqli_prepare($conn, "UPDATE sesi_tutup_toko SET nama_kasir=? WHERE kasir_id=?");
                mysqli_stmt_bind_param($uS, "si", $nm, $uid); mysqli_stmt_execute($uS); mysqli_stmt_close($uS);
                $uD = mysqli_prepare($conn, "UPDATE dokumen_pelanggan SET nama_kasir=? WHERE user_id=?");
                if ($uD) { mysqli_stmt_bind_param($uD, "si", $nm, $uid); @mysqli_stmt_execute($uD); mysqli_stmt_close($uD); }
                if ($uid === (int)$_SESSION['user_id']) {
                    $_SESSION['username'] = $un;
                    $_SESSION['nama_lengkap'] = $nm;
                    $_SESSION['role'] = $rl;
                }
                $ok = "Data user berhasil diperbarui &amp; disinkronkan ke seluruh riwayat.";
                $editUser = null;
            } else {
                $err = "Gagal memperbarui user (username mungkin sudah dipakai).";
            }
            mysqli_stmt_close($st);
        }
    } elseif (isset($_POST['delete_user'])) {
        $uid = (int)($_POST['uid'] ?? 0);
        if ($uid === (int)$_SESSION['user_id']) {
            $err = "Tidak dapat menghapus akun Anda sendiri yang sedang login.";
        } else {
            $st = mysqli_prepare($conn, "DELETE FROM users WHERE id=?");
            mysqli_stmt_bind_param($st, "i", $uid);
            if (mysqli_stmt_execute($st)) $ok = "User berhasil dihapus.";
            mysqli_stmt_close($st);
        }
    } elseif (isset($_POST['toggle_user'])) {
        $uid = (int)$_POST['uid'];
        if ($uid !== (int)$_SESSION['user_id']) {
            mysqli_query($conn, "UPDATE users SET aktif=IF(aktif=1,0,1) WHERE id=$uid");
            $ok = "Status user diperbarui.";
        } else {
            $err = "Tidak dapat menonaktifkan akun sendiri.";
        }
    }
}
$users = mysqli_query($conn, "SELECT u.*, (SELECT COUNT(*) FROM transaksi_gadai tg WHERE tg.user_id = u.id) AS jml_akad, (SELECT COUNT(*) FROM jurnal_akuntansi ja WHERE ja.user_id = u.id) AS jml_jurnal FROM users u ORDER BY u.id ASC");
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok"><?= e($ok); ?></div><?php endif; ?>
<?php if ($err): ?><div class="msg-err"><?= e($err); ?></div><?php endif; ?>

<?php if ($editUser): ?>
<div class="panel panel-yellow">
  <div class="panel-head"><div class="panel-title"><?= svg_icon('edit', 16); ?> Edit User: @<?= e($editUser['username']); ?></div><a href="manajemen_user.php" class="btn btn-sm">Batal</a></div>
  <form method="POST" class="form-grid">
    <input type="hidden" name="uid" value="<?= (int)$editUser['id']; ?>">
    <div class="field"><label>Username</label><input type="text" name="username" value="<?= e($editUser['username']); ?>" required></div>
    <div class="field"><label>Nama Lengkap</label><input type="text" name="nama_lengkap" value="<?= e($editUser['nama_lengkap']); ?>" required></div>
    <div class="field"><label>Password Baru (Opsional)</label><input type="password" name="password" placeholder="Kosongkan jika tidak ganti"></div>
    <div class="field"><label>Role</label><select name="role"><option value="Kasir" <?= $editUser['role']==='Kasir'?'selected':''; ?>>Kasir</option><option value="Owner" <?= $editUser['role']==='Owner'?'selected':''; ?>>Owner</option></select></div>
    <div class="field"><label>No. HP</label><input type="text" name="no_hp" value="<?= e($editUser['no_hp']); ?>"></div>
    <div class="field" style="justify-content:flex-end;"><button type="submit" name="edit_user" class="btn btn-primary"><?= svg_icon('edit', 14); ?> Simpan Perubahan</button></div>
  </form>
</div>
<?php else: ?>
<div class="panel panel-yellow"><div class="panel-head"><div class="panel-title"><?= svg_icon('user-cog', 16); ?> Tambah User Baru</div><a href="laporan_akuntansi.php" class="btn btn-sm"><?= svg_icon('file-text', 13); ?> Laporan Antar User</a></div>
<form method="POST" class="form-grid">
  <div class="field"><label>Username</label><input type="text" name="username" required></div>
  <div class="field"><label>Nama Lengkap</label><input type="text" name="nama_lengkap" required></div>
  <div class="field"><label>Password</label><input type="password" name="password" required></div>
  <div class="field"><label>Role</label><select name="role"><option value="Kasir">Kasir</option><option value="Owner">Owner</option></select></div>
  <div class="field"><label>No. HP</label><input type="text" name="no_hp"></div>
  <div class="field" style="justify-content:flex-end;"><button type="submit" name="add_user" class="btn btn-primary"><?= svg_icon('plus-circle', 14); ?> Tambah User</button></div>
</form></div>
<?php endif; ?>

<div class="panel"><div class="panel-head"><div class="panel-title">Daftar User &amp; Aktivitas Terhubung (Modal Awal Bersama: <?= rupiah(MODAL_AWAL_SISTEM); ?>)</div><a href="pengaturan_rumus.php#identitas-modal" class="btn btn-sm"><?= svg_icon('sliders', 13); ?> Atur Modal Awal</a></div><div class="table-box"><table><thead><tr><th>Username</th><th>Nama Lengkap</th><th>Role</th><th>No. HP</th><th>Aktivitas</th><th>Status</th><th>Aksi</th></tr></thead><tbody>
<?php while($u=mysqli_fetch_assoc($users)): ?>
<tr>
  <td class="mono">@<?= e($u['username']); ?></td>
  <td><strong><?= e($u['nama_lengkap']); ?></strong></td>
  <td><?= e($u['role']); ?></td>
  <td class="mono"><?= e($u['no_hp']); ?></td>
  <td class="mono"><a href="laporan_akuntansi.php?user_id=<?= (int)$u['id']; ?>"><?= (int)$u['jml_akad']; ?> Akad &middot; <?= (int)$u['jml_jurnal']; ?> Jurnal &rarr;</a></td>
  <td><?= $u['aktif'] ? '<span class="status-tag" style="background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0;">AKTIF</span>' : '<span class="status-tag" style="background:#fef2f2;color:#991b1b;border:1px solid #fecaca;">NONAKTIF</span>'; ?></td>
  <td>
    <div style="display:flex;gap:6px;flex-wrap:wrap;">
      <a href="laporan_akuntansi.php?user_id=<?= (int)$u['id']; ?>" class="btn btn-sm"><?= svg_icon('file-text', 13); ?> Jurnal</a>
      <a href="manajemen_user.php?edit=<?= (int)$u['id']; ?>" class="btn btn-sm"><?= svg_icon('edit', 13); ?> Edit</a>
      <?php if ((int)$u['id'] !== (int)$_SESSION['user_id']): ?>
      <form method="POST" style="display:inline;"><input type="hidden" name="uid" value="<?= (int)$u['id']; ?>"><button type="submit" name="toggle_user" class="btn btn-sm"><?= $u['aktif'] ? 'Nonaktifkan' : 'Aktifkan'; ?></button></form>
      <form method="POST" style="display:inline;" onsubmit="return confirm('Yakin hapus user @<?= e($u['username']); ?>?')"><input type="hidden" name="uid" value="<?= (int)$u['id']; ?>"><button type="submit" name="delete_user" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form>
      <?php endif; ?>
    </div>
  </td>
</tr>
<?php endwhile; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
