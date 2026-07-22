package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ReportEntity
import com.example.data.local.UserEntity
import com.example.ui.theme.AuraPink
import com.example.ui.theme.VerifiedBlue

@Composable
fun AdminDashboardScreen(
    users: List<UserEntity>,
    reports: List<ReportEntity>,
    onBackClick: () -> Unit,
    onToggleVerify: (String) -> Unit,
    onUpdateReportStatus: (ReportEntity, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Overview, 1: Users, 2: Reports Queue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("admin_dashboard_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Admin Control Center",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = AuraPink
                )
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("User Moderation") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Reports Queue") })
        }

        if (selectedTab == 0) {
            // Metrics Overview
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Platform Statistics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(title = "Total Users", value = "${users.size}", modifier = Modifier.weight(1f))
                    MetricCard(title = "Verified Accounts", value = "${users.count { it.isVerified }}", modifier = Modifier.weight(1f))
                    MetricCard(title = "Pending Reports", value = "${reports.count { it.status == "Pending" }}", modifier = Modifier.weight(1f))
                }
            }
        } else if (selectedTab == 1) {
            // User Moderation List
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "All Registered Users",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                users.forEach { user ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = user.username, fontWeight = FontWeight.Bold)
                                    if (user.isVerified) {
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = VerifiedBlue,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .size(16.dp)
                                        )
                                    }
                                }
                                Text(text = user.fullName, fontSize = 12.sp, color = Color.Gray)
                            }

                            Button(
                                onClick = { onToggleVerify(user.username) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (user.isVerified) Color.Gray else VerifiedBlue
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (user.isVerified) "Unverify" else "Grant Verification",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Moderation Reports Queue
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Content Safety & Moderation Reports",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (reports.isEmpty()) {
                    Text("No reports pending review.", color = Color.Gray)
                } else {
                    reports.forEach { r ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Report Type: ${r.contentType} | Reason: ${r.reason}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Reporter: @${r.reporterUsername} | Status: ${r.status}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onUpdateReportStatus(r, "Dismissed") },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Dismiss Report", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { onUpdateReportStatus(r, "Action Taken") },
                                        colors = ButtonDefaults.buttonColors(containerColor = AuraPink),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Take Action / Remove", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AuraPink)
            Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}
