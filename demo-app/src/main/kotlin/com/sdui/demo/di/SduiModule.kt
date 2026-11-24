package com.sdui.demo.di

import com.sdui.Sdui
import com.sdui.SduiEngine
import com.sdui.analytics.AnalyticsDispatcher
import com.sdui.navigation.NavigationManager
import com.sdui.state.StateStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bridges the SDK into the app's Hilt graph.
 *
 * The SDK builds its own object graph with explicit constructor wiring, which is
 * what lets it drop into an app using Hilt, Koin or nothing at all. A host that
 * *does* use Hilt exposes the pieces it needs with a module like this one, and
 * injects them into view models the ordinary way.
 */
@Module
@InstallIn(SingletonComponent::class)
object SduiModule {

    @Provides
    @Singleton
    fun provideEngine(): SduiEngine = Sdui.engine ?: error(
        "Sdui.initialize() must run in Application.onCreate() before injection.",
    )

    @Provides
    fun provideNavigator(engine: SduiEngine): NavigationManager = engine.navigator

    @Provides
    fun provideGlobalState(engine: SduiEngine): StateStore = engine.stateManager.global

    @Provides
    fun provideAnalytics(engine: SduiEngine): AnalyticsDispatcher = engine.analytics
}
