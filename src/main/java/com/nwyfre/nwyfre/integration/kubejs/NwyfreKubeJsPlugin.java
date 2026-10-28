package com.nwyfre.nwyfre.integration.kubejs;

import com.nwyfre.nwyfre.ritual.ImbuedRitual;
import com.nwyfre.nwyfre.ritual.NwyfreRitualEvent;
import com.nwyfre.nwyfre.ritual.RitualRecipe;
import com.nwyfre.nwyfre.ritual.RitualState;
import com.nwyfre.nwyfre.stage.NwyfreStageChangeEvent;
import com.nwyfre.nwyfre.stage.PlayerStages;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS integration.
 *
 * <p>Deliberately thin. Authoring rituals needs nothing here: {@code nwyfre:ritual} is an ordinary
 * recipe type, so {@code ServerEvents.recipes} with {@code event.custom({...})} already works. What
 * this adds is the other half of pack scripting -- a {@code Nwyfre} global for progression, and
 * permission to touch the ritual classes from a script.
 *
 * <p>Discovered by KubeJS through {@code kubejs.plugins.txt} at the jar root, so this class is
 * never loaded when KubeJS is absent.
 */
public class NwyfreKubeJsPlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("Nwyfre", new NwyfreBindings());
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        // Scripts legitimately want to read these: the two cancellable events are the whole
        // extension story, and the state types are what the events carry.
        filter.allow(NwyfreStageChangeEvent.class);
        filter.allow(PlayerStages.class);
        filter.allow(NwyfreRitualEvent.class);
        filter.allow(NwyfreRitualEvent.Starting.class);
        filter.allow(NwyfreRitualEvent.Completed.class);
        filter.allow(NwyfreRitualEvent.Stopped.class);
        filter.allow(RitualRecipe.class);
        filter.allow(RitualState.class);
        filter.allow(ImbuedRitual.class);
    }
}
