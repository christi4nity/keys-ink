/*
 * Copyright (C) 2012 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.keysink.inputmethod.latin.common

/**
 * Stores parallel arrays of x/y coordinates, pointer IDs, and timestamps for input events.
 * Used to pass touch data to the native suggestion engine.
 *
 * Not thread-safe.
 */
class InputPointers(private val defaultCapacity: Int) {

    private val xCoordinates = ResizableIntArray(defaultCapacity)
    private val yCoordinates = ResizableIntArray(defaultCapacity)
    private val pointerIds = ResizableIntArray(defaultCapacity)
    private val times = ResizableIntArray(defaultCapacity)

    fun addPointer(x: Int, y: Int, pointerId: Int, time: Int) {
        xCoordinates.add(x)
        yCoordinates.add(y)
        pointerIds.add(pointerId)
        times.add(time)
    }

    fun addPointerAt(index: Int, x: Int, y: Int, pointerId: Int, time: Int) {
        xCoordinates.addAt(index, x)
        yCoordinates.addAt(index, y)
        pointerIds.addAt(index, pointerId)
        times.addAt(index, time)
    }

    /** Shallow-copy reference from another InputPointers (shares backing arrays). */
    fun set(ip: InputPointers) {
        xCoordinates.set(ip.xCoordinates)
        yCoordinates.set(ip.yCoordinates)
        pointerIds.set(ip.pointerIds)
        times.set(ip.times)
    }

    /** Deep-copy data from another InputPointers. */
    fun copy(ip: InputPointers) {
        xCoordinates.copy(ip.xCoordinates)
        yCoordinates.copy(ip.yCoordinates)
        pointerIds.copy(ip.pointerIds)
        times.copy(ip.times)
    }

    fun reset() {
        xCoordinates.reset(defaultCapacity)
        yCoordinates.reset(defaultCapacity)
        pointerIds.reset(defaultCapacity)
        times.reset(defaultCapacity)
    }

    fun getPointerSize(): Int = xCoordinates.length

    fun getXCoordinates(): IntArray = xCoordinates.primitiveArray
    fun getYCoordinates(): IntArray = yCoordinates.primitiveArray
    fun getPointerIds(): IntArray = pointerIds.primitiveArray
    fun getTimes(): IntArray = times.primitiveArray

    override fun toString(): String =
        "size=${getPointerSize()} id=$pointerIds time=$times x=$xCoordinates y=$yCoordinates"
}

/**
 * A growable int array backed by a primitive IntArray. Inline helper for InputPointers.
 *
 * Not thread-safe.
 */
class ResizableIntArray(capacity: Int) {

    private var array: IntArray = IntArray(capacity)
    var length: Int = 0
        private set

    val primitiveArray: IntArray get() = array

    operator fun get(index: Int): Int {
        if (index < length) return array[index]
        throw ArrayIndexOutOfBoundsException("length=$length; index=$index")
    }

    fun add(value: Int) {
        ensureCapacity(length + 1)
        array[length++] = value
    }

    fun addAt(index: Int, value: Int) {
        if (index < length) {
            array[index] = value
        } else {
            length = index
            add(value)
        }
    }

    fun fill(value: Int, startPos: Int, count: Int) {
        require(startPos >= 0 && count >= 0) { "startPos=$startPos; count=$count" }
        val endPos = startPos + count
        ensureCapacity(endPos)
        array.fill(value, startPos, endPos)
        if (length < endPos) length = endPos
    }

    fun setLength(newLength: Int) {
        ensureCapacity(newLength)
        length = newLength
    }

    fun reset(capacity: Int) {
        array = IntArray(capacity)
        length = 0
    }

    /** Shallow-copy reference (shares backing array). */
    fun set(src: ResizableIntArray) {
        array = src.array
        length = src.length
    }

    /** Deep-copy data from src. */
    fun copy(src: ResizableIntArray) {
        val newCapacity = calculateCapacity(src.length)
        if (newCapacity > 0) array = IntArray(newCapacity)
        src.array.copyInto(array, 0, 0, src.length)
        length = src.length
    }

    fun append(src: ResizableIntArray, startPos: Int, count: Int) {
        if (count == 0) return
        val newLength = length + count
        ensureCapacity(newLength)
        src.array.copyInto(array, length, startPos, startPos + count)
        length = newLength
    }

    /** Shift left by elementCount, discarding the first elementCount elements. */
    fun shift(elementCount: Int) {
        array.copyInto(array, 0, elementCount, length)
        length -= elementCount
    }

    private fun calculateCapacity(minimumCapacity: Int): Int {
        val current = array.size
        if (current >= minimumCapacity) return 0
        val doubled = current * 2
        return maxOf(minimumCapacity, doubled)
    }

    private fun ensureCapacity(minimumCapacity: Int) {
        val newCapacity = calculateCapacity(minimumCapacity)
        if (newCapacity > 0) {
            array = array.copyOf(newCapacity)
        }
    }

    override fun toString(): String = buildString {
        append('[')
        for (i in 0 until length) {
            if (i != 0) append(',')
            append(array[i])
        }
        append(']')
    }
}
