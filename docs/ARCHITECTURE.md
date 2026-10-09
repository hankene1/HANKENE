# Architecture Générale — BioPointage Pro (SaaS Biométrique Multi-Entreprises)

## 1. Vue d'Ensemble & Vision Technique

**BioPointage Pro** est une plateforme SaaS et Borne/Mobile de gestion des présences biométrique conçue selon les principes de **Clean Architecture**, **Domain-Driven Design (DDD)**, **Zero-Trust Multi-Tenant Isolation** et **Offline-First Synchronization**.

### Diagramme d'Architecture Globale
```
[ Employé / Borne Kiosque / App Mobile ]
        │
        ├─► Couche Biométrique Matérielle (BiometricProvider)
        │     ├─► Android BiometricPrompt / Keystore TEE (Hardware-backed CryptoObject)
        │     ├─► Reconnaissance Faciale + Détection du Vivant (Liveness & Embeddings AES-256-GCM)
        │     └─► Pont SDK Lecteur d'Empreintes Externe USB/Série (Borne dédiée)
        │
        ├─► Moteur d'Horodatage & Anti-Fraude (ServerTimeAuthority + Geofencing)
        │     ├─► En ligne : Horodatage Serveur Autoritaire + Signature HMAC-SHA256
        │     └─► Hors ligne : Horloge Monotone (`elapsedRealtime`) + Statut `PENDING_SERVER_CONFIRMATION`
        │
        └─► Persistance & Isolation Multi-Entreprises (Room SQLite / PostgreSQL RLS)
              ├─► Isolation stricte par `companyId` (Clés étrangères + Filtres obligatoires)
              ├─► Séparation physique/logique Identité (`Employees`) vs Biométrie (`BiometricEnrollments`)
              └─► Journal d'Audit Inaltérable (`AuditLogs` chaîné par hash SHA-256)
```

## 2. Choix de la Stack Technique & Compromis

| Composant | Technologie Choisie | Justification & Compromis |
| :--- | :--- | :--- |
| **Application Mobile & Borne Kiosque** | **Kotlin + Jetpack Compose (Android Natif)** | Accès direct au matériel (`BiometricPrompt`, `AndroidKeyStore`, `CameraX`, `LocationManager`), latence < 100ms pour un pointage en moins de 3 secondes, mode Kiosque (`LockTaskMode`) natif. Compromis : codebase spécifique Android, mais indispensable pour la sécurité biométrique matérielle. |
| **Base de Données Locale & Cache Hors-Ligne** | **Room (SQLite) + Chiffrement AES-256-GCM** | Garantit le fonctionnement 100% hors-ligne sur borne en cas de coupure Internet. Requêtes réactives via Kotlin `Flow`, contraintes relationnelles fortes et indexation multi-colonnes (`companyId`, `employeeId`, `date`). |
| **Authentification Biométrique** | **AndroidX Biometric + Keystore TEE + Gabarits Chiffrés** | L'application ne stocke **jamais** d'image d'empreinte ni de photo de visage de référence. Utilisation de clés asymétriques/symétriques dans le Trusted Execution Environment (TEE) et de vecteurs mathématiques (embeddings 128D) chiffrés via `AES/GCM/NoPadding`. |
| **Sécurité des Mots de Passe & PIN** | **PBKDF2WithHmacSHA256 (65 536 itérations + Sel 128 bits)** | Implémentation cryptographique standard intégrée au JCA Android sans dépendance native externe instable, résistante au brute-force GPU. |
| **Architecture & État** | **MVVM + Clean Architecture + Coroutines/StateFlow** | Séparation stricte entre `domain` (modèles, interfaces, règles métier), `data` (Room, dépôts, cryptographie) et `ui` (Compose Material 3 adaptatif). |

## 3. Flux de Données Critiques

### A. Flux d'Enrôlement Biométrique (Conforme CDP Sénégal & RGPD)
1. L'administrateur sélectionne l'employé dans son entreprise (`companyId` vérifié).
2. Présentation du texte légal de consentement (finalité, durée de conservation, droit de retrait, alternative par code PIN).
3. Validation explicite et création d'un enregistrement dans `Consents`.
4. Capture via `BiometricProvider` -> Génération du gabarit mathématique (embedding) ou de la preuve cryptographique TEE.
5. Chiffrement immédiat en mémoire via `CryptoEngine` (`AES-256-GCM` avec clé maîtresse Android Keystore).
6. Enregistrement dans `BiometricEnrollments` (table isolée des données nominatives) + trace dans `AuditLogs`.

### B. Flux de Pointage (< 3 secondes)
1. L'employé se présente sur la borne ou son mobile (Empreinte, Visage avec détection du vivant, ou PIN de secours).
2. Vérification de l'appareil (`Devices.isAuthorized`), du périmètre géographique (`Sites.radiusMeters`) et de l'absence de doublon récent (fenêtre anti-rebond).
3. Acquisition de l'horodatage via `TimeAuthorityProvider` (Serveur en ligne, ou Horloge Monotone signée si hors-ligne).
4. Calcul automatique du statut (`ON_TIME`, `LATE`, `OVERTIME`) selon le planning (`Schedules`) et la tolérance configurée.
5. Création immuable dans `AttendanceRecords` avec `nom`, `prénom`, `matricule`, `date`, `heure serveur` et signature d'intégrité.

### C. Flux de Synchronisation Hors-Ligne
1. Coupure réseau détectée -> Bascule automatique en stockage local chiffré.
2. Pointage enregistré avec `syncStatus = PENDING_CONFIRMATION` et dérive horloge calculée par rapport au dernier tick serveur connu.
3. Au retour de la connexion -> Lot de synchronisation idempotent (clé d'idempotence `UUID` + `deviceId` + `monotonicTimestamp`) empêchant tout doublon.
