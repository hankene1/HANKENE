package com.example.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.BiometricEnrollmentEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.SiteEntity
import com.example.domain.model.BiometricModality
import com.example.ui.KioskConfirmationBanner

@Composable
fun KioskTerminalScreen(
    companyName: String,
    sites: List<SiteEntity>,
    employees: List<EmployeeEntity>,
    enrollments: List<BiometricEnrollmentEntity>,
    serverDate: String,
    serverClock: String,
    isOnline: Boolean,
    kioskConfirmation: KioskConfirmationBanner?,
    onClearConfirmation: () -> Unit,
    onExecutePunch: (
        employeeId: String,
        method: BiometricModality,
        isPunchOut: Boolean,
        livenessScore: Float,
        candidatePin: String?,
        simulateOutsideGeofence: Boolean
    ) -> Unit,
    onRequestHardwareBiometricPrompt: (onAuthenticated: () -> Unit) -> Unit
) {
    var selectedEmployeeId by remember(employees) {
        mutableStateOf(employees.firstOrNull()?.id ?: "")
    }
    var selectedModality by remember { mutableStateOf(BiometricModality.FINGERPRINT) }
    var isPunchOut by remember { mutableStateOf(false) }
    var livenessBlinkVerified by remember { mutableStateOf(true) }
    var livenessHeadTurnVerified by remember { mutableStateOf(true) }
    var simulatePhotoSpoofAttack by remember { mutableStateOf(false) }
    var simulateGeofenceViolation by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    val selectedEmployee = employees.find { it.id == selectedEmployeeId } ?: employees.firstOrNull()
    val employeeEnrollments = enrollments.filter { it.employeeId == selectedEmployee?.id }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        // Bannière Hero Visuelle + Horloge Serveur Temps Réel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_kiosk_hero_1791158323271),
                        contentDescription = "Borne Biométrique Entreprise",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp)
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = companyName.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = sites.firstOrNull()?.name ?: "Borne Kiosque Principale",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(if (isOnline) "SERVEUR SYNCHRONISÉ" else "MODE HORS-LIGNE CHIFFRÉ")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "HORODATAGE SERVEUR CERTIFIÉ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = serverClock,
                                        fontSize = 30.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Date : $serverDate",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Confirmation visuelle et sonore immédiate (< 3 secondes)
        item {
            AnimatedVisibility(visible = kioskConfirmation != null) {
                kioskConfirmation?.let { conf ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("kiosk_confirmation_card"),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Pointage confirmé",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (conf.isDeparture) "DÉPART ENREGISTRÉ AVEC SUCCÈS" else "ARRIVÉE ENREGISTRÉE AVEC SUCCÈS",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "${conf.employeeFirstName} ${conf.employeeLastName} (${conf.employeeMatricule})",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                IconButton(onClick = onClearConfirmation) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Date : ${conf.dateIso}  |  Heure officielle : ${conf.serverTimeHms}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Statut : ${conf.statusLabel}  |  Méthode : ${conf.methodLabel}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "État de synchronisation : ${conf.syncStateLabel}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Sélection Arrivée / Départ
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = !isPunchOut,
                    onClick = { isPunchOut = false },
                    label = { Text("POINTER MON ARRIVÉE", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Login, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("chip_punch_in")
                )
                FilterChip(
                    selected = isPunchOut,
                    onClick = { isPunchOut = true },
                    label = { Text("POINTER MON DÉPART", fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("chip_punch_out")
                )
            }
        }

        // Sélection de l'employé qui se présente devant la borne
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Identification de l'Employé sur la Borne",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    employees.forEach { emp ->
                        val isSelected = emp.id == selectedEmployee?.id
                        val empBioCount = enrollments.count { it.employeeId == emp.id }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedEmployeeId = emp.id }
                                .testTag("kiosk_emp_${emp.matricule}"),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = if (isSelected) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${emp.firstName.take(1)}${emp.lastName.take(1)}",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "${emp.firstName} ${emp.lastName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Matricule : ${emp.matricule} • ${emp.jobTitle}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Badge(
                                    containerColor = if (empBioCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                ) {
                                    Text(
                                        text = "$empBioCount Biométrie(s)",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sélection de la Méthode Biométrique (Empreinte, Face ID + Vivant, ou Code PIN de secours)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Méthode de Vérification Biométrique",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedModality == BiometricModality.FINGERPRINT,
                            onClick = { selectedModality = BiometricModality.FINGERPRINT },
                            label = { Text("Empreinte") },
                            leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                            modifier = Modifier.weight(1f).testTag("modality_fingerprint")
                        )
                        FilterChip(
                            selected = selectedModality == BiometricModality.FACE_ID,
                            onClick = { selectedModality = BiometricModality.FACE_ID },
                            label = { Text("Visage + Vivant") },
                            leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                            modifier = Modifier.weight(1f).testTag("modality_face")
                        )
                        FilterChip(
                            selected = selectedModality == BiometricModality.FALLBACK_PIN,
                            onClick = { selectedModality = BiometricModality.FALLBACK_PIN },
                            label = { Text("Code PIN") },
                            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                            modifier = Modifier.weight(1f).testTag("modality_pin")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedModality) {
                        BiometricModality.FINGERPRINT, BiometricModality.HARDWARE_SCANNER -> {
                            Text(
                                text = "Authentification via capteur d'empreinte Android TEE (BiometricPrompt) et vérification du gabarit mathématique chiffré AES-256-GCM.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        BiometricModality.FACE_ID -> {
                            Text(
                                text = "Contrôle de Détection du Vivant (Anti-Spoofing : rejet des photos, vidéos et écrans présentés devant la caméra) :",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = livenessBlinkVerified && !simulatePhotoSpoofAttack,
                                    onCheckedChange = {
                                        livenessBlinkVerified = it
                                        if (it) simulatePhotoSpoofAttack = false
                                    }
                                )
                                Text("Clignement des yeux & micro-mouvement 3D détectés", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = simulatePhotoSpoofAttack,
                                    onCheckedChange = { simulatePhotoSpoofAttack = it }
                                )
                                Text(
                                    "Tester l'Anti-Fraude : présenter une photo/écran (doit être refusé)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        BiometricModality.FALLBACK_PIN -> {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { pinInput = it },
                                label = { Text("Code PIN de secours (ex: 2468 pour Cheikh, 1357 pour Aminata)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_fallback_pin")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = simulateGeofenceViolation,
                            onCheckedChange = { simulateGeofenceViolation = it }
                        )
                        Text(
                            "Tester le Geofencing : simuler une position GPS hors du périmètre du site",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Gros bouton de pointage (< 3 secondes) avec retour sonore
                    Button(
                        onClick = {
                            val emp = selectedEmployee ?: return@Button
                            val computedLiveness = if (simulatePhotoSpoofAttack || !livenessBlinkVerified) 0.32f else 0.98f
                            val triggerPunch = {
                                runCatching {
                                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                                        .startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                                }
                                onExecutePunch(
                                    emp.id,
                                    selectedModality,
                                    isPunchOut,
                                    computedLiveness,
                                    pinInput,
                                    simulateGeofenceViolation
                                )
                            }
                            if (selectedModality == BiometricModality.FINGERPRINT) {
                                onRequestHardwareBiometricPrompt { triggerPunch() }
                            } else {
                                triggerPunch()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("btn_validate_biometric_punch"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = when (selectedModality) {
                                BiometricModality.FINGERPRINT -> Icons.Default.Fingerprint
                                BiometricModality.FACE_ID -> Icons.Default.CenterFocusStrong
                                BiometricModality.FALLBACK_PIN -> Icons.Default.LockOpen
                                BiometricModality.HARDWARE_SCANNER -> Icons.Default.Usb
                            },
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isPunchOut) {
                                "VALIDER LE DÉPART (${selectedEmployee?.firstName ?: ""})"
                            } else {
                                "VALIDER L'ARRIVÉE (${selectedEmployee?.firstName ?: ""})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
