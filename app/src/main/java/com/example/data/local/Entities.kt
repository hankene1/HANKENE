package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 1. Table Companies (Entreprises clientes en architecture Multi-Tenant stricte)
 */
@Entity(
    tableName = "companies",
    indices = [Index(value = ["registrationCode"], unique = true)]
)
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val registrationCode: String,
    val countryCode: String, // ex: "SN" (Sénégal - CDP), "FR" (RGPD)
    val timezoneId: String,
    val biometricRetentionDays: Int,
    val defaultLateToleranceMinutes: Int,
    val biometricConfidenceThreshold: Float,
    val isActive: Boolean,
    val createdAtEpochMs: Long
)

/**
 * 2. Table Sites (Établissements / Agences avec coordonnées de Geofencing)
 */
@Entity(
    tableName = "sites",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["companyId"])]
)
data class SiteEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val allowedRadiusMeters: Int,
    val enforceGeofencing: Boolean
)

/**
 * 3. Table Departments (Départements / Équipes rattachés à un site et une entreprise)
 */
@Entity(
    tableName = "departments",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["companyId"]), Index(value = ["siteId"])]
)
data class DepartmentEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val siteId: String,
    val name: String,
    val managerName: String
)

/**
 * 4. Table Schedules (Plannings, horaires, tolérances de retard, pauses, équipes de nuit)
 */
@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["companyId"])]
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val startTimeHm: String, // ex: "08:30"
    val endTimeHm: String,   // ex: "17:30"
    val breakDurationMinutes: Int,
    val lateToleranceMinutes: Int,
    val overtimeThresholdMinutes: Int,
    val isNightShift: Boolean,
    val workingDaysCsv: String // ex: "MON,TUE,WED,THU,FRI"
)

/**
 * 5. Table Employees (Identité de l'employé — SÉPARÉE des gabarits biométriques)
 */
@Entity(
    tableName = "employees",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = DepartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["companyId", "matricule"], unique = true),
        Index(value = ["companyId"]),
        Index(value = ["siteId"]),
        Index(value = ["departmentId"]),
        Index(value = ["scheduleId"])
    ]
)
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val siteId: String,
    val departmentId: String,
    val scheduleId: String,
    val firstName: String,     // Prénom de l'employé
    val lastName: String,      // Nom de l'employé
    val matricule: String,     // Identifiant matricule unique par entreprise
    val jobTitle: String,
    val email: String,
    val phone: String,
    val status: String,        // ACTIVE, SUSPENDED, DEPARTED
    val fallbackPinHash: String,
    val fallbackPinSalt: String,
    val allowNonBiometricAlternative: Boolean,
    val createdAtEpochMs: Long
)

/**
 * 6. Table Consents (Consentement explicite RGPD / CDP Sénégal avant tout enrôlement)
 */
@Entity(
    tableName = "consents",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["companyId"]), Index(value = ["employeeId"])]
)
data class ConsentEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val employeeId: String,
    val legalFramework: String, // "CDP_SENEGAL_2008_12 & RGPD_EU"
    val consentVersion: String,
    val purposeDescription: String,
    val explicitConsentGiven: Boolean,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val revokedAtEpochMs: Long? = null
)

/**
 * 7. Table BiometricEnrollments (Gabarits biométriques chiffrés AES-256-GCM — Aucune photo ni nom)
 */
@Entity(
    tableName = "biometric_enrollments",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConsentEntity::class,
            parentColumns = ["id"],
            childColumns = ["consentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["employeeId"]),
        Index(value = ["consentId"])
    ]
)
data class BiometricEnrollmentEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val employeeId: String,
    val consentId: String,
    val modality: String, // FINGERPRINT, FACE_ID, HARDWARE_SCANNER
    val encryptedTemplateBase64: String,
    val ivBase64: String,
    val templateSha256: String,
    val keyAlias: String,
    val livenessScoreAtEnrollment: Float,
    val enrolledByAdminId: String,
    val enrolledAtEpochMs: Long,
    val isActive: Boolean
)

/**
 * 8. Table Devices (Appareils mobiles et Bornes Kiosques enregistrés et révocables)
 */
@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["companyId"]), Index(value = ["siteId"])]
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val siteId: String,
    val deviceName: String,
    val deviceType: String, // KIOSK_TERMINAL, MOBILE_APP, EXTERNAL_SCANNER_TERMINAL
    val hardwareFingerprint: String,
    val isAuthorized: Boolean,
    val lastSyncEpochMs: Long
)

/**
 * 9. Table AttendanceRecords (Pointages inaltérables avec nom, prénom, date, heure serveur)
 */
@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["companyId", "dateIso"]),
        Index(value = ["companyId", "employeeId", "dateIso"]),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["employeeId"]),
        Index(value = ["siteId"]),
        Index(value = ["deviceId"])
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey val id: String,
    val idempotencyKey: String,        // Garantit 0 doublon à la synchronisation hors-ligne
    val companyId: String,
    val employeeId: String,
    val employeeLastName: String,      // Nom de l'employé (figé au moment du pointage)
    val employeeFirstName: String,     // Prénom de l'employé (figé au moment du pointage)
    val employeeMatricule: String,     // Matricule
    val dateIso: String,               // YYYY-MM-DD
    val arrivalServerTimeHms: String,  // Heure exacte d'arrivée (Serveur)
    val arrivalServerEpochMs: Long,
    val departureServerTimeHms: String?, // Heure exacte de départ (Serveur)
    val departureServerEpochMs: Long?,
    val workedMinutes: Int,
    val lateMinutes: Int,
    val overtimeMinutes: Int,
    val methodUsed: String,            // FINGERPRINT, FACE_ID, FALLBACK_PIN
    val deviceId: String,
    val deviceName: String,
    val siteId: String,
    val siteName: String,
    val status: String,                // ON_TIME, LATE, OVERTIME, ABSENT
    val verificationOutcome: String,   // SUCCESS ou motif d'échec
    val confidenceScore: Float,
    val livenessScore: Float,
    val syncState: String,             // CONFIRMED_SERVER, OFFLINE_TO_CONFIRM
    val integrityHmacSignature: String,
    val manualAdjustmentReason: String? = null,
    val adjustedByAdminName: String? = null
)

/**
 * 10. Table Leaves (Absences, congés et jours fériés)
 */
@Entity(
    tableName = "leaves",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["companyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["companyId"]), Index(value = ["employeeId"])]
)
data class LeaveEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val employeeId: String,
    val employeeFullName: String,
    val leaveType: String,
    val startDateIso: String,
    val endDateIso: String,
    val status: String,
    val reason: String,
    val approvedBy: String?
)

/**
 * 11. Table Users (Comptes utilisateurs & RBAC : Super Admin, Admin Entreprise, RH, Manager, Employé, Borne)
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true), Index(value = ["companyId"])]
)
data class UserAccountEntity(
    @PrimaryKey val id: String,
    val companyId: String, // "*" pour Super Admin plateforme, sinon ID de l'entreprise isolée
    val email: String,
    val fullName: String,
    val role: String,      // SUPER_ADMIN, COMPANY_ADMIN, HR_MANAGER, MANAGER, EMPLOYEE, KIOSK_OPERATOR
    val passwordHashBase64: String,
    val passwordSaltBase64: String,
    val mfaEnabled: Boolean,
    val mfaSecretCode: String,
    val linkedEmployeeId: String? = null,
    val departmentIdScope: String? = null
)

/**
 * 12. Table AuditLogs (Journal d'audit immuable et chaîné par hash SHA-256)
 */
@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["companyId", "timestampEpochMs"])]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val actorUserId: String,
    val actorName: String,
    val actorRole: String,
    val actionType: String,     // ex: "MANUAL_ATTENDANCE_CORRECTION", "BIOMETRIC_ENROLLMENT", "BIOMETRIC_PURGE"
    val targetEntity: String,
    val targetId: String,
    val justification: String,
    val timestampEpochMs: Long,
    val previousLogSha256: String,
    val entrySha256: String
)

/**
 * 13. Table Notifications (Alertes temps réel : retards récurrents, tentatives échouées, hors-ligne)
 */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["companyId", "createdAtEpochMs"])]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val title: String,
    val message: String,
    val severity: String, // INFO, WARNING, CRITICAL_FRAUD
    val isRead: Boolean,
    val createdAtEpochMs: Long
)
