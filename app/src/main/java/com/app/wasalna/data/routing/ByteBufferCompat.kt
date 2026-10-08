package com.app.wasalna.data.routing

import java.nio.ByteBuffer

/**
 * Phase 10.4.3.9 (Vela approach, fix #1 — MMAP on ART).
 *
 * Absolute bulk `ByteBuffer.get(int, byte[])` / `put(int, byte[])` only exist
 * since JDK 13 and are missing from Android's SDK even on API 34+, so any
 * memory-mapped graph reader that calls them crashes on-device. These helpers
 * perform the same absolute bulk transfer through a duplicated view
 * (position + relative ops exist since Java 1.4), which ART supports.
 *
 * Used by [RoutingGraphManager] when validating downloaded graph files, and
 * by the engine whenever the MMAP store is selected for large graphs.
 */
object ByteBufferCompat {

    /** ART-safe absolute bulk read: [length] bytes from [index] into [dst]. */
    fun getBulk(
        buffer: ByteBuffer,
        index: Int,
        dst: ByteArray,
        offset: Int = 0,
        length: Int = dst.size - offset,
    ) {
        require(index >= 0 && length >= 0 && offset >= 0)
        require(index + length <= buffer.capacity()) { "bulk read out of bounds" }
        require(offset + length <= dst.size) { "destination overflow" }
        val view = buffer.duplicate()
        view.position(index)
        view.get(dst, offset, length)
    }

    /** ART-safe absolute bulk write: [length] bytes from [src] at [index]. */
    fun putBulk(
        buffer: ByteBuffer,
        index: Int,
        src: ByteArray,
        offset: Int = 0,
        length: Int = src.size - offset,
    ) {
        require(index >= 0 && length >= 0 && offset >= 0)
        require(index + length <= buffer.capacity()) { "bulk write out of bounds" }
        require(offset + length <= src.size) { "source overflow" }
        val view = buffer.duplicate()
        view.position(index)
        view.put(src, offset, length)
    }

    /** ART-safe absolute int read (single absolute getters exist everywhere). */
    fun getInt(buffer: ByteBuffer, index: Int): Int = buffer.getInt(index)
}
