package com.vesc0.heartratemonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vesc0.heartratemonitor.data.local.PreferencesManager
import com.vesc0.heartratemonitor.ui.theme.buttonTextColor
import com.vesc0.heartratemonitor.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(auth: AuthViewModel) {
    val isSignedIn by auth.isSignedIn.collectAsState()
    val email by auth.currentEmail.collectAsState()
    val name by auth.name.collectAsState()
    val age by auth.age.collectAsState()
    val healthIssues by auth.healthIssues.collectAsState()
    val gender by auth.gender.collectAsState()
    val heightCm by auth.heightCm.collectAsState()
    val weightKg by auth.weightKg.collectAsState()

    var unitSystem by remember {
        mutableStateOf(ProfileUnitSystem.fromRaw(PreferencesManager.profileUnitSystem))
    }

    var showAuthSheet by remember { mutableStateOf(false) }
    var selectedAuthTab by remember { mutableIntStateOf(0) }
    var editingField by remember { mutableStateOf<EditableField?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }

    // Dismiss sheet when signed in
    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            showAuthSheet = false
            profileError = null
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) }
    ) { padding ->
        if (isSignedIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                SignedInContent(
                    name = name,
                    email = email,
                    age = age,
                    healthIssues = healthIssues,
                    gender = gender,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    unitSystem = unitSystem,
                    onUnitSystemChange = {
                        unitSystem = it
                        PreferencesManager.profileUnitSystem = it.raw
                    },
                    profileError = profileError,
                    onEdit = { editingField = it },
                    onSignOut = { auth.signOut() }
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                SignedOutContent(onShowAuth = {
                    selectedAuthTab = 0
                    showAuthSheet = true
                })
            }
        }
    }

    // Auth bottom sheet
    if (showAuthSheet) {
        val authSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAuthSheet = false },
            sheetState = authSheetState
        ) {
            AuthSheetContent(
                auth = auth,
                selectedTab = selectedAuthTab,
                onTabChange = { selectedAuthTab = it }
            )
        }
    }

    // Edit field dialog
    editingField?.let { field ->
        val currentValue = when (field) {
            EditableField.NAME -> name ?: ""
            EditableField.EMAIL -> email ?: ""
            EditableField.AGE -> age ?: ""
            EditableField.GENDER -> gender ?: ""
            EditableField.HEIGHT -> heightCm ?: ""
            EditableField.WEIGHT -> weightKg ?: ""
            EditableField.HEALTH -> healthIssues ?: ""
        }
        EditFieldDialog(
            field = field,
            initialValue = currentValue,
            onSave = { newValue ->
                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                scope.launch {
                    try {
                        when (field) {
                            EditableField.NAME -> auth.updateProfile(name = newValue)
                            EditableField.EMAIL -> auth.updateProfile(email = newValue)
                            EditableField.AGE -> auth.updateProfile(age = newValue.toIntOrNull())
                            EditableField.GENDER -> auth.updateProfile(gender = newValue.lowercase())
                            EditableField.HEIGHT -> auth.updateProfile(heightCm = newValue.toIntOrNull())
                            EditableField.WEIGHT -> auth.updateProfile(weightKg = newValue.toIntOrNull())
                            EditableField.HEALTH -> auth.updateProfile(healthIssues = newValue)
                        }
                        profileError = null
                    } catch (e: Exception) {
                        profileError = e.message
                    }
                }
                editingField = null
            },
            onDismiss = { editingField = null }
        )
    }
}

// ───────────────────────── Signed Out ─────────────────────────

@Composable
private fun SignedOutContent(onShowAuth: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Welcome", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Log in to sync your measurements,\nview stats, and manage your profile.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onShowAuth,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Gradient background hack
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Red
                ) {}
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log In or Sign Up", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ───────────────────────── Signed In ──────────────────────────

@Composable
private fun SignedInContent(
    name: String?,
    email: String?,
    age: String?,
    healthIssues: String?,
    gender: String?,
    heightCm: String?,
    weightKg: String?,
    unitSystem: ProfileUnitSystem,
    onUnitSystemChange: (ProfileUnitSystem) -> Unit,
    profileError: String?,
    onEdit: (EditableField) -> Unit,
    onSignOut: () -> Unit
) {
    // Header card
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Surface(
                modifier = Modifier.size(86.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                if (name.isNullOrEmpty()) "Your Name" else name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(email ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Details section
    Text(
        "DETAILS",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    )

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        SegmentedButton(
            selected = unitSystem == ProfileUnitSystem.METRIC,
            onClick = { onUnitSystemChange(ProfileUnitSystem.METRIC) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = Color.Red,
                activeContentColor = Color.White
            ),
            icon = {}
        ) {
            Text("Metric")
        }
        SegmentedButton(
            selected = unitSystem == ProfileUnitSystem.IMPERIAL,
            onClick = { onUnitSystemChange(ProfileUnitSystem.IMPERIAL) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = Color.Red,
                activeContentColor = Color.White
            ),
            icon = {}
        ) {
            Text("Imperial")
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column {
            ProfileRow(Icons.Filled.Person, "Name", name ?: "", onClick = { onEdit(EditableField.NAME) })
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            ProfileRow(Icons.Filled.Email, "Email", email ?: "", onClick = { onEdit(EditableField.EMAIL) })
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            ProfileRow(Icons.Filled.CalendarToday, "Age", age ?: "", onClick = { onEdit(EditableField.AGE) })
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            ProfileRow(Icons.Filled.Person, "Gender", gender ?: "", onClick = { onEdit(EditableField.GENDER) })
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            ProfileRow(
                Icons.Filled.Straighten,
                "Height",
                displayedHeight(heightCm, unitSystem),
                onClick = { onEdit(EditableField.HEIGHT) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            ProfileRow(
                Icons.Filled.FitnessCenter,
                "Weight",
                displayedWeight(weightKg, unitSystem),
                onClick = { onEdit(EditableField.WEIGHT) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            ProfileRow(Icons.Filled.HealthAndSafety, "Health", healthIssues ?: "", onClick = { onEdit(EditableField.HEALTH) })
        }
    }

    if (profileError != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(profileError!!, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Sign out
    OutlinedButton(
        onClick = onSignOut,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonTextColor()),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sign Out", fontWeight = FontWeight.SemiBold)
    }

    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (value.isEmpty()) "Not set" else value,
                    color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ───────────────────────── Auth Sheet ─────────────────────────

@Composable
private fun AuthSheetContent(
    auth: AuthViewModel,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(selectedTab) }

    Column(
        modifier = Modifier
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0; onTabChange(0) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color.Red,
                    activeContentColor = Color.White
                ),
                icon = {}
            ) { Text("Log In") }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1; onTabChange(1) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color.Red,
                    activeContentColor = Color.White
                ),
                icon = {}
            ) { Text("Sign Up") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tab == 0) LoginContent(auth) else SignUpContent(auth)
    }
}

@Composable
private fun LoginContent(auth: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val isLoginDisabled = isLoading || email.trim().isEmpty() || password.trim().isEmpty()
    val scope = rememberCoroutineScope()

    Column {
        Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Log in to continue tracking your heart health.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (errorText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorText!!, color = Color.Red, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    errorText = null; isLoading = true
                    try { auth.signIn(email.trim(), password) }
                    catch (e: Exception) { errorText = e.message ?: "Login failed." }
                    isLoading = false
                }
            },
            enabled = !isLoginDisabled,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else {
                Icon(Icons.Filled.ArrowCircleRight, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Log In", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SignUpContent(auth: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val passwordsMatch = password.isNotEmpty() && password == confirm
    val canSubmit = passwordsMatch && email.contains("@") && !isLoading

    Column {
        Text("Create an Account", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Sign up to sync and back up your measurements.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (min 8, Aa1)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm Password") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (!passwordsMatch && confirm.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Passwords do not match.", color = Color.Red, fontSize = 13.sp)
        }
        if (errorText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorText!!, color = Color.Red, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    errorText = null; isLoading = true
                    try { auth.signUp(email.trim(), password) }
                    catch (e: Exception) { errorText = e.message ?: "Sign up failed." }
                    isLoading = false
                }
            },
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Create an Account", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ───────────────────── Edit field dialog ──────────────────────

private enum class EditableField { NAME, EMAIL, AGE, GENDER, HEIGHT, WEIGHT, HEALTH }

private enum class ProfileUnitSystem(val raw: String) {
    METRIC("metric"),
    IMPERIAL("imperial");

    companion object {
        fun fromRaw(value: String): ProfileUnitSystem {
            return entries.firstOrNull { it.raw == value } ?: METRIC
        }
    }
}

private fun displayedHeight(heightCm: String?, unitSystem: ProfileUnitSystem): String {
    val cm = heightCm?.toIntOrNull() ?: return ""
    return if (unitSystem == ProfileUnitSystem.METRIC) {
        "$cm cm"
    } else {
        val totalInches = (cm / 2.54).toInt()
        val feet = totalInches / 12
        val inches = totalInches % 12
        "$feet ft $inches in"
    }
}

private fun displayedWeight(weightKg: String?, unitSystem: ProfileUnitSystem): String {
    val kg = weightKg?.toIntOrNull() ?: return ""
    return if (unitSystem == ProfileUnitSystem.METRIC) {
        "$kg kg"
    } else {
        val lb = (kg * 2.2046226218).toInt()
        "$lb lb"
    }
}

@Composable
private fun EditFieldDialog(
    field: EditableField,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initialValue) }
    val title = when (field) {
        EditableField.NAME -> "Name"
        EditableField.EMAIL -> "Email"
        EditableField.AGE -> "Age"
        EditableField.GENDER -> "Gender"
        EditableField.HEIGHT -> "Height (cm)"
        EditableField.WEIGHT -> "Weight (kg)"
        EditableField.HEALTH -> "Health Issues"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $title") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = when (field) {
                    EditableField.AGE, EditableField.HEIGHT, EditableField.WEIGHT ->
                        KeyboardOptions(keyboardType = KeyboardType.Number)
                    EditableField.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
                    else -> KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                colors = ButtonDefaults.textButtonColors(contentColor = buttonTextColor())
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = buttonTextColor())
            ) { Text("Cancel") }
        }
    )
}
