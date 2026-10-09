package com.example.domain.biometrics

import com.example.domain.model.BiometricModality

/**
 * Couche d'abstraction biométrique ("BiometricProvider") exigée par l'architecture Phase 1.
 * Permet d'interagir de façon uniforme avec :
 * 1. Le TEE natif Android (BiometricPrompt / Keystore CryptoObject)
 * 2. Le moteur de reconnaissance faciale CameraX + Détection du vivant (Liveness)
 * 3. Un lecteur d'empreintes matériel externe sur borne dédiée (USB Host / SDK SecuGen, ZKTeco, Suprema)
 *
 * RÈGLE D'OR DE CONFORMITÉ : Aucune image d'empreinte ni photo de référence n'est jamais exposée.
 */
interface BiometricProvider {
    val providerId: String
    val providerName: String
    val modality: BiometricModality

    /**
     * Vérifie la disponibilité réelle du capteur matériel ou du pont SDK.
     */
    suspend fun checkHardwareStatus(): HardwareBiometricStatus

    /**
     * Génère un gabarit mathématique normalisé ou une preuve cryptographique matérielle
     * lors de l'enrôlement (après validation du consentement explicite).
     */
    suspend fun createEnrollmentProof(
        employeeId: String,
        companyId: String,
        livenessVerified: Boolean,
        featureVector: FloatArray? = null
    ): Result<BiometricEnrollmentPayload>

    /**
     * Vérifie un gabarit chiffré ou une signature cryptographique avec seuil de confiance configurable.
     */
    suspend fun verifyAttendanceProof(
        candidateVector: FloatArray?,
        storedEncryptedTemplate: ByteArray,
        storedIv: ByteArray,
        minimumConfidenceThreshold: Float,
        livenessScore: Float
    ): BiometricVerificationResult
}

data class HardwareBiometricStatus(
    val isHardwareDetected: Boolean,
    val hasEnrolledSystemBiometrics: Boolean,
    val securityLevelDescription: String,
    val requiresExternalUsbSdk: Boolean = false
)

data class BiometricEnrollmentPayload(
    val encryptedTemplateBytes: ByteArray,
    val initializationVector: ByteArray,
    val templateHashSha256: String,
    val algorithmVersion: String,
    val keyAlias: String
)

data class BiometricVerificationResult(
    val isSuccess: Boolean,
    val confidenceScore: Float,
    val livenessScore: Float,
    val livenessPassed: Boolean,
    val cryptographicProofHash: String,
    val failureReasonCode: String? = null
)
