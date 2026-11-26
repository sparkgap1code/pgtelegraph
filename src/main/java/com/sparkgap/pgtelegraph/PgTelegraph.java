package com.sparkgap.pgtelegraph;

import com.sparkgap.pgtelegraph.block.BuzzerBlock;
import com.sparkgap.pgtelegraph.block.BuzzerBlockEntity;
import com.sparkgap.pgtelegraph.block.RelayBlock;
import com.sparkgap.pgtelegraph.block.RelayBlockEntity;
import com.sparkgap.pgtelegraph.block.SounderBlock;
import com.sparkgap.pgtelegraph.block.SounderBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import com.sparkgap.pgtelegraph.block.KeyerBlock;
import com.sparkgap.pgtelegraph.block.KeyerBlockEntity;
import com.sparkgap.pgtelegraph.block.PaddleBlock;
import com.sparkgap.pgtelegraph.block.PaddleBlockEntity;
import com.sparkgap.pgtelegraph.block.TelegraphKeyBlock;
import com.sparkgap.pgtelegraph.block.TelegraphKeyBlockEntity;
import com.sparkgap.pgtelegraph.network.KeyPressPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(PgTelegraph.MODID)
public class PgTelegraph {
    public static final String MODID = "pgtelegraph";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // --- Straight key (전건) ---
    public static final DeferredBlock<TelegraphKeyBlock> TELEGRAPH_KEY = BLOCKS.register("telegraph_key",
            () -> new TelegraphKeyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> TELEGRAPH_KEY_ITEM =
            ITEMS.registerSimpleBlockItem("telegraph_key", TELEGRAPH_KEY, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TelegraphKeyBlockEntity>> TELEGRAPH_KEY_BE =
            BLOCK_ENTITIES.register("telegraph_key", () -> BlockEntityType.Builder
                    .of((pos, state) -> new TelegraphKeyBlockEntity(PgTelegraph.TELEGRAPH_KEY_BE.get(), pos, state),
                            TELEGRAPH_KEY.get())
                    .build(null));

    // --- Iambic paddle (패들 키) ---
    public static final DeferredBlock<PaddleBlock> PADDLE = BLOCKS.register("paddle",
            () -> new PaddleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> PADDLE_ITEM =
            ITEMS.registerSimpleBlockItem("paddle", PADDLE, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PaddleBlockEntity>> PADDLE_BE =
            BLOCK_ENTITIES.register("paddle", () -> BlockEntityType.Builder
                    .of((pos, state) -> new PaddleBlockEntity(PgTelegraph.PADDLE_BE.get(), pos, state), PADDLE.get())
                    .build(null));

    // --- Electronic keyer (전자 키어) ---
    public static final DeferredBlock<KeyerBlock> KEYER = BLOCKS.register("keyer",
            () -> new KeyerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> KEYER_ITEM =
            ITEMS.registerSimpleBlockItem("keyer", KEYER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KeyerBlockEntity>> KEYER_BE =
            BLOCK_ENTITIES.register("keyer", () -> BlockEntityType.Builder
                    .of((pos, state) -> new KeyerBlockEntity(PgTelegraph.KEYER_BE.get(), pos, state), KEYER.get())
                    .build(null));

    // --- Keyer paddle (전자식 패들: 키어 내장) ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.KeyerPaddleBlock> KEYER_PADDLE = BLOCKS.register("keyer_paddle",
            () -> new com.sparkgap.pgtelegraph.block.KeyerPaddleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> KEYER_PADDLE_ITEM =
            ITEMS.registerSimpleBlockItem("keyer_paddle", KEYER_PADDLE, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.KeyerPaddleBlockEntity>> KEYER_PADDLE_BE =
            BLOCK_ENTITIES.register("keyer_paddle", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.KeyerPaddleBlockEntity(PgTelegraph.KEYER_PADDLE_BE.get(), pos, state), KEYER_PADDLE.get())
                    .build(null));

    // --- Sounds ---
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUNDER_DOWN = sound("sounder_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUNDER_UP = sound("sounder_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_CLICK = sound("relay_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUZZER_TONE = sound("buzzer_tone");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    private static BlockBehaviour.Properties device(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.5f).sound(sound).noOcclusion();
    }

    // --- Sounder (음향기) ---
    public static final DeferredBlock<SounderBlock> SOUNDER = BLOCKS.register("sounder",
            () -> new SounderBlock(device(MapColor.WOOD, SoundType.WOOD)));
    public static final DeferredItem<BlockItem> SOUNDER_ITEM =
            ITEMS.registerSimpleBlockItem("sounder", SOUNDER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SounderBlockEntity>> SOUNDER_BE =
            BLOCK_ENTITIES.register("sounder", () -> BlockEntityType.Builder
                    .of((pos, state) -> new SounderBlockEntity(PgTelegraph.SOUNDER_BE.get(), pos, state), SOUNDER.get())
                    .build(null));

    // --- Line relay (전신 계전기) ---
    public static final DeferredBlock<RelayBlock> RELAY = BLOCKS.register("relay",
            () -> new RelayBlock(device(MapColor.WOOD, SoundType.WOOD)));
    public static final DeferredItem<BlockItem> RELAY_ITEM =
            ITEMS.registerSimpleBlockItem("relay", RELAY, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RelayBlockEntity>> RELAY_BE =
            BLOCK_ENTITIES.register("relay", () -> BlockEntityType.Builder
                    .of((pos, state) -> new RelayBlockEntity(PgTelegraph.RELAY_BE.get(), pos, state), RELAY.get())
                    .build(null));

    // --- Buzzer (부저) ---
    public static final DeferredBlock<BuzzerBlock> BUZZER = BLOCKS.register("buzzer",
            () -> new BuzzerBlock(device(MapColor.COLOR_BLACK, SoundType.METAL)));
    public static final DeferredItem<BlockItem> BUZZER_ITEM =
            ITEMS.registerSimpleBlockItem("buzzer", BUZZER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BuzzerBlockEntity>> BUZZER_BE =
            BLOCK_ENTITIES.register("buzzer", () -> BlockEntityType.Builder
                    .of((pos, state) -> new BuzzerBlockEntity(PgTelegraph.BUZZER_BE.get(), pos, state), BUZZER.get())
                    .build(null));

    // --- Paper tape (종이 테이프) ---
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<com.sparkgap.pgtelegraph.tape.TapeData>> TAPE =
            DATA_COMPONENTS.registerComponentType("tape", b -> b
                    .persistent(com.sparkgap.pgtelegraph.tape.TapeData.CODEC)
                    .networkSynchronized(com.sparkgap.pgtelegraph.tape.TapeData.STREAM_CODEC));
    public static final DeferredItem<com.sparkgap.pgtelegraph.tape.PaperTapeItem> PAPER_TAPE =
            ITEMS.register("paper_tape", () -> new com.sparkgap.pgtelegraph.tape.PaperTapeItem(new Item.Properties()));

    // --- Morse inker (잉커) ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.InkerBlock> INKER = BLOCKS.register("inker",
            () -> new com.sparkgap.pgtelegraph.block.InkerBlock(device(MapColor.WOOD, SoundType.WOOD)));
    public static final DeferredItem<BlockItem> INKER_ITEM =
            ITEMS.registerSimpleBlockItem("inker", INKER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.InkerBlockEntity>> INKER_BE =
            BLOCK_ENTITIES.register("inker", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.InkerBlockEntity(PgTelegraph.INKER_BE.get(), pos, state), INKER.get())
                    .build(null));

    // --- Tape perforator (천공기) ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PerforatorBlock> PERFORATOR = BLOCKS.register("perforator",
            () -> new com.sparkgap.pgtelegraph.block.PerforatorBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredItem<BlockItem> PERFORATOR_ITEM =
            ITEMS.registerSimpleBlockItem("perforator", PERFORATOR, new Item.Properties());

    // --- Automatic tape transmitter (자동 송신기) ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.TapeTransmitterBlock> TAPE_TRANSMITTER = BLOCKS.register("tape_transmitter",
            () -> new com.sparkgap.pgtelegraph.block.TapeTransmitterBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredItem<BlockItem> TAPE_TRANSMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("tape_transmitter", TAPE_TRANSMITTER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.TapeTransmitterBlockEntity>> TAPE_TRANSMITTER_BE =
            BLOCK_ENTITIES.register("tape_transmitter", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.TapeTransmitterBlockEntity(PgTelegraph.TAPE_TRANSMITTER_BE.get(), pos, state), TAPE_TRANSMITTER.get())
                    .build(null));

    // --- Switchboard (교환대) ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.SwitchboardBlock> SWITCHBOARD = BLOCKS.register("switchboard",
            () -> new com.sparkgap.pgtelegraph.block.SwitchboardBlock(device(MapColor.WOOD, SoundType.WOOD)));
    public static final DeferredItem<BlockItem> SWITCHBOARD_ITEM =
            ITEMS.registerSimpleBlockItem("switchboard", SWITCHBOARD, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.SwitchboardBlockEntity>> SWITCHBOARD_BE =
            BLOCK_ENTITIES.register("switchboard", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.SwitchboardBlockEntity(PgTelegraph.SWITCHBOARD_BE.get(), pos, state), SWITCHBOARD.get())
                    .build(null));

    // --- Radio (무선) ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.TransmitterBlock> TRANSMITTER = BLOCKS.register("transmitter",
            () -> new com.sparkgap.pgtelegraph.block.TransmitterBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredItem<BlockItem> TRANSMITTER_ITEM =
            ITEMS.registerSimpleBlockItem("transmitter", TRANSMITTER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.TransmitterBlockEntity>> TRANSMITTER_BE =
            BLOCK_ENTITIES.register("transmitter", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.TransmitterBlockEntity(PgTelegraph.TRANSMITTER_BE.get(), pos, state), TRANSMITTER.get())
                    .build(null));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.ReceiverBlock> RECEIVER = BLOCKS.register("receiver",
            () -> new com.sparkgap.pgtelegraph.block.ReceiverBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredItem<BlockItem> RECEIVER_ITEM =
            ITEMS.registerSimpleBlockItem("receiver", RECEIVER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.ReceiverBlockEntity>> RECEIVER_BE =
            BLOCK_ENTITIES.register("receiver", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.ReceiverBlockEntity(PgTelegraph.RECEIVER_BE.get(), pos, state), RECEIVER.get())
                    .build(null));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.ShortAntennaBlock> SHORT_ANTENNA = BLOCKS.register("short_antenna",
            () -> new com.sparkgap.pgtelegraph.block.ShortAntennaBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredItem<BlockItem> SHORT_ANTENNA_ITEM =
            ITEMS.registerSimpleBlockItem("short_antenna", SHORT_ANTENNA, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.ShortAntennaBlockEntity>> SHORT_ANTENNA_BE =
            BLOCK_ENTITIES.register("short_antenna", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.ShortAntennaBlockEntity(PgTelegraph.SHORT_ANTENNA_BE.get(), pos, state), SHORT_ANTENNA.get())
                    .build(null));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.AntennaRodBlock> ANTENNA_ROD = BLOCKS.register("antenna_rod",
            () -> new com.sparkgap.pgtelegraph.block.AntennaRodBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f)
                    .sound(SoundType.CHAIN).noOcclusion()));
    public static final DeferredItem<BlockItem> ANTENNA_ROD_ITEM =
            ITEMS.registerSimpleBlockItem("antenna_rod", ANTENNA_ROD, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.CoaxBlock> COAX_THIN = BLOCKS.register("coax_thin",
            () -> new com.sparkgap.pgtelegraph.block.CoaxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3f)
                    .sound(SoundType.WOOL).noOcclusion().noCollission(), com.sparkgap.pgtelegraph.block.CoaxBlock.Grade.THIN));
    public static final DeferredItem<BlockItem> COAX_THIN_ITEM =
            ITEMS.registerSimpleBlockItem("coax_thin", COAX_THIN, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.CoaxBlock> COAX_THICK = BLOCKS.register("coax_thick",
            () -> new com.sparkgap.pgtelegraph.block.CoaxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3f)
                    .sound(SoundType.WOOL).noOcclusion().noCollission(), com.sparkgap.pgtelegraph.block.CoaxBlock.Grade.THICK));
    public static final DeferredItem<BlockItem> COAX_THICK_ITEM =
            ITEMS.registerSimpleBlockItem("coax_thick", COAX_THICK, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.CoaxBlock> COAX_HARDLINE = BLOCKS.register("coax_hardline",
            () -> new com.sparkgap.pgtelegraph.block.CoaxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3f)
                    .sound(SoundType.WOOL).noOcclusion().noCollission(), com.sparkgap.pgtelegraph.block.CoaxBlock.Grade.HARDLINE));
    public static final DeferredItem<BlockItem> COAX_HARDLINE_ITEM =
            ITEMS.registerSimpleBlockItem("coax_hardline", COAX_HARDLINE, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.CoaxBlock> COAX_HEAVY = BLOCKS.register("coax_heavy",
            () -> new com.sparkgap.pgtelegraph.block.CoaxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3f)
                    .sound(SoundType.WOOL).noOcclusion().noCollission(), com.sparkgap.pgtelegraph.block.CoaxBlock.Grade.HEAVY));
    public static final DeferredItem<BlockItem> COAX_HEAVY_ITEM =
            ITEMS.registerSimpleBlockItem("coax_heavy", COAX_HEAVY, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.CoaxBlock> COAX_RIGID = BLOCKS.register("coax_rigid",
            () -> new com.sparkgap.pgtelegraph.block.CoaxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.8f)
                    .sound(SoundType.COPPER).noOcclusion().noCollission(), com.sparkgap.pgtelegraph.block.CoaxBlock.Grade.RIGID));
    public static final DeferredItem<BlockItem> COAX_RIGID_ITEM =
            ITEMS.registerSimpleBlockItem("coax_rigid", COAX_RIGID, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.LinearAmplifierBlock> LINEAR_AMPLIFIER = BLOCKS.register("linear_amplifier",
            () -> new com.sparkgap.pgtelegraph.block.LinearAmplifierBlock(device(MapColor.METAL, SoundType.METAL)
                    .lightLevel(s -> s.getValue(com.sparkgap.pgtelegraph.block.LinearAmplifierBlock.LIT) ? 7 : 0)));
    public static final DeferredItem<BlockItem> LINEAR_AMPLIFIER_ITEM =
            ITEMS.registerSimpleBlockItem("linear_amplifier", LINEAR_AMPLIFIER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity>> LINEAR_AMPLIFIER_BE =
            BLOCK_ENTITIES.register("linear_amplifier", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity(PgTelegraph.LINEAR_AMPLIFIER_BE.get(), pos, state), LINEAR_AMPLIFIER.get())
                    .build(null));
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_NOISE = sound("radio_noise");

    // --- VLF station (초장파 송신국) ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.LoadingCoilBlock> LOADING_COIL = BLOCKS.register("loading_coil",
            () -> new com.sparkgap.pgtelegraph.block.LoadingCoilBlock(device(MapColor.WOOD, SoundType.WOOD)));
    public static final DeferredItem<BlockItem> LOADING_COIL_ITEM =
            ITEMS.registerSimpleBlockItem("loading_coil", LOADING_COIL, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.LoadingCoilBlock> LITZ_LOADING_COIL = BLOCKS.register("litz_loading_coil",
            () -> new com.sparkgap.pgtelegraph.block.LoadingCoilBlock(device(MapColor.WOOD, SoundType.WOOD), 3000));
    public static final DeferredItem<BlockItem> LITZ_LOADING_COIL_ITEM =
            ITEMS.registerSimpleBlockItem("litz_loading_coil", LITZ_LOADING_COIL, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.LoadingCoilBlockEntity>> LOADING_COIL_BE =
            BLOCK_ENTITIES.register("loading_coil", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.LoadingCoilBlockEntity(PgTelegraph.LOADING_COIL_BE.get(), pos, state), LOADING_COIL.get(), LITZ_LOADING_COIL.get())
                    .build(null));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.TopLoadWireBlock> TOP_LOAD_WIRE = BLOCKS.register("top_load_wire",
            () -> new com.sparkgap.pgtelegraph.block.TopLoadWireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5f)
                    .sound(SoundType.CHAIN).noOcclusion().noCollission()));
    public static final DeferredItem<BlockItem> TOP_LOAD_WIRE_ITEM =
            ITEMS.registerSimpleBlockItem("top_load_wire", TOP_LOAD_WIRE, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.GroundMatBlock> GROUND_MAT = BLOCKS.register("ground_mat",
            () -> new com.sparkgap.pgtelegraph.block.GroundMatBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.0f)
                    .sound(SoundType.COPPER)));
    public static final DeferredItem<BlockItem> GROUND_MAT_ITEM =
            ITEMS.registerSimpleBlockItem("ground_mat", GROUND_MAT, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.LinearAmplifierBlock> BROADCAST_AMPLIFIER = BLOCKS.register("broadcast_amplifier",
            () -> new com.sparkgap.pgtelegraph.block.LinearAmplifierBlock(device(MapColor.METAL, SoundType.METAL)
                    .lightLevel(s -> s.getValue(com.sparkgap.pgtelegraph.block.LinearAmplifierBlock.LIT) ? 10 : 0),
                    com.sparkgap.pgtelegraph.block.AmpTier.BROADCAST, PgTelegraph.BROADCAST_AMPLIFIER_BE));
    public static final DeferredItem<BlockItem> BROADCAST_AMPLIFIER_ITEM =
            ITEMS.registerSimpleBlockItem("broadcast_amplifier", BROADCAST_AMPLIFIER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity>> BROADCAST_AMPLIFIER_BE =
            BLOCK_ENTITIES.register("broadcast_amplifier", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity(PgTelegraph.BROADCAST_AMPLIFIER_BE.get(), pos, state), BROADCAST_AMPLIFIER.get())
                    .build(null));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.AlternatorBlock> ALTERNATOR = BLOCKS.register("alexanderson_alternator",
            () -> new com.sparkgap.pgtelegraph.block.AlternatorBlock(device(MapColor.METAL, SoundType.NETHERITE_BLOCK)));
    public static final DeferredItem<BlockItem> ALTERNATOR_ITEM =
            ITEMS.registerSimpleBlockItem("alexanderson_alternator", ALTERNATOR, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.AlternatorBlockEntity>> ALTERNATOR_BE =
            BLOCK_ENTITIES.register("alexanderson_alternator", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.AlternatorBlockEntity(PgTelegraph.ALTERNATOR_BE.get(), pos, state), ALTERNATOR.get())
                    .build(null));

    // --- VHF / UHF / SHF antennas ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.VhfAntennaBlock> VHF_WHIP = BLOCKS.register("vhf_whip",
            () -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlock(device(MapColor.METAL, SoundType.METAL), com.sparkgap.pgtelegraph.block.VhfAntennaBlock.Type.WHIP));
    public static final DeferredItem<BlockItem> VHF_WHIP_ITEM =
            ITEMS.registerSimpleBlockItem("vhf_whip", VHF_WHIP, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.VhfAntennaBlock> YAGI_ANTENNA = BLOCKS.register("yagi_antenna",
            () -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlock(device(MapColor.METAL, SoundType.METAL), com.sparkgap.pgtelegraph.block.VhfAntennaBlock.Type.YAGI));
    public static final DeferredItem<BlockItem> YAGI_ANTENNA_ITEM =
            ITEMS.registerSimpleBlockItem("yagi_antenna", YAGI_ANTENNA, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.VhfAntennaBlock> DISH_ANTENNA = BLOCKS.register("dish_antenna",
            () -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlock(device(MapColor.METAL, SoundType.METAL), com.sparkgap.pgtelegraph.block.VhfAntennaBlock.Type.DISH));
    public static final DeferredItem<BlockItem> DISH_ANTENNA_ITEM =
            ITEMS.registerSimpleBlockItem("dish_antenna", DISH_ANTENNA, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.VhfAntennaBlock> HELICAL_WHIP = BLOCKS.register("helical_whip",
            () -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlock(device(MapColor.METAL, SoundType.METAL), com.sparkgap.pgtelegraph.block.VhfAntennaBlock.Type.HELICAL_WHIP));
    public static final DeferredItem<BlockItem> HELICAL_WHIP_ITEM =
            ITEMS.registerSimpleBlockItem("helical_whip", HELICAL_WHIP, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.VhfAntennaBlock> RECEIVING_LOOP = BLOCKS.register("receiving_loop",
            () -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlock(device(MapColor.METAL, SoundType.METAL), com.sparkgap.pgtelegraph.block.VhfAntennaBlock.Type.LOOP));
    public static final DeferredItem<BlockItem> RECEIVING_LOOP_ITEM =
            ITEMS.registerSimpleBlockItem("receiving_loop", RECEIVING_LOOP, new Item.Properties());
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.VhfAntennaBlock> HELIX_ANTENNA = BLOCKS.register("helix_antenna",
            () -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlock(device(MapColor.METAL, SoundType.METAL), com.sparkgap.pgtelegraph.block.VhfAntennaBlock.Type.HELIX));
    public static final DeferredItem<BlockItem> HELIX_ANTENNA_ITEM =
            ITEMS.registerSimpleBlockItem("helix_antenna", HELIX_ANTENNA, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity>> VHF_ANTENNA_BE =
            BLOCK_ENTITIES.register("vhf_antenna", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity(PgTelegraph.VHF_ANTENNA_BE.get(), pos, state),
                            VHF_WHIP.get(), YAGI_ANTENNA.get(), DISH_ANTENNA.get(),
                            HELICAL_WHIP.get(), RECEIVING_LOOP.get(), HELIX_ANTENNA.get(), PgTelegraph.PHASED_ARRAY.get())
                    .build(null));

    // --- Handheld radio (휴대용 무전기) ---
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<com.sparkgap.pgtelegraph.radio.HandheldData>> HANDHELD =
            DATA_COMPONENTS.registerComponentType("handheld", b -> b
                    .persistent(com.sparkgap.pgtelegraph.radio.HandheldData.CODEC).networkSynchronized(com.sparkgap.pgtelegraph.radio.HandheldData.STREAM_CODEC));
    public static final DeferredItem<com.sparkgap.pgtelegraph.radio.ManpackItem> MANPACK =
            ITEMS.register("manpack_radio", () -> new com.sparkgap.pgtelegraph.radio.ManpackItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.radio.HandheldRadioItem> HANDHELD_RADIO =
            ITEMS.register("handheld_radio", () -> new com.sparkgap.pgtelegraph.radio.HandheldRadioItem(new Item.Properties()));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.HandheldChargerBlock> HANDHELD_CHARGER = BLOCKS.register("handheld_charger",
            () -> new com.sparkgap.pgtelegraph.block.HandheldChargerBlock(device(MapColor.COLOR_BLACK, SoundType.METAL)));
    public static final DeferredItem<BlockItem> HANDHELD_CHARGER_ITEM =
            ITEMS.registerSimpleBlockItem("handheld_charger", HANDHELD_CHARGER, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.HandheldChargerBlockEntity>> HANDHELD_CHARGER_BE =
            BLOCK_ENTITIES.register("handheld_charger", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.sparkgap.pgtelegraph.block.HandheldChargerBlockEntity(PgTelegraph.HANDHELD_CHARGER_BE.get(), pos, state), HANDHELD_CHARGER.get())
                    .build(null));

    // --- Clip-lead keys (집게 전건) ---
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<com.sparkgap.pgtelegraph.item.ClipKeyItem.Leads>> CLIP_LEADS =
            DATA_COMPONENTS.registerComponentType("clip_leads", b -> b
                    .persistent(com.sparkgap.pgtelegraph.item.ClipKeyItem.Leads.CODEC)
                    .networkSynchronized(com.sparkgap.pgtelegraph.item.ClipKeyItem.Leads.STREAM_CODEC));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.ClipKeyItem> CLIP_KEY =
            ITEMS.register("clip_key", () -> new com.sparkgap.pgtelegraph.item.ClipKeyItem(new Item.Properties(), false, 250f, 3f));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.ClipKeyItem> CLIP_PADDLE =
            ITEMS.register("clip_paddle", () -> new com.sparkgap.pgtelegraph.item.ClipKeyItem(new Item.Properties(), true, 30f, 0.5f));

    // --- Radio parts (부품) ---
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<com.sparkgap.pgtelegraph.parts.BatteryState>> BATTERY_STATE =
            DATA_COMPONENTS.registerComponentType("battery_state", b -> b
                    .persistent(com.sparkgap.pgtelegraph.parts.BatteryState.CODEC).networkSynchronized(com.sparkgap.pgtelegraph.parts.BatteryState.STREAM_CODEC));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<Integer>> PART_WEAR =
            DATA_COMPONENTS.registerComponentType("part_wear", b -> b
                    .persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
            net.minecraft.core.component.DataComponentType<String>> BURNT_OF =
            DATA_COMPONENTS.registerComponentType("burnt_of", b -> b
                    .persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));
    public static final java.util.Map<String, DeferredItem<com.sparkgap.pgtelegraph.parts.PartItem>> PART_ITEMS = new java.util.LinkedHashMap<>();

    static {
        for (com.sparkgap.pgtelegraph.parts.PartDef d : com.sparkgap.pgtelegraph.parts.PartDef.ALL.values()) {
            PART_ITEMS.put(d.id(), ITEMS.register(d.id(), () -> new com.sparkgap.pgtelegraph.parts.PartItem(new Item.Properties(), d)));
        }
    }

    public static final DeferredItem<com.sparkgap.pgtelegraph.parts.BurntPartItem> BURNT_PART =
            ITEMS.register("burnt_part", () -> new com.sparkgap.pgtelegraph.parts.BurntPartItem(new Item.Properties()));
    public static final DeferredItem<Item> EXPLODED_RADIO =
            ITEMS.register("exploded_radio", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.sparkgap.pgtelegraph.parts.ScrewdriverItem> SCREWDRIVER =
            ITEMS.register("screwdriver", () -> new com.sparkgap.pgtelegraph.parts.ScrewdriverItem(new Item.Properties()));
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,
            net.minecraft.world.inventory.MenuType<com.sparkgap.pgtelegraph.parts.RadioPartsMenu>> PARTS_MENU =
            MENUS.register("radio_parts", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.sparkgap.pgtelegraph.parts.RadioPartsMenu::new));


    // ---------------- batch 2: test gear, beacons, instruments, power, records ----------------
    private static <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> block(String id, java.util.function.Supplier<B> b) {
        DeferredBlock<B> r = BLOCKS.register(id, b);
        ITEMS.registerSimpleBlockItem(id, r, new Item.Properties());
        return r;
    }

    @SafeVarargs
    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> be(
            String id, BlockEntityType.BlockEntitySupplier<T> f, java.util.function.Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
        return BLOCK_ENTITIES.register(id, () -> {
            net.minecraft.world.level.block.Block[] bs = new net.minecraft.world.level.block.Block[blocks.length];
            for (int i = 0; i < bs.length; i++) bs[i] = blocks[i].get();
            //noinspection DataFlowIssue
            return BlockEntityType.Builder.of(f, bs).build(null);
        });
    }

    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.DummyLoadBlock> DUMMY_LOAD = block("dummy_load",
            () -> new com.sparkgap.pgtelegraph.block.DummyLoadBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.DummyLoadBlockEntity>> DUMMY_LOAD_BE =
            be("dummy_load", (p, st) -> new com.sparkgap.pgtelegraph.block.DummyLoadBlockEntity(PgTelegraph.DUMMY_LOAD_BE.get(), p, st), DUMMY_LOAD);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.AntennaTunerBlock> ANTENNA_TUNER = block("antenna_tuner",
            () -> new com.sparkgap.pgtelegraph.block.AntennaTunerBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.AntennaTunerBlock> REMOTE_TUNER = block("remote_tuner",
            () -> new com.sparkgap.pgtelegraph.block.AntennaTunerBlock(device(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL), com.sparkgap.pgtelegraph.radio.AntennaLink.Tuner.REMOTE));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.ManualTunerBlock> MANUAL_TUNER = block("manual_tuner",
            () -> new com.sparkgap.pgtelegraph.block.ManualTunerBlock(device(MapColor.WOOD, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.ManualTunerBlockEntity>> MANUAL_TUNER_BE =
            be("manual_tuner", (p, st) -> new com.sparkgap.pgtelegraph.block.ManualTunerBlockEntity(PgTelegraph.MANUAL_TUNER_BE.get(), p, st), MANUAL_TUNER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.BeaconBlock> BEACON = block("beacon", () -> new com.sparkgap.pgtelegraph.block.BeaconBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.BeaconBlockEntity>> BEACON_BE =
            be("beacon", (p, st) -> new com.sparkgap.pgtelegraph.block.BeaconBlockEntity(PgTelegraph.BEACON_BE.get(), p, st), BEACON);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.SolarBeaconBlock> SOLAR_BEACON = block("solar_beacon", () -> new com.sparkgap.pgtelegraph.block.SolarBeaconBlock(device(MapColor.COLOR_BLUE, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.SolarBeaconBlockEntity>> SOLAR_BEACON_BE =
            be("solar_beacon", (p, st) -> new com.sparkgap.pgtelegraph.block.SolarBeaconBlockEntity(PgTelegraph.SOLAR_BEACON_BE.get(), p, st), SOLAR_BEACON);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.SparkTransmitterBlock> SPARK_TRANSMITTER = block("spark_transmitter",
            () -> new com.sparkgap.pgtelegraph.block.SparkTransmitterBlock(device(MapColor.WOOD, SoundType.WOOD)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.SparkTransmitterBlockEntity>> SPARK_TRANSMITTER_BE =
            be("spark_transmitter", (p, st) -> new com.sparkgap.pgtelegraph.block.SparkTransmitterBlockEntity(PgTelegraph.SPARK_TRANSMITTER_BE.get(), p, st), SPARK_TRANSMITTER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.RepeaterBlock> REPEATER = block("repeater", () -> new com.sparkgap.pgtelegraph.block.RepeaterBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.RepeaterBlockEntity>> REPEATER_BE =
            be("repeater", (p, st) -> new com.sparkgap.pgtelegraph.block.RepeaterBlockEntity(PgTelegraph.REPEATER_BE.get(), p, st), REPEATER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.OscilloscopeBlock> OSCILLOSCOPE = block("oscilloscope", () -> new com.sparkgap.pgtelegraph.block.OscilloscopeBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.OscilloscopeBlockEntity>> OSCILLOSCOPE_BE =
            be("oscilloscope", (p, st) -> new com.sparkgap.pgtelegraph.block.OscilloscopeBlockEntity(PgTelegraph.OSCILLOSCOPE_BE.get(), p, st), OSCILLOSCOPE);

    private static DeferredBlock<com.sparkgap.pgtelegraph.block.BatteryBlock> battery(String id, com.sparkgap.pgtelegraph.block.BatteryBlockEntity.Chem c) {
        DeferredBlock<com.sparkgap.pgtelegraph.block.BatteryBlock> r = BLOCKS.register(id, () -> new com.sparkgap.pgtelegraph.block.BatteryBlock(device(MapColor.COLOR_GRAY, SoundType.METAL), c));
        ITEMS.register(id, () -> new com.sparkgap.pgtelegraph.block.BatteryItem(r.get(), new Item.Properties()));
        return r;
    }

    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.BatteryBlock> BATTERY_LI_ION = battery("battery_pack_li_ion", com.sparkgap.pgtelegraph.block.BatteryBlockEntity.Chem.LI_ION);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.BatteryBlock> BATTERY_LIFEPO4 = battery("battery_pack_lifepo4", com.sparkgap.pgtelegraph.block.BatteryBlockEntity.Chem.LIFEPO4);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.BatteryBlock> BATTERY_LEAD_ACID = battery("battery_pack_lead_acid", com.sparkgap.pgtelegraph.block.BatteryBlockEntity.Chem.LEAD_ACID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.BatteryBlockEntity>> BATTERY_BE =
            be("battery_pack", (p, st) -> new com.sparkgap.pgtelegraph.block.BatteryBlockEntity(PgTelegraph.BATTERY_BE.get(), p, st), BATTERY_LI_ION, BATTERY_LIFEPO4, BATTERY_LEAD_ACID);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.BmsBlock> BMS = block("bms", () -> new com.sparkgap.pgtelegraph.block.BmsBlock(device(MapColor.COLOR_GREEN, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.BmsBlockEntity>> BMS_BE =
            be("bms", (p, st) -> new com.sparkgap.pgtelegraph.block.BmsBlockEntity(PgTelegraph.BMS_BE.get(), p, st), BMS);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.DecoderBlock> DECODER = block("decoder", () -> new com.sparkgap.pgtelegraph.block.DecoderBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.DecoderBlockEntity>> DECODER_BE =
            be("decoder", (p, st) -> new com.sparkgap.pgtelegraph.block.DecoderBlockEntity(PgTelegraph.DECODER_BE.get(), p, st), DECODER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.TeleprinterBlock> TELEPRINTER = block("teleprinter", () -> new com.sparkgap.pgtelegraph.block.TeleprinterBlock(device(MapColor.COLOR_GRAY, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.TeleprinterBlockEntity>> TELEPRINTER_BE =
            be("teleprinter", (p, st) -> new com.sparkgap.pgtelegraph.block.TeleprinterBlockEntity(PgTelegraph.TELEPRINTER_BE.get(), p, st), TELEPRINTER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.TelegraphPoleBlock> TELEGRAPH_POLE = block("telegraph_pole",
            () -> new com.sparkgap.pgtelegraph.block.TelegraphPoleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.InsulatorBlock> GLASS_INSULATOR = block("glass_insulator",
            () -> new com.sparkgap.pgtelegraph.block.InsulatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.5f).sound(SoundType.GLASS).noOcclusion(), com.sparkgap.pgtelegraph.block.InsulatorBlock.Kind.GLASS));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.InsulatorBlock> WOODEN_PEG = block("wooden_peg",
            () -> new com.sparkgap.pgtelegraph.block.InsulatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5f).sound(SoundType.WOOD).noOcclusion(), com.sparkgap.pgtelegraph.block.InsulatorBlock.Kind.PEG));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.InsulatorBlockEntity>> INSULATOR_BE =
            be("insulator", (p, st) -> new com.sparkgap.pgtelegraph.block.InsulatorBlockEntity(PgTelegraph.INSULATOR_BE.get(), p, st), GLASS_INSULATOR, WOODEN_PEG);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.LnaBlock> LNA = block("inline_lna",
            () -> new com.sparkgap.pgtelegraph.block.LnaBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.LightningArresterBlock> LIGHTNING_ARRESTER = block("lightning_arrester",
            () -> new com.sparkgap.pgtelegraph.block.LightningArresterBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.RotatorBlock> ROTATOR = block("rotator", () -> new com.sparkgap.pgtelegraph.block.RotatorBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.RotatorBlockEntity>> ROTATOR_BE =
            be("rotator", (p, st) -> new com.sparkgap.pgtelegraph.block.RotatorBlockEntity(PgTelegraph.ROTATOR_BE.get(), p, st), ROTATOR);

    // items
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Long>> ITEM_FREQ =
            DATA_COMPONENTS.registerComponentType("item_freq", b -> b.persistent(com.mojang.serialization.Codec.LONG)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Boolean>> ELT_ON =
            DATA_COMPONENTS.registerComponentType("elt_on", b -> b.persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<java.util.List<String>>> LOGBOOK =
            DATA_COMPONENTS.registerComponentType("logbook", b -> b.persistent(com.mojang.serialization.Codec.STRING.listOf())
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list())));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<String>> QSL =
            DATA_COMPONENTS.registerComponentType("qsl", b -> b.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<String>> CIPHER_KEY =
            DATA_COMPONENTS.registerComponentType("cipher_key", b -> b.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    public static final DeferredItem<com.sparkgap.pgtelegraph.item.MeterItem> SWR_METER = ITEMS.register("swr_meter", () -> new com.sparkgap.pgtelegraph.item.MeterItem(new Item.Properties(), com.sparkgap.pgtelegraph.item.MeterItem.Kind.SWR));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.MeterItem> FIELD_METER = ITEMS.register("field_strength_meter", () -> new com.sparkgap.pgtelegraph.item.MeterItem(new Item.Properties(), com.sparkgap.pgtelegraph.item.MeterItem.Kind.FIELD));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.MeterItem> FREQ_COUNTER = ITEMS.register("frequency_counter", () -> new com.sparkgap.pgtelegraph.item.MeterItem(new Item.Properties(), com.sparkgap.pgtelegraph.item.MeterItem.Kind.COUNTER));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.MeterItem> DIRECTION_FINDER = ITEMS.register("direction_finder", () -> new com.sparkgap.pgtelegraph.item.MeterItem(new Item.Properties(), com.sparkgap.pgtelegraph.item.MeterItem.Kind.DF));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.MeterItem> SPECTRUM_ANALYZER = ITEMS.register("spectrum_analyzer", () -> new com.sparkgap.pgtelegraph.item.MeterItem(new Item.Properties(), com.sparkgap.pgtelegraph.item.MeterItem.Kind.SPECTRUM));
    public static final DeferredItem<com.sparkgap.pgtelegraph.radio.EltItem> ELT = ITEMS.register("elt", () -> new com.sparkgap.pgtelegraph.radio.EltItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.RepairKitItem> REPAIR_KIT = ITEMS.register("repair_kit", () -> new com.sparkgap.pgtelegraph.item.RepairKitItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.LicenseItem> LICENSE = ITEMS.register("license", () -> new com.sparkgap.pgtelegraph.item.LicenseItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.LogbookItem> LOGBOOK_ITEM = ITEMS.register("logbook", () -> new com.sparkgap.pgtelegraph.item.LogbookItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.QslCardItem> QSL_CARD = ITEMS.register("qsl_card", () -> new com.sparkgap.pgtelegraph.item.QslCardItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.CipherMachineItem> CIPHER_MACHINE = ITEMS.register("cipher_machine", () -> new com.sparkgap.pgtelegraph.item.CipherMachineItem(new Item.Properties()));
    // satellites
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Integer>> SAT_MODE =
            DATA_COMPONENTS.registerComponentType("sat_mode", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<String>> SAT_CONFIG =
            DATA_COMPONENTS.registerComponentType("sat_config", b -> b.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));
    public static final DeferredItem<Item> ROCKET = ITEMS.register("rocket", () -> new Item(new Item.Properties().stacksTo(4)));
    public static final DeferredItem<com.sparkgap.pgtelegraph.sat.CubeSatItem> CUBESAT = ITEMS.register("cubesat",
            () -> new com.sparkgap.pgtelegraph.sat.CubeSatItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.MeterItem> SAT_TRACKER = ITEMS.register("satellite_tracker",
            () -> new com.sparkgap.pgtelegraph.item.MeterItem(new Item.Properties(), com.sparkgap.pgtelegraph.item.MeterItem.Kind.TRACKER));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.sat.LaunchPadBlock> LAUNCH_PAD = block("launch_pad",
            () -> new com.sparkgap.pgtelegraph.sat.LaunchPadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.sat.LaunchPadBlockEntity>> LAUNCH_PAD_BE =
            be("launch_pad", (p, st) -> new com.sparkgap.pgtelegraph.sat.LaunchPadBlockEntity(PgTelegraph.LAUNCH_PAD_BE.get(), p, st), LAUNCH_PAD);
    // batch 3: ground station extras, broadcast
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> DeferredBlock<com.sparkgap.pgtelegraph.block.StationBlock<T>> station(
            String id, double[] box, Class<T> cls, java.util.function.Supplier<? extends BlockEntityType<? extends T>> type,
            com.sparkgap.pgtelegraph.block.StationBlock.Use<T> use, MapColor color) {
        return block(id, () -> new com.sparkgap.pgtelegraph.block.StationBlock<>(device(color, SoundType.METAL), box, cls, type, use));
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> DeferredBlock<com.sparkgap.pgtelegraph.block.PoweredStationBlock<T>> poweredStation(
            String id, double[] box, Class<T> cls, java.util.function.Supplier<? extends BlockEntityType<? extends T>> type,
            com.sparkgap.pgtelegraph.block.StationBlock.Use<T> use, MapColor color) {
        return block(id, () -> new com.sparkgap.pgtelegraph.block.PoweredStationBlock<>(device(color, SoundType.METAL), box, cls, type, use));
    }

    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PoweredStationBlock<com.sparkgap.pgtelegraph.block.IonosondeBlockEntity>> IONOSONDE = poweredStation("ionosonde", new double[]{1, 0, 2, 15, 5, 14},
            com.sparkgap.pgtelegraph.block.IonosondeBlockEntity.class, () -> PgTelegraph.IONOSONDE_BE.get(), (be, p, l, pos) -> {
                if (l.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.openIonogram(pos);
                return true;
            }, MapColor.METAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.IonosondeBlockEntity>> IONOSONDE_BE =
            be("ionosonde", (p, st) -> new com.sparkgap.pgtelegraph.block.IonosondeBlockEntity(PgTelegraph.IONOSONDE_BE.get(), p, st), IONOSONDE);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PoweredStationBlock<com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity>> QSO_ROBOT = poweredStation("qso_robot", new double[]{2, 0, 3, 14, 9, 14},
            com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity.class, () -> PgTelegraph.QSO_ROBOT_BE.get(), (be, p, l, pos) -> {
                if (l.isClientSide) {
                    if (p.isShiftKeyDown()) com.sparkgap.pgtelegraph.client.ClientAccess.openBeacon(pos, be.program());
                    else com.sparkgap.pgtelegraph.client.ClientAccess.openTune(pos, be.freqHz());
                }
                return true;
            }, MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity>> QSO_ROBOT_BE =
            be("qso_robot", (p, st) -> new com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity(PgTelegraph.QSO_ROBOT_BE.get(), p, st), QSO_ROBOT);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.StationBlock<com.sparkgap.pgtelegraph.block.TapeRecorderBlockEntity>> TAPE_RECORDER = station("tape_recorder", new double[]{2, 0, 3, 14, 7, 13},
            com.sparkgap.pgtelegraph.block.TapeRecorderBlockEntity.class, () -> PgTelegraph.TAPE_RECORDER_BE.get(), (be, p, l, pos) -> {
                if (l.isClientSide) return true;
                if (p.isShiftKeyDown()) {
                    var t = be.eject();
                    if (!t.isEmpty() && !p.getInventory().add(t)) p.drop(t, false);
                } else {
                    int st = be.cycle();
                    p.displayClientMessage(Component.translatable("pgtelegraph.recorder.state." + st), true);
                }
                return true;
            }, MapColor.COLOR_BROWN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.TapeRecorderBlockEntity>> TAPE_RECORDER_BE =
            be("tape_recorder", (p, st) -> new com.sparkgap.pgtelegraph.block.TapeRecorderBlockEntity(PgTelegraph.TAPE_RECORDER_BE.get(), p, st), TAPE_RECORDER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.CoaxSwitchBlock> COAX_SWITCH = block("coax_switch",
            () -> new com.sparkgap.pgtelegraph.block.CoaxSwitchBlock(device(MapColor.METAL, SoundType.METAL)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.VhfAntennaBlock> PHASED_ARRAY = block("phased_array",
            () -> new com.sparkgap.pgtelegraph.block.VhfAntennaBlock(device(MapColor.METAL, SoundType.METAL), com.sparkgap.pgtelegraph.block.VhfAntennaBlock.Type.ARRAY));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PoweredStationBlock<com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity>> SSTV_MONITOR = poweredStation("sstv_monitor",
            new double[]{2, 0, 3, 14, 12, 14}, com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity.class, () -> PgTelegraph.SSTV_MONITOR_BE.get(),
            (be, p, l, pos) -> {
                if (p.getMainHandItem().is(net.minecraft.world.item.Items.PAPER)) { // print the picture
                    if (!l.isClientSide) {
                        if (!p.getAbilities().instabuild) p.getMainHandItem().shrink(1);
                        var photo = be.print();
                        if (!p.getInventory().add(photo)) p.drop(photo, false);
                    }
                    return true;
                }
                if (l.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.openSstv(pos);
                return true;
            }, MapColor.COLOR_GRAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity>> SSTV_MONITOR_BE =
            be("sstv_monitor", (p, st) -> new com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity(PgTelegraph.SSTV_MONITOR_BE.get(), p, st), SSTV_MONITOR);

    // broadcast
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<java.util.List<Integer>>> MAG_TAPE =
            DATA_COMPONENTS.registerComponentType("mag_tape", b -> b.persistent(com.mojang.serialization.Codec.INT.listOf())
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.apply(net.minecraft.network.codec.ByteBufCodecs.list())));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<String>> AUDIO =
            DATA_COMPONENTS.registerComponentType("audio", b -> b.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.MagneticTapeItem> MAGNETIC_TAPE = ITEMS.register("magnetic_tape",
            () -> new com.sparkgap.pgtelegraph.item.MagneticTapeItem(new Item.Properties()));
    public static final DeferredItem<Item> ROCKET_MEDIUM = ITEMS.register("rocket_medium", () -> new Item(new Item.Properties().stacksTo(2)));
    public static final DeferredItem<Item> ROCKET_HEAVY = ITEMS.register("rocket_heavy", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.sparkgap.pgtelegraph.broadcast.RecordItems.Blank> BLANK_RECORD = ITEMS.register("blank_record",
            () -> new com.sparkgap.pgtelegraph.broadcast.RecordItems.Blank(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.broadcast.RecordItems.Recorded> RECORDED_DISC = ITEMS.register("recorded_disc",
            () -> new com.sparkgap.pgtelegraph.broadcast.RecordItems.Recorded(new Item.Properties()));
    public static final DeferredHolder<SoundEvent, SoundEvent> BROADCAST_STREAM = sound("broadcast_stream");
    public static final DeferredBlock<com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlock> BROADCAST_TX = block("broadcast_transmitter",
            () -> new com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlock(device(MapColor.COLOR_RED, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlockEntity>> BROADCAST_TX_BE =
            be("broadcast_transmitter", (p, st) -> new com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlockEntity(PgTelegraph.BROADCAST_TX_BE.get(), p, st), BROADCAST_TX);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.media.TvTransmitterBlock> TV_TX = block("tv_transmitter",
            () -> new com.sparkgap.pgtelegraph.media.TvTransmitterBlock(device(MapColor.COLOR_GREEN, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.media.TvTransmitterBlockEntity>> TV_TX_BE =
            be("tv_transmitter", (p, st) -> new com.sparkgap.pgtelegraph.media.TvTransmitterBlockEntity(PgTelegraph.TV_TX_BE.get(), p, st), TV_TX);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PoweredStationBlock<com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity>> TV_RECEIVER = poweredStation("tv_receiver",
            new double[]{2, 0, 3, 14, 12, 14}, com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity.class, () -> PgTelegraph.TV_RECEIVER_BE.get(),
            (be, p, l, pos) -> {
                if (l.isClientSide) {
                    if (p.isShiftKeyDown()) com.sparkgap.pgtelegraph.client.ClientAccess.openTune(pos, be.freqHz());
                    else com.sparkgap.pgtelegraph.client.ClientAccess.openTv(pos);
                }
                return true;
            }, MapColor.COLOR_BROWN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity>> TV_RECEIVER_BE =
            be("tv_receiver", (p, st) -> new com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity(PgTelegraph.TV_RECEIVER_BE.get(), p, st), TV_RECEIVER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlock> FM_BROADCAST_TX = block("fm_broadcast_transmitter",
            () -> new com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlock(device(MapColor.COLOR_BLUE, SoundType.METAL), () -> PgTelegraph.FM_BROADCAST_TX_BE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.broadcast.FmBroadcastTransmitterBlockEntity>> FM_BROADCAST_TX_BE =
            be("fm_broadcast_transmitter", (p, st) -> new com.sparkgap.pgtelegraph.broadcast.FmBroadcastTransmitterBlockEntity(PgTelegraph.FM_BROADCAST_TX_BE.get(), p, st), FM_BROADCAST_TX);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.JammerBlock> JAMMER = block("jammer",
            () -> new com.sparkgap.pgtelegraph.block.JammerBlock(device(MapColor.COLOR_RED, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.JammerBlockEntity>> JAMMER_BE =
            be("jammer", (p, st) -> new com.sparkgap.pgtelegraph.block.JammerBlockEntity(PgTelegraph.JAMMER_BE.get(), p, st), JAMMER);

    // --- audio: amplifier, cone speaker, horn loudspeaker ---
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.AudioAmpBlock> AUDIO_AMP = block("audio_amplifier",
            () -> new com.sparkgap.pgtelegraph.block.AudioAmpBlock(device(MapColor.COLOR_BLACK, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.AudioAmpBlockEntity>> AUDIO_AMP_BE =
            be("audio_amplifier", (p, st) -> new com.sparkgap.pgtelegraph.block.AudioAmpBlockEntity(PgTelegraph.AUDIO_AMP_BE.get(), p, st), AUDIO_AMP);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PowerSupplyBlock> POWER_SUPPLY = block("power_supply",
            () -> new com.sparkgap.pgtelegraph.block.PowerSupplyBlock(device(MapColor.COLOR_GRAY, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.PowerSupplyBlockEntity>> POWER_SUPPLY_BE =
            be("power_supply", (p, st) -> new com.sparkgap.pgtelegraph.block.PowerSupplyBlockEntity(PgTelegraph.POWER_SUPPLY_BE.get(), p, st), POWER_SUPPLY);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.FieldPhoneBlock> FIELD_PHONE = block("field_phone",
            () -> new com.sparkgap.pgtelegraph.block.FieldPhoneBlock(device(MapColor.COLOR_GREEN, SoundType.WOOD)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.FieldPhoneBlockEntity>> FIELD_PHONE_BE =
            be("field_phone", (p, st) -> new com.sparkgap.pgtelegraph.block.FieldPhoneBlockEntity(PgTelegraph.FIELD_PHONE_BE.get(), p, st), FIELD_PHONE);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.GenericDeviceBlock<com.sparkgap.pgtelegraph.block.DuplexSetBlockEntity>> DUPLEX_SET = block("duplex_set",
            () -> new com.sparkgap.pgtelegraph.block.GenericDeviceBlock<>(device(MapColor.COLOR_BROWN, SoundType.METAL), new double[]{1, 0, 1, 15, 4, 15},
                    new double[][]{{3, 4, 11.5, 6, 5.5, 14.5}, {10, 4, 11.5, 13, 5.5, 14.5}, {1.5, 4, 1.5, 4, 5.5, 4}, {4.5, 4, 1.5, 7, 5.5, 4}, {9, 4, 1.5, 11.5, 5.5, 4}, {12, 4, 1.5, 14.5, 5.5, 4}}, new String[]{"tline", "earth", "key", "key_com", "contact", "contact"}, com.sparkgap.pgtelegraph.block.DuplexSetBlockEntity.class, () -> PgTelegraph.DUPLEX_SET_BE.get(), null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.DuplexSetBlockEntity>> DUPLEX_SET_BE =
            be("duplex_set", (p, st) -> new com.sparkgap.pgtelegraph.block.DuplexSetBlockEntity(PgTelegraph.DUPLEX_SET_BE.get(), p, st), DUPLEX_SET);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.GenericDeviceBlock<com.sparkgap.pgtelegraph.block.LineTestSetBlockEntity>> LINE_TEST_SET = block("line_test_set",
            () -> new com.sparkgap.pgtelegraph.block.GenericDeviceBlock<>(device(MapColor.COLOR_BLACK, SoundType.METAL), new double[]{3, 0, 3, 13, 6, 13},
                    new double[][]{{4, 6, 9.5, 7, 7.5, 12.5}, {9, 6, 9.5, 12, 7.5, 12.5}}, new String[]{"tline", "earth"}, com.sparkgap.pgtelegraph.block.LineTestSetBlockEntity.class, () -> PgTelegraph.LINE_TEST_SET_BE.get(), (be, pl, l, pos) -> { if (!l.isClientSide) be.start(pl); return true; }));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.LineTestSetBlockEntity>> LINE_TEST_SET_BE =
            be("line_test_set", (p, st) -> new com.sparkgap.pgtelegraph.block.LineTestSetBlockEntity(PgTelegraph.LINE_TEST_SET_BE.get(), p, st), LINE_TEST_SET);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.GenericDeviceBlock<com.sparkgap.pgtelegraph.block.SubmarineCableBlockEntity>> SUBMARINE_CABLE = block("submarine_cable",
            () -> new com.sparkgap.pgtelegraph.block.GenericDeviceBlock<>(device(MapColor.COLOR_BLACK, SoundType.METAL), new double[]{0, 0, 4, 16, 4, 12},
                    new double[][]{{0.5, 4, 6.5, 3.5, 5.5, 9.5}, {12.5, 4, 6.5, 15.5, 5.5, 9.5}}, new String[]{"cable_a", "cable_b"}, com.sparkgap.pgtelegraph.block.SubmarineCableBlockEntity.class, () -> PgTelegraph.SUBMARINE_CABLE_BE.get(), null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.SubmarineCableBlockEntity>> SUBMARINE_CABLE_BE =
            be("submarine_cable", (p, st) -> new com.sparkgap.pgtelegraph.block.SubmarineCableBlockEntity(PgTelegraph.SUBMARINE_CABLE_BE.get(), p, st), SUBMARINE_CABLE);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.GenericDeviceBlock<com.sparkgap.pgtelegraph.block.MirrorGalvanometerBlockEntity>> MIRROR_GALVANOMETER = block("mirror_galvanometer",
            () -> new com.sparkgap.pgtelegraph.block.GenericDeviceBlock<>(device(MapColor.WOOD, SoundType.METAL), new double[]{3, 0, 3, 13, 8, 13},
                    new double[][]{{3.5, 8, 9.5, 6, 9.5, 12}, {10, 8, 9.5, 12.5, 9.5, 12}, {3.5, 8, 4.5, 6, 9.5, 7}, {10, 8, 4.5, 12.5, 9.5, 7}}, new String[]{"in_pos", "in_neg", "contact", "contact"}, com.sparkgap.pgtelegraph.block.MirrorGalvanometerBlockEntity.class, () -> PgTelegraph.MIRROR_GALVANOMETER_BE.get(), null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.MirrorGalvanometerBlockEntity>> MIRROR_GALVANOMETER_BE =
            be("mirror_galvanometer", (p, st) -> new com.sparkgap.pgtelegraph.block.MirrorGalvanometerBlockEntity(PgTelegraph.MIRROR_GALVANOMETER_BE.get(), p, st), MIRROR_GALVANOMETER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.GenericDeviceBlock<com.sparkgap.pgtelegraph.block.TelegraphRepeaterBlockEntity>> TELEGRAPH_REPEATER = block("telegraph_repeater",
            () -> new com.sparkgap.pgtelegraph.block.GenericDeviceBlock<>(device(MapColor.METAL, SoundType.METAL), new double[]{1, 0, 2, 15, 5, 14},
                    new double[][]{{1.5, 5, 6.5, 4.5, 6.5, 9.5}, {11.5, 5, 6.5, 14.5, 6.5, 9.5}, {6.5, 5, 10.5, 9.5, 6.5, 13.5}}, new String[]{"west", "east", "earth"}, com.sparkgap.pgtelegraph.block.TelegraphRepeaterBlockEntity.class, () -> PgTelegraph.TELEGRAPH_REPEATER_BE.get(), null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.TelegraphRepeaterBlockEntity>> TELEGRAPH_REPEATER_BE =
            be("telegraph_repeater", (p, st) -> new com.sparkgap.pgtelegraph.block.TelegraphRepeaterBlockEntity(PgTelegraph.TELEGRAPH_REPEATER_BE.get(), p, st), TELEGRAPH_REPEATER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.GenericDeviceBlock<com.sparkgap.pgtelegraph.block.LineSimulatorBlockEntity>> LINE_SIMULATOR = block("line_simulator",
            () -> new com.sparkgap.pgtelegraph.block.GenericDeviceBlock<>(device(MapColor.COLOR_PURPLE, SoundType.METAL), new double[]{2, 0, 2, 14, 6, 14},
                    new double[][]{{0.5, 6, 6.5, 3.5, 7.5, 9.5}, {12.5, 6, 6.5, 15.5, 7.5, 9.5}}, new String[]{"cable_a", "cable_b"},
                    com.sparkgap.pgtelegraph.block.LineSimulatorBlockEntity.class, () -> PgTelegraph.LINE_SIMULATOR_BE.get(), (be, pl, l, pos) -> {
                if (!l.isClientSide && pl.isShiftKeyDown()) be.nextKind(pl);
                return pl.isShiftKeyDown();
            }));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.LineSimulatorBlockEntity>> LINE_SIMULATOR_BE =
            be("line_simulator", (p, st) -> new com.sparkgap.pgtelegraph.block.LineSimulatorBlockEntity(PgTelegraph.LINE_SIMULATOR_BE.get(), p, st), LINE_SIMULATOR);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.StationBlock<com.sparkgap.pgtelegraph.block.CreativeAntennaBlockEntity>> CREATIVE_ANTENNA = block("creative_antenna",
            () -> new com.sparkgap.pgtelegraph.block.StationBlock<>(device(MapColor.COLOR_PURPLE, SoundType.METAL), new double[]{6, 0, 6, 10, 16, 10},
                    com.sparkgap.pgtelegraph.block.CreativeAntennaBlockEntity.class, () -> PgTelegraph.CREATIVE_ANTENNA_BE.get(), null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.CreativeAntennaBlockEntity>> CREATIVE_ANTENNA_BE =
            be("creative_antenna", (p, st) -> new com.sparkgap.pgtelegraph.block.CreativeAntennaBlockEntity(PgTelegraph.CREATIVE_ANTENNA_BE.get(), p, st), CREATIVE_ANTENNA);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.SelectorBlock> SELECTOR = block("operating_selector",
            () -> new com.sparkgap.pgtelegraph.block.SelectorBlock(device(MapColor.COLOR_BLACK, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.SelectorBlockEntity>> SELECTOR_BE =
            be("operating_selector", (p, st) -> new com.sparkgap.pgtelegraph.block.SelectorBlockEntity(PgTelegraph.SELECTOR_BE.get(), p, st), SELECTOR);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.TransmitterBlock> CREATIVE_TRANSMITTER = block("creative_transmitter",
            () -> new com.sparkgap.pgtelegraph.block.TransmitterBlock(device(MapColor.COLOR_PURPLE, SoundType.METAL), new net.minecraft.network.chat.Component[]{
                    net.minecraft.network.chat.Component.translatable("pgtelegraph.terminal.pwr_pos"),
                    net.minecraft.network.chat.Component.translatable("pgtelegraph.terminal.pwr_neg"),
                    net.minecraft.network.chat.Component.translatable("pgtelegraph.terminal.key"),
                    net.minecraft.network.chat.Component.translatable("pgtelegraph.terminal.key_com")}, () -> PgTelegraph.CREATIVE_TRANSMITTER_BE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.CreativeTransmitterBlockEntity>> CREATIVE_TRANSMITTER_BE =
            be("creative_transmitter", (p, st) -> new com.sparkgap.pgtelegraph.block.CreativeTransmitterBlockEntity(PgTelegraph.CREATIVE_TRANSMITTER_BE.get(), p, st), CREATIVE_TRANSMITTER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.ReceiverBlock> CREATIVE_RECEIVER = block("creative_receiver",
            () -> new com.sparkgap.pgtelegraph.block.ReceiverBlock(device(MapColor.COLOR_PURPLE, SoundType.METAL), () -> PgTelegraph.CREATIVE_RECEIVER_BE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.CreativeReceiverBlockEntity>> CREATIVE_RECEIVER_BE =
            be("creative_receiver", (p, st) -> new com.sparkgap.pgtelegraph.block.CreativeReceiverBlockEntity(PgTelegraph.CREATIVE_RECEIVER_BE.get(), p, st), CREATIVE_RECEIVER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.EquipmentRackBlock> EQUIPMENT_RACK = block("equipment_rack",
            () -> new com.sparkgap.pgtelegraph.block.EquipmentRackBlock(device(MapColor.COLOR_BLACK, SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PowerPanelBlock> POWER_PANEL = block("power_panel",
            () -> new com.sparkgap.pgtelegraph.block.PowerPanelBlock(device(MapColor.COLOR_RED, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.PowerPanelBlockEntity>> POWER_PANEL_BE =
            be("power_panel", (p, st) -> new com.sparkgap.pgtelegraph.block.PowerPanelBlockEntity(PgTelegraph.POWER_PANEL_BE.get(), p, st), POWER_PANEL);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PowerSupplyBlock> UPS = block("ups",
            () -> new com.sparkgap.pgtelegraph.block.PowerSupplyBlock(device(MapColor.COLOR_BLACK, SoundType.METAL), () -> PgTelegraph.UPS_BE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.UpsBlockEntity>> UPS_BE =
            be("ups", (p, st) -> new com.sparkgap.pgtelegraph.block.UpsBlockEntity(PgTelegraph.UPS_BE.get(), p, st), UPS);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.DtmfDecoderBlock> DTMF_DECODER = block("dtmf_decoder",
            () -> new com.sparkgap.pgtelegraph.block.DtmfDecoderBlock(device(MapColor.COLOR_GRAY, SoundType.METAL), new double[]{3, 0, 4, 13, 5, 13},
                    () -> PgTelegraph.DTMF_DECODER_BE.get(), (be, p, l, pos) -> {
                if (l.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.openDtmf(pos, be);
                return true;
            }));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity>> DTMF_DECODER_BE =
            be("dtmf_decoder", (p, st) -> new com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity(PgTelegraph.DTMF_DECODER_BE.get(), p, st), DTMF_DECODER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.SpeakerBlock> SPEAKER = block("speaker",
            () -> new com.sparkgap.pgtelegraph.block.SpeakerBlock(device(MapColor.WOOD, SoundType.WOOD), false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.SpeakerBlockEntity>> SPEAKER_BE =
            be("speaker", (p, st) -> new com.sparkgap.pgtelegraph.block.SpeakerBlockEntity(PgTelegraph.SPEAKER_BE.get(), p, st), SPEAKER);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.SpeakerBlock> HORN_SPEAKER = block("horn_speaker",
            () -> new com.sparkgap.pgtelegraph.block.SpeakerBlock(device(MapColor.COLOR_GRAY, SoundType.METAL), true));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.SpeakerBlockEntity>> HORN_SPEAKER_BE =
            be("horn_speaker", (p, st) -> new com.sparkgap.pgtelegraph.block.SpeakerBlockEntity(PgTelegraph.HORN_SPEAKER_BE.get(), p, st), HORN_SPEAKER);

    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.PoweredStationBlock<com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity>> BROADCAST_RADIO = poweredStation("broadcast_radio",
            new double[]{3, 0, 5, 13, 8, 11}, com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity.class, () -> PgTelegraph.BROADCAST_RADIO_BE.get(),
            (be, p, l, pos) -> {
                if (p.isShiftKeyDown()) {
                    if (l.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.openTune(pos, be.freqHz());
                } else if (!l.isClientSide) be.toggle();
                return true;
            }, MapColor.COLOR_BROWN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity>> BROADCAST_RADIO_BE =
            be("broadcast_radio", (p, st) -> new com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity(PgTelegraph.BROADCAST_RADIO_BE.get(), p, st), BROADCAST_RADIO);
//BATCH3X
    /** handheld / manpack CTCSS tone index */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Integer>> CTCSS =
            DATA_COMPONENTS.registerComponentType("ctcss", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
    /** APRS tracker: network key ("item|item") and on/off */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<String>> APRS_KEY =
            DATA_COMPONENTS.registerComponentType("aprs_key", b -> b.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Boolean>> APRS_ON =
            DATA_COMPONENTS.registerComponentType("aprs_on", b -> b.persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));
    /** a Power Grid wire type (data/pgtelegraph/powergrid/wire_types/patch_cord.json) that unplugs by hand */
    public static final DeferredItem<org.patryk3211.powergrid.electricity.wire.WireItem> PATCH_CORD = ITEMS.register("patch_cord",
            () -> new org.patryk3211.powergrid.electricity.wire.WireItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.VnaItem> NANOVNA = ITEMS.register("nanovna",
            () -> new com.sparkgap.pgtelegraph.item.VnaItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.sparkgap.pgtelegraph.item.AprsTrackerItem> APRS_TRACKER = ITEMS.register("aprs_tracker",
            () -> new com.sparkgap.pgtelegraph.item.AprsTrackerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.AprsStationBlock> APRS_STATION = block("aprs_station",
            () -> new com.sparkgap.pgtelegraph.block.AprsStationBlock(device(MapColor.COLOR_GREEN, SoundType.METAL), () -> PgTelegraph.APRS_STATION_BE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.AprsStationBlockEntity>> APRS_STATION_BE =
            be("aprs_station", (p, st) -> new com.sparkgap.pgtelegraph.block.AprsStationBlockEntity(PgTelegraph.APRS_STATION_BE.get(), p, st), APRS_STATION);
    public static final DeferredBlock<com.sparkgap.pgtelegraph.block.MobileRadioBlock> MOBILE_RADIO = block("mobile_radio",
            () -> new com.sparkgap.pgtelegraph.block.MobileRadioBlock(device(MapColor.COLOR_BLACK, SoundType.METAL), () -> PgTelegraph.MOBILE_RADIO_BE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.block.MobileRadioBlockEntity>> MOBILE_RADIO_BE =
            be("mobile_radio", (p, st) -> new com.sparkgap.pgtelegraph.block.MobileRadioBlockEntity(PgTelegraph.MOBILE_RADIO_BE.get(), p, st), MOBILE_RADIO);
    // batch 4: voice, cameras, ground SSTV
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<com.sparkgap.pgtelegraph.media.Photo>> PHOTO =
            DATA_COMPONENTS.registerComponentType("photo", b -> b.persistent(com.sparkgap.pgtelegraph.media.Photo.CODEC).networkSynchronized(com.sparkgap.pgtelegraph.media.Photo.STREAM));
    public static final DeferredItem<com.sparkgap.pgtelegraph.media.PhotoItem> PHOTO_ITEM = ITEMS.register("photo", () -> new com.sparkgap.pgtelegraph.media.PhotoItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.media.CameraItem> CAMERA = ITEMS.register("camera", () -> new com.sparkgap.pgtelegraph.media.CameraItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.media.VideoTapeItem> VIDEO_TAPE = ITEMS.register("video_tape", () -> new com.sparkgap.pgtelegraph.media.VideoTapeItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.sparkgap.pgtelegraph.media.CamcorderItem> CAMCORDER = ITEMS.register("camcorder", () -> new com.sparkgap.pgtelegraph.media.CamcorderItem(new Item.Properties()));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.media.VcrBlock> VCR = block("vcr", () -> new com.sparkgap.pgtelegraph.media.VcrBlock(device(MapColor.COLOR_BLACK, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.media.VcrBlockEntity>> VCR_BE =
            be("vcr", (p, st) -> new com.sparkgap.pgtelegraph.media.VcrBlockEntity(PgTelegraph.VCR_BE.get(), p, st), VCR);
    public static final DeferredItem<com.sparkgap.pgtelegraph.media.FpvGogglesItem> FPV_GOGGLES = ITEMS.register("fpv_goggles", () -> new com.sparkgap.pgtelegraph.media.FpvGogglesItem(new Item.Properties()));
    public static final DeferredItem<com.sparkgap.pgtelegraph.media.BodycamItem> BODYCAM = ITEMS.register("bodycam", () -> new com.sparkgap.pgtelegraph.media.BodycamItem(new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.media.MicrophoneBlock> MICROPHONE = block("microphone", () -> new com.sparkgap.pgtelegraph.media.MicrophoneBlock(device(MapColor.COLOR_BLACK, SoundType.METAL)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.media.TvCameraBlock> TV_CAMERA = block("tv_camera", () -> new com.sparkgap.pgtelegraph.media.TvCameraBlock(device(MapColor.COLOR_GRAY, SoundType.METAL)));
    public static final DeferredBlock<com.sparkgap.pgtelegraph.media.SstvTransmitterBlock> SSTV_TX = block("sstv_transmitter",
            () -> new com.sparkgap.pgtelegraph.media.SstvTransmitterBlock(device(MapColor.COLOR_PURPLE, SoundType.METAL)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.sparkgap.pgtelegraph.media.SstvTransmitterBlockEntity>> SSTV_TX_BE =
            be("sstv_transmitter", (p, st) -> new com.sparkgap.pgtelegraph.media.SstvTransmitterBlockEntity(PgTelegraph.SSTV_TX_BE.get(), p, st), SSTV_TX);
//BATCH4X
//BATCH2X

    /** batch-2 items listed at the end of the creative tab, in registration order */
    static final java.util.Set<String> SHOWN = new java.util.HashSet<>(java.util.List.of(
            "dummy_load", "antenna_tuner", "remote_tuner", "manual_tuner"
, "beacon", "solar_beacon", "spark_transmitter", "repeater", "rotator", "lightning_arrester",
            "oscilloscope", "swr_meter", "field_strength_meter", "frequency_counter", "direction_finder", "spectrum_analyzer",
            "battery_pack_li_ion", "battery_pack_lifepo4", "battery_pack_lead_acid", "bms",
            "decoder", "teleprinter", "telegraph_pole", "glass_insulator", "wooden_peg",
            "elt", "repair_kit", "license", "logbook", "qsl_card", "cipher_machine"
, "launch_pad", "rocket", "cubesat", "satellite_tracker"
, "ionosonde", "qso_robot", "tape_recorder", "magnetic_tape", "coax_switch", "phased_array",
            "sstv_monitor", "rocket_medium", "rocket_heavy", "broadcast_transmitter", "fm_broadcast_transmitter", "tv_transmitter", "tv_receiver", "broadcast_radio", "power_supply", "ups", "power_panel", "equipment_rack", "operating_selector", "dtmf_decoder", "aprs_station", "aprs_tracker", "nanovna", "patch_cord", "mobile_radio", "field_phone", "duplex_set", "line_test_set", "submarine_cable", "mirror_galvanometer", "telegraph_repeater", "creative_transmitter", "creative_receiver", "creative_antenna", "line_simulator", "audio_amplifier", "speaker", "horn_speaker", "jammer", "manpack_radio", "inline_lna", "fpv_goggles", "blank_record", "recorded_disc"
, "microphone", "camera", "bodycam", "camcorder", "video_tape", "vcr", "photo", "tv_camera", "sstv_transmitter"
//SHOWNX
    ));

    // --- Own creative tab ---
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pgtelegraph"))
                    .icon(() -> new ItemStack(TELEGRAPH_KEY_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(TELEGRAPH_KEY_ITEM.get());
                        out.accept(PADDLE_ITEM.get());
                        out.accept(KEYER_ITEM.get());
                        out.accept(KEYER_PADDLE_ITEM.get());
                        out.accept(CLIP_KEY.get());
                        out.accept(CLIP_PADDLE.get());
                        out.accept(SOUNDER_ITEM.get());
                        out.accept(RELAY_ITEM.get());
                        out.accept(BUZZER_ITEM.get());
                        out.accept(INKER_ITEM.get());
                        out.accept(PERFORATOR_ITEM.get());
                        out.accept(TAPE_TRANSMITTER_ITEM.get());
                        out.accept(PAPER_TAPE.get());
                        out.accept(SWITCHBOARD_ITEM.get());
                        out.accept(HANDHELD_RADIO.get());
                        out.accept(HANDHELD_CHARGER_ITEM.get());
                        out.accept(SCREWDRIVER.get());
                        for (var it : PART_ITEMS.values()) out.accept(it.get());
                        out.accept(TRANSMITTER_ITEM.get());
                        out.accept(RECEIVER_ITEM.get());
                        out.accept(ANTENNA_ROD_ITEM.get());
                        out.accept(SHORT_ANTENNA_ITEM.get());
                        out.accept(VHF_WHIP_ITEM.get());
                        out.accept(YAGI_ANTENNA_ITEM.get());
                        out.accept(DISH_ANTENNA_ITEM.get());
                        out.accept(HELICAL_WHIP_ITEM.get());
                        out.accept(RECEIVING_LOOP_ITEM.get());
                        out.accept(HELIX_ANTENNA_ITEM.get());
                        out.accept(COAX_THIN_ITEM.get());
                        out.accept(COAX_THICK_ITEM.get());
                        out.accept(COAX_HARDLINE_ITEM.get());
                        out.accept(COAX_HEAVY_ITEM.get());
                        out.accept(COAX_RIGID_ITEM.get());
                        out.accept(LINEAR_AMPLIFIER_ITEM.get());
                        out.accept(BROADCAST_AMPLIFIER_ITEM.get());
                        out.accept(LOADING_COIL_ITEM.get());
                        out.accept(LITZ_LOADING_COIL_ITEM.get());
                        out.accept(TOP_LOAD_WIRE_ITEM.get());
                        out.accept(GROUND_MAT_ITEM.get());
                        out.accept(ALTERNATOR_ITEM.get());
                        for (var it : ITEMS.getEntries()) {
                            var item = it.get();
                            if (!SHOWN.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath())) continue;
                            out.accept(item);
                        }
                    })
                    .build());

    public PgTelegraph(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, TelegraphConfig.SPEC);
        modBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent e) ->
                e.register(com.sparkgap.pgtelegraph.radio.StationChunks.CONTROLLER));
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        SOUNDS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(PgTelegraph::registerPayloads);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                {
                    com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(ALTERNATOR.get(),
                            () -> com.sparkgap.pgtelegraph.block.AlternatorBlockEntity.STRESS_PER_RPM);
                    com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(ROTATOR.get(),
                            () -> com.sparkgap.pgtelegraph.block.RotatorBlockEntity.STRESS_PER_RPM);
                    com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.register(MOBILE_RADIO.get(),
                            new com.sparkgap.pgtelegraph.block.MobileRadioBlockEntity.Movement());
                }));
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
            modBus.addListener(com.sparkgap.pgtelegraph.compat.CcPeripherals::register);
        }
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            com.sparkgap.pgtelegraph.client.ClientSetup.init(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("13");
        com.sparkgap.pgtelegraph.network.Batch2Payloads.register(registrar);
        com.sparkgap.pgtelegraph.broadcast.AudioNet.register(registrar);
        com.sparkgap.pgtelegraph.media.VoiceNet.register(registrar);
        com.sparkgap.pgtelegraph.media.PicNet.register(registrar);
        com.sparkgap.pgtelegraph.media.TvNet.register(registrar);
        com.sparkgap.pgtelegraph.media.SpeakerNet.register(registrar);
        registrar.playToServer(KeyPressPayload.TYPE, KeyPressPayload.STREAM_CODEC, KeyPressPayload::handle);
        registrar.playToServer(com.sparkgap.pgtelegraph.network.PaddleInputPayload.TYPE,
                com.sparkgap.pgtelegraph.network.PaddleInputPayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.PaddleInputPayload::handle);
        registrar.playToServer(com.sparkgap.pgtelegraph.network.PerforatePayload.TYPE,
                com.sparkgap.pgtelegraph.network.PerforatePayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.PerforatePayload::handle);
        registrar.playToServer(com.sparkgap.pgtelegraph.network.TunePayload.TYPE,
                com.sparkgap.pgtelegraph.network.TunePayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.TunePayload::handle);
        registrar.playToServer(com.sparkgap.pgtelegraph.network.CoilPayload.TYPE,
                com.sparkgap.pgtelegraph.network.CoilPayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.CoilPayload::handle);
        registrar.playToServer(com.sparkgap.pgtelegraph.network.AimPayload.TYPE,
                com.sparkgap.pgtelegraph.network.AimPayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.AimPayload::handle);
        registrar.playToServer(com.sparkgap.pgtelegraph.network.HandheldSetPayload.TYPE,
                com.sparkgap.pgtelegraph.network.HandheldSetPayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.HandheldSetPayload::handle);
        registrar.playToServer(com.sparkgap.pgtelegraph.network.OpenPartsPayload.TYPE,
                com.sparkgap.pgtelegraph.network.OpenPartsPayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.OpenPartsPayload::handle);
        registrar.playToClient(com.sparkgap.pgtelegraph.network.HandheldStatePayload.TYPE,
                com.sparkgap.pgtelegraph.network.HandheldStatePayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.HandheldStatePayload::handle);
        registrar.playToClient(com.sparkgap.pgtelegraph.network.HandheldRxPayload.TYPE,
                com.sparkgap.pgtelegraph.network.HandheldRxPayload.STREAM_CODEC, com.sparkgap.pgtelegraph.network.HandheldRxPayload::handle);
    }
}
