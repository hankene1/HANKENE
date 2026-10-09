package com.example.domain.time

import com.example.domain.model.SyncState

/**
 * Autorité d'horodatage serveur ("ServerTimeAuthority").
 * RÈGLE : L'heure officielle d'un pointage ne dépend JAMAIS de l'horloge système modifiable par l'utilisateur.
 * - En ligne : utilise l'horodatage serveur autoritaire signé.
 * - Hors ligne : utilise l'horloge monotone matérielle (`SystemClock.elapsedRealtime()`) corrélée au dernier
 *   ancrage serveur connu et marque le pointage comme `OFFLINE_TO_CONFIRM`.
 */
interface ServerTimeAuthority {
    /**
     * Acquiert un horodatage certifié pour un pointage.
     */
    suspend fun acquireCertifiedTimestamp(isNetworkAvailable: Boolean): CertifiedTimestamp

    /**
     * Recalibre l'ancrage monotone avec le serveur central.
     */
    suspend fun synchronizeAnchorWithServer(serverEpochMillis: Long)
}

data class CertifiedTimestamp(
    val officialEpochMillis: Long,
    val formattedDateIso: String,       // YYYY-MM-DD
    val formattedTimeHms: String,       // HH:mm:ss
    val monotonicElapsedRealtimeMs: Long,
    val syncState: SyncState,
    val serverSignatureHmac: String,
    val clockDriftDetectedMs: Long
)
