package com.example.ui.screens.planner

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.database.entities.StudyTaskEntity
import com.example.data.notifications.StudyNotificationHelper
import com.example.ui.localization.LocalAppStrings
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

enum class TimetableFilter {
    ALL, TODAY, UPCOMING, COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    viewModel: MainViewModel
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val allTasks by viewModel.allTasks.collectAsState()

    var selectedFilter by remember { mutableStateOf(TimetableFilter.ALL) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<StudyTaskEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<StudyTaskEntity?>(null) }

    // Live current device date that updates dynamically
    var currentDate by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentDate = Date()
            delay(30000L) // Refresh every 30 seconds
        }
    }

    val todayStr = remember(currentDate) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentDate)
    }

    val formattedDeviceHeaderDate = remember(currentDate) {
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(currentDate)
    }

    val formattedBadgeDate = remember(currentDate) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(currentDate)
    }

    // Request notification permission for timetable reminders on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!StudyNotificationHelper.hasNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val filteredTasks = remember(allTasks, selectedFilter, todayStr) {
        when (selectedFilter) {
            TimetableFilter.ALL -> allTasks.sortedWith(compareBy({ it.isCompleted }, { it.dueDate }, { it.dueTime }))
            TimetableFilter.TODAY -> allTasks.filter { it.dueDate == todayStr }.sortedWith(compareBy({ it.isCompleted }, { it.dueTime }))
            TimetableFilter.UPCOMING -> allTasks.filter { !it.isCompleted && it.dueDate >= todayStr }.sortedWith(compareBy({ it.dueDate }, { it.dueTime }))
            TimetableFilter.COMPLETED -> allTasks.filter { it.isCompleted }.sortedByDescending { it.dueDate }
        }
    }

    val completedCount = allTasks.count { it.isCompleted }
    val totalCount = allTasks.size
    val progressPercent = if (totalCount > 0) (completedCount * 100) / totalCount else 0

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.timetableTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formattedDeviceHeaderDate,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formattedBadgeDate,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !StudyNotificationHelper.hasNotificationPermission(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    editingTask = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = strings.addTask)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Overall Goal Progress Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.completionProgress,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$progressPercent%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$completedCount / $totalCount ${strings.tasksCompleted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Filter Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == TimetableFilter.ALL,
                        onClick = { selectedFilter = TimetableFilter.ALL },
                        label = { Text("${strings.filterAll} (${allTasks.size})") },
                        leadingIcon = if (selectedFilter == TimetableFilter.ALL) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                item {
                    val todayCount = allTasks.count { it.dueDate == todayStr }
                    FilterChip(
                        selected = selectedFilter == TimetableFilter.TODAY,
                        onClick = { selectedFilter = TimetableFilter.TODAY },
                        label = { Text("${strings.filterToday} ($todayCount)") },
                        leadingIcon = if (selectedFilter == TimetableFilter.TODAY) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                item {
                    val upcomingCount = allTasks.count { !it.isCompleted && it.dueDate >= todayStr }
                    FilterChip(
                        selected = selectedFilter == TimetableFilter.UPCOMING,
                        onClick = { selectedFilter = TimetableFilter.UPCOMING },
                        label = { Text("${strings.filterUpcoming} ($upcomingCount)") },
                        leadingIcon = if (selectedFilter == TimetableFilter.UPCOMING) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == TimetableFilter.COMPLETED,
                        onClick = { selectedFilter = TimetableFilter.COMPLETED },
                        label = { Text("${strings.filterCompleted} ($completedCount)") },
                        leadingIcon = if (selectedFilter == TimetableFilter.COMPLETED) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Tasks List
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.noTasksTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.noTasksSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        val isOverdue = !task.isCompleted && task.dueDate.isNotBlank() && task.dueDate < todayStr
                        TimetableTaskCard(
                            task = task,
                            isOverdue = isOverdue,
                            onToggleComplete = {
                                viewModel.toggleTaskCompleted(task)
                                if (!task.isCompleted) {
                                    android.widget.Toast.makeText(context, strings.taskCompletedToast, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEdit = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !StudyNotificationHelper.hasNotificationPermission(context)) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                editingTask = task
                                showAddEditDialog = true
                            },
                            onDelete = { taskToDelete = task }
                        )
                    }
                }
            }
        }

        // Add / Edit Task Dialog
        if (showAddEditDialog) {
            AddEditTaskDialog(
                task = editingTask,
                onDismiss = { showAddEditDialog = false },
                onSave = { title, subject, date, time, priority ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !StudyNotificationHelper.hasNotificationPermission(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (editingTask != null) {
                        viewModel.updateTask(
                            editingTask!!.copy(
                                title = title,
                                subject = subject,
                                dueDate = date,
                                dueTime = time,
                                priority = priority
                            )
                        )
                    } else {
                        viewModel.addTask(
                            title = title,
                            subject = subject,
                            dueDate = date,
                            dueTime = time,
                            priority = priority
                        )
                    }
                    showAddEditDialog = false
                    android.widget.Toast.makeText(context, strings.taskSavedToast, android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Delete Confirmation Dialog
        if (taskToDelete != null) {
            AlertDialog(
                onDismissRequest = { taskToDelete = null },
                title = { Text(strings.deleteTaskDialogTitle, fontWeight = FontWeight.Bold) },
                text = { Text(strings.deleteTaskDialogMsg) },
                confirmButton = {
                    Button(
                        onClick = {
                            taskToDelete?.let { viewModel.deleteTask(it.id) }
                            taskToDelete = null
                            android.widget.Toast.makeText(context, strings.taskDeletedToast, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.delete)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { taskToDelete = null }) {
                        Text(strings.cancel)
                    }
                }
            )
        }
    }
}

@Composable
fun TimetableTaskCard(
    task: StudyTaskEntity,
    isOverdue: Boolean,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current

    val priorityColor = when (task.priority.lowercase().trim()) {
        "high", "উচ্চ", "उच्च", "ಹೆಚ್ಚು", "ഉയർന്ന", "அதிக" -> MaterialTheme.colorScheme.error
        "low", "কম", "कमी", "ಕಡಿಮೆ", "കുറഞ്ഞ", "குறைந்த" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else if (isOverdue) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) strings.markIncomplete else strings.markCompleted,
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Subject Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = task.subject.ifBlank { "Study" },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Priority Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = priorityColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = task.priority,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isOverdue) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "⚠️ Overdue",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (task.dueDate.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = task.dueDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (task.dueTime.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = task.dueTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = strings.editTask,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = strings.delete,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddEditTaskDialog(
    task: StudyTaskEntity?,
    onDismiss: () -> Unit,
    onSave: (title: String, subject: String, date: String, time: String, priority: String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    val calendar = remember { Calendar.getInstance() }
    val defaultToday = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    var titleInput by remember { mutableStateOf(task?.title ?: "") }
    var subjectInput by remember { mutableStateOf(task?.subject ?: "") }
    var dateInput by remember { mutableStateOf(task?.dueDate?.ifBlank { defaultToday } ?: defaultToday) }
    var timeInput by remember { mutableStateOf(task?.dueTime?.ifBlank { "10:00 AM" } ?: "10:00 AM") }
    var priorityInput by remember { mutableStateOf(task?.priority?.ifBlank { strings.priorityMedium } ?: strings.priorityMedium) }

    val datePickerDialog = remember {
        val nowCal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                dateInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            },
            nowCal.get(Calendar.YEAR),
            nowCal.get(Calendar.MONTH),
            nowCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember {
        val nowCal = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val isPm = hourOfDay >= 12
                val hour12 = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                val amPm = if (isPm) "PM" else "AM"
                timeInput = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm)
            },
            nowCal.get(Calendar.HOUR_OF_DAY),
            nowCal.get(Calendar.MINUTE),
            false
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (task != null) strings.editTask else strings.addTask,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(strings.taskTitle) },
                    placeholder = { Text(strings.taskTitlePlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Subject
                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = { subjectInput = it },
                    label = { Text(strings.subjectLabel) },
                    placeholder = { Text(strings.timetableSubjectPlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Date & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text(strings.dateLabel) },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date", modifier = Modifier.size(20.dp))
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text(strings.timeLabel) },
                        placeholder = { Text(strings.timePlaceholder) },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { timePickerDialog.show() }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Select Time", modifier = Modifier.size(20.dp))
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Priority Selector
                Text(
                    text = strings.priorityLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(strings.priorityLow, strings.priorityMedium, strings.priorityHigh).forEach { p ->
                        FilterChip(
                            selected = priorityInput.equals(p, ignoreCase = true),
                            onClick = { priorityInput = p },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        titleInput.trim(),
                        subjectInput.trim().ifBlank { "General" },
                        dateInput.trim().ifBlank { defaultToday },
                        timeInput.trim().ifBlank { "10:00 AM" },
                        priorityInput
                    )
                },
                enabled = titleInput.isNotBlank()
            ) {
                Text(strings.saveTask)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
