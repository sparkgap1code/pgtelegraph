package com.sparkgap.pgtelegraph.media;

/** G.711 mu-law: 8 bits a sample, telephone quality (8 kHz voice). */
public final class MuLaw {
    private MuLaw() {}

    private static final int BIAS = 0x84, CLIP = 32635;

    public static byte encode(int pcm16) {
        int sign = (pcm16 >> 8) & 0x80;
        if (sign != 0) pcm16 = -pcm16;
        if (pcm16 > CLIP) pcm16 = CLIP;
        pcm16 += BIAS;
        int exp = 7;
        for (int mask = 0x4000; (pcm16 & mask) == 0 && exp > 0; mask >>= 1) exp--;
        int mant = (pcm16 >> (exp + 3)) & 0x0F;
        return (byte) ~(sign | (exp << 4) | mant);
    }

    public static int decode(byte b) {
        int u = ~b & 0xFF;
        int sign = u & 0x80, exp = (u >> 4) & 7, mant = u & 0x0F;
        int s = ((mant << 3) + BIAS) << exp;
        s -= BIAS;
        return sign != 0 ? -s : s;
    }
}
