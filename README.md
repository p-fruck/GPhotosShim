# GPhotosShim

GPhotosShim allows you to use an unmodified Google camera version without Google photos, it intercepts the request to view an image and passes it on to a gallery app of your choice.

Fork of the original project to support Android 17.

## Usage

- Install app
- Add storage scope permissions for `DCIM/Camera`. This is required for the shim to notice when a picture is taken/ready.
- Optional: Launch GPhotosShim and select a default app to show photos (e.g. Immich)
