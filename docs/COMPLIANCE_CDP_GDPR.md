# Conformité Légale, Biométrie & Protection des Données (CDP Sénégal & RGPD)

## 1. Principes Fondamentaux de Protection Biométrique

Conformément à la **Loi n° 2008-12 du 25 janvier 2008** portant sur la protection des données à caractère personnel au Sénégal (supervisée par la **CDP - Commission de Protection des Données Personnelles**) et au **Règlement Général sur la Protection des Données (RGPD - Article 9)** :

1. **Absence totale d'images brutes** :
   - **Empreinte digitale** : Sur mobile/tablette Android, l'image ne quitte jamais le **TEE (Trusted Execution Environment)** matériel du système. L'application reçoit uniquement un `CryptoObject` signé prouvant la réussite de l'authentification. Sur lecteur USB externe (via `BiometricProvider`), seul le gabarit minutiae normalisé ISO/IEC 19794-2 chiffré est traité.
   - **Reconnaissance faciale** : La caméra extrait en mémoire vive un vecteur mathématique (embedding 128 dimensions) après validation de la détection du vivant (*Liveness Detection* : clignement des yeux, mouvement de tête, analyse de texture anti-écran/photo). L'image caméra est immédiatement détruite de la RAM.

2. **Chiffrement au Repos et en Transit** :
   - Tous les gabarits dans `BiometricEnrollments` sont chiffrés avec **AES-256-GCM** (vecteur d'initialisation unique de 96 bits + tag d'authentification de 128 bits).
   - Les clés de chiffrement sont générées et stockées dans l'**Android Keystore matériel** (`KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT`).

3. **Séparation des Données** :
   - La table `BiometricEnrollments` ne contient ni nom, ni prénom, ni email. Elle est liée par un identifiant interne et stocke uniquement les métadonnées cryptographiques.

4. **Consentement Explicite & Alternative Non-Biométrique** :
   - Aucun enrôlement n'est possible sans l'enregistrement préalable d'un consentement explicite (`Consents`) précisant la version de la politique, la date d'acceptation et la durée de conservation.
   - Tout employé refusant la biométrie dispose automatiquement d'un mode alternatif (Code PIN personnel haché + validation superviseur sur borne) sans aucune discrimination fonctionnelle.

5. **Droit à l'Effacement & Purge Automatique** :
   - La révocation du consentement ou le passage du statut de l'employé à `DEPARTED` déclenche la suppression cryptographique immédiate de ses gabarits dans `BiometricEnrollments`, tout en journalisant l'action dans `AuditLogs`.

## 2. Réalisabilité par Plateforme (Transparence Technique)

| Plateforme | Empreinte Digitale | Reconnaissance Faciale + Vivant | Exigences Matérielles / SDK |
| :--- | :--- | :--- | :--- |
| **Android (Mobile & Tablette Kiosque)** | Natif via `BiometricPrompt` (TEE Class 3 Strong Biometrics) | Caméra frontale CameraX + Analyse Liveness (clignement/rotation) + Embedding | Capteur biométrique Android standard ou caméra frontale. Fonctionnel nativement. |
| **Borne Dédiée Industrielle** | Lecteur optique/capacitif USB (SecuGen, ZKTeco, Futronic, Suprema) | Caméra WDR double optique (RGB + Infrarouge) | Pont matériel implémenté via l'interface `BiometricProvider` (USB Host / JNI SDK). |
| **Navigateur Web (WebAuthn)** | Passkeys / FIDO2 (`navigator.credentials.create/get`) | Face ID / Windows Hello via WebAuthn | Navigateur compatible FIDO2 sur HTTPS. |
| **iOS (iPhone / iPad Kiosque)** | Touch ID (`LocalAuthentication`) | Face ID (Caméra TrueDepth matérielle) | API `LAContext` native iOS. |
