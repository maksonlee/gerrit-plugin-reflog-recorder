package com.maksonlee.gerrit.reflog.ssh;

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.sshd.PluginCommandModule;

import javax.inject.Inject;

public class SshModule extends PluginCommandModule {
    @Inject
    public SshModule(@PluginName String pluginName) {
        super(pluginName);
    }

    @Override
    protected void configureCommands() {
        command(GetRevisionCommand.class);
        command(ResolveManifestCommand.class);
        command(ImportReflogCommand.class);
    }
}
