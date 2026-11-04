import { Client, type StompSubscription } from '@stomp/stompjs';
import { useEffect, useMemo, useRef } from 'react';
import { apiClient, refreshOnce } from '../../../shared/api/apiClient';
import { useSessionStore } from '../../auth/sessionStore';
import {
  emitSignal,
  useConversationStore,
  type ChatMessage,
  type GroupMessage,
  type Signal,
} from '../../talk/conversationStore';
import { usePresenceStore, type FrameMessage, type Occupant } from './presenceStore';

export type PresenceControls = {
  walk: (x: number, y: number) => void;
  step: (dx: number, dy: number) => void;
  /** Sends any STOMP message on the floor's connection; dropped while reconnecting. */
  send: (destination: string, body: object) => void;
};

const socketUrl = () => `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`;

/**
 * While a floor is open: keeps one STOMP connection, enters the floor, and feeds frames into the presence
 * store, and conversation updates into the conversation store. Reconnects on its own; each connect asks for
 * a fresh access token first.
 */
export function usePresence(floorId: string | null): PresenceControls {
  const client = useRef<Client | null>(null);

  useEffect(() => {
    if (!floorId) return undefined;
    usePresenceStore.getState().reset();
    useConversationStore.getState().reset();
    const stomp = new Client({
      brokerURL: socketUrl(),
      reconnectDelay: 2000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: async () => {
        await refreshOnce().catch(() => undefined);
        stomp.connectHeaders = {
          Authorization: `Bearer ${useSessionStore.getState().accessToken ?? ''}`,
        };
      },
      onConnect: () => {
        usePresenceStore.getState().setConnected(true);
        subscribe(stomp, floorId);
        stomp.publish({ destination: `/app/floors/${floorId}/enter` });
        void loadOccupants(floorId);
        void loadFocus(floorId);
      },
      onWebSocketClose: () => usePresenceStore.getState().setConnected(false),
    });
    client.current = stomp;
    stomp.activate();
    return () => {
      if (stomp.connected) stomp.publish({ destination: '/app/leave' });
      void stomp.deactivate();
      client.current = null;
      usePresenceStore.getState().reset();
      useConversationStore.getState().reset();
    };
  }, [floorId]);

  return useMemo(() => {
    const send = (destination: string, body: object) => {
      if (client.current?.connected)
        client.current.publish({ destination, body: JSON.stringify(body) });
    };
    return {
      walk: (x, y) => send('/app/walk', { x, y }),
      step: (dx, dy) => send('/app/step', { dx, dy }),
      send,
    };
  }, []);
}

function subscribe(stomp: Client, floorId: string): StompSubscription[] {
  const json = <T>(body: string) => JSON.parse(body) as T;
  return [
    stomp.subscribe(`/topic/floors/${floorId}/frames`, (message) => {
      usePresenceStore.getState().applyFrame(json<FrameMessage>(message.body));
      if (usePresenceStore.getState().unnamed().length) void loadOccupants(floorId);
    }),
    stomp.subscribe(`/topic/floors/${floorId}/focus`, (message) =>
      useConversationStore.getState().setFocused(json<{ focused: string[] }>(message.body).focused),
    ),
    stomp.subscribe('/user/queue/conversation', (message) =>
      useConversationStore.getState().setGroup(json<GroupMessage>(message.body)),
    ),
    stomp.subscribe('/user/queue/messages', (message) =>
      useConversationStore.getState().addMessage(json<ChatMessage>(message.body)),
    ),
    stomp.subscribe('/user/queue/signals', (message) => emitSignal(json<Signal>(message.body))),
  ];
}

async function loadOccupants(floorId: string) {
  const occupants = await apiClient<Occupant[]>(`/api/v1/floors/${floorId}/occupants`).catch(
    () => [],
  );
  usePresenceStore.getState().arrive(occupants);
}

async function loadFocus(floorId: string) {
  const view = await apiClient<{ focused: string[] }>(`/api/v1/floors/${floorId}/focus`).catch(
    () => ({ focused: [] }),
  );
  useConversationStore.getState().setFocused(view.focused);
}
