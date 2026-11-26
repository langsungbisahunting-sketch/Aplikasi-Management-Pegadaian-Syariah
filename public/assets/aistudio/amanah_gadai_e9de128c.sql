-- Database: amanah_gadai (Portal Gadai Syariah - HP, Motor, Emas)
-- Kompatibel dengan MySQL 5.7+ / MariaDB 10.4+ (XAMPP)
-- Catatan: User default (adiministrator / 123456 dan kasir / 123456)
-- otomatis dibuat dengan password_hash() PHP pada akses pertama di config/koneksi.php.

CREATE DATABASE IF NOT EXISTS `amanah_gadai` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `amanah_gadai`;

DROP TABLE IF EXISTS `idempotency_tokens`;
DROP TABLE IF EXISTS `dokumen_pelanggan`;
DROP TABLE IF EXISTS `sesi_tutup_toko`;
DROP TABLE IF EXISTS `jurnal_akuntansi`;
DROP TABLE IF EXISTS `riwayat_perpanjangan`;
DROP TABLE IF EXISTS `transaksi_gadai`;
DROP TABLE IF EXISTS `paket_ujrah`;
DROP TABLE IF EXISTS `rumus_ltv`;
DROP TABLE IF EXISTS `pengaturan_sistem`;
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(60) NOT NULL,
  `nama_lengkap` VARCHAR(120) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `role` ENUM('Owner','Kasir') NOT NULL DEFAULT 'Kasir',
  `no_hp` VARCHAR(30) NOT NULL DEFAULT '',
  `aktif` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pengaturan_sistem` (
  `id` INT UNSIGNED NOT NULL DEFAULT 1,
  `nama_aplikasi` VARCHAR(120) NOT NULL DEFAULT 'AMANAH GADAI SYARIAH',
  `alamat_kantor` VARCHAR(200) NOT NULL DEFAULT 'Jl. Cipinang Muara 2 No. 18, Jakarta Timur',
  `kontak_kantor` VARCHAR(120) NOT NULL DEFAULT 'Telp / WhatsApp: 0812-6313-1041',
  `modal_awal` DECIMAL(15,2) NOT NULL DEFAULT 100000000.00,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `rumus_ltv` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `kategori` ENUM('HP','Motor','Emas') NOT NULL,
  `persentase` DECIMAL(5,2) NOT NULL,
  `label` VARCHAR(120) NOT NULL,
  `deskripsi` VARCHAR(255) NOT NULL DEFAULT '',
  `aktif` TINYINT(1) NOT NULL DEFAULT 0,
  `is_default` TINYINT(1) NOT NULL DEFAULT 0,
  `is_custom` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `paket_ujrah` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `kode` VARCHAR(30) NOT NULL,
  `nama_paket` VARCHAR(150) NOT NULL,
  `kategori` ENUM('HP','Motor','Emas') NOT NULL,
  `sub_tipe` VARCHAR(100) NOT NULL DEFAULT '',
  `ujrah_per_periode` DECIMAL(15,2) NOT NULL,
  `biaya_admin` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `hari_per_periode` INT NOT NULL DEFAULT 30,
  `deskripsi_syariah` VARCHAR(255) NOT NULL DEFAULT '',
  `aktif` TINYINT(1) NOT NULL DEFAULT 1,
  `is_default` TINYINT(1) NOT NULL DEFAULT 0,
  `is_custom` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `transaksi_gadai` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `no_akad` VARCHAR(40) NOT NULL,
  `tanggal_akad` DATE NOT NULL,
  `jatuh_tempo` DATE NOT NULL,
  `nama_nasabah` VARCHAR(120) NOT NULL,
  `nik_nasabah` VARCHAR(40) NOT NULL DEFAULT '',
  `no_hp` VARCHAR(30) NOT NULL DEFAULT '',
  `alamat_nasabah` VARCHAR(255) NOT NULL DEFAULT '',
  `kategori_barang` ENUM('HP','Motor','Emas') NOT NULL DEFAULT 'HP',
  `nama_barang` VARCHAR(150) NOT NULL,
  `spesifikasi_ringkas` VARCHAR(255) NOT NULL DEFAULT '',
  `kelengkapan_barang` VARCHAR(200) NOT NULL DEFAULT '',
  `kondisi_barang` VARCHAR(255) NOT NULL DEFAULT '',
  `detail_json` TEXT DEFAULT NULL,
  `nilai_taksiran` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `ltv_persen` DECIMAL(5,2) NOT NULL DEFAULT 75.00,
  `plafon_maksimal` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `pokok_pinjaman` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `biaya_admin` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `dana_bersih_diterima` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `ujrah_package_id` INT UNSIGNED DEFAULT NULL,
  `ujrah_package_nama` VARCHAR(150) NOT NULL DEFAULT '',
  `biaya_ujrah_periode` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `hari_per_periode` INT NOT NULL DEFAULT 30,
  `total_ujrah_dibayar` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `jumlah_perpanjangan` INT NOT NULL DEFAULT 0,
  `tanggal_perpanjang_terakhir` DATE DEFAULT NULL,
  `status_gadai` ENUM('AKTIF','DIPERPANJANG','LUNAS','LELANG') NOT NULL DEFAULT 'AKTIF',
  `tanggal_tebus` DATE DEFAULT NULL,
  `ujrah_tebus_dibayar` DECIMAL(15,2) DEFAULT NULL,
  `total_tebusan` DECIMAL(15,2) DEFAULT NULL,
  `tanggal_lelang` DATE DEFAULT NULL,
  `harga_lelang` DECIMAL(15,2) DEFAULT NULL,
  `sisa_dikembalikan_lelang` DECIMAL(15,2) DEFAULT NULL,
  `user_id` INT UNSIGNED DEFAULT NULL,
  `nama_kasir` VARCHAR(120) NOT NULL DEFAULT '',
  `file_dokumen` VARCHAR(255) DEFAULT NULL,
  `nama_file_asli` VARCHAR(255) DEFAULT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no_akad` (`no_akad`),
  KEY `idx_status` (`status_gadai`),
  KEY `idx_tgl` (`tanggal_akad`),
  KEY `idx_jt` (`jatuh_tempo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `riwayat_perpanjangan` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `no_akad` VARCHAR(40) NOT NULL,
  `tanggal_perpanjang` DATE NOT NULL,
  `jatuh_tempo_lama` DATE NOT NULL,
  `jatuh_tempo_baru` DATE NOT NULL,
  `jumlah_periode` INT NOT NULL DEFAULT 1,
  `biaya_ujrah_dibayar` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `user_id` INT UNSIGNED DEFAULT NULL,
  `nama_kasir` VARCHAR(120) NOT NULL DEFAULT '',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `jurnal_akuntansi` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tanggal` DATE NOT NULL,
  `no_akad` VARCHAR(40) NOT NULL,
  `jenis_menu` ENUM('MODAL_AWAL','GADAI_BARANG','PERPANJANG_GADAI','TEBUS_BARANG','LELANG_BARANG') NOT NULL,
  `arus_kas` ENUM('MASUK','KELUAR') NOT NULL,
  `akun_debit` VARCHAR(100) NOT NULL,
  `akun_kredit` VARCHAR(100) NOT NULL,
  `pokok_nominal` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `pendapatan_admin` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `pendapatan_ujrah` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `total_kas` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `keterangan` VARCHAR(255) NOT NULL,
  `user_id` INT UNSIGNED DEFAULT NULL,
  `nama_kasir` VARCHAR(120) NOT NULL DEFAULT '',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ja_tgl` (`tanggal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sesi_tutup_toko` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tanggal` DATE NOT NULL,
  `status` ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
  `saldo_awal_kas` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `jumlah_gadai_baru` INT NOT NULL DEFAULT 0,
  `jumlah_perpanjangan` INT NOT NULL DEFAULT 0,
  `jumlah_tebusan` INT NOT NULL DEFAULT 0,
  `total_uang_masuk` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `total_uang_keluar` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `saldo_kas_sistem` DECIMAL(15,2) NOT NULL DEFAULT 0.00,
  `uang_fisik_aktual` DECIMAL(15,2) DEFAULT NULL,
  `selisih_kas` DECIMAL(15,2) DEFAULT NULL,
  `catatan_selisih` VARCHAR(255) NOT NULL DEFAULT '',
  `kasir_id` INT UNSIGNED DEFAULT NULL,
  `nama_kasir` VARCHAR(120) NOT NULL DEFAULT '',
  `closed_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tgl_sesi` (`tanggal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `idempotency_tokens` (
  `token` VARCHAR(80) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dokumen_pelanggan` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `nik_nasabah` VARCHAR(40) NOT NULL,
  `nama_nasabah` VARCHAR(120) NOT NULL DEFAULT '',
  `nama_file_simpan` VARCHAR(255) NOT NULL,
  `nama_file_asli` VARCHAR(255) NOT NULL,
  `ukuran_byte` BIGINT UNSIGNED NOT NULL DEFAULT 0,
  `keterangan` VARCHAR(255) NOT NULL DEFAULT 'Bukti Data Diri & Dokumen Pendukung',
  `user_id` INT UNSIGNED DEFAULT NULL,
  `nama_kasir` VARCHAR(120) NOT NULL DEFAULT '',
  `uploaded_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nik_dokumen` (`nik_nasabah`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `pengaturan_sistem` (`id`, `nama_aplikasi`, `alamat_kantor`, `kontak_kantor`, `modal_awal`) VALUES
(1, 'AMANAH GADAI SYARIAH', 'Jl. Cipinang Muara 2 No. 18, Jakarta Timur', 'Telp / WhatsApp: 0812-6313-1041', 100000000.00);

INSERT INTO `rumus_ltv` (`kategori`, `persentase`, `label`, `deskripsi`, `aktif`, `is_default`, `is_custom`) VALUES
('HP', 60.00, 'Plafon 60% (HP)', 'Konservatif 60% dari nilai taksiran pasar HP', 0, 0, 0),
('HP', 70.00, 'Plafon 70% (HP)', 'Moderat 70% dari nilai taksiran pasar HP', 0, 0, 0),
('HP', 75.00, 'Plafon 75% (HP)', 'Standar default 75% dari nilai taksiran pasar HP', 1, 1, 0),
('HP', 80.00, 'Plafon 80% (HP)', 'Fleksibel 80% dari nilai taksiran pasar HP', 0, 0, 0),
('HP', 85.00, 'Plafon 85% (HP)', 'Maksimal 85% dari nilai taksiran pasar HP', 0, 0, 0),
('Motor', 60.00, 'Plafon 60% (Motor)', 'Konservatif 60% dari nilai taksiran pasar Motor', 0, 0, 0),
('Motor', 70.00, 'Plafon 70% (Motor)', 'Moderat 70% dari nilai taksiran pasar Motor', 0, 0, 0),
('Motor', 75.00, 'Plafon 75% (Motor)', 'Standar default 75% dari nilai taksiran pasar Motor', 1, 1, 0),
('Motor', 80.00, 'Plafon 80% (Motor)', 'Fleksibel 80% dari nilai taksiran pasar Motor', 0, 0, 0),
('Motor', 85.00, 'Plafon 85% (Motor)', 'Maksimal 85% dari nilai taksiran pasar Motor', 0, 0, 0),
('Emas', 60.00, 'Plafon 60% (Emas)', 'Konservatif 60% dari nilai taksiran Emas', 0, 0, 0),
('Emas', 70.00, 'Plafon 70% (Emas)', 'Moderat 70% dari nilai taksiran Emas', 0, 0, 0),
('Emas', 75.00, 'Plafon 75% (Emas)', 'Menengah 75% dari nilai taksiran Emas', 0, 0, 0),
('Emas', 80.00, 'Plafon 80% (Emas)', 'Tinggi 80% dari nilai taksiran Emas', 0, 0, 0),
('Emas', 85.00, 'Plafon 85% (Emas)', 'Standar default 85% dari nilai taksiran Emas', 1, 1, 0);

INSERT INTO `paket_ujrah` (`kode`, `nama_paket`, `kategori`, `sub_tipe`, `ujrah_per_periode`, `biaya_admin`, `hari_per_periode`, `deskripsi_syariah`, `aktif`, `is_default`, `is_custom`) VALUES
('KLS-A', 'Tarif Kelas A - HP / Elektronik', 'HP', 'Unit HP / Gadget', 50000.00, 25000.00, 30, 'Biaya riil brankas anti-lembab, segel amanah & perawatan baterai 30 hari', 1, 1, 0),
('KLS-B', 'Tarif Kelas B - Motor Titip Dokumen (BPKB)', 'Motor', 'Titip Dokumen (BPKB)', 75000.00, 50000.00, 30, 'Biaya riil arsip tahan api, verifikasi BPKB & pengawasan 30 hari', 1, 0, 0),
('KLS-C', 'Tarif Kelas C - Motor Titip Unit & Dokumen', 'Motor', 'Titip Unit & Dokumen', 150000.00, 50000.00, 30, 'Biaya riil sewa ruang garasi tertutup, keamanan 24 jam & perawatan unit 30 hari', 1, 1, 0),
('KLS-D', 'Tarif Kelas D - Emas < 10 gr', 'Emas', 'Emas < 10 gr', 40000.00, 20000.00, 30, 'Biaya riil kotak brankas SDB asuransi emas kecil (< 10 gram) 30 hari', 1, 1, 0),
('KLS-E', 'Tarif Kelas E - Emas >= 10 gr', 'Emas', 'Emas >= 10 gr', 80000.00, 30000.00, 30, 'Biaya riil kotak brankas SDB prioritas emas >= 10 gram 30 hari', 1, 0, 0);

INSERT INTO `jurnal_akuntansi` (`tanggal`, `no_akad`, `jenis_menu`, `arus_kas`, `akun_debit`, `akun_kredit`, `pokok_nominal`, `pendapatan_admin`, `pendapatan_ujrah`, `total_kas`, `keterangan`, `user_id`, `nama_kasir`) VALUES
(CURDATE(), 'MODAL-AWAL', 'MODAL_AWAL', 'MASUK', 'Kas Operasional', 'Modal Awal Pemilik', 100000000.00, 0.00, 0.00, 100000000.00, 'Setoran Modal Awal Usaha Gadai Syariah', 1, 'Administrator (Owner)');
