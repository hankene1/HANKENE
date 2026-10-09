package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.BioPointageRepository
import com.example.data.security.CryptoEngine
import com.example.domain.biometrics.BiometricProvider
import com.example.domain.biometrics.HardwareBiometricStatus
import com.example.domain.model.*
import com.example.domain.time.ServerTimeAuthority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class PrimaryMode(val titleFr: String) {
    ARCHITECTURE_PHASE1("Phase 1 : Architecture"),
    KIOSK_TERMINAL("Borne Kiosque"),
    ADMIN_CONSOLE("Administration SaaS"),
    EMPLOYEE_SELF_SERVICE("Espace Employé")
}

enum class AdminSection(val labelFr: String) {
    DASHBOARD("Dashboard"),
    EMPLOYEES("Employés & Biométrie"),
    ATTENDANCE("Pointages & Corrections"),
    SCHEDULES("Plannings & Horaires"),
    LEAVES("Absences & Congés"),
    SITES_DEVICES("Sites, Bornes & Geofencing"),
    REPORTS_EXPORTS("Rapports & Exports"),
    CONSENTS_CDP("Conformité CDP / RGPD"),
    AUDIT_LOGS("Audit Logs Inaltérables")
}

data class KioskConfirmationBanner(
    val employeeFirstName: String,
    val employeeLastName: String,
    val employeeMatricule: String,
    val dateIso: String,
    val serverTimeHms: String,
    val statusLabel: String,
    val methodLabel: String,
    val syncStateLabel: String,
    val isDeparture: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class BioPointageViewModel(
    private val dao: BioPointageDao,
    private val repository: BioPointageRepository,
    private val cryptoEngine: CryptoEngine,
    private val timeAuthority: ServerTimeAuthority,
    private val biometricProvider: BiometricProvider
) : ViewModel() {

    private val _activeCompanyId = MutableStateFlow("COMP-DAKAR-01")
    val activeCompanyId: StateFlow<String> = _activeCompanyId.asStateFlow()

    private val _primaryMode = MutableStateFlow(PrimaryMode.ARCHITECTURE_PHASE1)
    val primaryMode: StateFlow<PrimaryMode> = _primaryMode.asStateFlow()

    private val _adminSection = MutableStateFlow(AdminSection.DASHBOARD)
    val adminSection: StateFlow<AdminSection> = _adminSection.asStateFlow()

    private val _activeRole = MutableStateFlow(UserRole.COMPANY_ADMIN)
    val activeRole: StateFlow<UserRole> = _activeRole.asStateFlow()

    private val _activeLanguage = MutableStateFlow(AppLanguage.FR)
    val activeLanguage: StateFlow<AppLanguage> = _activeLanguage.asStateFlow()

    private val _isOnlineMode = MutableStateFlow(true)
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    private val _serverClockDisplay = MutableStateFlow("08:30:00 UTC")
    val serverClockDisplay: StateFlow<String> = _serverClockDisplay.asStateFlow()

    private val _serverDateDisplay = MutableStateFlow("2026-10-04")
    val serverDateDisplay: StateFlow<String> = _serverDateDisplay.asStateFlow()

    private val _hardwareStatus = MutableStateFlow(
        HardwareBiometricStatus(
            isHardwareDetected = true,
            hasEnrolledSystemBiometrics = true,
            securityLevelDescription = "Android Keystore TEE (AES-256-GCM) + Capteur Biométrique",
            requiresExternalUsbSdk = false
        )
    )
    val hardwareStatus: StateFlow<HardwareBiometricStatus> = _hardwareStatus.asStateFlow()

    private val _userFeedbackBanner = MutableStateFlow<String?>(null)
    val userFeedbackBanner: StateFlow<String?> = _userFeedbackBanner.asStateFlow()

    private val _isErrorBanner = MutableStateFlow(false)
    val isErrorBanner: StateFlow<Boolean> = _isErrorBanner.asStateFlow()

    private val _kioskConfirmation = MutableStateFlow<KioskConfirmationBanner?>(null)
    val kioskConfirmation: StateFlow<KioskConfirmationBanner?> = _kioskConfirmation.asStateFlow()

    private val _exportedReportContent = MutableStateFlow<String?>(null)
    val exportedReportContent: StateFlow<String?> = _exportedReportContent.asStateFlow()

    // Flux réactifs scopés strictement par l'entreprise sélectionnée (Multi-Tenant Isolation)
    val companies: StateFlow<List<CompanyEntity>> = repository.observeCompanies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sites: StateFlow<List<SiteEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeSites(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val departments: StateFlow<List<DepartmentEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeDepartments(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedules: StateFlow<List<ScheduleEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeSchedules(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val employees: StateFlow<List<EmployeeEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeEmployees(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enrollments: StateFlow<List<BiometricEnrollmentEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeEnrollments(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consents: StateFlow<List<ConsentEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeConsents(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devices: StateFlow<List<DeviceEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeDevices(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendanceRecords: StateFlow<List<AttendanceRecordEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeAttendance(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val leaves: StateFlow<List<LeaveEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeLeaves(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeAuditLogs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = _activeCompanyId
        .flatMapLatest { repository.observeNotifications(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            DatabaseSeeder.seedIfEmpty(dao, repository, cryptoEngine)
            _hardwareStatus.value = biometricProvider.checkHardwareStatus()
        }
        viewModelScope.launch {
            while (true) {
                val ts = timeAuthority.acquireCertifiedTimestamp(_isOnlineMode.value)
                _serverDateDisplay.value = ts.formattedDateIso
                _serverClockDisplay.value = "${ts.formattedTimeHms} UTC"
                delay(1000L)
            }
        }
    }

    fun selectCompany(companyId: String) {
        _activeCompanyId.value = companyId
        _kioskConfirmation.value = null
        showMessage("Isolation Multi-Entreprises active : contexte basculé sur $companyId", isError = false)
    }

    fun selectPrimaryMode(mode: PrimaryMode) {
        _primaryMode.value = mode
        _kioskConfirmation.value = null
    }

    fun selectAdminSection(section: AdminSection) {
        _adminSection.value = section
    }

    fun selectRole(role: UserRole) {
        _activeRole.value = role
        if (role == UserRole.KIOSK_OPERATOR) {
            _primaryMode.value = PrimaryMode.KIOSK_TERMINAL
        } else if (role == UserRole.EMPLOYEE) {
            _primaryMode.value = PrimaryMode.EMPLOYEE_SELF_SERVICE
        }
    }

    fun selectLanguage(language: AppLanguage) {
        _activeLanguage.value = language
    }

    fun toggleOnlineOfflineMode() {
        val next = !_isOnlineMode.value
        _isOnlineMode.value = next
        if (next) {
            syncPendingOfflineRecords()
        } else {
            showMessage("Mode Hors-Ligne activé : les pointages sont chiffrés localement et marqués 'À confirmer'.", isError = false)
        }
    }

    fun syncPendingOfflineRecords() {
        viewModelScope.launch {
            val count = repository.synchronizeOfflineRecords(
                companyId = _activeCompanyId.value,
                adminName = "Synchronisation Automatique Serveur"
            )
            if (count > 0) {
                showMessage("$count pointage(s) hors-ligne synchronisé(s) et confirmé(s) par le serveur sans doublon.", isError = false)
            } else {
                showMessage("Réseau en ligne rétabli. Aucun pointage hors-ligne en attente.", isError = false)
            }
        }
    }

    fun dismissBanner() {
        _userFeedbackBanner.value = null
    }

    fun clearKioskConfirmation() {
        _kioskConfirmation.value = null
    }

    fun executePunch(
        employeeId: String,
        method: BiometricModality,
        isPunchOut: Boolean,
        livenessScore: Float = 0.98f,
        candidatePin: String? = null,
        simulateOutsideGeofence: Boolean = false
    ) {
        viewModelScope.launch {
            val companyId = _activeCompanyId.value
            val currentDevices = devices.value
            val defaultDevice = currentDevices.firstOrNull()
            if (defaultDevice == null) {
                showMessage(AppError.UnauthorizedDevice.userFriendlyMessageFr, isError = true)
                return@launch
            }
            val currentSites = sites.value
            val defaultSite = currentSites.firstOrNull()
            val lat = if (simulateOutsideGeofence) (defaultSite?.latitude ?: 14.66) + 0.05 else defaultSite?.latitude
            val lon = if (simulateOutsideGeofence) (defaultSite?.longitude ?: -17.43) + 0.05 else defaultSite?.longitude

            val result = repository.performAttendancePunch(
                companyId = companyId,
                employeeId = employeeId,
                deviceId = defaultDevice.id,
                method = method,
                isPunchOut = isPunchOut,
                isOnline = _isOnlineMode.value,
                livenessScore = livenessScore,
                candidatePin = candidatePin,
                currentLatitude = lat,
                currentLongitude = lon
            )

            result.onSuccess { record ->
                val statusEnum = runCatching { AttendanceStatus.valueOf(record.status) }.getOrDefault(AttendanceStatus.ON_TIME)
                val methodEnum = runCatching { BiometricModality.valueOf(record.methodUsed) }.getOrDefault(method)
                val syncEnum = runCatching { SyncState.valueOf(record.syncState) }.getOrDefault(SyncState.CONFIRMED_SERVER)
                _kioskConfirmation.value = KioskConfirmationBanner(
                    employeeFirstName = record.employeeFirstName,
                    employeeLastName = record.employeeLastName,
                    employeeMatricule = record.employeeMatricule,
                    dateIso = record.dateIso,
                    serverTimeHms = if (isPunchOut) (record.departureServerTimeHms ?: record.arrivalServerTimeHms) else record.arrivalServerTimeHms,
                    statusLabel = statusEnum.labelFr,
                    methodLabel = methodEnum.labelFr,
                    syncStateLabel = syncEnum.labelFr,
                    isDeparture = isPunchOut
                )
                showMessage(
                    "Pointage validé pour ${record.employeeFirstName} ${record.employeeLastName} à ${if (isPunchOut) record.departureServerTimeHms else record.arrivalServerTimeHms}",
                    isError = false
                )
            }.onFailure { err ->
                showMessage(
                    err.message ?: AppError.BiometricVerificationFailed.userFriendlyMessageFr,
                    isError = true
                )
            }
        }
    }

    fun enrollEmployeeBiometric(
        employeeId: String,
        modality: BiometricModality,
        explicitConsent: Boolean,
        livenessPassed: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.enrollEmployeeBiometrics(
                companyId = _activeCompanyId.value,
                employeeId = employeeId,
                modality = modality,
                explicitConsentChecked = explicitConsent,
                adminUserId = "ADMIN-ACTIVE",
                adminName = "Responsable RH (${_activeRole.value.labelFr})",
                livenessVerified = livenessPassed
            )
            result.onSuccess {
                showMessage("Enrôlement ${modality.labelFr} chiffré (AES-256-GCM) enregistré avec consentement CDP/RGPD.", isError = false)
            }.onFailure { err ->
                showMessage(err.message ?: "Échec de l'enrôlement biométrique.", isError = true)
            }
        }
    }

    fun revokeAndPurgeBiometrics(employeeId: String, reason: String) {
        viewModelScope.launch {
            val result = repository.purgeEmployeeBiometrics(
                companyId = _activeCompanyId.value,
                employeeId = employeeId,
                adminUserId = "ADMIN-ACTIVE",
                adminName = "DPO / Administrateur",
                reason = reason
            )
            result.onSuccess {
                showMessage("Données biométriques intégralement supprimées (Droit à l'effacement CDP/RGPD).", isError = false)
            }.onFailure { err ->
                showMessage(err.message ?: "Erreur lors de la suppression.", isError = true)
            }
        }
    }

    fun addNewEmployee(
        firstName: String,
        lastName: String,
        matricule: String,
        jobTitle: String,
        email: String,
        phone: String,
        rawPin: String
    ) {
        if (firstName.isBlank() || lastName.isBlank() || matricule.isBlank() || rawPin.length < 4) {
            showMessage("Veuillez renseigner le prénom, le nom, le matricule et un code PIN d'au moins 4 chiffres.", isError = true)
            return
        }
        viewModelScope.launch {
            val siteId = sites.value.firstOrNull()?.id ?: return@launch
            val deptId = departments.value.firstOrNull()?.id ?: return@launch
            val schedId = schedules.value.firstOrNull()?.id ?: return@launch
            val res = repository.createEmployeeWithPin(
                companyId = _activeCompanyId.value,
                siteId = siteId,
                departmentId = deptId,
                scheduleId = schedId,
                firstName = firstName,
                lastName = lastName,
                matricule = matricule,
                jobTitle = jobTitle.ifBlank { "Collaborateur" },
                email = email.ifBlank { "${firstName.lowercase()}.${lastName.lowercase()}@entreprise.sn" },
                phone = phone.ifBlank { "+221 77 000 00 00" },
                rawFallbackPin = rawPin,
                adminUserId = "ADMIN-ACTIVE",
                adminName = "Admin RH"
            )
            res.onSuccess { emp ->
                showMessage("Employé ${emp.firstName} ${emp.lastName} (${emp.matricule}) créé avec succès.", isError = false)
            }.onFailure {
                showMessage("Impossible de créer l'employé (matricule déjà utilisé).", isError = true)
            }
        }
    }

    fun correctAttendanceManually(
        record: AttendanceRecordEntity,
        newArrivalHms: String,
        newDepartureHms: String?,
        newStatus: AttendanceStatus,
        justification: String
    ) {
        viewModelScope.launch {
            val res = repository.manuallyCorrectAttendance(
                companyId = _activeCompanyId.value,
                record = record,
                newArrivalHms = newArrivalHms,
                newDepartureHms = newDepartureHms,
                newStatus = newStatus,
                justification = justification,
                adminUserId = "ADMIN-ACTIVE",
                adminName = "Administrateur (${_activeRole.value.labelFr})"
            )
            res.onSuccess {
                showMessage("Correction enregistrée et journalisée dans les Audit Logs inaltérables.", isError = false)
            }.onFailure { err ->
                showMessage(err.message ?: "Correction refusée.", isError = true)
            }
        }
    }

    fun testCrossTenantIsolationAttack(targetForeignEmployeeId: String) {
        viewModelScope.launch {
            val res = repository.verifyTenantAccess(
                employeeId = targetForeignEmployeeId,
                activeCompanyId = _activeCompanyId.value
            )
            res.onFailure { err ->
                repository.appendImmutableAuditLog(
                    companyId = _activeCompanyId.value,
                    actorUserId = "SECURITY-MONITOR",
                    actorName = "Contrôleur Multi-Tenant",
                    actorRole = _activeRole.value.name,
                    actionType = "CROSS_TENANT_ACCESS_BLOCKED",
                    targetEntity = "Employee",
                    targetId = targetForeignEmployeeId,
                    justification = "Blocage d'accès inter-entreprises sur l'identifiant $targetForeignEmployeeId depuis ${_activeCompanyId.value}"
                )
                showMessage("Test d'isolation réussi : ${err.message}", isError = true)
            }
        }
    }

    fun generateCsvReport() {
        val records = attendanceRecords.value
        val companyName = companies.value.find { it.id == _activeCompanyId.value }?.name ?: _activeCompanyId.value
        val header = "Entreprise;Matricule;Nom;Prenom;Date;Heure_Arrivee_Serveur;Heure_Depart_Serveur;Duree_Min;Retard_Min;Statut;Methode_Biometrique;Borne;Site;Etat_Sync;Signature_HMAC"
        val rows = records.joinToString("\n") { r ->
            "$companyName;${r.employeeMatricule};${r.employeeLastName};${r.employeeFirstName};${r.dateIso};${r.arrivalServerTimeHms};${r.departureServerTimeHms ?: "-"};${r.workedMinutes};${r.lateMinutes};${r.status};${r.methodUsed};${r.deviceName};${r.siteName};${r.syncState};${r.integrityHmacSignature.take(16)}..."
        }
        _exportedReportContent.value = "$header\n$rows"
        showMessage("Rapport CSV/Excel généré (${records.size} pointages exportés).", isError = false)
    }

    fun clearExportedReport() {
        _exportedReportContent.value = null
    }

    private fun showMessage(msg: String, isError: Boolean) {
        _isErrorBanner.value = isError
        _userFeedbackBanner.value = msg
    }

    class Factory(
        private val dao: BioPointageDao,
        private val repository: BioPointageRepository,
        private val cryptoEngine: CryptoEngine,
        private val timeAuthority: ServerTimeAuthority,
        private val biometricProvider: BiometricProvider
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BioPointageViewModel(dao, repository, cryptoEngine, timeAuthority, biometricProvider) as T
        }
    }
}
