package com.lamndt.smartmovie

import android.content.Context
import com.lamndt.smartmovie.data.DefaultCatalogRepository
import com.lamndt.smartmovie.data.DefaultLibraryRepository
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.database.SmartMovieDatabase
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.network.CatalogNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AppContainer(context: Context, baseUrl: String) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = SmartMovieDatabase.create(context)
    private val network = CatalogNetworkDataSource(context, baseUrl)
    val catalog: CatalogRepository = DefaultCatalogRepository(network)
    val library: LibraryRepository = DefaultLibraryRepository(database)
    val watchRemote: PhoneWatchRemoteController by lazy {
        PhoneWatchRemoteController(context, library, applicationScope)
    }

    private val imageConfiguration = MutableStateFlow(ImageConfiguration.Fallback)
    val images = ImageUrlFactory(imageConfiguration::value)

    init {
        applicationScope.launch { imageConfiguration.value = catalog.imageConfiguration() }
    }
}
