package me.jfenn.bingo.client.impl

import me.jfenn.bingo.client.platform.ITabsWidget
import me.jfenn.bingo.client.platform.ITabsWidgetFactory
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.common.utils.EventListener
import me.jfenn.bingo.platform.utils.IEventListener
import net.minecraft.client.gui.ScreenRect
import net.minecraft.client.gui.tab.Tab
import net.minecraft.client.gui.tab.TabManager
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TabNavigationWidget
import net.minecraft.text.Text
import java.util.function.Consumer

class TabsWidgetImpl(
    private val tabImpls: List<TabImpl>,
    private val manager: TabManager,
    override var widget: TabNavigationWidget,
    val onTabUpdate: EventListener<Unit>,
) : ITabsWidget {
    override val height: Int = 24
    override var width: Int = 0
        set(value) {
            field = value
            widget.setWidth(value)
            widget.init()
        }

    override var currentTab: Int
        get() = tabImpls.indexOf(manager.currentTab)
        set(value) {
            widget.selectTab(value, false)
        }

    override val onTabChanged: IEventListener<Int> = EventListener()

    private var prevTab: Int = currentTab
    init {
        onTabUpdate {
            val tab = currentTab
            if (prevTab != tab) {
                prevTab = tab
                onTabChanged(tab)
            }
        }
    }
}

class TabImpl(private val text: Text) : Tab {
    override fun getTitle(): Text {
        return text
    }

    override fun forEachChild(consumer: Consumer<ClickableWidget?>?) {
        // Send at least one event to run onTabChanged
        consumer?.accept(null)
    }

    override fun refreshGrid(tabArea: ScreenRect?) {}

    override fun getNarratedHint(): Text = text
}

object TabsWidgetFactory : ITabsWidgetFactory {
    override fun create(tabs: List<IText>): ITabsWidget {
        val onTabUpdate = EventListener<Unit>()
        val manager = TabManager({ onTabUpdate.invoke(Unit) }, {})
        val tabImpls = tabs.map { TabImpl(it.value) }

        return TabsWidgetImpl(
            tabImpls = tabImpls,
            manager = manager,
            widget = TabNavigationWidget.builder(manager, 0)
                .tabs(*tabImpls.toTypedArray())
                .build(),
            onTabUpdate = onTabUpdate,
        )
    }
}
