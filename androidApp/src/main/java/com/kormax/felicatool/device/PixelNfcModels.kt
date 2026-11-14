package com.kormax.felicatool.device

/**
 * SKU lists from com.google.android.pixelnfc 17 (version code 37), shipped with the Pixel 9a.
 * Source APK SHA-256: 70bb70d946797f176ec2140235a9365507e71017ea0e6d56e833d3e15a4ff5e9.
 */
internal object PixelNfcModels {
    // DeviceInfoContentProvider.isDeviceJapanSku: accepted regardless of location.
    val supported =
        setOf(
            "G020N",
            "G020Q",
            "G025H",
            "G025M",
            "G03Z5",
            "G0B96",
            "G0CEX",
            "G0F3Y",
            "G1B60",
            "G1KAW",
            "G1XJ6",
            "G25QD",
            "G3Y12",
            "G4S1M",
            "G4TSL",
            "G576D",
            "G5NZ6",
            "G82U8",
            "G8AK3",
            "G9W5D",
            "GB17L",
            "GBDU9",
            "GBZ4S",
            "GC15S",
            "GC3G8",
            "GE9DP",
            "GER5L",
            "GF5KQ",
            "GFE4J",
            "GFW3R",
            "GG3HH",
            "GGE4J",
            "GHH4K",
            "GL066",
            "GM2SN",
            "GM66V",
            "GN4F5",
            "GPQ72",
            "GQ57S",
            "GQF4C",
            "GR1YH",
            "GRY0E",
            "GRZ2N",
            "GV0BP",
            "GV9X7",
            "GWSQ2",
            "GWVK6",
            "GYPW4",
            "GZPF0",
        )

    // DeviceInfoContentProvider.isROWSku: accepted only when country detection returns Japan.
    val supportedWhenInJapan =
        setOf(
            "G4HCD",
            "GM45K",
            "GQ05X",
            "GUJ0N",
            "GZDQ6",
        )
}
