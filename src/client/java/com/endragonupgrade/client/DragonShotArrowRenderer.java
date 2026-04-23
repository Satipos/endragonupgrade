package com.endragonupgrade.client;

import com.endragonupgrade.EndRagonUpgradeMod;
import com.endragonupgrade.item.DragonShotArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/**
 * Client renderer for {@link DragonShotArrow}. Uses the vanilla {@link ArrowRenderer} pipeline
 * with our dragon-shot-recoloured projectile texture.
 */
public class DragonShotArrowRenderer extends ArrowRenderer<DragonShotArrow, ArrowRenderState> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            EndRagonUpgradeMod.MOD_ID, "textures/entity/projectiles/dragon_shot.png");

    public DragonShotArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected Identifier getTextureLocation(ArrowRenderState state) {
        return TEXTURE;
    }

    @Override
    public ArrowRenderState createRenderState() {
        return new ArrowRenderState();
    }
}
