package com.cbpr.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import org.joml.Matrix4f

object RenderPass {
    class Saved(val projection: Matrix4f, val sorting: VertexSorting)

    fun enterWorld(projection: Matrix4f, view: Matrix4f): Saved = enter(projection, VertexSorting.DISTANCE_TO_ORIGIN, view)

    fun enterScreen(): Saved = enter(Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z, Matrix4f())

    fun leave(saved: Saved) {
        val modelView = RenderSystem.getModelViewStack()
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setProjectionMatrix(saved.projection, saved.sorting)
    }

    private fun enter(projection: Matrix4f, sorting: VertexSorting, view: Matrix4f): Saved {
        val modelView = RenderSystem.getModelViewStack()
        val saved = Saved(Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting())
        RenderSystem.setProjectionMatrix(projection, sorting)
        modelView.pushMatrix()
        modelView.identity()
        modelView.mul(view)
        RenderSystem.applyModelViewMatrix()
        return saved
    }
}
