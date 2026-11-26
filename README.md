# Power Grid Telegraph

**Telegraphy, radio, TV and satellites that run on real circuits** — an addon for
[Create: Power Grid](https://github.com/patryk3211/PowerGrid) (NeoForge 1.21.1).

Every device is a Power Grid component: keys close real contacts, sounders are coils that pull in
at their rated current, radios draw power from your grid and burn when you overload them. Radio
signals travel with a propagation model — ground wave, sky wave off a day/night ionosphere, line of
sight with terrain, fading — so what you hear depends on frequency, antenna, height and time of day.

> 한국어 설명은 아래에 있습니다.

## Requirements
| | Version |
|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.x |
| Create | 6.0.9 or later (6.0.x) |
| Create: Power Grid | 0.6.0 or later |
| CC: Tweaked | optional — computer peripherals for most stations |

## What's inside
- **Wired telegraphy** — telegraph keys, paddles and electronic keyers, sounders, relays, buzzers, inkers,
  paper-tape perforators and Wheatstone tape transmitters, teleprinters, rotor cipher machine,
  telegraph poles and insulators (wooden pegs leak in rain), switchboards with patch cords,
  **field telephones** with magneto ringing and real voice, **bridge duplex**, **line test set**
  (finds breaks and leaks), **submarine cable** with its RC delay and a **mirror galvanometer**,
  **automatic repeater stations**.
- **Radio** — CW / FSK / AM / FM / SSB transmitters and receivers built from parts (PA, filters,
  LNA, fuses...), handhelds and manpacks with real push-to-talk voice, linear amplifiers,
  antennas from long wires to Yagis, dishes and helices, coax with loss, tuners, SWR meter,
  **VNA** with Smith chart, lightning and lightning arresters, CTCSS tone squelch, repeaters,
  DTMF remote control, APRS position reports, vehicle radios for Create contraptions.
- **Power for the shack** — 13.8 V supply, UPS, fused distribution panel, power lamps, equipment racks.
- **Broadcast and media** — AM/FM broadcast stations, records, PA systems, SSTV, analog TV with
  cameras, bodycams, FPV goggles and VCRs.
- **Space** — launch your own CubeSats (beacons, transponders, SSTV), NOAA-style weather
  satellites sending APT pictures of your area, an ionosonde.
- **On the air** — the NCDXF/IARU beacon network on its real schedule, CW activity on the HF bands,
  a QSO robot that can run a contest, throw a pileup at you or work split as rare DX, contest
  scoring (QSOs × multipliers, dupes).
- **Ponder scenes** for every block (English and Korean).

## Bugs and feedback
Please [open an issue](https://github.com/sparkgap1code/pgtelegraph/issues) with the game log (`logs/latest.log`) or crash report attached, your mod list,
and what you did. Screenshots of the goggle overlay help a lot with circuit problems.

## Building
The other mods are compile-only dependencies and are not in this repository. Put these jars in
`libs/`: Create, Create: Power Grid, Flywheel, Ponder, Registrate, Architectury, CC: Tweaked.
Then run `./gradlew build` (Java 21). The jar is in `build/libs/`.

## License
GPL-3.0-or-later — see `LICENSE`. Third-party material is listed in `NOTICE`.

---

## 한국어

**실제 회로로 돌아가는 전신, 무선, TV, 위성** — Create: Power Grid 애드온입니다(NeoForge 1.21.1).

모든 장치가 Power Grid 부품입니다. 전건은 실제 접점을 닫고, 음향기는 정격 전류에서 당겨지는
코일이며, 무전기는 전력망에서 전기를 끌어 쓰고 과부하가 걸리면 탑니다. 전파는 지상파,
낮과 밤이 다른 전리층 반사파, 지형을 따지는 가시거리, 페이딩까지 계산하므로 주파수, 안테나,
높이, 시간에 따라 들리는 것이 달라집니다.

**필수 모드:** Create 6.0.9 이상, Create: Power Grid 0.6.0 이상. **선택:** CC: Tweaked.

**버그 제보:** [GitHub 이슈](https://github.com/sparkgap1code/pgtelegraph/issues)에 게임 로그(`logs/latest.log`)나 크래시 리포트, 모드 목록, 무엇을 했는지를 이슈로
올려 주십시오. 회로 문제는 고글 화면 스크린샷이 큰 도움이 됩니다.

**라이선스:** GPL-3.0-or-later.
