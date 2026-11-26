package com.sparkgap.pgtelegraph.radio;

/** Amateur bands VLF..SHF (kHz). Only a guide on the dial: nothing stops you transmitting elsewhere. */
public final class Bands {
    private Bands() {}

    public static final String[] NAMES = {"VLF", "2200m", "630m", "160m", "80m", "40m", "30m", "20m", "17m", "15m", "12m", "10m",
            "6m", "2m", "70cm", "23cm", "13cm", "3cm"};
    public static final int[][] KHZ = {{10, 30}, {135, 138}, {472, 479}, {1800, 2000}, {3500, 4000}, {7000, 7300}, {10100, 10150}, {14000, 14350},
            {18068, 18168}, {21000, 21450}, {24890, 24990}, {28000, 29700},
            {50000, 54000}, {144000, 148000}, {430000, 450000}, {1240000, 1300000}, {2300000, 2450000}, {10000000, 10500000}};
    /** customary CW calling spots */
    public static final int[] CW_KHZ = {17, 136, 475, 1810, 3530, 7030, 10116, 14060, 18086, 21060, 24906, 28060,
            50090, 144050, 432050, 1296050, 2304050, 10368050};

    public static String name(long hz) {
        long k = hz / 1000;
        for (int i = 0; i < KHZ.length; i++) if (k >= KHZ[i][0] && k <= KHZ[i][1]) return NAMES[i];
        return null;
    }
}
