package com.asosiaciondeasis.animalesdeasis.Abstraccions;

import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;

/**
 * Implemented by any controller loaded into the portal's content area.
 *
 * <p>Beyond handing the controller a reference to the portal, this interface is
 * where a screen gets told it is going away. That matters because screens here
 * subscribe to application-wide events — {@code SyncEventManager} keeps a static
 * list of listeners — and a subscription outlives the screen that made it unless
 * something cancels it.</p>
 */
public interface IPortalAwareController {

    void setPortalController(PortalController controller);

    /**
     * Called by {@link PortalController} on the outgoing screen, immediately
     * before it is replaced.
     *
     * <p>Release anything that would otherwise keep this controller reachable:
     * listeners on shared registries, running {@code Task}s, timers, open
     * hardware such as the barcode scanner's camera.</p>
     *
     * <p>The portal used to do this with a hardcoded
     * {@code instanceof AnimalManagementController} check, so exactly one screen
     * was ever cleaned up and every other one leaked its subscriptions on each
     * navigation. Making it part of the contract means a new screen cannot be
     * forgotten — the default below is a deliberate no-op for screens that hold
     * nothing, not an invitation to skip thinking about it.</p>
     */
    default void cleanup() {
        // Nothing held by default.
    }
}
