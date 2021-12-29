## Hardware enumerator

### Motivation

While Project Oberon was originally designed to run on a single board, there are
now multiple boards (and emulators) with varying capabilities available. Not all
of them provide the same hardware support. As a consequence, when moving a disk
(image) from one system to another one, often parts of the system need to be
recompiled to take advantage of the changed hardware configuration.

This document tries to provide an interface that can be called by the software
to obtain insight about the installed hardware.

### Conventions for magic values

Many values returned by the hardware enumerator are 32-bit magic values. To make
them more human readable, they are sometimes written as 4-byte ASCII strings in
single quotation marks, interpreted in network byte order (e.g. the value
`41626364H` may be written as `'Abcd'`). In other cases the values may be
written in hexadecimal (suffixed by `H`) or decimal.

### Basic concept

Information in the hardware enumerator is divided into multiple hardware
descriptors, each indexed by a 32-bit integer value and consisting of a finite
list of 32-bit integer values. Zero values at the end of the list are not
significant, i.e. it cannot be determined by software if they are present or
not. Hardware descriptors are expected not to change after bootup, so that the
software can cache them if desired. Of course they may change between different
boots as the hardware may have changed.

The main hardware descriptor uses an index of `0` and each of its entries
describes the hardware ID one kind of hardware present. Additional information
about that kind of hardware may (in case the hardware requires it) be found in
another hardware descriptor whose index is the hardware ID.

A hardware ID (called "enhanced") may supersede another (less capable) hardware ID. If a
device supports both the superseded and the enhanced hardware ID, the enhanced ID
has to appear first in the main hardware descriptor. Software is expected to
skip unsupported hardware IDs and find the superseded entry that way. Once the
enhanced hardware ID has been seen, software is expected to skip the superseded ID
and only parse the enhanced one. *So far, no hardware IDs have been superseded.*

Access to the hardware enumerator is done by accessing the MMIO port `-4` (the
last word available). When reading from this port after bootup, an infinite
series of `0` words are returned. When writing a value to the port, the hardware
descriptor indexed by that value is returned. After the whole hardware
descriptor has been read, an infinite series of `0` words is returned again.

### Fallback mechanism

When reading MMIO address `-4` on the original board, only `0` words are
returned. Therefore, the main hardware descriptor appears to be empty. In case
an empty hardware descriptor is read, the system may assume the hardware
configuration to be as follows:

- **0**: 'mVid', 'Timr', 'SwLD', 'SPrt', 'SPIf', 'MsKb'
- **'mVid'**: 1, 0, 1024, 768, 128, 0E7F00H
- **'Timr'**: -64
- **'SwLD'**: -60
- **'SPrt'**: 1, -52, -56
- **'SPIf'**: -44, -48
- **'MsKb'**: -40, -36

## Assigned hardware IDs

### `mVid`: Monochrome video support

The board supports monochrome video, in one or more resolutions.

Values in the `mVid` descriptor (in that order):

- Number of supported video modes (at least 1). Monochrome video modes start
  with mode 0.
- MMIO address to read to get the current video mode, or to write to set it. May
  be 0 if only one mode is supported.

*The following values are repeated for each video mode:*
- Vertical resolution
- Horizontal resolution
- Scan line span in bytes (i.e. how many bytes to increment the address to get to the pixel below)
- Base address of framebuffer

### `mDyn`: Monochrome video dynamic resolution

The board supports monochrome video with dynamic resolutions. Probably only
relevant for emulated systems which can choose arbitrary resolutions. The MMIO
address used for this may be the same as the MMIO address for choosing fixed
resolutions. To choose a dynamic resolution, the value's bit 31 needs to be
cleared and 30 needs to be set. The 15 upper remaining bits (29-15) designate
the width, the 15 lower bits (14-0) designate the height. When reading back the
MMIO address, the same value is returned if the switch has been successful. By
providing 15 bits, a maximum resolution of 32767×32767 is supported.

Values in the `mDyn` descriptor:
- MMIO address to write the resolution to (may be same as used in `mVid`
  descriptor)
- Maximum vertical resolution
- Maximum horizontal resolution.
- Increment in vertical resolution (valid resolutions need to be a multiple of this value)
- Increment in horizontal resolution (likely 1)
- Scan line span in bytes, or -1 to determine it dynamically from the vertical resolution
- Base address of framebuffer

### `16cV`: 16-color video support

The board supports 16-color video mode. It may support both monochrome and 16-color modes, therefore the first color mode may not be value 0.

Values in the `16cV` descriptor (in that order):

- Number of supported video modes (at least 1)
- Number of the first supported video mode
- MMIO address to read to get the current video mode, or to write to set it. May
  be 0 if only one mode is supported, and may be the same address as used in `mVid` and/or `mDyn`.
- MMIO address where the palette starts. May be 0 if palette changes are not supported.

*The following values are repeated for each video mode:*
- Vertical resolution
- Horizontal resolution
- Scan line span in bytes (i.e. how many bytes to increment the address to get to the pixel below)
- Base address of framebuffer

### `16cD`: 16-color video dynamic resolution

The board supports 16-color video with dynamic resolutions. Probably only
relevant for emulated systems which can choose arbitrary resolutions. The MMIO
address used for this may be the same as the other video related MMIO addresses.
To choose a dynamic resolution, the value's bit 31 and 30 need to be set. The 15
upper remaining bits (29-15) designate the width, the 15 lower bits (14-0)
designate the height. When reading back the MMIO address, the same value is
returned if the switch has been successful. By providing 15 bits, a maximum
resolution of 32767×32767 is supported.

Values in the `16cD` descriptor:
- MMIO address to write the resolution to
- MMIO address where the palette starts. May be 0 if palette changes are not supported.
- Maximum vertical resolution
- Maximum horizontal resolution
- Increment in vertical resolution (valid resolutions need to be a multiple of this value)
- Increment in horizontal resolution (likely 1)
- Scan line span in bytes, or -1 to determine it dynamically from the vertical resolution
- Base address of framebuffer

### `vRTC`: Provide a real time clock hint

Probably most useful for emulators. It may be used to provide the clock time (as
returned by `Kernel.Clock`) for a time that corresponds to a timer value that is
in the past, but larger than zero

Values in the `vRTC` descriptor:

- Timer value in the past
- Clock value corresponding to the same time.

The system may then "tick" the real time clock forwards until it reaches the
current timer value.

### `Timr`: Timer (with optional power manaagement)

Defines an MMIO address to obtain the current timer and/or trigger power
management. For power management, the CPU needs a way to detect input (keyboard,
mouse, serial) to wake up without actually running (e.g. interrupts). It also
needs to keep track if any input happened since the last write to the power
management port. When a value is written to the power management port and no
input has happened since the last write, the board may pause the CPU until
either input happens or the timer is larger than the written value.

Values in the `Timr` descriptor

- MMIO address for the timer / power management
- `1` in case power management is supported, `0` otherwise.

## `SwLD`: Switches and LED

Defines an MMIO address where switches can be read and LEDs can be written

Values in the `SwLD` descriptor:
- MMIO Address for switches / LED

## `SPrt`: Serial (RS232) port(s)

Defines MMIO addresses used for serial port(s).

Values in the `SPrt` descriptor:
- Number of serial ports
- MMIO address for reading status. Also used for selecting port (by writing
  0-based index), if more than one port supported.
- MMIO address for reading/writing data

## `SPIf`: Serial Peripheral Interface

Definnes the MMIO address for SPI

Values in the `SPIf` descriptor:
- MMIO address for SPI control
- MMIO address for SPI data

## `MsKb`: Mouse and keyboard

Defines mouse and keyboard input.

Values in the `MsKb` descriptor:
- MMIO address for mouse input / keyboard status
- MMIO address for keyboard input
- Keyboard input mode: `0`: Standard PS/2, `1`: Paravirtualized (ASCII/Unicode codepoints), `2`: Both.

## `HsFs`: Host filesystem

Used in emulators to access files on the host.

Values in the `HsFs` descriptor:
- MMIO address for host FS

## `vDsk`: Paravirtualized disk

Used in emulators for faster disk access and/or to keep the emulator simpler by
skipping SPI implementation.

Values of the `vDsk` descriptor:
- MMIO address for paravirtualized disk.

## `vClp`: Paravirtualized clipboard

Used in emulators to provide access to the host clipboard

Values of the `vClp` descriptor:
- MMIO address for paravirtualized clipboard control
- MMIO address for paravirtualized clipboard data
