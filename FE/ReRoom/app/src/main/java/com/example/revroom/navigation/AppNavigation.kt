package com.example.revroom.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.revroom.core.ui.StudioBackground
import com.example.revroom.core.ui.StudioBottomBar
import com.example.revroom.core.ui.StudioTab
import com.example.revroom.data.local.UserManager
import com.example.revroom.features.auth.ui.SettingsScreen
import com.example.revroom.features.chat.ui.ChatScreen
import com.example.revroom.features.design_studio.model.DesignMode
import com.example.revroom.features.design_studio.model.designFlowProcessingStep
import com.example.revroom.features.design_studio.model.designFlowTotalSteps
import com.example.revroom.features.design_studio.model.selectedFeatureRoomSubtitle
import com.example.revroom.features.design_studio.model.selectedFeatureStyleSubtitle
import com.example.revroom.features.design_studio.model.selectedFeatureTitle
import com.example.revroom.features.design_studio.model.selectedFeatureUploadSubtitle
import com.example.revroom.features.design_studio.ui.DesignHomeScreen
import com.example.revroom.features.design_studio.ui.ProcessingResultScreen
import com.example.revroom.features.design_studio.ui.RoomTypeScreen
import com.example.revroom.features.design_studio.ui.StyleScreen
import com.example.revroom.features.design_studio.ui.UploadPhotoScreen
import com.example.revroom.features.design_studio.viewmodel.DesignViewModel
import com.example.revroom.features.history.ui.HistoryScreen

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val factory = remember(context) { DesignViewModel.Factory(context) }
    val viewModel: DesignViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val designStyles by viewModel.designStyles.collectAsState()
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentMainTab = currentBackStackEntry?.destination?.route.toStudioTab()
    var lastMainTab by remember { mutableStateOf(StudioTab.Interior) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val isBottomBarVisible = currentMainTab != null
    val bottomSafeInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val hiddenOffsetBufferPx = with(density) { BOTTOM_BAR_HIDE_BUFFER_DP.dp.toPx() }
    val hiddenBottomBarOffsetPx = bottomBarHeightPx + bottomSafeInsetPx + hiddenOffsetBufferPx
    val bottomBarOffsetY by animateFloatAsState(
        targetValue = if (isBottomBarVisible) 0f else hiddenBottomBarOffsetPx,
        animationSpec = tween(
            BOTTOM_BAR_VISIBILITY_ANIMATION_MILLIS,
            easing = FastOutSlowInEasing
        ),
        label = "BottomBarOffsetY"
    )
    val bottomBarAlpha = if (
        !isBottomBarVisible &&
        hiddenBottomBarOffsetPx > 0f &&
        bottomBarOffsetY >= hiddenBottomBarOffsetPx * 0.95f
    ) {
        0f
    } else {
        1f
    }
    val disabledBottomBarAction = {}

    LaunchedEffect(currentMainTab) {
        currentMainTab?.let { lastMainTab = it }
    }

    fun go(route: String) {
        if (navController.currentDestination?.route == route) {
            return
        }

        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goInterior() = go(Route.INTERIOR_HOME)
    fun goExterior() = go(Route.EXTERIOR_HOME)
    fun goChat() = go(Route.CHAT)
    fun goGallery() = go(Route.GALLERY)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
    ) {
        NavHost(
            navController = navController,
            startDestination = Route.INTERIOR_HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { routeEnterTransition() },
            exitTransition = { routeExitTransition() },
            popEnterTransition = { routeEnterTransition() },
            popExitTransition = { routeExitTransition() }
        ) {
                composable(Route.INTERIOR_HOME) {
                    DesignHomeScreen(
                        title = "Interior Design",
                        chips = listOf("Interior Design", "Furnish Empty Room", "Remove Furniture"),
                        features = viewModel.interiorFeatures,
                        selectedTab = StudioTab.Interior,
                        onFeatureClick = { featureId ->
                            viewModel.startDesign(DesignMode.Interior, featureId)
                            navController.navigate(Route.UPLOAD)
                        },
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        showBottomBar = false
                    )
                }

                composable(Route.EXTERIOR_HOME) {
                    DesignHomeScreen(
                        title = "Exterior Design",
                        chips = listOf("Exterior Design", "Facade", "Landscaping", "Add Greenery"),
                        features = viewModel.exteriorFeatures,
                        selectedTab = StudioTab.Exterior,
                        onFeatureClick = { featureId ->
                            viewModel.startDesign(DesignMode.Exterior, featureId)
                            navController.navigate(Route.UPLOAD)
                        },
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        showBottomBar = false
                    )
                }

                composable(Route.UPLOAD) {
                    val isExterior = uiState.designMode == DesignMode.Exterior
                    val isRemoveFurniture = uiState.selectedFeature == "remove_furniture"
                    val flowTitle = if (isExterior) "Exterior Design" else uiState.selectedFeatureTitle
                    UploadPhotoScreen(
                        title = flowTitle,
                        stepCaption = if (isExterior) "Upload your exterior photo" else uiState.selectedFeatureUploadSubtitle,
                        selectedTab = if (isExterior) StudioTab.Exterior else StudioTab.Interior,
                        selectedImageUri = uiState.selectedImageUri,
                        onImageSelected = viewModel::selectImage,
                        onNext = {
                            if (isRemoveFurniture) {
                                viewModel.createDesign()
                                navController.navigate(Route.PROCESSING_RESULT) {
                                    launchSingleTop = true
                                }
                            } else if (isExterior) {
                                navController.navigate(Route.STYLE_SELECT)
                            } else {
                                navController.navigate(Route.ROOM_TYPE)
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        totalSteps = uiState.designFlowTotalSteps,
                        showBottomBar = false
                    )
                }

                composable(Route.ROOM_TYPE) {
                    RoomTypeScreen(
                        title = uiState.selectedFeatureTitle,
                        stepCaption = uiState.selectedFeatureRoomSubtitle,
                        selectedRoomType = uiState.selectedRoomType,
                        roomTypes = viewModel.roomTypes,
                        onRoomTypeSelected = viewModel::selectRoomType,
                        onNext = { navController.navigate(Route.STYLE_SELECT) },
                        onBack = { navController.popBackStack() },
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        totalSteps = 4,
                        showBottomBar = false
                    )
                }

                composable(Route.STYLE_SELECT) {
                    val isExterior = uiState.designMode == DesignMode.Exterior
                    val flowTitle = if (isExterior) "Exterior Design" else uiState.selectedFeatureTitle
                    StyleScreen(
                        title = flowTitle,
                        stepCaption = if (isExterior) "Choose your style" else uiState.selectedFeatureStyleSubtitle,
                        selectedTab = if (isExterior) StudioTab.Exterior else StudioTab.Interior,
                        selectedStyle = uiState.selectedStyle,
                        styles = designStyles,
                        onStyleSelected = viewModel::selectStyle,
                        onCreateDesign = {
                            viewModel.createDesign()
                            navController.navigate(Route.PROCESSING_RESULT) {
                                launchSingleTop = true
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        totalSteps = 4,
                        showBottomBar = false
                    )
                }

                composable(Route.PROCESSING_RESULT) {
                    ProcessingResultScreen(
                        uiState = uiState,
                        onRetry = viewModel::retry,
                        onSaveToHistory = { goGallery() },
                        onCreateAnother = {
                            val mode = uiState.designMode
                            val feature = uiState.selectedFeature ?: if (mode == DesignMode.Exterior) {
                                "exterior_design"
                            } else {
                                "interior_design"
                            }
                            viewModel.startDesign(mode, feature)
                            navController.navigate(Route.UPLOAD) {
                                popUpTo(Route.UPLOAD) { inclusive = true }
                            }
                        },
                        onBack = {
                            val destination = if (uiState.designMode == DesignMode.Exterior) {
                                Route.EXTERIOR_HOME
                            } else {
                                Route.INTERIOR_HOME
                            }
                            viewModel.reset()
                            navController.navigate(destination) {
                                popUpTo(Route.INTERIOR_HOME) { inclusive = false }
                            }
                        },
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        currentStep = uiState.designFlowProcessingStep,
                        totalSteps = uiState.designFlowTotalSteps,
                        showBottomBar = false
                    )
                }

                composable(Route.CHAT) {
                    ChatScreen(
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        showBottomBar = false
                    )
                }

                composable(Route.GALLERY) {
                    HistoryScreen(
                        onInterior = ::goInterior,
                        onExterior = ::goExterior,
                        onChat = ::goChat,
                        onGallery = ::goGallery,
                        onProfileClick = { navController.navigate(Route.SETTINGS) },
                        showBottomBar = false
                    )
                }

                composable(Route.SETTINGS) {
                    SettingsScreen(userManager = UserManager(LocalContext.current))
                }
        }

        StudioBottomBar(
            selectedTab = currentMainTab ?: lastMainTab,
            onInterior = if (isBottomBarVisible) ::goInterior else disabledBottomBarAction,
            onExterior = if (isBottomBarVisible) ::goExterior else disabledBottomBarAction,
            onChat = if (isBottomBarVisible) ::goChat else disabledBottomBarAction,
            onGallery = if (isBottomBarVisible) ::goGallery else disabledBottomBarAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .onSizeChanged { bottomBarHeightPx = it.height }
                .graphicsLayer {
                    translationY = bottomBarOffsetY
                    alpha = bottomBarAlpha
                }
        )
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.routeEnterTransition(): EnterTransition {
    val direction = routeSlideDirection(
        initialRoute = initialState.destination.route,
        targetRoute = targetState.destination.route
    ) ?: return EnterTransition.None

    return slideIntoContainer(
        towards = direction,
        animationSpec = tween(TAB_TRANSITION_MILLIS, easing = FastOutSlowInEasing)
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.routeExitTransition(): ExitTransition {
    val direction = routeSlideDirection(
        initialRoute = initialState.destination.route,
        targetRoute = targetState.destination.route
    ) ?: return ExitTransition.None

    return slideOutOfContainer(
        towards = direction,
        animationSpec = tween(TAB_TRANSITION_MILLIS, easing = FastOutSlowInEasing)
    )
}

private fun routeSlideDirection(
    initialRoute: String?,
    targetRoute: String?
): AnimatedContentTransitionScope.SlideDirection? {
    val initialIndex = initialRoute.routeTransitionIndex() ?: return null
    val targetIndex = targetRoute.routeTransitionIndex() ?: return null
    if (initialIndex == targetIndex) {
        return null
    }

    return if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

private fun String?.routeTransitionIndex(): Int? {
    return mainTabTransitionIndex() ?: designFlowTransitionIndex()
}

private fun String?.mainTabTransitionIndex(): Int? {
    return when (this) {
        Route.INTERIOR_HOME -> 10
        Route.EXTERIOR_HOME -> 11
        Route.CHAT -> 12
        Route.GALLERY -> 13
        else -> null
    }
}

private fun String?.designFlowTransitionIndex(): Int? {
    return when (this) {
        Route.UPLOAD -> 20
        Route.ROOM_TYPE -> 21
        Route.STYLE_SELECT -> 22
        Route.PROCESSING_RESULT -> 23
        else -> null
    }
}

private fun String?.toStudioTab(): StudioTab? {
    return when (this) {
        Route.INTERIOR_HOME -> StudioTab.Interior
        Route.EXTERIOR_HOME -> StudioTab.Exterior
        Route.CHAT -> StudioTab.Chat
        Route.GALLERY -> StudioTab.Gallery
        else -> null
    }
}

private const val TAB_TRANSITION_MILLIS = 320
private const val BOTTOM_BAR_VISIBILITY_ANIMATION_MILLIS = 880
private const val BOTTOM_BAR_HIDE_BUFFER_DP = 24

private object Route {
    const val INTERIOR_HOME = "interior_home"
    const val EXTERIOR_HOME = "exterior_home"
    const val UPLOAD = "upload"
    const val ROOM_TYPE = "room_type"
    const val STYLE_SELECT = "style_select"
    const val PROCESSING_RESULT = "processing_result"
    const val CHAT = "chat"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
}
