package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BioPointageDao {

    // --- ENTREPRISES (MULTI-TENANT) ---
    @Query("SELECT * FROM companies ORDER BY name ASC")
    fun observeCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies")
    suspend fun getAllCompanies(): List<CompanyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: CompanyEntity)

    // --- SITES & GEOFENCING ---
    @Query("SELECT * FROM sites WHERE companyId = :companyId ORDER BY name ASC")
    fun observeSitesByCompany(companyId: String): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE companyId = :companyId")
    suspend fun getSitesByCompany(companyId: String): List<SiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSite(site: SiteEntity)

    // --- DÉPARTEMENTS ---
    @Query("SELECT * FROM departments WHERE companyId = :companyId ORDER BY name ASC")
    fun observeDepartmentsByCompany(companyId: String): Flow<List<DepartmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartment(department: DepartmentEntity)

    // --- HORAIRES & PLANNINGS ---
    @Query("SELECT * FROM schedules WHERE companyId = :companyId ORDER BY name ASC")
    fun observeSchedulesByCompany(companyId: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE id = :scheduleId AND companyId = :companyId LIMIT 1")
    suspend fun getScheduleById(scheduleId: String, companyId: String): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity)

    // --- EMPLOYÉS (ISOLÉS PAR ENTREPRISE) ---
    @Query("SELECT * FROM employees WHERE companyId = :companyId ORDER BY lastName ASC, firstName ASC")
    fun observeEmployeesByCompany(companyId: String): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees WHERE companyId = :companyId ORDER BY lastName ASC")
    suspend fun getEmployeesByCompany(companyId: String): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :employeeId AND companyId = :companyId LIMIT 1")
    suspend fun getEmployeeByIdScoped(employeeId: String, companyId: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: EmployeeEntity)

    @Update
    suspend fun updateEmployee(employee: EmployeeEntity)

    // --- CONSENTEMENTS (CDP SÉNÉGAL & RGPD) ---
    @Query("SELECT * FROM consents WHERE companyId = :companyId ORDER BY grantedAtEpochMs DESC")
    fun observeConsentsByCompany(companyId: String): Flow<List<ConsentEntity>>

    @Query("SELECT * FROM consents WHERE employeeId = :employeeId AND companyId = :companyId AND explicitConsentGiven = 1 AND revokedAtEpochMs IS NULL LIMIT 1")
    suspend fun getActiveConsentForEmployee(employeeId: String, companyId: String): ConsentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsent(consent: ConsentEntity)

    // --- ENRÔLEMENTS BIOMÉTRIQUES CHIFFRÉS ---
    @Query("SELECT * FROM biometric_enrollments WHERE companyId = :companyId AND isActive = 1")
    fun observeActiveEnrollmentsByCompany(companyId: String): Flow<List<BiometricEnrollmentEntity>>

    @Query("SELECT * FROM biometric_enrollments WHERE employeeId = :employeeId AND companyId = :companyId AND isActive = 1")
    suspend fun getActiveEnrollmentsForEmployee(employeeId: String, companyId: String): List<BiometricEnrollmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBiometricEnrollment(enrollment: BiometricEnrollmentEntity)

    @Query("DELETE FROM biometric_enrollments WHERE employeeId = :employeeId AND companyId = :companyId")
    suspend fun purgeBiometricsForEmployee(employeeId: String, companyId: String)

    // --- APPAREILS & BORNES KIOSQUES ---
    @Query("SELECT * FROM devices WHERE companyId = :companyId ORDER BY deviceName ASC")
    fun observeDevicesByCompany(companyId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :deviceId AND companyId = :companyId LIMIT 1")
    suspend fun getDeviceByIdScoped(deviceId: String, companyId: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    // --- POINTAGES (ATTENDANCE RECORDS) ---
    @Query("SELECT * FROM attendance_records WHERE companyId = :companyId ORDER BY arrivalServerEpochMs DESC")
    fun observeAttendanceByCompany(companyId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE companyId = :companyId ORDER BY arrivalServerEpochMs DESC")
    suspend fun getAttendanceByCompany(companyId: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_records WHERE companyId = :companyId AND employeeId = :employeeId AND dateIso = :dateIso AND verificationOutcome = 'SUCCESS' ORDER BY arrivalServerEpochMs DESC LIMIT 1")
    suspend fun getLatestSuccessfulAttendanceForDay(companyId: String, employeeId: String, dateIso: String): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance_records WHERE companyId = :companyId AND syncState = 'OFFLINE_TO_CONFIRM'")
    suspend fun getPendingOfflineAttendance(companyId: String): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttendanceRecordIdempotent(record: AttendanceRecordEntity): Long

    @Update
    suspend fun updateAttendanceRecord(record: AttendanceRecordEntity)

    // --- ABSENCES & CONGÉS ---
    @Query("SELECT * FROM leaves WHERE companyId = :companyId ORDER BY startDateIso DESC")
    fun observeLeavesByCompany(companyId: String): Flow<List<LeaveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeave(leave: LeaveEntity)

    // --- UTILISATEURS & RÔLES (RBAC) ---
    @Query("SELECT * FROM users WHERE companyId = :companyId OR companyId = '*' ORDER BY fullName ASC")
    fun observeUsersForCompany(companyId: String): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccountEntity)

    // --- AUDIT LOGS INALTÉRABLES ---
    @Query("SELECT * FROM audit_logs WHERE companyId = :companyId ORDER BY timestampEpochMs DESC")
    fun observeAuditLogsByCompany(companyId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE companyId = :companyId ORDER BY timestampEpochMs DESC LIMIT 1")
    suspend fun getLatestAuditLog(companyId: String): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: AuditLogEntity)

    // --- NOTIFICATIONS ---
    @Query("SELECT * FROM notifications WHERE companyId = :companyId ORDER BY createdAtEpochMs DESC")
    fun observeNotificationsByCompany(companyId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE companyId = :companyId")
    suspend fun markAllNotificationsRead(companyId: String)
}

@Database(
    entities = [
        CompanyEntity::class,
        SiteEntity::class,
        DepartmentEntity::class,
        ScheduleEntity::class,
        EmployeeEntity::class,
        ConsentEntity::class,
        BiometricEnrollmentEntity::class,
        DeviceEntity::class,
        AttendanceRecordEntity::class,
        LeaveEntity::class,
        UserAccountEntity::class,
        AuditLogEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BioPointageDatabase : RoomDatabase() {
    abstract fun bioPointageDao(): BioPointageDao
}
