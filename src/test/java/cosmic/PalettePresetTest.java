package cosmic;

import cosmic.graphics.PalettePreset;
import cosmic.util.Config;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PalettePresetTest {

    @Test
    void defaultPalettePreservesTheExistingAmberColors() {
        Config config = new Config();
        PalettePreset amber = config.getColorPalette();

        assertEquals(PalettePreset.GARGANTUA_AMBER, amber);
        assertEquals(new PalettePreset.Rgb(1.10f, 0.18f, 0.018f), amber.getDiskStop(0));
        assertEquals(new PalettePreset.Rgb(3.80f, 3.40f, 2.85f), amber.getDiskStop(4));
        assertEquals(new PalettePreset.Rgb(0.014f, 0.0045f, 0.0008f), amber.getSkyDust());
    }

    @Test
    void paletteCanBeLoadedAndCyclesInBothDirections() {
        Config config = new Config();
        Properties properties = new Properties();
        properties.setProperty("colorPalette", "verdelite_green");
        config.loadFromProperties(properties);

        assertEquals(PalettePreset.VERDELITE_GREEN, config.getColorPalette());
        assertEquals(PalettePreset.GARGANTUA_AMBER, PalettePreset.VIOLET_NEBULA.cycle(1));
        assertEquals(PalettePreset.VIOLET_NEBULA, PalettePreset.GARGANTUA_AMBER.cycle(-1));
    }
}
