package com.maksonlee.gerrit.reflog.api;

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
                .to(ReflogRecorder.class);
    }
}