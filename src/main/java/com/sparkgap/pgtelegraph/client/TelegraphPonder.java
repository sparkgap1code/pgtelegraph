package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import org.patryk3211.powergrid.ponder.base.ElectricInstructions;

/**
 * Ponder scenes (hold W over the item). Wires are real Power Grid insulated wire (ElectricInstructions);
 * shaft-driven blocks are shown turned by a spinning cogwheel pair (CreateSceneBuilder).
 */
public class TelegraphPonder implements PonderPlugin {
    @Override
    public String getModId() {
        return PgTelegraph.MODID;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, path);
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(id("transmitter"), id("antenna_tuner"), id("lightning_arrester"), id("dummy_load"))
                .addStoryBoard("radio_station", TelegraphPonder::radioStation);
        helper.forComponents(id("screwdriver"), id("transmitter"), id("receiver"), id("fuse_20a"), id("pa_100w"), id("band_general"),
                        id("filter_crystal_500"), id("regulator_linear"), id("lna_standard"))
                .addStoryBoard("radio_parts", TelegraphPonder::radioParts);
        helper.forComponents(id("transmitter"), id("receiver"), id("telegraph_key"), id("battery_pack_lead_acid"), id("decoder"))
                .addStoryBoard("cw_station", TelegraphPonder::cwStation);
        helper.forComponents(id("battery_pack_li_ion"), id("battery_pack_lifepo4"), id("battery_pack_lead_acid"), id("bms"))
                .addStoryBoard("battery_bms", TelegraphPonder::batteryBms);
        helper.forComponents(id("telegraph_pole"), id("glass_insulator"), id("wooden_peg"), id("teleprinter"), id("decoder"))
                .addStoryBoard("overhead_line", TelegraphPonder::overheadLine);
        helper.forComponents(id("launch_pad"), id("cubesat"), id("rocket"), id("sstv_monitor"), id("rotator"), id("satellite_tracker"))
                .addStoryBoard("satellite_station", TelegraphPonder::satelliteStation);
        helper.forComponents(id("broadcast_transmitter"), id("fm_broadcast_transmitter"), id("broadcast_radio"), id("blank_record"), id("recorded_disc"))
                .addStoryBoard("broadcast", TelegraphPonder::broadcast);
        helper.forComponents(id("fm_broadcast_transmitter"), id("broadcast_radio"))
                .addStoryBoard("fm_broadcast", TelegraphPonder::fmBroadcast);
        helper.forComponents(id("sstv_transmitter"), id("tv_camera"), id("camera"), id("photo"), id("sstv_monitor"))
                .addStoryBoard("sstv_station", TelegraphPonder::sstvStation);
        helper.forComponents(id("linear_amplifier"), id("broadcast_amplifier"), id("coax_thin"), id("coax_thick"),
                        id("coax_hardline"), id("coax_heavy"), id("coax_rigid"))
                .addStoryBoard("high_power", TelegraphPonder::highPower);
        helper.forComponents(id("transmitter"), id("receiver"), id("antenna_rod"), id("coax_thin"), id("antenna_tuner"))
                .addStoryBoard("radio_basics", TelegraphPonder::radioBasics);
        helper.forComponents(id("receiver"), id("transmitter"), id("linear_amplifier"))
                .addStoryBoard("decibels", TelegraphPonder::decibels);
        helper.forComponents(id("ionosonde"), id("antenna_rod"), id("receiver"))
                .addStoryBoard("propagation", TelegraphPonder::propagation);
        helper.forComponents(id("license"), id("logbook"), id("qsl_card"), id("telegraph_key"), id("qso_robot"))
                .addStoryBoard("operating", TelegraphPonder::operating);
        helper.forComponents(id("telegraph_key"), id("sounder"), id("relay"), id("buzzer"))
                .addStoryBoard("wired_telegraph", TelegraphPonder::wiredTelegraph);
        helper.forComponents(id("paddle"), id("keyer"), id("keyer_paddle"), id("clip_key"), id("clip_paddle"))
                .addStoryBoard("keyer", TelegraphPonder::keyer);
        helper.forComponents(id("perforator"), id("tape_transmitter"), id("inker"), id("paper_tape"))
                .addStoryBoard("paper_tape", TelegraphPonder::paperTape);
        helper.forComponents(id("alexanderson_alternator"), id("loading_coil"), id("litz_loading_coil"), id("top_load_wire"), id("ground_mat"))
                .addStoryBoard("vlf_station", TelegraphPonder::vlfStation);
        helper.forComponents(id("yagi_antenna"), id("dish_antenna"), id("helix_antenna"), id("rotator"), id("receiving_loop"), id("phased_array"))
                .addStoryBoard("beam_antennas", TelegraphPonder::beamAntennas);
        helper.forComponents(id("repeater"), id("qso_robot"), id("beacon"), id("solar_beacon"))
                .addStoryBoard("auto_stations", TelegraphPonder::autoStations);
        helper.forComponents(id("microphone"), id("tape_recorder"), id("receiver"))
                .addStoryBoard("voice_station", TelegraphPonder::voiceStation);
        helper.forComponents(id("switchboard"))
                .addStoryBoard("switchboard", TelegraphPonder::switchboard);
        helper.forComponents(id("handheld_radio"), id("handheld_charger"))
                .addStoryBoard("handheld", TelegraphPonder::handheld);
        helper.forComponents(id("swr_meter"), id("field_strength_meter"), id("frequency_counter"), id("direction_finder"), id("spectrum_analyzer"), id("oscilloscope"))
                .addStoryBoard("instruments", TelegraphPonder::instruments);
        helper.forComponents(id("coax_switch"))
                .addStoryBoard("coax_switch", TelegraphPonder::coaxSwitch);
        helper.forComponents(id("spark_transmitter"))
                .addStoryBoard("spark", TelegraphPonder::spark);
        helper.forComponents(id("tv_transmitter"), id("tv_receiver"), id("tv_camera"))
                .addStoryBoard("analog_tv", TelegraphPonder::analogTv);
        helper.forComponents(id("vcr"), id("video_tape"), id("camcorder"))
                .addStoryBoard("vcr", TelegraphPonder::vcr);
        helper.forComponents(id("bodycam"), id("tv_transmitter"))
                .addStoryBoard("bodycam", TelegraphPonder::bodycam);
        helper.forComponents(id("audio_amplifier"), id("speaker"), id("horn_speaker"))
                .addStoryBoard("audio_pa", TelegraphPonder::audioPa);
        helper.forComponents(id("jammer"))
                .addStoryBoard("jammer", TelegraphPonder::jammer);
        helper.forComponents(id("inline_lna"), id("antenna_tuner"))
                .addStoryBoard("lna", TelegraphPonder::lna);
        helper.forComponents(id("power_supply"), id("transmitter"), id("broadcast_radio"), id("sstv_monitor"), id("qso_robot"))
                .addStoryBoard("power_supply", TelegraphPonder::powerSupplyScene);
        helper.forComponents(id("manual_tuner"), id("antenna_tuner"), id("remote_tuner"))
                .addStoryBoard("manual_tuner", TelegraphPonder::manualTunerScene);
        helper.forComponents(id("coax_thin"), id("coax_thick"), id("coax_hardline"), id("coax_heavy"), id("coax_rigid"))
                .addStoryBoard("wall_coax", TelegraphPonder::wallCoaxScene);
        helper.forComponents(id("yagi_antenna"), id("dish_antenna"), id("helix_antenna"))
                .addStoryBoard("antenna_stacking", TelegraphPonder::antennaStackingScene);
        helper.forComponents(id("manpack_radio"))
                .addStoryBoard("manpack", TelegraphPonder::manpackScene);
        helper.forComponents(id("fpv_goggles"), id("tv_camera"), id("bodycam"))
                .addStoryBoard("fpv_goggles", TelegraphPonder::fpvGogglesScene);
        helper.forComponents(id("microphone"), id("audio_amplifier"), id("speaker"), id("horn_speaker"))
                .addStoryBoard("pa_microphone", TelegraphPonder::paMicrophoneScene);
        helper.forComponents(id("ups"), id("power_panel"))
                .addStoryBoard("ups_power_panel", TelegraphPonder::upsPowerPanelScene);
        helper.forComponents(id("equipment_rack"))
                .addStoryBoard("equipment_rack", TelegraphPonder::equipmentRackScene);
        helper.forComponents(id("dtmf_decoder"))
                .addStoryBoard("dtmf_decoder", TelegraphPonder::dtmfDecoderScene);
        helper.forComponents(id("aprs_station"), id("mobile_radio"), id("aprs_tracker"))
                .addStoryBoard("aprs", TelegraphPonder::aprsScene);
        helper.forComponents(id("field_phone"), id("patch_cord"))
                .addStoryBoard("field_phone", TelegraphPonder::fieldPhoneScene);
        helper.forComponents(id("duplex_set"))
                .addStoryBoard("duplex_set", TelegraphPonder::duplexSetScene);
        helper.forComponents(id("line_test_set"), id("line_simulator"))
                .addStoryBoard("line_testing", TelegraphPonder::lineTestingScene);
        helper.forComponents(id("submarine_cable"), id("mirror_galvanometer"))
                .addStoryBoard("submarine_cable", TelegraphPonder::submarineCableScene);
        helper.forComponents(id("telegraph_repeater"))
                .addStoryBoard("telegraph_repeater", TelegraphPonder::telegraphRepeaterScene);
        helper.forComponents(id("creative_transmitter"), id("creative_receiver"), id("creative_antenna"), id("nanovna"))
                .addStoryBoard("creative_radio", TelegraphPonder::creativeRadioScene);
        helper.forComponents(id("vhf_whip"), id("helical_whip"))
                .addStoryBoard("fm_broadcast", TelegraphPonder::fmBroadcast);
        helper.forComponents(id("short_antenna"))
                .addStoryBoard("radio_basics", TelegraphPonder::radioBasics);
    }

    static void radioStation(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("radio_station", "Feeding an antenna safely");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(120).text("A CW transmitter puts out 1-100 W (front panel) at 13.8 V, about 60 % efficient: 100 W out draws about 170 W")
                .pointAt(util.vector().topOf(0, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(130);
        scene.world().showSection(util.select().position(0, 1, 0), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(0, 1, 0), 0, util.grid().at(0, 1, 2), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 0), 1, util.grid().at(0, 1, 2), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(107).text("Its supply goes on the back PWR+ / PWR- terminals (Power Grid wire). Reversed polarity burns it")
                .pointAt(util.vector().topOf(0, 1, 0)).placeNearTarget().attachKeyFrame();
        scene.idle(117);
        scene.world().showSection(util.select().position(0, 1, 4), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(0, 1, 4), 0, util.grid().at(0, 1, 2), 2, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 4), 1, util.grid().at(0, 1, 2), 3, DyeColor.WHITE);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(71).text("A key across the front KEY / COM terminals keys the carrier")
                .pointAt(util.vector().topOf(0, 1, 4)).placeNearTarget();
        scene.idle(81);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(202).text("RF leaves through coax on any free side. A desk antenna tuner in the run matches up to SWR 10:1, a remote tuner at the antenna up to 100:1, and a manual tuner 64:1 at the frequency its knobs are set for; the worse the match, the more they lose")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(212);
        scene.overlay().showText(122).text("Coax is laid like redstone, 1 block = 1 m. Thin coax loses about 0.05 dB/m at 10 MHz and carries at most 200 W")
                .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget();
        scene.idle(132);
        scene.world().showSection(util.select().fromTo(3, 1, 2, 5, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showOutlineWithText(util.select().position(4, 0, 2), 142).colored(PonderPalette.GREEN).text("A lightning arrester stops surges - but only when a ground mat or a Power Grid grounding rod touches it (or sits two blocks below)")
                .pointAt(util.vector().topOf(4, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(152);
        scene.world().showSection(util.select().fromTo(6, 1, 2, 6, 5, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(95).colored(PonderPalette.RED).text("Lightning within 6 blocks of the antenna burns every radio down an unprotected coax")
                .pointAt(util.vector().topOf(6, 2, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(105);
        scene.world().hideSection(util.select().fromTo(6, 1, 2, 6, 5, 2), Direction.UP);
        scene.idle(15);
        scene.world().setBlock(util.grid().at(6, 1, 2), PgTelegraph.DUMMY_LOAD.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(6, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(103).text("To test without radiating, put a dummy load where the antenna was: a 50 Ohm resistor in oil")
                .pointAt(util.vector().topOf(6, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(113);
        scene.overlay().showText(125).colored(PonderPalette.RED).text("All the power becomes heat: 100 W settles about 10 C above air, 1.5 kW near 170 C. Above 180 C the resistor opens")
                .pointAt(util.vector().topOf(6, 1, 2)).placeNearTarget();
        scene.idle(135);
        scene.markAsFinished();
        elec.unload();
    }

    static void batteryBms(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("battery_bms", "Battery packs and the BMS");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(127).text("Three packs: Li-ion 4S 20 Ah (12.0-16.8 V), LiFePO4 4S 50 Ah (10.0-14.6 V, flat near 13.3 V), lead-acid 12 V 100 Ah")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(137);
        scene.overlay().showText(125).text("Each has a current limit: Li-ion 40 A, LiFePO4 100 A, lead-acid 200 A. More than that for a second cooks the pack")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(135);
        scene.overlay().showText(155).colored(PonderPalette.RED).text("A pack has no protection of its own. Overcharged, Li-ion catches fire and explodes, LiFePO4 vents and dies, lead-acid gasses and loses capacity")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(165);
        scene.world().showSection(util.select().position(3, 1, 1), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(0, 1, 1), 0, util.grid().at(3, 1, 1), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 1), 1, util.grid().at(3, 1, 1), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(93).text("Put a BMS between the pack and everything else: BAT+ / BAT- (back) go to the pack")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(103);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(3, 1, 1), 2, util.grid().at(6, 1, 1), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 1), 3, util.grid().at(6, 1, 1), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(116).text("LOAD+ / LOAD- (front) go to the radio - and the charger goes there too, so charging is protected as well")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(126);
        scene.overlay().showText(133).text("Its front panel sets the under-voltage and over-voltage cut-off and the current limit; a MOSFET opens when one is crossed")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(143);
        scene.overlay().showText(92).colored(PonderPalette.BLUE).text("Lead-acid left nearly flat slowly sulphates and loses capacity - keep it charged")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(102);
        scene.overlay().showText(86).colored(PonderPalette.RED).text("Above 100 A its MOSFET fails short - after that, nothing protects the pack")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(96);
        scene.markAsFinished();
        elec.unload();
    }

    static void overheadLine(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("overhead_line", "Overhead lines in the rain");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 5, 3, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(119).text("Stack telegraph poles and put a glass insulator or a wooden peg on top: that is where the line wire is tied")
                .pointAt(util.vector().topOf(3, 3, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(129);
        elec.connect(util.grid().at(3, 3, 1), 0, util.grid().at(5, 3, 1), 0, DyeColor.GRAY);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(86).text("Run Power Grid wire from tie point to tie point (up to 16 blocks per span)")
                .pointAt(util.vector().topOf(4, 3, 1)).placeNearTarget();
        scene.idle(96);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 1, 1, 2), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(0, 1, 0), 0, util.grid().at(1, 1, 1), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(1, 1, 1), 1, util.grid().at(3, 3, 1), 0, DyeColor.GRAY);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 0), 1, util.grid().at(0, 1, 2), 0, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(101).text("Sending end: battery + to the key, the key to the line. Battery - goes to a grounding rod")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(111);
        scene.world().showSection(util.select().fromTo(7, 1, 1, 8, 1, 2), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(5, 3, 1), 0, util.grid().at(7, 1, 1), 0, DyeColor.GRAY);
        scene.idle(4);
        elec.connect(util.grid().at(7, 1, 1), 1, util.grid().at(8, 1, 2), 0, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(150).colored(PonderPalette.GREEN).text("Receiving end: line to the decoder (or a sounder), then to a second grounding rod. The earth carries the current back - one wire is enough")
                .pointAt(util.vector().topOf(7, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(160);
        scene.overlay().showText(111).text("The decoder pulls in at 10 mA, times the marks and spaces, and writes the text after 2 s of silence")
                .pointAt(util.vector().topOf(7, 1, 1)).placeNearTarget();
        scene.idle(121);
        scene.overlay().showText(97).colored(PonderPalette.BLUE).text("In rain every tie point leaks to earth: a glass insulator 50 MOhm dry, 50 kOhm wet...")
                .pointAt(util.vector().topOf(3, 3, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(107);
        scene.overlay().showText(102).colored(PonderPalette.RED).text("...a wooden peg 50 kOhm dry but only 400 Ohm wet. A long line on pegs goes dead in a storm")
                .pointAt(util.vector().topOf(5, 3, 1)).placeNearTarget();
        scene.idle(112);
        scene.overlay().showText(111).text("On a long line use glass, a higher battery voltage, or a relay at the far end (see Wired telegraph)")
                .pointAt(util.vector().topOf(7, 1, 1)).placeNearTarget();
        scene.idle(121);
        scene.world().showSection(util.select().position(8, 1, 0), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(144).text("A teleprinter types over the same kind of line: ITA2 code at 10 baud. TX 1/2 is its sending contact, RX 1/2 a 100 Ohm magnet (10 mA)")
                .pointAt(util.vector().topOf(8, 1, 0)).placeNearTarget().attachKeyFrame();
        scene.idle(154);
        scene.markAsFinished();
        elec.unload();
    }

    static void satelliteStation(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cs = new CreateSceneBuilder(scene);
        scene.title("satellite_station", "Launching and working a satellite");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(129).text("First set up a CubeSat (use it): name, TX power and payloads - CW beacon, transponder, RTTY, SSTV, mailbox, ionosonde")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(139);
        scene.overlay().showText(145).text("Put a rocket on the pad, then the CubeSat, then an empty hand starts the 10 s countdown (or a redstone pulse). 5 % blow up on the pad")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(155);
        scene.overlay().showText(144).text("Small rocket: 500-800 km, a pass every 16 min, decays in hours to days. Medium: 1200-2000 km, lasts. Heavy: geostationary, always up")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(154);
        scene.overlay().showText(111).colored(PonderPalette.BLUE).text("The pad's facing is the launch direction: east for the lowest orbit, north or south for a polar one")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(121);
        scene.world().showSection(util.select().fromTo(4, 1, 3, 4, 2, 3), Direction.DOWN);
        scene.idle(6);
        cs.world().setKineticSpeed(util.select().position(4, 0, 3), 64f);
        cs.world().setKineticSpeed(util.select().position(3, 0, 2), -32f);
        scene.idle(12);
        scene.overlay().showText(136).text("Put the yagi on a rotator and drive it with a shaft from below (here a cogwheel): 100:1, 64 RPM is about one turn in 2.5 min")
                .pointAt(util.vector().topOf(4, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(146);
        scene.overlay().showText(103).text("Set the antenna to 'Aim: satellite': it follows the pass and corrects the radio for Doppler")
                .pointAt(util.vector().topOf(4, 2, 3)).placeNearTarget();
        scene.idle(113);
        scene.overlay().showText(128).text("Hold a Satellite Tracker to see the satellite overhead (azimuth / elevation, range, beacon Doppler) or the next pass")
                .pointAt(util.vector().topOf(4, 2, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(138);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 6, 1, 4), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(116).text("Receivers hear the beacon and transponder; an SSTV receiver paints the satellite's pictures of the Earth")
                .pointAt(util.vector().topOf(6, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(126);
        scene.overlay().showText(112).colored(PonderPalette.BLUE).text("Satellites are above 30 MHz, so it is line of sight: you hear one only while it is above the horizon")
                .pointAt(util.vector().topOf(5, 1, 4)).placeNearTarget();
        scene.idle(122);
    }

    static void broadcast(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("broadcast", "Running a broadcast station");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.overlay().showText(93).text("Use a blank record to import an MP3, WAV or OGG file - it becomes a recorded disc")
                .pointAt(util.vector().topOf(3, 0, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(103);
        scene.world().showSection(util.select().position(0, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(141).text("Put the record (or any music disc) on the broadcast transmitter: it plays it on the air, looping. Sneak + empty hand takes it off")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(151);
        scene.overlay().showText(129).text("It needs a 250 V supply (works from 120 V) and gives up to 1 kW carrier, 70 % efficient - 1 kW out draws about 1.4 kW")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(139);
        scene.overlay().showText(148).colored(PonderPalette.BLUE).text("It broadcasts AM from 150 kHz to 30 MHz: long, medium (530-1700 kHz) and short wave. By night medium wave reaches far off the ionosphere")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(158);
        scene.overlay().showText(159).text("For FM there is the blue FM broadcast transmitter: 87.5-108 MHz built in, clean wide audio, but only as far as line of sight - put its antenna high")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(169);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 5, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(111).text("Like any transmitter it needs coax to a matched antenna. Medium wave wants a tall mast - or a tuner")
                .pointAt(util.vector().topOf(3, 2, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(121);
        scene.overlay().showText(110).text("A microphone next to it goes out live over the record. INHIBIT shorted to COM takes it off the air")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(120);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(151).colored(PonderPalette.GREEN).text("The station is the clock: its record keeps turning on the air. A radio switched on 30 s into a song joins it 30 s in, like a real broadcast")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(161);
        scene.overlay().showText(118).text("A radio tuned to its frequency plays the programme. Empty hand: on / off, sneak: tune (starts at 1000 kHz)")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget();
        scene.idle(128);
        scene.overlay().showText(179).text("What you hear went through the air: AM sounds narrow (up to 4.5 kHz) and gets quieter and noisier as it weakens; FM is wide and clean, then crackles near the threshold")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget();
        scene.idle(189);
        scene.overlay().showText(95).colored(PonderPalette.BLUE).text("Its built-in ferrite rod is 20 dB down: coax to an outside antenna hears far better")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget();
        scene.idle(105);
    }

    static void sstvStation(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("sstv_station", "Sending pictures: SSTV");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.overlay().showText(80).text("A hand camera takes a 320x256 photo of what you see, on one sheet of paper")
                .pointAt(util.vector().topOf(1, 0, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 1, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("Put the photo on the SSTV transmitter - or let a TV camera touching it send a live picture")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.world().showSection(util.select().fromTo(2, 1, 1, 3, 4, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("It sends a line every 0.2 s, 51 s a frame - like any transmitter it needs power and an antenna")
                .pointAt(util.vector().topOf(3, 4, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.world().showSection(util.select().fromTo(4, 1, 3, 6, 4, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("An SSTV receiver on the same frequency paints it line by line. Paper in hand prints the screen")
                .pointAt(util.vector().topOf(6, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90).colored(PonderPalette.RED)
                .text("Below 30 MHz tune exactly: 100 Hz off shifts the brightness. Sky-wave pictures flutter and ghost")
                .pointAt(util.vector().topOf(6, 1, 3)).placeNearTarget();
        scene.idle(100);
    }

    static void highPower(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("high_power", "High power: amplifiers and coax");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 2, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("Amplifiers go in the coax run. Any side works: the side whose coax leads to the antenna is the output")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 8, 6, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90).text("Each stage adds 20 dB: 100 W -> linear amplifier 10 kW -> broadcast amplifier up to 200 kW")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90).text("Every coax has a power rating: thin 200 W, thick 1.5 kW, hardline 3 kW, heavy 20 kW, rigid line 500 kW")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90).colored(PonderPalette.RED)
                .text("Overload a run and its weakest piece burns through. Each stage needs coax rated above its output")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(100);
    }

    static void wiredTelegraph(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("wired_telegraph", "Wired telegraph: key, relay, sounder");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 3), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(139).text("A telegraph key is only a switch: its two terminals are joined while you hold it. It needs a battery and a receiver in one loop")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(149);
        scene.world().showSection(util.select().position(0, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(util.select().position(7, 1, 3), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(0, 1, 1), 0, util.grid().at(1, 1, 3), 0, DyeColor.RED);
        scene.idle(4);
        var wLine = elec.connect(util.grid().at(1, 1, 3), 1, util.grid().at(7, 1, 3), 0, DyeColor.GRAY);
        scene.idle(4);
        var wRet = elec.connect(util.grid().at(7, 1, 3), 1, util.grid().at(0, 1, 1), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(117).text("Simplest line: battery + -> key -> sounder -> back to battery -. 12 V through the 20 Ohm sounder is 0.6 A")
                .pointAt(util.vector().topOf(7, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(127);
        scene.overlay().showText(123).text("The sounder pulls in at 0.1 A (click) and drops below 0.07 A (clack): you read the gaps between click and clack")
                .pointAt(util.vector().topOf(7, 1, 3)).placeNearTarget();
        scene.idle(133);
        elec.removeWire(wRet);
        scene.idle(6);
        scene.world().showSection(util.select().position(0, 1, 3), Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(util.select().position(5, 1, 4), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(0, 1, 1), 1, util.grid().at(0, 1, 3), 0, DyeColor.BLACK);
        scene.idle(4);
        var wGnd = elec.connect(util.grid().at(7, 1, 3), 1, util.grid().at(5, 1, 4), 0, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(132).colored(PonderPalette.GREEN).text("Real lines save the return wire: ground both ends with a Power Grid grounding rod and the earth carries the current back")
                .pointAt(util.vector().topOf(0, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(142);
        scene.overlay().showText(84).text("Earth adds resistance: conductive blocks around a grounding rod lower it")
                .pointAt(util.vector().topOf(5, 1, 4)).placeNearTarget();
        scene.idle(94);
        elec.removeWire(wLine);
        scene.idle(6);
        elec.removeWire(wGnd);
        scene.idle(6);
        scene.world().showSection(util.select().position(5, 1, 2), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(1, 1, 3), 1, util.grid().at(5, 1, 2), 0, DyeColor.GRAY);
        scene.idle(4);
        elec.connect(util.grid().at(5, 1, 2), 1, util.grid().at(5, 1, 4), 0, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(124).text("A long line is too weak for a sounder. Put a relay at the far end: the line goes through its rear coil (150 Ohm)")
                .pointAt(util.vector().topOf(5, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(134);
        scene.overlay().showText(93).text("It picks up at 5-200 mA (top panel, default 20 mA) and lets go below 70 % of that")
                .pointAt(util.vector().topOf(5, 1, 2)).placeNearTarget();
        scene.idle(103);
        scene.world().showSection(util.select().position(7, 1, 0), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(7, 1, 0), 0, util.grid().at(5, 1, 2), 2, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(5, 1, 2), 3, util.grid().at(7, 1, 3), 0, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(7, 1, 3), 1, util.grid().at(7, 1, 0), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(150).colored(PonderPalette.BLUE).text("Its front contact is a switch for a local loop: local battery + -> contact -> contact -> sounder -> battery -. Now the sounder clicks hard")
                .pointAt(util.vector().topOf(7, 1, 0)).placeNearTarget().attachKeyFrame();
        scene.idle(160);
        scene.world().showSection(util.select().position(8, 1, 4), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(103).text("For practice a buzzer (100 Ohm, beeps from 10 mA, 400-1000 Hz) can take the sounder's place")
                .pointAt(util.vector().topOf(8, 1, 4)).placeNearTarget().attachKeyFrame();
        scene.idle(113);
        scene.overlay().showText(89).text("Sneak R-click the key for focus mode: key with Space or the left mouse button")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget();
        scene.idle(99);
        scene.markAsFinished();
        elec.unload();
    }

    static void keyer(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("keyer", "Paddles and keyers");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(107).text("A paddle has COM in the middle and DIT / DAH contacts on each side. It has no timing of its own")
                .pointAt(util.vector().topOf(0, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(117);
        scene.world().showSection(util.select().position(2, 1, 2), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(0, 1, 2), 0, util.grid().at(2, 1, 2), 0, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 2), 1, util.grid().at(2, 1, 2), 1, DyeColor.BLACK);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 2), 2, util.grid().at(2, 1, 2), 2, DyeColor.RED);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(117).text("Wire it to the back of an electronic keyer: DIT to DIT, COM to COM, DAH to DAH. The keyer needs no supply")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(127);
        scene.overlay().showText(100).text("It closes its front OUT-OUT contact in correct Morse timing, 5-24 WPM on the front panel")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget();
        scene.idle(110);
        scene.world().showSection(util.select().fromTo(4, 1, 0, 4, 1, 2), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(4, 1, 0), 0, util.grid().at(4, 1, 2), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(4, 1, 0), 1, util.grid().at(4, 1, 2), 1, DyeColor.BLACK);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 3, util.grid().at(4, 1, 2), 2, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 4, util.grid().at(4, 1, 2), 3, DyeColor.WHITE);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(119).text("Wire the keyer's two OUT terminals to the transmitter's KEY and COM - exactly where a straight key would go")
                .pointAt(util.vector().topOf(4, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(129);
        scene.overlay().showText(132).colored(PonderPalette.BLUE).text("Squeeze both paddles and dits and dahs alternate (iambic). R-click the paddle for focus mode: Z / X or the mouse buttons")
                .pointAt(util.vector().topOf(0, 1, 2)).placeNearTarget();
        scene.idle(142);
        scene.world().showSection(util.select().position(6, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(120).text("A keyer paddle has the keyer built in: wire its two OUT terminals straight to KEY / COM. No supply, 5-40 WPM")
                .pointAt(util.vector().topOf(6, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(130);
        scene.overlay().showText(81).colored(PonderPalette.RED).text("Its contact is rated 30 V, 0.5 A - switch heavy loads through a relay")
                .pointAt(util.vector().topOf(6, 1, 2)).placeNearTarget();
        scene.idle(91);
        scene.overlay().showText(150).text("A clip key or clip paddle needs no block: its leads are insulated copper wire. Sneak R-click two terminals (KEY, then COM) to clip them on")
                .pointAt(util.vector().topOf(4, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(160);
        scene.markAsFinished();
        elec.unload();
    }

    static void paperTape(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        CreateSceneBuilder cs = new CreateSceneBuilder(scene);
        scene.title("paper_tape", "Sending from punched tape");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(119).text("A perforator punches a typed message into paper tape in Morse - letters, digits and Hangul. No power needed")
                .pointAt(util.vector().topOf(0, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(129);
        scene.world().showSection(util.select().position(2, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(89).text("A tape transmitter reads the tape with a star wheel and works its OUT contact")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(99);
        scene.world().showSection(util.select().fromTo(2, 1, 1, 3, 2, 1), Direction.DOWN);
        scene.idle(6);
        cs.world().setKineticSpeed(util.select().position(2, 1, 1), 64f);
        cs.world().setKineticSpeed(util.select().position(3, 2, 1), -32f);
        scene.idle(12);
        scene.overlay().showText(111).text("Its tape is pulled by Create rotation on the back, sides or bottom: RPM / 4 = WPM (64 RPM = 16 WPM)")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(121);
        scene.overlay().showText(70).colored(PonderPalette.RED).text("Below 8 RPM it stalls, above 128 RPM the tape tears")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget();
        scene.idle(80);
        scene.world().showSection(util.select().position(4, 1, 0), Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(util.select().position(5, 1, 2), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(4, 1, 0), 0, util.grid().at(2, 1, 2), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 1, util.grid().at(5, 1, 2), 0, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(5, 1, 2), 1, util.grid().at(4, 1, 0), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(131).text("Its OUT is a contact like a key: put it in a loop with a battery and an inker here, or across a transmitter's KEY / COM")
                .pointAt(util.vector().topOf(4, 1, 0)).placeNearTarget().attachKeyFrame();
        scene.idle(141);
        scene.overlay().showText(102).text("An inker draws the marks on tape from 40 mA. Right-click the tape to read the decoded text")
                .pointAt(util.vector().topOf(5, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(112);
        scene.overlay().showText(98).text("An inked tape can go back into a tape transmitter: it plays back as received at 48 RPM")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget();
        scene.idle(108);
        scene.markAsFinished();
        elec.unload();
    }

    static void vlfStation(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cs = new CreateSceneBuilder(scene);
        scene.title("vlf_station", "A VLF station");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(130).text("An Alexanderson alternator makes RF by rotation alone - it is keyed through its KEY / COM terminals like a transmitter")
                .pointAt(util.vector().topOf(0, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(140);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 1, 2, 1), Direction.DOWN);
        scene.idle(6);
        cs.world().setKineticSpeed(util.select().position(0, 1, 1), 256f);
        cs.world().setKineticSpeed(util.select().position(1, 2, 1), -128f);
        scene.idle(12);
        scene.overlay().showText(102).text("Drive it with Create rotation on its back: frequency = RPM x 67.19 Hz (256 RPM = 17.2 kHz)")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(112);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 5, 6, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(101).text("At VLF any mast is far too short. Wire alternator -> coax -> loading coil -> coax -> mast")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(111);
        scene.overlay().showText(139).text("Set the coil's inductance with an empty hand and watch resonance through Engineer's Goggles: the bandwidth is only about 100 Hz")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget();
        scene.idle(149);
        scene.world().showSection(util.select().fromTo(3, 4, 2, 8, 6, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(118).text("Top-load wire from the mast top adds capacitance and effective height - about 3x the mast height is plenty")
                .pointAt(util.vector().topOf(6, 3, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(128);
        scene.overlay().showText(116).colored(PonderPalette.GREEN).text("Ground mats within 24 blocks of the mast foot cut the ground loss - the biggest factor in VLF efficiency")
                .pointAt(util.vector().topOf(5, 0, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(126);
        scene.overlay().showText(160).colored(PonderPalette.BLUE).text("The earth and the bottom of the ionosphere guide VLF like a pipe: it loses only 2-3 dB per 1000 km, day and night, and is heard thousands of km away")
                .pointAt(util.vector().topOf(5, 3, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(170);
        scene.overlay().showText(97).colored(PonderPalette.RED).text("Keep the speed steady: a speed change moves the frequency off the antenna's resonance")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(107);
    }

    static void beamAntennas(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cs = new CreateSceneBuilder(scene);
        scene.title("beam_antennas", "Directional antennas");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 2, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(136).text("Yagi, helix and dish antennas are directional. They are placed pointing where you look; empty hand opens azimuth / elevation")
                .pointAt(util.vector().topOf(1, 2, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(146);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(6);
        cs.world().setKineticSpeed(util.select().position(1, 0, 1), 64f);
        cs.world().setKineticSpeed(util.select().position(2, 0, 2), -32f);
        scene.idle(12);
        scene.overlay().showText(126).text("Or put a rotator under it and drive it with Create rotation from below: 100:1, 64 RPM is about one turn in 2.5 min")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(136);
        scene.world().showSection(util.select().position(5, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(123).text("The narrower the beam, the more exactly you must aim: yagi 12 dBi / 40 deg, dish about 38 dBi / 2 deg at 10 GHz")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(133);
        scene.overlay().showText(70).colored(PonderPalette.BLUE).text("Set the band on the antenna's side panel")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget();
        scene.idle(80);
        scene.world().showSection(util.select().position(5, 1, 3), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(151).text("A receiving loop hears LF/VLF about as well as a huge antenna. Its deep null broadside finds a station's bearing (sneak R-click to turn it)")
                .pointAt(util.vector().topOf(5, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(161);
    }

    static void autoStations(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("auto_stations", "Stations that run themselves");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(119).text("A repeater hears its dial frequency and re-sends it on dial + offset (HF +100 kHz, VHF +600 kHz, UHF +5 MHz). One antenna, up to 50 W")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(129);
        scene.overlay().showText(70).text("Signed with a license, it sends DE call/R every 10 minutes while idle")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(80);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 3, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(108).text("A QSO robot is a 5 W CW station with its own operator: call CQ and it answers with a report, and signs off after TU or 73")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(118);
        scene.world().showSection(util.select().fromTo(5, 1, 1, 5, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(99).text("A beacon keys itself from a program (sneak + empty hand): a message, an NDB for direction finders, or a carrier")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(109);
        scene.world().showSection(util.select().fromTo(7, 1, 1, 7, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(78).text("A solar beacon needs no supply: 5 W panel, 20 Wh battery, 0.1-5 W out. Give it open sky")
                .pointAt(util.vector().topOf(7, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(88);
        scene.overlay().showText(70).colored(PonderPalette.RED).text("Each still needs coax to an antenna")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(80);
    }

    static void voiceStation(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("voice_station", "Talking on the air");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 3, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).text("Set the transmitter to AM or FM (button in the tuning screen)")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(80);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 0, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(91).text("Put a microphone against it. Within 4 blocks, hold push-to-talk (V) and talk into your real microphone")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(101);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 5, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(105).text("Receivers in the same mode play your voice. FM is clean above about 14 dB SNR; AM gets steadily noisier as it weakens")
                .pointAt(util.vector().topOf(5, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(115);
        scene.world().showSection(util.select().fromTo(6, 1, 3, 6, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(99).text("A tape recorder touching the receiver records 5 minutes onto magnetic tape. Empty hand: stop -> record -> play")
                .pointAt(util.vector().topOf(6, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(109);
    }

    static void switchboard(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("switchboard", "Patching lines on a switchboard");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 3, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(99).text("A switchboard takes 8 lines. R-click two jacks in turn to patch them with a cord; the same pair again pulls it")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(109);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 6, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).text("A call - 5 mA or more on an idle line - drops that line's orange shutter")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(80);
        scene.overlay().showText(70).colored(PonderPalette.RED).text("The rear common terminal must be grounded")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(80);
        scene.overlay().showText(93).text("Sneak R-click a jack to pull all its cords. Wire line terminals between boards to chain them with trunks")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(103);
    }

    static void radioBasics(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("radio_basics", "Radio basics 1: frequency and antennas");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 0, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).text("A radio station is three things: a radio, a feed line (coax) and an antenna")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(80);
        scene.overlay().showText(109).text("Frequency and wavelength go together: wavelength (m) = 300 / frequency (MHz). 7 MHz is the 40 m band, 14 MHz the 20 m band")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(119);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 2, 5, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(109).text("An antenna works best at resonance. Rods stacked up from the feed make a vertical: about 75 / length MHz (5 rods = 15 MHz)")
                .pointAt(util.vector().topOf(2, 5, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(119);
        scene.world().showSection(util.select().fromTo(3, 1, 0, 6, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(95).text("Rods extended both ways from the feed make a dipole: about 150 / total length MHz (11 rods = about 14 MHz)")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(105);
        scene.overlay().showText(108).colored(PonderPalette.RED).text("A wrong length reflects power back (high SWR) and overheats the transmitter. An antenna tuner in the coax fixes the match")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(118);
        scene.overlay().showText(100).text("Every metre of coax loses a little, more at higher frequency: keep runs short and use thicker coax for long ones")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget();
        scene.idle(110);
    }

    static void decibels(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("decibels", "Radio basics 2: decibels and signals");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 4, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(88).text("Radio power is counted in decibels: +3 dB = twice the power, +10 dB = 10 times, +20 dB = 100 times")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(98);
        scene.overlay().showText(83).text("So 100 W instead of 10 W is only +10 dB - a better antenna or a quieter band often helps more")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(93);
        scene.overlay().showText(99).text("The S-meter shows signal strength: S9 = -73 dBm, each S-unit is 6 dB. Engineer's Goggles show it on a receiver")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(109);
        scene.overlay().showText(109).colored(PonderPalette.BLUE).text("What decides whether you can copy is signal over noise (SNR). Noise is highest on low bands, at night and in thunderstorms")
                .pointAt(util.vector().topOf(3, 4, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(119);
        scene.overlay().showText(77).text("Narrow modes win: CW in a 500 Hz filter lets in about 11 dB less noise than AM (6 kHz)")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(87);
    }

    static void propagation(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("propagation", "Radio basics 3: how signals travel");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 0, 4, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(93).text("Low frequencies (LF / MF) follow the curve of the ground: the ground wave. Its loss grows with frequency")
                .pointAt(util.vector().topOf(0, 4, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(103);
        scene.world().showSection(util.select().fromTo(8, 1, 1, 8, 4, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(95).text("On HF (3-30 MHz) the signal bounces off the ionosphere - the sky wave - and can reach far across the world")
                .pointAt(util.vector().topOf(8, 4, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(105);
        scene.overlay().showText(93).text("Only below the MUF (maximum usable frequency): about 20 MHz by day and 10 MHz at night at an average sun")
                .pointAt(util.vector().topOf(4, 4, 1)).placeNearTarget();
        scene.idle(103);
        scene.overlay().showText(82).colored(PonderPalette.BLUE).text("By day the D layer swallows the low bands: use about 14-21 MHz by day and 3.5-7 MHz at night")
                .pointAt(util.vector().topOf(4, 4, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(92);
        scene.overlay().showText(83).text("Close to the MUF there is a skip zone: too far for the ground wave, too near for the sky wave")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget();
        scene.idle(93);
        scene.overlay().showText(100).text("Above 30 MHz (VHF / UHF) it is line of sight: height matters most - put antennas high, or go through a satellite")
                .pointAt(util.vector().topOf(8, 4, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.world().showSection(util.select().fromTo(4, 1, 1, 4, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(110).colored(PonderPalette.RED).text("Geomagnetic storms lower the MUF and solar flares black out the low HF by day for a few minutes. The ionosonde shows it all")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(120);
    }

    static void operating(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("operating", "Radio basics 4: making a contact");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 4, 1), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(2, 1, 0), 0, util.grid().at(2, 1, 1), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 0), 1, util.grid().at(2, 1, 1), 1, DyeColor.BLACK);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 1), 0, util.grid().at(2, 1, 1), 2, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(0, 1, 1), 1, util.grid().at(2, 1, 1), 3, DyeColor.WHITE);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(145).text("The station: battery to PWR+ / PWR- (back), key to KEY / COM (front), coax to an antenna. \"Wiring a CW station\" shows it step by step")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(155);
        scene.overlay().showText(112).text("Name a Station License in an anvil: that is your callsign. Use it on a beacon or repeater to sign it")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(122);
        scene.overlay().showText(109).text("To find a contact, call CQ: CQ CQ DE <your call> K. Whoever hears you answers with their callsign")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(119);
        scene.overlay().showText(91).text("Exchange an RST report: Readability 1-5, Strength 1-9, Tone 1-9. 599 is perfect")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(101);
        scene.overlay().showText(128).colored(PonderPalette.BLUE).text("Common shorthand: QTH = location, QSL = received / confirm, QRZ = who is calling?, TU = thank you, 73 = best regards")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(138);
        scene.overlay().showText(125).text("Write each contact in the Station Logbook (call, frequency, RST, note); a logbook entry fills in a blank QSL card")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(135);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(79).text("Nobody else on the air? A QSO robot answers your CQ - good practice")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(89);
        scene.markAsFinished();
        elec.unload();
    }

    static void cwStation(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("cw_station", "Wiring a CW station");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(129).text("A transmitter has four wire terminals: PWR+ / PWR- on the back, KEY / COM on the front. Look at it to see their names")
                .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(139);
        scene.world().showSection(util.select().position(1, 1, 0), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(1, 1, 0), 0, util.grid().at(1, 1, 2), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(1, 1, 0), 1, util.grid().at(1, 1, 2), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(136).text("Power first: battery + to PWR+, battery - to PWR-, with Power Grid wire. 13.8 V gives full power; reversed polarity burns it")
                .pointAt(util.vector().topOf(1, 1, 0)).placeNearTarget().attachKeyFrame();
        scene.idle(146);
        scene.overlay().showText(94).text("100 W out draws about 12 A: any of the battery packs can do it (Li-ion up to 40 A)")
                .pointAt(util.vector().topOf(1, 1, 0)).placeNearTarget();
        scene.idle(104);
        scene.world().showSection(util.select().position(1, 1, 4), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(1, 1, 4), 0, util.grid().at(1, 1, 2), 2, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(1, 1, 4), 1, util.grid().at(1, 1, 2), 3, DyeColor.WHITE);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(127).text("Then the key: one terminal to KEY, the other to COM. Holding the key closes them and the carrier goes out (CW mode)")
                .pointAt(util.vector().topOf(1, 1, 4)).placeNearTarget().attachKeyFrame();
        scene.idle(137);
        scene.overlay().showText(125).colored(PonderPalette.BLUE).text("Anything that closes a contact can key it instead: an electronic keyer's OUT, a tape transmitter, a relay contact")
                .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget();
        scene.idle(135);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 4, 4, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(119).colored(PonderPalette.RED).text("Last, coax from any free side to a matched antenna. Never key without one: the reflected power overheats it")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(129);
        scene.overlay().showText(85).text("Empty hand on it opens the dial: type the frequency in kHz or pick a band")
                .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget();
        scene.idle(95);
        scene.world().showSection(util.select().fromTo(6, 1, 0, 8, 4, 2), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(6, 1, 0), 0, util.grid().at(6, 1, 2), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(6, 1, 0), 1, util.grid().at(6, 1, 2), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(131).text("A receiver is wired the same way: power on the back, coax to an antenna. Tune it to the same frequency to hear the tone")
                .pointAt(util.vector().topOf(6, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(141);
        scene.world().showSection(util.select().position(6, 1, 4), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(6, 1, 0), 0, util.grid().at(6, 1, 4), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(6, 1, 4), 1, util.grid().at(6, 1, 2), 2, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(6, 1, 2), 3, util.grid().at(6, 1, 0), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(177).text("Its front OUT contact closes while a signal is heard. Put it in a loop with a battery and a decoder, sounder or inker: battery + -> device -> OUT -> OUT -> battery -")
                .pointAt(util.vector().topOf(6, 1, 4)).placeNearTarget().attachKeyFrame();
        scene.idle(187);
        scene.overlay().showText(133).text("The squelch on its front panel sets how strong a signal must be to close OUT. Engineer's Goggles show the S-meter and SWR")
                .pointAt(util.vector().topOf(6, 1, 2)).placeNearTarget();
        scene.idle(143);
        scene.markAsFinished();
        elec.unload();
    }

    static void fmBroadcast(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("fm_broadcast", "FM broadcasting");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(0, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(160).text("The blue FM broadcast transmitter has the FM band built in: 87.5-108 MHz, up to 1 kW, 250 V supply. Records and the microphone work as on the AM set")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(170);
        scene.overlay().showText(136).colored(PonderPalette.BLUE).text("FM swings the frequency by up to 75 kHz with the sound: 15 kHz audio, clean and wide - a strong station has no static at all")
                .pointAt(util.vector().topOf(0, 1, 1)).placeNearTarget();
        scene.idle(146);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 3, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(164).text("VHF goes only as far as line of sight, so height is everything: the radio horizon is 4.12 x (sqrt h1 + sqrt h2) km. Put the antenna on a tower or a hill")
                .pointAt(util.vector().topOf(3, 3, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(174);
        scene.overlay().showText(94).colored(PonderPalette.RED).text("Hills and buildings in the way cost a lot: the far side of a mountain hears little")
                .pointAt(util.vector().topOf(3, 2, 1)).placeNearTarget();
        scene.idle(104);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(96).text("A radio tuned above 30 MHz is an FM set: tune it to the station (sneak + empty hand)")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(106);
        scene.overlay().showText(146).text("Near the edge FM does not fade into hiss like AM: it stays clean, then crackles and drops out, and the radio turns a weak station down")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget();
        scene.idle(156);
        scene.overlay().showText(125).colored(PonderPalette.BLUE).text("Two stations on one frequency: the stronger captures the radio completely and the other vanishes (capture effect)")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget();
        scene.idle(135);
    }

    static void radioParts(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("radio_parts", "Fitting the parts");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(94).colored(PonderPalette.RED).text("A freshly built radio is an empty case: it does nothing until its parts are fitted")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(104);
        scene.overlay().showControls(util.vector().topOf(1, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 60)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:screwdriver"))));
        scene.idle(8);
        scene.overlay().showText(112).text("R-click it with a screwdriver to open the parts bay. Engineer's Goggles list the parts still missing")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(122);
        scene.overlay().showControls(util.vector().topOf(1, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 40)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:fuse_20a"))));
        scene.idle(8);
        scene.overlay().showText(136).text("A transmitter needs a fuse (20 A for 100 W), a power stage (PA: 10, 50 or 100 W) and a band module that covers the frequency")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(146);
        scene.overlay().showControls(util.vector().topOf(1, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 40)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:lpf_hf"))));
        scene.idle(8);
        scene.overlay().showText(162).colored(PonderPalette.BLUE).text("Optional: a low-pass filter stops harmonics (without one the 2nd harmonic is only 25 dB down); a TCXO, OCXO or GPSDO keeps the frequency from drifting")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(172);
        scene.world().showSection(util.select().position(5, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.overlay().showControls(util.vector().topOf(5, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 40)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:filter_crystal_500"))));
        scene.idle(8);
        scene.idle(12);
        scene.overlay().showText(115).text("A receiver needs a fuse (2 A), a regulator, a low-noise amplifier (LNA), an IF filter and a band module")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(125);
        scene.overlay().showText(131).text("The IF filter sets what you hear: 250 or 500 Hz crystal for CW, 2.4 kHz ceramic for voice. The narrower, the less noise")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget();
        scene.idle(141);
        scene.overlay().showText(108).colored(PonderPalette.BLUE).text("Optional: an EMI filter keeps out the hash of nearby machines; a better LNA hears weaker signals")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget();
        scene.idle(118);
        scene.overlay().showText(121).colored(PonderPalette.GREEN).text("Broken up, a radio keeps its parts inside: carry it away and place it again as it was. Its tooltip lists them")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(131);
        scene.overlay().showText(138).text("A handheld opens its parts with the Parts button on its panel: battery, regulator, LNA, IF filter, power stage and band module")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(148);
    }

    static void handheld(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("handheld", "The handheld radio");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(3, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 60)
                .rightClick().whileSneaking().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:handheld_radio"))));
        scene.idle(8);
        scene.overlay().showText(133).text("The handheld is a 2 m / 70 cm CW transceiver you carry: 0.5, 1 or 5 W into a rubber-duck antenna, a 7.4 V 14.8 Wh battery")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(143);
        scene.overlay().showText(190).text("Sneak R-click opens its panel: power, frequency, output, squelch - and the Parts button. A new one is empty: fit a battery, regulator, LNA, IF filter, power stage and band module")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(200);
        scene.overlay().showText(133).text("Hold R-click to send like a straight key; in paddle mode left click is dit and right click dah through the built-in keyer")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(143);
        scene.overlay().showText(126).colored(PonderPalette.BLUE).text("It listens from anywhere in your inventory. VHF is line of sight: stand on high ground and it reaches much further")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(136);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(util.select().position(4, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(148).text("The charger docks it (R-click with the radio) and charges at about 10 W from a 12 V Power Grid supply: empty to full in about 1 h 45 min")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(158);
        scene.overlay().showText(117).text("Green light: charging. Red: the supply is too low (it needs 9 V or more). Empty hand takes the radio back")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget();
        scene.idle(127);
    }

    static void instruments(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("instruments", "Measuring instruments");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 4, 3, 1), Direction.DOWN);
        scene.idle(6);
        scene.overlay().showControls(util.vector().topOf(1, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 50)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:swr_meter"))));
        scene.idle(8);
        scene.idle(12);
        scene.overlay().showText(146).text("SWR / power meter: hold it and look at a transmitter - forward and reflected power and the SWR. Above about 2:1, retune or add a tuner")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(156);
        scene.overlay().showControls(util.vector().topOf(4, 2, 1), net.createmod.catnip.math.Pointing.DOWN, 50)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:field_strength_meter"))));
        scene.idle(8);
        scene.overlay().showText(134).text("Field strength meter: hold it - all the RF arriving where you stand, every frequency. Quick check that an antenna radiates")
                .pointAt(util.vector().topOf(4, 2, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(144);
        scene.overlay().showControls(util.vector().topOf(4, 2, 1), net.createmod.catnip.math.Pointing.DOWN, 50)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:frequency_counter"))));
        scene.idle(8);
        scene.overlay().showText(121).text("Frequency counter: the frequency of the strongest signal here (above -90 dBm) - catches a drifting oscillator")
                .pointAt(util.vector().topOf(4, 2, 1)).placeNearTarget();
        scene.idle(131);
        scene.overlay().showControls(util.vector().topOf(4, 2, 1), net.createmod.catnip.math.Pointing.DOWN, 50)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:direction_finder"))));
        scene.idle(8);
        scene.overlay().showText(135).text("Direction finder: hold it and sweep round - the signal peaks in its 40 degree beam toward the station. Sneak + use tunes it")
                .pointAt(util.vector().topOf(4, 2, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(145);
        scene.overlay().showControls(util.vector().topOf(4, 2, 1), net.createmod.catnip.math.Pointing.DOWN, 50)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:spectrum_analyzer"))));
        scene.idle(8);
        scene.overlay().showText(126).text("Spectrum analyzer: use it to open a display of the band - every carrier, harmonics and the noise floor at a glance")
                .pointAt(util.vector().topOf(4, 2, 1)).placeNearTarget();
        scene.idle(136);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(143).text("Oscilloscope: two channels (CH1, CH2 +/-) on Power Grid wires, 1 MOhm inputs rated 1 kV, one sample a tick, the last 10 s on screen")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(153);
    }

    static void coaxSwitch(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("coax_switch", "Switching antennas");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 0, 3, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(100).text("A coax switch has a common port at its back and two ports: A on the left, B on the right")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 5, 4, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(114).text("Run the radio into the common port and an antenna to each side - here two antennas for different bands")
                .pointAt(util.vector().topOf(1, 3, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(124);
        scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(119).colored(PonderPalette.BLUE).text("No redstone: A is connected. A redstone signal throws it to B - so the antennas can be chosen from far away")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(129);
        scene.overlay().showText(78).text("It works the other way round too: one antenna shared by two radios")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget();
        scene.idle(88);
    }

    static void spark(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("spark", "The spark-gap transmitter");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(130).text("The way radio began (about 1900): a spark jumps a gap and rings the antenna like a struck bell. There is no oscillator")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(140);
        scene.overlay().showText(131).text("It needs about 1 kV (at least 400 V; insulated to 3 kV), up to 2 kW on the panel, and only 25 % of that leaves as radio")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(141);
        scene.world().showSection(util.select().fromTo(2, 1, 1, 4, 4, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(138).text("So the frequency is the antenna's own resonance - the dial does nothing. Lengthen the antenna or add a loading coil to move it")
                .pointAt(util.vector().topOf(4, 2, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(148);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(137).colored(PonderPalette.RED).text("The signal is smeared over 10 % of that frequency (at least 20 kHz): every receiver around hears a harsh rasp across the band")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(147);
        scene.overlay().showText(122).text("That is why spark was banned and tuned transmitters took over - fun to build, but your neighbours will hear it")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(132);
    }

    static void analogTv(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("analog_tv", "Analog television");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 1, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(152).text("A TV transmitter sends the live picture of the TV camera touching it - the camera's real view, filmed by a nearby player's game at 640 x 480")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(162);
        scene.overlay().showText(129).text("It works on 54-890 MHz (the VHF and UHF TV bands), up to 1 kW from 250 V. INHIBIT shorted to COM takes it off the air")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(139);
        scene.world().showSection(util.select().fromTo(2, 1, 1, 4, 3, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(81).text("TV is VHF / UHF: line of sight. A high antenna covers far more ground")
                .pointAt(util.vector().topOf(4, 3, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(91);
        scene.world().showSection(util.select().position(8, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(152).text("A TV set tuned to the channel shows it. Empty hand watches close up, sneak changes channel. Coax to an outside antenna beats its rabbit ears")
                .pointAt(util.vector().topOf(8, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(162);
        scene.overlay().showText(128).colored(PonderPalette.BLUE).text("A weak signal looks like real analog TV: snow, the colour fading out, lines tearing sideways and the picture rolling")
                .pointAt(util.vector().topOf(8, 1, 1)).placeNearTarget();
        scene.idle(138);
        scene.overlay().showText(97).colored(PonderPalette.RED).text("Hills in the way add ghosts; another station on the same channel shows as moving bars")
                .pointAt(util.vector().topOf(8, 1, 1)).placeNearTarget();
        scene.idle(107);
    }

    static void vcr(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vcr", "Video tapes and the VCR");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(3, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(109).text("A VCR plays and records video tapes: 10 minutes each, 320 x 240 at 15 frames a second (about VHS)")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(119);
        scene.overlay().showControls(util.vector().topOf(3, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 50)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:video_tape"))));
        scene.idle(8);
        scene.overlay().showText(117).text("Tape in hand: put it in. Empty hand: STOP, PLAY, LOOP, REC in turn. Sneak + empty hand: take the tape out")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(127);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(121).colored(PonderPalette.GREEN).text("Playing, its RF output puts the picture on channel 3 (61.25 MHz) at a microwatt: tune a TV set close by to it")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(131);
        scene.overlay().showText(106).colored(PonderPalette.RED).text("REC records the TV set touching it - the picture as that set gets it, snow and ghosts included")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget();
        scene.idle(116);
        scene.world().showSection(util.select().position(4, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(113).colored(PonderPalette.BLUE).text("A TV transmitter touching a playing VCR broadcasts the tape in place of a camera. LOOP keeps it going")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(123);
        scene.overlay().showControls(util.vector().topOf(3, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 50)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:camcorder"))));
        scene.idle(8);
        scene.overlay().showText(129).text("A camcorder records what you see onto the tape inside it. Off hand tape + sneak R-click: load. R-click: record / stop")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(139);
        scene.overlay().showText(126).text("Video files on the server's machine go onto a held tape with /pgvideo import (and out as MP4 with /pgvideo export)")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(136);
    }

    static void bodycam(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("bodycam", "Bodycam");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(123).text("A bodycam is the TV transmitter's partner: it films what its wearer sees and the transmitter puts it on the air")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(133);
        scene.overlay().showControls(util.vector().topOf(1, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 60)
                .rightClick().whileSneaking().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:bodycam"))));
        scene.idle(8);
        scene.overlay().showText(71).colored(PonderPalette.GREEN).text("Sneak + R-click the TV transmitter with it: they are paired")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(81);
        scene.overlay().showText(155).text("Then wear it in the chest armour slot (R-click puts it on). Within 300 blocks of the transmitter it sends 640 x 480 at up to 30 frames a second")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(165);
        scene.world().showSection(util.select().position(3, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(131).colored(PonderPalette.BLUE).text("With no TV camera touching the transmitter, the bodycam's picture is what goes out - watch it on any set on the channel")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(141);
    }

    static void audioPa(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("audio_pa", "Amplifier and loudspeakers");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(109).text("An audio amplifier plays the sound of the radio set touching it - a broadcast radio or a receiver")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(119);
        scene.world().showSection(util.select().position(2, 1, 4), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(2, 1, 4), 0, util.grid().at(2, 1, 2), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 4), 1, util.grid().at(2, 1, 2), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(133).text("It runs off PWR+ / PWR- at the back: 1-2000 W out (front panel), 60 % efficient, 230 V nominal - less voltage, less power")
                .pointAt(util.vector().topOf(2, 1, 4)).placeNearTarget().attachKeyFrame();
        scene.idle(143);
        scene.world().showSection(util.select().fromTo(5, 1, 1, 5, 1, 3), Direction.DOWN);
        scene.idle(6);
        elec.connect(util.grid().at(2, 1, 2), 2, util.grid().at(5, 1, 1), 0, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 3, util.grid().at(5, 1, 1), 1, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 2, util.grid().at(5, 1, 3), 0, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 3, util.grid().at(5, 1, 3), 1, DyeColor.WHITE);
        scene.idle(4);
        scene.idle(12);
        scene.overlay().showText(132).text("Wire loudspeakers to its front SPK terminals; they share the output. A speaker: 92 dB at 1 m for 1 W, up to 100 W, broad")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(142);
        scene.overlay().showText(84).colored(PonderPalette.RED).text("Driven past 1.5 times its rating for 5 s, a speaker burns its voice coil")
                .pointAt(util.vector().topOf(5, 1, 3)).placeNearTarget();
        scene.idle(94);
        scene.world().showSection(util.select().position(8, 1, 2), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(155).colored(PonderPalette.BLUE).text("A horn loudspeaker throws 112 dB per watt in a 15 degree beam from the mouth at its back - up to 600 W, heard kilometres away. Empty hand: tilt")
                .pointAt(util.vector().topOf(8, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(165);
        scene.overlay().showText(118).text("Loudness falls 6 dB per doubling of distance and 5 dB a km to the air; sound arrives late by 1 s per 343 m")
                .pointAt(util.vector().topOf(8, 1, 2)).placeNearTarget();
        scene.idle(128);
        scene.overlay().showText(146).colored(PonderPalette.RED).text("In front of a horn at full power it hurts: from 120 dB (about 10 m) you take damage, from 110 dB you feel sick. Walls and helmets help")
                .pointAt(util.vector().topOf(8, 1, 2)).placeNearTarget();
        scene.idle(156);
        scene.markAsFinished();
        elec.unload();
    }

    static void jammer(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("jammer", "Jamming");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 3, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(125).text("A jammer sends noise, not a signal: its power spread over a bandwidth you choose (10 kHz - 50 MHz) round the dial")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(135);
        scene.world().showSection(util.select().position(6, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(178).colored(PonderPalette.RED).text("Receivers in that band hear no station - their noise floor rises and weak signals drown first. TV sets snow up, SSTV fills with grain, satellites stop taking commands")
                .pointAt(util.vector().topOf(6, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(188);
        scene.overlay().showText(152).text("The narrower the band, the harder it hits: a tenth of the width is 10 dB more. Up to 1 kW from 250 V; INHIBIT shorted to COM switches it off")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget();
        scene.idle(162);
    }

    static void lna(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("lna", "In-line LNA");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 1, 4, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.world().showSection(util.select().fromTo(6, 1, 1, 6, 4, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(107).text("A long coax run loses signal - and every dB it loses on VHF/UHF is a dB of signal-to-noise gone")
                .pointAt(util.vector().topOf(2, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(117);
        scene.world().showSection(util.select().position(5, 1, 1), Direction.DOWN);
        scene.idle(6);
        scene.idle(12);
        scene.overlay().showText(159).colored(PonderPalette.GREEN).text("An in-line LNA right at the antenna lifts the signal 20 dB (noise figure 0.8 dB) before the coax can lose it: weak stations come up out of the hiss")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(169);
        scene.overlay().showText(82).text("On HF the sky is noisier than any amplifier, so it gains nothing there")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget();
        scene.idle(92);
        scene.overlay().showText(148).colored(PonderPalette.RED).text("It only amplifies towards the radio: transmit through it and it burns out at once. Give the transmitter its own antenna or a coax switch")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget();
        scene.idle(158);
    }

    static void powerSupplyScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("power_supply", "The 13.8 V power supply");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 3, 1, 1), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(138).text("A DC power supply turns anything from 90 to 300 V on the grid into the steady 13.8 V radios run on")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(148);
        scene.overlay().showText(143).text("The grid goes on IN+ / IN- at the back - Power Grid wire, or a power cord plugged straight into the box")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(153);
        scene.world().showSection(util.select().fromTo(1, 1, 4, 1, 1, 4), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 4, 5, 1, 4), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(3, 1, 1), 2, util.grid().at(1, 1, 4), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 1), 3, util.grid().at(1, 1, 4), 1, DyeColor.BLACK);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 1), 2, util.grid().at(5, 1, 4), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 1), 3, util.grid().at(5, 1, 4), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.overlay().showText(163).text("13.8 V equipment goes on OUT+ / OUT- at the front: transmitters, receivers, radios, the SSTV receiver - several in parallel")
                .pointAt(util.vector().topOf(1, 1, 4)).placeNearTarget().attachKeyFrame();
        scene.idle(173);
        scene.overlay().showText(176).colored(PonderPalette.RED).text("Rated 30 A (about 400 W), 88 % efficient. Past 33 A it folds the voltage back, so a short will not burn it - but more than 300 V in will")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(186);
        scene.overlay().showText(150).colored(PonderPalette.BLUE).text("Power Grid is all DC: 230 V boxes like the TV set, the ionosonde or the audio amplifier take the grid directly")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(160);
    }

    static void manualTunerScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("manual_tuner", "Tuning by hand");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(4, 1, 2, 6, 4, 2), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(3, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(169).text("A manual tuner goes in the coax between the rig and the antenna. Its two knobs set a roller inductor L and a variable capacitor C")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(179);
        scene.overlay().showText(148).text("It matches only around f = 1/(2π√LC): turn the knobs (scroll on them) until that is your operating frequency")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(158);
        scene.overlay().showText(164).text("The meter on the lid shows the SWR the rig sees at its own frequency: tune for the dip, left is 1:1, the red end is past 3:1")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(174);
        scene.overlay().showText(182).text("It matches up to 64:1 and loses less than an automatic tuner, but the further off the antenna is, the narrower the match - change band, retune")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget();
        scene.idle(192);
        scene.overlay().showText(152).colored(PonderPalette.BLUE).text("Automatic tuners need no hands: a desk tuner matches up to 10:1, a remote tuner right at the antenna up to 100:1")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget();
        scene.idle(162);
    }

    static void wallCoaxScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("wall_coax", "Coax up a wall");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 5, 4, 0), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 4, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(4, 1, 2, 4, 1, 2), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(149).text("Coax laid on the floor runs like redstone. Click the side of a block with it and it clips to the wall instead")
                .pointAt(util.vector().topOf(4, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(159);
        scene.world().showSection(util.select().fromTo(4, 1, 1, 4, 4, 1), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(145).text("Wall coax runs straight up and down the wall, and sideways along it; the floor run joins it at the bottom")
                .pointAt(util.vector().topOf(4, 3, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(155);
        scene.world().showSection(util.select().fromTo(4, 5, 0, 5, 8, 0), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(147).text("At the top it goes over onto the roof and on to the antenna - one block of cable is still one metre of loss")
                .pointAt(util.vector().topOf(4, 5, 0)).placeNearTarget().attachKeyFrame();
        scene.idle(157);
    }

    static void antennaStackingScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("antenna_stacking", "Stacking beam antennas");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 3, 4, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(70).text("One Yagi fed through coax")
                .pointAt(util.vector().topOf(4, 1, 3)).placeNearTarget();
        scene.idle(80);
        scene.world().showSection(util.select().fromTo(4, 1, 1, 4, 1, 1), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(4, 1, 5, 4, 1, 5), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(199).text("More of the same kind on the same band, aimed the same way (within about 10°) and within 2 blocks of each other, work as one stack - feeding one feeds them all")
                .pointAt(util.vector().topOf(4, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(209);
        scene.overlay().showText(199).text("Each doubling adds about 3 dB of gain (10·log N, less 0.4 dB of phasing loss) and the beam narrows by √N - up to 16 antennas, side by side or one above another")
                .pointAt(util.vector().topOf(4, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(209);
        scene.overlay().showText(126).colored(PonderPalette.BLUE).text("They want to be about 0.6 wavelengths apart: packed closer, they overlap and give less")
                .pointAt(util.vector().topOf(4, 1, 5)).placeNearTarget();
        scene.idle(136);
        scene.overlay().showText(80).text("Yagis, dishes and helices stack this way")
                .pointAt(util.vector().topOf(4, 1, 3)).placeNearTarget();
        scene.idle(90);
    }

    static void manpackScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("manpack", "The manpack radio");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(2, 0, 2), net.createmod.catnip.math.Pointing.DOWN, 60)
                .rightClick().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:manpack_radio"))));
        scene.idle(10);
        scene.overlay().showText(134).text("R-click puts the manpack on your back (chest armour slot) - other players see it, whip and all")
                .pointAt(util.vector().topOf(2, 0, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(144);
        scene.overlay().showControls(util.vector().topOf(2, 0, 2), net.createmod.catnip.math.Pointing.DOWN, 60)
                .rightClick().whileSneaking().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:manpack_radio"))));
        scene.idle(10);
        scene.overlay().showText(162).text("Sneak R-click opens its panel: power, frequency, output 1 / 5 / 10 / 20 W, squelch - and the parts, the same as a handheld")
                .pointAt(util.vector().topOf(2, 0, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(172);
        scene.overlay().showText(190).text("Hold V (push to talk) and speak: it goes out as NFM voice, and whoever is on the frequency comes back through the earpiece, noise and squelch included")
                .pointAt(util.vector().topOf(2, 0, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(200);
        scene.overlay().showText(143).colored(PonderPalette.BLUE).text("The 1 m whip above your shoulder and the bigger power stage reach several times further than a handheld")
                .pointAt(util.vector().topOf(2, 0, 2)).placeNearTarget();
        scene.idle(153);
    }

    static void fpvGogglesScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("fpv_goggles", "FPV goggles");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(0, 1, 2, 4, 1, 2), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(104).text("A TV camera into a TV transmitter puts a live picture on the air")
                .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(114);
        scene.overlay().showControls(util.vector().topOf(2, 1, 2), net.createmod.catnip.math.Pointing.DOWN, 60)
                .rightClick().whileSneaking().withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:fpv_goggles"))));
        scene.idle(10);
        scene.overlay().showText(183).text("FPV goggles are a TV receiver worn on the head (helmet slot). Sneak R-click sets the channel, 50 MHz - 6 GHz - the transmitter's or a bodycam's")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(193);
        scene.overlay().showText(177).text("Put them on (R-click) and the picture fills your view, with snow, colour loss and tearing as the signal fades. Take them off to see again")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget();
        scene.idle(187);
    }

    static void paMicrophoneScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("pa_microphone", "Public address");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 1, 5, 1, 3), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(2, 1, 2), 2, util.grid().at(5, 1, 1), 0, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 3, util.grid().at(5, 1, 1), 1, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 2, util.grid().at(5, 1, 3), 0, DyeColor.WHITE);
        scene.idle(4);
        elec.connect(util.grid().at(2, 1, 2), 3, util.grid().at(5, 1, 3), 1, DyeColor.WHITE);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 1, 1, 2), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(165).text("A microphone touching an audio amplifier makes a public address: hold V and your voice comes out of every speaker on the line")
                .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(175);
        scene.world().showSection(util.select().fromTo(3, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(131).text("A radio touching the amplifier plays through the speakers only - its own speaker goes quiet")
                .pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(141);
        scene.overlay().showText(123).text("Talk over the music and it ducks under your voice, then comes back up when you stop")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget();
        scene.idle(133);
        scene.overlay().showText(136).colored(PonderPalette.BLUE).text("All the speakers on one amplifier play in step: only the distance to each one makes a difference")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget();
        scene.idle(146);
    }

    static void upsPowerPanelScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("ups_power_panel", "Battery backup and fused distribution");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 3, 1, 1), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(156).text("The UPS is the 13.8 V power supply with 500 Wh of batteries inside. With the grid up it runs the station and charges")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(166);
        scene.overlay().showText(181).colored(PonderPalette.BLUE).text("When IN drops below 90 V the batteries take over without a break, and it beeps every 5 seconds. The goggles show the charge and the time left")
                .pointAt(util.vector().topOf(3, 1, 1)).placeNearTarget();
        scene.idle(191);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 1, 3), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(3, 1, 1), 2, util.grid().at(3, 1, 3), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 1), 3, util.grid().at(3, 1, 3), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.overlay().showText(131).text("A distribution panel splits one 13.8 V feed into four outputs, each with its own blade fuse")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(141);
        scene.world().showSection(util.select().fromTo(1, 1, 5, 5, 1, 5), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(3, 1, 3), 2, util.grid().at(1, 1, 5), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 3), 3, util.grid().at(3, 1, 5), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 3), 4, util.grid().at(5, 1, 5), 0, DyeColor.RED);
        scene.idle(4);
        scene.overlay().showText(169).text("Each set takes an OUT on its PWR+, and every PWR- goes back to GND. A short on one output blows only that fuse - the rest stay on")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(179);
        scene.overlay().showControls(util.vector().topOf(3, 1, 3), net.createmod.catnip.math.Pointing.DOWN, 50).rightClick();
        scene.idle(8);
        scene.overlay().showText(157).colored(PonderPalette.RED).text("Empty hand: new fuses for the blown ones. Sneak: the fuse size, 5 / 10 / 20 / 30 A. A blown fuse makes the lamp blink")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget();
        scene.idle(167);
    }

    static void equipmentRackScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("equipment_rack", "Equipment racks and grounding");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 3, 3), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(124).text("Equipment racks stack into one steel frame. Set the station's boxes in and around it")
                .pointAt(util.vector().topOf(3, 2, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(134);
        scene.world().showSection(util.select().fromTo(4, 1, 3, 4, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(169).text("Racks that touch are one piece of metal: ground any one of them - a grounding rod or a ground mat - and the whole group is ground")
                .pointAt(util.vector().topOf(4, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(179);
        scene.world().showSection(util.select().fromTo(2, 3, 3, 2, 3, 3), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(152).colored(PonderPalette.BLUE).text("A lightning arrester touching any rack of a grounded group counts as grounded - no separate ground wire for each")
                .pointAt(util.vector().topOf(2, 3, 3)).placeNearTarget();
        scene.idle(162);
    }

    static void dtmfDecoderScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("dtmf_decoder", "Remote control by touch-tone");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 1, 6), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(148).text("A DTMF decoder has its own FM receiver: an antenna on its coax, the frequency set in its screen (empty hand)")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(158);
        scene.overlay().showText(154).text("Press keys on a handheld or manpack panel: each press keys the radio for a short touch-tone burst on its frequency")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(164);
        scene.world().showSection(util.select().fromTo(2, 1, 3, 4, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(169).text("The right code gives a redstone signal on every side: a 1 s pulse, a latch with ON / OFF codes, or the last digit as its strength")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(179);
        scene.overlay().showText(120).colored(PonderPalette.BLUE).text("It runs on 13.8 V. A receiver placed right against it is used instead of its own")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget();
        scene.idle(130);
    }

    static void aprsScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("aprs", "APRS position reports");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 1, 4), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(143).text("An APRS station listens on 144.390 MHz and plots every position report it decodes on a map (empty hand)")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(153);
        scene.overlay().showControls(util.vector().topOf(2, 1, 2), net.createmod.catnip.math.Pointing.DOWN, 50).rightClick()
                .withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:redstone"))));
        scene.idle(8);
        scene.overlay().showText(164).text("The two slots on top are its network key, set like a redstone link's frequency: only reports with the same two items show up")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(174);
        scene.overlay().showControls(util.vector().topOf(2, 1, 2), net.createmod.catnip.math.Pointing.DOWN, 50).rightClick()
                .withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:aprs_tracker"))));
        scene.idle(8);
        scene.overlay().showText(177).text("Right-click the station with an APRS tracker to copy the key. Switched on, the tracker sends your position every 30 s from your inventory")
                .pointAt(util.vector().topOf(2, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(187);
        scene.world().showSection(util.select().fromTo(5, 1, 4, 5, 1, 4), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(162).colored(PonderPalette.BLUE).text("A vehicle radio on a train, cart or airship reports the vehicle's position, and riders' radios transmit through it at 50 W")
                .pointAt(util.vector().topOf(5, 1, 4)).placeNearTarget();
        scene.idle(172);
    }

    static void fieldPhoneScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("field_phone", "Field telephones");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 1, 1), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 1, 5, 1, 1), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(1, 1, 1), 0, util.grid().at(5, 1, 1), 0, DyeColor.RED);
        scene.idle(4);
        elec.connect(util.grid().at(1, 1, 1), 1, util.grid().at(5, 1, 1), 1, DyeColor.BLACK);
        scene.idle(4);
        scene.overlay().showText(134).text("Field telephones talk over a pair of wires (or one wire with L2 to earth). No supply is needed")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(144);
        scene.overlay().showControls(util.vector().topOf(1, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 50).rightClick().whileSneaking();
        scene.idle(8);
        scene.overlay().showText(136).text("Sneak + empty hand turns the magneto crank: about 75 V on the line rings the bell at the far end")
                .pointAt(util.vector().topOf(1, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(146);
        scene.overlay().showControls(util.vector().topOf(5, 1, 1), net.createmod.catnip.math.Pointing.DOWN, 50).rightClick();
        scene.idle(8);
        scene.overlay().showText(158).text("Empty hand lifts the handset. Hold push-to-talk (V) and speak: your voice reaches every phone off the hook on the line")
                .pointAt(util.vector().topOf(5, 1, 1)).placeNearTarget().attachKeyFrame();
        scene.idle(168);
        scene.world().showSection(util.select().fromTo(3, 1, 4, 3, 1, 4), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(1, 1, 5, 1, 1, 5), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(1, 1, 5), 0, util.grid().at(3, 1, 4), 0, DyeColor.YELLOW);
        scene.idle(4);
        scene.overlay().showText(154).text("Through a switchboard the voice follows only the cords plugged in - cords are insulated copper wire or patch cords")
                .pointAt(util.vector().topOf(3, 1, 4)).placeNearTarget().attachKeyFrame();
        scene.idle(164);
        scene.overlay().showText(144).colored(PonderPalette.BLUE).text("A patch cord goes on like insulated wire, but comes off by hand: right-click the terminal its plug is in")
                .pointAt(util.vector().topOf(1, 1, 5)).placeNearTarget();
        scene.idle(154);
    }

    static void duplexSetScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("duplex_set", "Duplex telegraphy");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 1, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 5, 1, 3), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(1, 1, 3), 0, util.grid().at(5, 1, 3), 0, DyeColor.YELLOW);
        scene.idle(4);
        scene.overlay().showText(121).text("A duplex set lets both ends send at the same time over one wire with earth return")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(131);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 1, 1), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 1, 5, 1, 1), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(1, 1, 5, 1, 1, 5), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 5, 5, 1, 5), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(149).text("LINE to the line, EARTH to a grounding rod, the key on KEY / KEY COM, a sounder with its local battery on OUT")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(159);
        scene.overlay().showText(165).text("Inside is a Wheatstone bridge: one arm to the line, the other to an artificial line (the value box). The relay sits across it")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(175);
        scene.overlay().showText(171).colored(PonderPalette.BLUE).text("Turn the artificial line until your own key no longer moves your relay - the goggles say \"balanced\". Then only the far end works it")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget();
        scene.idle(181);
    }

    static void lineTestingScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("line_testing", "Finding line faults");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 1, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 1, 1), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(149).text("A line test set: LINE to the line under test (a patch cord makes a handy test lead), EARTH to a grounding rod")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(159);
        scene.overlay().showControls(util.vector().topOf(1, 1, 3), net.createmod.catnip.math.Pointing.DOWN, 50).rightClick();
        scene.idle(8);
        scene.overlay().showText(166).text("Press it: for 2 s it puts 10 V on and measures line-to-earth resistance. Open lines read very high, leaks and earth faults low")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(176);
        scene.overlay().showText(169).text("It also walks the line: metres of wire to every device, every break and every wet, leaking insulator - in chat and in the goggles")
                .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(179);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 5, 1, 3), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(1, 1, 3), 0, util.grid().at(3, 1, 3), 0, DyeColor.YELLOW);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 3), 1, util.grid().at(5, 1, 3), 0, DyeColor.YELLOW);
        scene.idle(4);
        scene.overlay().showText(156).text("For testing without kilometres of poles: the creative line simulator acts as a line of the set length, 1 m to 250 km")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(166);
        scene.overlay().showText(159).colored(PonderPalette.BLUE).text("Its wire resistance and insulator leakage (much worse in rain) are real, and the tester and telephones count its length")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget();
        scene.idle(169);
    }

    static void submarineCableScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("submarine_cable", "Submarine cables");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(131).text("A submarine cable block holds a whole cable, 10 to 4000 km long, with the sea as its return")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(141);
        scene.overlay().showText(157).text("Every km has 3 Ω and 0.25 μF to the sea: a pulse comes out the far end rounded, late and weak - the longer, the worse")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(167);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 1, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 5, 1, 5), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(1, 1, 3), 1, util.grid().at(3, 1, 3), 0, DyeColor.YELLOW);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 3), 1, util.grid().at(5, 1, 3), 0, DyeColor.YELLOW);
        scene.idle(4);
        scene.overlay().showText(136).text("Read it with a mirror galvanometer: a spot of light swings across its scale with a few microamps")
                .pointAt(util.vector().topOf(5, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(146);
        scene.overlay().showText(180).colored(PonderPalette.BLUE).text("Past its trip level its contact closes and can work a local sounder. Send slower than the cable's RC time (goggles) or the dots run together")
                .pointAt(util.vector().topOf(5, 1, 3)).placeNearTarget();
        scene.idle(190);
    }

    static void telegraphRepeaterScene(SceneBuilder scene, SceneBuildingUtil util) {
        ElectricInstructions elec = ElectricInstructions.of(scene);
        scene.title("telegraph_repeater", "Telegraph repeater stations");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 1, 5), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(166).text("On a very long line the current is too weak to work a sounder. A repeater in the middle sends it on with a fresh 120 V battery")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(176);
        scene.world().showSection(util.select().fromTo(0, 1, 3, 0, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(6, 1, 3, 6, 1, 3), Direction.DOWN);
        scene.idle(8);
        elec.connect(util.grid().at(0, 1, 3), 0, util.grid().at(3, 1, 3), 0, DyeColor.YELLOW);
        scene.idle(4);
        elec.connect(util.grid().at(3, 1, 3), 1, util.grid().at(6, 1, 3), 0, DyeColor.YELLOW);
        scene.idle(4);
        scene.overlay().showText(154).text("Current from the WEST line pulls the west relay, which puts the battery on the EAST line - and the other way round")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(164);
        scene.overlay().showText(170).colored(PonderPalette.BLUE).text("While one side is repeating, the other side's relay is locked out so it cannot latch on its own output. Pick-up current: value box")
                .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget();
        scene.idle(180);
    }

    static void creativeRadioScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creative_radio", "Creative radios and the VNA");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 3, 2, 1, 5), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(4, 1, 3, 4, 1, 5), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(120).text("Creative sets need no supply and no parts and never burn - for testing a station")
                .pointAt(util.vector().topOf(2, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(130);
        scene.overlay().showText(150).text("The transmitter's power box has a row for each multiplier x1 / x10 / x100 / x1000, 1-250 in each: up to 250 kW")
                .pointAt(util.vector().topOf(2, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(160);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 1, 1, 3), Direction.DOWN);
        scene.idle(8);
        scene.overlay().showText(124).text("Besides its KEY terminals, a microphone or a computer, a redstone signal keys it too")
                .pointAt(util.vector().topOf(2, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(134);
        scene.overlay().showControls(util.vector().topOf(4, 1, 4), net.createmod.catnip.math.Pointing.DOWN, 50).rightClick()
                .withItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("pgtelegraph:nanovna"))));
        scene.idle(8);
        scene.overlay().showText(162).colored(PonderPalette.BLUE).text("A VNA on a radio, tuner or coax sweeps the antenna behind it: SWR, return loss, a Smith chart and R + jX at the marker")
                .pointAt(util.vector().topOf(4, 1, 4)).placeNearTarget();
        scene.idle(172);
        scene.overlay().showText(170).colored(PonderPalette.BLUE).text("A creative antenna is matched on every frequency with no loss of its own; its value box sets how high it counts as, 1 to 2500 m")
                .pointAt(util.vector().topOf(2, 1, 5)).placeNearTarget();
        scene.idle(180);
    }
}
