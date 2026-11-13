package com.teamshryne.vimo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teamshryne.vimo.ProjectInfo
import com.teamshryne.vimo.ui.theme.VimoColors
import java.text.DateFormat
import java.util.Date

/**
 * The home screen: the projects.
 *
 * This is where the app starts. It lists the projects that are on the
 * device, and it is the place to make a new one.
 */
@Composable
fun ProjectListScreen(
    projects: List<ProjectInfo>,
    onOpen: (ProjectInfo) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (ProjectInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<ProjectInfo?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VimoColors.Background),
    ) {
        // The top bar.
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(VimoColors.Surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = VimoColors.Accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Vimo",
                style = MaterialTheme.typography.titleLarge,
                color = VimoColors.OnSurface,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    newName = ""
                    creating = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VimoColors.Accent,
                    contentColor = androidx.compose.ui.graphics.Color.Black,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New project", fontWeight = FontWeight.SemiBold)
            }
        }

        if (projects.isEmpty()) {
            EmptyProjects(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(projects, key = { it.path }) { project ->
                    ProjectRow(
                        project = project,
                        onOpen = { onOpen(project) },
                        onMenu = { menuFor = project },
                    )
                }
            }
        }
    }

    // The menu of a project.
    val menu = menuFor
    if (menu != null) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = { menuFor = null },
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    menuFor = null
                    onOpen(menu)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuFor = null
                    onDelete(menu)
                },
            )
        }
    }

    // The dialog that names a new project.
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New project") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    creating = false
                    onCreate(newName.ifBlank { "Untitled" })
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("Cancel") }
            },
        )
    }
}

/** One row of the project list. */
@Composable
private fun ProjectRow(
    project: ProjectInfo,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VimoColors.SurfaceRaised)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VimoColors.SurfaceActive),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = VimoColors.AccentVideo,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                project.name,
                style = MaterialTheme.typography.titleMedium,
                color = VimoColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                project.modifiedLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = VimoColors.OnSurfaceMuted,
            )
        }
        IconButton(onClick = onMenu) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = VimoColors.OnSurfaceMuted,
            )
        }
    }
}

/** The state of an app with no projects yet. */
@Composable
private fun EmptyProjects(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = VimoColors.OnSurfaceDisabled,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No projects yet",
                style = MaterialTheme.typography.titleMedium,
                color = VimoColors.OnSurfaceMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Make one to start editing.",
                style = MaterialTheme.typography.bodySmall,
                color = VimoColors.OnSurfaceDisabled,
            )
        }
    }
}

/** When the project was last changed. */
private fun ProjectInfo.modifiedLabel(): String {
    if (modified <= 0) return ""
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return format.format(Date(modified * 1000))
}
