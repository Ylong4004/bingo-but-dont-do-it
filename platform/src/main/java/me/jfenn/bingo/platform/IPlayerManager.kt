package me.jfenn.bingo.platform

import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.commands.ISignedMessage
import me.jfenn.bingo.platform.inventory.IContainerItemView
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.text.IText
import net.minecraft.command.CommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import org.joml.Vector3d
import java.util.*

interface IPlayerManager {

    fun forPlayer(player: ServerPlayerEntity): IPlayerHandle

    fun getPlayer(uuid: UUID): IPlayerHandle?

    fun getPlayers(): List<IPlayerHandle>

    fun getOfflinePlayer(profile: PlayerProfile): IPlayerHandle

    fun updatePlayerListName(player: IPlayerHandle)

    /**
     * Sends a sound manually to [player], and broadcasts a
     * sound event to surrounding players.
     *
     * If lobbyChaosProtection=true, this will result in
     * only [player] hearing the sound.
     */
    fun playToAround(player: ServerPlayerEntity?, sound: PlayerSoundEvent, category: PlayerSoundCategory, volume: Float, pitch: Float, position: Triple<Double, Double, Double>, world: ServerWorld)

    fun broadcastChatMessage(message: ISignedMessage, sender: IPlayerHandle)

}

interface IPlayerHandle : ILivingEntity {
    val player: ServerPlayerEntity
    val server: IMinecraftServer?
    val world: IServerWorld
    val isAlive: Boolean
    val isSpectator: Boolean
    val playerName: String
    val commandSource: CommandSource

    var fireTicks: Int
    var isOnFire: Boolean
    var foodLevel: Int
    val maxFoodLevel: Int get() = 20
    var saturationLevel: Float
    val maxSaturationLevel: Float get() = 5f
    var exhaustion: Float
    val isSneaking: Boolean
    var experienceLevel: Int
    var experienceProgress: Float

    val profile get() = PlayerProfile(uuid, playerName)

    /**
     * vaguely determine if the held item will be used or not
     * (e.g. suspicious stew, potions, etc. - vs. static items such as iron ingots)
     */
    fun canUseItem(stack: IItemStack): Boolean

    var gameMode: PlayerGameMode
    val mainHandStack: IItemStack
    val offHandStack: IItemStack
    fun allHeldStackViews(): Sequence<IContainerItemView>
    fun allHeldStacks(): Sequence<IItemStack> = allHeldStackViews().map { it.stack }
    fun allInventorySlots(): Sequence<Pair<Int, IItemStack>>

    /**
     * Give the player an ItemStack, equipping it in the preferred slot if armor,
     * and otherwise placing it in the inventory
     */
    fun giveOrEquipStack(stack: IItemStack)

    fun giveItemStack(stack: IItemStack)
    fun removeStack(slot: Int)
    fun setStack(slot: Int, stack: IItemStack)

    fun playSound(sound: PlayerSoundEvent, category: PlayerSoundCategory, volume: Float, pitch: Float)

    val serverWorld: ServerWorld
    val blockPos: BlockPosition

    fun respawn(): IPlayerHandle
    fun teleport(world: IServerWorld, pos: Vector3d, yaw: Float, pitch: Float)
    fun setSpawnPoint(world: IServerWorld, spawn: BlockPosition, angle: Float, forced: Boolean, sendMessage: Boolean)

    fun hasPermissionLevel(level: Int): Boolean
    fun sendMessage(message: IText)
    fun sendHotbarMessage(message: IText)
    fun sendChatMessage(message: ISignedMessage, sender: IPlayerHandle)
    fun sendTeamMessage(message: ISignedMessage, sender: IPlayerHandle, teamName: IText)
    fun sendTeamMessage(message: IText, sender: IPlayerHandle, teamName: IText)

    fun sendTitle(title: IText, subtitle: IText?)

    fun startRiding(entity: IEntity, force: Boolean)

    val abilities: IPlayerAbilities
    fun sendAbilitiesUpdate()
}

interface IPlayerAbilities {
    var allowFlying: Boolean
}

enum class PlayerSoundEvent {
    BLOCK_LEVER_CLICK,
    BLOCK_WOODEN_BUTTON_CLICK_ON,
    BLOCK_WOODEN_BUTTON_CLICK_OFF,
    ENTITY_PLAYER_LEVELUP,
    ENTITY_SHULKER_AMBIENT,
    ENTITY_TNT_PRIMED,
    BLOCK_NOTE_BLOCK_BASS,
    ITEM_LODESTONE_COMPASS_LOCK,
    BLOCK_PORTAL_TRAVEL,
    BLOCK_NOTE_BLOCK_PLING,
}

enum class PlayerSoundCategory {
    MAIN,
    RECORDS,
    BLOCKS,
}

enum class PlayerGameMode {
    SURVIVAL,
    CREATIVE,
    ADVENTURE,
    SPECTATOR
}
