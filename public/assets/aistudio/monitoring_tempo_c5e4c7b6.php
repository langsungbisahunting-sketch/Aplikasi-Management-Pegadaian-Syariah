<?php
require_once "config/koneksi.php";
require_owner();
$page_title = "Monitoring Jatuh Tempo";
$ok = "";
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
        $ok = "Akad <strong>" . e($nak) . "</strong> berhasil dihapus.";
    }
}
$res = mysqli_query($conn, "SELECT * FROM transaksi_gadai WHERE status_gadai IN ('AKTIF','DIPERPANJANG') ORDER BY jatuh_tempo ASC");
$list = [];
while ($r = mysqli_fetch_assoc($res)) $list[] = $r;
if (isset($_GET['export']) && $_GET['export'] === 'xlsx') {
    $rows = [['No. Akad','Jatuh Tempo','Rahin','No. HP','Kategori','Barang','Pokok Qardh (Rp)','Ujrah (Rp)']];
    foreach ($list as $r) $rows[] = [$r['no_akad'],$r['jatuh_tempo'],$r['nama_nasabah'],$r['no_hp'],$r['kategori_barang'],$r['nama_barang'],(float)$r['pokok_pinjaman'],(float)$r['biaya_ujrah_periode']];
    SyariahXlsx::download('monitoring_tempo_' . date('Y-m-d') . '.xlsx', [['name' => 'Monitoring', 'rows' => $rows]]);
}
require_once "includes/header.php";
?>
<?php if ($ok): ?><div class="msg-ok no-print"><?= $ok; ?></div><?php endif; ?>
<div class="panel panel-yellow no-print"><div class="panel-head"><div class="panel-title"><?= svg_icon('clock', 16); ?> Monitoring Jatuh Tempo</div><div style="display:flex;gap:6px;"><a class="btn btn-sm" href="monitoring_tempo.php?export=xlsx"><?= svg_icon('download', 14); ?> Export Excel (.xlsx)</a> <button type="button" class="btn btn-sm btn-primary" onclick="window.print()"><?= svg_icon('printer', 14); ?> Cetak</button></div></div></div>
<div class="panel"><div class="table-box"><table><thead><tr><th>No. Akad</th><th>Jatuh Tempo</th><th>Rahin</th><th>Marhun</th><th>Pokok &amp; Ujrah</th><th>Keterangan Tempo</th><th class="no-print">Aksi</th></tr></thead><tbody>
<?php foreach($list as $r):
  $diff = (int)floor((strtotime($r['jatuh_tempo']) - strtotime(date('Y-m-d'))) / 86400);
  if ($diff < 0) $ket = '<span class="status-tag" style="background:#fef2f2;color:#991b1b;border:1px solid #fecaca;">Lewat ' . abs($diff) . ' Hari</span>';
  elseif ($diff === 0) $ket = '<span class="status-tag" style="background:#fffbeb;color:#92400e;border:1px solid #fde68a;">Jatuh Tempo Hari Ini</span>';
  elseif ($diff <= 3) $ket = '<span class="status-tag" style="background:#fffbeb;color:#92400e;border:1px solid #fde68a;">H-' . $diff . '</span>';
  else $ket = '<span class="status-tag" style="background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0;">' . $diff . ' Hari Lagi</span>';
?>
<tr><td class="mono"><a href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><strong><?= e($r['no_akad']); ?></strong></a></td><td class="mono"><strong><?= e($r['jatuh_tempo']); ?></strong></td><td><a href="data_pelanggan.php?q=<?= urlencode($r['nama_nasabah']); ?>"><?= e($r['nama_nasabah']); ?></a><div class="cell-sub mono"><?= e($r['no_hp']); ?></div></td><td><?= e($r['kategori_barang']); ?> - <?= e($r['nama_barang']); ?></td><td class="mono"><?= rupiah($r['pokok_pinjaman']); ?> + <?= rupiah($r['biaya_ujrah_periode']); ?></td><td><?= $ket; ?></td>
<td class="no-print"><div style="display:flex;gap:5px;flex-wrap:wrap;"><a class="btn btn-sm" href="gadai_barang.php?cetak=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('printer', 13); ?> Nota</a><a class="btn btn-sm" href="perpanjang_gadai.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('refresh', 13); ?> Perpanjang</a><a class="btn btn-sm" href="tebus_barang.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('check-circle', 13); ?> Tebus</a><a class="btn btn-sm" href="lelang_barang.php?no_akad=<?= urlencode($r['no_akad']); ?>"><?= svg_icon('gavel', 13); ?> Lelang</a><a class="btn btn-sm" href="gadai_barang.php?edit=<?= (int)$r['id']; ?>"><?= svg_icon('edit', 13); ?> Edit</a><form method="POST" style="display:inline;" onsubmit="return confirm('Hapus akad <?= e($r['no_akad']); ?>?')"><input type="hidden" name="id" value="<?= (int)$r['id']; ?>"><button type="submit" name="delete_gadai" class="btn btn-sm btn-danger"><?= svg_icon('trash', 13); ?> Hapus</button></form></div></td></tr>
<?php endforeach; ?>
</tbody></table></div></div>
<?php require_once "includes/footer.php"; ?>
