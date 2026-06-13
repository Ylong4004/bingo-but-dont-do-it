package me.jfenn.bingo.common.utils

import org.joml.Vector3d
import org.joml.Vector3f

operator fun Vector3d.plus(other: Vector3d): Vector3d {
    return add(other, Vector3d())
}

operator fun Vector3d.plusAssign(other: Vector3d) {
    add(other)
}

fun Vector3f.toVector3d() = Vector3d(x.toDouble(), y.toDouble(), z.toDouble())

fun Vector3d.toVector3f() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
