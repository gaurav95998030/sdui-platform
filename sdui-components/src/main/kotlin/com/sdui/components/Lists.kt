package com.sdui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdui.core.model.SduiNode
import com.sdui.renderer.ComponentRenderer
import com.sdui.renderer.LayoutParent
import com.sdui.renderer.LocalBindingScope
import com.sdui.renderer.LocalScreenRuntime
import com.sdui.renderer.RenderNode
import com.sdui.renderer.RenderScope
import com.sdui.renderer.ResolvedNode
import com.sdui.renderer.buildModifier
import com.sdui.renderer.int
import com.sdui.renderer.num
import com.sdui.renderer.str
import com.sdui.renderer.bool
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The data half of a lazy list: the bound collection plus the template that
 * renders one element.
 *
 * `itemTemplate` is the node's single child (or `props.itemTemplate`), and each
 * item is rendered with `item`/`index` in scope — the same aliases `repeat` uses,
 * so authors learn one mechanism.
 */
private class ListBinding(
    val items: List<JsonElement>,
    val template: SduiNode?,
    val itemAlias: String,
    val indexAlias: String,
)

@Composable
private fun rememberListBinding(node: ResolvedNode): ListBinding {
    val runtime = LocalScreenRuntime.current
    val binding = LocalBindingScope.current

    // `props` arrive already interpolated, so a bound `items` is normally a real
    // JsonArray by now. The raw form is only consulted when interpolation could not
    // produce one (for instance when the expression is on `repeat` instead).
    val items = remember(node.props, binding) {
        (node.props["items"] as? JsonArray)
            ?: (node.node.props.str("items") ?: node.node.repeat?.items)
                ?.let { raw ->
                    runtime.expressions.evaluate(runtime.expressions.unwrap(raw), binding) as? JsonArray
                }
            ?: JsonArray(emptyList())
    }

    return ListBinding(
        items = items.toList(),
        template = node.children.firstOrNull(),
        itemAlias = node.node.repeat?.itemAlias ?: node.props.str("as") ?: "item",
        indexAlias = node.node.repeat?.indexAlias ?: node.props.str("indexAs") ?: "index",
    )
}

/**
 * Fires `onLoadMore` when the user gets within [threshold] items of the end.
 *
 * Implemented with `snapshotFlow` rather than a recomposition check so it emits
 * exactly once per threshold crossing, not once per frame while scrolling.
 */
@Composable
private fun InfiniteScrollEffect(
    node: ResolvedNode,
    scope: RenderScope,
    state: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    threshold: Int,
) {
    val runtime = LocalScreenRuntime.current
    if (!runtime.handles(node, "onLoadMore")) return

    LaunchedEffect(state, itemCount) {
        snapshotFlow {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= itemCount - 1 - threshold
        }
            .distinctUntilChanged()
            .filter { it && itemCount > 0 }
            .collect { runtime.dispatch(node, "onLoadMore", scope.locals) }
    }
}

/**
 * `lazyColumn` — the workhorse for server-driven feeds.
 *
 * ```json
 * { "type": "lazyColumn",
 *   "props": { "items": "{{expenses}}", "spacing": 12, "pullToRefresh": true,
 *              "loadingMore": "{{ui.loadingMore}}" },
 *   "actions": { "onRefresh": {…}, "onLoadMore": {…} },
 *   "children": [ { "type": "card", "children": [ … "{{item.title}}" … ] } ] }
 * ```
 */
public object LazyColumnRenderer : ComponentRenderer {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val runtime = LocalScreenRuntime.current
        val binding = rememberListBinding(node)
        val listState = rememberLazyListState()
        val spacing = node.props.num("spacing") ?: 0f
        val refreshing = node.props.bool("refreshing")

        InfiniteScrollEffect(node, scope, listState, binding.items.size, node.props.int("loadMoreThreshold", 3))

        val content: @Composable (Modifier) -> Unit = { modifier ->
            LazyColumn(
                modifier = modifier,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(spacing.dp),
                contentPadding = node.props.num("contentPadding")?.let { PaddingValues(it.dp) }
                    ?: PaddingValues(0.dp),
                reverseLayout = node.props.bool("reverse"),
            ) {
                itemsIndexedTemplate(binding, scope, node)
                if (node.props.bool("loadingMore")) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(Modifier.padding(4.dp))
                        }
                    }
                }
            }
        }

        val outerModifier = buildModifier(node.modifier, scope.parent)

        if (node.props.bool("pullToRefresh") && runtime.handles(node, "onRefresh")) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { runtime.dispatch(node, "onRefresh", scope.locals) },
                state = rememberPullToRefreshState(),
                modifier = outerModifier,
            ) {
                content(Modifier)
            }
        } else {
            content(outerModifier)
        }
    }
}

/** `lazyRow` — horizontal carousel over the same binding contract. */
public object LazyRowRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val binding = rememberListBinding(node)
        val listState = rememberLazyListState()
        val spacing = node.props.num("spacing") ?: 0f

        InfiniteScrollEffect(node, scope, listState, binding.items.size, node.props.int("loadMoreThreshold", 3))

        LazyRow(
            modifier = buildModifier(node.modifier, scope.parent),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            contentPadding = node.props.num("contentPadding")?.let { PaddingValues(it.dp) }
                ?: PaddingValues(0.dp),
        ) {
            itemsIndexedTemplate(binding, scope, node)
        }
    }
}

/** `grid` — fixed-column or adaptive lazy grid. */
public object GridRenderer : ComponentRenderer {
    @Composable
    override fun Render(node: ResolvedNode, scope: RenderScope) {
        val binding = rememberListBinding(node)
        val spacing = node.props.num("spacing") ?: 8f
        val cells = node.props.num("minItemWidth")
            ?.let { GridCells.Adaptive(it.dp) }
            ?: GridCells.Fixed(node.props.int("columns", 2))

        LazyVerticalGrid(
            columns = cells,
            modifier = buildModifier(node.modifier, scope.parent),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            contentPadding = node.props.num("contentPadding")?.let { PaddingValues(it.dp) }
                ?: PaddingValues(0.dp),
        ) {
            val template = binding.template
            if (template != null) {
                items(binding.items.size) { index ->
                    RenderNode(
                        node = template,
                        scope = scope.copy(
                            path = "${scope.path}.grid[$index]",
                            parent = LayoutParent.None,
                            locals = scope.locals + mapOf(
                                binding.itemAlias to binding.items[index],
                                binding.indexAlias to JsonPrimitive(index),
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Emits one lazy item per element, keyed by the element's `id` when it has one so
 * Compose can reuse slots across data updates instead of rebuilding the list.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedTemplate(
    binding: ListBinding,
    scope: RenderScope,
    node: ResolvedNode,
) {
    val template = binding.template ?: return
    items(
        count = binding.items.size,
        key = { index ->
            val element = binding.items[index]
            (element as? kotlinx.serialization.json.JsonObject)
                ?.get("id")?.let { (it as? JsonPrimitive)?.content }
                ?: "${scope.path}[$index]"
        },
    ) { index ->
        RenderNode(
            node = template,
            scope = scope.copy(
                path = "${scope.path}[$index]",
                parent = LayoutParent.None,
                locals = scope.locals + mapOf(
                    binding.itemAlias to binding.items[index],
                    binding.indexAlias to JsonPrimitive(index),
                ),
            ),
        )
    }
}
