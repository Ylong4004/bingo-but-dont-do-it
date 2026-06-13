package me.jfenn.bingo.platform.particle

import me.jfenn.bingo.platform.IPlayerHandle
import org.joml.Vector3d

interface IParticleFactory {
    fun createDustParticle(color: Int, scale: Float): me.jfenn.bingo.platform.particle.IParticle
}

interface IParticle {
    fun spawn(player: IPlayerHandle, position: Vector3d, count: Int, delta: Vector3d, speed: Double)
}
