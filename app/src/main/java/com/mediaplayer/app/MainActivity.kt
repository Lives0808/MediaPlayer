package com.mediaplayer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediaplayer.app.ui.home.HomeScreen
import com.mediaplayer.app.ui.player.PlayerScreen
import com.mediaplayer.app.ui.settings.SettingsScreen
import com.mediaplayer.app.ui.theme.MediaPlayerTheme

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openedFromOutside = handleIntent(intent)

        setContent {
            MediaPlayerTheme {
                val nav = rememberNavController()
                DisposableEffect(nav) {
                    navController = nav
                    onDispose { if (navController === nav) navController = null }
                }

                NavHost(
                    navController = nav,
                    startDestination = if (openedFromOutside) Routes.externalPlayer() else Routes.HOME,
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenVideo = { video -> nav.navigate(Routes.player(video.id)) },
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                    }
                    composable(
                        route = Routes.PLAYER,
                        arguments = listOf(
                            navArgument(Routes.MEDIA_ID_ARG) { type = NavType.StringType },
                        ),
                    ) {
                        PlayerScreen(onBack = { nav.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleIntent(intent)) {
            navController?.navigate(Routes.externalPlayer()) { launchSingleTop = true }
        }
    }

    /** 记录外部应用传来的视频地址，返回是否是外部播放请求 */
    private fun handleIntent(intent: Intent?): Boolean {
        val uri = intent?.toExternalVideoUri() ?: return false
        val container = (application as MediaPlayerApp).container
        container.externalVideoUri = uri
        container.externalVideoTitle = intent.getStringExtra(Intent.EXTRA_TITLE)
            ?: uri.lastPathSegment?.substringBeforeLast('.')
        return true
    }
}
