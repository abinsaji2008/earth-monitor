# Earth Monitor

Earth Monitor is an Android companion app and a lightweight browser-based ESP32-CAM viewer for a local Wi-Fi network.

## Android app features

- Enter the ESP32 IPv4 address manually.
- Scan the current Wi-Fi subnet for devices with an HTTP service (ports 81, 80, 8080, and 443).
- Select a discovered device and connect to its ESP32-CAM MJPEG stream.
- Display ultrasonic distance in centimetres, refreshing every 2 seconds.
- Save a camera image to the device gallery.
- Camera LED brightness and ON/OFF controls have been removed.

## ESP32 endpoints expected by the Android app

- Camera stream: `http://ESP32_IP:81/stream`
- Ultrasonic distance: `http://ESP32_IP/distance`

The distance endpoint should return either a plain numeric value (for example, `42.5`) or JSON such as:

```json
{"distance_cm": 42.5}
```

The app also accepts JSON keys `distance`, `cm`, or `ultrasonic`.

If your ESP32 firmware uses a different route or response format, update the `/distance` request and parser in `MainActivity.java` to match it.

## Build the Android app

Open the `android` folder in Android Studio and build the app. The app needs Wi-Fi access and internet/network permission to scan the local subnet and connect to the ESP32.

## Browser viewer

Open `index.html` in a browser, or publish the repository with GitHub Pages. The browser page supports a manually entered camera stream URL. Browser security restrictions prevent a normal hosted web page from reliably scanning all devices on a local network; use the Android app for LAN scanning.

## Notes

- The phone and ESP32 must be connected to the same local Wi-Fi network.
- Network scanning checks common HTTP ports and lists devices that respond; it cannot guarantee that every device on the network will be discovered.
- If the page is hosted over HTTPS, browsers may restrict a camera stream served over plain HTTP.
