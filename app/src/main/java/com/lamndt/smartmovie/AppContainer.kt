package com.lamndt.smartmovie

import android.content.Context
import com.lamndt.smartmovie.data.DefaultCatalogRepository
import com.lamndt.smartmovie.data.DefaultLibraryRepository
import com.lamndt.smartmovie.data.DurableAccountMutationOutbox
import com.lamndt.smartmovie.data.ImageUrlFactory
import com.lamndt.smartmovie.database.SmartMovieDatabase
import com.lamndt.smartmovie.model.CatalogRepository
import com.lamndt.smartmovie.model.AccountRepository
import com.lamndt.smartmovie.model.CatalogV2Repository
import com.lamndt.smartmovie.model.ImageConfiguration
import com.lamndt.smartmovie.model.LibraryRepository
import com.lamndt.smartmovie.model.LibrarySyncRepository
import com.lamndt.smartmovie.network.AccountNetworkRepository
import com.lamndt.smartmovie.network.CatalogNetworkDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class AppContainer(context: Context, baseUrl: String) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = SmartMovieDatabase.create(context)
    private val network = CatalogNetworkDataSource(context, baseUrl)
    val catalog: CatalogV2Repository = DefaultCatalogRepository(network)
    val library: LibrarySyncRepository = DefaultLibraryRepository(database)
    val account: AccountRepository = AccountNetworkRepository(context, baseUrl)
    val accountOutbox = DurableAccountMutationOutbox(database, account)
    val preferences = CatalogPreferences(context)
    val accountSession = AccountSessionController(account, library, accountOutbox, applicationScope)
    val watchRemote: PhoneWatchRemoteController by lazy {
        PhoneWatchRemoteController(context, library, applicationScope)
    }

    private val imageConfiguration = MutableStateFlow(ImageConfiguration.Fallback)
    val images = ImageUrlFactory(imageConfiguration::value)

    init {
        applicationScope.launch { imageConfiguration.value = catalog.imageConfiguration() }
        accountSession.refresh(com.lamndt.smartmovie.model.CatalogLocale.from(java.util.Locale.getDefault().language, java.util.Locale.getDefault().country))
        applicationScope.launch {
            while (isActive) {
                delay(30_000)
                accountSession.flushOutbox()
            }
        }
    }
}
