package me.jfenn.bingo.platform.item

interface IFireworkRocket : IItemStack {
    // This is just a List<Unit> because it'll only ever be set to emptyList() at the moment...
    // ...but this may be expanded upon in the future
    var fireworks: List<Unit>?
}