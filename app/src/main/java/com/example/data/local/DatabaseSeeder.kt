package com.example.data.local

import com.example.data.repository.BioPointageRepository
import com.example.data.security.CryptoEngine
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BiometricModality
import com.example.domain.model.EmployeeStatus
import com.example.domain.model.LeaveStatus
import com.example.domain.model.LeaveType
import com.example.domain.model.SyncState
import com.example.domain.model.UserRole
import com.example.domain.model.VerificationOutcome

/**
 * Initialise les données de référence multi-entreprises au premier démarrage
 * afin que toutes les fonctionnalités (Borne Kiosque, Enrôlement, Horaires, Multi-Tenant, Rapports, Audit)
 * soient immédiatement vérifiables et testables avec de vraies opérations cryptographiques et SQLite.
 */
object DatabaseSeeder {

    suspend fun seedIfEmpty(
        dao: BioPointageDao,
        repository: BioPointageRepository,
        cryptoEngine: CryptoEngine
    ) {
        if (dao.getAllCompanies().isNotEmpty()) return

        val now = System.currentTimeMillis()

        // --- ENTREPRISE 1 : Teranga Tech & Logistique SA (Dakar, Sénégal - Conformité CDP) ---
        val comp1 = CompanyEntity(
            id = "COMP-DAKAR-01",
            name = "Teranga Tech & Logistique SA",
            registrationCode = "SN-DKR-2026-8841",
            countryCode = "SN",
            timezoneId = "Africa/Dakar",
            biometricRetentionDays = 365,
            defaultLateToleranceMinutes = 10,
            biometricConfidenceThreshold = 0.88f,
            isActive = true,
            createdAtEpochMs = now
        )
        // --- ENTREPRISE 2 : Sahel Énergie & Industries (Abidjan / Dakar - Isolation Multi-Tenant) ---
        val comp2 = CompanyEntity(
            id = "COMP-SAHEL-02",
            name = "Sahel Énergie & Industries",
            registrationCode = "SN-SLY-2026-4102",
            countryCode = "SN",
            timezoneId = "Africa/Dakar",
            biometricRetentionDays = 180,
            defaultLateToleranceMinutes = 5,
            biometricConfidenceThreshold = 0.90f,
            isActive = true,
            createdAtEpochMs = now
        )
        dao.insertCompany(comp1)
        dao.insertCompany(comp2)

        // --- SITES ---
        val siteDakarPlateau = SiteEntity(
            id = "SITE-DKR-PLATEAU",
            companyId = comp1.id,
            name = "Siège Dakar Plateau — Place de l'Indépendance",
            address = "14 Avenue Léopold Sédar Senghor, Dakar",
            latitude = 14.6692,
            longitude = -17.4381,
            allowedRadiusMeters = 250,
            enforceGeofencing = true
        )
        val siteDiamniadio = SiteEntity(
            id = "SITE-DIAMNIADIO",
            companyId = comp1.id,
            name = "Pôle Industriel Diamniadio",
            address = "Zone Économique Spéciale, Diamniadio",
            latitude = 14.7245,
            longitude = -17.1789,
            allowedRadiusMeters = 400,
            enforceGeofencing = true
        )
        val siteSahelHQ = SiteEntity(
            id = "SITE-SAHEL-HQ",
            companyId = comp2.id,
            name = "Centrale Almadies",
            address = "Route des Almadies, Dakar",
            latitude = 14.7456,
            longitude = -17.5142,
            allowedRadiusMeters = 300,
            enforceGeofencing = true
        )
        dao.insertSite(siteDakarPlateau)
        dao.insertSite(siteDiamniadio)
        dao.insertSite(siteSahelHQ)

        // --- DÉPARTEMENTS ---
        val deptIng = DepartmentEntity("DEPT-ING", comp1.id, siteDakarPlateau.id, "Ingénierie & Cybersécurité", "Mamadou Ndiaye")
        val deptOps = DepartmentEntity("DEPT-OPS", comp1.id, siteDiamniadio.id, "Opérations & Logistique", "Aïssatou Sow")
        val deptRh = DepartmentEntity("DEPT-RH", comp1.id, siteDakarPlateau.id, "Ressources Humaines", "Fatou Diop")
        val deptSahelProd = DepartmentEntity("DEPT-SAHEL-PROD", comp2.id, siteSahelHQ.id, "Maintenance Turbines", "Ousmane Kane")
        dao.insertDepartment(deptIng)
        dao.insertDepartment(deptOps)
        dao.insertDepartment(deptRh)
        dao.insertDepartment(deptSahelProd)

        // --- HORAIRES & PLANNINGS ---
        val schedStandard = ScheduleEntity(
            id = "SCHED-STD-0830",
            companyId = comp1.id,
            name = "Horaire Journée Standard (08h30 - 17h30)",
            startTimeHm = "08:30",
            endTimeHm = "17:30",
            breakDurationMinutes = 60,
            lateToleranceMinutes = 10,
            overtimeThresholdMinutes = 30,
            isNightShift = false,
            workingDaysCsv = "LUN,MAR,MER,JEU,VEN"
        )
        val schedMatin = ScheduleEntity(
            id = "SCHED-MATIN-0700",
            companyId = comp1.id,
            name = "Brigade Matin Logistique (07h00 - 15h00)",
            startTimeHm = "07:00",
            endTimeHm = "15:00",
            breakDurationMinutes = 45,
            lateToleranceMinutes = 5,
            overtimeThresholdMinutes = 20,
            isNightShift = false,
            workingDaysCsv = "LUN,MAR,MER,JEU,VEN,SAM"
        )
        val schedSahel = ScheduleEntity(
            id = "SCHED-SAHEL-0800",
            companyId = comp2.id,
            name = "Exploitation Énergie (08h00 - 17h00)",
            startTimeHm = "08:00",
            endTimeHm = "17:00",
            breakDurationMinutes = 60,
            lateToleranceMinutes = 5,
            overtimeThresholdMinutes = 30,
            isNightShift = false,
            workingDaysCsv = "LUN,MAR,MER,JEU,VEN"
        )
        dao.insertSchedule(schedStandard)
        dao.insertSchedule(schedMatin)
        dao.insertSchedule(schedSahel)

        // --- APPAREILS & BORNES KIOSQUES ---
        val kioskDakar = DeviceEntity(
            id = "DEV-KIOSK-DKR-01",
            companyId = comp1.id,
            siteId = siteDakarPlateau.id,
            deviceName = "Borne Kiosque Hall Principal (Dakar Plateau)",
            deviceType = "KIOSK_TERMINAL",
            hardwareFingerprint = "ANDROID-TEE-DKR-99481A",
            isAuthorized = true,
            lastSyncEpochMs = now
        )
        val mobileAppDakar = DeviceEntity(
            id = "DEV-MOBILE-DKR-02",
            companyId = comp1.id,
            siteId = siteDiamniadio.id,
            deviceName = "Terminal Mobile Superviseur Diamniadio",
            deviceType = "MOBILE_APP",
            hardwareFingerprint = "ANDROID-TEE-DMN-55210B",
            isAuthorized = true,
            lastSyncEpochMs = now
        )
        val kioskSahel = DeviceEntity(
            id = "DEV-KIOSK-SAHEL-01",
            companyId = comp2.id,
            siteId = siteSahelHQ.id,
            deviceName = "Borne Biométrique Almadies",
            deviceType = "KIOSK_TERMINAL",
            hardwareFingerprint = "ANDROID-TEE-SHL-11029C",
            isAuthorized = true,
            lastSyncEpochMs = now
        )
        dao.insertDevice(kioskDakar)
        dao.insertDevice(mobileAppDakar)
        dao.insertDevice(kioskSahel)

        // --- EMPLOYÉS ENTREPRISE 1 ---
        val pin1 = cryptoEngine.hashPasswordOrPin("2468")
        val emp1 = EmployeeEntity(
            id = "EMP-DKR-001",
            companyId = comp1.id,
            siteId = siteDakarPlateau.id,
            departmentId = deptIng.id,
            scheduleId = schedStandard.id,
            firstName = "Cheikh",
            lastName = "DIALLO",
            matricule = "TTL-001",
            jobTitle = "Architecte Systèmes & Sécurité",
            email = "c.diallo@terangatech.sn",
            phone = "+221 77 412 88 90",
            status = EmployeeStatus.ACTIVE.name,
            fallbackPinHash = pin1.hashBase64,
            fallbackPinSalt = pin1.saltBase64,
            allowNonBiometricAlternative = true,
            createdAtEpochMs = now
        )

        val pin2 = cryptoEngine.hashPasswordOrPin("1357")
        val emp2 = EmployeeEntity(
            id = "EMP-DKR-002",
            companyId = comp1.id,
            siteId = siteDakarPlateau.id,
            departmentId = deptRh.id,
            scheduleId = schedStandard.id,
            firstName = "Aminata",
            lastName = "NDIAYE",
            matricule = "TTL-002",
            jobTitle = "Directrice des Opérations RH",
            email = "a.ndiaye@terangatech.sn",
            phone = "+221 77 605 19 34",
            status = EmployeeStatus.ACTIVE.name,
            fallbackPinHash = pin2.hashBase64,
            fallbackPinSalt = pin2.saltBase64,
            allowNonBiometricAlternative = true,
            createdAtEpochMs = now
        )

        val pin3 = cryptoEngine.hashPasswordOrPin("9999")
        val emp3 = EmployeeEntity(
            id = "EMP-DKR-003",
            companyId = comp1.id,
            siteId = siteDiamniadio.id,
            departmentId = deptOps.id,
            scheduleId = schedMatin.id,
            firstName = "Moussa",
            lastName = "FALL",
            matricule = "TTL-003",
            jobTitle = "Chef d'Équipe Logistique",
            email = "m.fall@terangatech.sn",
            phone = "+221 76 310 44 12",
            status = EmployeeStatus.ACTIVE.name,
            fallbackPinHash = pin3.hashBase64,
            fallbackPinSalt = pin3.saltBase64,
            allowNonBiometricAlternative = true,
            createdAtEpochMs = now
        )

        val pin4 = cryptoEngine.hashPasswordOrPin("4321")
        val emp4 = EmployeeEntity(
            id = "EMP-DKR-004",
            companyId = comp1.id,
            siteId = siteDakarPlateau.id,
            departmentId = deptIng.id,
            scheduleId = schedStandard.id,
            firstName = "Khadija",
            lastName = "GUEYE",
            matricule = "TTL-004",
            jobTitle = "Ingénieure QA & Conformité",
            email = "k.gueye@terangatech.sn",
            phone = "+221 78 192 00 51",
            status = EmployeeStatus.ACTIVE.name,
            fallbackPinHash = pin4.hashBase64,
            fallbackPinSalt = pin4.saltBase64,
            allowNonBiometricAlternative = true,
            createdAtEpochMs = now
        )

        // --- EMPLOYÉ ENTREPRISE 2 (Pour démontrer l'isolation stricte Multi-Tenant) ---
        val pinSahel = cryptoEngine.hashPasswordOrPin("5555")
        val empSahel = EmployeeEntity(
            id = "EMP-SHL-001",
            companyId = comp2.id,
            siteId = siteSahelHQ.id,
            departmentId = deptSahelProd.id,
            scheduleId = schedSahel.id,
            firstName = "Ibrahima",
            lastName = "SARR",
            matricule = "SEI-101",
            jobTitle = "Ingénieur Réseau Haute Tension",
            email = "i.sarr@sahel-energie.sn",
            phone = "+221 77 800 11 22",
            status = EmployeeStatus.ACTIVE.name,
            fallbackPinHash = pinSahel.hashBase64,
            fallbackPinSalt = pinSahel.saltBase64,
            allowNonBiometricAlternative = true,
            createdAtEpochMs = now
        )

        dao.insertEmployee(emp1)
        dao.insertEmployee(emp2)
        dao.insertEmployee(emp3)
        dao.insertEmployee(emp4)
        dao.insertEmployee(empSahel)

        // --- ENRÔLEMENTS BIOMÉTRIQUES CHIFFRÉS RÉELS (AES-256-GCM + CONSENTEMENT CDP/RGPD) ---
        repository.enrollEmployeeBiometrics(
            companyId = comp1.id,
            employeeId = emp1.id,
            modality = BiometricModality.FINGERPRINT,
            explicitConsentChecked = true,
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE (DRH)",
            livenessVerified = true
        )
        repository.enrollEmployeeBiometrics(
            companyId = comp1.id,
            employeeId = emp1.id,
            modality = BiometricModality.FACE_ID,
            explicitConsentChecked = true,
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE (DRH)",
            livenessVerified = true
        )
        repository.enrollEmployeeBiometrics(
            companyId = comp1.id,
            employeeId = emp2.id,
            modality = BiometricModality.FACE_ID,
            explicitConsentChecked = true,
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE (DRH)",
            livenessVerified = true
        )
        repository.enrollEmployeeBiometrics(
            companyId = comp1.id,
            employeeId = emp3.id,
            modality = BiometricModality.FINGERPRINT,
            explicitConsentChecked = true,
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE (DRH)",
            livenessVerified = true
        )
        repository.enrollEmployeeBiometrics(
            companyId = comp2.id,
            employeeId = empSahel.id,
            modality = BiometricModality.FINGERPRINT,
            explicitConsentChecked = true,
            adminUserId = "USR-SAHEL-ADMIN",
            adminName = "Ousmane KANE",
            livenessVerified = true
        )

        // --- UTILISATEURS & RÔLES (RBAC) ---
        val adminPass = cryptoEngine.hashPasswordOrPin("Admin@2026")
        dao.insertUser(
            UserAccountEntity(
                id = "USR-SUPER-01",
                companyId = "*",
                email = "superadmin@biopointage.sn",
                fullName = "Habibe Diémé (Architecte Plateforme)",
                role = UserRole.SUPER_ADMIN.name,
                passwordHashBase64 = adminPass.hashBase64,
                passwordSaltBase64 = adminPass.saltBase64,
                mfaEnabled = true,
                mfaSecretCode = "654321"
            )
        )
        dao.insertUser(
            UserAccountEntity(
                id = "USR-ADMIN-01",
                companyId = comp1.id,
                email = "a.ndiaye@terangatech.sn",
                fullName = "Aminata NDIAYE",
                role = UserRole.HR_MANAGER.name,
                passwordHashBase64 = adminPass.hashBase64,
                passwordSaltBase64 = adminPass.saltBase64,
                mfaEnabled = true,
                mfaSecretCode = "112233",
                linkedEmployeeId = emp2.id
            )
        )
        dao.insertUser(
            UserAccountEntity(
                id = "USR-EMP-01",
                companyId = comp1.id,
                email = "c.diallo@terangatech.sn",
                fullName = "Cheikh DIALLO",
                role = UserRole.EMPLOYEE.name,
                passwordHashBase64 = adminPass.hashBase64,
                passwordSaltBase64 = adminPass.saltBase64,
                mfaEnabled = false,
                mfaSecretCode = "",
                linkedEmployeeId = emp1.id
            )
        )

        // --- POINTAGES DU JOUR & HISTORIQUE INITIAL ---
        val todayIso = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        dao.insertAttendanceRecordIdempotent(
            AttendanceRecordEntity(
                id = "ATT-SEED-001",
                idempotencyKey = "${comp1.id}_${emp2.id}_${todayIso}_ARRIVAL",
                companyId = comp1.id,
                employeeId = emp2.id,
                employeeLastName = emp2.lastName,
                employeeFirstName = emp2.firstName,
                employeeMatricule = emp2.matricule,
                dateIso = todayIso,
                arrivalServerTimeHms = "08:21:14",
                arrivalServerEpochMs = now - 3600_000L * 3,
                departureServerTimeHms = null,
                departureServerEpochMs = null,
                workedMinutes = 180,
                lateMinutes = 0,
                overtimeMinutes = 0,
                methodUsed = BiometricModality.FACE_ID.name,
                deviceId = kioskDakar.id,
                deviceName = kioskDakar.deviceName,
                siteId = siteDakarPlateau.id,
                siteName = siteDakarPlateau.name,
                status = AttendanceStatus.ON_TIME.name,
                verificationOutcome = VerificationOutcome.SUCCESS.name,
                confidenceScore = 0.97f,
                livenessScore = 0.99f,
                syncState = SyncState.CONFIRMED_SERVER.name,
                integrityHmacSignature = cryptoEngine.signHmacSha256("SEED_001")
            )
        )
        dao.insertAttendanceRecordIdempotent(
            AttendanceRecordEntity(
                id = "ATT-SEED-002",
                idempotencyKey = "${comp1.id}_${emp3.id}_${todayIso}_ARRIVAL",
                companyId = comp1.id,
                employeeId = emp3.id,
                employeeLastName = emp3.lastName,
                employeeFirstName = emp3.firstName,
                employeeMatricule = emp3.matricule,
                dateIso = todayIso,
                arrivalServerTimeHms = "07:19:42",
                arrivalServerEpochMs = now - 3600_000L * 4,
                departureServerTimeHms = null,
                departureServerEpochMs = null,
                workedMinutes = 240,
                lateMinutes = 14,
                overtimeMinutes = 0,
                methodUsed = BiometricModality.FINGERPRINT.name,
                deviceId = mobileAppDakar.id,
                deviceName = mobileAppDakar.deviceName,
                siteId = siteDiamniadio.id,
                siteName = siteDiamniadio.name,
                status = AttendanceStatus.LATE.name,
                verificationOutcome = VerificationOutcome.SUCCESS.name,
                confidenceScore = 0.95f,
                livenessScore = 1.0f,
                syncState = SyncState.OFFLINE_TO_CONFIRM.name,
                integrityHmacSignature = cryptoEngine.signHmacSha256("SEED_002")
            )
        )

        // --- CONGÉS ---
        dao.insertLeave(
            LeaveEntity(
                id = "LVE-001",
                companyId = comp1.id,
                employeeId = emp4.id,
                employeeFullName = "${emp4.firstName} ${emp4.lastName}",
                leaveType = LeaveType.PAID_LEAVE.name,
                startDateIso = todayIso,
                endDateIso = todayIso,
                status = LeaveStatus.APPROVED.name,
                reason = "Congé annuel planifié",
                approvedBy = "Aminata NDIAYE"
            )
        )
    }
}
