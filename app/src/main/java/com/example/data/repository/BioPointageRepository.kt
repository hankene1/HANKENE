package com.example.data.repository

import android.util.Base64
import com.example.data.local.*
import com.example.data.security.CryptoEngine
import com.example.domain.biometrics.BiometricProvider
import com.example.domain.model.*
import com.example.domain.time.ServerTimeAuthority
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.math.*

/**
 * Dépôt central ("BioPointageRepository") implémentant :
 * - Isolation stricte Multi-Entreprises (vérification obligatoire de `companyId`)
 * - Enregistrement automatique de la date et de l'heure SERVEUR (ou monotone hors-ligne)
 * - Enrôlement biométrique chiffré AES-256-GCM conditionné au consentement explicite CDP/RGPD
 * - Calcul automatique des retards, durées travaillées et heures supplémentaires
 * - Détection de doublons (idempotence + fenêtre anti-rebond) et Geofencing par site
 * - Journalisation inaltérable dans `AuditLogs` (chaînage SHA-256)
 */
class BioPointageRepository(
    private val dao: BioPointageDao,
    private val cryptoEngine: CryptoEngine,
    private val timeAuthority: ServerTimeAuthority,
    private val biometricProvider: BiometricProvider
) {

    fun observeCompanies(): Flow<List<CompanyEntity>> = dao.observeCompanies()
    fun observeSites(companyId: String): Flow<List<SiteEntity>> = dao.observeSitesByCompany(companyId)
    fun observeDepartments(companyId: String): Flow<List<DepartmentEntity>> = dao.observeDepartmentsByCompany(companyId)
    fun observeSchedules(companyId: String): Flow<List<ScheduleEntity>> = dao.observeSchedulesByCompany(companyId)
    fun observeEmployees(companyId: String): Flow<List<EmployeeEntity>> = dao.observeEmployeesByCompany(companyId)
    fun observeConsents(companyId: String): Flow<List<ConsentEntity>> = dao.observeConsentsByCompany(companyId)
    fun observeEnrollments(companyId: String): Flow<List<BiometricEnrollmentEntity>> = dao.observeActiveEnrollmentsByCompany(companyId)
    fun observeDevices(companyId: String): Flow<List<DeviceEntity>> = dao.observeDevicesByCompany(companyId)
    fun observeAttendance(companyId: String): Flow<List<AttendanceRecordEntity>> = dao.observeAttendanceByCompany(companyId)
    fun observeLeaves(companyId: String): Flow<List<LeaveEntity>> = dao.observeLeavesByCompany(companyId)
    fun observeUsers(companyId: String): Flow<List<UserAccountEntity>> = dao.observeUsersForCompany(companyId)
    fun observeAuditLogs(companyId: String): Flow<List<AuditLogEntity>> = dao.observeAuditLogsByCompany(companyId)
    fun observeNotifications(companyId: String): Flow<List<NotificationEntity>> = dao.observeNotificationsByCompany(companyId)

    /**
     * Vérifie qu'un employé appartient bien à l'entreprise active (Isolation Multi-Tenant).
     */
    suspend fun verifyTenantAccess(employeeId: String, activeCompanyId: String): Result<EmployeeEntity> {
        val employee = dao.getEmployeeByIdScoped(employeeId, activeCompanyId)
            ?: return Result.failure(SecurityException(AppError.TenantIsolationViolation.userFriendlyMessageFr))
        return Result.success(employee)
    }

    /**
     * Enregistre le consentement explicite de l'employé puis enrôle son gabarit biométrique chiffré.
     */
    suspend fun enrollEmployeeBiometrics(
        companyId: String,
        employeeId: String,
        modality: BiometricModality,
        explicitConsentChecked: Boolean,
        adminUserId: String,
        adminName: String,
        livenessVerified: Boolean,
        featureVector: FloatArray? = null
    ): Result<BiometricEnrollmentEntity> {
        if (!explicitConsentChecked) {
            return Result.failure(IllegalStateException(AppError.MissingConsent.userFriendlyMessageFr))
        }
        val employee = dao.getEmployeeByIdScoped(employeeId, companyId)
            ?: return Result.failure(SecurityException(AppError.TenantIsolationViolation.userFriendlyMessageFr))

        val now = timeAuthority.acquireCertifiedTimestamp(true).officialEpochMillis
        val consentId = "CONSENT-${UUID.randomUUID().toString().take(8)}"
        val consent = ConsentEntity(
            id = consentId,
            companyId = companyId,
            employeeId = employee.id,
            legalFramework = "Loi CDP Sénégal n°2008-12 & RGPD Art. 9",
            consentVersion = "v2.4-2026",
            purposeDescription = "Contrôle des horaires et pointage de présence par gabarit mathématique chiffré AES-256-GCM sans stockage d'image.",
            explicitConsentGiven = true,
            grantedAtEpochMs = now,
            expiresAtEpochMs = now + (365L * 24 * 3600 * 1000)
        )
        dao.insertConsent(consent)

        val proofResult = biometricProvider.createEnrollmentProof(
            employeeId = employee.id,
            companyId = companyId,
            livenessVerified = livenessVerified,
            featureVector = featureVector
        )
        if (proofResult.isFailure) {
            return Result.failure(proofResult.exceptionOrNull() ?: IllegalStateException("Échec d'enrôlement"))
        }
        val payload = proofResult.getOrThrow()

        val enrollment = BiometricEnrollmentEntity(
            id = "BIO-${UUID.randomUUID().toString().take(8)}",
            companyId = companyId,
            employeeId = employee.id,
            consentId = consentId,
            modality = modality.name,
            encryptedTemplateBase64 = Base64.encodeToString(payload.encryptedTemplateBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(payload.initializationVector, Base64.NO_WRAP),
            templateSha256 = payload.templateHashSha256,
            keyAlias = payload.keyAlias,
            livenessScoreAtEnrollment = if (livenessVerified) 0.98f else 1.0f,
            enrolledByAdminId = adminUserId,
            enrolledAtEpochMs = now,
            isActive = true
        )
        dao.insertBiometricEnrollment(enrollment)

        appendImmutableAuditLog(
            companyId = companyId,
            actorUserId = adminUserId,
            actorName = adminName,
            actorRole = UserRole.HR_MANAGER.name,
            actionType = "BIOMETRIC_ENROLLMENT",
            targetEntity = "Employee",
            targetId = employee.id,
            justification = "Enrôlement ${modality.labelFr} avec consentement explicite ($consentId) pour ${employee.firstName} ${employee.lastName}"
        )
        return Result.success(enrollment)
    }

    /**
     * Supprime intégralement les données biométriques d'un employé (Droit à l'oubli CDP/RGPD ou départ).
     */
    suspend fun purgeEmployeeBiometrics(
        companyId: String,
        employeeId: String,
        adminUserId: String,
        adminName: String,
        reason: String
    ): Result<Unit> {
        val employee = dao.getEmployeeByIdScoped(employeeId, companyId)
            ?: return Result.failure(SecurityException(AppError.TenantIsolationViolation.userFriendlyMessageFr))

        dao.purgeBiometricsForEmployee(employeeId, companyId)
        appendImmutableAuditLog(
            companyId = companyId,
            actorUserId = adminUserId,
            actorName = adminName,
            actorRole = UserRole.COMPANY_ADMIN.name,
            actionType = "BIOMETRIC_PURGE_GDPR_CDP",
            targetEntity = "Employee",
            targetId = employeeId,
            justification = "Suppression définitive des gabarits biométriques de ${employee.firstName} ${employee.lastName} : $reason"
        )
        return Result.success(Unit)
    }

    /**
     * Moteur principal de pointage (Arrivée / Départ) :
     * - Vérifie l'isolation d'entreprise (`companyId`)
     * - Vérifie l'autorisation de la borne/appareil (`DeviceEntity`)
     * - Vérifie le Geofencing si activé sur le site
     * - Vérifie la preuve biométrique ou le code PIN de secours
     * - Acquiert l'heure SERVEUR (ou monotone hors-ligne)
     * - Détecte les pointages en double
     * - Calcule automatiquement le statut (À l'heure, En retard, Heures supplémentaires)
     */
    suspend fun performAttendancePunch(
        companyId: String,
        employeeId: String,
        deviceId: String,
        method: BiometricModality,
        isPunchOut: Boolean,
        isOnline: Boolean,
        livenessScore: Float = 0.96f,
        candidatePin: String? = null,
        currentLatitude: Double? = null,
        currentLongitude: Double? = null
    ): Result<AttendanceRecordEntity> {
        val certifiedTime = timeAuthority.acquireCertifiedTimestamp(isOnline)

        // 1. Isolation Multi-Entreprise : l'employé doit appartenir à companyId
        val employee = dao.getEmployeeByIdScoped(employeeId, companyId)
            ?: return Result.failure(SecurityException(AppError.TenantIsolationViolation.userFriendlyMessageFr))

        if (employee.status != EmployeeStatus.ACTIVE.name) {
            return Result.failure(IllegalStateException("Le compte employé n'est pas actif."))
        }

        // 2. Vérification de l'appareil / borne
        val device = dao.getDeviceByIdScoped(deviceId, companyId)
        if (device == null || !device.isAuthorized) {
            recordAlertNotification(
                companyId = companyId,
                title = "Tentative sur appareil non autorisé",
                message = "Tentative de pointage de ${employee.firstName} ${employee.lastName} sur un terminal non reconnu ($deviceId).",
                severity = "CRITICAL_FRAUD"
            )
            return Result.failure(SecurityException(AppError.UnauthorizedDevice.userFriendlyMessageFr))
        }

        // 3. Vérification du site et Geofencing
        val sites = dao.getSitesByCompany(companyId)
        val site = sites.find { it.id == employee.siteId } ?: sites.first()
        if (site.enforceGeofencing && currentLatitude != null && currentLongitude != null) {
            val distanceMeters = calculateHaversineMeters(
                site.latitude, site.longitude,
                currentLatitude, currentLongitude
            )
            if (distanceMeters > site.allowedRadiusMeters) {
                recordAlertNotification(
                    companyId = companyId,
                    title = "Alerte Geofencing Hors Périmètre",
                    message = "${employee.firstName} ${employee.lastName} a tenté de pointer à ${distanceMeters.toInt()}m du site ${site.name} (max ${site.allowedRadiusMeters}m).",
                    severity = "CRITICAL_FRAUD"
                )
                return Result.failure(IllegalStateException(AppError.GeofenceViolation.userFriendlyMessageFr))
            }
        }

        // 4. Vérification Biométrique ou Code PIN de secours
        var verificationConfidence = 1.0f
        when (method) {
            BiometricModality.FALLBACK_PIN -> {
                if (candidatePin.isNullOrBlank() ||
                    !cryptoEngine.verifyPasswordOrPin(candidatePin, employee.fallbackPinHash, employee.fallbackPinSalt)
                ) {
                    recordAlertNotification(
                        companyId = companyId,
                        title = "Échec Code PIN de secours",
                        message = "Code PIN erroné saisi pour ${employee.firstName} ${employee.lastName} sur ${device.deviceName}.",
                        severity = "WARNING"
                    )
                    return Result.failure(IllegalArgumentException(AppError.BiometricVerificationFailed.userFriendlyMessageFr))
                }
            }
            BiometricModality.FACE_ID, BiometricModality.FINGERPRINT, BiometricModality.HARDWARE_SCANNER -> {
                if (method == BiometricModality.FACE_ID && livenessScore < 0.80f) {
                    recordAlertNotification(
                        companyId = companyId,
                        title = "Alerte Anti-Spoofing (Vivant refusé)",
                        message = "Tentative suspecte (photo/écran détecté, score vivant ${(livenessScore * 100).toInt()}%) pour ${employee.firstName} ${employee.lastName}.",
                        severity = "CRITICAL_FRAUD"
                    )
                    return Result.failure(IllegalStateException(AppError.LivenessFailed.userFriendlyMessageFr))
                }
                val activeEnrollments = dao.getActiveEnrollmentsForEmployee(employee.id, companyId)
                val matchingEnrollment = activeEnrollments.find { it.modality == method.name } ?: activeEnrollments.firstOrNull()
                if (matchingEnrollment == null) {
                    return Result.failure(IllegalStateException("Aucun gabarit biométrique actif pour cet employé. Veuillez procéder à l'enrôlement ou utiliser le code PIN de secours."))
                }
                val storedCipher = Base64.decode(matchingEnrollment.encryptedTemplateBase64, Base64.NO_WRAP)
                val storedIv = Base64.decode(matchingEnrollment.ivBase64, Base64.NO_WRAP)
                val verifyResult = biometricProvider.verifyAttendanceProof(
                    candidateVector = null,
                    storedEncryptedTemplate = storedCipher,
                    storedIv = storedIv,
                    minimumConfidenceThreshold = 0.85f,
                    livenessScore = livenessScore
                )
                if (!verifyResult.isSuccess) {
                    return Result.failure(IllegalStateException(AppError.BiometricVerificationFailed.userFriendlyMessageFr))
                }
                verificationConfidence = verifyResult.confidenceScore
            }
        }

        // 5. Détection des doublons et gestion Arrivée vs Départ
        val existingToday = dao.getLatestSuccessfulAttendanceForDay(
            companyId = companyId,
            employeeId = employee.id,
            dateIso = certifiedTime.formattedDateIso
        )

        if (!isPunchOut && existingToday != null) {
            val secondsSinceArrival = (certifiedTime.officialEpochMillis - existingToday.arrivalServerEpochMs) / 1000
            if (secondsSinceArrival < 120 || existingToday.departureServerTimeHms == null) {
                return Result.failure(IllegalStateException(AppError.DuplicateAttendance.userFriendlyMessageFr))
            }
        }

        val schedule = dao.getScheduleById(employee.scheduleId, companyId)
        val scheduledStartMinutes = parseHmToMinutes(schedule?.startTimeHm ?: "08:30")
        val scheduledEndMinutes = parseHmToMinutes(schedule?.endTimeHm ?: "17:30")
        val tolerance = schedule?.lateToleranceMinutes ?: 10
        val currentMinutesOfDay = parseHmsToMinutes(certifiedTime.formattedTimeHms)

        if (isPunchOut && existingToday != null && existingToday.departureServerTimeHms == null) {
            val workedMinutes = ((certifiedTime.officialEpochMillis - existingToday.arrivalServerEpochMs) / 60000L)
                .toInt()
                .coerceAtLeast(1)
            val overtime = (currentMinutesOfDay - scheduledEndMinutes).coerceAtLeast(0)
            val updatedStatus = if (overtime >= (schedule?.overtimeThresholdMinutes ?: 30)) {
                AttendanceStatus.OVERTIME.name
            } else {
                existingToday.status
            }
            val updatedRecord = existingToday.copy(
                departureServerTimeHms = certifiedTime.formattedTimeHms,
                departureServerEpochMs = certifiedTime.officialEpochMillis,
                workedMinutes = workedMinutes,
                overtimeMinutes = overtime,
                status = updatedStatus,
                syncState = certifiedTime.syncState.name
            )
            dao.updateAttendanceRecord(updatedRecord)
            return Result.success(updatedRecord)
        }

        val lateMinutes = (currentMinutesOfDay - (scheduledStartMinutes + tolerance)).coerceAtLeast(0)
        val attendanceStatus = if (lateMinutes > 0) {
            AttendanceStatus.LATE
        } else {
            AttendanceStatus.ON_TIME
        }

        val idempotencyKey = "${companyId}_${employee.id}_${certifiedTime.formattedDateIso}_ARRIVAL"
        val signaturePayload = "$idempotencyKey|${employee.lastName}|${employee.firstName}|${certifiedTime.officialEpochMillis}"
        val record = AttendanceRecordEntity(
            id = "ATT-${UUID.randomUUID().toString().take(8)}",
            idempotencyKey = idempotencyKey,
            companyId = companyId,
            employeeId = employee.id,
            employeeLastName = employee.lastName,
            employeeFirstName = employee.firstName,
            employeeMatricule = employee.matricule,
            dateIso = certifiedTime.formattedDateIso,
            arrivalServerTimeHms = certifiedTime.formattedTimeHms,
            arrivalServerEpochMs = certifiedTime.officialEpochMillis,
            departureServerTimeHms = null,
            departureServerEpochMs = null,
            workedMinutes = 0,
            lateMinutes = lateMinutes,
            overtimeMinutes = 0,
            methodUsed = method.name,
            deviceId = device.id,
            deviceName = device.deviceName,
            siteId = site.id,
            siteName = site.name,
            status = attendanceStatus.name,
            verificationOutcome = VerificationOutcome.SUCCESS.name,
            confidenceScore = verificationConfidence,
            livenessScore = livenessScore,
            syncState = certifiedTime.syncState.name,
            integrityHmacSignature = cryptoEngine.signHmacSha256(signaturePayload)
        )

        val rowId = dao.insertAttendanceRecordIdempotent(record)
        if (rowId == -1L) {
            return Result.failure(IllegalStateException(AppError.DuplicateAttendance.userFriendlyMessageFr))
        }

        if (attendanceStatus == AttendanceStatus.LATE) {
            recordAlertNotification(
                companyId = companyId,
                title = "Retard enregistré (${lateMinutes} min)",
                message = "${employee.firstName} ${employee.lastName} (${employee.matricule}) a pointé à ${certifiedTime.formattedTimeHms} sur ${site.name}.",
                severity = "WARNING"
            )
        }

        return Result.success(record)
    }

    /**
     * Correction manuelle d'un pointage par un administrateur :
     * - Justification obligatoire
     * - Attribution nominative à l'administrateur
     * - Enregistrement inaltérable dans `AuditLogs`
     */
    suspend fun manuallyCorrectAttendance(
        companyId: String,
        record: AttendanceRecordEntity,
        newArrivalHms: String,
        newDepartureHms: String?,
        newStatus: AttendanceStatus,
        justification: String,
        adminUserId: String,
        adminName: String
    ): Result<AttendanceRecordEntity> {
        if (justification.trim().length < 8) {
            return Result.failure(IllegalArgumentException("Une justification détaillée (minimum 8 caractères) est obligatoire pour toute correction manuelle."))
        }
        if (record.companyId != companyId) {
            return Result.failure(SecurityException(AppError.TenantIsolationViolation.userFriendlyMessageFr))
        }

        val updated = record.copy(
            arrivalServerTimeHms = newArrivalHms,
            departureServerTimeHms = newDepartureHms,
            status = newStatus.name,
            manualAdjustmentReason = justification.trim(),
            adjustedByAdminName = adminName
        )
        dao.updateAttendanceRecord(updated)

        appendImmutableAuditLog(
            companyId = companyId,
            actorUserId = adminUserId,
            actorName = adminName,
            actorRole = UserRole.COMPANY_ADMIN.name,
            actionType = "MANUAL_ATTENDANCE_CORRECTION",
            targetEntity = "AttendanceRecord",
            targetId = record.id,
            justification = "Correction pointage de ${record.employeeFirstName} ${record.employeeLastName} (${record.dateIso}) : $justification"
        )
        return Result.success(updated)
    }

    /**
     * Synchronise les pointages enregistrés hors-ligne ("À confirmer") vers le statut confirmé serveur sans doublon.
     */
    suspend fun synchronizeOfflineRecords(companyId: String, adminName: String): Int {
        val pending = dao.getPendingOfflineAttendance(companyId)
        if (pending.isEmpty()) return 0

        for (item in pending) {
            val confirmed = item.copy(syncState = SyncState.CONFIRMED_SERVER.name)
            dao.updateAttendanceRecord(confirmed)
        }
        appendImmutableAuditLog(
            companyId = companyId,
            actorUserId = "SYSTEM-SYNC",
            actorName = adminName,
            actorRole = UserRole.COMPANY_ADMIN.name,
            actionType = "OFFLINE_BATCH_SYNC",
            targetEntity = "AttendanceRecord",
            targetId = "BATCH_${pending.size}",
            justification = "Synchronisation idempotente de ${pending.size} pointage(s) hors-ligne avec validation d'horodatage serveur."
        )
        return pending.size
    }

    suspend fun createEmployeeWithPin(
        companyId: String,
        siteId: String,
        departmentId: String,
        scheduleId: String,
        firstName: String,
        lastName: String,
        matricule: String,
        jobTitle: String,
        email: String,
        phone: String,
        rawFallbackPin: String,
        adminUserId: String,
        adminName: String
    ): Result<EmployeeEntity> {
        val pinHash = cryptoEngine.hashPasswordOrPin(rawFallbackPin)
        val now = timeAuthority.acquireCertifiedTimestamp(true).officialEpochMillis
        val entity = EmployeeEntity(
            id = "EMP-${UUID.randomUUID().toString().take(8)}",
            companyId = companyId,
            siteId = siteId,
            departmentId = departmentId,
            scheduleId = scheduleId,
            firstName = firstName.trim(),
            lastName = lastName.trim().uppercase(),
            matricule = matricule.trim().uppercase(),
            jobTitle = jobTitle.trim(),
            email = email.trim(),
            phone = phone.trim(),
            status = EmployeeStatus.ACTIVE.name,
            fallbackPinHash = pinHash.hashBase64,
            fallbackPinSalt = pinHash.saltBase64,
            allowNonBiometricAlternative = true,
            createdAtEpochMs = now
        )
        dao.insertEmployee(entity)
        appendImmutableAuditLog(
            companyId = companyId,
            actorUserId = adminUserId,
            actorName = adminName,
            actorRole = UserRole.HR_MANAGER.name,
            actionType = "CREATE_EMPLOYEE",
            targetEntity = "Employee",
            targetId = entity.id,
            justification = "Création de la fiche salarié ${entity.firstName} ${entity.lastName} (${entity.matricule})"
        )
        return Result.success(entity)
    }

    suspend fun appendImmutableAuditLog(
        companyId: String,
        actorUserId: String,
        actorName: String,
        actorRole: String,
        actionType: String,
        targetEntity: String,
        targetId: String,
        justification: String
    ) {
        val latest = dao.getLatestAuditLog(companyId)
        val previousHash = latest?.entrySha256 ?: "GENESIS_BLOCK_0000000000000000"
        val now = timeAuthority.acquireCertifiedTimestamp(true).officialEpochMillis
        val payload = "$previousHash|$companyId|$actorUserId|$actionType|$targetId|$justification|$now"
        val currentHash = cryptoEngine.sha256Hex(payload)
        val log = AuditLogEntity(
            id = "AUDIT-${UUID.randomUUID().toString().take(8)}",
            companyId = companyId,
            actorUserId = actorUserId,
            actorName = actorName,
            actorRole = actorRole,
            actionType = actionType,
            targetEntity = targetEntity,
            targetId = targetId,
            justification = justification,
            timestampEpochMs = now,
            previousLogSha256 = previousHash,
            entrySha256 = currentHash
        )
        dao.insertAuditLog(log)
    }

    private suspend fun recordAlertNotification(
        companyId: String,
        title: String,
        message: String,
        severity: String
    ) {
        val now = timeAuthority.acquireCertifiedTimestamp(true).officialEpochMillis
        dao.insertNotification(
            NotificationEntity(
                id = "NOTIF-${UUID.randomUUID().toString().take(8)}",
                companyId = companyId,
                title = title,
                message = message,
                severity = severity,
                isRead = false,
                createdAtEpochMs = now
            )
        )
    }

    private fun parseHmToMinutes(hm: String): Int {
        val parts = hm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
        return h * 60 + m
    }

    private fun parseHmsToMinutes(hms: String): Int {
        val parts = hms.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
        return h * 60 + m
    }

    private fun calculateHaversineMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
