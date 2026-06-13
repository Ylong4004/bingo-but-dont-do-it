package me.jfenn.bingo.client.platform.renderer

interface IMatrixStack {
    fun translate(x: Float, y: Float, z: Float)
    fun scale(x: Float, y: Float, z: Float)

    /**
     * @param angle The angle in radians
     */
    fun rotate(angle: Float)

    fun push()
    fun pop()
}

inline fun IMatrixStack.use(cb: IMatrixStack.() -> Unit) {
    try {
        push()
        cb()
    } finally {
        pop()
    }
}
