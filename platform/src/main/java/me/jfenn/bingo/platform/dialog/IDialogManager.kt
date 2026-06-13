package me.jfenn.bingo.platform.dialog

import me.jfenn.bingo.platform.IPlayerHandle

interface IDialogManager {
    fun noticeBuilder(): INoticeDialogBuilder? = null
    fun confirmationBuilder(): IConfirmationDialogBuilder? = null
    fun multiActionBuilder(): IMultiActionDialogBuilder? = null
    fun showDialog(player: IPlayerHandle, dialog: IDialogHandle)
}

interface IDialogHandle
