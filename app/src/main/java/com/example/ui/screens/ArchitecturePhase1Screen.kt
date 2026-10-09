package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.domain.biometrics.HardwareBiometricStatus

@Composable
fun ArchitecturePhase1Screen(
    hardwareStatus: HardwareBiometricStatus,
    companies: List<CompanyEntity>,
    activeCompanyId: String,
    employeesCount: Int,
    enrollmentsCount: Int,
    attendanceCount: Int,
    auditLogsCount: Int,
    isOnline: Boolean,
    serverClock: String,
    onTestCrossTenantIsolation: () -> Unit,
    onLaunchKiosk: () -> Unit,
    onLaunchAdmin: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Architecture Phase 1",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "PHASE 1 & FONDATIONS — ÉTAT RÉEL DU SYSTÈME",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Badge(
                            containerColor = if (isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = if (isOnline) "EN LIGNE" else "HORS LIGNE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Architecture Multi-Entreprises (Zero-Trust), Moteur Biométrique (Android Keystore TEE + Gabarits Chiffrés AES-256-GCM), Autorité d'Horodatage Serveur ($serverClock) et Base SQLite/Room normalisée (13 tables).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onLaunchKiosk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_kiosk_from_phase1")
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ouvrir Borne Kiosque")
                        }
                        OutlinedButton(
                            onClick = onLaunchAdmin,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_open_admin_from_phase1")
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dashboard Admin")
                        }
                    }
                }
            }
        }

        // Télémétrie temps réel de la base Room et du matériel
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Diagnostic Matériel, Cryptographique & Base de Données",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MetricRow("Fournisseur Biométrique (BiometricProvider)", hardwareStatus.securityLevelDescription)
                    MetricRow("Capteur Matériel Détecté", if (hardwareStatus.isHardwareDetected) "OUI (Actif)" else "Pont Externe Requis")
                    MetricRow("Chiffrement des Gabarits au Repos", "AES-256-GCM (IV 96-bit + Tag 128-bit, 0 photo stockée)")
                    MetricRow("Hachage Mots de Passe & PIN", "PBKDF2WithHmacSHA256 (65 536 itérations + Sel 128-bit)")
                    MetricRow("Entreprises Isolées (Multi-Tenant)", "${companies.size} entreprises actives (Contexte : $activeCompanyId)")
                    MetricRow("Employés / Gabarits Chiffrés (Entreprise active)", "$employeesCount employés / $enrollmentsCount gabarits AES-GCM")
                    MetricRow("Pointages / Audit Logs Chaînés SHA-256", "$attendanceCount pointages / $auditLogsCount entrées d'audit")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onTestCrossTenantIsolation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_test_tenant_isolation")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vérifier l'Isolation Multi-Entreprises (Simuler requête ID autre entreprise)")
                    }
                }
            }
        }

        // Livrables Architecturaux des 10 points de la Phase 1
        item {
            ArchitectureSpecCard(
                number = "2",
                title = "Stack Technique & Compromis Justifiés",
                bullets = listOf(
                    "Mobile & Borne Kiosque : Kotlin + Jetpack Compose + AndroidX Biometric + CameraX. Choisi pour l'accès natif au TEE matériel (Hardware Keystore) et un pointage < 3 secondes sans latence WebView.",
                    "Persistance Locale & Mode Hors-Ligne : Room (SQLite) avec clés étrangères strictes, index composites (companyId, employeeId, dateIso) et idempotence native.",
                    "Cryptographie : JCA AndroidKeyStore (AES/GCM/NoPadding 256 bits) + PBKDF2-HMAC-SHA256 + Signatures d'intégrité HMAC-SHA256 sur chaque pointage.",
                    "Compromis assumé : L'exécution locale hors-ligne utilise l'horloge matérielle monotone (SystemClock.elapsedRealtime) corrélée au serveur et marque l'enregistrement 'OFFLINE_TO_CONFIRM' jusqu'à validation réseau."
                )
            )
        }

        item {
            ArchitectureSpecCard(
                number = "3",
                title = "Hiérarchie Multi-Entreprises & Schéma Relationnel (13 Tables)",
                bullets = listOf(
                    "Hiérarchie : Entreprise (companies) ➔ Site/Établissement (sites) ➔ Département (departments) ➔ Employé (employees) ➔ Pointages (attendance_records).",
                    "Séparation Identité / Biométrie : La table 'employees' stocke nom, prénom et matricule. La table 'biometric_enrollments' stocke uniquement les vecteurs mathématiques chiffrés AES-256-GCM liés à un consentement ('consents').",
                    "Tables d'Administration & Conformité : 'schedules', 'leaves', 'devices', 'users' (RBAC 6 rôles), 'notifications' et 'audit_logs' (chaînage cryptographique SHA-256 immuable)."
                )
            )
        }

        item {
            ArchitectureSpecCard(
                number = "4",
                title = "Réalisabilité Biométrique par Plateforme & Détection du Vivant",
                bullets = listOf(
                    "Android / iOS / WebAuthn : Utilisation des authentificateurs de plateforme (BiometricPrompt Class 3, Touch ID / Face ID, Passkeys FIDO2). L'application ne reçoit jamais l'image de l'empreinte : uniquement une preuve cryptographique.",
                    "Reconnaissance Faciale + Liveness : Analyse du vivant obligatoire (clignement des yeux, rotation de tête, rejet des photos/écrans avec seuil >= 80%) avant extraction d'un embedding mathématique chiffré.",
                    "Borne Dédiée Externe : Interface 'BiometricProvider' découplée permettant de brancher un lecteur USB optique/capacitif (SecuGen, Suprema, ZKTeco) ou d'utiliser le code PIN de secours validé."
                )
            )
        }

        item {
            ArchitectureSpecCard(
                number = "5",
                title = "Conformité Légale CDP (Sénégal) & RGPD Européen",
                bullets = listOf(
                    "Consentement explicite obligatoire avant tout enrôlement biométrique avec mention de la finalité et de la durée de conservation.",
                    "Alternative non-biométrique garantie (Code PIN personnel haché) pour tout salarié qui refuse la biométrie.",
                    "Droit à l'effacement en 1 clic : purge immédiate des gabarits dans 'biometric_enrollments' au départ du salarié ou sur demande, avec trace dans les Audit Logs."
                )
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.48f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.52f)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
}

@Composable
private fun ArchitectureSpecCard(
    number: String,
    title: String,
    bullets: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            bullets.forEach { b ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = b, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
