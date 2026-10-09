package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.BiometricEnrollmentEntity
import com.example.data.local.EmployeeEntity

@Composable
fun EmployeeSelfServiceScreen(
    employees: List<EmployeeEntity>,
    enrollments: List<BiometricEnrollmentEntity>,
    attendanceRecords: List<AttendanceRecordEntity>,
    onGoToKioskPunch: () -> Unit
) {
    var selectedEmployeeId by remember(employees) {
        mutableStateOf(employees.firstOrNull()?.id ?: "")
    }
    val currentEmployee = employees.find { it.id == selectedEmployeeId } ?: employees.firstOrNull()
    val myRecords = attendanceRecords.filter { it.employeeId == currentEmployee?.id }
    val myEnrollments = enrollments.filter { it.employeeId == currentEmployee?.id }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                employees.forEach { emp ->
                    FilterChip(
                        selected = emp.id == currentEmployee?.id,
                        onClick = { selectedEmployeeId = emp.id },
                        label = { Text("${emp.firstName} (${emp.matricule})") }
                    )
                }
            }
        }

        currentEmployee?.let { emp ->
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${emp.firstName} ${emp.lastName} — ${emp.matricule}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Poste : ${emp.jobTitle} • Email : ${emp.email}")
                        Text(
                            text = "Méthodes biométriques enrôlées : ${myEnrollments.size} (${myEnrollments.joinToString { it.modality }.ifBlank { "PIN de secours uniquement" }})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onGoToKioskPunch,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pointer mon Arrivée / Départ")
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mon Historique Personnel de Pointages (${myRecords.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (myRecords.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Aucun pointage enregistré pour cet employé aujourd'hui.")
                    }
                }
            }
        } else {
            items(myRecords, key = { it.id }) { rec ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Date : ${rec.dateIso}",
                                fontWeight = FontWeight.Bold
                            )
                            Badge { Text(rec.status) }
                        }
                        Text(
                            text = "Arrivée (Serveur) : ${rec.arrivalServerTimeHms} • Départ : ${rec.departureServerTimeHms ?: "En cours"}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Site : ${rec.siteName} • Méthode : ${rec.methodUsed}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
