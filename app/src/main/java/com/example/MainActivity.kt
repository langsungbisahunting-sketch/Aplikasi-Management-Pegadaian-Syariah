package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppRoute
import com.example.ui.AppViewModel
import com.example.ui.components.FeedbackBanners
import com.example.ui.components.PortalTopHeader
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DataBarangScreen
import com.example.ui.screens.DataPelangganScreen
import com.example.ui.screens.DokumenPelangganScreen
import com.example.ui.screens.GadaiBarangScreen
import com.example.ui.screens.LaporanAkuntansiScreen
import com.example.ui.screens.LelangBarangScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ManajemenUserScreen
import com.example.ui.screens.MonitoringTempoScreen
import com.example.ui.screens.PengaturanRumusScreen
import com.example.ui.screens.PerpanjangGadaiScreen
import com.example.ui.screens.TebusBarangScreen
import com.example.ui.screens.TutupTokoScreen
import com.example.ui.theme.AmanahGadaiTheme
import com.example.ui.theme.ForestDark
import com.example.ui.theme.SurfaceSubtle
import com.example.ui.theme.TextMuted

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmanahGadaiTheme {
                AmanahGadaiRoot()
            }
        }
    }
}

@Composable
fun AmanahGadaiRoot(vm: AppViewModel = viewModel()) {
    val currentUser by vm.currentUser.collectAsStateWithLifecycle()
    val currentRoute by vm.currentRoute.collectAsStateWithLifecycle()
    val canBack by vm.canNavigateBack.collectAsStateWithLifecycle()
    val pengaturan by vm.pengaturan.collectAsStateWithLifecycle()
    val loginError by vm.loginError.collectAsStateWithLifecycle()
    val okMessage by vm.okMessage.collectAsStateWithLifecycle()
    val errMessage by vm.errMessage.collectAsStateWithLifecycle()

    val users by vm.users.collectAsStateWithLifecycle()
    val rumusLtv by vm.rumusLtv.collectAsStateWithLifecycle()
    val paketUjrah by vm.paketUjrah.collectAsStateWithLifecycle()
    val transaksiGadai by vm.transaksiGadai.collectAsStateWithLifecycle()
    val riwayatPerpanjangan by vm.riwayatPerpanjangan.collectAsStateWithLifecycle()
    val jurnalAkuntansi by vm.jurnalAkuntansi.collectAsStateWithLifecycle()
    val sesiTutupToko by vm.sesiTutupToko.collectAsStateWithLifecycle()
    val dokumenMap by vm.dokumenMap.collectAsStateWithLifecycle()
    val customerSummaries by vm.customerSummaries.collectAsStateWithLifecycle()

    val user = currentUser
    if (user == null) {
        LoginScreen(
            namaAplikasi = pengaturan.namaAplikasi,
            errorMessage = loginError,
            onLogin = { un, pw -> vm.login(un, pw) }
        )
        return
    }

    BackHandler(enabled = canBack) {
        vm.navigateBack()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSubtle),
        containerColor = SurfaceSubtle,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestDark)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                PortalTopHeader(
                    appName = pengaturan.namaAplikasi,
                    user = user,
                    currentRoute = currentRoute,
                    onSelectRoute = { vm.navigateTo(it) },
                    onLogout = { vm.logout() }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 960.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                FeedbackBanners(
                    okMessage = okMessage,
                    errMessage = errMessage,
                    onDismiss = { vm.clearBanners() }
                )

                when (currentRoute) {
                    AppRoute.DASHBOARD -> DashboardScreen(
                        vm = vm,
                        pengaturan = pengaturan,
                        transaksi = transaksiGadai,
                        jurnal = jurnalAkuntansi,
                        dokumenMap = dokumenMap
                    )
                    AppRoute.GADAI_BARANG -> GadaiBarangScreen(
                        vm = vm,
                        currentUser = user,
                        pengaturan = pengaturan,
                        transaksiList = transaksiGadai,
                        ltvList = rumusLtv,
                        paketList = paketUjrah,
                        dokumenMap = dokumenMap
                    )
                    AppRoute.PERPANJANG_GADAI -> PerpanjangGadaiScreen(
                        vm = vm,
                        currentUser = user,
                        transaksiList = transaksiGadai,
                        riwayatList = riwayatPerpanjangan
                    )
                    AppRoute.TEBUS_BARANG -> TebusBarangScreen(
                        vm = vm,
                        currentUser = user,
                        transaksiList = transaksiGadai
                    )
                    AppRoute.DATA_BARANG -> DataBarangScreen(
                        vm = vm,
                        pengaturan = pengaturan,
                        transaksiList = transaksiGadai
                    )
                    AppRoute.DATA_PELANGGAN -> DataPelangganScreen(
                        vm = vm,
                        currentUser = user,
                        pengaturan = pengaturan,
                        customers = customerSummaries,
                        dokumenMap = dokumenMap
                    )
                    AppRoute.DOKUMEN_PELANGGAN -> DokumenPelangganScreen(
                        vm = vm,
                        customers = customerSummaries,
                        dokumenMap = dokumenMap
                    )
                    AppRoute.MONITORING_TEMPO -> MonitoringTempoScreen(
                        vm = vm,
                        pengaturan = pengaturan,
                        transaksiList = transaksiGadai
                    )
                    AppRoute.LELANG_BARANG -> LelangBarangScreen(
                        vm = vm,
                        transaksiList = transaksiGadai
                    )
                    AppRoute.LAPORAN_AKUNTANSI -> LaporanAkuntansiScreen(
                        vm = vm,
                        currentUser = user,
                        pengaturan = pengaturan,
                        users = users,
                        transaksiList = transaksiGadai,
                        jurnalList = jurnalAkuntansi,
                        sesiList = sesiTutupToko
                    )
                    AppRoute.TUTUP_TOKO -> TutupTokoScreen(
                        vm = vm,
                        currentUser = user,
                        pengaturan = pengaturan,
                        transaksiList = transaksiGadai,
                        riwayatList = riwayatPerpanjangan,
                        jurnalList = jurnalAkuntansi,
                        sesiList = sesiTutupToko
                    )
                    AppRoute.MANAJEMEN_USER -> ManajemenUserScreen(
                        vm = vm,
                        currentUser = user,
                        pengaturan = pengaturan,
                        users = users,
                        transaksiList = transaksiGadai,
                        jurnalList = jurnalAkuntansi
                    )
                    AppRoute.PENGATURAN_RUMUS -> PengaturanRumusScreen(
                        vm = vm,
                        pengaturan = pengaturan,
                        ltvList = rumusLtv,
                        paketList = paketUjrah
                    )
                }

                Text(
                    text = "${pengaturan.namaAplikasi} · ${pengaturan.alamatKantor} · ${pengaturan.kontakKantor}",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
            }
        }
    }
}
