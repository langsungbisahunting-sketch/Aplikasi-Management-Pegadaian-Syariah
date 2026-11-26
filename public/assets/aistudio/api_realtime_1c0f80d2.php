<?php
require_once "config/koneksi.php";
header('Content-Type: application/json; charset=utf-8');
if (empty($_SESSION['user_id'])) {
    http_response_code(401);
    echo json_encode(['ok' => false]);
    exit;
}
$t = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(MAX(id),0) AS mx, COALESCE(SUM(pokok_pinjaman+total_ujrah_dibayar),0) AS sm, COALESCE(GROUP_CONCAT(status_gadai ORDER BY id),'') AS st FROM transaksi_gadai"));
$rp = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(MAX(id),0) AS mx, COALESCE(SUM(biaya_ujrah_dibayar),0) AS sm FROM riwayat_perpanjangan"));
$j = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(MAX(id),0) AS mx, COALESCE(SUM(total_kas),0) AS sm FROM jurnal_akuntansi"));
$s = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(MAX(id),0) AS mx, COALESCE(SUM(uang_fisik_aktual),0) AS sm FROM sesi_tutup_toko"));
$u = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(SUM(aktif),0) AS sa FROM users"));
$p = mysqli_fetch_assoc(mysqli_query($conn, "SELECT updated_at, modal_awal, nama_aplikasi FROM pengaturan_sistem WHERE id=1"));
$lt = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(SUM(is_default+aktif),0) AS sm FROM rumus_ltv"));
$pu = mysqli_fetch_assoc(mysqli_query($conn, "SELECT COUNT(*) AS c, COALESCE(SUM(ujrah_per_periode+biaya_admin+aktif),0) AS sm FROM paket_ujrah"));
$sig = md5(json_encode([$t, $rp, $j, $s, $u, $p, $lt, $pu]));
echo json_encode(['ok' => true, 'signature' => $sig, 'time' => date('c')]);
