<?php
require_once "config/koneksi.php";
if (!empty($_SESSION['user_id'])) {
    header("Location: " . (is_owner() ? "index.php" : "gadai_barang.php"));
    exit;
}
$err = "";
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = trim($_POST['username'] ?? '');
    $password = $_POST['password'] ?? '';
    $st = mysqli_prepare($conn, "SELECT * FROM users WHERE username = ? LIMIT 1");
    mysqli_stmt_bind_param($st, "s", $username);
    mysqli_stmt_execute($st);
    $u = mysqli_fetch_assoc(mysqli_stmt_get_result($st));
    mysqli_stmt_close($st);
    if (!$u || !password_verify($password, $u['password_hash'])) {
        $err = "Username atau password salah.";
    } elseif ((int)$u['aktif'] !== 1) {
        $err = "Akun Anda berstatus NONAKTIF.";
    } else {
        $_SESSION['user_id'] = (int)$u['id'];
        $_SESSION['username'] = $u['username'];
        $_SESSION['nama_lengkap'] = $u['nama_lengkap'];
        $_SESSION['role'] = $u['role'];
        header("Location: " . ($u['role'] === 'Owner' ? "index.php" : "gadai_barang.php"));
        exit;
    }
}
?>
<!doctype html>
<html lang="id">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Login — <?= e(NAMA_APLIKASI); ?></title>
  <link rel="icon" type="image/svg+xml" href="assets/favicon.svg">
  <style>
    body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#163628;font-family:system-ui,sans-serif;padding:16px}
    .box{background:#fff;padding:28px;border-radius:10px;width:100%;max-width:400px;box-shadow:0 10px 30px rgba(0,0,0,0.2)}
    .f{margin-bottom:12px}
    .f label{display:block;font-size:12px;font-weight:600;margin-bottom:4px;color:#163628}
    .f input{width:100%;padding:9px 10px;border:1px solid #c6d3cb;border-radius:6px;box-sizing:border-box;font-size:13.5px}
    .btn{width:100%;padding:10px;background:#2B7A58;color:#fff;border:none;border-radius:6px;font-weight:700;cursor:pointer;font-size:14px}
    .btn:hover{background:#226347}
    .err{background:#fef2f2;color:#991b1b;padding:9px 11px;border-radius:6px;font-size:12.5px;margin-bottom:12px;border:1px solid #fecaca}
  </style>
</head>
<body>
<div class="box">
  <div style="text-align:center;margin-bottom:10px;color:#2B7A58;"><?= svg_icon('shield', 32); ?></div>
  <h2 style="margin:0 0 4px;font-size:18px;text-align:center;color:#163628;"><?= e(NAMA_APLIKASI); ?></h2>
  <p style="margin:0 0 18px;font-size:12px;color:#53665b;text-align:center;">Login Petugas Portal Gadai Syariah</p>
  <?php if ($err): ?><div class="err"><?= e($err); ?></div><?php endif; ?>
  <form method="POST">
    <div class="f"><label>Username</label><input type="text" name="username" required autofocus placeholder="adiministrator / kasir"></div>
    <div class="f"><label>Password</label><input type="password" name="password" required placeholder="123456"></div>
    <button type="submit" class="btn">Masuk ke Portal</button>
  </form>
  <div style="margin-top:14px;padding:10px;background:#f8fbf9;border:1px solid #d8e2dc;border-radius:6px;font-size:11.5px;color:#53665b;">
    <div><strong>Owner:</strong> <code>adiministrator</code> / <code>123456</code></div>
    <div><strong>Kasir:</strong> <code>kasir</code> / <code>123456</code></div>
  </div>
</div>
</body>
</html>
