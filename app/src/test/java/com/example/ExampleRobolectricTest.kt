package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BioPointageDao
import com.example.data.local.BioPointageDatabase
import com.example.data.local.DatabaseSeeder
import com.example.data.repository.BioPointageRepository
import com.example.data.security.CryptoEngine
import com.example.data.security.EmbeddedSecureBiometricProvider
import com.example.data.security.RealServerTimeAuthority
import com.example.domain.model.AttendanceStatus
import com.example.domain.model.BiometricModality
import com.example.domain.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    private lateinit var database: BioPointageDatabase
    private lateinit var dao: BioPointageDao
    private lateinit var cryptoEngine: CryptoEngine
    private lateinit var timeAuthority: RealServerTimeAuthority
    private lateinit var biometricProvider: EmbeddedSecureBiometricProvider
    private lateinit var repository: BioPointageRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BioPointageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.bioPointageDao()
        cryptoEngine = CryptoEngine()
        timeAuthority = RealServerTimeAuthority(cryptoEngine)
        biometricProvider = EmbeddedSecureBiometricProvider(cryptoEngine = cryptoEngine)
        repository = BioPointageRepository(dao, cryptoEngine, timeAuthority, biometricProvider)
        DatabaseSeeder.seedIfEmpty(dao, repository, cryptoEngine)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testMultiTenantIsolation_preventsCrossCompanyEmployeeAccess() = runBlocking {
        // L'employé EMP-SHL-001 appartient à COMP-SAHEL-02 et NON à COMP-DAKAR-01
        val accessResult = repository.verifyTenantAccess(
            employeeId = "EMP-SHL-001",
            activeCompanyId = "COMP-DAKAR-01"
        )
        assertTrue("Cross-tenant access must be rejected", accessResult.isFailure)

        // Une tentative de pointage sur l'entreprise 1 avec un employé de l'entreprise 2 doit échouer
        val punchCrossTenant = repository.performAttendancePunch(
            companyId = "COMP-DAKAR-01",
            employeeId = "EMP-SHL-001",
            deviceId = "DEV-KIOSK-DKR-01",
            method = BiometricModality.FINGERPRINT,
            isPunchOut = false,
            isOnline = true
        )
        assertTrue("Cross-tenant attendance punch must fail", punchCrossTenant.isFailure)
    }

    @Test
    fun testBiometricConsentAndAesGcmEncryptionAndPurge() = runBlocking {
        // 1. L'enrôlement sans consentement explicite doit être refusé (Conformité CDP Sénégal & RGPD)
        val noConsentResult = repository.enrollEmployeeBiometrics(
            companyId = "COMP-DAKAR-01",
            employeeId = "EMP-DKR-004",
            modality = BiometricModality.FINGERPRINT,
            explicitConsentChecked = false,
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE",
            livenessVerified = true
        )
        assertTrue("Enrollment without explicit consent must fail", noConsentResult.isFailure)

        // 2. L'enrôlement avec consentement explicite chiffre le gabarit en AES-256-GCM
        val validConsentResult = repository.enrollEmployeeBiometrics(
            companyId = "COMP-DAKAR-01",
            employeeId = "EMP-DKR-004",
            modality = BiometricModality.FINGERPRINT,
            explicitConsentChecked = true,
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE",
            livenessVerified = true
        )
        assertTrue("Enrollment with explicit consent must succeed", validConsentResult.isSuccess)
        val enrolled = validConsentResult.getOrThrow()
        assertTrue(enrolled.encryptedTemplateBase64.isNotBlank())
        assertTrue(enrolled.ivBase64.isNotBlank())

        // 3. Droit à l'oubli / Purge biométrique supprime tous les gabarits de l'employé
        repository.purgeEmployeeBiometrics(
            companyId = "COMP-DAKAR-01",
            employeeId = "EMP-DKR-004",
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE",
            reason = "Départ de l'entreprise"
        )
        val remaining = dao.getActiveEnrollmentsForEmployee("EMP-DKR-004", "COMP-DAKAR-01")
        assertTrue("Biometric templates must be completely purged", remaining.isEmpty())
    }

    @Test
    fun testAttendanceArrivalDeparture_duplicatePrevention_andOfflineSync() = runBlocking {
        // Pointage d'arrivée hors-ligne pour Cheikh DIALLO (EMP-DKR-001)
        val arrivalResult = repository.performAttendancePunch(
            companyId = "COMP-DAKAR-01",
            employeeId = "EMP-DKR-001",
            deviceId = "DEV-KIOSK-DKR-01",
            method = BiometricModality.FINGERPRINT,
            isPunchOut = false,
            isOnline = false
        )
        assertTrue("Arrival punch must succeed", arrivalResult.isSuccess)
        val record = arrivalResult.getOrThrow()
        assertEquals("DIALLO", record.employeeLastName)
        assertEquals("Cheikh", record.employeeFirstName)
        assertEquals(SyncState.OFFLINE_TO_CONFIRM.name, record.syncState)

        // Un second pointage d'arrivée immédiat doit être bloqué comme doublon
        val duplicateResult = repository.performAttendancePunch(
            companyId = "COMP-DAKAR-01",
            employeeId = "EMP-DKR-001",
            deviceId = "DEV-KIOSK-DKR-01",
            method = BiometricModality.FINGERPRINT,
            isPunchOut = false,
            isOnline = false
        )
        assertTrue("Duplicate arrival punch must be rejected", duplicateResult.isFailure)

        // Synchronisation hors-ligne vers le serveur sans doublon
        val syncedCount = repository.synchronizeOfflineRecords("COMP-DAKAR-01", "Test Sync")
        assertTrue("At least one offline record must be synced", syncedCount >= 1)

        // Correction manuelle exige une justification et crée un Audit Log chaîné SHA-256
        val correctionFail = repository.manuallyCorrectAttendance(
            companyId = "COMP-DAKAR-01",
            record = record,
            newArrivalHms = "08:25:00",
            newDepartureHms = "17:35:00",
            newStatus = AttendanceStatus.ON_TIME,
            justification = "court",
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE"
        )
        assertTrue("Short justification must be rejected", correctionFail.isFailure)

        val correctionOk = repository.manuallyCorrectAttendance(
            companyId = "COMP-DAKAR-01",
            record = record,
            newArrivalHms = "08:25:00",
            newDepartureHms = "17:35:00",
            newStatus = AttendanceStatus.ON_TIME,
            justification = "Mission client matinale validée par la direction RH",
            adminUserId = "USR-ADMIN-01",
            adminName = "Aminata NDIAYE"
        )
        assertTrue("Valid manual correction must succeed", correctionOk.isSuccess)
        val latestAudit = dao.getLatestAuditLog("COMP-DAKAR-01")
        assertNotNull(latestAudit)
        assertEquals("MANUAL_ATTENDANCE_CORRECTION", latestAudit?.actionType)
    }
}
