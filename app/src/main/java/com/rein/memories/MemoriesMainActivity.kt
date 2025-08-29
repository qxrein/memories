package com.rein.memories

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rein.memories.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.app.PendingIntent
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.combinedClickable
import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
class MemoriesMainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			MemoriesTheme {
				MemoriesMainScreen()
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MemoriesMainScreen() {
	var selectedDate by remember { mutableStateOf(LocalDate.now()) }
	var isLoading by remember { mutableStateOf(false) }
	var refreshKey by remember { mutableStateOf(0) }
	
	val context = LocalContext.current
	
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
		rememberTopAppBarState()
	)
	
	val mediaPermissionState = rememberPermissionState(Manifest.permission.READ_MEDIA_IMAGES)
	// Using stock camera; no runtime CAMERA permission needed
	val takePictureLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.TakePicture()
	) { success ->
		if (success) {
			refreshKey++
		}
	}
	
	// Launcher for delete request (system confirmation on Android 10+ if needed)
	val deleteLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.StartIntentSenderForResult()
	) { result ->
		if (result.resultCode == Activity.RESULT_OK) {
			refreshKey++
		}
	}
	
	val gridState: LazyGridState = rememberLazyGridState()
	
	Scaffold(
		modifier = Modifier
			.fillMaxSize()
			.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			ExpressiveTopBar(
				selectedDate = selectedDate,
				scrollBehavior = scrollBehavior
			)
		},
		floatingActionButton = {
			CameraFAB(
				onClick = {
					val name = selectedDate.toString() + "-" + System.currentTimeMillis()
					val contentValues = ContentValues().apply {
						put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
						put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
						}
					}
					val uri = context.contentResolver.insert(
						MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						contentValues
					)
					if (uri != null) {
						takePictureLauncher.launch(uri)
					}
				}
			)
		}
	) { innerPadding ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
		) {
			Column(
				modifier = Modifier.fillMaxSize()
			) {
				// Always show Month calendar
				MonthCalendar(
					selectedDate = selectedDate,
					onDateSelected = { selectedDate = it }
				)
				
				// Memories Grid
				if (!mediaPermissionState.status.isGranted) {
					RequestMediaPermission(onRequest = { mediaPermissionState.launchPermissionRequest() })
				} else {
					MemoriesGrid(
						selectedDate = selectedDate,
						refreshKey = refreshKey,
						isLoading = isLoading,
						onLoadingComplete = { isLoading = false },
						gridState = gridState,
						onRequestDelete = { uri ->
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
								val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(
									context.contentResolver,
									listOf(Uri.parse(uri))
								)
								deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
							} else {
								context.contentResolver.delete(Uri.parse(uri), null, null)
								refreshKey++
							}
						}
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ExpressiveTopBar(
	selectedDate: LocalDate,
	scrollBehavior: TopAppBarScrollBehavior
) {
	LargeTopAppBar(
		title = {
			Column {
				Text(
					text = "Memories",
					style = MaterialTheme.typography.headlineLarge,
					fontWeight = FontWeight.Black,
					color = MaterialTheme.colorScheme.onPrimaryContainer
				)
				Text(
					text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
					style = MaterialTheme.typography.titleMedium,
					color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
				)
			}
		},
		scrollBehavior = scrollBehavior,
		colors = TopAppBarDefaults.largeTopAppBarColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer,
			titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
			actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
		)
	)
}

@Composable
fun CameraFAB(onClick: () -> Unit) {
	val scale by animateFloatAsState(
		targetValue = 1f,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioMediumBouncy,
			stiffness = Spring.StiffnessLow
		), label = ""
	)
	
	ExtendedFloatingActionButton(
		onClick = onClick,
		modifier = Modifier
			.scale(scale),
		containerColor = MaterialTheme.colorScheme.tertiaryContainer,
		contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
		shape = RoundedCornerShape(28.dp),
		elevation = FloatingActionButtonDefaults.elevation(
			defaultElevation = 8.dp,
			pressedElevation = 14.dp,
			focusedElevation = 10.dp,
			hoveredElevation = 10.dp
		)
	) {
		Icon(
			imageVector = Icons.Default.CameraAlt,
			contentDescription = "Capture Memory",
			modifier = Modifier.size(24.dp)
		)
		Spacer(modifier = Modifier.width(10.dp))
		Text(
			text = "Capture",
			style = MaterialTheme.typography.titleSmall,
			fontWeight = FontWeight.Bold
		)
	}
}

@Composable
fun MonthCalendar(
	selectedDate: LocalDate,
	onDateSelected: (LocalDate) -> Unit
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.padding(16.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surface,
		),
		elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
	) {
		val initialMillis = selectedDate
			.atStartOfDay(java.time.ZoneId.systemDefault())
			.toInstant()
			.toEpochMilli()
		val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
		LaunchedEffect(state.selectedDateMillis) {
			state.selectedDateMillis?.let { millis ->
				val newDate = java.time.Instant.ofEpochMilli(millis)
					.atZone(java.time.ZoneId.systemDefault())
					.toLocalDate()
				if (newDate != selectedDate) onDateSelected(newDate)
			}
		}
		DatePicker(
			state = state,
			showModeToggle = false,
			colors = DatePickerDefaults.colors(
				containerColor = MaterialTheme.colorScheme.surface,
				dayContentColor = MaterialTheme.colorScheme.onSurface,
				selectedDayContainerColor = MaterialTheme.colorScheme.primary,
				selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
				todayDateBorderColor = MaterialTheme.colorScheme.primary
			)
		)
	}
}

@Composable
fun CalendarDay(
	date: LocalDate,
	isSelected: Boolean,
	onClick: () -> Unit
) {
	val backgroundColor by animateColorAsState(
		targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
		label = ""
	)
	
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.aspectRatio(1f)
			.clip(RoundedCornerShape(12.dp))
			.background(backgroundColor)
			.clickable { onClick() }
			.padding(6.dp)
	) {
		Text(
			text = date.dayOfMonth.toString(),
			style = MaterialTheme.typography.titleSmall,
			fontWeight = FontWeight.Bold,
			color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
			   else MaterialTheme.colorScheme.onSurface
		)
	}
}

@Composable
fun MemoriesGrid(
	selectedDate: LocalDate,
	refreshKey: Int,
	isLoading: Boolean,
	onLoadingComplete: () -> Unit,
	gridState: LazyGridState,
	onRequestDelete: (String) -> Unit
) {
	var memories by remember(selectedDate, refreshKey) { mutableStateOf<List<Memory>>(emptyList()) }
	var loading by remember(selectedDate, refreshKey) { mutableStateOf(true) }
	val context = LocalContext.current

	LaunchedEffect(selectedDate, refreshKey) {
		loading = true
		memories = withContext(Dispatchers.IO) { queryMemoriesForDate(context, selectedDate) }
		loading = false
		onLoadingComplete()
	}

	when {
		loading -> SwiggyStyleLoader()
		memories.isEmpty() -> EmptyMemoriesState()
		else -> LazyVerticalGrid(
			columns = GridCells.Fixed(2),
			contentPadding = PaddingValues(16.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			state = gridState
		) {
			items(memories) { memory ->
				MemoryCardInteractive(memory = memory, onLongPress = { onRequestDelete(memory.imageUri) })
			}
		}
	}
}

@Composable
fun SwiggyStyleLoader() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(32.dp),
		contentAlignment = Alignment.Center
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
			Spacer(modifier = Modifier.height(16.dp))
			Text(
				text = "Loading memories...",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
			)
		}
	}
}

@Composable
fun EmptyMemoriesState() {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(32.dp),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = "No memories for this day",
			style = MaterialTheme.typography.titleMedium,
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
		)
	}
}

@Composable
fun MemoryCard(memory: Memory) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.aspectRatio(1f),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant
		),
		elevation = CardDefaults.cardElevation(
			defaultElevation = 6.dp,
			pressedElevation = 12.dp
		)
	) {
		Box(modifier = Modifier.fillMaxSize()) {
			AsyncImage(
				model = ImageRequest.Builder(LocalContext.current)
					.data(memory.imageUri)
					.crossfade(true)
					.build(),
				contentDescription = null,
				modifier = Modifier.fillMaxSize(),
				contentScale = ContentScale.Crop
			)
		}
	}
}

@Composable
fun MemoryCardInteractive(memory: Memory, onLongPress: () -> Unit) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.aspectRatio(1f)
			.combinedClickable(onClick = {}, onLongClick = onLongPress),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant
		),
		elevation = CardDefaults.cardElevation(
			defaultElevation = 6.dp,
			pressedElevation = 12.dp
		)
	) {
		AsyncImage(
			model = ImageRequest.Builder(LocalContext.current)
				.data(memory.imageUri)
				.crossfade(true)
				.build(),
			contentDescription = null,
			modifier = Modifier.fillMaxSize(),
			contentScale = ContentScale.Crop
		)
	}
}

// Data classes and device query
data class Memory(
	val id: String,
	val imageUri: String,
	val date: LocalDate
)

suspend fun queryMemoriesForDate(context: android.content.Context, date: LocalDate): List<Memory> {
	val startOfDay = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
	val endOfDay = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
	
	val projection = arrayOf(
		MediaStore.Images.Media._ID,
		MediaStore.Images.Media.DATE_TAKEN,
		MediaStore.Images.Media.DATE_ADDED,
		MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
		MediaStore.Images.Media.RELATIVE_PATH
	)
	
	val selection = "((" + MediaStore.Images.Media.DATE_TAKEN + " BETWEEN ? AND ?) OR (" + MediaStore.Images.Media.DATE_TAKEN + " IS NULL AND " + MediaStore.Images.Media.DATE_ADDED + " BETWEEN ? AND ?)) AND (" +
		MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " IN ('Camera','CAMERA') OR " + MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? )"
	val selectionArgs = arrayOf(
		startOfDay.toString(),
		endOfDay.toString(),
		(startOfDay / 1000).toString(),
		(endOfDay / 1000).toString(),
		"%DCIM/Camera%"
	)
	
	val sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC"
	val images = mutableListOf<Memory>()
	
	context.contentResolver.query(
		MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
		projection,
		selection,
		selectionArgs,
		sortOrder
	)?.use { cursor ->
		val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
		while (cursor.moveToNext()) {
			val id = cursor.getLong(idColumn)
			val contentUri: Uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
			images.add(
				Memory(
					id = id.toString(),
					imageUri = contentUri.toString(),
					date = date
				)
			)
		}
	}
	
	return images
}

@Composable
fun RequestMediaPermission(onRequest: () -> Unit) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.padding(24.dp),
		contentAlignment = Alignment.Center
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(
				text = "Allow access to your photos to show memories",
				style = MaterialTheme.typography.titleMedium,
				textAlign = TextAlign.Center
			)
			Spacer(modifier = Modifier.height(12.dp))
			Button(onClick = onRequest) {
				Text("Grant permission")
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
fun MemoriesMainScreenPreview() {
	MemoriesTheme {
		MemoriesMainScreen()
	}
}
