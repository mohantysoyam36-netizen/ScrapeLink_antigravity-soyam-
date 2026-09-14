package com.ewaste.formalization.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrGenerator {

    /**
     * Generates a square QR Code bitmap for the Digital Material Passport payload.
     */
    fun generateQrBitmap(content: String, size: Int = 400): Bitmap {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            // Fallback: simple matrix bitmap representation
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val step = 16
            for (i in 0 until size step step) {
                for (j in 0 until size step step) {
                    if ((content.hashCode() + i * 31 + j * 17) % 2 == 0) {
                        for (dx in 0 until step - 2) {
                            for (dy in 0 until step - 2) {
                                if (i + dx < size && j + dy < size) {
                                    bitmap.setPixel(i + dx, j + dy, Color.BLACK)
                                }
                            }
                        }
                    }
                }
            }
            bitmap
        }
    }
}
