# Photo Mosaic Android App

Rod Bailey
20 July 2016

# Summary

This is an Android application created as a technical exercise for Canva.

The app accepts raw images and produces a mosaic equivalent of them. The tiles of the mosaic can be created in two ways:

- By an external Mosaic tile server supplied by Canva
- Using an internal, simple algorithm supplied by myself.

Switch between these two options by changing the value of `Constants.TILE_STRATEGY`

The root directory of this project contains a file `example mosaics.zip` which contains samples of the mosaics produced in both modes of operation.

# Design

The app consists mainly of an Activity and a Service. The Activity handles the UI work and the long-running operations associated with constructing the Mosaic image are delegated to the Service. The service does the compute-heavy operations on background threads, leaving the main thread free to keep the UI running smoothly.

Images get into the app by being *Shared* from some other application on the device, such as a Camera, Gallery or File Explorer app. *Photo Mosaic* will appear as one of the available destination apps when the user pressed the *Share* button.

Once the mosaic equivalent of the given image has been generated, it is stored in the public *Pictures* directory and also added to the Android Media Store.

Finally, the user can elect to *Share* the mosaic image themselves to some other app on the device.

# Concurrency

An essential element of the `MosaicService` design is the user of parallelism to speed up the mosaic'ing process. Note the use of the `ExecutorService` to take advantage of the fact that the contents of mosaic tile images can be calculated independently.

# TO DO

Being a time-constrained exercise, there are of course a few limitations that are obvious and should be corrected:

* There are several `TODO` items in the code that point to possible efficiency improvements.
* Circular tiles whose full width or height don't fit in the image's area are squashed into ellipses. This may not be what the client desires - perhaps they want the normal circular tile truncated.
* The UI is very basic and could use some polish.
* A facility to change the mosaic tile size from the built in 32 x 32 would be a good idea.
* A Quick Preview option might be useful. It could be implemented by applying the mosaic operation to the scaled down version of the image that actually appears on the screen.
* I have locked the app to portrait device orientation only, to save time. Landscape should also be supported.
