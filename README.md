# ceilingbounce

Flashlight testing for Android

## Usage

Ceilingbounce uses the light sensor in your phone as a light meter for flashlight testing. The light meter in your phone is not accurate, so you must calibrate it if you want anything but relative numbers. Calibration should be done with a freshly charged battery on medium modes with several lights. Ideally, the same light should be tested with calibrated equipment, but if a test is available of the same *model* using calibrated equipment, that's better than nothing.

Despite the name, measurements taken by bouncing a light off the ceiling aren't very useful. More accurate ~~testing~~ estimating setups are described below.

## A note on light detection

Several of the procedures described here call for turning on the light and waiting for something to happen after 30 seconds. There are certain limitations. The following will be detected as a light being turned on:

* It's at least 10 lumens (or before calibration, 10 raw "lux" from the light meter, which may or may not be somewhere near 10 actual lux)
* It's at least half the last peak value
* It's at least twice the average of the last 20 values recorded (up to several values may be recorded per second)

If you're having trouble with your light not being detected, press the reset button and it should start working.

### Calibrating lumens

You'll need an integrating device. Discussion of building integrating spheres can be found on CPF and BLF. Here, I'll show something more crude: the integrating *shoebox*.

The shoebox should be white inside, or lined with white paper. There should be a slit cut for the phone and a hole for the light. The beam of the light should not hit the phone's light sensor directly. It should be possible to position the light and the phone the same way every time with some sort of visual reference. In my case, the bottom of the tab bar on the screen aligns with the wall of the box, and the bezel of the light aligns with the edge of the paper. It looks like [this](http://i.imgur.com/cjoP3D3.jpg) (lid open for illustration; it should be used with the lid closed).

In the lumens tab, lux per lumen will default to 1, or you can set it to 1 and press the update button if you need to recalibrate. With several lights, follow the procedure for measuring lumens on a medium mode with known output. Record the output shown and divide by the known output. Enter the average of these ratios in the lux per lumen field and press update.

### Measuring lumens

Using an integrating device as described in the calibration section, tap the Lumens tab, position the light and the phone as appropriate in the integrating device. Turn on the light. The large number on the screen is the current lumen reading. A peak reading is shown below the reset button, and a 30 second reading will be updated when available.

After 30 seconds, your default notification sound will play, and there should be a 30 second reading, in the style of the FL1 standard.

### Calibrating throw

Calibrating throw is similar to calibrating lumens, except instead of a ratio of recorded lux to lumens, you'll be computing an effective distance. The effective distance is the distance at which your meter would measure the lux it does with the candela used if it was accurate. Again, you'll need lights with known medium modes and known candela. If you only have known candela on max and known lumens in medium, the ratio of candela per lumen is close enough to fixed in the same light in different modes.

After recording values from several known lights, the effective distance for each light is calculated with the following formula:

sqrt(known candela / measured lux)

Average these distances and enter that number in the calculated distance field.

### Measuring throw

Prop up the phone against a fixed object and find a fixed object between about 3 and 10 meters away that you can index against to hold a light at the same distance and same angle from the phone every time. As with lumens, there are constant, peak and 30 second readouts, but an FL1 throw distance is calculated as well as candela.

Turn on the light, pointed at the phone and wait. After 30 seconds, your default notification sound should play.

For the next 10 seconds, Ceilingbounce will update the 30s value. This allows you to move the light around slightly to ensure the most intense part of the hotspot crosses the sensor. Only the highest value during this period is retained. Both candela and FL1 throw distances are recorded.

### Making runtime graphs

Ceilingbounce does not (yet) make graphes natively. Instead, it logs data to a CSV file, and you can use a spreadsheet program to make an XY plot. Use of spreadsheets is beyond the scope of this document.

On the runtime tab, enter a filename for the output file. If you don't, "test" will be used. Position the phone and light in your integrating device, or if you haven't built one, bounce the light off the ceiling or otherwise arrange things such that light from the test flashlight reaches the light sensor, and other light does not. It's best if this produces a large number (in the thousands for high modes) on the instant readout, but should not max out the sensor (you'll notice that the number doesn't change no matter what you do to the light).

Once things are arranged properly, press the Start runtime test button. After 30 seconds, your default notification sound should play. Wait until the light is dim/off or you're finished testing what it is you wanted to test, then press the Stop test button. Your results should be in the `/sdcard/ceilingbounce/[your-filename]/` directory, which you can access with the file manager of your choice or by plugging the phone into a PC. The CSV columns are: raw reading, minutes, percent of 30 second value.

CSV files will not be overwritten if you run several tests with the same name - each has a timestamp. Figuring out which is which is up to the user.

## Errata

* Just opening the app in a bright environment can trigger 30 second events
* Sometimes 30 second events are triggered more than once
* Automatic light detection doesn't work with very low modes
* Ceilingbounce will keep your screen on; it's intended to be used with the phone plugged in
* Switching in and out of the app will always bring you back to the lumens tab
* If your phone doesn't have a light meter, Ceilingbounce will probably crash on opening

## License

Copyright © 2017 Zak Wilson

Distributed under the Eclipse Public License, the same as Clojure.
