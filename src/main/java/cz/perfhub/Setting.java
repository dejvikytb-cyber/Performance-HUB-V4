package cz.perfhub;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/** Jedno přepínatelné nastavení: klik = další hodnota ze seznamu. */
public final class Setting<T> {
    private final String label;
    private final Supplier<OptionInstance<T>> option;
    private final List<T> values;
    private final Function<T, String> fmt;
    private final BiPredicate<T, T> same;

    public Setting(String label, Supplier<OptionInstance<T>> option, List<T> values, Function<T, String> fmt) {
        this(label, option, values, fmt, Objects::equals);
    }

    public Setting(String label, Supplier<OptionInstance<T>> option, List<T> values,
                   Function<T, String> fmt, BiPredicate<T, T> same) {
        this.label = label;
        this.option = option;
        this.values = values;
        this.fmt = fmt;
        this.same = same;
    }

    public void cycle() {
        OptionInstance<T> o = option.get();
        T cur = o.get();
        int idx = -1;
        for (int i = 0; i < values.size(); i++) {
            if (same.test(values.get(i), cur)) { idx = i; break; }
        }
        o.set(values.get((idx + 1) % values.size()));
        Minecraft.getInstance().options.save();
    }

    public Component label() {
        String shown;
        try {
            shown = fmt.apply(option.get().get());
        } catch (Throwable t) {
            shown = "?";
        }
        return Component.literal(label + "  ")
                .append(Component.literal(shown).withStyle(ChatFormatting.GREEN));
    }
}
