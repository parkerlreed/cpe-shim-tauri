# OmniRemote IR Test (Android)

A standalone Android test app embedding the patched Cloudpilot Palm m130
emulator and a user-supplied OmniRemote installation. Package:
`local.omniremote.irtest`. This project builds separately from the Tauri wrapper.

Select **Internal IR** inside OmniRemote. Training → Train buttons and tapping a
button invokes Android USB capture directly, then returns the envelope to
`ORLibRecordSample`. Skip OmniRemote's preliminary "Test remote" screen to enter
capture. This screen only tests reception; the following record call starts the
Android capture. The app shows a USB permission prompt when necessary.

Normal OmniRemote button presses route the Palm UART's gated carrier samples to
the native D552 USB sender. Receiving and transmitting share one worker, so USB
operations do not overlap. The existing IR Blaster app must release the dongle.

Android **Back** saves the Palm session before closing the app. If learning is
active, Back cancels the capture before saving. The screen shows only the Palm
display, Graffiti area and hardware buttons; capture feedback appears briefly
as an overlay. Palm state survives APK updates. The app also saves when its page
becomes hidden. App uninstallation deletes this state.

The dongle supplies envelope timings, not measured carrier frequency. Set
OmniRemote's Training Settings to the intended carrier (38 kHz for the tested
NEC remote). Automatic carrier detection through the Palm's receiver is not
implemented. The bridge targets the supplied OmniRemote Pro 2.x `ORLib.prc`
A807 interface, and transmit currently targets the D552 (`70 01`) dongle.
Android's status bar is hidden; swiping can reveal it temporarily.
Sensor rotation supports all four orientations, including upside-down portrait.

The app registers for attachment of the tested `045c:0132` dongle. On connecting
it, choose **OmniRemote IR Test** in Android's USB app chooser and enable the
default/always option. Later attachments open the app automatically. If the
Palm session is already running, attachment brings that same activity forward.

Build prerequisites: Java 17 or 21, Android SDK 36, Emscripten (tested 4.0.18),
and Node.js/Yarn for Cloudpilot's skin assets. Create ignored `local.properties`
containing `sdk.dir=/path/to/android-sdk`, and set `JAVA_HOME` to the compatible
JDK. From this directory, run:

```sh
EMSDK=/path/to/emsdk JAVA_HOME=/path/to/jdk21 \
  PALM_IMAGE=/path/to/your-omniremote-session.img ./build.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n local.omniremote.irtest/local.omniremote.MainActivity
```

The build checks out the revision in `cloudpilot-revision.txt` into ignored
`build/cloudpilot-emu` and applies `patches/cloudpilot-omniremote-ir.patch`.
`CLOUDPILOT_SOURCE` may select an existing checkout at that revision; the script
accepts the patch already applied and checks for conflicts before applying it.
The core patch contains the capture hook, UART envelope output bridge and native
register/UI diagnostics used during development.

The session image must already have the ROM, OmniRemote and desired databases
installed. `PALM_IMAGE` is required and is copied into the APK; keep the resulting
APK local. ROMs, sessions, generated emulator binaries and build outputs are
excluded from Git.

Validation performed: native and WASM builds; restore of the supplied session;
OmniRemote A807 capture interception; recorded phone capture delivered back to
OmniRemote; replay from the trained button decoded as NEC `20DF10EF`; Java USB
encoding independently decoded and compared with the Python checksum/framing.
Android learning and physical transmission were confirmed on the phone.
Back-to-save behavior and the Graffiti coordinate mapping are checked with
`node test/back-save.cjs`.
