import { useCallback, useEffect, useRef, useState } from 'react';
import { apiClient } from '../../shared/api/apiClient';
import { onSignal, type GroupMessage, type Signal } from './conversationStore';

type Send = (destination: string, body: object) => void;

type Peer = {
  pc: RTCPeerConnection;
  /** Empty until the connection has its media slots; see {@link slots}. */
  audio: RTCRtpSender | null;
  video: RTCRtpSender | null;
  remote: MediaStream;
  makingOffer: boolean;
};

export type Call = {
  /** Remote media by user id, once it arrives. */
  streams: Record<string, MediaStream>;
  /** RTCPeerConnection.connectionState by user id. */
  states: Record<string, RTCPeerConnectionState>;
  /** Your own camera, for your tile. */
  local: MediaStream | null;
  muted: boolean;
  cameraOn: boolean;
  micBlocked: boolean;
  inCall: boolean;
  toggleMute: () => void;
  toggleCamera: () => void;
  leave: () => void;
  retry: () => void;
};

/**
 * Perfect negotiation: either side may offer, and when both do at once the polite one (the higher user id) gives
 * way. So the lower id's offer wins.
 */
export const isPolite = (me: string, other: string) => me > other;

function slots(peer: Peer, mic: MediaStreamTrack | null) {
  for (const transceiver of peer.pc.getTransceivers()) {
    transceiver.direction = 'sendrecv';
    const kind = transceiver.receiver.track.kind;
    if (kind === 'audio') peer.audio ??= transceiver.sender;
    if (kind === 'video') peer.video ??= transceiver.sender;
  }
  if (mic) void peer.audio?.replaceTrack(mic);
}

/**
 * One RTCPeerConnection per other member of a peer-to-peer group, mesh style (ADR-005). Each connection has an
 * audio and a video slot from the start; the microphone and camera are plugged in with replaceTrack when they are
 * ready, so a slow permission prompt never holds the call up and turning the camera on needs no new offer.
 */
export function usePeerConnections(
  group: GroupMessage | null,
  me: string | null,
  send: Send,
): Call {
  const peers = useRef(new Map<string, Peer>());
  const mic = useRef<MediaStreamTrack | null>(null);
  const iceServers = useRef<RTCIceServer[]>([]);
  const [streams, setStreams] = useState<Record<string, MediaStream>>({});
  const [states, setStates] = useState<Record<string, RTCPeerConnectionState>>({});
  const [muted, setMuted] = useState(false);
  const [preview, setPreview] = useState<MediaStream | null>(null);
  const [micBlocked, setMicBlocked] = useState(false);
  const [left, setLeft] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const active = Boolean(
    me && group?.groupId && group.route === 'PEER_TO_PEER' && left !== group.groupId,
  );
  const others = active ? (group?.members ?? []).filter((id) => id !== me).sort() : [];
  const othersKey = others.join(',');

  const signal = useCallback(
    (to: string, kind: Signal['kind'], payload: unknown) =>
      send('/app/conversations/signal', { to, kind, payload: JSON.stringify(payload) }),
    [send],
  );

  const forget = useCallback((id: string) => {
    peers.current.get(id)?.pc.close();
    peers.current.delete(id);
    setStreams(({ [id]: _gone, ...rest }) => rest);
    setStates(({ [id]: _gone, ...rest }) => rest);
  }, []);

  const connect = useCallback(
    // The side that offers first adds the audio and video slots; the side that answers takes the ones the offer
    // made, so the two never end up with extra slots and a second round of offers.
    (id: string, answering = false) => {
      const existing = peers.current.get(id);
      if (existing) return existing;
      const pc = new RTCPeerConnection({ iceServers: iceServers.current });
      const remote = new MediaStream();
      const peer: Peer = {
        pc,
        audio: answering ? null : pc.addTransceiver('audio', { direction: 'sendrecv' }).sender,
        video: answering ? null : pc.addTransceiver('video', { direction: 'sendrecv' }).sender,
        remote,
        makingOffer: false,
      };
      peers.current.set(id, peer);
      if (mic.current) void peer.audio?.replaceTrack(mic.current);
      pc.ontrack = (event) => {
        remote.addTrack(event.track);
        setStreams((all) => ({ ...all, [id]: remote }));
      };
      pc.onicecandidate = (event) =>
        event.candidate && signal(id, 'candidate', event.candidate.toJSON());
      pc.onconnectionstatechange = () => setStates((all) => ({ ...all, [id]: pc.connectionState }));
      pc.onnegotiationneeded = async () => {
        try {
          peer.makingOffer = true;
          await pc.setLocalDescription();
          signal(id, 'offer', pc.localDescription);
        } finally {
          peer.makingOffer = false;
        }
      };
      return peer;
    },
    [signal],
  );

  // Join the people in the group, and drop the ones who walked away.
  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (!othersKey) {
        [...peers.current.keys()].forEach(forget);
        mic.current?.stop();
        mic.current = null;
        setPreview((camera) => {
          camera?.getTracks().forEach((track) => track.stop());
          return null;
        });
        return;
      }
      if (!iceServers.current.length) {
        iceServers.current = await apiClient<{ iceServers: RTCIceServer[] }>(
          '/api/v1/media/ice-servers',
        )
          .then((r) => r.iceServers)
          .catch(() => []);
      }
      if (cancelled) return;
      const wanted = othersKey.split(',');
      [...peers.current.keys()].filter((id) => !wanted.includes(id)).forEach(forget);
      wanted.forEach((id) => connect(id));
    })();
    return () => {
      cancelled = true;
    };
  }, [othersKey, connect, forget]);

  // Ask for the microphone once there is someone to talk to.
  useEffect(() => {
    if (!othersKey || mic.current) return;
    void navigator.mediaDevices
      ?.getUserMedia({ audio: true })
      .then((stream) => {
        const track = stream.getAudioTracks()[0] ?? null;
        mic.current = track;
        setMicBlocked(false);
        peers.current.forEach((peer) => void peer.audio?.replaceTrack(track));
      })
      .catch(() => setMicBlocked(true));
  }, [othersKey, attempt]);

  // Answer what the others send.
  useEffect(
    () =>
      onSignal(async ({ from, kind, payload }) => {
        if (!me || !othersKey.split(',').includes(from)) return;
        const peer = connect(from, kind === 'offer');
        const { pc } = peer;
        const data = JSON.parse(payload) as RTCSessionDescriptionInit & RTCIceCandidateInit;
        if (kind === 'candidate') {
          await pc.addIceCandidate(data).catch(() => undefined);
          return;
        }
        const collision = kind === 'offer' && (peer.makingOffer || pc.signalingState !== 'stable');
        if (collision && !isPolite(me, from)) return;
        await pc.setRemoteDescription(data);
        if (!peer.audio) slots(peer, mic.current);
        if (kind === 'offer') {
          await pc.setLocalDescription();
          signal(from, 'answer', pc.localDescription);
        }
      }),
    [me, othersKey, connect, signal],
  );

  const toggleMute = () => {
    if (mic.current) mic.current.enabled = muted;
    setMuted(!muted);
  };

  const toggleCamera = async () => {
    if (preview) {
      preview.getTracks().forEach((track) => track.stop());
      peers.current.forEach((peer) => void peer.video?.replaceTrack(null));
      setPreview(null);
      return;
    }
    const camera = await navigator.mediaDevices?.getUserMedia({ video: true }).catch(() => null);
    const track = camera?.getVideoTracks()[0];
    if (!camera || !track) return;
    peers.current.forEach((peer) => void peer.video?.replaceTrack(track));
    setPreview(camera);
  };

  return {
    streams,
    states,
    local: preview,
    muted,
    // The camera goes off with the call it was on.
    cameraOn: Boolean(preview) && others.length > 0,
    micBlocked,
    inCall: others.length > 0,
    toggleMute,
    toggleCamera: () => void toggleCamera(),
    leave: () => setLeft(group?.groupId ?? null),
    retry: () => setAttempt((n) => n + 1),
  };
}
