package com.example.domain.model

enum class UserRole(val labelFr: String, val descriptionFr: String) {
    SUPER_ADMIN("Super Admin (Plateforme)", "Gestion globale multi-entreprises, infrastructure et sécurité"),
    COMPANY_ADMIN("Admin Entreprise", "Administration complète d'une entreprise, sites, appareils et paramètres"),
    HR_MANAGER("Responsable RH", "Gestion des employés, horaires, congés, rapports et enrôlements"),
    MANAGER("Manager d'Équipe", "Consultation et supervision de son département uniquement"),
    EMPLOYEE("Employé", "Pointage personnel et consultation de son propre historique"),
    KIOSK_OPERATOR("Opérateur de Borne", "Interface restreinte au mode kiosque de pointage uniquement")
}

enum class EmployeeStatus(val labelFr: String) {
    ACTIVE("Actif"),
    SUSPENDED("Suspendu"),
    DEPARTED("Parti (Biométrie purgée)")
}

enum class BiometricModality(val labelFr: String) {
    FINGERPRINT("Empreinte Digitale (TEE / Capteur)"),
    FACE_ID("Reconnaissance Faciale + Détection du Vivant"),
    FALLBACK_PIN("Code PIN de Secours"),
    HARDWARE_SCANNER("Lecteur Biométrique Externe (USB/SDK)")
}

enum class AttendanceStatus(val labelFr: String) {
    ON_TIME("À l'heure"),
    LATE("En retard"),
    OVERTIME("Heures supplémentaires"),
    ABSENT("Absent"),
    EARLY_DEPARTURE("Départ anticipé")
}

enum class VerificationOutcome(val labelFr: String) {
    SUCCESS("Réussi"),
    FAILED_BIOMETRIC_MISMATCH("Échec : Biométrie non reconnue"),
    FAILED_LIVENESS_CHECK("Échec Anti-Fraude : Détection du vivant refusée"),
    FAILED_GEOFENCE("Échec Anti-Fraude : Hors périmètre du site"),
    FAILED_UNAUTHORIZED_DEVICE("Échec Sécurité : Appareil non autorisé"),
    FAILED_DUPLICATE_PUNCH("Refusé : Pointage en double détecté"),
    FAILED_INVALID_PIN("Échec : Code de secours invalide")
}

enum class SyncState(val labelFr: String) {
    CONFIRMED_SERVER("Horodatage Serveur Confirmé"),
    OFFLINE_TO_CONFIRM("Hors ligne — À confirmer"),
    SYNC_CONFLICT_RESOLVED("Synchronisé (Résolution d'idempotence)")
}

enum class LeaveType(val labelFr: String) {
    PAID_LEAVE("Congé payé"),
    SICK_LEAVE("Congé maladie"),
    AUTHORIZED_ABSENCE("Absence autorisée"),
    MATERNITY_PATERNITY("Congé parental"),
    PUBLIC_HOLIDAY("Jour férié")
}

enum class LeaveStatus(val labelFr: String) {
    PENDING("En attente"),
    APPROVED("Approuvé"),
    REJECTED("Refusé")
}

enum class AppLanguage(val code: String, val label: String) {
    FR("fr", "Français"),
    EN("en", "English"),
    WO("wo", "Wolof")
}

sealed class AppError(val userFriendlyMessageFr: String, val technicalCode: String) {
    object BiometricVerificationFailed : AppError(
        "Le pointage n'a pas pu être validé. Veuillez réessayer ou contacter votre responsable.",
        "ERR_BIO_VERIFY_001"
    )
    object LivenessFailed : AppError(
        "La vérification de présence réelle (détection du vivant) a échoué. Assurez-vous d'être bien éclairé face à la caméra.",
        "ERR_BIO_LIVENESS_002"
    )
    object DuplicateAttendance : AppError(
        "Un pointage vient déjà d'être enregistré il y a quelques instants.",
        "ERR_ATT_DUP_003"
    )
    object GeofenceViolation : AppError(
        "Le pointage n'est pas autorisé en dehors du périmètre géographique de votre site d'affectation.",
        "ERR_GEO_OUT_004"
    )
    object UnauthorizedDevice : AppError(
        "Cet appareil ou cette borne n'est pas autorisé pour enregistrer des pointages.",
        "ERR_DEV_UNAUTH_005"
    )
    object TenantIsolationViolation : AppError(
        "Accès refusé : vous n'êtes pas autorisé à consulter les données de cette entreprise.",
        "ERR_SEC_TENANT_006"
    )
    object MissingConsent : AppError(
        "Le consentement explicite de l'employé est obligatoire avant tout enrôlement biométrique.",
        "ERR_CDP_CONSENT_007"
    )
}
