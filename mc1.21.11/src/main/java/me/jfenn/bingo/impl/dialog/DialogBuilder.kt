package me.jfenn.bingo.impl.dialog

import me.jfenn.bingo.impl.TextImpl
import me.jfenn.bingo.platform.dialog.*
import me.jfenn.bingo.platform.item.IItemStack
import me.jfenn.bingo.platform.text.IText
import net.minecraft.dialog.AfterAction
import net.minecraft.dialog.DialogActionButtonData
import net.minecraft.dialog.DialogButtonData
import net.minecraft.dialog.DialogCommonData
import net.minecraft.dialog.action.DialogAction
import net.minecraft.dialog.action.DynamicRunCommandDialogAction
import net.minecraft.dialog.action.ParsedTemplate
import net.minecraft.dialog.action.SimpleDialogAction
import net.minecraft.dialog.body.DialogBody
import net.minecraft.dialog.body.ItemDialogBody
import net.minecraft.dialog.body.PlainMessageDialogBody
import net.minecraft.dialog.input.BooleanInputControl
import net.minecraft.dialog.input.TextInputControl
import net.minecraft.dialog.type.ConfirmationDialog
import net.minecraft.dialog.type.DialogInput
import net.minecraft.dialog.type.MultiActionDialog
import net.minecraft.dialog.type.NoticeDialog
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtString
import net.minecraft.text.ClickEvent
import net.minecraft.text.Text
import java.util.*

abstract class DialogBuilder : IDialogBuilder {
    override var title: IText = TextImpl(Text.empty())
    private val body = mutableListOf<DialogBody>()
    private val inputs = mutableListOf<DialogInput>()

    override fun addText(text: IText, width: Int) {
        body.add(PlainMessageDialogBody(text.value, width))
    }

    override fun addItem(item: IItemStack) {
        body.add(ItemDialogBody(item.stack, Optional.empty(), true, true, 1, 1))
    }

    override fun addInput(input: IDialogInput) {
        when (input) {
            is IDialogInput.Boolean -> {
                inputs.add(DialogInput(
                    input.key,
                    BooleanInputControl(input.label.value, false, "true", "false")
                ))
            }
            is IDialogInput.Text -> {
                inputs.add(DialogInput(
                    input.key,
                    TextInputControl(
                        input.width,
                        input.label.value,
                        true,
                        input.initial,
                        input.maxLength,
                        Optional.empty(),
                    )
                ))
            }
        }
    }

    internal fun buildCommon(): DialogCommonData {
        return DialogCommonData(
            title.value,
            Optional.empty(),
            true,
            false,
            AfterAction.CLOSE,
            body,
            inputs,
        )
    }
}

class NoticeDialogBuilder : DialogBuilder(), INoticeDialogBuilder {
    private var exitAction: DialogActionButtonData? = null

    override fun setAction(label: IText, action: IDialogAction) {
        exitAction = DialogActionButtonData(
            DialogButtonData(
                label.value,
                DialogButtonData.DEFAULT_WIDTH,
            ),
            action.toDialogAction()
        )
    }

    override fun build(): IDialogHandle {
        val dialog = NoticeDialog(
            buildCommon(),
            exitAction ?: NoticeDialog.OK_BUTTON,
        )
        return DialogHandle(dialog)
    }
}

class ConfirmationDialogBuilder : DialogBuilder(), IConfirmationDialogBuilder {
    private var yes: DialogActionButtonData? = null
    private var no: DialogActionButtonData? = null

    override fun setYes(label: IText, action: IDialogAction) {
        yes = DialogActionButtonData(
            DialogButtonData(
                label.value,
                DialogButtonData.DEFAULT_WIDTH
            ),
            action.toDialogAction()
        )
    }

    override fun setNo(label: IText, action: IDialogAction) {
        no = DialogActionButtonData(
            DialogButtonData(
                label.value,
                DialogButtonData.DEFAULT_WIDTH
            ),
            action.toDialogAction()
        )
    }

    override fun build(): IDialogHandle {
        val dialog = ConfirmationDialog(
            buildCommon(),
            requireNotNull(yes),
            requireNotNull(no)
        )
        return DialogHandle(dialog)
    }
}

class MultiActionDialogBuilder : DialogBuilder(), IMultiActionDialogBuilder {
    override var columns: Int = 2
    private val actions = mutableListOf<DialogActionButtonData>()
    private var exitAction: DialogActionButtonData? = null

    override fun addAction(label: IText, action: IDialogAction) {
        actions.add(
            DialogActionButtonData(
                DialogButtonData(
                    label.value,
                    DialogButtonData.DEFAULT_WIDTH,
                ),
                action.toDialogAction()
            )
        )
    }

    override fun setExitAction(label: IText, action: IDialogAction) {
        exitAction = DialogActionButtonData(
            DialogButtonData(
                label.value,
                DialogButtonData.DEFAULT_WIDTH,
            ),
            action.toDialogAction()
        )
    }

    override fun build(): IDialogHandle {
        if (actions.isEmpty()) {
            throw IllegalArgumentException("Dialog actions must not be empty")
        }
        val dialog = MultiActionDialog(
            buildCommon(),
            actions,
            Optional.ofNullable(exitAction),
            columns,
        )
        return DialogHandle(dialog)
    }
}

private fun IDialogAction.toDialogAction(): Optional<DialogAction> = when (this) {
    is IDialogAction.None -> Optional.empty()
    is IDialogAction.RunCommand -> {
        Optional.of(
            SimpleDialogAction(ClickEvent.RunCommand(command))
        )
    }
    is IDialogAction.DynamicRunCommand -> {
        Optional.of(
            DynamicRunCommandDialogAction(
                ParsedTemplate.CODEC.parse(NbtOps.INSTANCE, NbtString.of(command))
                    .getOrThrow { IllegalArgumentException(it) }
            )
        )
    }
}
