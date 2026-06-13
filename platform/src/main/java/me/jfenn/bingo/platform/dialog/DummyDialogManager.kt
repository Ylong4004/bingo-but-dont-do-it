package me.jfenn.bingo.platform.dialog

import me.jfenn.bingo.platform.IPlayerHandle

object DummyDialogManager : IDialogManager {
    override fun showDialog(
        player: IPlayerHandle,
        dialog: IDialogHandle
    ) = Unit
}