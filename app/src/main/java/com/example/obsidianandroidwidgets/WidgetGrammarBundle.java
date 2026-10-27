package com.example.obsidianandroidwidgets;

import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(includeAll = true, grammarLocatorClassName = ".WidgetGrammarLocator")
public final class WidgetGrammarBundle {
    public static io.noties.prism4j.Prism4j prism() { return Holder.INSTANCE; }
    private static class Holder {
        static final io.noties.prism4j.Prism4j INSTANCE = new io.noties.prism4j.Prism4j(new WidgetGrammarLocator());
    }
}
