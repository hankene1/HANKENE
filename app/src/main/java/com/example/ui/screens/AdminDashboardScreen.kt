package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BiometricModality
import com.example.domain.model.SyncState
import com.example.ui.AdminSection

@Composable
fun AdminDashboardScreen(
    activeSection: AdminSection,
    onSelectSection: (AdminSection) -> Unit,
    employees: List<EmployeeEntity>,
    enrollments: List<BiometricEnrollmentEntity>,
    consents: List<ConsentEntity>,
    attendanceRecords: List<AttendanceRecordEntity>,
    schedules: List<ScheduleEntity>,
    leaves: List<LeaveEntity>,
    sites: List<SiteEntity>,
    devices: List<DeviceEntity>,
    auditLogs: List<AuditLogEntity>,
    notifications: List<NotificationEntity>,
    exportedReportContent: String?,
    onGenerateCsvReport: () -> Unit,
    onClearExportedReport: () -> Unit,
    onAddEmployee: (firstName: String, lastName: String, matricule: String, jobTitle: String, email: String, phone: String, rawPin: String) -> Unit,
    onEnrollBiometric: (employeeId: String, modality: BiometricModality, explicitConsent: Boolean, livenessPassed: Boolean) -> Unit,
    onPurgeBiometrics: (employeeId: String, reason: String) -> Unit,
    onManualCorrection: (record: AttendanceRecordEntity, newArrival: String, newDeparture: String?, newStatus: AttendanceStatus, justification: String) -> Unit,
    onSyncOfflineRecords: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Barre de navigation horizontale des 9 sous-modules administrateur
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminSection.entries.forEach { section ->
                FilterChip(
                    selected = activeSection == section,
                    onClick = { onSelectSection(section) },
                    label = { Text(section.labelFr) },
                    modifier = Modifier.testTag("admin_tab_${section.name.lowercase()}")
                )
            }
        }

        when (activeSection) {
            AdminSection.DASHBOARD -> DashboardOverviewPane(
                employees = employees,
                attendanceRecords = attendanceRecords,
                leaves = leaves,
                notifications = notifications,
                onSyncOfflineRecords = onSyncOfflineRecords
            )
            AdminSection.EMPLOYEES -> EmployeesAndBiometricsPane(
                employees = employees,
                enrollments = enrollments,
                onAddEmployee = onAddEmployee,
                onEnrollBiometric = onEnrollBiometric,
                onPurgeBiometrics = onPurgeBiometrics
            )
            AdminSection.ATTENDANCE -> AttendanceAndCorrectionPane(
                attendanceRecords = attendanceRecords,
                onManualCorrection = onManualCorrection
            )
            AdminSection.SCHEDULES -> SchedulesPane(schedules = schedules)
            AdminSection.LEAVES -> LeavesPane(leaves = leaves)
            AdminSection.SITES_DEVICES -> SitesAndDevicesPane(sites = sites, devices = devices)
            AdminSection.REPORTS_EXPORTS -> ReportsAndExportsPane(
                attendanceRecords = attendanceRecords,
                exportedReportContent = exportedReportContent,
                onGenerateCsvReport = onGenerateCsvReport,
                onClearExportedReport = onClearExportedReport
            )
            AdminSection.CONSENTS_CDP -> ConsentsCompliancePane(
                employees = employees,
                consents = consents,
                enrollments = enrollments,
                onPurgeBiometrics = onPurgeBiometrics
            )
            AdminSection.AUDIT_LOGS -> AuditLogsPane(auditLogs = auditLogs)
        }
    }
}

@Composable
private fun DashboardOverviewPane(
    employees: List<EmployeeEntity>,
    attendanceRecords: List<AttendanceRecordEntity>,
    leaves: List<LeaveEntity>,
    notifications: List<NotificationEntity>,
    onSyncOfflineRecords: () -> Unit
) {
    val presentCount = attendanceRecords.map { it.employeeId }.distinct().size
    val lateCount = attendanceRecords.count { it.status == AttendanceStatus.LATE.name }
    val offlinePendingCount = attendanceRecords.count { it.syncState == SyncState.OFFLINE_TO_CONFIRM.name }
    val onLeaveCount = leaves.size
    val absentCount = (employees.size - presentCount - onLeaveCount).coerceAtLeast(0)
    val presenceRate = if (employees.isNotEmpty()) (presentCount * 100) / employees.size else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard("Présents", "$presentCount / ${employees.size}", "Taux : $presenceRate%", Modifier.weight(1f))
                KpiCard("En Retard", "$lateCount", "Tolérance appliquée", Modifier.weight(1f))
                KpiCard("Absents / Congés", "$absentCount / $onLeaveCount", "Aujourd'hui", Modifier.weight(1f))
            }
        }

        if (offlinePendingCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$offlinePendingCount pointage(s) hors-ligne 'À confirmer'",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Stockés localement avec chiffrement et clé d'idempotence anti-doublon.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(onClick = onSyncOfflineRecords) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Synchroniser")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Pointages du Jour en Temps Réel (Horodatage Serveur)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(attendanceRecords, key = { it.id }) { rec ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${rec.employeeFirstName} ${rec.employeeLastName} (${rec.employeeMatricule})",
                            fontWeight = FontWeight.Bold
                        )
                        Badge(
                            containerColor = if (rec.status == AttendanceStatus.LATE.name) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        ) {
                            Text(
                                text = rec.status,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Date : ${rec.dateIso} • Arrivée : ${rec.arrivalServerTimeHms} • Départ : ${rec.departureServerTimeHms ?: "En poste"}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Méthode : ${rec.methodUsed} (Confiance ${(rec.confidenceScore * 100).toInt()}%) • Borne : ${rec.deviceName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "État : ${rec.syncState}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (notifications.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Alertes Anti-Fraude & Notifications Récentes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(notifications, key = { it.id }) { notif ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (notif.severity == "CRITICAL_FRAUD") {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = notif.title, fontWeight = FontWeight.Bold)
                        Text(text = notif.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmployeesAndBiometricsPane(
    employees: List<EmployeeEntity>,
    enrollments: List<BiometricEnrollmentEntity>,
    onAddEmployee: (String, String, String, String, String, String, String) -> Unit,
    onEnrollBiometric: (String, BiometricModality, Boolean, Boolean) -> Unit,
    onPurgeBiometrics: (String, String) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var matricule by remember { mutableStateOf("") }
    var jobTitle by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("2580") }
    var explicitConsentChecked by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fiches Employés & Enrôlement Biométrique",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showAddForm = !showAddForm },
                    modifier = Modifier.testTag("btn_toggle_add_employee")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showAddForm) "Fermer" else "Nouvel Employé")
                }
            }
        }

        if (showAddForm) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Enregistrer un nouvel employé", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text("Prénom") },
                            modifier = Modifier.fillMaxWidth().testTag("input_emp_firstname")
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text("Nom") },
                            modifier = Modifier.fillMaxWidth().testTag("input_emp_lastname")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = matricule,
                                onValueChange = { matricule = it },
                                label = { Text("Matricule (ex: TTL-009)") },
                                modifier = Modifier.weight(1f).testTag("input_emp_matricule")
                            )
                            OutlinedTextField(
                                value = pinCode,
                                onValueChange = { pinCode = it },
                                label = { Text("PIN Secours") },
                                modifier = Modifier.weight(1f).testTag("input_emp_pin")
                            )
                        }
                        OutlinedTextField(
                            value = jobTitle,
                            onValueChange = { jobTitle = it },
                            label = { Text("Poste / Fonction") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onAddEmployee(firstName, lastName, matricule, jobTitle, "", "", pinCode)
                                firstName = ""
                                lastName = ""
                                matricule = ""
                                jobTitle = ""
                                showAddForm = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("btn_save_new_employee")
                        ) {
                            Text("Créer la Fiche Salarié")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = explicitConsentChecked,
                        onCheckedChange = { explicitConsentChecked = it },
                        modifier = Modifier.testTag("checkbox_cdp_consent")
                    )
                    Text(
                        text = "Consentement explicite de l'employé recueilli (Loi CDP Sénégal & RGPD) pour l'enrôlement biométrique chiffré AES-256-GCM.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(employees, key = { it.id }) { emp ->
            val empEnrollments = enrollments.filter { it.employeeId == emp.id }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${emp.firstName} ${emp.lastName} (${emp.matricule})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${emp.jobTitle} • ${emp.email}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Badge { Text(emp.status) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (empEnrollments.isEmpty()) {
                            "Aucun gabarit biométrique actif (Alternative Code PIN disponible)"
                        } else {
                            "Gabarits chiffrés actifs : " + empEnrollments.joinToString(", ") {
                                "${it.modality} (SHA-256: ${it.templateSha256.take(10)}...)"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onEnrollBiometric(emp.id, BiometricModality.FINGERPRINT, explicitConsentChecked, true)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enrôler Empreinte", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                onEnrollBiometric(emp.id, BiometricModality.FACE_ID, explicitConsentChecked, true)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enrôler Visage", style = MaterialTheme.typography.labelSmall)
                        }
                        if (empEnrollments.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    onPurgeBiometrics(emp.id, "Retrait du consentement / Purge RGPD-CDP")
                                }
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Purger la biométrie",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceAndCorrectionPane(
    attendanceRecords: List<AttendanceRecordEntity>,
    onManualCorrection: (AttendanceRecordEntity, String, String?, AttendanceStatus, String) -> Unit
) {
    var recordBeingEdited by remember { mutableStateOf<AttendanceRecordEntity?>(null) }
    var editedArrival by remember { mutableStateOf("08:30:00") }
    var editedDeparture by remember { mutableStateOf("17:30:00") }
    var justification by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Registre des Pointages (Inaltérables sans Audit Log)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Règle de sécurité : Aucun pointage ne peut être modifié silencieusement. Toute correction exige une justification et génère un bloc SHA-256 dans les Audit Logs.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        recordBeingEdited?.let { target ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Correction manuelle tracée : ${target.employeeFirstName} ${target.employeeLastName}",
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editedArrival,
                                onValueChange = { editedArrival = it },
                                label = { Text("Heure Arrivée") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editedDeparture,
                                onValueChange = { editedDeparture = it },
                                label = { Text("Heure Départ") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = justification,
                            onValueChange = { justification = it },
                            label = { Text("Justification obligatoire (ex: Mission externe validée RH)") },
                            modifier = Modifier.fillMaxWidth().testTag("input_correction_justification")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onManualCorrection(
                                        target,
                                        editedArrival,
                                        editedDeparture.ifBlank { null },
                                        AttendanceStatus.ON_TIME,
                                        justification
                                    )
                                    recordBeingEdited = null
                                    justification = ""
                                },
                                modifier = Modifier.testTag("btn_confirm_correction")
                            ) {
                                Text("Valider & Journaliser dans AuditLogs")
                            }
                            TextButton(onClick = { recordBeingEdited = null }) {
                                Text("Annuler")
                            }
                        }
                    }
                }
            }
        }

        items(attendanceRecords, key = { it.id }) { rec ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${rec.employeeFirstName} ${rec.employeeLastName} (${rec.employeeMatricule})",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${rec.dateIso} | Arrivée : ${rec.arrivalServerTimeHms} | Départ : ${rec.departureServerTimeHms ?: "-"}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                recordBeingEdited = rec
                                editedArrival = rec.arrivalServerTimeHms
                                editedDeparture = rec.departureServerTimeHms ?: "17:30:00"
                            }
                        ) {
                            Text("Corriger")
                        }
                    }
                    if (rec.manualAdjustmentReason != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Corrigé par ${rec.adjustedByAdminName} — Motif : ${rec.manualAdjustmentReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulesPane(schedules: List<ScheduleEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Plannings, Tolérances de Retard & Heures Supplémentaires",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(schedules, key = { it.id }) { sched ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(sched.name, fontWeight = FontWeight.Bold)
                    Text("Horaires : ${sched.startTimeHm} ➔ ${sched.endTimeHm} (Pause : ${sched.breakDurationMinutes} min)")
                    Text(
                        "Tolérance retard : ${sched.lateToleranceMinutes} min • Seuil Heures Sup : ${sched.overtimeThresholdMinutes} min • Jours : ${sched.workingDaysCsv}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LeavesPane(leaves: List<LeaveEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Gestion des Absences, Congés & Jours Fériés",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(leaves, key = { it.id }) { leave ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("${leave.employeeFullName} — ${leave.leaveType}", fontWeight = FontWeight.Bold)
                    Text("Période : ${leave.startDateIso} au ${leave.endDateIso} • Statut : ${leave.status}")
                    Text("Motif : ${leave.reason} (Validé par : ${leave.approvedBy ?: "-"})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SitesAndDevicesPane(sites: List<SiteEntity>, devices: List<DeviceEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Sites & Périmètres de Geofencing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(sites, key = { it.id }) { site ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(site.name, fontWeight = FontWeight.Bold)
                    Text(site.address, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "GPS : ${site.latitude}, ${site.longitude} • Rayon autorisé : ${site.allowedRadiusMeters}m",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Bornes Kiosques & Terminaux Enregistrés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(devices, key = { it.id }) { dev ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(dev.deviceName, fontWeight = FontWeight.Bold)
                    Text("Type : ${dev.deviceType} • Empreinte matérielle : ${dev.hardwareFingerprint}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReportsAndExportsPane(
    attendanceRecords: List<AttendanceRecordEntity>,
    exportedReportContent: String?,
    onGenerateCsvReport: () -> Unit,
    onClearExportedReport: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rapports & Exports (CSV / Excel / PDF)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Inclut : nom, prénom, matricule, date, heure serveur, durée, statut et signature HMAC.", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onGenerateCsvReport,
                    modifier = Modifier.testTag("btn_generate_csv_report")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exporter CSV")
                }
            }
        }

        if (exportedReportContent != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Aperçu de l'Export Officiel Généré", fontWeight = FontWeight.Bold)
                            TextButton(onClick = onClearExportedReport) { Text("Fermer") }
                        }
                        Text(
                            text = exportedReportContent,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        items(attendanceRecords, key = { it.id }) { r ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("${r.employeeLastName} ${r.employeeFirstName} (${r.employeeMatricule})", fontWeight = FontWeight.Bold)
                        Text("${r.dateIso} • ${r.arrivalServerTimeHms} ➔ ${r.departureServerTimeHms ?: "En cours"}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${r.workedMinutes} min (${r.status})", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ConsentsCompliancePane(
    employees: List<EmployeeEntity>,
    consents: List<ConsentEntity>,
    enrollments: List<BiometricEnrollmentEntity>,
    onPurgeBiometrics: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Registre des Consentements Biométriques (CDP Sénégal & RGPD)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Séparation stricte entre l'identité de l'employé et les gabarits mathématiques chiffrés AES-256-GCM.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(consents, key = { it.id }) { c ->
            val emp = employees.find { it.id == c.employeeId }
            val activeBioCount = enrollments.count { it.employeeId == c.employeeId }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "${emp?.firstName ?: ""} ${emp?.lastName ?: c.employeeId} — ${c.legalFramework}",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Version : ${c.consentVersion} • Gabarits actifs : $activeBioCount", style = MaterialTheme.typography.bodySmall)
                    Text(c.purposeDescription, style = MaterialTheme.typography.bodySmall)
                    if (activeBioCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onPurgeBiometrics(c.employeeId, "Droit de retrait exercé par le salarié") }
                        ) {
                            Text("Exercer le Droit à l'Effacement Biométrique")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogsPane(auditLogs: List<AuditLogEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Journal d'Audit Inaltérable (Chaînage Cryptographique SHA-256)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(auditLogs, key = { it.id }) { log ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(log.actionType, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(log.actorName, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(log.justification, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SHA-256 : ${log.entrySha256.take(24)}... (Prev: ${log.previousLogSha256.take(12)}...)",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
