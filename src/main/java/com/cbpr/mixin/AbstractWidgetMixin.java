package com.cbpr.mixin;

import com.cbpr.render.MenuHost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {

    @WrapMethod(method = "render", require = 0)
    private void cbpr$relocate(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, Operation<Void> original) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        if (MenuHost.relocatesWidget(widget)) {
            MenuHost.relocateWidget(widget, graphics, () -> original.call(graphics, mouseX, mouseY, partialTicks));
        } else {
            original.call(graphics, mouseX, mouseY, partialTicks);
        }
    }
}
