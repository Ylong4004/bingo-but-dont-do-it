package me.jfenn.bingo.impl

import com.mojang.authlib.GameProfile
import me.jfenn.bingo.impl.inventory.ContainerItemView
import me.jfenn.bingo.platform.*
import me.jfenn.bingo.platform.block.BlockPosition
import me.jfenn.bingo.platform.commands.ISignedMessage
import me.jfenn.bingo.platform.inventory.IContainerItemView
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.item.IItemStackFactory
import me.jfenn.bingo.platform.player.PlayerProfile
import me.jfenn.bingo.platform.scope.BingoKoin
import me.jfenn.bingo.platform.text.IText
import net.minecraft.command.CommandSource
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.*
import net.minecraft.item.consume.UseAction
import net.minecraft.network.message.MessageType
import net.minecraft.network.message.SentMessage
import net.minecraft.network.packet.c2s.common.SyncedClientOptions
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.registry.Registries
import net.minecraft.screen.AbstractCraftingScreenHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.hit.HitResult
import net.minecraft.world.GameMode
import org.joml.Vector3d
import java.util.*

class PlayerManager(
    private val server: MinecraftServer,
    private val itemStackFactory: IItemStackFactory,
) : IPlayerManager {
    override fun forPlayer(player: ServerPlayerEntity): IPlayerHandle {
        return PlayerHandle(player, itemStackFactory)
    }

    override fun getPlayer(uuid: UUID): IPlayerHandle? {
        return server.playerManager.getPlayer(uuid)
            ?.let { PlayerHandle(it, itemStackFactory) }
    }

    override fun getPlayers(): List<IPlayerHandle> {
        return server.playerManager.playerList
            .map { PlayerHandle(it, itemStackFactory) }
    }

    override fun updatePlayerListName(player: IPlayerHandle) {
        val packet = PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player.player)
        server.playerManager.sendToAll(packet)
    }

    override fun getOfflinePlayer(profile: PlayerProfile): IPlayerHandle {
        val player = ServerPlayerEntity(
            server,
            server.overworld,
            GameProfile(profile.uuid, profile.name),
            SyncedClientOptions.createDefault(),
        )

        server.playerManager.loadPlayerData(player)
        return PlayerHandle(player, itemStackFactory)
    }

    override fun playToAround(
        player: ServerPlayerEntity?,
        sound: PlayerSoundEvent,
        category: PlayerSoundCategory,
        volume: Float,
        pitch: Float,
        position: Triple<Double, Double, Double>,
        world: ServerWorld,
    ) {
        val (x, y, z) = position
        val soundEntry = Registries.SOUND_EVENT.getEntry(sound.toSoundEvent())
        val packet = PlaySoundS2CPacket(soundEntry, category.toSoundCategory(), x, y, z, volume, pitch, server.overworld.seed)
        player?.networkHandler?.sendPacket(packet)
        server.playerManager.sendToAround(player, x, y, z, soundEntry.value().getDistanceToTravel(volume).toDouble(), world.registryKey, packet)
    }

    override fun broadcastChatMessage(message: ISignedMessage, sender: IPlayerHandle) {
        require(message is SignedMessageImpl)
        require(sender is PlayerHandle)

        val parameters: MessageType.Parameters = MessageType.params(MessageType.CHAT, sender.player)
        server.playerManager.broadcast(message.message, sender.player, parameters)
    }
}

class PlayerHandle(
    override val player: ServerPlayerEntity,
    private val itemStackFactory: IItemStackFactory? = null,
) : IPlayerHandle, LivingEntityImpl(player) {

    private fun getItemStackFactory() = itemStackFactory
        ?: BingoKoin.getScope(player.server)!!.get<IItemStackFactory>()

    override val world: IServerWorld
        get() = ServerWorldImpl(player.serverWorld)

    override val server: IMinecraftServer?
        get() = player.server?.let { MinecraftServerImpl(it) }

    override val isAlive: Boolean
        get() = player.isAlive
    override val isSpectator: Boolean
        get() = player.isSpectator

    override val playerName: String
        get() = player.nameForScoreboard

    override val commandSource: CommandSource
        get() = player.getCommandSource(player.serverWorld)

    override var fireTicks: Int by player::fireTicks
    override var isOnFire: Boolean
        get() = player.isOnFire
        set(value) {
            player.isOnFire = value
        }

    override var foodLevel: Int by player.hungerManager::foodLevel
    override var saturationLevel: Float by player.hungerManager::saturationLevel
    override var exhaustion: Float by player.hungerManager.accessor::exhaustion

    override var experienceLevel: Int
        get() = player.experienceLevel
        set(value) { player.setExperienceLevel(value) }

    override var experienceProgress: Float
        get() = player.experienceProgress
        set(value) { player.setExperiencePoints((value * player.nextLevelExperience).toInt()) }

    override val isSneaking: Boolean
        get() = player.isSneaking

    override fun sendMessage(message: IText) {
        player.sendMessage(message.value)
    }

    override fun sendHotbarMessage(message: IText) {
        player.sendMessage(message.value, true)
    }

    override fun sendChatMessage(message: ISignedMessage, sender: IPlayerHandle) {
        require(message is SignedMessageImpl)
        require(sender is PlayerHandle)

        val parameters: MessageType.Parameters = MessageType.params(MessageType.CHAT, sender.player)
        player.sendChatMessage(SentMessage.of(message.message), false, parameters)
    }

    override fun sendTeamMessage(message: ISignedMessage, sender: IPlayerHandle, teamName: IText) {
        require(message is SignedMessageImpl)
        require(sender is PlayerHandle)

        val parameters: MessageType.Parameters = MessageType.params(
            if (sender.uuid == this.uuid) MessageType.TEAM_MSG_COMMAND_OUTGOING else MessageType.TEAM_MSG_COMMAND_INCOMING,
            sender.player
        ).withTargetName(teamName.value)
        player.sendChatMessage(SentMessage.of(message.message), false, parameters)
    }

    override fun sendTeamMessage(message: IText, sender: IPlayerHandle, teamName: IText) {
        require(sender is PlayerHandle)

        val parameters: MessageType.Parameters = MessageType.params(
            if (sender.uuid == this.uuid) MessageType.TEAM_MSG_COMMAND_OUTGOING else MessageType.TEAM_MSG_COMMAND_INCOMING,
            sender.player
        ).withTargetName(teamName.value)
        val decoratedMessage = parameters.type.value().chat.apply(message.value, parameters)

        player.sendMessage(decoratedMessage)
    }

    override fun sendTitle(title: IText, subtitle: IText?) {
        player.networkHandler.sendPacket(TitleS2CPacket(title.value))
        player.networkHandler.sendPacket(SubtitleS2CPacket(subtitle?.value ?: Text.empty()))
    }

    override fun hasPermissionLevel(level: Int): Boolean {
        return player.hasPermissionLevel(level)
    }

    override fun canUseItem(stack: IItemStack): Boolean {
        val itemStack: ItemStack = stack.stack
        return when {
            itemStack.isEmpty -> false
            itemStack.useAction == UseAction.EAT -> player.canConsume(false)
            itemStack.useAction == UseAction.DRINK -> true
            itemStack.useAction == UseAction.BLOCK -> true // blocking with a shield
            itemStack.item is BoatItem -> true // trying to place a boat
            itemStack.item is BlockItem -> {
                // if holding a block, check if it can be placed on a surface within reach
                val hit = player.raycast(5.0, 0f, false)
                hit.type == HitResult.Type.BLOCK
            }
            itemStack.item == Items.FIREWORK_ROCKET -> player.isGliding || player.isTouchingWater
            itemStack.item == Items.SUSPICIOUS_STEW -> true
            itemStack.isUsedOnRelease -> true
            itemStack.useAction == UseAction.NONE -> false
            else -> true
        }
    }

    override var gameMode: PlayerGameMode
        get() = player.interactionManager.gameMode.toGameMode()
        set(value) {
            player.changeGameMode(value.toGameMode())
        }

    override val mainHandStack: IItemStack
        get() = getItemStackFactory().forStack(player.mainHandStack)

    override val offHandStack: IItemStack
        get() = getItemStackFactory().forStack(player.offHandStack)

    private fun allNestedStacks(stack: ItemStack): Sequence<Pair<ItemStack, ItemStack>> = sequence {
        stack[DataComponentTypes.CONTAINER]?.iterateNonEmpty()
            ?.forEach {
                yield(Pair(stack, it))
                yieldAll(allNestedStacks(it))
            }
        stack[DataComponentTypes.BUNDLE_CONTENTS]?.iterate()
            ?.forEach {
                yield(Pair(stack, it))
                yieldAll(allNestedStacks(it))
            }
    }

    override fun allHeldStackViews(): Sequence<IContainerItemView> {
        val itemStackFactory = getItemStackFactory()
        return sequence {
            // yield all items in inventory
            for (i in 0 until player.inventory.size()) {
                yield(player.inventory.getStack(i))
            }

            // yield the current cursor stack
            yield(player.currentScreenHandler.cursorStack)

            // yield items held in crafting inputs
            player.currentScreenHandler?.let { screenHandler ->
                if (screenHandler is AbstractCraftingScreenHandler) {
                    for (slot in screenHandler.inputSlots) {
                        yield(slot.stack)
                    }
                }
            }

            // yield ender chest inventory
            yieldAll(player.enderChestInventory.heldStacks)
        }
            .filter { !it.isEmpty }
            .flatMap { sequenceOf(Pair(null, it)) + allNestedStacks(it) }
            .filter { !it.second.isEmpty }
            .map { (container, stack) ->
                val stackImpl = itemStackFactory.forStack(stack)
                if (container?.item is BundleItem) {
                    ContainerItemView.Bundle(container, stackImpl)
                } else if (container?.get(DataComponentTypes.CONTAINER) != null) {
                    ContainerItemView.Container(container, stackImpl)
                } else {
                    ContainerItemView.Inventory(stackImpl)
                }
            }
    }

    override fun allInventorySlots(): Sequence<Pair<Int, IItemStack>> {
        val itemStackFactory = getItemStackFactory()
        return sequence {
            // yield all items in inventory
            for (i in 0 until player.inventory.size()) {
                yield(Pair(i, player.inventory.getStack(i)))
            }
        }.map { (i, stack) ->
            i to itemStackFactory.forStack(stack)
        }
    }

    override fun giveOrEquipStack(stack: IItemStack) {
        val itemStack: ItemStack = stack.stack
        val equippable = itemStack[DataComponentTypes.EQUIPPABLE]

        // if the item can be equipped (e.g. iron chestplate), put it in an equipment slot
        val slot = equippable?.slot
        if (slot != null && player.canEquip(itemStack, slot) && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            player.equipStack(slot, itemStack)
        } else {
            // otherwise, put it in the player's inventory
            player.giveItemStack(itemStack)
        }
    }

    override fun giveItemStack(stack: IItemStack) {
        player.giveItemStack(stack.stack)
    }

    override fun removeStack(slot: Int) {
        player.inventory.removeStack(slot)
    }

    override fun setStack(slot: Int, stack: IItemStack) {
        if (slot in 0 until PlayerInventory.MAIN_SIZE) {
            player.getStackReference(slot).set(stack.stack)
        } else {
            player.inventory.setStack(slot, stack.stack)
        }
    }

    override fun playSound(sound: PlayerSoundEvent, category: PlayerSoundCategory, volume: Float, pitch: Float) {
        player.playSoundToPlayer(
            sound.toSoundEvent(),
            category.toSoundCategory(),
            volume,
            pitch,
        )
    }

    override val serverWorld: ServerWorld by player::serverWorld

    override val blockPos: BlockPosition
        get() = BlockPosition(player.blockX, player.blockY, player.blockZ)

    override fun respawn(): IPlayerHandle {
        val networkHandler = player.networkHandler
        networkHandler.onClientStatus(ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN))
        return PlayerHandle(networkHandler.player, itemStackFactory)
    }

    override fun teleport(world: IServerWorld, pos: Vector3d, yaw: Float, pitch: Float) {
        player.teleport(world.world, pos.x, pos.y, pos.z, emptySet(), yaw, pitch, true)
    }

    override fun setSpawnPoint(
        world: IServerWorld,
        spawn: BlockPosition,
        angle: Float,
        forced: Boolean,
        sendMessage: Boolean
    ) {
        val serverWorld: ServerWorld = world.world
        player.setSpawnPoint(
            ServerPlayerEntity.Respawn(serverWorld.registryKey, spawn.toBlockPos(), angle, forced),
            sendMessage
        )
    }

    override fun startRiding(entity: IEntity, force: Boolean) {
        player.startRiding(entity.entity, true)
    }

    override val abilities: IPlayerAbilities
        get() = object : IPlayerAbilities {
            override var allowFlying by player.abilities::allowFlying
        }

    override fun sendAbilitiesUpdate() {
        player.sendAbilitiesUpdate()
    }
}

private fun PlayerSoundEvent.toSoundEvent() = when (this) {
    PlayerSoundEvent.BLOCK_LEVER_CLICK -> SoundEvents.BLOCK_LEVER_CLICK
    PlayerSoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON -> SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON
    PlayerSoundEvent.BLOCK_WOODEN_BUTTON_CLICK_OFF -> SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_OFF
    PlayerSoundEvent.ENTITY_PLAYER_LEVELUP -> SoundEvents.ENTITY_PLAYER_LEVELUP
    PlayerSoundEvent.ENTITY_SHULKER_AMBIENT -> SoundEvents.ENTITY_SHULKER_AMBIENT
    PlayerSoundEvent.ENTITY_TNT_PRIMED -> SoundEvents.ENTITY_TNT_PRIMED
    PlayerSoundEvent.BLOCK_NOTE_BLOCK_BASS -> SoundEvents.BLOCK_NOTE_BLOCK_BASS.value()
    PlayerSoundEvent.ITEM_LODESTONE_COMPASS_LOCK -> SoundEvents.ITEM_LODESTONE_COMPASS_LOCK
    PlayerSoundEvent.BLOCK_PORTAL_TRAVEL -> SoundEvents.BLOCK_PORTAL_TRAVEL
    PlayerSoundEvent.BLOCK_NOTE_BLOCK_PLING -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
}

private fun PlayerSoundCategory.toSoundCategory() = when (this) {
    PlayerSoundCategory.MAIN -> SoundCategory.MASTER
    PlayerSoundCategory.RECORDS -> SoundCategory.RECORDS
    PlayerSoundCategory.BLOCKS -> SoundCategory.BLOCKS
}

private fun GameMode.toGameMode() = when (this) {
    GameMode.SURVIVAL -> PlayerGameMode.SURVIVAL
    GameMode.CREATIVE -> PlayerGameMode.CREATIVE
    GameMode.ADVENTURE -> PlayerGameMode.ADVENTURE
    GameMode.SPECTATOR -> PlayerGameMode.SPECTATOR
}

private fun PlayerGameMode.toGameMode() = when (this) {
    PlayerGameMode.SURVIVAL -> GameMode.SURVIVAL
    PlayerGameMode.CREATIVE -> GameMode.CREATIVE
    PlayerGameMode.ADVENTURE -> GameMode.ADVENTURE
    PlayerGameMode.SPECTATOR -> GameMode.SPECTATOR
}