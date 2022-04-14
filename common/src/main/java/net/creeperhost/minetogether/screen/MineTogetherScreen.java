package net.creeperhost.minetogether.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import io.sentry.Sentry;
import net.creeperhost.polylib.client.screen.widget.buttons.ButtonMultiple;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

public class MineTogetherScreen extends Screen
{
    public MineTogetherScreen(Component component)
    {
        super(component);
    }

    @Override
    public void renderComponentHoverEffect(PoseStack poseStack, @Nullable Style style, int i, int j)
    {
        super.renderComponentHoverEffect(poseStack, style, i, j);
    }

    public boolean handleComponentClicked(@Nullable Style style, double mouseX, double mouseY)
    {
        return false;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
    {
        super.render(poseStack, mouseX, mouseY, partialTicks);
        renderTooltips(poseStack, mouseX, mouseY, partialTicks);
    }

    public void renderTooltips(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
    {
        try
        {
            if (children() != null && !children().isEmpty())
            {
                for (GuiEventListener guiEventListener : children())
                {
                    if (!(guiEventListener instanceof Button)) continue;

                    Button abstractWidget = (Button) guiEventListener;

                    if (abstractWidget.isHoveredOrFocused() && abstractWidget instanceof ButtonMultiple)
                    {
                        ButtonMultiple buttonMultiple = (ButtonMultiple) abstractWidget;
                        if (buttonMultiple.getTooltip() != null && !buttonMultiple.getTooltip().getString().isEmpty())
                            renderTooltip(poseStack, buttonMultiple.getTooltip(), mouseX, mouseY);
                    }
                }
            }
        } catch (Exception e)
        {
            Sentry.captureException(e);
        }
    }
}
