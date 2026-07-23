package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LouvorViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LouvorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "cultos_list"
                    ) {
                        composable("cultos_list") {
                            val cultos by viewModel.allCultos.collectAsStateWithLifecycle()
                            CultosListScreen(
                                cultos = cultos,
                                onAddCultoClicked = { navController.navigate("culto_create") },
                                onCultoSelected = { id -> navController.navigate("culto_details/$id") },
                                onDeleteCulto = { viewModel.deleteCulto(it) },
                                onImportCulto = { importedCulto, importedLouvores ->
                                    viewModel.importCultoWithLouvores(importedCulto, importedLouvores)
                                }
                            )
                        }

                        composable("culto_create") {
                            CreateCultoScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onSaveCulto = { nome, data, horario ->
                                    viewModel.createCulto(nome, data, horario) {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }

                        composable(
                            route = "culto_details/{cultoId}",
                            arguments = listOf(navArgument("cultoId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val cultoId = backStackEntry.arguments?.getInt("cultoId") ?: 0
                            val culto by viewModel.getCultoById(cultoId).collectAsStateWithLifecycle(initialValue = null)
                            val louvores by viewModel.getLouvoresForCulto(cultoId).collectAsStateWithLifecycle(initialValue = emptyList())

                            CultoDetailsScreen(
                                cultoId = cultoId,
                                culto = culto,
                                louvores = louvores,
                                onNavigateBack = { navController.popBackStack() },
                                onAddLouvorClicked = { id -> navController.navigate("louvor_add/$id") },
                                onLouvorSelected = { louvorId -> navController.navigate("louvor_details/$louvorId") },
                                onOpenYoutubeUrl = { url -> openYoutubeLink(this@MainActivity, url) },
                                onUpdateCulto = { updatedCulto -> viewModel.updateCulto(updatedCulto) },
                                onSyncCultoWithCloud = { id, updatedCulto, updatedLouvores ->
                                    viewModel.syncCultoWithCloud(id, updatedCulto, updatedLouvores)
                                }
                            )
                        }

                        composable(
                            route = "louvor_add/{cultoId}",
                            arguments = listOf(navArgument("cultoId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val cultoId = backStackEntry.arguments?.getInt("cultoId") ?: 0
                            AddLouvorScreen(
                                cultoId = cultoId,
                                onNavigateBack = { navController.popBackStack() },
                                onSaveLouvor = { cId, nome, youtube, cantor, tom, obs, stat ->
                                    viewModel.addLouvor(cId, nome, youtube, cantor, tom, obs, stat) {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }

                        composable(
                            route = "louvor_details/{louvorId}",
                            arguments = listOf(navArgument("louvorId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val louvorId = backStackEntry.arguments?.getInt("louvorId") ?: 0
                            val louvor by viewModel.getLouvorById(louvorId).collectAsStateWithLifecycle(initialValue = null)

                            LouvorDetailsScreen(
                                louvor = louvor,
                                onNavigateBack = { navController.popBackStack() },
                                onStatusChanged = { updatedLouvor, newStatus ->
                                    viewModel.updateLouvorStatus(updatedLouvor, newStatus)
                                },
                                onDeleteLouvor = { deletedLouvor ->
                                    viewModel.deleteLouvor(deletedLouvor)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
