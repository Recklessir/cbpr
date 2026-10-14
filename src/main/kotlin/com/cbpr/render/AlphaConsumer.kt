package com.cbpr.render

import com.mojang.blaze3d.vertex.VertexConsumer

class AlphaConsumer(private val out: VertexConsumer, var alpha: Float) : VertexConsumer {
    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        out.addVertex(x, y, z)
        return this
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        out.setColor(red, green, blue, (alpha * this.alpha).toInt())
        return this
    }

    override fun setUv(u: Float, v: Float): VertexConsumer {
        out.setUv(u, v)
        return this
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer {
        out.setUv1(u, v)
        return this
    }

    override fun setUv2(u: Int, v: Int): VertexConsumer {
        out.setUv2(u, v)
        return this
    }

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer {
        out.setNormal(x, y, z)
        return this
    }
}
