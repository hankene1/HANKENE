package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.data.local.BioPointageDatabase
import com.example.data.repository.BioPointageRepository
import com.example.data.security.CryptoEngine
import com.example.data.security.EmbeddedSecureBiometricProvider
import com.example.data.security.RealServerTimeAuthority
import com.example.ui.BioPointageViewModel
import com.example.ui.PrimaryMode
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.ArchitecturePhase1Screen
import com.example.ui.screens.EmployeeSelfServiceScreen
import com.example.ui.screens.KioskTerminalScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private val database: BioPointageDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            BioPointageDatabase::class.java,
            "biopointage_pro_enterprise.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    private val cryptoEngine by lazy { CryptoEngine() }
    private val timeAuthority by lazy { RealServerTimeAuthority(cryptoEngine) }
    private val biometricProvider by lazy {
        val bm = BiometricManager.from(applicationContext)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        EmbeddedSecureBiometricProvider(
            cryptoEngine = cryptoEngine,
            isHardwareSensorPresent = canAuth != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            hasSystemBiometricsEnrolled = canAuth == BiometricManager.BIOMETRIC_SUCCESS
        )
    }

    private val repository by lazy {
        BioPointageRepository(
            dao = database.bioPointageDao(),
            cryptoEngine = cryptoEngine,
            timeAuthority = timeAuthority,
            biometricProvider = biometricProvider
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val vm: BioPointageViewModel = viewModel(
                    factory = BioPointageViewModel.Factory(
                        dao = database.bioPointageDao(),
                        repository = repository,
                        cryptoEngine = cryptoEngine,
                        timeAuthority = timeAuthority,
                        biometricProvider = biometricProvider
                    )
                )
                BioPointageAppShell(
                    viewModel = vm,
                    onInvokeSystemBiometricPrompt = { onAuthenticated ->
                        triggerSystemBiometricPrompt(onAuthenticated)
                    }
                )
            }
        }
    }

    private fun triggerSystemBiometricPrompt(onAuthenticated: () -> Unit) {
        val bm = BiometricManager.from(this)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Vérification Biométrique — BioPointage Pro")
                .setSubtitle("Authentification matérielle TEE (Aucune image transmise)")
                .setNegativeButtonText("Utiliser la vérification interne")
                .build()

            val prompt = BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onAuthenticated()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // Si l'émulateur n'a pas d'empreinte configurée ou si l'opérateur clique sur le bouton secondaire,
                        // on vérifie immédiatement le gabarit chiffré AES-256-GCM en base.
                        onAuthenticated()
                    }
                }
            )
            prompt.authenticate(promptInfo)
        } else {
            onAuthenticated()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioPointageAppShell(
    viewModel: BioPointageViewModel,
    onInvokeSystemBiometricPrompt: (onAuthenticated: () -> Unit) -> Unit
) {
    val primaryMode by viewModel.primaryMode.collectAsStateWithLifecycle()
    val adminSection by viewModel.adminSection.collectAsStateWithLifecycle()
    val activeCompanyId by viewModel.activeCompanyId.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val enrollments by viewModel.enrollments.collectAsStateWithLifecycle()
    val consents by viewModel.consents.collectAsStateWithLifecycle()
    val attendanceRecords by viewModel.attendanceRecords.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val leaves by viewModel.leaves.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val hardwareStatus by viewModel.hardwareStatus.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnlineMode.collectAsStateWithLifecycle()
    val serverClock by viewModel.serverClockDisplay.collectAsStateWithLifecycle()
    val serverDate by viewModel.serverDateDisplay.collectAsStateWithLifecycle()
    val feedbackBanner by viewModel.userFeedbackBanner.collectAsStateWithLifecycle()
    val isErrorBanner by viewModel.isErrorBanner.collectAsStateWithLifecycle()
    val kioskConfirmation by viewModel.kioskConfirmation.collectAsStateWithLifecycle()
    val exportedReportContent by viewModel.exportedReportContent.collectAsStateWithLifecycle()

    val activeCompanyName = companies.find { it.id == activeCompanyId }?.name ?: "Teranga Tech & Logistique SA"

    if (primaryMode != PrimaryMode.ARCHITECTURE_PHASE1) {
        BackHandler {
            viewModel.selectPrimaryMode(PrimaryMode.ARCHITECTURE_PHASE1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "BioPointage Pro",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "$activeCompanyName • $serverClock",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    // Sélecteur d'entreprise (démonstration de l'isolation stricte Multi-Tenant)
                    FilledTonalButton(
                        onClick = {
                            val nextCompany = companies.firstOrNull { it.id != activeCompanyId }?.id ?: "COMP-DAKAR-01"
                            viewModel.selectCompany(nextCompany)
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("btn_switch_company")
                    ) {
                        Icon(Icons.Default.Business, contentDescription = "Changer d'entreprise", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (activeCompanyId.contains("DAKAR")) "Teranga SA" else "Sahel Énergie",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Bascule En ligne / Hors ligne
                    IconButton(
                        onClick = { viewModel.toggleOnlineOfflineMode() },
                        modifier = Modifier.testTag("btn_toggle_online_offline")
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Basculer mode hors ligne",
                            tint = if (isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = primaryMode == PrimaryMode.ARCHITECTURE_PHASE1,
                    onClick = { viewModel.selectPrimaryMode(PrimaryMode.ARCHITECTURE_PHASE1) },
                    icon = { Icon(Icons.Default.Architecture, contentDescription = "Architecture Phase 1") },
                    label = { Text("Phase 1") },
                    modifier = Modifier.testTag("nav_phase1")
                )
                NavigationBarItem(
                    selected = primaryMode == PrimaryMode.KIOSK_TERMINAL,
                    onClick = { viewModel.selectPrimaryMode(PrimaryMode.KIOSK_TERMINAL) },
                    icon = { Icon(Icons.Default.Fingerprint, contentDescription = "Borne Kiosque") },
                    label = { Text("Borne Kiosque") },
                    modifier = Modifier.testTag("nav_kiosk")
                )
                NavigationBarItem(
                    selected = primaryMode == PrimaryMode.ADMIN_CONSOLE,
                    onClick = { viewModel.selectPrimaryMode(PrimaryMode.ADMIN_CONSOLE) },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin SaaS") },
                    label = { Text("Admin SaaS") },
                    modifier = Modifier.testTag("nav_admin")
                )
                NavigationBarItem(
                    selected = primaryMode == PrimaryMode.EMPLOYEE_SELF_SERVICE,
                    onClick = { viewModel.selectPrimaryMode(PrimaryMode.EMPLOYEE_SELF_SERVICE) },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Portail Employé") },
                    label = { Text("Employé") },
                    modifier = Modifier.testTag("nav_employee")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = feedbackBanner != null) {
                feedbackBanner?.let { msg ->
                    Surface(
                        color = if (isErrorBanner) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("status_feedback_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.dismissBanner() }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }

            when (primaryMode) {
                PrimaryMode.ARCHITECTURE_PHASE1 -> ArchitecturePhase1Screen(
                    hardwareStatus = hardwareStatus,
                    companies = companies,
                    activeCompanyId = activeCompanyId,
                    employeesCount = employees.size,
                    enrollmentsCount = enrollments.size,
                    attendanceCount = attendanceRecords.size,
                    auditLogsCount = auditLogs.size,
                    isOnline = isOnline,
                    serverClock = serverClock,
                    onTestCrossTenantIsolation = {
                        val foreignEmpId = if (activeCompanyId == "COMP-DAKAR-01") "EMP-SHL-001" else "EMP-DKR-001"
                        viewModel.testCrossTenantIsolationAttack(foreignEmpId)
                    },
                    onLaunchKiosk = { viewModel.selectPrimaryMode(PrimaryMode.KIOSK_TERMINAL) },
                    onLaunchAdmin = { viewModel.selectPrimaryMode(PrimaryMode.ADMIN_CONSOLE) }
                )

                PrimaryMode.KIOSK_TERMINAL -> KioskTerminalScreen(
                    companyName = activeCompanyName,
                    sites = sites,
                    employees = employees,
                    enrollments = enrollments,
                    serverDate = serverDate,
                    serverClock = serverClock,
                    isOnline = isOnline,
                    kioskConfirmation = kioskConfirmation,
                    onClearConfirmation = { viewModel.clearKioskConfirmation() },
                    onExecutePunch = { empId, method, isOut, liveness, pin, outsideGeo ->
                        viewModel.executePunch(empId, method, isOut, liveness, pin, outsideGeo)
                    },
                    onRequestHardwareBiometricPrompt = onInvokeSystemBiometricPrompt
                )

                PrimaryMode.ADMIN_CONSOLE -> AdminDashboardScreen(
                    activeSection = adminSection,
                    onSelectSection = { viewModel.selectAdminSection(it) },
                    employees = employees,
                    enrollments = enrollments,
                    consents = consents,
                    attendanceRecords = attendanceRecords,
                    schedules = schedules,
                    leaves = leaves,
                    sites = sites,
                    devices = devices,
                    auditLogs = auditLogs,
                    notifications = notifications,
                    exportedReportContent = exportedReportContent,
                    onGenerateCsvReport = { viewModel.generateCsvReport() },
                    onClearExportedReport = { viewModel.clearExportedReport() },
                    onAddEmployee = { fn, ln, mat, job, mail, ph, pin ->
                        viewModel.addNewEmployee(fn, ln, mat, job, mail, ph, pin)
                    },
                    onEnrollBiometric = { empId, mod, consent, live ->
                        viewModel.enrollEmployeeBiometric(empId, mod, consent, live)
                    },
                    onPurgeBiometrics = { empId, reason ->
                        viewModel.revokeAndPurgeBiometrics(empId, reason)
                    },
                    onManualCorrection = { rec, arr, dep, st, justif ->
                        viewModel.correctAttendanceManually(rec, arr, dep, st, justif)
                    },
                    onSyncOfflineRecords = { viewModel.syncPendingOfflineRecords() }
                )

                PrimaryMode.EMPLOYEE_SELF_SERVICE -> EmployeeSelfServiceScreen(
                    employees = employees,
                    enrollments = enrollments,
                    attendanceRecords = attendanceRecords,
                    onGoToKioskPunch = { viewModel.selectPrimaryMode(PrimaryMode.KIOSK_TERMINAL) }
                )
            }
        }
    }
}
