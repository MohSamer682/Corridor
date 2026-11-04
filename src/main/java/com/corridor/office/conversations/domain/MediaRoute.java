package com.corridor.office.conversations.domain;

/** How a group's voice and video travel; small groups connect directly to each other (ADR-005). */
public sealed interface MediaRoute permits MediaRoute.PeerToPeer, MediaRoute.TooBig {

    int MAX_PEERS = 4;

    String name();

    /** Signals may only pass between members when the route carries media peer to peer. */
    boolean relaysSignals();

    static MediaRoute forGroup(Group group) {
        return group.members().size() <= MAX_PEERS ? new PeerToPeer() : new TooBig();
    }

    record PeerToPeer() implements MediaRoute {

        @Override
        public String name() {
            return "PEER_TO_PEER";
        }

        @Override
        public boolean relaysSignals() {
            return true;
        }
    }

    /** Chat only, until meeting rooms bring an SFU (F-09). */
    record TooBig() implements MediaRoute {

        @Override
        public String name() {
            return "TOO_BIG";
        }

        @Override
        public boolean relaysSignals() {
            return false;
        }
    }
}
