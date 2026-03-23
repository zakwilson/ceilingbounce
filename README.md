# ceilingbounce

Flashlight testing and runtime graphs for Android

## Keep Android Open

Google plans to prevent most Android users from installing apps unless the developer registers with Google, giving Google the power to ban developers and apps it doesn't like. See https://keepandroidopen.org/ for what you can do about it.

## Usage

Ceilingbounce uses the light sensor in your phone as a light meter for flashlight testing. The light meter in your phone is not accurate, so you must calibrate it if you want anything but relative numbers. Calibration should be done with a freshly charged battery on medium modes with several lights. Ideally, the same light should be tested with calibrated equipment, but if a test is available of the same *model* using calibrated equipment, that's better than nothing.

Despite the name, measurements taken by bouncing a light off the ceiling aren't very useful except for runtime graphs. More accurate ~~testing~~ estimating setups are described below.

## Frequently asked and anticipated questions

* **How do I install this?** - Google "android sideloading".
* **Will all this calibration stuff be easier in a future release?** - yes
* **Will it be in F-Droid?** - Probably.
* **Will it be in the Play store?** - Probably not.
* **Will there be an iOS version?** - No. I don't have an iOS dev setup, Apple bans apps that use the light sensor from the app store, and there's no *reasonable* way to sideload.
* **Can you explain how to...?** - It's probably in this README. Please read it twice before you ask your question.
* **Do you take pull requests?** - Yes, but please say what it is you want to do before you start coding.
* **This is too hard, will you hold my hand and walk me through using it?** - No, this is a pre-release intended for users with moderately high technical knowledge of both Android phones and photometrics.
* **Is this a good substitute for real test equipment?** - Not really, but it sure beats eyeballing it, and it makes quick graphs really easy.
* **Is this app a flashlight?** - NO! Stop using your phone as a flashlight. Why are you even here?
* **My graphs are jagged when the output changed smoothly** - whoever wrote the driver for your phone's light sensor is a jerk. You probably can't use it for much more than screen brightness control. I have a possible mitigation to try in the future, but don't expect miracles.

## Calibration

Calibration requires doing some manual calculations. That's annoying, and I promise to improve it in the future.

### Calibrating lumens

You'll need an integrating device. Discussion of building integrating spheres can be found on CPF and BLF. Here, I'll show something more crude: the integrating *shoebox*.

![](http://i.imgur.com/cjoP3D3.jpg)

The shoebox should be white inside, or lined with white paper. There should be a slit cut for the phone and a hole for the light. The beam of the light should not hit the phone's light sensor directly. It should be possible to position the light and the phone the same way every time with some sort of visual reference. In my case, the bottom of the tab bar on the screen aligns with the wall of the box, and the bezel of the light aligns with the edge of the paper. It should be used with the lid closed.

In the settings tab, lux per lumen will default to 1, or you can set it to 1 if you need to recalibrate. With several lights, follow the procedure for measuring lumens on a medium mode with known output. Record the output shown and divide by the known output. Enter the average of these ratios in the lux per lumen field.

### Measuring lumens

Using an integrating device as described in the calibration section, tap the Lumens tab, position the light and the phone as appropriate in the integrating device. Turn on the light. The large number on the screen is the current lumen reading.

To perform FL1-style measurements, set a start value below what you expect to measure and tap the MEASURE button. Measurement will start when the threshold is exceeded, and stop at 30 seconds. Tones will play at the start and end of the measurement if sound is enabled.

### Calibrating throw

Prop up the phone against a fixed object and find a fixed object between about 3 and 10 meters away that you can index against to hold a light at the same distance and same angle from the phone every time.

Calibrating throw is similar to calibrating lumens, except instead of a ratio of recorded lux to lumens, you'll be computing an effective distance. The effective distance is the distance at which your meter would measure the lux it does with the candela used if it was accurate. Again, you'll need lights with known medium modes and known candela. If you only have known candela on max and known lumens in medium, the ratio of candela per lumen is close enough to fixed in the same light in different modes.

After recording values from several known lights, the effective distance for each light is calculated with the following formula:

sqrt(known candela / measured lux)

Average these distances and enter that number in the calculated distance field.

### Measuring throw

As with lumens, there is a constant candela display.

To perform FL1-style measurements, set a start value below what you expect to measure and tap the MEASURE button. Measurement will start when the threshold is exceeded and play a tone, then play another tone at 30 seconds. For the next 10 seconds, Ceilingbounce will update the 30s value. This allows you to move the light around slightly to ensure the most intense part of the hotspot crosses the sensor. Only the highest value during this period is retained. Both candela and FL1 throw distances are recorded. A tone will play to indicate the end of the measurement period.

Both candela and FL1 throw distance will be recorded for the whole measurement interval, and for the 30-40 second interval. The second should be considered more consistent with the FL1 standard.

### Making runtime graphs

Ceilingbounce makes runtime graphs directly in the app. A graph is drawn in real-time during the test. The 100% point is adjusted at 30 seconds, and a larger PNG of the graph is saved when measurement stops. You must select a storage directory in the settings tab before you can make graphs.

On the runtime tab, enter a filename for the output file and choose whether to graph percent, lumens, or raw lux. Start and end values are also in that unit, except that the start value is raw lux when percent is selected.

Position the phone and light in your integrating device, or if you haven't built one, bounce the light off the ceiling or otherwise arrange things such that light from the test flashlight reaches the light sensor, and other light does not. It's best if this produces a large number (in the thousands for high modes) on the instant readout, but should not max out the sensor (you'll notice that the number doesn't change no matter what you do to the light).

Once things are arranged properly, press the START TEST button, then turn on the flashlight. After 30 seconds, you should see the graph redraw. 

Your results should be in the `[storage-directory]/[your-filename]/` directory, which you can access with the file manager of your choice or by plugging the phone into a PC. The CSV columns are: raw reading, minutes, percent of 30 second value.

Output files will not be overwritten if you run several tests with the same name - each has a timestamp. Figuring out which is which is up to the user.

## Errata

* Ceilingbounce will keep your screen on and probably use a fair amount of battery; it's intended to be used with the phone plugged in
* Ceilingbounce forces a dark theme regardless of your settings; light from the screen can affect the reading

## License

Copyright © 2015-2026 Zak Wilson

Distributed under the GNU General Public License, version 3.
