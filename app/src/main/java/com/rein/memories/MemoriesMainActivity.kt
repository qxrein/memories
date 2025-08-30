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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.painterResource
import com.rein.memories.R

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
	var showSettings by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Handle system back navigation when in settings
    BackHandler(enabled = showSettings) {
        showSettings = false
    }
    
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
            if (showSettings) {
                SettingsTopBar(
                    onBackPressed = { showSettings = false },
                    scrollBehavior = scrollBehavior
                )
            } else {
                ExpressiveTopBar(
                    selectedDate = selectedDate,
                    scrollBehavior = scrollBehavior,
                    onSettingsClick = { showSettings = true }
                )
            }
        },
        floatingActionButton = {
            if (!showSettings) {
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
        }
    ) { innerPadding ->
        val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()
        var didBounce by remember { mutableStateOf(false) }
        
        AnimatedContent(
            targetState = showSettings,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(350)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -(fullWidth / 4) },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(350)
                ) + scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )
            },
            label = "settings_navigation"
        ) { isSettings ->
            if (isSettings) {
                // Settings page without drawer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(0.dp),
                            spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                ) {
                    SettingsContent()
                }
            } else {
                // Main page with drawer
                BottomSheetScaffold(
                scaffoldState = bottomSheetScaffoldState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                sheetPeekHeight = 254.dp,
                sheetDragHandle = {
                    val infiniteTransition = rememberInfiniteTransition(label = "")
                    val handleAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.12f,
                        targetValue = 0.18f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ), label = ""
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 5.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* absorb all clicks in handle area */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = handleAlpha))
                        )
                    }
                },
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetTonalElevation = 4.dp,
                sheetContent = {
                    // Images drawer floating over calendar
                    val scope = rememberCoroutineScope()
                    var cumulativeDrag by remember { mutableStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.98f)
                            .pointerInput(bottomSheetScaffoldState.bottomSheetState.currentValue) {
                                detectVerticalDragGestures(
                                    onDragStart = { cumulativeDrag = 0f },
                                    onVerticalDrag = { _, dragAmount ->
                                        cumulativeDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        if (cumulativeDrag <= -60f) {
                                            scope.launch { bottomSheetScaffoldState.bottomSheetState.expand() }
                                        } else if (cumulativeDrag >= 60f) {
                                            scope.launch { bottomSheetScaffoldState.bottomSheetState.partialExpand() }
                                        }
                                    }
                                )
                        }
                    ) { // allow near full screen stretch
                        if (!mediaPermissionState.status.isGranted) {
                            RequestMediaPermission(onRequest = { mediaPermissionState.launchPermissionRequest() })
                        } else {
                            MemoriesGrid(
                                selectedDate = selectedDate,
                                onDateSelected = { selectedDate = it },
                                refreshKey = refreshKey,
                                isLoading = isLoading,
                                onLoadingComplete = { isLoading = false },
                                gridState = gridState,
                                onRequestDelete = { uri ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(
                                            context.contentResolver,
                                            listOf(Uri.parse(uri))
                                        )
                                        deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                    } else {
                                        context.contentResolver.delete(Uri.parse(uri), null, null)
                                        refreshKey++
                                    }
                                },
                                onOpenExternal = { uri ->
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(uri), "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            ) {
                // One-time bouncy hint animation on app open
                LaunchedEffect(Unit) {
                    if (!didBounce) {
                        didBounce = true
                        // Wait for layout to settle so animation is visible
                        delay(150)
                        // Ensure we start collapsed
                        bottomSheetScaffoldState.bottomSheetState.partialExpand()
                        delay(50)
                        // Single gentle bounce
                        // Bounce to full expand then back to peek
                        bottomSheetScaffoldState.bottomSheetState.expand()
                        delay(150)
                        bottomSheetScaffoldState.bottomSheetState.partialExpand()
                    }
                }
                // Calendar stays behind, sheet floats over it
                MonthCalendar(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, _ ->
                                    change.consumeAllChanges()
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { },
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it }
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
    scrollBehavior: TopAppBarScrollBehavior,
    onSettingsClick: () -> Unit
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
        actions = {
            IconButton(
                onClick = onSettingsClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.gear),
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    onBackPressed: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeTopAppBar(
        title = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackPressed,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
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
fun SettingsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App version
        val context = LocalContext.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "App Version",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Star on GitHub
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qxrein/memories"))
                            context.startActivity(intent)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Star on GitHub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Support the project on GitHub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Star",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
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
    modifier: Modifier = Modifier,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
		// Keep a visible month independent of day selection navigation
		var currentMonth by remember(selectedDate) { mutableStateOf(java.time.YearMonth.from(selectedDate)) }

		Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
			// Header: Month navigation
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
					Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous month")
				}
				Text(
					text = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + currentMonth.year,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold
				)
				IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
					Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next month")
				}
			}

			Spacer(modifier = Modifier.height(6.dp))

			// Weekday labels (Mon..Sun) – fixed 7 equal columns
			val weekdayNames = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
			Row(modifier = Modifier.fillMaxWidth()) {
				weekdayNames.forEach { name ->
					Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
						Text(
							text = name,
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(6.dp))

			// Days grid: build 6 rows x 7 columns, equal weights
			val firstOfMonth = currentMonth.atDay(1)
			val leadingBlanks = (firstOfMonth.dayOfWeek.value - java.time.DayOfWeek.MONDAY.value + 7) % 7
			val daysInMonth = currentMonth.lengthOfMonth()
			val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7 // round up to full weeks

			for (row in 0 until totalCells / 7) {
				Row(modifier = Modifier.fillMaxWidth()) {
					for (col in 0 until 7) {
						val cellIndex = row * 7 + col
						val dayNumber = cellIndex - leadingBlanks + 1
						if (dayNumber in 1..daysInMonth) {
							val date = currentMonth.atDay(dayNumber)
							val isSelected = date == selectedDate
							Box(
								modifier = Modifier
									.weight(1f)
									.aspectRatio(1f)
									.padding(4.dp)
									.clip(CircleShape)
									.background(
										if (isSelected) MaterialTheme.colorScheme.primary
										else Color.Transparent
									)
									.clickable { onDateSelected(date) },
								contentAlignment = Alignment.Center
							) {
								Text(
									text = dayNumber.toString(),
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
									color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
								)
							}
						} else {
							// Placeholder to keep 7 equal columns
							Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(4.dp))
						}
					}
				}
				Spacer(modifier = Modifier.height(2.dp))
			}
		}
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
	onDateSelected: (LocalDate) -> Unit,
	refreshKey: Int,
    isLoading: Boolean,
	onLoadingComplete: () -> Unit,
	gridState: LazyGridState,
	onRequestDelete: (String) -> Unit,
	onOpenExternal: (String) -> Unit
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
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			state = gridState
        ) {
			items(memories) { memory ->
				MemoryCardInteractive(
					memory = memory,
					onClick = { onOpenExternal(memory.imageUri) },
					onLongPress = { onRequestDelete(memory.imageUri) }
				)
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
fun MemoryCardInteractive(memory: Memory, onClick: () -> Unit, onLongPress: () -> Unit) {
	Card(
                modifier = Modifier
			.fillMaxWidth()
			.aspectRatio(1f)
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongPress,
				// Use default Material3 ripple/indication for compatibility
			),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceVariant
		),
		elevation = CardDefaults.cardElevation(
			defaultElevation = 4.dp,
			pressedElevation = 8.dp
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

@Composable
fun ImageDetailOverlay(memory: Memory, onDismiss: () -> Unit) {
	BackHandler(enabled = true) { onDismiss() }
	AnimatedVisibility(
		visible = true,
		enter = fadeIn() + scaleIn(initialScale = 0.95f),
		exit = fadeOut() + scaleOut(targetScale = 0.95f)
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black.copy(alpha = 0.3f)),
			contentAlignment = Alignment.Center
		) {
			Card(
				shape = RoundedCornerShape(24.dp),
				elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
			) {
				Box(modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(3f / 4f)) {
					AsyncImage(
						model = ImageRequest.Builder(LocalContext.current)
							.data(memory.imageUri)
							.crossfade(true)
							.build(),
						contentDescription = null,
						modifier = Modifier.fillMaxSize(),
						contentScale = ContentScale.Fit
					)
					IconButton(
						onClick = onDismiss,
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(8.dp)
					) {
						Icon(
							imageVector = Icons.Default.ArrowBack,
							contentDescription = "Back",
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}
		}
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
