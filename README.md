# Earth Monitor

A small, responsive browser app for viewing an ESP32-CAM MJPEG stream.

## Features

- Editable stream URL (defaults to `http://192.168.1.25:81/stream`)
- Saves the URL in browser local storage
- Connect, refresh, and stop controls
- Mobile-friendly layout and connection troubleshooting hints

## Run

Open `index.html` in a browser, or publish the repository with GitHub Pages.

Connect the device running this page to the same Wi-Fi network as the ESP32-CAM. If the stream is unreachable, confirm the camera IP, port (usually `81` for CameraWebServer MJPEG), and `/stream` path.

## GitHub Pages

In the repository settings, open **Pages**, select the `main` branch and `/(root)`, and save. GitHub will provide the published URL after the site deploys.

## Notes

- This page embeds the MJPEG stream in an `<img>` element.
- If you host the page over HTTPS, browsers may restrict a camera stream served over plain HTTP.
- The URL is stored only in the current browser/device.
