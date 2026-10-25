package dev.wakeuppure

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import dev.wakeuppure.ui.PureTheme
import dev.wakeuppure.ui.settings.ShiguangxuImportScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShiguangxuImportUiTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test fun predictiveBackCanBeCancelledWithoutLosingInputThenCommittedToTransfer() {
        lateinit var navigation: NavHostController
        lateinit var back: OnBackPressedDispatcher
        var disposed = 0
        compose.setContent { PureTheme("light") {
            val nav = rememberNavController()
            val dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            SideEffect { navigation = nav; back = dispatcher }
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            NavHost(nav, startDestination = "transfer",
                enterTransition = { fadeIn(effects) + slideInHorizontally(spatial) { it / 12 } },
                exitTransition = { fadeOut(effects) + slideOutHorizontally(spatial) { -it / 12 } },
                popEnterTransition = { fadeIn(effects) + slideInHorizontally(spatial) { -it / 12 } },
                popExitTransition = { fadeOut(effects) + slideOutHorizontally(spatial) { it / 12 } }) {
                composable("transfer") { Button(onClick = { nav.navigate("shiguangxu") }) { Text("打开时光序") } }
                composable("shiguangxu") {
                    DisposableEffect(Unit) { onDispose { disposed++ } }
                    ShiguangxuImportScreen({ nav.popBackStack() }, {})
                }
            }
        } }
        compose.onNodeWithText("打开时光序").performClick()
        compose.onNodeWithText("手机号").performTextInput("1234567890")
        compose.onNodeWithText("密码", substring = false).performTextInput("synthetic-password")
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.runOnIdle { back.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { back.dispatchOnBackProgressed(BackEventCompat(150f, 500f, 0.5f, BackEventCompat.EDGE_LEFT)) }
        compose.onNodeWithText("打开时光序").assertExists()
        compose.runOnIdle { back.dispatchOnBackCancelled() }
        compose.onNodeWithText("1234567890").assertExists()
        compose.onNodeWithText("synthetic-password").assertExists()
        compose.runOnIdle {
            assertEquals(0, disposed)
            assertEquals("shiguangxu", navigation.currentDestination?.route)
            back.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
        }
        compose.runOnIdle { back.dispatchOnBackProgressed(BackEventCompat(240f, 500f, 0.8f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("打开时光序").assertIsDisplayed()
        compose.runOnIdle { assertEquals("transfer", navigation.currentDestination?.route); assertEquals(1, disposed) }
    }

    @Test fun loginRequiresCredentialsAndDoesNotPrefillTestAccount() {
        var visible by mutableStateOf(true)
        compose.setContent { PureTheme("light") { if (visible) ShiguangxuImportScreen({ visible = false }, {}) } }
        compose.onNodeWithText("登录并继续").assertIsNotEnabled()
        compose.onNodeWithText("国家码").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "shiguangxu-login.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("手机号").performTextInput("1234567890")
        compose.onNodeWithText("登录并继续").assertIsNotEnabled()
        compose.onNodeWithText("密码", substring = false).performTextInput("synthetic-password")
        compose.onNodeWithText("登录并继续").assertIsEnabled()
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.onNodeWithContentDescription("隐藏密码").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertFalse(visible); visible = true }
        compose.onNodeWithText("登录并继续").assertIsNotEnabled()
        compose.onNodeWithText("synthetic-password").assertDoesNotExist()
        compose.onNodeWithText("1234567890").assertDoesNotExist()
    }
}
