# Proton Calendar Widgets+

A custom fork of [Proton Calendar](https://github.com/ProtonMail/android-calendar) focused on expanding and redesigning its Android home-screen widgets.

This project is based on the **latest Proton Calendar release** and preserves the original Agenda widget while adding two additional widget designs.

## Widgets

### Agenda

The original Proton Calendar widget design.

### Month & Agenda

A **4×2** widget combining:

* A full monthly calendar.
* The current date and upcoming events.
* Dynamic week-start day based on Proton Calendar user settings.
* Highlighting for the current day and Sundays.
* Support for months requiring a sixth calendar row.

### Monthly

A compact **2×2** widget displaying the monthly calendar at a glance.

## Highlights

* Three independent widget designs.
* Original Agenda widget preserved.
* Custom monthly calendar layout.
* Dynamic six-week calendar support without unnecessary empty rows.
* Week-start configuration follows Proton Calendar settings.
* Improved widget refresh handling across all three designs.
* Stable event list IDs for smoother updates and scrolling.
* Separate layouts and `RemoteViewsService` implementations for each widget.

## Download

[![Get it on Obtainium](assets/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/MerkuR-92/proton-calendar-widgets-plus)

Install and track updates automatically through Obtainium.

Prebuilt APKs are available from the project's [GitHub Releases](../../releases).

The APK is signed with a separate signing key for this fork and is intended for installation outside Google Play.

## Installation

Download the latest APK from the Releases page and install it on your Android device.

Because this is a custom build with its own signing key, it cannot be installed as an update over the official Proton Calendar app. An existing installation signed with the official Proton key must be removed before installing this fork.

Future updates of this fork can be installed over previous versions of the fork as long as the same signing key is retained.

## Development

This repository tracks the latest Proton Calendar releases while maintaining custom widget-related changes.

The main branch for this project is:

`main`

The official Proton Calendar repository is configured as the upstream source so that future Proton releases can be integrated into this fork.

## Disclaimer

This is an independent community fork and is **not an official Proton product**.

Proton Calendar and the Proton Calendar source code remain the property of Proton AG.

## License

This project follows the license of the upstream [Proton Calendar repository](https://github.com/ProtonMail/android-calendar).
