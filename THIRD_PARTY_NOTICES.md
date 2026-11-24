# Third-party notices

The application's original code, documentation, and original artwork are
licensed under the MIT License in `LICENSE`. The third-party components below
retain their own licenses.

## Google Material Design icons

The Settings gear, Menu, Close, Back, and Backspace vector icons in
`app/src/main/res/drawable` are adapted from Google Material Design icons.
The SVG geometry was converted to Android vector paths with local formatting
and coordinate changes.

Copyright Google Inc.
Licensed under the Apache License, Version 2.0.
The full license is included in `licenses/APACHE-2.0.txt`.

Upstream: https://github.com/google/material-design-icons
Settings geometry: https://github.com/google/material-design-icons/blob/3.0.1/action/svg/production/ic_settings_24px.svg

The Liddle Puzzles tiled-L launcher artwork and Undo/Erase paths are local artwork.

## Daily Word English dictionary

`app/src/main/assets/daily-word/guesses-v1.txt` contains 12,628 lowercase
ASCII five-letter entries filtered from the English word list in Loren
Brichter's **Words** project (the Letterpress dictionary). Twenty-five formerly
accepted entries were removed from the shipped guess list.

Source: https://github.com/lorenbrichter/Words
Pinned revision: `f4976d6a6d34beb431dec8d4b443a138f4dc8bee`
Source file: `Words/en.txt`
Source SHA-256: `b78af5ad2f66bed4e64fd48334af7d5620a91c45e7a578c084b9b72f9abcd5dd`
License: **CC0 1.0 Universal**, retained in `licenses/CC0-1.0-Words.txt`
and bundled in the app's legal assets.

The separate 619-word answer selection and its frozen ordering were authored
for Liddle Puzzles, checked against this dictionary, and are distributed with
the app under its MIT license. Neither vocabulary nor schedule uses the
New York Times' word schedule, branding, artwork, or source code.

## Gradle Wrapper (source distribution only)

The Gradle wrapper scripts and `gradle/wrapper/gradle-wrapper.jar` are Gradle
project components licensed under the Apache License, Version 2.0.
Retain the scripts' existing copyright/license headers and the JAR's embedded
`META-INF/LICENSE`. A full copy is also included in `licenses/APACHE-2.0.txt`.

Upstream: https://github.com/gradle/gradle

Gradle and the Android Gradle Plugin are build tools, not third-party runtime
libraries bundled into Liddle Puzzles.
