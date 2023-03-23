package at.hannibal2.skyhanni.features.garden

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.GardenToolChangeEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.PacketEvent
import at.hannibal2.skyhanni.events.ProfileJoinEvent
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.NEUItems
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

class GardenAPI {
    var tick = 0

    @SubscribeEvent
    fun onSendPacket(event: PacketEvent.SendEvent) {
        if (!inGarden()) return
        if (event.packet !is C09PacketHeldItemChange) return
        checkItemInHand()
    }

    @SubscribeEvent
    fun onCloseWindow(event: GuiContainerEvent.CloseWindowEvent) {
        if (!inGarden()) return
        checkItemInHand()
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (!inGarden()) return
        tick++
        if (tick % 10 == 0) {
            // We ignore random hypixel moments
            Minecraft.getMinecraft().currentScreen ?: return
            checkItemInHand()
        }
    }

    private fun checkItemInHand() {
        val toolItem = Minecraft.getMinecraft().thePlayer.heldItem
        val crop = getCropTypeFromItem(toolItem)
        val newTool = getToolInHand(toolItem, crop)
        if (toolInHand != newTool) {
            toolInHand = newTool
            cropInHand = crop
            GardenToolChangeEvent(crop, toolItem).postAndCatch()
        }
    }

    private fun getToolInHand(toolItem: ItemStack?, crop: CropType?): String? {
        if (crop != null) return crop.cropName

        val internalName = toolItem?.getInternalName() ?: return null
        return if (internalName.startsWith("DAEDALUS_AXE")) "Other Tool" else null
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    fun onProfileJoin(event: ProfileJoinEvent) {
        if (cropsPerSecond.isEmpty()) {
            for (cropType in CropType.values()) {
                cropsPerSecond[cropType] = -1
            }
        }
    }

    companion object {
        fun inGarden() = LorenzUtils.inSkyBlock && LorenzUtils.skyBlockIsland == IslandType.GARDEN

        var toolInHand: String? = null
        val cropsPerSecond: MutableMap<CropType, Int> get() = SkyHanniMod.feature.hidden.gardenCropsPerSecond
        var cropInHand: CropType? = null

        fun getCropTypeFromItem(item: ItemStack?): CropType? {
            val internalName = item?.getInternalName() ?: return null
            return CropType.values().firstOrNull { internalName.startsWith(it.toolName) }
        }

        fun readCounter(itemStack: ItemStack): Int {
            if (itemStack.hasTagCompound()) {
                val tag = itemStack.tagCompound
                if (tag.hasKey("ExtraAttributes", 10)) {
                    val ea = tag.getCompoundTag("ExtraAttributes")
                    if (ea.hasKey("mined_crops", 99)) {
                        return ea.getInteger("mined_crops")
                    }

                    // only using cultivating when no crops counter is there
                    if (ea.hasKey("farmed_cultivating", 99)) {
                        return ea.getInteger("farmed_cultivating")
                    }
                }
            }
            return -1
        }

        fun getCropsPerSecond(crop: CropType): Int {
            return cropsPerSecond[crop]!!
        }

        fun itemNameToCropName(itemName: String): CropType? {
            if (itemName == "Red Mushroom" || itemName == "Brown Mushroom") {
                return CropType.MUSHROOM
            }
            return CropType.getByName(itemName)
        }

        private fun getItemStackForCrop(crop: CropType): ItemStack {
            val cropName = if (crop == CropType.MUSHROOM) "Red Mushroom Block" else crop.cropName
            val internalName = NEUItems.getInternalName(cropName)
            return NEUItems.getItemStack(internalName)
        }

        fun addGardenCropToList(crop: CropType, list: MutableList<Any>) {
            try {
                list.add(getItemStackForCrop(crop))
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }
    }
}