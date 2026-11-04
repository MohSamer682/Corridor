import {
  ConnectionState,
  Room,
  RoomEvent,
  Track,
  type Participant,
  type RemoteParticipant,
} from 'livekit-client';
import { useEffect, useState } from 'react';
import type { Call } from './usePeerConnections';

export type LiveKitJoin = { url: string; token: string };

const streamOf = (participant: Participant) => {
  const tracks = [...participant.trackPublications.values()]
    .map((publication) => publication.track?.mediaStreamTrack)
    .filter((track): track is MediaStreamTrack => Boolean(track));
  return tracks.length ? new MediaStream(tracks) : undefined;
};

/**
 * The same call controls as a peer-to-peer group, through LiveKit: used by meetings and by conversations of five or
 * more. Remote people are keyed by their user id (LiveKit's identity).
 */
export function useLiveKitCall(join: LiveKitJoin | null): Call & { names: Record<string, string> } {
  const [room] = useState(() => new Room({ adaptiveStream: true, dynacast: true }));
  const [remote, setRemote] = useState<RemoteParticipant[]>([]);
  const [, setVersion] = useState(0);
  const [connected, setConnected] = useState(false);
  const [micBlocked, setMicBlocked] = useState(false);
  // Leaving drops this ticket only; a new group or meeting brings a new one.
  const [leftToken, setLeftToken] = useState<string | null>(null);
  const left = Boolean(join) && leftToken === join?.token;

  useEffect(() => {
    if (!join || left) return undefined;
    const refresh = () => {
      setRemote([...room.remoteParticipants.values()]);
      setVersion((n) => n + 1);
    };
    room
      .on(RoomEvent.ParticipantConnected, refresh)
      .on(RoomEvent.ParticipantDisconnected, refresh)
      .on(RoomEvent.TrackSubscribed, refresh)
      .on(RoomEvent.TrackUnsubscribed, refresh)
      .on(RoomEvent.TrackMuted, refresh)
      .on(RoomEvent.TrackUnmuted, refresh)
      .on(RoomEvent.LocalTrackPublished, refresh)
      .on(RoomEvent.LocalTrackUnpublished, refresh)
      .on(RoomEvent.ConnectionStateChanged, (state) =>
        setConnected(state === ConnectionState.Connected),
      );
    void room
      .connect(join.url, join.token)
      .then(() => room.localParticipant.setMicrophoneEnabled(true))
      .then(() => setMicBlocked(false))
      .catch(() => setMicBlocked(true))
      .finally(refresh);
    return () => {
      room.removeAllListeners();
      void room.disconnect();
    };
  }, [join, left, room]);

  const local = room.localParticipant;
  const camera = local.getTrackPublication(Track.Source.Camera)?.track?.mediaStreamTrack;
  return {
    streams: Object.fromEntries(remote.map((p) => [p.identity, streamOf(p)]).filter(([, s]) => s)),
    states: Object.fromEntries(
      remote.map((p) => [p.identity, connected ? 'connected' : 'connecting']),
    ),
    names: Object.fromEntries(remote.map((p) => [p.identity, p.name ?? ''])),
    local: camera ? new MediaStream([camera]) : null,
    muted: !local.isMicrophoneEnabled,
    cameraOn: local.isCameraEnabled,
    micBlocked,
    inCall: Boolean(join) && !left,
    toggleMute: () =>
      void local
        .setMicrophoneEnabled(!local.isMicrophoneEnabled)
        .then(() => setVersion((n) => n + 1)),
    toggleCamera: () =>
      void local.setCameraEnabled(!local.isCameraEnabled).then(() => setVersion((n) => n + 1)),
    leave: () => setLeftToken(join?.token ?? null),
    retry: () =>
      void local
        .setMicrophoneEnabled(true)
        .then(() => setMicBlocked(false))
        .catch(() => setMicBlocked(true)),
  };
}
