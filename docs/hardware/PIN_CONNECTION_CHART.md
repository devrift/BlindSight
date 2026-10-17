# BlindSight — pin-by-pin connection chart

> ⚠️ **SUPERSEDED by WIRING_V2_BUCK_AND_TTL.md**
> The dual-Arduino isolated rail wiring design described in this chart has been replaced by the unified 5V 3A buck converter architecture with a single common ground.
> Refer to [WIRING_V2_BUCK_AND_TTL.md](WIRING_V2_BUCK_AND_TTL.md) for the active production wiring specification.

## Main ESP32 DevKit V1

### PCM5102 / PCM5100 I2S DAC
| DAC pin | Main ESP32 pin | Purpose |
|---|---|---|
| VCC | Uno #1 5V rail | DAC power (verify your module supports 5 V) |
| GND | Uno #1 GND rail | common ground |
| BCK / BCLK | GPIO 26 | I2S bit clock |
| LCK / LRCK / WS | GPIO 25 | I2S word-select clock |
| DIN | GPIO 22 | I2S data |
| SCK | leave unconnected | not required by typical PCM5102 modules |
| LOUT, ROUT, AGND | headphone amp / suitable audio input | PCM5102 is line-level; use a headphone amplifier if needed |

### Three HC-SR04 sensors
**Never connect ECHO directly to an ESP32.** Each ECHO wire needs this divider: `ECHO -- 1kΩ --+-- ESP32 GPIO`, with `+ -- 2kΩ -- GND`.

| Sensor | VCC | GND | TRIG | ECHO (through divider) |
|---|---|---|---|---|
| Left | Uno #1 5V | Uno #1 GND | GPIO 13 | GPIO 34 |
| Centre | Uno #1 5V | Uno #1 GND | GPIO 14 | GPIO 35 |
| Right | Uno #1 5V | Uno #1 GND | GPIO 27 | GPIO 39 |

### Fallback button
| Button side | Connection |
|---|---|
| side 1 | GPIO 32 |
| side 2 | Uno #1 GND / main ESP32 GND |

The code uses `INPUT_PULLUP`; pressed is LOW.

## ESP32-CAM (AI Thinker)
| ESP32-CAM pin | Connection |
|---|---|
| 5V | Uno #2 5V rail |
| GND | Uno #2 GND rail |
| U0R, U0T | USB-TTL adapter only while uploading |
| GPIO 0 | Ground only while uploading, then disconnect and reset |

Put a **470–1000 µF, ≥6.3 V capacitor** directly across ESP32-CAM 5V (+) and GND (–).

## Power arrangement
| Rail | Loads |
|---|---|
| Raspberry Pi own supply | Raspberry Pi only |
| Uno #1 5 V/GND distribution rail | main ESP32, DAC, three HC-SR04s, button ground |
| Uno #2 5 V/GND distribution rail | ESP32-CAM only |

Do not join separate 5 V rails. An Uno is only a temporary distribution point here, not a regulator. The Pi/ESP boards communicate wirelessly, so no data wire connects their rails.
