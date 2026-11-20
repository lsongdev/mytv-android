package me.lsong.mytv.ui.focus

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/** Main-thread registry: deactivated lazy items unregister even if their slots are retained. */
internal class PlacedFocusTargets {
    private val targets = mutableMapOf<String,FocusRequester>()
    fun requester(id: String?): FocusRequester? = targets[id]
    fun place(id: String,requester: FocusRequester) { targets[id] = requester }
    fun remove(id: String,requester: FocusRequester) { if (targets[id] === requester) targets.remove(id) }
}

internal fun Modifier.placedFocusTarget(id: String,requester: FocusRequester,owner: PlacedFocusTargets): Modifier =
    this.then(FocusRegistration(id,requester,owner))

private class FocusRegistration(val id: String,val requester: FocusRequester,val owner: PlacedFocusTargets) : ModifierNodeElement<PlacedFocusNode>() {
    override fun create(): PlacedFocusNode = PlacedFocusNode(id,requester,owner)
    override fun update(node: PlacedFocusNode) { node.unregister(); node.id = id; node.requester = requester; node.owner = owner }
    override fun InspectorInfo.inspectableProperties() { name = "placedFocusTarget" }
    override fun equals(other: Any?) = other is FocusRegistration && other.id == id && other.requester === requester && other.owner === owner
    override fun hashCode() = 31 * (31 * id.hashCode() + requester.hashCode()) + owner.hashCode()
}
private class PlacedFocusNode(var id: String,var requester: FocusRequester,var owner: PlacedFocusTargets) : Modifier.Node(),LayoutAwareModifierNode {
    override fun onPlaced(coordinates: LayoutCoordinates) { owner.place(id,requester) }
    fun unregister() = owner.remove(id,requester)
    override fun onDetach() = unregister()
}
