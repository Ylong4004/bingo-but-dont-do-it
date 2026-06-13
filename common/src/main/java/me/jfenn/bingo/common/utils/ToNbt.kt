package me.jfenn.bingo.common.utils

import net.minecraft.nbt.NbtDouble
import net.minecraft.nbt.NbtFloat
import net.minecraft.nbt.NbtList
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3d

fun Vec3d.toNbt(): NbtList {
    return NbtList().apply {
        add(NbtDouble.of(x))
        add(NbtDouble.of(y))
        add(NbtDouble.of(z))
    }
}

fun Vector3d.toNbt(): NbtList {
    return NbtList().apply {
        add(NbtDouble.of(x))
        add(NbtDouble.of(y))
        add(NbtDouble.of(z))
    }
}

fun List<Float>.toNbt(): NbtList {
    val list = NbtList()
    forEach { list.add(NbtFloat.of(it)) }
    return list
}

/**
 * Formats into a row-major list of NBT floats
 */
fun Matrix4f.toNbt(): NbtList {
    val list = NbtList()
    for (row in 0..3) for (col in 0..3) {
        list.add(NbtFloat.of(this.get(col, row)))
    }
    return list
}
