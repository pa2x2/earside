package eu.darken.capod.rules.ui

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavigationEntry
import eu.darken.capod.rules.ui.editor.RuleEditorScreenHost
import eu.darken.capod.rules.ui.list.DeviceRulesScreenHost
import javax.inject.Inject

class DeviceRulesNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<Nav.Main.DeviceRules> { key ->
            DeviceRulesScreenHost(profileId = key.profileId)
        }
        entry<Nav.Main.DeviceRuleEditor> { key ->
            RuleEditorScreenHost(profileId = key.profileId, ruleId = key.ruleId)
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds
        @IntoSet
        abstract fun bind(entry: DeviceRulesNavigation): NavigationEntry
    }
}
