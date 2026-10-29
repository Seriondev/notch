package id.dynamicisland.screen;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/** Slider bilangan bulat dengan label, mis. "Ukuran: 100%". */
public class IntSlider extends SliderWidget {
    private final String label;
    private final String suffix;
    private final int min;
    private final int max;
    private final IntConsumer onChange;

    public IntSlider(int x, int y, int w, int h, String label, int min, int max, int current,
                     String suffix, IntConsumer onChange) {
        super(x, y, w, h, Text.empty(), (Math.max(min, Math.min(max, current)) - min) / (double) (max - min));
        this.label = label;
        this.suffix = suffix;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        updateMessage();
    }

    private int intValue() {
        return min + (int) Math.round(this.value * (max - min));
    }

    @Override
    protected void updateMessage() {
        if (label == null) return;
        setMessage(Text.literal(label + ": " + intValue() + suffix));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }
}
