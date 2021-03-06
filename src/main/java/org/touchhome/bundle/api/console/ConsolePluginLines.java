package org.touchhome.bundle.api.console;

import java.util.Collection;

public interface ConsolePluginLines extends ConsolePlugin<Collection<String>> {

    @Override
    default RenderType getRenderType() {
        return RenderType.string;
    }
}
