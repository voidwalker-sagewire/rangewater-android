package com.sagewire.rangewater.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sagewire.rangewater.data.FieldRecordEntity
import com.sagewire.rangewater.data.FieldRecordType
import com.sagewire.rangewater.data.FieldTaskStatus
import com.sagewire.rangewater.data.PastureWithVertices
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun FieldRecordType.displayName(): String = name.lowercase()
    .replaceFirstChar { it.titlecase(Locale.US) }

@Composable
internal fun FieldRecordEditorDialog(
    coordinate: Pair<Double, Double>,
    accuracyMeters: Float?,
    pastures: List<PastureWithVertices>,
    suggestedPastureId: Long?,
    editing: FieldRecordEntity?,
    onPlaceOnMap: () -> Unit,
    onUseMyLocation: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (FieldRecordType, String, Long?, FieldTaskStatus, Long) -> Unit
) {
    var type by remember(editing?.id) { mutableStateOf(editing?.recordType ?: FieldRecordType.NOTE) }
    var note by remember(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    var pastureId by remember(editing?.id, suggestedPastureId) {
        mutableStateOf(editing?.pastureId ?: suggestedPastureId)
    }
    var taskStatus by remember(editing?.id) {
        mutableStateOf(editing?.taskStatus ?: FieldTaskStatus.OPEN)
    }
    var observedAt by remember(editing?.id) { mutableStateOf(editing?.observedAt ?: System.currentTimeMillis()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Field Log" else "Edit Field Record") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Record type", fontWeight = FontWeight.Bold)
                    FieldRecordType.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { candidate ->
                                FilterChip(
                                    selected = type == candidate,
                                    onClick = { type = candidate },
                                    label = { Text(candidate.displayName(), fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Required field note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                item {
                    Text(
                        String.format(Locale.US, "Lat %.6f • Lng %.6f", coordinate.first, coordinate.second),
                        fontSize = 12.sp
                    )
                    accuracyMeters?.let { Text(String.format(Locale.US, "Reported accuracy: ±%.0f m", it), fontSize = 11.sp) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onPlaceOnMap, modifier = Modifier.weight(1f)) { Text("Place on Map", fontSize = 11.sp) }
                        OutlinedButton(onClick = onUseMyLocation, modifier = Modifier.weight(1f)) { Text("Use My Location", fontSize = 11.sp) }
                    }
                }
                item {
                    Text("Pasture association", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selected = pastureId == null, onClick = { pastureId = null })
                        Text("No pasture", modifier = Modifier.padding(top = 12.dp))
                    }
                    pastures.forEach { pasture ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = pastureId == pasture.pasture.id,
                                onClick = { pastureId = pasture.pasture.id }
                            )
                            Text(pasture.pasture.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
                if (type == FieldRecordType.TASK) {
                    item {
                        Text("Task status", fontWeight = FontWeight.Bold)
                        FieldTaskStatus.entries.forEach { status ->
                            Row(Modifier.fillMaxWidth()) {
                                RadioButton(selected = taskStatus == status, onClick = { taskStatus = status })
                                Text(status.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Observed ${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(Date(observedAt))}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = note.isNotBlank(),
                onClick = { onSave(type, note.trim(), pastureId, taskStatus, observedAt) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun FieldLedgerDialog(
    records: List<FieldRecordEntity>,
    pastures: List<PastureWithVertices>,
    onSelect: (FieldRecordEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var typeFilter by remember { mutableStateOf<FieldRecordType?>(null) }
    var pastureFilter by remember { mutableStateOf<Long?>(null) }
    var taskStatusFilter by remember { mutableStateOf<FieldTaskStatus?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val pastureNames = pastures.associate { it.pasture.id to it.pasture.name }
    val filtered = records.filter { record ->
        (if (showArchived) record.archivedAt != null else record.archivedAt == null) &&
            (typeFilter == null || record.recordType == typeFilter) &&
            (pastureFilter == null || record.pastureId == pastureFilter) &&
            (taskStatusFilter == null || record.taskStatus == taskStatusFilter) &&
            (search.isBlank() || record.note.contains(search, ignoreCase = true) ||
                pastureNames[record.pastureId]?.contains(search, ignoreCase = true) == true)
    }.sortedByDescending { it.observedAt }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Field Ledger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search notes or pasture") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All types") })
                    FilterChip(selected = showArchived, onClick = { showArchived = !showArchived }, label = { Text(if (showArchived) "Archived" else "Active") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldRecordType.entries.take(4).forEach { type ->
                        FilterChip(
                            selected = typeFilter == type,
                            onClick = { typeFilter = type },
                            label = { Text(type.displayName().take(4), fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldRecordType.entries.drop(4).forEach { type ->
                        FilterChip(
                            selected = typeFilter == type,
                            onClick = { typeFilter = type },
                            label = { Text(type.displayName(), fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (typeFilter == FieldRecordType.TASK || typeFilter == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FieldTaskStatus.entries.forEach { status ->
                            FilterChip(
                                selected = taskStatusFilter == status,
                                onClick = { taskStatusFilter = if (taskStatusFilter == status) null else status },
                                label = { Text(status.name.take(4), fontSize = 9.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (pastures.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(selected = pastureFilter == null, onClick = { pastureFilter = null }, label = { Text("All pastures", fontSize = 9.sp) })
                        pastures.forEach { pasture ->
                            FilterChip(
                                selected = pastureFilter == pasture.pasture.id,
                                onClick = { pastureFilter = pasture.pasture.id },
                                label = { Text(pasture.pasture.name, maxLines = 1, fontSize = 9.sp) }
                            )
                        }
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (filtered.isEmpty()) {
                        item { Text(if (records.isEmpty()) "No Field Ledger records yet." else "No records match the active filters.", color = Color.Gray) }
                    } else {
                        items(filtered, key = { it.id }) { record ->
                            OutlinedButton(onClick = { onSelect(record) }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("${record.recordType.displayName()} • ${pastureNames[record.pastureId] ?: "No pasture"}", fontWeight = FontWeight.Bold)
                                    Text(record.note, maxLines = 2, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
