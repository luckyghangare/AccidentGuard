package com.accidentguard.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.accidentguard.data.model.EmergencyContact
import com.accidentguard.ui.components.BottomNavigationBar
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.ContactViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(
    navController: NavController,
    viewModel: ContactViewModel
) {
    val state by viewModel.uiState.collectAsState()

    // Add dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newRel by remember { mutableStateOf("Family") }
    var newIsPrimary by remember { mutableStateOf(false) }

    // Edit dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editRel by remember { mutableStateOf("") }
    var editIsPrimary by remember { mutableStateOf(false) }

    // Show error/success snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Contacts", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = { BottomNavigationBar(navController = navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight)
        ) {
            Text(
                text = "Manage the contacts who should be notified during an emergency.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // ── RECEIVED INVITATIONS SECTION ──
                    if (state.receivedInvitations.isNotEmpty()) {
                        item {
                            Text(
                                "Pending Invitations",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(state.receivedInvitations) { invitation ->
                            InvitationItem(
                                invitation = invitation,
                                onAccept = { viewModel.acceptInvitation(invitation.id) },
                                onReject = { viewModel.rejectInvitation(invitation.id) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        item {
                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = DividerGrey)
                        }
                    }

                    // ── MY CONTACTS SECTION ──
                    if (state.contacts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No emergency contacts yet", color = TextSecondary, fontSize = 15.sp)
                                    Text("Add a contact below", color = TextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "My Emergency Contacts",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(state.contacts) { contact ->
                            ContactCard(
                                contact = contact,
                                onEdit = {
                                    editContact = contact
                                    editName = contact.name
                                    editPhone = contact.phoneNumber
                                    editRel = contact.relationshipType
                                    editIsPrimary = contact.isPrimary
                                    showEditDialog = true
                                },
                                onDelete = { viewModel.deleteContact(contact.id) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Emergency Contact", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── ADD CONTACT DIALOG ──
    if (showAddDialog) {
        ContactDialog(
            title = "Add Emergency Contact",
            name = newName, onNameChange = { newName = it },
            phone = newPhone, onPhoneChange = { newPhone = it },
            relationship = newRel, onRelChange = { newRel = it },
            isPrimary = newIsPrimary, onIsPrimaryChange = { newIsPrimary = it },
            confirmLabel = "Add Contact",
            onConfirm = {
                if (newName.isNotBlank() && newPhone.isNotBlank()) {
                    viewModel.addContact(newName, newPhone, newRel, newIsPrimary)
                    newName = ""; newPhone = ""; newRel = "Family"; newIsPrimary = false
                    showAddDialog = false
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // ── EDIT CONTACT DIALOG ──
    if (showEditDialog && editContact != null) {
        ContactDialog(
            title = "Edit Contact",
            name = editName, onNameChange = { editName = it },
            phone = editPhone, onPhoneChange = { editPhone = it },
            relationship = editRel, onRelChange = { editRel = it },
            isPrimary = editIsPrimary, onIsPrimaryChange = { editIsPrimary = it },
            confirmLabel = "Save Changes",
            onConfirm = {
                if (editName.isNotBlank() && editPhone.isNotBlank()) {
                    viewModel.updateContact(editContact!!.id, editName, editPhone, editRel, editIsPrimary)
                    showEditDialog = false
                    editContact = null
                }
            },
            onDismiss = { showEditDialog = false; editContact = null }
        )
    }
}

@Composable
fun ContactDialog(
    title: String,
    name: String, onNameChange: (String) -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    relationship: String, onRelChange: (String) -> Unit,
    isPrimary: Boolean, onIsPrimaryChange: (Boolean) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    label = { Text("Full Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone, onValueChange = onPhoneChange,
                    label = { Text("Phone Number") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = relationship, onValueChange = onRelChange,
                    label = { Text("Relationship (e.g. Family, Friend)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isPrimary, onCheckedChange = onIsPrimaryChange,
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                    )
                    Text("Set as Primary Contact", fontSize = 14.sp, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun InvitationItem(
    invitation: EmergencyContact,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), // Light orange bg
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECEIVED INVITATION",
                    fontSize = 11.sp, color = WarningOrange, fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${invitation.name} wants to add you as their emergency contact.",
                fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Phone: ${invitation.phoneNumber}", fontSize = 13.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Decline", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Accept", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ContactCard(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val relationshipCategory = when (contact.relationshipType.lowercase()) {
        "brother", "sister", "mother", "father", "family" -> "Family Contact"
        "friend" -> "Friend Contact"
        "services" -> "Emergency Services"
        else -> "${contact.relationshipType} Contact"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF3FB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = relationshipCategory,
                    fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when {
                        contact.status == "PENDING" -> WarningOrange.copy(alpha = 0.1f)
                        contact.isPrimary -> PrimaryBlue.copy(alpha = 0.1f)
                        else -> Color.Transparent
                    }
                ) {
                    Text(
                        text = when {
                            contact.status == "PENDING" -> "PENDING CONSENT"
                            contact.isPrimary -> "PRIMARY"
                            else -> "SECONDARY"
                        },
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = when {
                            contact.status == "PENDING" -> WarningOrange
                            contact.isPrimary -> PrimaryBlue
                            else -> TextSecondary
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = contact.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = contact.relationshipType, fontSize = 13.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = contact.phoneNumber, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)

            if (contact.status == "PENDING") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Waiting for user to accept your invitation...",
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (contact.status != "PENDING") {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        if (contact.status == "PENDING") Icons.Default.Cancel else Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DangerRed
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (contact.status == "PENDING") "Cancel" else "Delete",
                        color = DangerRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Contact?", fontWeight = FontWeight.Bold) },
            text = { Text("Remove ${contact.name} from emergency contacts?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
