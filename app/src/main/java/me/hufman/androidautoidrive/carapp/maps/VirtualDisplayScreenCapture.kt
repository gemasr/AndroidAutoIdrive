package me.hufman.androidautoidrive.carapp.maps

import android.content.Context
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream


class VirtualDisplayScreenCapture(context: Context, val width:Int = 1000, val height:Int = 400, val dpi:Int = 300) {
	/** Prepares an ImageReader, and sends JPG-compressed images to a callback */
	protected val imageCapture = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)!!
	val virtualDisplay: VirtualDisplay

	private val origRect = Rect(0, 0, imageCapture.width, imageCapture.height)    // the full size of the main map
	private var sourceRect = Rect(0, 0, imageCapture.width, imageCapture.height)    // the capture region from the main map
	private var bitmap = Bitmap.createBitmap(imageCapture.width, imageCapture.height, Bitmap.Config.ARGB_8888)
	private val resizeFilter = Paint()
	private var resizedBitmap = Bitmap.createBitmap(imageCapture.width, imageCapture.height, Bitmap.Config.ARGB_8888)
	private var resizedCanvas = Canvas(resizedBitmap)
	private var resizedRect = Rect(0, 0, resizedBitmap.width, resizedBitmap.height) // draw to the full region of the resize canvas
	private val jpg = ByteArrayOutputStream()

	init {
		resizeFilter.isFilterBitmap = false

		val displayManager = context.getSystemService(DisplayManager::class.java)
		virtualDisplay = displayManager.createVirtualDisplay("IDriveGoogleMaps",
				imageCapture.width, imageCapture.height, dpi,
				imageCapture.surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
				null, Handler(Looper.getMainLooper()))
	}

	fun registerImageListener(listener: ImageReader.OnImageAvailableListener?) {
		this.imageCapture.setOnImageAvailableListener(listener, Handler(Looper.getMainLooper()))
	}

	fun changeImageSize(width: Int, height: Int) {
		synchronized(this) {
			resizedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
			resizedCanvas = Canvas(resizedBitmap)
			resizedRect = Rect(0, 0, resizedBitmap.width, resizedBitmap.height)
			sourceRect = findInnerRect(origRect, resizedRect)    // the capture region from the main map
			Log.i(TAG, "Preparing resize pipeline of $sourceRect to $resizedRect")
		}
	}

	private fun findInnerRect(fullRect: Rect, smallRect: Rect): Rect {
		/** Given a destination smallRect,
		 * find the biggest rect inside fullRect that matches the aspect ratio
		 */
		val aspectRatio: Float = 1.0f * smallRect.width() / smallRect.height()
		// try for max width
		var width = fullRect.width()
		var height = (width / aspectRatio).toInt()
		if (height > fullRect.height()) {
			// try for max height
			height = fullRect.height()
			width = (height * aspectRatio).toInt()
		}
		val left = fullRect.width() / 2 - width / 2
		val top = fullRect.height() / 2 - height / 2
		return Rect(left, top, left+width, top+height)
	}

	private fun convertToBitmap(image: Image): Bitmap {
		// read from the image store to a Bitmap object
		val planes = image.planes
		val buffer = planes[0].buffer
		val padding = planes[0].rowStride - planes[0].pixelStride * image.width
		val width = image.width + padding / planes[0].pixelStride
		if (bitmap.width != width) {
			Log.i(TAG, "Setting capture bitmap to ${width}x${imageCapture.height}")
			bitmap = Bitmap.createBitmap(width, imageCapture.height, Bitmap.Config.ARGB_8888)
		}
		bitmap.copyPixelsFromBuffer(buffer)

		// resize the image
		var outputBitmap: Bitmap = bitmap
		synchronized(this) {
			if (sourceRect != resizedRect) {
				// if we need to resize
				resizedCanvas.drawBitmap(bitmap, sourceRect, resizedRect, null)
				outputBitmap = resizedBitmap
			}
		}
		return outputBitmap
	}

	fun getFrame(): Bitmap? {
		val image = imageCapture.acquireLatestImage()
		if (image != null) {
			val bitmap = convertToBitmap(image)
			image.close()
			return bitmap
		}
		return null
	}

	fun compressBitmap(bitmap: Bitmap): ByteArray {
		// send to car
		jpg.reset()
		bitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpg)
		return jpg.toByteArray()
	}

	fun onDestroy() {
		this.imageCapture.setOnImageAvailableListener(null, null)
		virtualDisplay.release()
	}
}