package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the preview of a function, above which optional buttons can be drawn (if focused)
 * to make changes to the item.
 */
public class MapWidgetTransferFunctionItem extends MapWidget {
    public static final int HEIGHT = 15;
    protected static final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
    protected static final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);
    protected static final byte COLOR_BG_MOVING = MapColorPalette.getColor(247, 233, 163);

    protected final List<Button> buttons = new ArrayList<>();
    private final TransferFunctionHost host;
    private final TransferFunction.Holder<TransferFunction> function;
    protected boolean moving;
    protected int selButtonIdx = 0; // -1 = function mode

    public MapWidgetTransferFunctionItem(TransferFunctionHost host, TransferFunction.Holder<TransferFunction> function) {
        this.host = host;
        this.function = function;
        this.setFocusable(true);
        this.setSize(128 - 14 - 10 /* default of a List */, HEIGHT);
    }

    public void onMoveUp() {
    }

    public void onMoveDown() {
    }

    public TransferFunction getFunction() {
        return function.getFunction();
    }

    public MapWidgetTransferFunctionItem addConfigureButton() {
        return addButton(ButtonIcon.CONFIGURE, this::configure);
    }

    public MapWidgetTransferFunctionItem addButton(ButtonIcon icon, Runnable action) {
        buttons.add(new Button(icon, action));
        return this;
    }

    public void configure() {
        // If it can't be edited, sound a hiss and do nothing more
        if (getFunction().openDialogMode() == TransferFunction.DialogMode.NONE) {
            display.playSound(SoundEffect.EXTINGUISH);
            return;
        }

        // If inline, add fake window over the top of this widget and activate to focus them
        // If no widgets are added, cancel (with a sound)
        if (getFunction().openDialogMode() == TransferFunction.DialogMode.INLINE) {
            InlineDialog inlineDialog = new InlineDialog();
            updateInlineDialogBounds(inlineDialog);
            this.addWidget(inlineDialog);
            getFunction().openDialog(inlineDialog);
            if (inlineDialog.getWidgetCount() == 0) {
                inlineDialog.close();
                display.playSound(SoundEffect.EXTINGUISH);
            } else {
                // Hides UI and focuses widgets that are children of the inline dialog
                this.activate();
            }

            return;
        }

        // If already displayed inside a transfer function dialog, navigate that dialog
        // to the new item. Otherwise, open a new dialog and edit it inside of that.
        MapWidgetTransferFunctionDialog dialog = getCurrentDialog();
        if (dialog != null) {
            dialog.navigate(function);
        } else {
            dialog = new MapWidgetTransferFunctionDialog(host, function.getFunction()) {
                @Override
                public void onChanged(TransferFunction function) {
                    MapWidgetTransferFunctionItem.this.function.setFunction(function);
                    MapWidgetTransferFunctionItem.this.invalidate();
                }
            };

            // Add it to the parent, so that when this dialog closes, it doesn't glitch out
            // and activate this widget (which normally does not support that)
            this.getParent().addWidget(dialog);
        }
    }

    public boolean isMoving() {
        return moving;
    }

    protected void setSelectedButton(int index) {
        if (index < 0) {
            index = 0;
        } else if (index >= buttons.size()) {
            index = buttons.size() - 1;
        }

        if (selButtonIdx != index) {
            selButtonIdx = index;
            invalidate();
        }
    }

    protected MapWidgetTransferFunctionDialog getCurrentDialog() {
        for (MapWidget w = this.getParent(); w != null; w = w.getParent()) {
            if (w instanceof MapWidgetTransferFunctionDialog) {
                return (MapWidgetTransferFunctionDialog) w;
            }
        }
        return null; // Not in a dialog window
    }

    @Override
    public void onFocus() {
        selButtonIdx = 0;
    }

    @Override
    public void onDraw() {
        view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
        view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                moving ? COLOR_BG_MOVING : (isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT));

        if (!isActivated()) {
            MapCanvas previewView = view.getView(2, 1, getWidth() - 2, getHeight() - 2);
            getFunction().drawPreview(this, previewView);

            drawUI();
        }
    }

    protected void updateInlineDialogBounds(InlineDialog dialog) {
        dialog.setBounds(1, 1, getWidth() - 2, getHeight() - 2);
    }

    protected void drawUI() {
        if (!moving && isFocused() && !buttons.isEmpty()) {
            int x_icon_step = buttons.get(0).icon.width() + 1;
            int x = getWidth() - buttons.size() * x_icon_step - 1;
            int i = 0;
            for (Button b : buttons) {
                view.draw(b.icon.icon(selButtonIdx == i), x, 2);
                ++i;
                x += x_icon_step;
            }
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (moving) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                onMoveUp();
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                onMoveDown();
            } else if (event.getKey() == MapPlayerInput.Key.BACK || event.getKey() == MapPlayerInput.Key.ENTER) {
                moving = false;
                MapWidgetTransferFunctionDialog dialog = getCurrentDialog();
                if (dialog != null) {
                    dialog.setExitOnBack(true);
                }
                invalidate();
            }
        } else if (event.getKey() == MapPlayerInput.Key.LEFT && isFocused()) {
            setSelectedButton(selButtonIdx - 1);
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT && isFocused()) {
            setSelectedButton(selButtonIdx + 1);
        } else if (event.getKey() == MapPlayerInput.Key.ENTER && selButtonIdx >= 0 && !buttons.isEmpty() && isFocused()) {
            buttons.get(selButtonIdx).action.run();
        } else {
            super.onKeyPressed(event);
        }
    }

    protected class InlineDialog extends MapWidget implements TransferFunction.Dialog {

        @Override
        public MapWidget getWidget() {
            return this;
        }

        @Override
        public TransferFunctionHost getHost() {
            return host;
        }

        @Override
        public void setFunction(TransferFunction function) {
            MapWidgetTransferFunctionItem.this.function.setFunction(function);
        }

        @Override
        public void markChanged() {
            setFunction(function.getFunction());
        }

        public void close() {
            this.removeWidget();
            MapWidgetTransferFunctionItem.this.focus();
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.BACK) {
                close();
            } else {
                super.onKeyPressed(event);
            }
        }
    }

    private static class Button {
        public final ButtonIcon icon;
        public Runnable action;

        public Button(ButtonIcon icon, Runnable action) {
            this.icon = icon;
            this.action = action;
        }
    }

    /**
     * A type of button icon that can be displayed for this item
     */
    public enum ButtonIcon {
        /**
         * Configures the configuration of the current item widget
         */
        CONFIGURE("Configure"),
        /**
         * Moves this item to a different index (in the list)
         */
        MOVE("Change order"),
        /**
         * Removes this item, or if this is not a list, clears/resets the item to one that passes
         * the input forward (default behavior)
         */
        REMOVE("Remove operation"),
        /**
         * Adds a new function below this one
         */
        ADD("Add new operation"),
        /**
         * Shown when an element can only contain one function, but none is set yet, to initialize one.
         * Behaves the same as {@link #ADD} except it doesn't add one below, but instead replaces the
         * current item.
         */
        INITIALIZE("Set operation");

        private final MapTexture icon_selected;
        private final MapTexture icon_default;
        private final String tooltip;

        ButtonIcon(String tooltip) {
            MapTexture atlas = MapTexture.loadPluginResource(TrainCarts.plugin,
                    "com/bergerkiller/bukkit/tc/textures/attachments/transfer_function_item_buttons.png");
            this.icon_selected = atlas.getView(ordinal() * atlas.getHeight(), 0, atlas.getHeight(), atlas.getHeight()).clone();
            this.icon_default = this.icon_selected.clone();
            this.icon_default.setBlendMode(MapBlendMode.SUBTRACT);
            this.icon_default.fill(MapColorPalette.getColor(20, 20, 64));
            this.tooltip = tooltip;
        }

        public int width() {
            return icon_selected.getWidth();
        }

        public MapTexture icon(boolean selected) {
            return selected ? icon_selected : icon_default;
        }

        public String tooltip() {
            return tooltip;
        }
    }
}
